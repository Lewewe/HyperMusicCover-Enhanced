package com.os4.musiccover

import android.content.Context
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Optional extension IPC; no Canvas implementation is loaded into the host app. */
internal object HyperCanvasBridge {
    /** Fixed package only; a failed uninstall must never restart the user's players. */
    fun uninstall(context: Context): Int = try {
        val process = ProcessBuilder("su", "-c", "pm uninstall com.yzc26623.HyperCanvas")
            .redirectErrorStream(true).start()
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            0
        } else if (process.exitValue() != 0 || !process.inputStream.bufferedReader().readText().contains("Success")) {
            0
        } else {
            context.getSharedPreferences("extension_downloads", 0).edit().remove("com.yzc26623.HyperCanvas").commit()
            if (ModuleBridge.restartCanvasScopes()) 2 else 1
        }
    } catch (_: Exception) { 0 }

    /** LSPosed supplies its service when the module app starts, so rebind after enabling it. */
    suspend fun refreshActivation(context: Context): Boolean {
        val user = android.os.Process.myUid() / 100000
        val stopped = withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder("su", "-c", "am force-stop --user $user com.yzc26623.HyperCanvas")
                    .redirectErrorStream(true).start()
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else process.exitValue() == 0
            }.getOrDefault(false)
        }
        if (!stopped) return false
        // Start the application in the background so LSPosed can supply its service.
        // No activity launch means HyperOS does not need cross-app launch approval.
        return withContext(Dispatchers.IO) {
            runCatching {
                val process = ProcessBuilder("su", "-c", "am broadcast --user $user --include-stopped-packages " +
                    "-n com.yzc26623.HyperCanvas/com.os4.canvas.BridgeReceiver " +
                    "-a com.os4.canvas.REQUEST_BRIDGE").redirectErrorStream(true).start()
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    false
                } else process.exitValue() == 0
            }.getOrDefault(false)
        }
    }

    /** Open LSPosed without changing module activation or scopes. */
    suspend fun openLsposed(context: Context): Boolean = withContext(Dispatchers.IO) {
        val user = android.os.Process.myUid() / 100000
        val data = "module://com.yzc26623.HyperCanvas:$user"
        val standalone = context.packageManager.getLaunchIntentForPackage("org.lsposed.manager")
        if (standalone != null && runCatching {
            context.startActivity(standalone.setData(Uri.parse(data))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess) return@withContext true
        runCatching {
            // Use LSPosed's own module-action entry point. Starting a shell activity directly
            // can open Android's bug-report dialog when the manager is not injected yet.
            val process = ProcessBuilder("su", "-c", "am broadcast --user $user " +
                "-a android.telephony.action.SECRET_CODE " +
                "-d android_secret_code://5776733 android").redirectErrorStream(true).start()
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                val output = process.inputStream.bufferedReader().readText()
                process.exitValue() == 0 && !output.contains("Error") && !output.contains("Exception")
            }
        }.getOrDefault(false)
    }

    private val uri = Uri.parse("content://com.yzc26623.HyperCanvas.bridge")
    suspend fun query(context: Context): Bundle = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.call(uri, "query", null, null) }
            .onFailure { android.util.Log.w("HyperCanvasBridge", "Extension query failed", it) }.getOrNull() ?: Bundle()
    }
    suspend fun requestScopes(context: Context): Bundle = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.call(uri, "requestScopes", null, null) }.getOrNull() ?: query(context)
    }

    suspend fun prepareRestart(context: Context): Bundle = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.call(uri, "prepareRestart", null, null) }.getOrNull() ?: Bundle()
    }

    /** No provider or restart is needed on devices without the optional APK. */
    suspend fun synchronizeBlock(context: Context, blocked: Boolean): Bundle = withContext(Dispatchers.IO) {
        val current = query(context)
        if (!current.getBoolean("installed") || current.getBoolean("hostBlocked") == blocked)
            return@withContext current
        val result = runCatching {
            context.contentResolver.call(uri, "block", null, Bundle().apply { putBoolean("hostBlocked", blocked) })
        }.getOrNull() ?: return@withContext current
        if (result.getBoolean("hostBlocked") == blocked) ModuleBridge.restartCanvasScopes()
        result
    }
    suspend fun configure(context: Context, data: Bundle): Bundle = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.call(uri, "configure", null, data) }.getOrNull() ?: Bundle()
    }
}
