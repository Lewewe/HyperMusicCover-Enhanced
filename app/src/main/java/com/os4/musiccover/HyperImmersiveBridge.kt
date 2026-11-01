package com.os4.musiccover

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The optional StandBy extension (HyperImmersive), which has no screen of its own: its settings
 * live on the Extensions page and reach it through its provider. An empty bundle means it is not
 * installed, or not signed like this app.
 */
internal object HyperImmersiveBridge {
    const val PACKAGE = "com.yzc26623.HyperImmersive"
    private val uri = Uri.parse("content://$PACKAGE.bridge")

    private suspend fun call(context: Context, method: String, extras: Bundle? = null): Bundle =
        withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.call(uri, method, null, extras) }.getOrNull() ?: Bundle()
        }

    suspend fun query(context: Context) = call(context, "query")
    suspend fun configure(context: Context, key: String, on: Boolean) =
        call(context, "configure", Bundle().apply { putBoolean(key, on) })
    suspend fun preview(context: Context) = call(context, "preview")

    /**
     * What StandBy needs and cannot ask for without a screen: notification access (the
     * artwork), opening from the background, and its watcher's notification. Root only.
     */
    suspend fun grant(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val user = android.os.Process.myUid() / 100000
            val process = ProcessBuilder("su", "-c",
                "cmd notification allow_listener $PACKAGE/com.os4.immersive.MediaListener $user; " +
                "appops set --user $user $PACKAGE SYSTEM_ALERT_WINDOW allow; " +
                "pm grant --user $user $PACKAGE android.permission.POST_NOTIFICATIONS")
                .redirectErrorStream(true).start()
            if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0
        }.getOrDefault(false)
    }
}
