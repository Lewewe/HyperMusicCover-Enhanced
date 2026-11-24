package com.os4.musiccover.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.os4.musiccover.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The frame every page in this app is built in.
 *
 * It was copy-pasted six times before it was a function, and the copies had drifted: one page
 * put its top inset on the list's own modifier instead of in `contentPadding`, which shortens
 * the list to below the bar - so nothing scrolls under it, the backdrop has nothing to sample up
 * there, and that page's bar stops matching every other page's. That is the failure mode this
 * exists to make impossible rather than merely unlikely.
 *
 * What it fixes, in order: the backdrop and whether the bar is transparent, the Scaffold and its
 * window insets, the `BlurredBar`/`TopAppBar` pair, the single `layerBackdrop` over the content,
 * and the list with `overscrollEffect = null` (which miuix's damped overscroll requires - see
 * [pageScrollModifiers]) and the top inset in the right place.
 *
 * The one thing callers still own is what goes in the list.
 */
@Composable
fun PageScaffold(
    title: String,
    isBlurEnabled: Boolean,
    extraBottomPadding: Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    /**
     * Drawn above the list and OUTSIDE its scroll area.
     *
     * For the one page whose subject is the thing being adjusted: KernelSU puts its preview as
     * the list's first item and lets it scroll away, which is fine when the controls are short
     * but wrong when the preview is what the user is watching while they drag. Supplying this
     * also moves the top inset onto the wrapping column, because a list that cannot reach the
     * bar is exactly what has to happen when something is pinned above it - and that is the
     * arrangement the other branch deliberately avoids.
     */
    pinned: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * A soft glow of this colour from the top of the screen, behind everything, and the small bar
     * with no large title: for a page whose own header already names it.
     */
    topGlow: Color? = null,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()
    val blurActive = isBlurEnabled && backdrop != null
    val barColor = if (blurActive || topGlow != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        popupHost = { },
        topBar = {
            // Over a top glow the bar's dark blend read as a black band at rest: it comes in as the
            // list scrolls under it instead.
            val barFadePx = with(LocalDensity.current) { 96.dp.toPx() }
            BlurredBar(backdrop, blurActive, scrollBehavior, fadeIn = topGlow?.let { {
                if (listState.firstVisibleItemIndex > 0) 1f
                else (listState.firstVisibleItemScrollOffset / barFadePx).coerceIn(0f, 1f)
            } }) {
                if (topGlow != null) SmallTopAppBar(
                    title = "",
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { if (onBack != null) PageBackButton(onBack) },
                    actions = { actions() },
                ) else TopAppBar(
                    title = title,
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = { if (onBack != null) PageBackButton(onBack) },
                    actions = { actions() },
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = (if (blurActive) Modifier.layerBackdrop(backdrop) else Modifier)
            .then(if (topGlow == null) Modifier else Modifier.drawBehind {
                // Fades out over the first ~third of a screen of scrolling; read here so only the draw reruns.
                val scrolled = if (listState.firstVisibleItemIndex > 0) 1f
                    else listState.firstVisibleItemScrollOffset / (size.height / 3f)
                val fade = 1f - scrolled.coerceIn(0f, 1f)
                if (fade <= 0f) return@drawBehind
                // A soft light from just above the screen: eased stops so there is no visible rim.
                val center = Offset(size.width / 2f, -size.width * .15f)
                val r = size.width * .95f
                scale(1.15f, .75f, center) {
                    drawCircle(Brush.radialGradient(
                        0f to topGlow.copy(alpha = .42f * fade),
                        .35f to topGlow.copy(alpha = .22f * fade),
                        .7f to topGlow.copy(alpha = .06f * fade),
                        1f to Color.Transparent,
                        center = center, radius = r), radius = r, center = center)
                }
            })) {
            val bottom = innerPadding.calculateBottomPadding() + extraBottomPadding
            if (pinned == null) {
                PageList(
                    state = listState,
                    scrollBehavior = scrollBehavior,
                    contentPadding = PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = bottom,
                    ),
                    content = content,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding()),
                    // Centred, which is where a pinned preview goes: it is a picture of a phone,
                    // and the page's own layout puts everything else around its middle.
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    pinned()
                    PageList(
                        state = listState,
                        scrollBehavior = scrollBehavior,
                        contentPadding = PaddingValues(bottom = bottom),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageList(
    state: LazyListState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        state = state,
        overscrollEffect = null,
        modifier = Modifier
            .fillMaxSize()
            .pageScrollModifiers(showTopAppBar = true, topAppBarScrollBehavior = scrollBehavior),
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * The back arrow, which was the same eight lines on three pages.
 *
 * `contentDescription` is the licence page's string and stays it: it is what that page has always
 * announced, odd as the name reads, and every other page now says the same thing because they are
 * the same control.
 */
@Composable
fun PageBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        val layoutDirection = LocalLayoutDirection.current
        Icon(
            modifier = Modifier.graphicsLayer {
                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
            },
            imageVector = MiuixIcons.Back,
            contentDescription = stringResource(R.string.rules_cancel),
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}
