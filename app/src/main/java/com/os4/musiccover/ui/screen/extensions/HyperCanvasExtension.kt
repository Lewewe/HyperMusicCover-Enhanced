package com.os4.musiccover.ui.screen.extensions

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.os4.musiccover.HyperCanvasBridge
import com.os4.musiccover.HyperCanvasSetup
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.screen.features.ValueSlider
import com.os4.musiccover.ui.util.PageScaffold
import com.os4.musiccover.updater.HyperCanvasRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

internal val HyperCanvasInfo = ExtensionInfo("HyperCanvas", "Beta", HyperCanvasRelease,
    R.drawable.ic_extension_hypercanvas, R.string.hypercanvas_downloading, needs = "com.spotify.music" to "Spotify")

/** Coming back, the extension's process may be new, and LSPosed takes a moment to reach it. */
private const val CANVAS_SETTLE_MS = 4_000L

/** Older extensions expose hook receipts and their live LSPosed connection state. */
private fun legacyCanvasActive(state: Bundle): Boolean = state.getString("scopeState") == "ready" &&
    state.getLong("systemSeen") > 0L

private fun canvasScopesGranted(state: Bundle): Boolean = state.getBoolean("scopesGranted") ||
    (state.getInt("setupProtocol") < 1 && state.getString("scopeState") == "ready")

/** HyperCanvas as the Extensions tab knows it: its state, read again while the tab is shown, and its setup. */
@Stable
internal class HyperCanvasModel(private val context: Context, private val resources: Resources,
    private val scope: CoroutineScope) {
    val release = ReleaseState(HyperCanvasInfo)
    var state by mutableStateOf(Bundle())
    var hostBlocked by mutableStateOf(false)
    var setupBusy by mutableStateOf(false)
    var setupMessage by mutableStateOf("")
    var restartFailed by mutableStateOf(false)
    var returningFromLsposed = false
    var settlingUntil by mutableLongStateOf(System.currentTimeMillis() + CANVAS_SETTLE_MS)
    var setupNow by mutableLongStateOf(System.currentTimeMillis())
    var iconRevision = 0L

    val blocked get() = hostBlocked || state.getBoolean("hostBlocked")
    val installed get() = state.getBoolean("installed")
    val enabled get() = state.getBoolean("enabled")

    val stage: HyperCanvasSetup.Stage get() = HyperCanvasSetup.stage(release.installedVersion != null,
        state.getInt("setupProtocol"), canvasScopesGranted(state), state.getBoolean("setupReady"),
        if (restartFailed) 0L else state.getLong("setupStartedAt"), setupNow, installed, legacyCanvasActive(state)).let {
        val unknown = it == HyperCanvasSetup.Stage.CHECKING ||
            (it == HyperCanvasSetup.Stage.PERMISSIONS && !state.getBoolean("frameworkConnected"))
        when {
            unknown && setupNow < settlingUntil -> HyperCanvasSetup.Stage.CHECKING
            // No answer for this long: the request is the one thing left to offer.
            it == HyperCanvasSetup.Stage.CHECKING -> HyperCanvasSetup.Stage.PERMISSIONS
            else -> it
        }
    }

    val canOpen get() = stage == HyperCanvasSetup.Stage.READY && !setupBusy
    private val awaitingApproval get() = !canvasScopesGranted(state) &&
        state.getString("scopeState") == "awaiting-approval"

    /** What its card shows: ready, or the setup step its button takes. */
    val cardSetup: CardSetup get() = stage.let { s ->
        CardSetup(ready = when (s) {
            HyperCanvasSetup.Stage.READY -> true
            HyperCanvasSetup.Stage.CHECKING -> null
            else -> false
        }, busy = setupBusy || awaitingApproval || s == HyperCanvasSetup.Stage.VERIFYING,
            run = if (s == HyperCanvasSetup.Stage.PERMISSIONS || s == HyperCanvasSetup.Stage.RESTART) ::runSetupStep else null,
            label = if (s == HyperCanvasSetup.Stage.RESTART) R.string.extension_restart_scopes else R.string.extension_set_up,
            message = setupMessage.ifEmpty {
                if (s == HyperCanvasSetup.Stage.PERMISSIONS && state.getString("scopeState") == "approval-failed") {
                    resources.getString(R.string.extension_setup_scope_failed)
                } else ""
            },
            hint = when (s) {
                HyperCanvasSetup.Stage.PERMISSIONS -> when {
                    awaitingApproval -> R.string.hypercanvas_approval
                    !state.getBoolean("frameworkConnected") -> R.string.hypercanvas_enable_module
                    else -> R.string.hypercanvas_setup_request_scope
                }
                HyperCanvasSetup.Stage.RESTART -> R.string.hypercanvas_restart
                HyperCanvasSetup.Stage.VERIFYING -> R.string.extension_setup_verifying
                else -> null
            })
    }

    /**
     * Whether its scopes (Spotify and System UI) are granted; null while that is not known.
     * shortcut: one answer for both scopes, as the extension reports them; split if it ever reports each.
     */
    val scoped: Boolean? get() = if (release.installedVersion == null || !state.containsKey("installed")) null
        else canvasScopesGranted(state)

    val status get() = when {
        release.installedVersion == null || stage != HyperCanvasSetup.Stage.READY || blocked -> ExtensionStatus.SETUP
        enabled -> ExtensionStatus.RUNNING
        else -> ExtensionStatus.OFF
    }

    /** Why it is not running, when that is not only that it is off. */
    val problem: Int? get() = when {
        blocked -> R.string.hypercanvas_background_blocked
        !installed -> R.string.hypercanvas_missing
        state.getString("scopeState") == "awaiting-approval" -> R.string.hypercanvas_approval
        state.getString("scopeState") == "enable-module" -> R.string.hypercanvas_enable_module
        state.getString("scopeState") == "restart-scopes" -> R.string.hypercanvas_restart
        state.getString("scopeState") == "approval-failed" -> R.string.hypercanvas_scope_failed
        state.getLong("systemSeen") == 0L -> R.string.hypercanvas_restart
        else -> null
    }

    fun push(key: String, value: Int) {
        if (stage != HyperCanvasSetup.Stage.READY) return
        val updated = Bundle(state)
        if (key == "enabled" || key == "showInPill") updated.putBoolean(key, value != 0) else updated.putInt(key, value)
        state = updated
        scope.launch { state = HyperCanvasBridge.configure(context, updated) }
    }

    /** Back from LSPosed, or installed without its scopes yet: ask again, giving it a moment. */
    fun onResume() {
        settlingUntil = System.currentTimeMillis() + CANVAS_SETTLE_MS
        if (setupBusy || awaitingApproval ||
            !(returningFromLsposed || (release.installedVersion != null && !canvasScopesGranted(state)))) return
        returningFromLsposed = false
        scope.launch {
            setupBusy = true
            try {
                if (HyperCanvasBridge.refreshActivation(context)) {
                    repeat(6) {
                        delay(300L)
                        val result = HyperCanvasBridge.query(context)
                        state = result
                        if (result.getBoolean("frameworkConnected")) return@launch
                    }
                } else state = HyperCanvasBridge.query(context)
            } finally { setupBusy = false }
        }
    }

    suspend fun poll() {
        val host = ModuleBridge.query(context)
        if (host.alive) {
            hostBlocked = host.hidePlayerBackground
            state = HyperCanvasBridge.synchronizeBlock(context, hostBlocked)
        } else state = HyperCanvasBridge.query(context)
        val newIconRevision = runCatching {
            context.packageManager.getPackageInfo(HyperCanvasRelease.PACKAGE, 0).lastUpdateTime
        }.getOrDefault(0L)
        if (newIconRevision != iconRevision && newIconRevision != 0L && !canvasScopesGranted(state) &&
            !setupBusy && !awaitingApproval) {
            setupBusy = true
            try {
                HyperCanvasBridge.refreshActivation(context)
                state = HyperCanvasBridge.query(context)
            } finally { setupBusy = false }
        }
        iconRevision = newIconRevision
        setupNow = System.currentTimeMillis()
        release.refreshLocal(context)
    }

    /** The setup step [stage] offers: ask LSPosed for the scopes, or restart them. */
    fun runSetupStep() {
        if (setupBusy || awaitingApproval) return
        val current = stage
        setupBusy = true
        setupMessage = ""
        scope.launch {
            try {
                if (current == HyperCanvasSetup.Stage.PERMISSIONS || current == HyperCanvasSetup.Stage.CHECKING) {
                    HyperCanvasBridge.refreshActivation(context)
                    delay(700L)
                    state = HyperCanvasBridge.requestScopes(context)
                    if (!state.getBoolean("frameworkConnected") && !canvasScopesGranted(state)) {
                        returningFromLsposed = true
                        if (!HyperCanvasBridge.openLsposed(context)) {
                            returningFromLsposed = false
                            setupMessage = resources.getString(R.string.hyperimmersive_setup_open_failed)
                        }
                    }
                } else {
                    if (!extensionRestartHasRoot(context)) return@launch
                    restartFailed = false
                    val prepared = if (state.getInt("setupProtocol") < 1 && canvasScopesGranted(state)) {
                        Bundle(state).apply {
                            putBoolean("scopesGranted", true)
                            putLong("setupStartedAt", System.currentTimeMillis())
                        }
                    } else HyperCanvasBridge.prepareRestart(context)
                    state = prepared
                    setupNow = System.currentTimeMillis()
                    if (prepared.getBoolean("scopesGranted") && prepared.getLong("setupStartedAt") > 0L) {
                        val restarted = withContext(Dispatchers.IO + NonCancellable) { ModuleBridge.restartCanvasScopes() }
                        if (!restarted) {
                            restartFailed = true
                            setupMessage = resources.getString(R.string.hypercanvas_setup_restart_failed)
                        }
                    }
                }
            } finally { setupBusy = false }
        }
    }
}

/** Keeps [model] current while the Extensions tab is shown: faster while its setup settles. */
@Composable
internal fun HyperCanvasUpdates(model: HyperCanvasModel, isCurrent: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.onResume() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isCurrent) {
        if (isCurrent) while (true) {
            model.poll()
            delay(if (model.setupNow < model.settlingUntil) 500L else 2500L)
        }
    }
}

@Composable
internal fun HyperCanvasPage(model: HyperCanvasModel, isBlurEnabled: Boolean, extraBottomPadding: Dp, onBack: () -> Unit) {
    val state = model.state
    // Its setup came undone, or it went: back to the list, where the step is.
    LaunchedEffect(model.canOpen, model.stage, model.release.installedVersion) {
        if (model.release.installedVersion == null ||
            (!model.canOpen && model.stage != HyperCanvasSetup.Stage.CHECKING)) onBack()
    }
    val on = model.installed && model.enabled && !model.blocked
    PageScaffold(title = HyperCanvasInfo.name, isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding, onBack = onBack,
        topGlow = rememberExtensionGlow(HyperCanvasInfo, model.release.installedVersion)) {
        item { ExtensionHeader(HyperCanvasInfo, model.release.installedVersion, model.status) }
        item {
            ExtensionGroup(null) {
                SwitchPreference(title = stringResource(R.string.hypercanvas_title),
                    summary = stringResource(R.string.hypercanvas_summary) +
                        (model.problem?.let { "\n" + stringResource(it) } ?: ""),
                    checked = model.enabled, enabled = model.installed && !model.blocked,
                    onCheckedChange = { model.push("enabled", if (it) 1 else 0) })
            }
        }
        item {
            ExtensionGroup(R.string.extension_group_display) {
                WindowDropdownPreference(title = stringResource(R.string.hypercanvas_renderer),
                    summary = stringResource(R.string.hypercanvas_renderer_summary),
                    items = listOf(stringResource(R.string.hypercanvas_renderer_texture),
                        stringResource(R.string.hypercanvas_renderer_gles)),
                    selectedIndex = state.getInt("renderer", 0).coerceIn(0, 1), enabled = on,
                    onSelectedIndexChange = { model.push("renderer", it) })
                SwitchPreference(title = stringResource(R.string.hypercanvas_show_in_pill),
                    summary = stringResource(R.string.hypercanvas_show_in_pill_summary),
                    checked = state.getBoolean("showInPill"), enabled = on,
                    onCheckedChange = { model.push("showInPill", if (it) 1 else 0) })
            }
        }
        item {
            ExtensionGroup(R.string.extension_group_effects) {
                ValueSlider(title = stringResource(R.string.hypercanvas_dim), value = state.getInt("dim", 35).toFloat(),
                    valueRange = 0f..80f, enabled = on, detent = 35f, unit = "%",
                    onValueChange = { model.push("dim", it.toInt()) })
                ValueSlider(title = stringResource(R.string.hypercanvas_cover_blur), value = state.getInt("blur", 18).toFloat(),
                    valueRange = 0f..40f, enabled = on, detent = 18f, onValueChange = { model.push("blur", it.toInt()) })
                ValueSlider(title = stringResource(R.string.hypercanvas_cover_dim), value = state.getInt("coverDim", 45).toFloat(),
                    valueRange = 0f..80f, enabled = on, detent = 45f, unit = "%",
                    onValueChange = { model.push("coverDim", it.toInt()) })
                if (on) Text(stringResource(R.string.hypercanvas_cache, state.getInt("count")), modifier = Modifier.padding(16.dp))
            }
        }
        item {
            ExtensionManageGroup(HyperCanvasInfo, model.release, R.string.hypercanvas_uninstall_summary,
                R.string.hypercanvas_removed, R.string.hypercanvas_removed_restart_failed, R.string.hypercanvas_remove_failed,
                uninstall = { c -> HyperCanvasBridge.uninstall(c) }, onRemoved = onBack)
        }
        item { Text(stringResource(R.string.hypercanvas_note), modifier = Modifier.padding(16.dp)) }
    }
}
