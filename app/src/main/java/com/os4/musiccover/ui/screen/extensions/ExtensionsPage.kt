package com.os4.musiccover.ui.screen.extensions

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.os.Bundle
import androidx.compose.ui.platform.LocalResources
import com.os4.musiccover.HyperCanvasSetup
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.os4.musiccover.ui.component.DropdownItem
import com.os4.musiccover.ui.component.MenuPopupDefaults
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.os4.musiccover.updater.ExtensionVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.os4.musiccover.updater.ExtensionRelease
import com.os4.musiccover.updater.GithubAsset
import com.os4.musiccover.updater.HyperCanvasRelease
import com.os4.musiccover.updater.HyperImmersiveRelease
import com.os4.musiccover.updater.UpdateInstaller
import com.os4.musiccover.updater.InstallOutcome
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.preference.ArrowPreference
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.os4.musiccover.HyperCanvasBridge
import com.os4.musiccover.HyperImmersiveBridge
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.screen.features.ValueSlider
import com.os4.musiccover.ui.util.PageScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun ExtensionsPageView(isBlurEnabled: Boolean, isCurrent: Boolean, extraBottomPadding: Dp) {
    var selectedExtension by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = isCurrent && selectedExtension != null) { selectedExtension = null }
    AnimatedContent(targetState = selectedExtension == "hyperimmersive", label = "StandBy navigation",
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(280)) { it / 6 * direction } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(280)) { -it / 8 * direction } + fadeOut(tween(160)))
        }) { standby ->
        if (standby) HyperImmersivePage(isBlurEnabled, isCurrent, extraBottomPadding,
            onBack = { selectedExtension = null })
        else HyperCanvasContent(isBlurEnabled, isCurrent, extraBottomPadding,
            showDetails = selectedExtension == "hypercanvas",
            onOpen = { selectedExtension = "hypercanvas" }, onBack = { selectedExtension = null },
            moreExtensions = {
                item { HyperImmersiveCard(isCurrent, onOpen = { selectedExtension = "hyperimmersive" }) }
            })
    }
}

@Composable
private fun HyperCanvasContent(isBlurEnabled: Boolean, isCurrent: Boolean, extraBottomPadding: Dp,
    showDetails: Boolean, onOpen: () -> Unit, onBack: () -> Unit,
    moreExtensions: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(Bundle()) }
    var fetching by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var githubOnly by remember { mutableStateOf(false) }
    var latestRelease by remember { mutableStateOf<ExtensionRelease.Release?>(null) }
    var checking by remember { mutableStateOf(true) }
    var iconRevision by remember { mutableStateOf(0L) }
    val extensionIcon = remember(iconRevision) {
        runCatching { context.packageManager.getApplicationIcon(HyperCanvasRelease.PACKAGE) }
            .getOrElse { resources.getDrawable(R.drawable.ic_extension_hypercanvas, null) }
    }
    var installedVersion by remember { mutableStateOf(HyperCanvasRelease.installedVersion(context)) }
    var downloadedVersion by remember { mutableStateOf(HyperCanvasRelease.downloadedVersion(context)) }
    var removing by remember { mutableStateOf(false) }
    var removalStatus by remember { mutableStateOf("") }
    var setupBusy by remember { mutableStateOf(false) }
    var setupMessage by remember { mutableStateOf("") }
    var restartFailed by remember { mutableStateOf(false) }
    var returningFromLsposed by rememberSaveable { mutableStateOf(false) }
    // Coming back, the extension's process may be new, and LSPosed takes a moment to reach it.
    var settlingUntil by remember { mutableLongStateOf(System.currentTimeMillis() + CANVAS_SETTLE_MS) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) settlingUntil = System.currentTimeMillis() + CANVAS_SETTLE_MS
            if (event == Lifecycle.Event.ON_RESUME && !setupBusy &&
                (returningFromLsposed || (installedVersion != null && !canvasScopesGranted(state)))) {
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
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var setupNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            checking = true
            githubOnly = false
            try { latestRelease = HyperCanvasRelease.latest() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                downloadStatus = resources.getString(R.string.update_failed, e.message.orEmpty())
                githubOnly = latestRelease == null
            }
            finally { checking = false }
        }
    }
    var hostBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) while (true) {
            val host = ModuleBridge.query(context)
            if (host.alive) {
                hostBlocked = host.hidePlayerBackground
                state = HyperCanvasBridge.synchronizeBlock(context, hostBlocked)
            } else state = HyperCanvasBridge.query(context)
            val newIconRevision = runCatching {
                context.packageManager.getPackageInfo(HyperCanvasRelease.PACKAGE, 0).lastUpdateTime
            }.getOrDefault(0L)
            if (newIconRevision != iconRevision && newIconRevision != 0L &&
                !canvasScopesGranted(state) && !setupBusy) {
                setupBusy = true
                try {
                    HyperCanvasBridge.refreshActivation(context)
                    state = HyperCanvasBridge.query(context)
                } finally { setupBusy = false }
            }
            iconRevision = newIconRevision
            setupNow = System.currentTimeMillis()
            installedVersion = HyperCanvasRelease.installedVersion(context)
            downloadedVersion = HyperCanvasRelease.downloadedVersion(context)
            delay(if (setupNow < settlingUntil) 500L else 2500L)
        }
    }
    fun push(key: String, value: Int) {
        if (HyperCanvasSetup.stage(installedVersion != null, state.getInt("setupProtocol"),
                canvasScopesGranted(state), state.getBoolean("setupReady"),
                state.getLong("setupStartedAt"), setupNow, state.getBoolean("installed"),
                legacyCanvasActive(state)) != HyperCanvasSetup.Stage.READY) return
        val updated = Bundle(state)
        if (key == "enabled" || key == "showInPill") updated.putBoolean(key, value != 0) else updated.putInt(key, value)
        state = updated
        scope.launch { state = HyperCanvasBridge.configure(context, updated) }
    }
    val blocked = hostBlocked || state.getBoolean("hostBlocked")
    val installed = state.getBoolean("installed")
    val enabled = state.getBoolean("enabled")
    val setupStage = HyperCanvasSetup.stage(installedVersion != null, state.getInt("setupProtocol"),
        canvasScopesGranted(state), state.getBoolean("setupReady"),
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
    val canOpenSettings = setupStage == HyperCanvasSetup.Stage.READY && !setupBusy
    LaunchedEffect(canOpenSettings, setupStage) {
        if (showDetails && !canOpenSettings && setupStage != HyperCanvasSetup.Stage.CHECKING) onBack()
    }
    val status = when {
        blocked -> R.string.hypercanvas_background_blocked
        !installed -> R.string.hypercanvas_missing
        state.getString("scopeState") == "awaiting-approval" -> R.string.hypercanvas_approval
        state.getString("scopeState") == "enable-module" -> R.string.hypercanvas_enable_module
        state.getString("scopeState") == "restart-scopes" -> R.string.hypercanvas_restart
        state.getString("scopeState") == "approval-failed" -> R.string.hypercanvas_scope_failed
        state.getLong("systemSeen") == 0L -> R.string.hypercanvas_restart
        else -> R.string.hypercanvas_connected
    }
    val release = latestRelease
    val versionLabel = installedVersion?.let { stringResource(R.string.extension_installed_version, it) }
    val canDownload = release != null && !checking && !fetching && !removing
            && ExtensionVersion.isNewer(release.version, installedVersion)

    val hookConnected = setupStage == HyperCanvasSetup.Stage.READY && !blocked
    val download: () -> Unit = {
                        if (canDownload && !UpdateInstaller.inFlight) {
                            fetching = true
                            scope.launch {
                                try {
                                    githubOnly = !downloadExtension(context, resources, release.asset,
                                        HyperCanvasRelease.PACKAGE, R.string.hypercanvas_downloading) { downloadStatus = it }
                                } finally {
                                    downloadedVersion = HyperCanvasRelease.downloadedVersion(context)
                                    fetching = false
                                }
                            }
                        }
                    }
    LaunchedEffect(installedVersion) { if (showDetails && installedVersion == null) onBack() }
    AnimatedContent(targetState = showDetails, label = "Extension navigation",
        transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(280)) { it / 6 * direction } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(280)) { -it / 8 * direction } + fadeOut(tween(160)))
        }) { detailPage ->
    PageScaffold(title = if (detailPage) "HyperCanvas" else stringResource(R.string.tab_extensions),
        isBlurEnabled = isBlurEnabled, extraBottomPadding = extraBottomPadding,
        onBack = if (detailPage) onBack else null) {
        if (!detailPage) {
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = canOpenSettings, onClick = { if (canOpenSettings) onOpen() })
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AndroidView(modifier = Modifier.size(42.dp),
                            factory = { android.widget.ImageView(it).apply {
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            } },
                            update = { it.setImageDrawable(extensionIcon) })
                        Column(Modifier.weight(1f)) {
                            Row(modifier = Modifier.padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("HyperCanvas", fontSize = 15.sp, maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false))
                                ExtensionStageBadge("Beta")
                            }
                            versionLabel?.let { label ->
                                Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.alpha(.7f))
                            }
                            Text(stringResource(R.string.hypercanvas_list_summary), fontSize = 12.sp,
                                modifier = Modifier.alpha(.7f))
                        }
                        ExtensionPill(installedVersion, downloadedVersion, release, checking, fetching, canDownload,
                            HyperCanvasRelease.releasesPage.takeIf { githubOnly && (installedVersion == null || canDownload) },
                            download)
                        if (canOpenSettings) Text("›", fontSize = 24.sp)
                    }
                    if (downloadStatus.isNotEmpty() && !fetching && (installedVersion == null || (githubOnly && canDownload)))
                        Text(downloadStatus, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    if (installedVersion != null && setupStage != HyperCanvasSetup.Stage.READY &&
                        setupStage != HyperCanvasSetup.Stage.CHECKING) {
                        val setupTitle = when (setupStage) {
                            HyperCanvasSetup.Stage.RESTART -> R.string.hypercanvas_setup_restart
                            HyperCanvasSetup.Stage.VERIFYING -> R.string.hypercanvas_setup_verifying
                            else -> R.string.hypercanvas_setup_permissions
                        }
                        val setupSummary = when (setupStage) {
                            HyperCanvasSetup.Stage.RESTART -> R.string.hypercanvas_setup_restart_summary
                            HyperCanvasSetup.Stage.VERIFYING -> R.string.hypercanvas_setup_verifying_summary
                            else -> if (state.getString("scopeState") == "awaiting-approval")
                                R.string.hypercanvas_approval else R.string.hypercanvas_setup_permissions_summary
                        }
                        ArrowPreference(title = stringResource(setupTitle),
                            summary = setupMessage.ifEmpty { stringResource(setupSummary) },
                            enabled = !setupBusy && !fetching && !removing &&
                                setupStage != HyperCanvasSetup.Stage.VERIFYING,
                            onClick = {
                                if (!setupBusy) {
                                    setupBusy = true
                                    setupMessage = ""
                                    scope.launch {
                                        try {
                                            if (setupStage == HyperCanvasSetup.Stage.PERMISSIONS || setupStage == HyperCanvasSetup.Stage.CHECKING) {
                                                HyperCanvasBridge.refreshActivation(context)
                                                delay(700L)
                                                state = HyperCanvasBridge.requestScopes(context)
                                                if (!state.getBoolean("frameworkConnected") && !canvasScopesGranted(state)) {
                                                    returningFromLsposed = true
                                                    if (!HyperCanvasBridge.openLsposed(context)) returningFromLsposed = false
                                                }
                                            } else {
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
                                                    val restarted = withContext(Dispatchers.IO + NonCancellable) {
                                                        ModuleBridge.restartCanvasScopes()
                                                    }
                                                    if (!restarted) {
                                                        restartFailed = true
                                                        setupMessage = resources.getString(R.string.hypercanvas_setup_restart_failed)
                                                    }
                                                }
                                            }
                                        } finally { setupBusy = false }
                                    }
                                }
                            })
                    }
                }
            }
            moreExtensions()
        } else {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Its default end margin of 28 dp would hold the pill far from the name.
                        SmallTitle("HyperCanvas", insideMargin = PaddingValues(start = 28.dp, top = 8.dp, end = 8.dp, bottom = 8.dp))
                        ExtensionStageBadge("Beta")
                    }
                    versionLabel?.let { label ->
                        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 28.dp, bottom = 8.dp).alpha(.7f))
                    }
                }
                val color = if (hookConnected) Color(0xFF388E3C) else Color(0xFFD64545)
                Text(stringResource(if (hookConnected) R.string.hypercanvas_hook_connected else R.string.hypercanvas_hook_disconnected),
                    color = color, fontSize = 11.sp,
                    modifier = Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
        item {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(title = stringResource(R.string.hypercanvas_title), summary = stringResource(R.string.hypercanvas_summary) +
                        if (status == R.string.hypercanvas_connected) "" else "\n" + stringResource(status),
                    checked = enabled, enabled = installed && !blocked, onCheckedChange = { push("enabled", if (it) 1 else 0) })
                if (installed && enabled) {
                    WindowDropdownPreference(
                        title = stringResource(R.string.hypercanvas_renderer),
                        summary = stringResource(R.string.hypercanvas_renderer_summary),
                        items = listOf(stringResource(R.string.hypercanvas_renderer_texture),
                            stringResource(R.string.hypercanvas_renderer_gles)),
                        selectedIndex = state.getInt("renderer", 0).coerceIn(0, 1),
                        onSelectedIndexChange = { push("renderer", it) })
                    SwitchPreference(title = stringResource(R.string.hypercanvas_show_in_pill),
                        summary = stringResource(R.string.hypercanvas_show_in_pill_summary),
                        checked = state.getBoolean("showInPill"),
                        onCheckedChange = { push("showInPill", if (it) 1 else 0) })
                    ValueSlider(title = stringResource(R.string.hypercanvas_dim), value = state.getInt("dim", 35).toFloat(), valueRange = 0f..80f,
                        enabled = true, detent = 35f, label = { "${it.toInt()}%" }, onValueChange = { push("dim", it.toInt()) })
                    ValueSlider(title = stringResource(R.string.hypercanvas_cover_blur), value = state.getInt("blur", 18).toFloat(), valueRange = 0f..40f,
                        enabled = true, detent = 18f, onValueChange = { push("blur", it.toInt()) })
                    ValueSlider(title = stringResource(R.string.hypercanvas_cover_dim), value = state.getInt("coverDim", 45).toFloat(), valueRange = 0f..80f,
                        enabled = true, detent = 45f, label = { "${it.toInt()}%" }, onValueChange = { push("coverDim", it.toInt()) })
                    Text(stringResource(R.string.hypercanvas_cache, state.getInt("count")), modifier = Modifier.padding(16.dp))
                }
            }
        }
        if (installedVersion != null) item {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(title = stringResource(R.string.hypercanvas_uninstall),
                    summary = removalStatus.ifEmpty { stringResource(R.string.hypercanvas_uninstall_summary) },
                    enabled = !removing && !fetching,
                    onClick = {
                        if (!removing) {
                            removing = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO + NonCancellable) {
                                    HyperCanvasBridge.uninstall(context)
                                }
                                installedVersion = HyperCanvasRelease.installedVersion(context)
                                downloadedVersion = HyperCanvasRelease.downloadedVersion(context)
                                state = HyperCanvasBridge.query(context)
                                removalStatus = resources.getString(when (result) {
                                    2 -> R.string.hypercanvas_removed
                                    1 -> R.string.hypercanvas_removed_restart_failed
                                    else -> R.string.hypercanvas_remove_failed
                                })
                                android.widget.Toast.makeText(context, removalStatus, android.widget.Toast.LENGTH_LONG).show()
                                removing = false
                            }
                        }
                    })
            }
        }
        item { Text(stringResource(R.string.hypercanvas_note), modifier = Modifier.padding(16.dp)) }
        }
    }
    }
}

private const val CANVAS_SETTLE_MS = 4_000L

/** System UI takes this long to come back and load a new hook; the setup rows wait for it. */
private const val STANDBY_RESTART_MS = 20_000L

/** A download that has not moved for this long is given up on; the pill offers GitHub instead. */
private const val DOWNLOAD_STALL_MS = 60_000L

/**
 * Fetch a release's APK, verify it and hand it to the installer, saying how that goes. False when
 * the APK did not get anywhere.
 */
private suspend fun downloadExtension(context: android.content.Context, resources: android.content.res.Resources,
    asset: GithubAsset, pkg: String, downloading: Int, onStatus: (String) -> Unit): Boolean {
    try {
        onStatus(resources.getString(R.string.hypercanvas_fetching))
        UpdateInstaller.start(context, asset.url, asset.name, pkg, reuseCached = true)
        var seen = UpdateInstaller.progress
        var movedAt = System.currentTimeMillis()
        var stalled = false
        while (UpdateInstaller.inFlight) {
            val percent = UpdateInstaller.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
            onStatus(resources.getString(downloading) + percent)
            delay(250)
            if (UpdateInstaller.progress != seen) {
                seen = UpdateInstaller.progress
                movedAt = System.currentTimeMillis()
            } else if (!stalled && System.currentTimeMillis() - movedAt > DOWNLOAD_STALL_MS) {
                stalled = true
                UpdateInstaller.cancel()
            }
        }
        val outcome = UpdateInstaller.lastOutcome
        onStatus(when (outcome) {
            is InstallOutcome.HandedOff -> resources.getString(R.string.hypercanvas_install_ready)
            is InstallOutcome.NotOurs -> resources.getString(R.string.update_not_ours)
            is InstallOutcome.NoInstaller -> resources.getString(R.string.update_no_installer)
            is InstallOutcome.SavedToDownloads -> resources.getString(R.string.update_saved_to_downloads, outcome.name)
            is InstallOutcome.Failed -> resources.getString(R.string.update_failed, outcome.message)
            is InstallOutcome.Cancelled -> resources.getString(R.string.update_failed, "timed out")
            else -> ""
        })
        return outcome is InstallOutcome.HandedOff || outcome is InstallOutcome.SavedToDownloads
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onStatus(resources.getString(R.string.update_failed, e.message.orEmpty()))
        return false
    }
}

/**
 * Download, update or installed, at an extension's row end, with the version on offer under it.
 * With [releasesPage], the app could not get the APK itself and the pill opens that page instead.
 */
@Composable
private fun ExtensionPill(installedVersion: String?, downloadedVersion: String?, release: ExtensionRelease.Release?,
    checking: Boolean, fetching: Boolean, canDownload: Boolean, releasesPage: String?, onDownload: () -> Unit) {
    val context = LocalContext.current
    val ready = installedVersion != null && !canDownload
    val color = if (ready) Color(0xFF388E3C) else MiuixTheme.colorScheme.primary
    val label = when {
        fetching -> UpdateInstaller.progress?.let { "${(it * 100).toInt()}%" } ?: "…"
        releasesPage != null -> "GitHub"
        ready -> stringResource(R.string.extension_installed)
        checking -> "…"
        canDownload && installedVersion != null -> stringResource(R.string.extension_update)
        downloadedVersion != null && (release == null ||
            !ExtensionVersion.isNewer(release.version, downloadedVersion)) -> stringResource(R.string.extension_downloaded)
        else -> stringResource(R.string.extension_download)
    }
    val newVersion = when {
        release == null -> null
        installedVersion == null -> release.version
        ExtensionVersion.isNewer(release.version, installedVersion) ->
            stringResource(R.string.extension_new_version, release.version)
        else -> null
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.alpha(if (ready || canDownload || releasesPage != null) 1f else .5f)
                .clip(RoundedCornerShape(50)).border(1.dp, color, RoundedCornerShape(50))
                .clickable(enabled = canDownload || releasesPage != null, role = Role.Button, onClick = {
                    if (releasesPage == null) onDownload()
                    else runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(releasesPage))) }
                })
                .padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (canDownload || releasesPage != null) Icon(MiuixIcons.Download, contentDescription = null,
                tint = color, modifier = Modifier.size(12.dp))
            Text(label, color = color, fontSize = 10.sp)
        }
        newVersion?.let { latest ->
            Text(latest, fontSize = 11.sp, maxLines = 1, modifier = Modifier.alpha(.7f))
        }
    }
}

/** Older extensions expose hook receipts and their live LSPosed connection state. */
private fun legacyCanvasActive(state: Bundle): Boolean = state.getString("scopeState") == "ready" &&
    state.getLong("systemSeen") > 0L

private fun canvasScopesGranted(state: Bundle): Boolean = state.getBoolean("scopesGranted") ||
    (state.getInt("setupProtocol") < 1 && state.getString("scopeState") == "ready")

@Composable
private fun ExtensionStageBadge(stage: String) {
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Text(stage, fontSize = 11.sp, color = color,
        modifier = Modifier.border(1.dp, color.copy(alpha = .6f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp))
}

/**
 * A delay of StandBy's, in seconds: a number on the left, its unit on the right, both at the
 * row's end. What is typed applies as soon as it is in range; leaving the box puts anything else
 * back in range. 0 means never, with a warning under the row.
 */
@Composable
private fun StandbyTimeRow(title: String, summary: String, stored: Int, range: IntRange,
                           enabled: Boolean, onChange: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(stored % 60 == 0) }
    fun shown(value: Int) = (if (minutes) value / 60 else value).toString()
    var text by remember { mutableStateOf(shown(stored)) }
    var showUnits by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun typed() = text.toIntOrNull()?.let { if (minutes) it * 60 else it }
    fun settled(): Int {
        val value = typed() ?: stored
        if (value == 0) return 0
        return value.coerceIn(if (minutes) maxOf(range.first, 60) else range.first, range.last)
    }
    fun settle() {
        val value = settled()
        text = shown(value)
        onChange(value)
    }
    // The page reads the state again every couple of seconds: only a real change moves the box.
    LaunchedEffect(stored) {
        if (!focused && typed() != stored) {
            minutes = stored % 60 == 0
            text = shown(stored)
        }
    }
    val unitNames = listOf(stringResource(R.string.hyperimmersive_idle_unit_seconds),
        stringResource(R.string.hyperimmersive_idle_unit_minutes))
    val colors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    BasicComponent(title = title, summary = summary, enabled = enabled,
        endActions = {
            Row(Modifier.alpha(if (enabled) 1f else .5f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicTextField(value = text, enabled = enabled, singleLine = true,
                    onValueChange = { new ->
                        text = new.filter(Char::isDigit).take(5)
                        typed()?.takeIf { it in range || it == 0 }?.let(onChange)
                    },
                    textStyle = TextStyle(color = colors.onSurface, fontSize = 15.sp, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.width(76.dp).clip(shape).background(colors.secondaryContainer)
                        .padding(vertical = 9.dp)
                        .onFocusChanged {
                            if (focused && !it.isFocused) settle()
                            focused = it.isFocused
                        })
                Box {
                    Text(unitNames[if (minutes) 1 else 0], fontSize = 15.sp, maxLines = 1,
                        modifier = Modifier.clip(shape).background(colors.secondaryContainer)
                            .clickable(enabled = enabled) { focus.clearFocus(); showUnits = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp))
                    OverlayListPopup(show = showUnits,
                        popupPositionProvider = MenuPopupDefaults.MenuPositionProvider,
                        alignment = PopupPositionProvider.Align.TopEnd,
                        onDismissRequest = { showUnits = false }) {
                        ListPopupColumn {
                            unitNames.forEachIndexed { index, name ->
                                DropdownItem(text = name, optionSize = unitNames.size, index = index,
                                    onSelectedIndexChange = {
                                        showUnits = false
                                        minutes = index == 1
                                        settle()
                                    })
                            }
                        }
                    }
                }
            }
        })
    if (stored == 0) Text(stringResource(R.string.hyperimmersive_never_warning), color = Color(0xFFD64545), fontSize = 12.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
}

/** HyperImmersive's state, read again every couple of seconds while it is on screen. */
@Composable
private fun rememberStandbyState(isCurrent: Boolean): MutableState<Bundle> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(Bundle()) }
    LaunchedEffect(isCurrent) {
        var wakes = 0
        if (isCurrent) while (true) {
            var answer = HyperImmersiveBridge.query(context)
            // Installed but silent: HyperOS will not let this app start it. A few tries: the new
            // process can abort in zygote before any of its code runs (seen right after a force
            // stop), and a copy signed by someone else stays silent however awake it is.
            if (answer.isEmpty && wakes < 5 && HyperImmersiveRelease.installedVersion(context) != null) {
                wakes++
                HyperImmersiveBridge.wake()
                delay(500L)
                answer = HyperImmersiveBridge.query(context)
            }
            state.value = answer
            delay(2000L)
        }
    }
    return state
}

/** Calendar and the watcher's notification are left out: StandBy opens and plays without them. */
private fun standbyPermissionsReady(state: Bundle) = state.getBoolean("notificationAccess") &&
    state.getBoolean("backgroundStart") && state.getBoolean("lockScreen")

@Composable
private fun StandbyGrantRow(state: Bundle, onChecked: (Bundle) -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var granting by remember { mutableStateOf(false) }
    fun yes(key: String) = resources.getString(
        if (state.getBoolean(key)) R.string.hyperimmersive_allowed else R.string.hyperimmersive_missing)
    ArrowPreference(title = stringResource(R.string.hyperimmersive_grant),
        summary = message.ifEmpty {
            stringResource(R.string.hyperimmersive_grant_summary,
                yes("notificationAccess"), yes("backgroundStart"), yes("notifications"), yes("calendar"), yes("lockScreen"))
        },
        enabled = !granting,
        onClick = {
            granting = true
            message = ""
            scope.launch {
                if (!HyperImmersiveBridge.grant()) message = resources.getString(R.string.hyperimmersive_grant_failed)
                onChecked(HyperImmersiveBridge.query(context))
                granting = false
            }
        })
}

@Composable
private fun StandbyIcon(installedVersion: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val icon = remember(installedVersion) {
        runCatching { context.packageManager.getApplicationIcon(HyperImmersiveBridge.PACKAGE) }
            .getOrElse { resources.getDrawable(R.drawable.ic_extension_hyperimmersive, null) }
    }
    AndroidView(modifier = Modifier.size(42.dp),
        factory = { android.widget.ImageView(it).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } },
        update = { it.setImageDrawable(icon) })
}

/**
 * The StandBy extension in the list: download it, then the two setup steps HyperCanvas has, until
 * System UI answers with its hook loaded and the settings open. LSPosed says nothing about a module
 * from outside it, so the steps stay up together rather than one following the other.
 */
@Composable
private fun HyperImmersiveCard(isCurrent: Boolean, onOpen: () -> Unit) {
    var state by rememberStandbyState(isCurrent)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ExtensionRelease.Release?>(null) }
    var checking by remember { mutableStateOf(true) }
    var fetching by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var githubOnly by remember { mutableStateOf(false) }
    var setupMessage by remember { mutableStateOf("") }
    var hook by remember { mutableStateOf<Boolean?>(null) }
    var permissionsReady by remember { mutableStateOf<Boolean?>(null) }
    var restartedAt by remember { mutableLongStateOf(0L) }
    var installedVersion by remember { mutableStateOf(HyperImmersiveRelease.installedVersion(context)) }
    var downloadedVersion by remember { mutableStateOf(HyperImmersiveRelease.downloadedVersion(context)) }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            checking = true
            githubOnly = false
            try { release = HyperImmersiveRelease.latest() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                status = resources.getString(R.string.update_failed, e.message.orEmpty())
                githubOnly = release == null
            }
            finally { checking = false }
        }
    }
    LaunchedEffect(state) {
        installedVersion = HyperImmersiveRelease.installedVersion(context)
        downloadedVersion = HyperImmersiveRelease.downloadedVersion(context)
        if (state.containsKey("hookActive")) hook = state.getBoolean("hookActive")
        if (state.containsKey("notificationAccess")) permissionsReady = standbyPermissionsReady(state)
        if (installedVersion == null) {
            hook = null
            permissionsReady = null
        }
    }
    val latest = release
    val canDownload = latest != null && !checking && !fetching &&
        ExtensionVersion.isNewer(latest.version, installedVersion)
    val canOpen = installedVersion != null && hook == true && permissionsReady == true
    val verifying = hook == false && System.currentTimeMillis() - restartedAt < STANDBY_RESTART_MS
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().clickable(enabled = canOpen, onClick = onOpen).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StandbyIcon(installedVersion)
            Column(Modifier.weight(1f)) {
                Row(modifier = Modifier.padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HyperImmersive", fontSize = 15.sp, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false))
                    ExtensionStageBadge("Alpha")
                }
                installedVersion?.let { v ->
                    Text(stringResource(R.string.extension_installed_version, v), fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(.7f))
                }
                Text(stringResource(R.string.hyperimmersive_list_summary), fontSize = 12.sp, modifier = Modifier.alpha(.7f))
            }
            ExtensionPill(installedVersion, downloadedVersion, latest, checking, fetching, canDownload,
                HyperImmersiveRelease.releasesPage.takeIf { githubOnly && (installedVersion == null || canDownload) }) {
                if (canDownload && latest != null && !UpdateInstaller.inFlight) {
                    fetching = true
                    scope.launch {
                        try {
                            githubOnly = !downloadExtension(context, resources, latest.asset,
                                HyperImmersiveRelease.PACKAGE, R.string.hyperimmersive_downloading) { status = it }
                        } finally {
                            downloadedVersion = HyperImmersiveRelease.downloadedVersion(context)
                            fetching = false
                        }
                    }
                }
            }
            if (canOpen) Text("›", fontSize = 24.sp)
        }
        if (status.isNotEmpty() && !fetching && (installedVersion == null || (githubOnly && canDownload)))
            Text(status, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (installedVersion != null && permissionsReady == false) StandbyGrantRow(state) { state = it }
        if (installedVersion != null && hook == false) {
            if (verifying) {
                ArrowPreference(title = stringResource(R.string.hypercanvas_setup_verifying),
                    summary = stringResource(R.string.hyperimmersive_setup_verifying_summary),
                    enabled = false, onClick = {})
            } else {
                ArrowPreference(title = stringResource(R.string.hypercanvas_setup_permissions),
                    summary = stringResource(R.string.hyperimmersive_setup_permissions_summary),
                    onClick = {
                        scope.launch { HyperCanvasBridge.openLsposed(context, HyperImmersiveRelease.PACKAGE) }
                    })
                ArrowPreference(title = stringResource(R.string.hypercanvas_setup_restart),
                    summary = setupMessage.ifEmpty { stringResource(R.string.hyperimmersive_setup_restart_summary) },
                    onClick = {
                        setupMessage = ""
                        restartedAt = System.currentTimeMillis()
                        scope.launch {
                            if (!withContext(Dispatchers.IO + NonCancellable) { ModuleBridge.restartSystemUi() }) {
                                restartedAt = 0L
                                setupMessage = resources.getString(R.string.hyperimmersive_setup_restart_failed)
                            }
                        }
                    })
            }
        }
    }
}

@Composable
private fun HyperImmersivePage(isBlurEnabled: Boolean, isCurrent: Boolean, extraBottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var state by rememberStandbyState(isCurrent)
    var removing by remember { mutableStateOf(false) }
    var removalStatus by remember { mutableStateOf("") }
    fun set(key: String, on: Boolean) {
        state = Bundle(state).apply { putBoolean(key, on) }
        scope.launch { HyperImmersiveBridge.configure(context, key, on).takeIf { it.size() > 0 }?.let { state = it } }
    }
    val enabled = state.getBoolean("enabled")
    PageScaffold(title = "HyperImmersive", isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding, onBack = onBack) {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Its default end margin of 28 dp would hold the pill far from the name.
                        SmallTitle("HyperImmersive", insideMargin = PaddingValues(start = 28.dp, top = 8.dp, end = 8.dp, bottom = 8.dp))
                        ExtensionStageBadge("Alpha")
                    }
                    state.getString("version")?.let { v ->
                        Text(stringResource(R.string.extension_installed_version, v), fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 28.dp, bottom = 8.dp).alpha(.7f))
                    }
                }
                val watching = state.getBoolean("watching")
                val color = if (watching) Color(0xFF388E3C) else Color(0xFFD64545)
                Text(stringResource(if (watching) R.string.hyperimmersive_watching else R.string.hyperimmersive_stopped),
                    color = color, fontSize = 11.sp,
                    modifier = Modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp))
            }
        }
        item {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(title = stringResource(R.string.hyperimmersive_title),
                    summary = stringResource(R.string.hyperimmersive_summary),
                    checked = enabled, onCheckedChange = { set("enabled", it) })
                SwitchPreference(title = stringResource(R.string.hyperimmersive_blur),
                    summary = stringResource(R.string.hyperimmersive_blur_summary),
                    checked = state.getBoolean("blurBackdrop"), enabled = enabled,
                    onCheckedChange = { set("blurBackdrop", it) })
                fun setTime(key: String, seconds: Int) {
                    state = Bundle(state).apply { putInt(key, seconds) }
                    scope.launch {
                        HyperImmersiveBridge.configure(context, key, seconds).takeIf { it.size() > 0 }?.let { state = it }
                    }
                }
                StandbyTimeRow(title = stringResource(R.string.hyperimmersive_idle),
                    summary = stringResource(R.string.hyperimmersive_idle_summary),
                    stored = state.getInt("idleSeconds", 60), range = 10..3600,
                    enabled = enabled, onChange = { setTime("idleSeconds", it) })
                StandbyTimeRow(title = stringResource(R.string.hyperimmersive_off),
                    summary = stringResource(R.string.hyperimmersive_off_summary),
                    stored = state.getInt("offSeconds", 0), range = 10..86400,
                    enabled = enabled, onChange = { setTime("offSeconds", it) })
                ArrowPreference(title = stringResource(R.string.hyperimmersive_preview),
                    summary = stringResource(R.string.hyperimmersive_preview_summary),
                    onClick = { scope.launch { HyperImmersiveBridge.preview(context) } })
            }
        }
        item {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                StandbyGrantRow(state) { state = it }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(title = stringResource(R.string.hyperimmersive_uninstall),
                    summary = removalStatus.ifEmpty { stringResource(R.string.hyperimmersive_uninstall_summary) },
                    enabled = !removing,
                    onClick = {
                        removing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO + NonCancellable) {
                                HyperImmersiveBridge.uninstall(context)
                            }
                            removalStatus = resources.getString(when (result) {
                                2 -> R.string.hyperimmersive_removed
                                1 -> R.string.hyperimmersive_removed_restart_failed
                                else -> R.string.hyperimmersive_remove_failed
                            })
                            android.widget.Toast.makeText(context, removalStatus, android.widget.Toast.LENGTH_LONG).show()
                            removing = false
                            if (result != 0) onBack()
                        }
                    })
            }
        }
    }
}
