package com.os4.musiccover

import com.os4.musiccover.HyperImmersiveSetup.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class HyperImmersiveSetupTest {
    @Test fun freshInstallWaitsForEachConfirmation() {
        assertEquals(Step.CHECKING, step(responding = false))
        assertEquals(Step.ENABLE, step(scope = "enable-module"))
        assertEquals(Step.REQUEST_SCOPE, step(scope = "request-scopes"))
        assertEquals(Step.APPROVAL, step(scope = "awaiting-approval"))
        assertEquals(Step.REQUEST_SCOPE, step(scope = "approval-failed"))
        assertEquals(Step.RESTART, step(scope = "ready"))
        assertEquals(Step.VERIFY, step(scope = "ready", hook = null, verifying = true))
        assertEquals(Step.GRANT, step(scope = "ready", hook = true))
        assertEquals(Step.READY, step(scope = "ready", hook = true, permissions = true))
        assertEquals(Step.REQUEST_SCOPE, step(scope = "request-scopes", hook = true, permissions = true))
    }

    @Test fun alreadyRunningAndLegacyExtensionsRemainUsable() {
        assertEquals(Step.ENABLE, step())
        assertEquals(Step.RESTART, step(manual = true))
        assertEquals(Step.VERIFY, step(verifying = true))
        assertEquals(Step.READY, step(hook = true, permissions = true))
        assertEquals(Step.READY, step(scope = "enable-module", hook = true, permissions = true))
    }

    private fun step(responding: Boolean = true, scope: String? = null, hook: Boolean? = false,
                     permissions: Boolean? = false, manual: Boolean = false, verifying: Boolean = false) =
        HyperImmersiveSetup.step(responding, scope, hook, permissions, manual, verifying)
}
