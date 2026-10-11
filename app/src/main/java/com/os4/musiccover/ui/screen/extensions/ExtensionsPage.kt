package com.os4.musiccover.ui.screen.extensions

import android.os.Bundle
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
import androidx.compose.ui.unit.sp
import com.os4.musiccover.updater.ExtensionVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.os4.musiccover.updater.HyperCanvasRelease
import com.os4.musiccover.updater.UpdateInstaller
import com.os4.musiccover.updater.InstallOutcome
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.preference.ArrowPreference
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.os4.musiccover.HyperCanvasBridge
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.screen.features.ValueSlider
import com.os4.musiccover.ui.util.PageScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun ExtensionsPageView(isBlurEnabled: Boolean, isCurrent: Boolean, extraBottomPadding: Dp) {
    var selectedExtension by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = isCurrent && selectedExtension != null) { selectedExtension = null }
    HyperCanvasContent(isBlurEnabled, isCurrent, extraBottomPadding,
        showDetails = selectedExtension == "hypercanvas",
        onOpen = { selectedExtension = "hypercanvas" }, onBack = { selectedExtension = null })
}

@Composable
private fun HyperCanvasContent(isBlurEnabled: Boolean, isCurrent: Boolean, extraBottomPadding: Dp,
    showDetails: Boolean, onOpen: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(Bundle()) }
    var fetching by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var latestRelease by remember { mutableStateOf<HyperCanvasRelease.Release?>(null) }
    var checking by remember { mutableStateOf(true) }
    var iconRevision by remember { mutableStateOf(0L) }
    val extensionIcon = remember(iconRevision) {
        runCatching { context.packageManager.getApplicationIcon(HyperCanvasRelease.PACKAGE) }
            .getOrElse { context.packageManager.defaultActivityIcon }
    }
    var installedVersion by remember { mutableStateOf(HyperCanvasRelease.installedVersion(context)) }
    var downloadedVersion by remember { mutableStateOf(HyperCanvasRelease.downloadedVersion(context)) }
    var removing by remember { mutableStateOf(false) }
    var removalStatus by remember { mutableStateOf("") }
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            checking = true
            try { latestRelease = HyperCanvasRelease.latest() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { downloadStatus = context.getString(R.string.update_failed, e.message.orEmpty()) }
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
            iconRevision = runCatching {
                context.packageManager.getPackageInfo(HyperCanvasRelease.PACKAGE, 0).lastUpdateTime
            }.getOrDefault(0L)
            installedVersion = HyperCanvasRelease.installedVersion(context)
            downloadedVersion = HyperCanvasRelease.downloadedVersion(context)
            delay(2500L)
        }
    }
    fun push(key: String, value: Int) {
        val updated = Bundle(state)
        if (key == "enabled" || key == "showInPill") updated.putBoolean(key, value != 0) else updated.putInt(key, value)
        state = updated
        scope.launch { state = HyperCanvasBridge.configure(context, updated) }
    }
    val blocked = hostBlocked || state.getBoolean("hostBlocked")
    val installed = state.getBoolean("installed")
    val enabled = state.getBoolean("enabled")
    val status = when {
        blocked -> R.string.hypercanvas_background_blocked
        !installed -> R.string.hypercanvas_missing
        state.getString("scopeState") == "awaiting-approval" -> R.string.hypercanvas_approval
        state.getString("scopeState") == "enable-module" -> R.string.hypercanvas_enable_module
        state.getString("scopeState") == "restart-scopes" -> R.string.hypercanvas_restart
        state.getString("scopeState") == "approval-failed" -> R.string.hypercanvas_scope_failed
        state.getLong("spotifySeen") == 0L || state.getLong("systemSeen") == 0L -> R.string.hypercanvas_restart
        else -> R.string.hypercanvas_connected
    }
    val release = latestRelease
    val canDownload = release != null && !checking && !fetching && !removing
            && ExtensionVersion.isNewer(release.version, installedVersion)
            && ExtensionVersion.isNewer(release.version, downloadedVersion)
    val hookConnected = installed && state.getLong("spotifySeen") > 0L && !blocked
    val download: () -> Unit = {
                        if (canDownload && !UpdateInstaller.inFlight) {
                            fetching = true
                            scope.launch {
                                try {
                                    downloadStatus = context.getString(R.string.hypercanvas_fetching)
                                    val asset = release.asset
                                    UpdateInstaller.start(context, asset.url, asset.name, HyperCanvasRelease.PACKAGE)
                                    while (UpdateInstaller.inFlight) {
                                        val percent = UpdateInstaller.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
                                        downloadStatus = context.getString(R.string.hypercanvas_downloading) + percent
                                        delay(250)
                                    }
                                    downloadStatus = when (val outcome = UpdateInstaller.lastOutcome) {
                                        is InstallOutcome.HandedOff -> context.getString(R.string.hypercanvas_install_ready)
                                        is InstallOutcome.NotOurs -> context.getString(R.string.update_not_ours)
                                        is InstallOutcome.NoInstaller -> context.getString(R.string.update_no_installer)
                                        is InstallOutcome.SavedToDownloads -> context.getString(R.string.update_saved_to_downloads, outcome.name)
                                        is InstallOutcome.Failed -> context.getString(R.string.update_failed, outcome.message)
                                        else -> ""
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    downloadStatus = context.getString(R.string.update_failed, e.message.orEmpty())
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
                    Row(Modifier.fillMaxWidth().clickable(enabled = installedVersion != null, onClick = onOpen)
                        .padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AndroidView(modifier = Modifier.size(42.dp),
                            factory = { android.widget.ImageView(it).apply {
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            } },
                            update = { it.setImageDrawable(extensionIcon) })
                        Column(Modifier.weight(1f)) {
                            Text("HyperCanvas")
                            Text(stringResource(R.string.hypercanvas_list_summary), fontSize = 12.sp,
                                modifier = Modifier.alpha(.7f))
                        }
                        val ready = installedVersion != null && !canDownload
                        val color = if (ready) Color(0xFF388E3C) else MiuixTheme.colorScheme.primary
                        val label = when {
                            fetching -> UpdateInstaller.progress?.let { "${(it * 100).toInt()}%" } ?: "…"
                            ready -> stringResource(R.string.extension_installed)
                            checking -> "…"
                            canDownload && installedVersion != null -> stringResource(R.string.extension_update)
                            downloadedVersion != null && !canDownload -> stringResource(R.string.extension_downloaded)
                            else -> stringResource(R.string.extension_download)
                        }
                        Text(label, color = color, fontSize = 10.sp,
                            modifier = Modifier.alpha(if (ready || canDownload) 1f else .5f)
                                .clip(RoundedCornerShape(50)).border(1.dp, color, RoundedCornerShape(50))
                                .clickable(enabled = canDownload, role = Role.Button, onClick = download)
                                .padding(horizontal = 8.dp, vertical = 4.dp))
                        if (installedVersion != null) Text("›", fontSize = 24.sp)
                    }
                    if (downloadStatus.isNotEmpty() && !fetching && installedVersion == null)
                        Text(downloadStatus, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        } else {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SmallTitle("HyperCanvas · Beta")
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
                                removalStatus = context.getString(when (result) {
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
