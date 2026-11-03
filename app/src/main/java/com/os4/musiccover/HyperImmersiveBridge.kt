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
    suspend fun configure(context: Context, key: String, value: Int) =
        call(context, "configure", Bundle().apply { putInt(key, value) })
    suspend fun preview(context: Context) = call(context, "preview")

    /**
     * Start its process and watcher through its receiver. HyperOS rejects this app starting a
     * stopped one through the provider (WakePathChecker), and after an install it is stopped.
     */
    suspend fun wake(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val user = android.os.Process.myUid() / 100000
            val process = ProcessBuilder("su", "-c", "am broadcast --user $user --include-stopped-packages " +
                "-n $PACKAGE/com.os4.immersive.BootReceiver -a android.intent.action.MY_PACKAGE_REPLACED")
                .redirectErrorStream(true).start()
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0
        }.getOrDefault(false)
    }

    /** Root: 2 when it is gone and System UI has dropped its hook, 1 if only the first, 0 otherwise. */
    fun uninstall(context: Context): Int = try {
        val process = ProcessBuilder("su", "-c", "pm uninstall $PACKAGE").redirectErrorStream(true).start()
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            0
        } else if (process.exitValue() != 0 || !process.inputStream.bufferedReader().readText().contains("Success")) {
            0
        } else {
            context.getSharedPreferences("extension_downloads", 0).edit().remove(PACKAGE).commit()
            if (ModuleBridge.restartSystemUi()) 2 else 1
        }
    } catch (_: Exception) { 0 }

    /**
     * What StandBy needs and cannot ask for without a screen: notification access (the
     * artwork), opening from the background, its watcher's notification, the calendar (its 4x2
     * widget's events), and taking the lock screen. HyperOS can set that last one to ignore when
     * an update adds a permission; 10020 and 10021 are its own show-when-locked and
     * background-start ops. Root only.
     */
    suspend fun grant(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val user = android.os.Process.myUid() / 100000
            val process = ProcessBuilder("su", "-c",
                "cmd notification allow_listener $PACKAGE/com.os4.immersive.MediaListener $user; " +
                "appops set --user $user $PACKAGE SYSTEM_ALERT_WINDOW allow; " +
                "appops set --user $user --uid $PACKAGE USE_FULL_SCREEN_INTENT allow; " +
                "appops set --user $user $PACKAGE USE_FULL_SCREEN_INTENT allow; " +
                "appops set --user $user $PACKAGE 10020 allow; " +
                "appops set --user $user $PACKAGE 10021 allow; " +
                "pm grant --user $user $PACKAGE android.permission.POST_NOTIFICATIONS; " +
                "pm grant --user $user $PACKAGE android.permission.READ_CALENDAR")
                .redirectErrorStream(true).start()
            if (!process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0
        }.getOrDefault(false)
    }
}
