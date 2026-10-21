// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// ColorOS-style lock screen capsule, written for this project. Its geometry, springs and timings
// are modelled on the behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is
// included. If you reuse this file, keep this notice and the AGPL-3.0 terms (publish your source).
package com.os4.musiccover

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.ui.unit.dp

/**
 * The lock screen row as the module draws it, played on the island settings page: the torch,
 * the island and the camera on a lock screen strip. In the ColorOS style the switch runs on
 * ColorOsPill's own geometry - the same frame, metaball neck, ears and brackets the lock screen
 * draws - swiping to the next island and back. In the HyperOS style a small island comes in
 * beside the big one and goes again.
 */
@Composable
internal fun IslandDemo(colorOs: Boolean, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "island demo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "t")
    // The shortcuts' own glyphs, as the first island preview drew them.
    val torch = rememberVectorPainter(Icons.Rounded.FlashlightOn)
    val camera = rememberVectorPainter(Icons.Rounded.PhotoCamera)
    Canvas(modifier.fillMaxWidth().height(112.dp)) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2E3F5C), Color(0xFF18222F))),
            cornerRadius = CornerRadius(20.dp.toPx()))
        val d = size.height * 0.42f
        val cy = size.height * 0.56f
        val side = 20.dp.toPx()
        drawDisc(Offset(side + d / 2f, cy), d, torch)
        drawDisc(Offset(size.width - side - d / 2f, cy), d, camera)
        val gap = 22.dp.toPx()
        val rest = CoverMorphMotion.Box(side + d + gap, cy - d / 2f, size.width - 2f * (side + d + gap), d)
        if (colorOs) drawColorOs(rest, t) else drawHyperOs(rest, t)
    }
}

/** One loop: rest, swipe to the next island, rest, swipe back. */
private const val LOOP_MS = 6400


/** Glass lit from above, as the HyperOS material reads on a dark lock screen. */
private fun glass(top: Float, bottom: Float) =
    Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x26FFFFFF)), startY = top, endY = bottom)

/** A swipe: quick off the mark, settling in, as a finger lets a switch run out. */
private val SWIPE = CubicBezierEasing(0.3f, 0f, 0.15f, 1f)
private val INK = Color(0xE6FFFFFF)

private fun DrawScope.drawColorOs(rest: CoverMorphMotion.Box, t: Float) {
    val out = rest.h * 0.19f
    val stroke = rest.h * 0.05f
    // 0-0.15 rest, 0.15-0.45 to the next, 0.45-0.6 rest, 0.6-0.9 back, 0.9-1 rest.
    val toNext = t >= 0.15f && t < 0.45f
    val back = t >= 0.6f && t < 0.9f
    val shape = android.graphics.Path()
    if (!toNext && !back) {
        // The music at rest, the message while it is the one shown.
        val island = if (t >= 0.45f && t < 0.6f) 1 else 0
        shape.addRoundRect(android.graphics.RectF(rest.x, rest.y, rest.x + rest.w, rest.y + rest.h),
            rest.h / 2f, rest.h / 2f, android.graphics.Path.Direction.CW)
        ColorOsPill.neighbourPath(rest, right = false, grow = 1f, out = out, stroke = stroke, into = shape)
        ColorOsPill.neighbourPath(rest, right = true, grow = 1f, out = out, stroke = stroke, into = shape)
        drawPath(shape.asComposePath(), glass(rest.y, rest.y + rest.h))
        drawContent(island, rest, 1f)
        return
    }
    val u = SWIPE.transform(((if (toNext) t - 0.15f else t - 0.6f) / 0.3f).coerceIn(0f, 1f))
    val next = toNext
    val leaving = if (toNext) 0 else 1
    val arriving = 1 - leaving
    val f = ColorOsPill.frame(rest, u, next, 0f, out, stroke)
    val oldCapsule = f.oldBean < 0f
    val newCapsule = f.newBean < 0f
    if (oldCapsule && newCapsule) ColorOsPill.gooPath(f.new, f.old, shape, f.reach)
    else {
        if (oldCapsule) ColorOsPill.gooPath(f.old, null, shape)
        else ColorOsPill.earGlass(f.old, outerLeft = next, rest, stroke, shape)
        if (newCapsule) ColorOsPill.gooPath(f.new, null, shape)
        else ColorOsPill.earGlass(f.new, outerLeft = !next, rest, stroke, shape)
    }
    ColorOsPill.neighbourPath(rest, right = next, grow = f.bracket, out = out, stroke = stroke, into = shape)
    ColorOsPill.neighbourPath(rest, right = !next, grow = f.oldBracket, out = out, stroke = stroke, into = shape)
    drawPath(shape.asComposePath(), glass(rest.y, rest.y + rest.h))
    val roundAt = ColorOsPill.roundAt(rest, stroke)
    // Drawn in toward the end it hides at, grown out of the side it comes in on (gooShrink).
    if (oldCapsule) drawContent(leaving, f.old, ColorOsPill.oldContent(u).second, rest, toLeft = next)
    if (newCapsule) drawContent(arriving, f.new, ColorOsPill.newContent(u, roundAt).second, rest, toLeft = !next)
}

private fun DrawScope.drawHyperOs(rest: CoverMorphMotion.Box, t: Float) {
    // The small island comes in beside the big one over the first half and goes again.
    val share = when {
        t < 0.2f -> 0f
        t < 0.35f -> ease((t - 0.2f) / 0.15f)
        t < 0.7f -> 1f
        t < 0.85f -> 1f - ease((t - 0.7f) / 0.15f)
        else -> 0f
    }
    val h = rest.h
    val small = h * share
    val gap = h * 0.18f * share
    val big = CoverMorphMotion.Box(rest.x, rest.y, rest.w - small - gap, h)
    drawRoundRect(glass(big.y, big.y + big.h), Offset(big.x, big.y), Size(big.w, big.h), CornerRadius(h / 2f))
    drawContent(0, big, 1f)
    if (small > 1f) {
        val c = Offset(rest.x + rest.w - small / 2f, rest.cy())
        drawCircle(glass(c.y - small / 2f, c.y + small / 2f), small / 2f, c)
        drawCircle(Color(0xFF7FB2FF).copy(alpha = share), small * 0.28f, c)
    }
}

/** An island's content in [box]: the music (artwork, two lines, play) or a message (avatar, two lines). */
private fun DrawScope.drawContent(island: Int, box: CoverMorphMotion.Box, alpha: Float,
                                  rest: CoverMorphMotion.Box? = null, toLeft: Boolean = true) {
    if (alpha <= 0.01f) return
    if (rest != null) {
        val k = (box.w / rest.w).coerceIn(0.2f, 1f)
        val pivot = Offset(if (toLeft) box.x + box.h / 2f else box.x + box.w - box.h / 2f, box.cy())
        // Laid out from its frame's left at the rest width, as the pill's content is, and scaled
        // with the glass toward its end.
        val laid = CoverMorphMotion.Box(box.x, box.y, rest.w, box.h)
        val clip = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(box.x, box.y, box.x + box.w, box.y + box.h,
                CornerRadius(minOf(box.w, box.h) / 2f)))
        }
        clipPath(clip) { scale(k, k, pivot) { drawContent(island, laid, alpha) } }
        return
    }
    val h = box.h
    val clip = androidx.compose.ui.graphics.Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(box.x, box.y, box.x + box.w, box.y + box.h,
            CornerRadius(minOf(box.w, h) / 2f)))
    }
    clipPath(clip) {
        val pad = h * 0.18f
        val icon = h - 2f * pad
        val left = box.x + pad
        if (island == 0) {
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFFFFB86B), Color(0xFFE2557A)),
                Offset(left, box.y + pad), Offset(left + icon, box.y + pad + icon)),
                Offset(left, box.y + pad), Size(icon, icon), CornerRadius(icon * 0.22f), alpha = alpha)
            val px = box.x + box.w - pad - icon / 2f
            drawCircle(INK, icon * 0.28f, Offset(px, box.cy()), alpha = alpha)
        } else {
            drawCircle(Color(0xFF6FD39A), icon / 2f, Offset(left + icon / 2f, box.cy()), alpha = alpha)
        }
        val tx = left + icon + pad * 0.8f
        val tw = (box.x + box.w - tx - pad - if (island == 0) icon else 0f) .coerceAtLeast(0f)
        val bar = h * 0.11f
        drawRoundRect(INK, Offset(tx, box.cy() - bar * 1.6f), Size(tw * 0.75f, bar), CornerRadius(bar / 2f), alpha = alpha)
        drawRoundRect(INK.copy(alpha = 0.55f), Offset(tx, box.cy() + bar * 0.6f), Size(tw * 0.5f, bar),
            CornerRadius(bar / 2f), alpha = alpha)
    }
}

private fun DrawScope.drawDisc(c: Offset, d: Float, glyph: Painter) {
    drawCircle(glass(c.y - d / 2f, c.y + d / 2f), d / 2f, c)
    val g = d * 0.5f
    translate(c.x - g / 2f, c.y - g / 2f) {
        with(glyph) { draw(Size(g, g), colorFilter = ColorFilter.tint(INK)) }
    }
}

private fun ease(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}
