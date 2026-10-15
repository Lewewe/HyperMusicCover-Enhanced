package com.os4.musiccover.ui.screen.features

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HsvColorPicker
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import top.yukonga.miuix.kmp.basic.TabRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.os4.musiccover.MediaCardConfig
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.util.PageScaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@Composable
internal fun MediaCardPageView(isBlurEnabled: Boolean, refreshKey: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    var module by remember { mutableStateOf(ModuleBridge.State()) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val lost by ModuleBridge.lost.collectAsState()
    LaunchedEffect(refreshKey, lost) { module = ModuleBridge.queryAlive(context) }
    val values = remember(module.mediaCardConfig) { MediaCardConfig.parse(module.mediaCardConfig) }
    // Several keys in one state update: one at a time, each would start from the same stale map.
    val pushAll: (List<Pair<String, Int>>) -> Unit = { changes ->
        module = module.copy(mediaCardConfig = MediaCardConfig.encode(values + changes))
        changes.forEach { (key, value) -> ModuleBridge.setMediaCardStyle(context, key, value) }
    }
    val push: (String, Int) -> Unit = { key, value -> pushAll(listOf(key to value)) }
    PageScaffold(title = stringResource(R.string.media_customization_title), isBlurEnabled = isBlurEnabled, onBack = onBack,
        pinned = {
            TabRow(tabs = listOf(stringResource(R.string.media_visualization_tab), stringResource(R.string.media_appearance_tab)),
                selectedTabIndex = selectedTab, onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
        }) {
        item { Text(stringResource(R.string.media_style_note), modifier = Modifier.padding(16.dp)) }
        if (selectedTab == 0) {
            item { SmallTitle(stringResource(R.string.media_super_island_wave)) }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    VisualizerBetaSwitch(values.getValue("island.visualizer") != 0, module.alive) { push("island.visualizer", if (it) 1 else 0) }
                    if (values.getValue("island.visualizer") != 0) {
                        WaveColorChoice(values, module.alive, pushAll)
                        MediaSwitch("island", "btSync", R.string.media_wave_bt_sync, values, module.alive, push, R.string.media_wave_bt_sync_summary)
                        if (values.getValue("island.btSync") != 0) {
                            ValueSlider(title = stringResource(R.string.media_wave_bt_adjustment),
                                summary = stringResource(R.string.media_wave_bt_adjustment_summary),
                                value = values.getValue("island.btOffset").toFloat(), valueRange = -250f..250f,
                                enabled = module.alive, detent = 0f, label = { "${it.toInt()} ms" },
                                onValueChange = { push("island.btOffset", it.toInt()) })
                        }
                        MediaSwitch("island", "hideWaveDevice", R.string.media_wave_hide_device, values, module.alive, push, R.string.media_wave_hide_device_summary)
                        MediaSwitch("notification", "lockOutputWave", R.string.media_wave_lock_screen, values, module.alive, push, R.string.media_output_wave_summary)
                        MediaSwitch("notification", "outputWave", R.string.media_wave_notification, values, module.alive, push, R.string.media_output_wave_summary)
                        MediaSwitch("island", "outputWave", R.string.media_wave_island, values, module.alive, push, R.string.media_output_wave_summary)
                    }
                }
            }
        } else {
            item { SmallTitle(stringResource(R.string.media_scroll_text)) }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    MediaSwitch("notification", "scrollText", R.string.media_scope_notification,
                        values, module.alive, push, R.string.media_scroll_text_summary)
                    MediaSwitch("island", "scrollText", R.string.media_scope_island,
                        values, module.alive, push, R.string.media_scroll_text_summary)
                }
            }
            for (scope in MediaCardConfig.scopes) {
                item { SmallTitle(stringResource(if (scope == "notification") R.string.media_scope_notification else R.string.media_scope_island)) }
                item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                        MediaChoice(scope, "cover", R.string.media_style_cover,
                            listOf(R.string.media_style_native, R.string.media_style_circle, R.string.media_style_rotating, R.string.media_style_hidden), values, module.alive, push)
                        MediaSwitch(scope, "hideSource", R.string.media_hide_source, values, module.alive, push)
                        MediaSwitch(scope, "hideDevice", R.string.media_hide_device, values, module.alive, push)
                    }
                }
                item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 16.dp)) {
                        MediaChoice(scope, "background", R.string.media_background_style,
                            listOf(R.string.media_style_native, R.string.media_background_mosaic, R.string.media_background_blur,
                                R.string.media_background_radial, R.string.media_background_linear, R.string.media_background_soft), values, module.alive, push)
                        val style = values.getValue("$scope.background")
                        if (style == 0) {
                            MediaChoice(scope, "theme", R.string.media_theme,
                                listOf(R.string.media_theme_system, R.string.media_theme_light, R.string.media_theme_dark), values, module.alive, push)
                            MediaChoice(scope, "flow", R.string.media_flow,
                                listOf(R.string.media_style_native, R.string.media_flow_dynamic, R.string.media_flow_cover, R.string.media_flow_artwork, R.string.media_flow_disabled), values, module.alive, push)
                            if (values.getValue("$scope.flow") in 1..3) {
                                MediaSwitch(scope, "pauseRestore", R.string.media_pause_restore, values, module.alive, push, R.string.media_pause_restore_summary)
                            }
                        }
                        if (style == 5) {
                            MediaChoice(scope, "tone", R.string.media_background_tone,
                                listOf(R.string.media_theme_light, R.string.media_theme_dark), values, module.alive, push)
                        }
                        if (style > 0 || values.getValue("$scope.flow") in 1..3) {
                            MediaSwitch(scope, "animate", R.string.media_background_animate, values, module.alive, push)
                        }
                        if (style == 2) {
                            ValueSlider(title = stringResource(R.string.media_background_amount),
                                value = values.getValue("$scope.blur").toFloat(), valueRange = 1f..20f,
                                enabled = module.alive, detent = 8f, label = { it.toInt().toString() },
                                onValueChange = { push("$scope.blur", it.toInt()) })
                        }
                        if (style == 4) MediaSwitch(scope, "invert", R.string.media_background_invert, values, module.alive, push)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaChoice(scope: String, name: String, title: Int, options: List<Int>, values: Map<String, Int>, enabled: Boolean, push: (String, Int) -> Unit) {
    WindowDropdownPreference(title = stringResource(title), items = options.map { stringResource(it) },
        selectedIndex = values.getValue("$scope.$name"), enabled = enabled,
        onSelectedIndexChange = { push("$scope.$name", it) })
}

@Composable
private fun MediaSwitch(scope: String, name: String, title: Int, values: Map<String, Int>, enabled: Boolean,
                        push: (String, Int) -> Unit, summary: Int? = null) {
    SwitchPreference(title = stringResource(title), summary = summary?.let { stringResource(it) },
        checked = values.getValue("$scope.$name") != 0, enabled = enabled,
        onCheckedChange = { push("$scope.$name", if (it) 1 else 0) })
}


@Composable
private fun VisualizerBetaSwitch(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val color = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch,
        onValueChange = onCheckedChange).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.media_audio_wave), modifier = Modifier.weight(1f, fill = false),
                    fontSize = 17.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .5f))
                Text("Beta", fontSize = 11.sp, color = color,
                    modifier = Modifier.border(1.dp, color.copy(alpha = .6f), RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 2.dp))
            }
            Text(stringResource(R.string.media_audio_wave_summary), fontSize = 14.sp,
                color = color, modifier = Modifier.padding(top = 4.dp))
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/** The bars' colour: the artwork gradient, white, black, or a colour picked in a dialog. */
@Composable
private fun WaveColorChoice(values: Map<String, Int>, enabled: Boolean, push: (List<Pair<String, Int>>) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val mode = values.getValue("island.waveColor")
    val custom = values.getValue("island.waveCustom")
    WindowDropdownPreference(title = stringResource(R.string.media_wave_color),
        items = listOf(R.string.media_wave_color_default, R.string.media_wave_color_white,
            R.string.media_wave_color_dark, R.string.media_wave_color_custom).map { stringResource(it) },
        selectedIndex = mode, enabled = enabled,
        onSelectedIndexChange = {
            // Custom is kept only once a colour is saved; until then the old choice stands.
            if (it == MediaCardConfig.WAVE_CUSTOM) picking = true else push(listOf("island.waveColor" to it))
        })
    if (mode == MediaCardConfig.WAVE_CUSTOM) {
        ArrowPreference(title = stringResource(R.string.media_wave_color_pick),
            summary = "#%08X".format(custom), enabled = enabled,
            endActions = {
                Box(Modifier.padding(end = 8.dp).size(22.dp).clip(CircleShape).background(Color(custom))
                    .border(1.dp, MiuixTheme.colorScheme.dividerLine, CircleShape))
            },
            onClick = { picking = true })
    }
    if (picking) {
        var color by remember { mutableStateOf(Color(custom)) }
        WindowDialog(show = true, title = stringResource(R.string.media_wave_color_pick),
            onDismissRequest = { picking = false }) {
            val dismiss = LocalDismissState.current
            Column(Modifier.fillMaxWidth()) {
                HsvColorPicker(color = color, onColorChanged = { color = it })
                TextButton(text = stringResource(R.string.media_wave_color_save),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        push(listOf("island.waveCustom" to color.toArgb(),
                            "island.waveColor" to MediaCardConfig.WAVE_CUSTOM))
                        dismiss?.invoke()
                    })
                TextButton(text = stringResource(R.string.media_wave_color_cancel),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = { dismiss?.invoke() })
            }
        }
    }
}

