// Bridge from the PWA to the native AppUpdater Capacitor plugin.
// Lets the app install an update in-place (download the APK + hand it to the
// system package installer) instead of bouncing the user out to the browser.
// No-ops / reports "not native" in a plain browser or PWA, where there is no
// installer — callers fall back to opening the download URL there.
//
// Lazy resolution + native dispatch: see focus-activity.js for the full
// rationale. In short — `isNative` is read live (never frozen at load), and the
// plugin is reached through the bridge primitive `Capacitor.nativePromise()`
// rather than `Capacitor.registerPlugin()`. This is a NO-BUNDLER app, so
// @capacitor/core (which is what defines `registerPlugin`) is never loaded; the
// injected native bridge only exposes `nativePromise()` / `addListener()`. The
// old `cap.registerPlugin("AppUpdater")` always returned undefined inside the
// APK, so the updater silently fell back to window.open(apkUrl) — sending users
// to the browser and the "package conflicts" install error instead of updating
// in place. Dispatching via nativePromise actually reaches the Kotlin plugin.
(function () {
  "use strict";

  var NAME = "AppUpdater";

  function noopHandle() { return { remove: function () {} }; }

  function getCap() {
    return (typeof Capacitor !== "undefined") ? Capacitor
         : (typeof window !== "undefined" ? window.Capacitor : null);
  }

  function nativePlatform() {
    var cap = getCap();
    try { return !!(cap && cap.isNativePlatform && cap.isNativePlatform()); }
    catch (e) { return false; }
  }

  // Prefer a real plugin proxy when @capacitor/core's runtime is present; cached
  // only after a successful resolve. Falls through to nativePromise otherwise.
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

  // Invoke a native method by name; returns the native Promise, or null when no
  // native channel exists so callers can fall back (e.g. open the browser).
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

  // AppUpdater.canInstall() → Promise<{ granted }>
  //   Whether the app may install APKs ("install unknown apps" allowed; always
  //   true before Android 8).
  //
  // AppUpdater.openInstallSettings() → Promise<void>
  //   Opens the system "install unknown apps" toggle for this app.
  //
  // AppUpdater.downloadAndInstall({ url }) → Promise<{ status }>
  //   status "installing"       → APK downloaded, system installer launched.
  //   status "needs-permission" → sent the user to the install-unknown-apps
  //                               toggle; they should allow it and try again.
  //   Rejects on download failure.
  //
  // AppUpdater.addListener("downloadProgress", ({ percent }) => …)
  //   Fires with 0–100 while the APK downloads (when the size is known).

  window.AppUpdater = {
    get isNative() { return nativePlatform(); },
    canInstall:          function ()  { return invoke("canInstall")          || Promise.resolve({ granted: false }); },
    openInstallSettings: function ()  { return invoke("openInstallSettings") || Promise.resolve(); },
    downloadAndInstall:  function (o) { return invoke("downloadAndInstall", o) || Promise.reject(new Error("not native")); },
    addListener:         function (ev, cb) { return listen(ev, cb); },
  };
})();
