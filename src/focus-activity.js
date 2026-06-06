// Bridge from the PWA to the native FocusActivity Capacitor plugin.
// No-ops gracefully when running in a plain browser or when the native plugin
// is not present, so the web app works without any native build.
//
// CRITICAL — lazy resolution: the "are we native?" check and the native call
// path are resolved ON EVERY CALL, never frozen at load. The previous version
// froze `isNative` in an IIFE the instant this script parsed; if Capacitor's
// bridge wasn't ready at that exact moment (timing varies by WebView/Android
// version), `isNative` stuck at false FOREVER. A live getter removes that race.
//
// CRITICAL — how we actually reach the native plugin (this was silently broken):
// this app has NO bundler, so the @capacitor/core ES module — the thing that
// provides `Capacitor.registerPlugin()` and the `Capacitor.Plugins` proxy — is
// NEVER loaded into the page. The only `window.Capacitor` present is the bridge
// the native WebView injects, and (verified against Capacitor 6.2) that bridge
// does NOT expose `registerPlugin`; it ships only the low-level primitives
// `nativePromise()` / `addListener()`, which is exactly what `registerPlugin`
// calls under the hood. So the old `cap.registerPlugin("FocusActivity")` always
// returned undefined → `plugin()` was null → EVERY native call fell through to
// the JS no-op below: the OS "Allow notifications?" dialog never fired, reminders
// never posted, and the diagnostics only ever read the fake fallback values.
// We now dispatch straight through `Capacitor.nativePromise(name, method, opts)`,
// which genuinely reaches the Kotlin plugin. A real registerPlugin proxy is still
// preferred when present (e.g. if @capacitor/core is ever bundled), so both worlds work.
(function () {
  "use strict";

  var NAME = "FocusActivity";

  function noopHandle() { return { remove: function () {} }; }

  function getCap() {
    return (typeof Capacitor !== "undefined") ? Capacitor
         : (typeof window !== "undefined" ? window.Capacitor : null);
  }

  // Live check — re-evaluated on every read, never cached.
  function nativePlatform() {
    var cap = getCap();
    try { return !!(cap && cap.isNativePlatform && cap.isNativePlatform()); }
    catch (e) { return false; }
  }

  // Prefer a real plugin proxy when one is obtainable — only when @capacitor/core's
  // runtime is loaded (not the case in this no-bundler build, but kept so the
  // bridge still works if it ever is). Cached only after a successful resolve, so
  // an early call before the bridge is ready doesn't poison later ones.
  var cachedProxy = null;
  function proxy() {
    if (cachedProxy) return cachedProxy;
    var cap = getCap();
    if (!cap || !nativePlatform()) return null;
    try {
      if (typeof cap.registerPlugin === "function") cachedProxy = cap.registerPlugin(NAME);
      else if (cap.Plugins && cap.Plugins[NAME]) cachedProxy = cap.Plugins[NAME];
    } catch (e) { cachedProxy = null; }
    return cachedProxy;
  }

  // Invoke a native method by name. Returns the native Promise, or null when no
  // native channel exists (plain browser / PWA) so each caller can choose its own
  // web fallback. Tries a resolved proxy first, then the bridge's nativePromise.
  function invoke(method, opts) {
    var p = proxy();
    if (p && typeof p[method] === "function") {
      try { return p[method](opts || {}); } catch (e) {}
    }
    var cap = getCap();
    if (cap && nativePlatform() && typeof cap.nativePromise === "function") {
      try { return cap.nativePromise(NAME, method, opts || {}); } catch (e) {}
    }
    return null;
  }

  // Subscribe to a native plugin event (notification-button taps). Uses the
  // proxy's addListener when available, else the bridge's low-level addListener
  // (signature: addListener(pluginName, eventName, callback) → { remove }).
  function listen(eventName, cb) {
    var p = proxy();
    if (p && typeof p.addListener === "function") {
      try { return p.addListener(eventName, cb); } catch (e) {}
    }
    var cap = getCap();
    if (cap && nativePlatform() && typeof cap.addListener === "function") {
      try { return cap.addListener(NAME, eventName, cb); } catch (e) {}
    }
    return noopHandle();
  }

  // FocusActivity.start({ title, startedAt, elapsedMs, targetMs, accent })
  //   Launches the foreground service and shows the ongoing notification.
  //   elapsedMs is the total accumulated focus time (all previous sessions
  //   for the same block), so the chronometer starts from the right offset.
  //   targetMs (optional, 0 = open-ended) makes the notification count DOWN to
  //   the Pomodoro target instead of up. accent (optional hex) tints the
  //   notification so it reads like the in-app focus control.
  //
  // FocusActivity.update({ elapsedMs, paused })
  //   Switches the notification between chronometer and static "Pausado" text.
  //
  // FocusActivity.stop()
  //   Stops the foreground service and removes the notification.
  //
  // FocusActivity.addListener("action", ({ kind }) => ...)
  //   kind: "pause" | "resume" | "conclude" — a notification button was tapped.
  //
  // FocusActivity.checkPermission() → Promise<{ granted }>
  //   Whether POST_NOTIFICATIONS is granted (always true pre-Android 13).
  //
  // FocusActivity.requestPermission() → Promise<{ granted }>
  //   Prompts for POST_NOTIFICATIONS. The system only shows the dialog once —
  //   after a hard denial the user must enable it in system Settings, so callers
  //   should guide there if the result is still false.
  //
  // FocusActivity.shouldShowRationale() → Promise<{ show }>
  //   True when the OS will still show the permission dialog on the next request.
  //   False when permanently denied — guide the user to openAppSettings() instead.
  //
  // FocusActivity.openAppSettings() → Promise<void>
  //   Opens the system notification settings screen for this app.
  //
  // FocusActivity.notify({ title, body, tag }) → Promise<{ shown }>
  //   Posts a one-shot local reminder on its own channel. This is the ONLY
  //   notification channel that works inside the Capacitor WebView. `tag`
  //   dedupes/replaces (habits vs reflection get stable, distinct ids).
  //
  // FocusActivity.scheduleReminders({ enabled, habitsTime, reflectionTime,
  //   habitsTitle, habitsBody, reflectionTitle, reflectionBody })
  //   → Promise<{ scheduled, exact }>  — daily AlarmManager reminders that fire
  //   even with the app fully CLOSED. Times are "HH:mm"; strings arrive
  //   already-localized. `exact` is false when the OS denied exact alarms.
  //
  // FocusActivity.cancelReminders() → Promise<{ scheduled:false }>
  //
  // FocusActivity.shareImage({ base64, filename, title }) → Promise<{ shared }>
  //   Writes a PNG (base64, no data: prefix) to the app cache and hands it to the
  //   Android share sheet via a FileProvider content URI. This exists because the
  //   Web Share API's file support is unreliable inside the Capacitor WebView, so
  //   navigator.share({ files }) silently no-ops on many devices — the native
  //   ACTION_SEND path actually opens the chooser.

  window.FocusActivity = {
    get isNative() { return nativePlatform(); },
    start:       function (o) { invoke("start", o); },
    update:      function (o) { invoke("update", o); },
    stop:        function ()  { invoke("stop"); },
    notify:      function (o) { return invoke("showReminder", o)      || Promise.resolve({ shown: false }); },
    scheduleReminders: function (o) { return invoke("scheduleReminders", o) || Promise.resolve({ scheduled: false, exact: false }); },
    cancelReminders:   function ()  { return invoke("cancelReminders")       || Promise.resolve({ scheduled: false }); },
    shareImage:  function (o) { return invoke("shareImage", o) || Promise.resolve({ shared: false }); },
    addListener: function (ev, cb) { return listen(ev, cb); },
    checkPermission:     function () { return invoke("checkPermission")     || Promise.resolve({ granted: false }); },
    requestPermission:   function () { return invoke("requestPermission")   || Promise.resolve({ granted: false }); },
    shouldShowRationale: function () { return invoke("shouldShowRationale") || Promise.resolve({ show: false }); },
    openAppSettings:     function () { return invoke("openAppSettings")     || Promise.resolve(); },
    notifStatus:         function () { return invoke("notifStatus")         || Promise.resolve({ permission: false, enabled: false, channelBlocked: false }); },
  };
})();
