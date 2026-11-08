package com.os4.musiccover.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.os4.musiccover.LsposedService
import com.os4.musiccover.ModuleBridge
import kotlin.concurrent.thread

/** Restarts the whole scope after every package replacement, regardless of installer. */
class UpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                // Without LSPosed's answer restartScope kills every shipped scope package, enabled
                // or not, so it gets a moment to bind in this fresh process.
                LsposedService.init(app)
                val deadline = SystemClock.elapsedRealtime() + BIND_WAIT_MS
                while (LsposedService.service.value == null && SystemClock.elapsedRealtime() < deadline) {
                    Thread.sleep(100)
                }
                ModuleBridge.restartScope(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val BIND_WAIT_MS = 3_000L
    }
}
