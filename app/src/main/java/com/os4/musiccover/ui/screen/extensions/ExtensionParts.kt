package com.os4.musiccover.ui.screen.extensions

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import top.yukonga.miuix.kmp.icon.extended.Settings
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.os4.musiccover.R
import com.os4.musiccover.updater.ExtensionRelease
import com.os4.musiccover.updater.ExtensionVersion
import com.os4.musiccover.updater.InstallOutcome
import com.os4.musiccover.updater.UpdateInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** What every extension's page and card share: its name, its stage and where its releases are. */
internal class ExtensionInfo(val name: String, val stage: String, val release: ExtensionRelease,
    val fallbackIcon: Int, val downloading: Int, val needs: Pair<String, String>? = null)

/** How an extension is doing, one word for every extension. */
internal enum class ExtensionStatus { RUNNING, SETUP, OFF }

/**
 * An extension's GitHub release and its download: kept for the whole Extensions tab, so the list
 * and an extension's page show the same, and GitHub is asked again only after [RECHECK_MS].
 */
@Stable
internal class ReleaseState(val info: ExtensionInfo) {
    var latest by mutableStateOf<ExtensionRelease.Release?>(null)
    var checking by mutableStateOf(true)
    var fetching by mutableStateOf(false)
    var status by mutableStateOf("")
    /** The app could not get the APK itself: the pill opens the releases page instead. */
    var githubOnly by mutableStateOf(false)
    var installedVersion by mutableStateOf<String?>(null)
    var downloadedVersion by mutableStateOf<String?>(null)
    private var checkedAt by mutableLongStateOf(0L)

    val canDownload: Boolean get() = latest?.let {
        !checking && !fetching && ExtensionVersion.isNewer(it.version, installedVersion)
    } == true

    fun refreshLocal(context: Context) {
        installedVersion = info.release.installedVersion(context)
        downloadedVersion = info.release.downloadedVersion(context)
    }

    suspend fun check(resources: Resources) {
        if (latest != null && System.currentTimeMillis() - checkedAt < RECHECK_MS) { checking = false; return }
        checking = true
        githubOnly = false
        try {
            latest = info.release.latest()
            checkedAt = System.currentTimeMillis()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            status = resources.getString(R.string.update_failed, e.message.orEmpty())
            githubOnly = latest == null
        } finally { checking = false }
    }

    suspend fun download(context: Context, resources: Resources) {
        val asset = latest?.asset ?: return
        if (!canDownload || UpdateInstaller.inFlight) return
        fetching = true
        try {
            githubOnly = !downloadExtension(context, resources, asset, info.release.PACKAGE, info.downloading) { status = it }
        } finally {
            refreshLocal(context)
            fetching = false
        }
    }
}

private const val RECHECK_MS = 10 * 60_000L

/** A download that has not moved for this long is given up on; the pill offers GitHub instead. */
private const val DOWNLOAD_STALL_MS = 60_000L

/**
 * Fetch a release's APK, verify it and hand it to the installer, saying how that goes. False when
 * the APK did not get anywhere.
 */
private suspend fun downloadExtension(context: Context, resources: Resources,
    asset: com.os4.musiccover.updater.GithubAsset, pkg: String, downloading: Int, onStatus: (String) -> Unit): Boolean {
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

@Composable
private fun ExtensionIcon(info: ExtensionInfo, installedVersion: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val icon = remember(installedVersion) {
        runCatching { context.packageManager.getApplicationIcon(info.release.PACKAGE) }
            .getOrElse { resources.getDrawable(info.fallbackIcon, null) }
    }
    AndroidView(modifier = Modifier.size(42.dp),
        factory = { android.widget.ImageView(it).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } },
        update = { it.setImageDrawable(icon) })
}

@Composable
private fun StageBadge(stage: String) = Chip(stage, MiuixTheme.colorScheme.onSurfaceVariantSummary)

@Composable
private fun StatusChip(status: ExtensionStatus) {
    val color = when (status) {
        ExtensionStatus.RUNNING -> Color(0xFF388E3C)
        ExtensionStatus.SETUP -> Color(0xFFD64545)
        ExtensionStatus.OFF -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Chip(stringResource(when (status) {
        ExtensionStatus.RUNNING -> R.string.extension_status_running
        ExtensionStatus.SETUP -> R.string.extension_status_setup
        ExtensionStatus.OFF -> R.string.extension_status_off
    }), color)
}

/** An icon's mean colour over its opaque pixels, saturated a little: the glow behind it. */
private fun glowColor(icon: android.graphics.drawable.Drawable): Color = runCatching {
    val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
    icon.setBounds(0, 0, 16, 16)
    icon.draw(android.graphics.Canvas(bitmap))
    var r = 0L; var g = 0L; var b = 0L; var n = 0L
    for (x in 0 until 16) for (y in 0 until 16) {
        val p = bitmap.getPixel(x, y)
        if (android.graphics.Color.alpha(p) < 200) continue
        r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); n++
    }
    bitmap.recycle()
    if (n == 0L) return@runCatching Color.Transparent
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
    hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceAtLeast(.8f)
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrDefault(Color.Transparent)

/**
 * Where an installed extension stands, for its card: [ready] true once it is set up (null while
 * that is not known yet), [run] the next setup step when one can be taken, [busy] while one runs.
 */
internal class CardSetup(val ready: Boolean?, val busy: Boolean = false, val run: (() -> Unit)? = null,
    /** Why the last setup step failed, if it did. */
    val message: String = "")

/**
 * An extension in the list, the new look being tried on HyperCanvas first: a large icon at the top
 * left over a soft glow of its colour, the name and version together beside it, and what it does
 * under the icon. Its status pill says whether it is ready; the button at the bottom right is always
 * the next thing to do - download, set up, update, or open its settings; the card itself does not open.
 */
@Composable
internal fun ExtensionHeroCard(info: ExtensionInfo, release: ReleaseState, summary: String, setup: CardSetup,
    onOpen: () -> Unit, needsScoped: Boolean? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val installed = release.installedVersion
    val icon = remember(installed) {
        runCatching { context.packageManager.getApplicationIcon(info.release.PACKAGE) }
            .getOrElse { resources.getDrawable(info.fallbackIcon, null) }
    }
    val glow = remember(icon) { glowColor(icon) }
    val ready = installed != null && setup.ready == true
    val releasesPage = info.release.releasesPage.takeIf { release.githubOnly && (installed == null || release.canDownload) }
    val downloading = installed == null || release.canDownload || release.fetching || releasesPage != null
    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
        Column(Modifier.fillMaxWidth()
            .drawBehind {
                drawRect(androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(glow.copy(alpha = .28f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(42.dp.toPx(), 42.dp.toPx()),
                    radius = 150.dp.toPx()))
            }
            .padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AndroidView(modifier = Modifier.size(52.dp),
                    factory = { android.widget.ImageView(it).apply {
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    } },
                    update = { it.setImageDrawable(icon) })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The name and the bare version read as one: the same size and weight.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(info.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        installed?.let {
                            // With an update on offer: where it is and where it goes.
                            val next = release.latest?.version?.takeIf { release.canDownload || release.fetching }
                            val dim = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            Text(it.substringBefore('-'), fontSize = 17.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, color = dim)
                            next?.let { v ->
                                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null, tint = dim, modifier = Modifier.size(16.dp))
                                Text(v.substringBefore('-'), fontSize = 17.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, color = MiuixTheme.colorScheme.primary)
                            }
                        }
                    }
                    // The stage and, once installed, whether it is ready.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StageBadge(info.stage)
                        if (installed != null) when (setup.ready) {
                            true -> Chip(stringResource(R.string.extension_status_ready), Color(0xFF388E3C))
                            false -> Chip(stringResource(R.string.extension_status_setup), Color(0xFFE0A100))
                            null -> InstalledChip()
                        }
                    }
                }
            }
            // What it does on the left, the next thing to do at the bottom right.
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(summary, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    info.needs?.let { (pkg, appName) -> NeedsLine(pkg, appName, needsScoped) }
                    if (setup.message.isNotEmpty()) Text(setup.message, fontSize = 12.sp, color = Color(0xFFD64545),
                        modifier = Modifier.padding(top = 4.dp))
                }
                when {
                    downloading -> DownloadButton(release, releasesPage) { scope.launch { release.download(context, resources) } }
                    setup.ready == false -> CardButton(if (setup.busy) "…" else stringResource(R.string.extension_set_up),
                        filled = !setup.busy && setup.run != null, onClick = setup.run?.takeIf { !setup.busy })
                    ready -> CardButton(stringResource(R.string.extension_settings), filled = false,
                        icon = MiuixIcons.Settings, onClick = onOpen)
                }
            }
        }
        if (release.status.isNotEmpty() && !release.fetching &&
            (installed == null || (release.githubOnly && release.canDownload)))
            Text(release.status, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/** Taps [onClick], sinking a little under the finger as HyperOS's buttons do instead of rippling. */
private fun Modifier.pressSink(onClick: () -> Unit): Modifier = composed {
    val sink = remember { androidx.compose.animation.core.Animatable(1f) }
    val scope = rememberCoroutineScope()
    // From the finger's first touch, not the list's press (held back ~100ms to tell taps from
    // scrolls): a quick tap still sinks all the way before it springs back.
    graphicsLayer { scaleX = sink.value; scaleY = sink.value }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val down = scope.launch { sink.animateTo(.92f, androidx.compose.animation.core.tween(90)) }
                waitForUpOrCancellation()
                scope.launch {
                    down.join()
                    sink.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = .55f, stiffness = 500f))
                }
            }
        }
        .clickable(indication = null, interactionSource = null, role = Role.Button, onClick = onClick)
}

/** The card's rounded button: solid for the step to take, quiet grey otherwise. */
@Composable
private fun CardButton(label: String, filled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)?) {
    val primary = MiuixTheme.colorScheme.primary
    val fg = if (filled) Color.White else MiuixTheme.colorScheme.onSurface
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.then(if (onClick != null) Modifier.pressSink(onClick) else Modifier)
            .height(34.dp).clip(RoundedCornerShape(10.dp))
            .background(if (filled) primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .16f))
            .padding(horizontal = 14.dp)) {
        icon?.let { Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp)) }
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * Whether the app an extension works with is installed (looked at again every few seconds) and,
 * when [scoped] is known, in its LSPosed scope: green, red when missing, amber when not scoped.
 */
@Composable
private fun NeedsLine(pkg: String, appName: String, scoped: Boolean?) {
    val context = LocalContext.current
    fun present() = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    var has by remember { mutableStateOf(present()) }
    LaunchedEffect(pkg) { while (true) { delay(3000L); has = present() } }
    val (text, color) = when {
        !has -> R.string.extension_needs_missing to Color(0xFFD64545)
        scoped == false -> R.string.extension_needs_scope to Color(0xFFE0A100)
        else -> R.string.extension_needs_ok to Color(0xFF388E3C)
    }
    Text(stringResource(text, appName), fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun InstalledChip() = Chip(stringResource(R.string.extension_installed), Color(0xFF388E3C))

/**
 * The card's download, as a rounded button at its bottom right: Download or Update with the version
 * on offer, the progress while it fetches, GitHub when the app could not get the APK. Nothing when
 * the installed version is the latest.
 */
@Composable
private fun DownloadButton(release: ReleaseState, releasesPage: String?, onDownload: () -> Unit) {
    val context = LocalContext.current
    val installed = release.installedVersion != null
    val latest = release.latest
    val action = release.canDownload || releasesPage != null
    if (installed && !action && !release.fetching) return
    val label = when {
        release.fetching -> UpdateInstaller.progress?.let { "${(it * 100).toInt()}%" } ?: "…"
        releasesPage != null -> "GitHub"
        release.checking -> "…"
        // The versions stand by the name; the button says only what it does.
        installed -> stringResource(R.string.update_confirm)
        release.downloadedVersion != null && (latest == null ||
            !ExtensionVersion.isNewer(latest.version, release.downloadedVersion)) -> stringResource(R.string.extension_downloaded)
        else -> stringResource(R.string.extension_download) + (latest?.version?.let { " $it" } ?: "")
    }
    val primary = MiuixTheme.colorScheme.primary
    val fg = if (action) Color.White else primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.then(if (action) Modifier.pressSink {
                if (releasesPage == null) onDownload()
                else runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(releasesPage))) }
            } else Modifier)
            .height(34.dp).clip(RoundedCornerShape(10.dp))
            .background(if (action) primary else primary.copy(alpha = .16f))
            .padding(horizontal = 14.dp)) {
        if (action) Icon(MiuixIcons.Download, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/**
 * The cards' one chip: a tinted, borderless capsule of one height. [filled] is for an action
 * (solid, white text); [dim] for something not ready yet.
 */
@Composable
private fun Chip(text: String, color: Color, filled: Boolean = false, dim: Boolean = false, icon: Boolean = false,
    onClick: (() -> Unit)? = null) {
    val fg = if (filled) Color.White else color
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.alpha(if (dim) .5f else 1f).height(22.dp).clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = .16f))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp)) {
        if (icon) Icon(MiuixIcons.Download, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The top of an extension's page: its name, stage and version, and how it is doing. */
@Composable
internal fun ExtensionHeader(info: ExtensionInfo, version: String?, status: ExtensionStatus) {
    val icon = rememberExtensionIcon(info, version)
    // Its card's look, larger and centred: the icon, the name and version as one, and how it is
    // doing. The glow is the page's own, from the top of the screen (see [rememberExtensionGlow]).
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(modifier = Modifier.size(76.dp),
            factory = { android.widget.ImageView(it).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } },
            update = { it.setImageDrawable(icon) })
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(info.name, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            version?.let {
                Text(it.substringBefore('-'), fontSize = 22.sp, fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StageBadge(info.stage)
            StatusChip(status)
        }
    }
}

@Composable
private fun rememberExtensionIcon(info: ExtensionInfo, version: String?): android.graphics.drawable.Drawable {
    val context = LocalContext.current
    val resources = LocalResources.current
    return remember(version) {
        runCatching { context.packageManager.getApplicationIcon(info.release.PACKAGE) }
            .getOrElse { resources.getDrawable(info.fallbackIcon, null) }
    }
}

/** The colour of an extension's icon, for its page's glow from the top of the screen. */
@Composable
internal fun rememberExtensionGlow(info: ExtensionInfo, version: String?): Color {
    val icon = rememberExtensionIcon(info, version)
    return remember(icon) { glowColor(icon) }
}
/** A titled group of an extension's page. */
@Composable
internal fun ExtensionGroup(title: Int?, content: @Composable () -> Unit) {
    Column {
        title?.let { SmallTitle(text = stringResource(it)) }
        Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) { content() }
    }
}

/**
 * The last group of every extension's page: its update, when there is one, and its removal - asked
 * about first, as it cannot be taken back. [uninstall] answers 2 removed, 1 removed but the restart
 * after it failed, 0 failed.
 */
@Composable
internal fun ExtensionManageGroup(info: ExtensionInfo, release: ReleaseState, uninstallSummary: Int,
    removed: Int, removedRestartFailed: Int, removeFailed: Int, uninstall: (Context) -> Int, onRemoved: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf(false) }
    var removalStatus by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    ExtensionGroup(R.string.extension_group_manage) {
        val latest = release.latest
        if (release.canDownload || release.fetching) ArrowPreference(
            title = stringResource(R.string.extension_update_to, latest?.version.orEmpty()),
            summary = release.status.takeIf { release.fetching || release.githubOnly }
                ?: stringResource(R.string.extension_update_summary),
            enabled = !release.fetching && !removing,
            onClick = {
                if (release.githubOnly) runCatching { context.startActivity(android.content.Intent(
                    android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.release.releasesPage))) }
                else scope.launch { release.download(context, resources) }
            })
        ArrowPreference(title = stringResource(R.string.extension_uninstall, info.name),
            summary = removalStatus.ifEmpty { stringResource(uninstallSummary) },
            enabled = !removing && !release.fetching,
            onClick = { asking = true })
    }
    WindowDialog(show = asking, title = stringResource(R.string.extension_uninstall_confirm_title, info.name),
        summary = stringResource(uninstallSummary), onDismissRequest = { asking = false }) {
        val dismiss = LocalDismissState.current
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.padding(top = 12.dp)) {
            TextButton(modifier = Modifier.weight(1f), text = stringResource(R.string.update_cancel),
                onClick = { dismiss?.invoke() })
            Spacer(Modifier.width(20.dp))
            TextButton(modifier = Modifier.weight(1f), text = stringResource(R.string.extension_uninstall_confirm),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    dismiss?.invoke()
                    if (!removing) {
                        removing = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO + NonCancellable) { uninstall(context) }
                            release.refreshLocal(context)
                            removalStatus = resources.getString(when (result) {
                                2 -> removed
                                1 -> removedRestartFailed
                                else -> removeFailed
                            })
                            android.widget.Toast.makeText(context, removalStatus, android.widget.Toast.LENGTH_LONG).show()
                            removing = false
                            if (result != 0) onRemoved()
                        }
                    }
                })
        }
    }
}
