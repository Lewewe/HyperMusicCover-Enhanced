package com.os4.musiccover.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Beta releases are intentionally included for this experimental extension. */
internal object HyperCanvasRelease {
    const val PACKAGE = "com.yzc26623.HyperCanvas"
    data class Release(val version: String, val asset: GithubAsset)
    fun installedVersion(context: android.content.Context): String? = try {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }

    fun downloadedVersion(context: android.content.Context): String? =
        context.getSharedPreferences("extension_downloads", 0).getString(PACKAGE, null)

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val connection = HttpCall("https://api.github.com/repos/Lewewe/HyperCanvas/releases?per_page=20", 15_000)
            .open("application/vnd.github+json")
        try {
            check(connection.responseCode == 200) { "GitHub HTTP ${connection.responseCode}" }
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                if (release.optBoolean("draft")) continue
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.matches(Regex("HyperCanvas-[A-Za-z0-9._-]+\\.apk"))
                        && url.startsWith("https://github.com/Lewewe/HyperCanvas/releases/download/")) {
                        return@withContext Release(release.getString("tag_name").removePrefix("v"), GithubAsset(name, url))
                    }
                }
            }
            error("No HyperCanvas APK is available")
        } finally {
            connection.disconnect()
        }
    }
}
