package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Test

class HyperCanvasSetupTest {
    @Test fun installationAndOldHeartbeatsCannotUnlockSettings() {
        assertEquals(HyperCanvasSetup.Stage.RESTART, HyperCanvasSetup.stage(true, 0, true, true, 0, 1000))
        assertEquals(HyperCanvasSetup.Stage.PERMISSIONS, HyperCanvasSetup.stage(true, 1, false, true, 0, 1000))
        assertEquals(HyperCanvasSetup.Stage.RESTART, HyperCanvasSetup.stage(true, 1, true, false, 0, 1000))
    }
    @Test fun legacyActivationRemainsUsableUntilUpgrade() {
        assertEquals(HyperCanvasSetup.Stage.READY, HyperCanvasSetup.stage(true, 0, false, false, 0, 1000, legacyActive = true))
        assertEquals(HyperCanvasSetup.Stage.RESTART, HyperCanvasSetup.stage(true, 1, true, false, 0, 1000, legacyActive = true))
        assertEquals(HyperCanvasSetup.Stage.CHECKING, HyperCanvasSetup.stage(true, 0, false, false, 0, 1000, responding = false))
    }
    @Test fun waitingAllowsRetryAfterTimeoutButNeverGrantsAccess() {
        assertEquals(HyperCanvasSetup.Stage.VERIFYING, HyperCanvasSetup.stage(true, 1, true, false, 1000, 44999))
        assertEquals(HyperCanvasSetup.Stage.RESTART, HyperCanvasSetup.stage(true, 1, true, false, 1000, 46000))
        assertEquals(HyperCanvasSetup.Stage.RESTART, HyperCanvasSetup.stage(true, 1, true, false, 1000, 999))
    }
    @Test fun confirmedHooksUnlockOnlyWhileInstalledAndScoped() {
        assertEquals(HyperCanvasSetup.Stage.READY, HyperCanvasSetup.stage(true, 1, true, true, 0, 5000))
        assertEquals(HyperCanvasSetup.Stage.MISSING, HyperCanvasSetup.stage(false, 1, true, true, 0, 5000))
        assertEquals(HyperCanvasSetup.Stage.PERMISSIONS, HyperCanvasSetup.stage(true, 1, false, true, 0, 5000))
    }
}
