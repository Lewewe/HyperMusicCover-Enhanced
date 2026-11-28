package com.os4.musiccover.ui.screen.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.os4.musiccover.ModuleBridge
import com.os4.musiccover.R
import com.os4.musiccover.ui.util.PageScaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** The MIX Flip's outer screen: what its lock screen clock row does. */
@Composable
internal fun FlipPageView(
    isBlurEnabled: Boolean,
    refreshKey: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var module by remember { mutableStateOf(ModuleBridge.State()) }
    LaunchedEffect(refreshKey) { module = ModuleBridge.queryAlive(context) }
    PageScaffold(
        title = stringResource(R.string.flip_title),
        isBlurEnabled = isBlurEnabled,
        onBack = onBack,
    ) {
        item {
            Column {
                SmallTitle(text = stringResource(R.string.flip_outer_section))
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    SwitchPreference(
                        title = stringResource(R.string.flip_clock),
                        summary = stringResource(R.string.flip_clock_summary),
                        checked = module.flipClock,
                        enabled = module.alive,
                        onCheckedChange = {
                            module = module.copy(flipClock = it)
                            ModuleBridge.setFlip(context, "clock", it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.flip_status),
                        summary = stringResource(R.string.flip_status_summary),
                        checked = module.flipStatus,
                        enabled = module.alive,
                        onCheckedChange = {
                            module = module.copy(flipStatus = it)
                            ModuleBridge.setFlip(context, "status", it)
                        },
                    )
                    SwitchPreference(
                        title = stringResource(R.string.flip_face_left),
                        summary = stringResource(R.string.flip_face_left_summary),
                        checked = module.flipFaceLeft,
                        enabled = module.alive,
                        onCheckedChange = {
                            module = module.copy(flipFaceLeft = it)
                            ModuleBridge.setFlip(context, "faceleft", it)
                        },
                    )
                }
            }
        }
    }
}
