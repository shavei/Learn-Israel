package com.shavei.mapa.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updating the app from inside it, from the same GitHub release the README links to. This
 * is the app's only use of the internet, and only when the player asks for it in settings:
 * nothing is sent, and nothing is checked in the background. The release workflow
 * publishes `version.txt` (the build's version code) next to `mapa.apk`.
 */
object Updates {
    private const val LATEST = "https://github.com/shavei/Learn-Israel/releases/latest/download"
    const val VERSION_URL = "$LATEST/version.txt"
    const val APK_URL = "$LATEST/mapa.apk"

    fun installedVersion(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    /** The version code in a published `version.txt`, null if it is not one. */
    fun parseVersion(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it > 0 }

    /** The published build's version code. Throws when there is no connection or no answer. */
    suspend fun latestVersion(): Long = withContext(Dispatchers.IO) {
        val text = open(VERSION_URL).run { inputStream.bufferedReader().use { it.readText() } }
        parseVersion(text) ?: error("Not a version code: $text")
    }

    /** Downloads the published APK into the app's cache; [onProgress] gets the bytes so far and the size, 0 when unknown. */
    suspend fun download(context: Context, onProgress: (done: Long, size: Long) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val part = File(dir, "mapa.apk.part")
        val apk = File(dir, "mapa.apk")
        val connection = open(APK_URL)
        val size = connection.contentLengthLong
        connection.inputStream.use { input ->
            part.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    onProgress(done, size.coerceAtLeast(0))
                }
            }
        }
        // Only a whole download is offered to the installer.
        if (size > 0 && part.length() != size) error("Download cut off at ${part.length()} of $size bytes")
        if (!part.renameTo(apk)) error("Could not keep the download")
        apk
    }

    /**
     * Hands the APK to Android's installer, which asks the player to confirm. The first time,
     * Android asks to allow installing apps from Al HaMapa, and offers its settings for that.
     */
    fun install(context: Context, apk: File) {
        context.startActivity(installIntent(context, apk))
    }

    /** Android's installer, opened on [apk]. */
    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            // GitHub sends release files from another host; never a cached answer.
            useCaches = false
            if (responseCode !in 200..299) error("HTTP $responseCode for $url")
        }
}
