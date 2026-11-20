package com.os4.musiccover.ui.screen.extensions

import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.os4.musiccover.HyperCanvasBridge
import com.os4.musiccover.HyperImmersiveBridge
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.component.DropdownItem
import com.os4.musiccover.ui.component.MenuPopupDefaults
import com.os4.musiccover.ui.util.PageScaffold
import com.os4.musiccover.updater.HyperImmersiveRelease
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal val HyperImmersiveInfo = ExtensionInfo("HyperImmersive", "Alpha", HyperImmersiveRelease,
    R.drawable.ic_extension_hyperimmersive, R.string.hyperimmersive_downloading)

/** System UI takes this long to come back and load a new hook; the setup step waits for it. */
private const val STANDBY_RESTART_MS = 20_000L

/** Calendar and the watcher's notification are left out: StandBy opens and plays without them. */
private fun standbyPermissionsReady(state: Bundle) = state.getBoolean("notificationAccess") &&
    state.getBoolean("backgroundStart") && state.getBoolean("lockScreen")

/** HyperImmersive as the Extensions tab knows it: its state, read every couple of seconds, and its setup. */
@Stable
internal class HyperImmersiveModel(private val context: Context, private val scope: CoroutineScope) {
    val release = ReleaseState(HyperImmersiveInfo)
    var state by mutableStateOf(Bundle())
    /** Whether System UI has the hook loaded; null until it has answered. */
    var hook by mutableStateOf<Boolean?>(null)
    var permissionsReady by mutableStateOf<Boolean?>(null)
    var restartedAt by mutableLongStateOf(0L)
    /** LSPosed was opened for it: restarting System UI is what is left. */
    var lsposedVisited by mutableStateOf(false)
    var setupMessage by mutableStateOf("")
    /** Tries to wake it since the tab was last opened. */
    var wakes = 0

    val enabled get() = state.getBoolean("enabled")
    val canOpen get() = release.installedVersion != null && hook == true && permissionsReady == true
    val status get() = when {
        !canOpen -> ExtensionStatus.SETUP
        state.getBoolean("watching") -> ExtensionStatus.RUNNING
        else -> ExtensionStatus.OFF
    }

    var granting by mutableStateOf(false)

    /**
     * What its card shows: ready, or the one setup step left - LSPosed's scope, then System UI's
     * restart to load the hook (LSPosed says nothing about a module from outside it, so a restart
     * that did not bring the hook sends back to LSPosed), then the app's own permissions.
     */
    val cardSetup: CardSetup get() {
        val verifying = hook == false && restartedAt != 0L &&
            System.currentTimeMillis() - restartedAt < STANDBY_RESTART_MS
        val ready = when {
            release.installedVersion == null || hook == null -> null
            hook == false -> false
            else -> permissionsReady
        }
        return CardSetup(ready, busy = verifying || granting, run = if (ready == false) ::runSetupStep else null,
            message = setupMessage)
    }

    fun runSetupStep() {
        when {
            hook == false -> if (!lsposedVisited || restartedAt != 0L) openLsposed()
                else restartSystemUi(context.getString(R.string.hyperimmersive_setup_restart_failed))
            permissionsReady == false -> scope.launch {
                granting = true
                setupMessage = ""
                if (!grant()) setupMessage = context.getString(R.string.hyperimmersive_grant_failed)
                granting = false
            }
        }
    }

    suspend fun poll() {
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
        accept(answer)
    }

    private fun accept(answer: Bundle) {
        state = answer
        release.refreshLocal(context)
        if (answer.containsKey("hookActive")) hook = answer.getBoolean("hookActive")
        if (answer.containsKey("notificationAccess")) permissionsReady = standbyPermissionsReady(answer)
        if (release.installedVersion == null) {
            hook = null
            permissionsReady = null
        }
    }

    fun set(key: String, on: Boolean) {
        state = Bundle(state).apply { putBoolean(key, on) }
        scope.launch { HyperImmersiveBridge.configure(context, key, on).takeIf { it.size() > 0 }?.let { state = it } }
    }

    fun set(key: String, value: Int) {
        state = Bundle(state).apply { putInt(key, value) }
        scope.launch { HyperImmersiveBridge.configure(context, key, value).takeIf { it.size() > 0 }?.let { state = it } }
    }

    fun openLsposed() {
        lsposedVisited = true
        restartedAt = 0L
        scope.launch { HyperCanvasBridge.openLsposed(context, HyperImmersiveRelease.PACKAGE) }
    }

    fun restartSystemUi(failed: String) {
        setupMessage = ""
        restartedAt = System.currentTimeMillis()
        scope.launch {
            if (!withContext(Dispatchers.IO + NonCancellable) { ModuleBridge.restartSystemUi() }) {
                restartedAt = 0L
                setupMessage = failed
            }
        }
    }

    suspend fun grant(): Boolean {
        val granted = HyperImmersiveBridge.grant()
        accept(HyperImmersiveBridge.query(context))
        return granted
    }
}

@Composable
internal fun HyperImmersiveUpdates(model: HyperImmersiveModel, isCurrent: Boolean) {
    LaunchedEffect(isCurrent) {
        model.wakes = 0
        if (isCurrent) while (true) {
            model.poll()
            delay(2000L)
        }
    }
}

@Composable
private fun StandbyGrantRow(model: HyperImmersiveModel) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var granting by remember { mutableStateOf(false) }
    val state = model.state
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
                if (!model.grant()) message = resources.getString(R.string.hyperimmersive_grant_failed)
                granting = false
            }
        })
}

@Composable
internal fun HyperImmersivePage(model: HyperImmersiveModel, isBlurEnabled: Boolean, extraBottomPadding: Dp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = model.state
    val enabled = model.enabled
    LaunchedEffect(model.release.installedVersion) { if (model.release.installedVersion == null) onBack() }
    PageScaffold(title = HyperImmersiveInfo.name, isBlurEnabled = isBlurEnabled,
        extraBottomPadding = extraBottomPadding, onBack = onBack,
        topGlow = rememberExtensionGlow(HyperImmersiveInfo, model.release.installedVersion)) {
        item { ExtensionHeader(HyperImmersiveInfo, state.getString("version") ?: model.release.installedVersion, model.status) }
        item {
            ExtensionGroup(null) {
                SwitchPreference(title = stringResource(R.string.hyperimmersive_title),
                    summary = stringResource(R.string.hyperimmersive_summary),
                    checked = enabled, onCheckedChange = { model.set("enabled", it) })
                ArrowPreference(title = stringResource(R.string.hyperimmersive_preview),
                    summary = stringResource(R.string.hyperimmersive_preview_summary),
                    onClick = { scope.launch { HyperImmersiveBridge.preview(context) } })
            }
        }
        item {
            ExtensionGroup(R.string.extension_group_appearance) {
                SwitchPreference(title = stringResource(R.string.hyperimmersive_blur),
                    summary = stringResource(R.string.hyperimmersive_blur_summary),
                    checked = state.getBoolean("blurBackdrop"), enabled = enabled,
                    onCheckedChange = { model.set("blurBackdrop", it) })
            }
        }
        item {
            ExtensionGroup(R.string.extension_group_lyrics) {
                SwitchPreference(title = stringResource(R.string.hyperimmersive_lyrics_beside),
                    summary = stringResource(R.string.hyperimmersive_lyrics_beside_summary),
                    checked = state.getBoolean("lyricsBeside"), enabled = enabled,
                    onCheckedChange = { model.set("lyricsBeside", it) })
                // Which way the lyric opens beside the cover; greyed out until that switch is on.
                WindowDropdownPreference(title = stringResource(R.string.hyperimmersive_lyrics_style),
                    items = listOf(stringResource(R.string.hyperimmersive_lyrics_style_thumb),
                        stringResource(R.string.hyperimmersive_lyrics_style_column)),
                    selectedIndex = state.getInt("lyricsStyle", 0).coerceIn(0, 1),
                    enabled = enabled && state.getBoolean("lyricsBeside"),
                    onSelectedIndexChange = { model.set("lyricsStyle", it) })
            }
        }
        item {
            ExtensionGroup(R.string.extension_group_timing) {
                StandbyTimeRow(title = stringResource(R.string.hyperimmersive_idle),
                    summary = stringResource(R.string.hyperimmersive_idle_summary),
                    stored = state.getInt("idleSeconds", 60), range = 10..3600,
                    enabled = enabled, onChange = { model.set("idleSeconds", it) })
                StandbyTimeRow(title = stringResource(R.string.hyperimmersive_off),
                    summary = stringResource(R.string.hyperimmersive_off_summary),
                    stored = state.getInt("offSeconds", 0), range = 10..86400,
                    enabled = enabled, onChange = { model.set("offSeconds", it) })
            }
        }
        // Only while something is missing; the list's card asks for the same.
        if (model.permissionsReady == false) item {
            ExtensionGroup(R.string.extension_group_permissions) { StandbyGrantRow(model) }
        }
        item {
            ExtensionManageGroup(HyperImmersiveInfo, model.release, R.string.hyperimmersive_uninstall_summary,
                R.string.hyperimmersive_removed, R.string.hyperimmersive_removed_restart_failed,
                R.string.hyperimmersive_remove_failed, uninstall = { c -> HyperImmersiveBridge.uninstall(c) },
                onRemoved = onBack)
        }
    }
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
