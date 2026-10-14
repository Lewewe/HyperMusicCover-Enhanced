package com.os4.musiccover.ui.screen.experiments

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.os4.musiccover.IslandGesturePolicy
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.screen.features.ValueSlider
import com.os4.musiccover.ui.util.PageScaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * The A/B tab is internal: hidden until the app icon on About is tapped [TAPS] times in a row.
 * Kept in the app's own settings, and published so the tab bar picks it up without a restart.
 */
object ExperimentsUnlock {
    const val TAPS = 7
    /** The countdown toast starts at this tap. */
    const val SHOW_FROM = 3
    /** Longest pause between two taps that still counts as the same run. */
    const val TAP_WINDOW_MS = 2000L
    private const val PREFS_NAME = "app_settings"
    private const val KEY = "ab_tab_unlocked"
    private val unlocked = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)

    fun state(context: android.content.Context): kotlinx.coroutines.flow.StateFlow<Boolean?> {
        if (unlocked.value == null) unlocked.value = context.getSharedPreferences(
            PREFS_NAME, android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)
        return unlocked
    }

    fun unlock(context: android.content.Context) {
        context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, true).apply()
        unlocked.value = true
    }
}

@Composable
internal fun ExperimentsPageView(isBlurEnabled: Boolean, isCurrent: Boolean,
    resumeKey: Int, extraBottomPadding: Dp) {
    val context = LocalContext.current
    var module by remember { mutableStateOf(ModuleBridge.State()) }
    val lost by ModuleBridge.lost.collectAsState()
    LaunchedEffect(isCurrent, resumeKey, lost) {
        if (isCurrent) module = ModuleBridge.queryAlive(context)
    }
    PageScaffold(title = stringResource(R.string.tab_experiments),
        isBlurEnabled = isBlurEnabled, extraBottomPadding = extraBottomPadding) {
        item {
            SmallTitle(text = stringResource(R.string.experiments_iphone))
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                SwitchPreference(
                    title = stringResource(R.string.island_iphone_gestures),
                    summary = stringResource(R.string.island_iphone_gestures_summary),
                    checked = module.islandIphoneGestures,
                    enabled = module.alive,
                    onCheckedChange = {
                        module = module.copy(islandIphoneGestures = it)
                        ModuleBridge.setIslandIphoneGestures(context, it)
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.island_iphone_top),
                    summary = stringResource(R.string.island_iphone_top_summary),
                    checked = module.islandIphoneTop,
                    enabled = module.alive,
                    onCheckedChange = {
                        module = module.copy(islandIphoneTop = it)
                        ModuleBridge.setIslandIphoneTop(context, it)
                    },
                )
                ValueSlider(
                    title = stringResource(R.string.island_iphone_radius),
                    summary = stringResource(R.string.island_iphone_radius_summary),
                    value = module.islandIphoneRadius.toFloat(),
                    valueRange = IslandGesturePolicy.MIN_RADIUS_DP.toFloat()..
                        IslandGesturePolicy.MAX_RADIUS_DP.toFloat(),
                    enabled = module.alive && module.islandIphoneTop,
                    detent = IslandGesturePolicy.DEFAULT_RADIUS_DP.toFloat(),
                    label = { "${it.toInt()} dp" },
                    onValueChange = {
                        val dp = it.toInt()
                        module = module.copy(islandIphoneRadius = dp)
                        ModuleBridge.setIslandIphoneRadius(context, dp)
                    },
                )
            }
        }
    }
}
