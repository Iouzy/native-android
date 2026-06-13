package com.pauta.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.pauta.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater. Offline-first means the ONLY network call in the whole app is
 * this one: it polls the rolling `latest-native` GitHub Release, and if its APK
 * carries a higher CI run number than this build ([BuildConfig.BUILD_RUN]),
 * offers to download + install it via the system package installer (same
 * signature → in-place upgrade, data preserved). // PT: o updater — a única
 * chamada de rede da app; compara a release com a build instalada.
 */
object AppUpdater {
    private const val REPO = "Iouzy/native-android"
    private val json = Json { ignoreUnknownKeys = true }

    /** A newer release. [notes] is the GitHub release `body` (markdown shown
     *  verbatim), empty when the release carries none. */
    data class Update(val run: Int, val url: String, val notes: String = "")

    /** Outcome of a [check]. Distinguishing [Failed] from [UpToDate] lets the UI
     *  say "couldn't check" on a flaky/offline network instead of the old lie
     *  "up to date" — the whole point of B2. // PT: distingue falha de rede de
     *  "está atualizado", que antes eram indistinguíveis (ambos → null). */
    sealed interface CheckResult {
        data class Available(val update: Update) : CheckResult
        data object UpToDate : CheckResult
        data object Failed : CheckResult
    }

    /** Transient-failure backoff: wait 2s, then 4s, then 8s between the up-to-four
     *  attempts. A dropped packet or a sleeping radio shouldn't read as "no update".
     *  // PT: recuo 2s/4s/8s entre tentativas — uma falha de rede não é "atualizado". */
    private val BACKOFF_MS = longArrayOf(2_000, 4_000, 8_000)

    /** Run [attempt] up to `BACKOFF_MS.size + 1` times, sleeping the backoff
     *  between tries; returns the first non-throwing result, or null once every
     *  attempt has thrown. Callers map null to their own failed state. The block
     *  must never return null on success (both callers return non-null or throw),
     *  so a null here always means "all attempts failed". */
    private suspend fun <T : Any> withRetry(attempt: () -> T): T? {
        for (i in 0..BACKOFF_MS.size) {
            runCatching(attempt).getOrNull()?.let { return it }
            if (i < BACKOFF_MS.size) delay(BACKOFF_MS[i])
        }
        return null
    }

    /** Polls the rolling release; retries transient failures, then reports
     *  [CheckResult.Failed] rather than silently swallowing the error. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        withRetry { fetchLatest() } ?: CheckResult.Failed
    }

    /** One network round-trip: fetch the rolling release, parse its assets + notes
     *  and decide if it's newer than this build. Throws on any network/parse error
     *  so [withRetry] can back off and [check] can ultimately report Failed. */
    private fun fetchLatest(): CheckResult {
        val conn = (URL("https://api.github.com/repos/$REPO/releases/tags/latest-native").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000; readTimeout = 10_000
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(text).jsonObject
        // Release notes (the GitHub release `body`) — surfaced in the Settings card.
        val notes = root["body"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val assets = root["assets"]?.jsonArray ?: return CheckResult.UpToDate
        var best: Update? = null
        for (a in assets) {
            val o = a.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val run = Regex("pauta-native-v(\\d+)\\.apk").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val url = o["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: continue
            if (best == null || run > best!!.run) best = Update(run, url, notes)
        }
        val newer = best?.takeIf { it.run > BuildConfig.BUILD_RUN }
        return if (newer != null) CheckResult.Available(newer) else CheckResult.UpToDate
    }

    /** Download the release APK to the cache; reports progress (0–100) via [onProgress].
     *  GitHub release assets redirect (302) to a CDN host. HttpURLConnection's built-in
     *  redirect following can silently produce 0-byte files on some Android versions when
     *  the redirect crosses hosts, so we follow the redirect chain manually — same
     *  approach as the Capacitor AppUpdaterPlugin. */
    suspend fun download(context: Context, url: String, onProgress: (Int) -> Unit = {}): File? =
        withContext(Dispatchers.IO) {
            // Same 2s/4s/8s retry as the check — a flaky CDN hop shouldn't strand
            // the user on "couldn't download" when a second try would have worked.
            // Each attempt re-deletes the partial file, so retries start clean.
            withRetry { downloadOnce(context, url, onProgress) }
        }

    /** One download attempt: follows the redirect chain to the CDN and streams the
     *  APK to cache, throwing on any failure so [withRetry] can back off. */
    private fun downloadOnce(context: Context, url: String, onProgress: (Int) -> Unit): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "pauta-update.apk")
        if (file.exists()) file.delete()

        var current = url
        var redirects = 0
        val conn: HttpURLConnection
        while (true) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = c.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc.isNullOrBlank()) throw RuntimeException("redirect without Location")
                current = loc
                redirects++
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                c.disconnect()
                throw RuntimeException("HTTP $code")
            }
            conn = c
            break
        }

        val total = conn.contentLength.toLong()
        try {
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPct = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read == -1) break
                        output.write(buf, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val pct = (downloaded * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        return file
    }

    /** Hand the APK to the system installer (in-place upgrade). */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    /** Whether the OS will let us install (Android 8+ gates "unknown apps"). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Open the system "install unknown apps" toggle for this app so the user can
     *  allow it and tap download again. Without the grant the installer intent
     *  silently does nothing — exactly why the button could look like it did
     *  nothing on a fresh install. // PT: abre a definição "instalar apps
     *  desconhecidas" desta app. */
    fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
