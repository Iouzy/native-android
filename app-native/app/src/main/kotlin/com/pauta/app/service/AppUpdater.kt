package com.pauta.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.pauta.app.BuildConfig
import kotlinx.coroutines.Dispatchers
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

    data class Update(val run: Int, val url: String)

    /** Returns a newer release than the installed build, or null. */
    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://api.github.com/repos/$REPO/releases/tags/latest-native").openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000; readTimeout = 10_000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val assets = json.parseToJsonElement(text).jsonObject["assets"]?.jsonArray ?: return@runCatching null
            var best: Update? = null
            for (a in assets) {
                val o = a.jsonObject
                val name = o["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val run = Regex("pauta-native-v(\\d+)\\.apk").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val url = o["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: continue
                if (best == null || run > best!!.run) best = Update(run, url)
            }
            best?.takeIf { it.run > BuildConfig.BUILD_RUN }
        }.getOrNull()
    }

    /** Download the release APK to the cache. */
    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "pauta-update.apk")
            (URL(url).openConnection() as HttpURLConnection).inputStream.use { input ->
                file.outputStream().use { out -> input.copyTo(out) }
            }
            file
        }.getOrNull()
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
}
