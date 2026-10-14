package com.os4.musiccover

/** Installation is not activation: the restarted SystemUI must confirm this APK. */
internal object HyperCanvasSetup {
    enum class Stage { MISSING, CHECKING, PERMISSIONS, RESTART, VERIFYING, READY }

    fun stage(installed: Boolean, protocol: Int, scopesGranted: Boolean, ready: Boolean,
              startedAt: Long, now: Long, responding: Boolean = true, legacyActive: Boolean = false): Stage = when {
        !installed -> Stage.MISSING
        !responding -> Stage.CHECKING
        protocol < 1 -> when {
            legacyActive -> Stage.READY
            scopesGranted -> Stage.RESTART
            else -> Stage.PERMISSIONS
        }
        !scopesGranted -> Stage.PERMISSIONS
        ready -> Stage.READY
        startedAt > 0L && now >= startedAt && now - startedAt < 45_000L -> Stage.VERIFYING
        else -> Stage.RESTART
    }
}
