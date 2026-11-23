// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Native drawing and timing live in CapsuleDemoCanvas.kt.
package com.os4.musiccover

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val guideTitles = intArrayOf(R.string.mini_demo_list, R.string.mini_demo_stack, R.string.mini_demo_capsule, R.string.mini_demo_hyperos, R.string.mini_demo_hyperos_list)
private val guideSummaries = intArrayOf(R.string.mini_demo_list_summary, R.string.mini_demo_stack_summary,
    R.string.mini_demo_capsule_summary, R.string.mini_demo_hyperos_summary, R.string.mini_demo_hyperos_list_summary)

@Composable
internal fun IslandDemo(colorOs: Boolean, allowStack: Boolean) {
    val pages = if (!colorOs) listOf(4, 3) else if (allowStack) listOf(0, 1, 2) else listOf(0, 2)
    val pager = key(colorOs, allowStack) { rememberPagerState { pages.size } }
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    // ColorOS's guide measures every page and uses the tallest page's height.
    key(colorOs, allowStack) {
        HorizontalPager(state = pager, beyondViewportPageCount = pages.lastIndex,
            key = { pages[it] }) { page ->
            val guide = pages[page]
            Column(Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                CapsuleDemoCanvas(guide, Modifier.width(180.dp).height(194.dp),
                    playing = pager.settledPage == page)
                Text(stringResource(guideTitles[guide]), fontWeight = FontWeight.Medium,
                    fontSize = 16.sp, lineHeight = 18.5.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).padding(top = 24.dp))
                Text(stringResource(guideSummaries[guide]), color = colors.onSurfaceVariantSummary,
                    fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp).padding(top = 8.dp))
            }
        }
    }
    if (pages.size == 1) {
        Box(Modifier.height(26.dp))
        return
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp).selectableGroup(),
        horizontalArrangement = Arrangement.Center) {
        pages.forEachIndexed { index, guide ->
            val selected = pager.currentPage == index
            val label = stringResource(guideTitles[guide])
            Box(
                Modifier.size(14.dp, 22.dp).selectable(selected, role = Role.Tab,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } })
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(if (selected) 7.dp else 6.dp)
                    .background(if (selected) colors.primary else colors.onSurfaceVariantSummary, CircleShape))
            }
        }
    }
}

@Composable
internal fun NotificationStylePicker(selectedStyle: Int, allowStack: Boolean, enabled: Boolean,
    onSelect: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp)
        .background(colors.onSurfaceVariantSummary.copy(alpha = 0.36f)))
    Text(stringResource(R.string.mini_default_notification_style), fontWeight = FontWeight.Medium,
        fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 16.dp))
    val styles = listOf(MiniPlayerConfig.NOTIFICATIONS_CAPSULE to R.string.mini_notifications_capsule,
        MiniPlayerConfig.NOTIFICATIONS_STACK to R.string.mini_notifications_stack,
        MiniPlayerConfig.NOTIFICATIONS_LIST to R.string.mini_notifications_list)
    val stackProgress by animateFloatAsState(if (allowStack) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 438.65f),
        label = "bottom stack")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 16.dp)
        .selectableGroup(), contentAlignment = Alignment.TopCenter) {
        val tileWidth = (maxWidth - 12.dp).coerceAtLeast(0.dp) / 3
        val outerOffset = maxWidth / 4
        val shownOffset = tileWidth + 6.dp
        for ((index, option) in styles.withIndex()) {
            val (style, title) = option
            val stack = style == MiniPlayerConfig.NOTIFICATIONS_STACK
            if (stack && !allowStack && stackProgress <= 0f) continue
            val selected = selectedStyle == style
            val activeColor = if (enabled) colors.primary else colors.onSurfaceVariantSummary
            Column(Modifier.width(tileWidth)
                .offset {
                    val distance = outerOffset + (shownOffset - outerOffset) * stackProgress
                    IntOffset((distance * (index - 1)).roundToPx(), 0)
                }
                .graphicsLayer { alpha = if (stack) stackProgress else 1f }
                .clip(RoundedCornerShape(12.dp))
                .selectable(selected, enabled && (!stack || allowStack), Role.RadioButton) { onSelect(style) }
                .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val guide = when (style) {
                    MiniPlayerConfig.NOTIFICATIONS_STACK -> 1
                    MiniPlayerConfig.NOTIFICATIONS_LIST -> 0
                    else -> 2
                }
                CapsuleDemoCanvas(guide,
                    Modifier.widthIn(max = 102.dp).fillMaxWidth(0.85f).aspectRatio(288f / 634f),
                    playing = false, preview = true)
                Text(stringResource(title), modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                    textAlign = TextAlign.Center, color = if (selected) activeColor else colors.onSurfaceVariantSummary)
                Canvas(Modifier.size(19.dp)) {
                    val ringWidth = if (selected) 2.dp.toPx() else 1.5.dp.toPx()
                    drawCircle(if (selected) activeColor else colors.onSurfaceVariantSummary.copy(alpha = 0.65f),
                        radius = size.minDimension / 2f - ringWidth / 2f, style = Stroke(ringWidth))
                    if (selected) drawCircle(activeColor, radius = size.minDimension * 0.23f)
                }
            }
        }
    }
}
