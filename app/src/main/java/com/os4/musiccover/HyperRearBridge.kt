package com.os4.musiccover

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The optional rear screen extension (HyperRear), an LSPosed module in the rear screen's launcher
 * with no screen of its own: its settings live on the Extensions page and reach it through its
 * provider. An empty bundle means it is not installed, or not signed like this app.
 */
internal object HyperRearBridge {
    const val PACKAGE = "com.yzc26623.HyperRear"
    const val HOST = "com.xiaomi.subscreencenter"
    private val uri = Uri.parse("content://$PACKAGE.bridge")

    private suspend fun call(context: Context, method: String, extras: Bundle? = null): Bundle =
        withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.call(uri, method, null, extras) }.getOrNull() ?: Bundle()
        }

    suspend fun query(context: Context) = call(context, "query")
    suspend fun requestScope(context: Context) = call(context, "requestScopes")
    suspend fun configure(context: Context, key: String, on: Boolean) =
        call(context, "configure", Bundle().apply { putBoolean(key, on) })

    private suspend fun su(command: String, seconds: Long = 10): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            if (!process.waitFor(seconds, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else process.exitValue() == 0
        }.getOrDefault(false)
    }

    private val user get() = android.os.Process.myUid() / 100000

    /** Let root start its provider when HyperOS blocks an ordinary cross-app cold start. */
    suspend fun wake() = su("content call --user $user --uri $uri --method wake")

    /** Reopen the extension process so LSPosed can deliver its service after activation. */
    suspend fun refreshActivation() = su("am force-stop --user $user $PACKAGE") && wake()

    /** The rear screen's launcher comes straight back, with the hook LSPosed now gives it. */
    suspend fun restartHost() = su("killall $HOST")

    /** Root: 2 when it is gone and the rear launcher has dropped its hook, 1 if only the first, 0 otherwise. */
    fun uninstall(context: Context): Int = try {
        val process = ProcessBuilder("su", "-c", "pm uninstall $PACKAGE").redirectErrorStream(true).start()
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            0
        } else if (process.exitValue() != 0 || !process.inputStream.bufferedReader().readText().contains("Success")) {
            0
        } else {
            context.getSharedPreferences("extension_downloads", 0).edit().remove(PACKAGE).commit()
            val restarted = runCatching {
                ProcessBuilder("su", "-c", "killall $HOST").start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
            }.getOrDefault(false)
            if (restarted) 2 else 1
        }
    } catch (_: Exception) { 0 }
}
