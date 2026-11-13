package com.os4.musiccover

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One file a user can attach to a report, so nobody has to find LSPosed's log directory.
 *
 * Module output reaches LSPosed's own files, not logcat (logd keeps nothing under error on these
 * phones), so the module log is read from there, previous boot first. Only lines from our own
 * packages are kept: other modules' lines in the same file can carry the user's private data.
 * The crash buffer is taken whole, since a SystemUI or app crash is what most reports need.
 */
object LogExport {
    private val SCRIPT = """
        echo "== device"
        getprop ro.product.model; getprop ro.mi.os.version.incremental; getprop ro.build.version.incremental
        echo "== packages"
        for p in com.yzc26623.HyperMusicCoverEnhanced com.yzc26623.HyperImmersive com.yzc26623.HyperCanvas; do
          echo "${'$'}p ${'$'}(dumpsys package ${'$'}p 2>/dev/null | grep -m1 versionName)"
        done
        echo "== module log"
        for f in ${'$'}(ls -tr /data/adb/lspd/log.old/modules_* /data/adb/lspd/log/modules_* 2>/dev/null); do
          grep -F com.yzc26623.Hyper "${'$'}f"
        done | tail -n 20000
        echo "== crash"
        logcat -d -b crash
        exit 0
    """.trimIndent()

    /** False when root was refused or nothing was written. */
    suspend fun write(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            process.outputStream.use { it.write((SCRIPT + "\n").toByteArray()) }
            val bytes = context.contentResolver.openOutputStream(uri)?.use { out ->
                process.inputStream.use { it.copyTo(out) }
            } ?: 0L
            process.waitFor() == 0 && bytes > 0
        }.getOrDefault(false)
    }
}
