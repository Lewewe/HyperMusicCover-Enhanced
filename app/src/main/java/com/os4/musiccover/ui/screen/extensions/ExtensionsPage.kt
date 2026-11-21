package com.os4.musiccover.ui.screen.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.os4.musiccover.R
import com.os4.musiccover.ui.util.PageScaffold

/**
 * The Extensions tab: every extension's card, and the page of the one opened. Each extension's
 * state lives here, read once for both, while the tab is shown.
 */
@Composable
internal fun ExtensionsPageView(canvas: HyperCanvasModel, standby: HyperImmersiveModel,
    isBlurEnabled: Boolean, isCurrent: Boolean, extraBottomPadding: Dp) {
    val context = LocalContext.current
    val resources = LocalResources.current
    HyperCanvasUpdates(canvas, isCurrent)
    HyperImmersiveUpdates(standby, isCurrent)
    LaunchedEffect(isCurrent) {
        if (isCurrent) {
            canvas.release.refreshLocal(context)
            standby.release.refreshLocal(context)
            canvas.release.check(resources)
            standby.release.check(resources)
        }
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = isCurrent && selected != null) { selected = null }
    AnimatedContent(targetState = selected, label = "Extension navigation",
        transitionSpec = {
            val direction = if (targetState != null) 1 else -1
            (slideInHorizontally(tween(280)) { it / 6 * direction } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(280)) { -it / 8 * direction } + fadeOut(tween(160)))
        }) { page ->
        val back = { selected = null }
        when (page) {
            HyperCanvasInfo.name -> HyperCanvasPage(canvas, isBlurEnabled, extraBottomPadding, back)
            HyperImmersiveInfo.name -> HyperImmersivePage(standby, isBlurEnabled, extraBottomPadding, back)
            else -> PageScaffold(title = stringResource(R.string.tab_extensions), isBlurEnabled = isBlurEnabled,
                extraBottomPadding = extraBottomPadding, onBack = null) {
                item {
                    ExtensionHeroCard(HyperCanvasInfo, canvas.release, stringResource(R.string.hypercanvas_list_summary),
                        canvas.cardSetup, onOpen = { if (canvas.canOpen) selected = HyperCanvasInfo.name },
                        needsScoped = canvas.scoped, animateButton = isCurrent)
                }
                item {
                    ExtensionHeroCard(HyperImmersiveInfo, standby.release, stringResource(R.string.hyperimmersive_list_summary),
                        standby.cardSetup, onOpen = { if (standby.canOpen) selected = HyperImmersiveInfo.name },
                        animateButton = isCurrent)
                }
            }
        }
    }
}
