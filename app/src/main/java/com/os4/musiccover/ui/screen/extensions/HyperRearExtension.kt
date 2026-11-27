package com.os4.musiccover.ui.screen.extensions

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.os4.musiccover.HyperCanvasBridge
import com.os4.musiccover.HyperImmersiveSetup
import com.os4.musiccover.HyperImmersiveSetup.Step
import com.os4.musiccover.HyperRearBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.util.PageScaffold
import com.os4.musiccover.updater.HyperRearRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.SwitchPreference

internal val HyperRearInfo = ExtensionInfo("HyperRear", "Alpha", HyperRearRelease,
    R.drawable.ic_extension_hyperrear, R.string.hyperrear_downloading)

/** The rear launcher is back and has loaded a new hook within this; the setup step waits for it. */
private const val REAR_RESTART_MS = 10_000L

/**
 * HyperRear as the Extensions tab knows it: its state, read every couple of seconds, and its
 * setup. Its steps are HyperImmersive's, with the rear launcher in place of System UI and no
 * permissions of its own to grant.
 */
@Stable
internal class HyperRearModel(private val context: Context, private val scope: CoroutineScope) {
    val release = ReleaseState(HyperRearInfo)
    var state by mutableStateOf(Bundle())
        private set
    private var verifyUntil by mutableLongStateOf(0L)
    private var now by mutableLongStateOf(SystemClock.elapsedRealtime())
    private var manualScope by mutableStateOf(false)
    private var returningFromLsposed = false
    private var setupMessage by mutableStateOf("")
    private var setupBusy by mutableStateOf(false)
    /** Tries to wake it since the tab was last opened. */
    var wakes = 0

    private val hook get() = if (state.containsKey("hookActive")) state.getBoolean("hookActive") else null
    private val scopeState get() = state.getString("scopeState")
    private val step get() = HyperImmersiveSetup.step(state.getBoolean("installed"), scopeState, hook,
        true, manualScope, now < verifyUntil)

    val canOpen get() = release.installedVersion != null && step == Step.READY
    val status get() = when {
        !canOpen -> ExtensionStatus.SETUP
        state.getBoolean("enabled") -> ExtensionStatus.RUNNING
        else -> ExtensionStatus.OFF
    }

    val cardSetup: CardSetup get() {
        val current = step
        val ready = when {
            release.installedVersion == null || current == Step.CHECKING -> null
            else -> current == Step.READY
        }
        return CardSetup(ready, busy = setupBusy || current == Step.APPROVAL || current == Step.VERIFY,
            run = if (ready == false) ::runSetupStep else null,
            label = if (current == Step.RESTART) R.string.extension_restart_scopes else R.string.extension_set_up,
            message = setupMessage.ifEmpty {
                if (current == Step.REQUEST_SCOPE && scopeState == "approval-failed") {
                    context.getString(R.string.extension_setup_scope_failed)
                } else ""
            },
            hint = when (current) {
                Step.ENABLE -> R.string.hyperrear_setup_permissions_summary
                Step.REQUEST_SCOPE -> R.string.hyperrear_setup_request_scope
                Step.APPROVAL -> R.string.hyperrear_setup_scope_pending
                Step.RESTART -> R.string.hyperrear_setup_restart_summary
                Step.VERIFY -> R.string.extension_setup_verifying
                else -> null
            })
    }

    private fun runSetupStep() {
        val current = step
        runSetupAction {
            when (current) {
                Step.ENABLE -> {
                    returningFromLsposed = true
                    if (!HyperCanvasBridge.openLsposed(context, HyperRearRelease.PACKAGE)) {
                        returningFromLsposed = false
                        setupMessage = context.getString(R.string.hyperrear_setup_open_failed)
                    }
                }
                Step.REQUEST_SCOPE -> {
                    val answer = HyperRearBridge.requestScope(context)
                    if (answer.isEmpty) setupMessage = context.getString(R.string.extension_setup_scope_failed)
                    else accept(answer)
                }
                Step.RESTART -> if (extensionRestartHasRoot(context)) {
                    manualScope = false
                    now = SystemClock.elapsedRealtime()
                    verifyUntil = now + REAR_RESTART_MS
                    if (!HyperRearBridge.restartHost()) {
                        verifyUntil = 0L
                        setupMessage = context.getString(R.string.hyperrear_setup_restart_failed)
                    }
                }
                else -> Unit
            }
        }
    }

    fun onResume() {
        if (!returningFromLsposed) return
        scope.launch {
            // The launch call can still be returning when Android dispatches ON_RESUME.
            while (setupBusy) delay(50L)
            if (!returningFromLsposed) return@launch
            returningFromLsposed = false
            runSetupAction {
                accept(HyperRearBridge.query(context))
                if (scopeState == "enable-module" && hook != true) {
                    HyperRearBridge.refreshActivation()
                    accept(HyperRearBridge.query(context))
                }
                manualScope = state.getBoolean("installed") && scopeState == null
            }
        }
    }

    private fun runSetupAction(action: suspend () -> Unit) {
        if (setupBusy) return
        setupBusy = true
        setupMessage = ""
        scope.launch {
            try { action() } finally { setupBusy = false }
        }
    }

    suspend fun poll() {
        if (setupBusy) return
        var answer = HyperRearBridge.query(context)
        // Installed but silent: HyperOS will not let this app start it. A few tries, as for HyperImmersive.
        if (answer.isEmpty && wakes < 5 && HyperRearRelease.installedVersion(context) != null) {
            wakes++
            HyperRearBridge.wake()
            delay(500L)
            answer = HyperRearBridge.query(context)
        }
        if (setupBusy) return
        accept(answer)
        now = SystemClock.elapsedRealtime()
        if (verifyUntil != 0L && (hook == true || now >= verifyUntil)) {
            verifyUntil = 0L
            if (hook != true) setupMessage = context.getString(R.string.hyperrear_setup_hook_missing)
        }
    }

    private fun accept(answer: Bundle) {
        val previous = release.installedVersion
        release.refreshLocal(context)
        if (release.installedVersion != previous || release.installedVersion == null) {
            manualScope = false
            verifyUntil = 0L
            setupMessage = ""
            state = Bundle()
        }
        if (release.installedVersion != null && !answer.isEmpty) state = answer
    }

    fun set(key: String, on: Boolean) {
        state = Bundle(state).apply { putBoolean(key, on) }
        scope.launch {
            HyperRearBridge.configure(context, key, on).takeIf { it.size() > 0 }?.let { state = it }
            // The card is built once; a new one shows the change now.
            if (key == "enabled") HyperRearBridge.restartHost()
        }
    }
}

@Composable
internal fun HyperRearUpdates(model: HyperRearModel, isCurrent: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model, isCurrent) {
        val observer = LifecycleEventObserver { _, event ->
            if (isCurrent && event == Lifecycle.Event.ON_RESUME) model.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isCurrent) {
        model.wakes = 0
        if (isCurrent) while (true) {
            model.poll()
            delay(2000L)
        }
    }
}

@Composable
internal fun HyperRearPage(model: HyperRearModel, isBlurEnabled: Boolean, extraBottomPadding: Dp, onBack: () -> Unit) {
    val state = model.state
    val enabled = state.getBoolean("enabled")
    LaunchedEffect(model.release.installedVersion) { if (model.release.installedVersion == null) onBack() }
    PageScaffold(title = HyperRearInfo.name, isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding, onBack = onBack,
        topGlow = rememberExtensionGlow(HyperRearInfo, model.release.installedVersion)) {
        item { ExtensionHeader(HyperRearInfo, state.getString("version") ?: model.release.installedVersion, model.status) }
        item {
            ExtensionGroup(null) {
                SwitchPreference(title = stringResource(R.string.hyperrear_title),
                    summary = stringResource(R.string.hyperrear_summary),
                    checked = enabled, onCheckedChange = { model.set("enabled", it) })
                SwitchPreference(title = stringResource(R.string.hyperrear_volume),
                    summary = stringResource(R.string.hyperrear_volume_summary),
                    checked = state.getBoolean("volumeRing"), enabled = enabled,
                    onCheckedChange = { model.set("volumeRing", it) })
            }
        }
        item {
            ExtensionManageGroup(HyperRearInfo, model.release, R.string.hyperrear_uninstall_summary,
                R.string.hyperrear_removed, R.string.hyperrear_removed_restart_failed,
                R.string.hyperrear_remove_failed, uninstall = { c -> HyperRearBridge.uninstall(c) },
                onRemoved = onBack)
        }
    }
}
