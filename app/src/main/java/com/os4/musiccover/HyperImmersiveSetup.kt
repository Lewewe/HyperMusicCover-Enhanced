package com.os4.musiccover

internal object HyperImmersiveSetup {
    enum class Step { CHECKING, ENABLE, REQUEST_SCOPE, APPROVAL, RESTART, VERIFY, GRANT, READY }

    fun step(responding: Boolean, scopeState: String?, hook: Boolean?, permissions: Boolean?,
             manualScope: Boolean, verifying: Boolean): Step = when {
        !responding -> Step.CHECKING
        scopeState == "awaiting-approval" -> Step.APPROVAL
        scopeState == "request-scopes" || scopeState == "approval-failed" -> Step.REQUEST_SCOPE
        hook == true -> when (permissions) {
            true -> Step.READY
            false -> Step.GRANT
            null -> Step.CHECKING
        }
        verifying -> Step.VERIFY
        scopeState == "ready" || (scopeState == null && manualScope) -> Step.RESTART
        else -> Step.ENABLE
    }
}
