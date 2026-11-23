// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
package com.os4.musiccover

import android.graphics.Canvas as NativeCanvas
import android.graphics.Color as NativeColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Original guide coordinates and 60 Hz timeline, evaluated continuously at the display rate. */
internal object CapsuleDemoMotion {
    val lengths = intArrayOf(171, 265, 358, 324, 300)
    private val ease = CubicBezierEasing(.29f, 0f, .17f, 1f)
    fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun smooth(t: Float): Float {
        val p = t.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
    fun glide(frame: Float, start: Float, end: Float) = smooth((frame - start) / (end - start))
    fun progress(frame: Float, start: Float, end: Float) =
        ease.transform(((frame - start) / (end - start)).coerceIn(0f, 1f))

    fun hyperOsListProgress(frame: Float) =
        spring(frame, .6f, .75f) - spring(frame, 3f, .65f)

    // Remotion measures the natural spring at 60 Hz before fitting it to the requested duration.
    private fun response(t: Double, damping: Double): Double {
        val w = 2 * PI / .45
        val wd = w * sqrt(1 - damping * damping)
        return 1 - exp(-damping * w * t) * (cos(wd * t) + damping * w / wd * sin(wd * t))
    }
    private fun naturalFrames(damping: Double): Int {
        var frame = 0
        while (kotlin.math.abs(1 - response(frame / 60.0, damping)) >= .005) frame++
        var finished = frame
        var stable = 0
        while (stable < 20) {
            frame++
            if (kotlin.math.abs(1 - response(frame / 60.0, damping)) >= .005) {
                stable = 0
                finished = frame + 1
            } else stable++
        }
        return finished
    }
    private val normalFrames = naturalFrames(.75)
    private val touchFrames = naturalFrames(.99)
    fun spring(frame: Float, start: Float, duration: Float = .7f, touch: Boolean = false): Float {
        val elapsed = frame / 60f - start
        if (elapsed <= 0) return 0f
        if (elapsed >= duration) return 1f
        val natural = if (touch) touchFrames else normalFrames
        return response(elapsed / duration * natural / 60.0, if (touch) .99 else .75).toFloat()
    }
}

@Composable
internal fun CapsuleDemoCanvas(guide: Int, modifier: Modifier, playing: Boolean, preview: Boolean = false) {
    val renderer = remember { CapsuleDemoRenderer() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var frame by remember(guide, preview) {
        mutableFloatStateOf(if (preview) floatArrayOf(0f, 75f, 72f)[guide] else 0f)
    }
    LaunchedEffect(guide, playing, lifecycle) {
        if (!playing) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (isActive) withFrameNanos { now ->
                if (previous != 0L) {
                    frame = (frame + (now - previous) / 1_000_000_000f * 60f) %
                        CapsuleDemoMotion.lengths[guide]
                }
                previous = now
            }
        }
    }
    Canvas(modifier) {
        val width = if (preview) 288f else 600f
        val height = if (preview) 634f else 650f
        val scale = min(size.width / width, size.height / height)
        drawIntoCanvas {
            val canvas = it.nativeCanvas
            val save = canvas.save()
            canvas.translate((size.width - width * scale) / 2, (size.height - height * scale) / 2)
            canvas.scale(scale, scale)
            canvas.clipRect(0f, 0f, width, height)
            if (preview) canvas.translate(-156f, -8f)
            renderer.draw(canvas, guide, frame, preview)
            canvas.restoreToCount(save)
        }
    }
}

private class CapsuleDemoRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val clip = Path()
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regular
        fontVariationSettings = "'wght' 400"
        textAlign = Paint.Align.CENTER
    }
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(regular, 500, false)
        fontVariationSettings = "'wght' 500"
        textAlign = Paint.Align.CENTER
    }
    private val screen = CoverMorphMotion.Box(160f, 12f, 280f, 626f)
    private val capsule = CoverMorphMotion.Box(241f, 575f, 118f, 34f)
    private val nodes = Array(4) { RenderNode("Capsule demo content $it") }
    private val wallpaper = LinearGradient(160f, 12f, 398f, 638f,
        intArrayOf(0xff567ca7.toInt(), 0xff4e88a9.toInt(), 0xff438e8e.toInt()),
        floatArrayOf(0f, .46f, 1f), Shader.TileMode.CLAMP)
    private val highlight = LinearGradient(160f, 306f, 440f, 638f,
        intArrayOf(0x29d1f5ff, 0x00e1fff0), null, Shader.TileMode.CLAMP)
    private val glass = LinearGradient(0f, 117.6f, 0f, 258.72f,
        0x4dffffff, 0x26ffffff, Shader.TileMode.CLAMP)

    private fun mix(a: Float, b: Float, p: Float) = CapsuleDemoMotion.mix(a, b, p)
    private fun p(f: Float, a: Float, b: Float) = CapsuleDemoMotion.progress(f, a, b)
    private fun g(f: Float, a: Float, b: Float) = CapsuleDemoMotion.glide(f, a, b)

    private fun fill(color: Int, alpha: Float = 1f, shader: Shader? = null) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (NativeColor.alpha(color) * alpha.coerceIn(0f, 1f)).toInt()
        paint.shader = shader
    }
    private fun rect(c: NativeCanvas, x: Float, y: Float, w: Float, h: Float, r: Float,
                     color: Int, alpha: Float = 1f, shader: Shader? = null) {
        if (w <= 0 || h <= 0 || alpha <= 0) return
        fill(color, alpha, shader)
        c.drawRoundRect(x, y, x + w, y + h, r, r, paint)
    }
    private fun circle(c: NativeCanvas, x: Float, y: Float, r: Float, color: Int, alpha: Float = 1f) {
        fill(color, alpha)
        c.drawCircle(x, y, r, paint)
    }
    private fun line(c: NativeCanvas, points: Path, color: Int, width: Float, alpha: Float = 1f) {
        fill(color, alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        c.drawPath(points, paint)
        paint.style = Paint.Style.FILL
    }
    private fun text(c: NativeCanvas, value: String, x: Float, y: Float, size: Float,
                     color: Int, weight: Boolean = false, fitWidth: Float? = null) {
        // Keep each weight on its own paint: resetting a variable typeface can drop its axes.
        val textPaint = if (weight) clockPaint else labelPaint
        textPaint.color = color
        textPaint.textSize = size
        val save = c.save()
        if (fitWidth != null) c.scale(fitWidth / textPaint.measureText(value), 1f, x, y)
        c.drawText(value, x, y, textPaint)
        c.restoreToCount(save)
    }

    fun draw(c: NativeCanvas, guide: Int, frame: Float, preview: Boolean) {
        val zoom = when {
            preview -> 0f
            guide == 2 || guide == 3 -> p(frame, 26f, 61f) - p(frame, 280f, 319f)
            else -> 0f
        }
        c.scale(1 + .94f * zoom, 1 + .94f * zoom, 300f, 640f)
        rect(c, 156f, 8f, 288f, 634f, 44f, 0xff51606f.toInt())
        path.reset()
        path.addRoundRect(156f, 8f, 444f, 642f, 44f, 44f, Path.Direction.CW)
        line(c, path, 0xff93a5b1.toInt(), 1.5f, .65f)
        clip.reset()
        clip.addRoundRect(screen.x, screen.y, screen.x + screen.w, screen.y + screen.h,
            40f, 40f, Path.Direction.CW)
        c.clipPath(clip)
        rect(c, 160f, 12f, 280f, 626f, 0f, -1, shader = wallpaper)
        path.reset()
        path.moveTo(160f, 478f)
        path.cubicTo(235f, 393f, 309f, 427f, 440f, 306f)
        path.lineTo(440f, 638f)
        path.lineTo(160f, 638f)
        path.close()
        fill(-1, shader = highlight)
        c.drawPath(path, paint)
        text(c, "Nov 21, Sat", 300f, 85f, 15f, 0xebf3f5ff.toInt())
        text(c, "23", 242f, 161f, 80f, 0xebf3f5ff.toInt(), true, 94f)
        text(c, "52", 358f, 161f, 80f, 0xebf3f5ff.toInt(), true, 94f)
        circle(c, 300f, 121f, 4.2f, 0xebf3f5ff.toInt())
        circle(c, 300f, 146f, 4.2f, 0xebf3f5ff.toInt())
        if (guide != 3) {
            for (x in floatArrayOf(210f, 390f)) circle(c, x, 592f, 17f, 0x94d9f3f5.toInt())
            path.reset()
            path.moveTo(205f, 584f)
            path.lineTo(215f, 584f)
            path.lineTo(215f, 588f)
            path.lineTo(212f, 591f)
            path.lineTo(212f, 599f)
            path.lineTo(208f, 599f)
            path.lineTo(208f, 591f)
            path.lineTo(205f, 588f)
            path.close()
            path.moveTo(205f, 588f)
            path.lineTo(215f, 588f)
            path.moveTo(382f, 588f)
            path.lineTo(386f, 588f)
            path.lineTo(388f, 586f)
            path.lineTo(392f, 586f)
            path.lineTo(394f, 588f)
            path.lineTo(398f, 588f)
            path.lineTo(398f, 598f)
            path.lineTo(382f, 598f)
            path.close()
            path.addCircle(390f, 593f, 3f, Path.Direction.CW)
            line(c, path, 0xff66627f.toInt(), 1.7f)
        }
        when {
            guide == 3 -> {
                val save = c.save()
                val scale = 280f / 1140f
                c.translate(160f, 592f - 336f * .56f * scale)
                c.scale(scale, scale)
                hyperOs(c, frame)
                c.restoreToCount(save)
            }
            guide == 4 -> hyperOsNotifications(c, frame)
            guide == 2 && !preview -> carousel(c, frame)
            else -> notifications(c, frame, guide == 1)
        }
    }

    private fun touch(c: NativeCanvas, f: Float, start: Float, end: Float,
                      x0: Float, y0: Float, x1: Float, y1: Float) {
        if (f < start || f > end + 8f) return
        val t = p(f, start, end)
        val back = p(f - 6, start, end)
        val alpha = p(f, start, start + 4) * (1 - p(f, end, end + 8))
        val x = mix(x0, x1, t)
        val y = mix(y0, y1, t)
        path.reset()
        path.moveTo(mix(x0, x1, back), mix(y0, y1, back))
        path.lineTo(x, y)
        line(c, path, 0xffeeadcc.toInt(), 19f, .52f * alpha)
        circle(c, x, y, 9f, 0xfffff5fa.toInt(), alpha)
        path.reset()
        path.addCircle(x, y, 9f, Path.Direction.CW)
        line(c, path, 0xffcd80a8.toInt(), 4f, alpha)
    }

    private fun notifications(c: NativeCanvas, f: Float, stacked: Boolean) {
        val list = 1 - g(f, 30f, 62f) + g(f, 210f, 243f)
        val pile = g(f, 30f, 62f) - g(f, 85f, 113f) + g(f, 150f, 177f) - g(f, 210f, 243f)
        fun rebound(at: Float) = g(f, at - 6, at) - g(f, at, at + 12)
        val bounce = 6 * rebound(62f) + 7 * rebound(113f) - 5 * rebound(177f) - 8 * rebound(243f)
        for (i in 2 downTo 0) {
            val listY = 224.5f + 59 * i
            val stackY = 518.5f + 13 * i
            val cw = 118f - 9 * i
            val ch = 34f - 4 * i
            val visible = if (stacked) list + pile else
                1 - g(f, 34f - i, 56f - 3 * i) + g(f, 95f + 3 * i, 119f + i)
            val width = mix(cw, 234f, visible) - if (stacked) (8 + 15 * i) * pile else 0f
            val height = mix(ch, 49f, visible) - if (stacked) 2 * i * pile else 0f
            val cy = if (stacked) capsule.cy() + (stackY - capsule.cy()) * pile +
                (listY - capsule.cy()) * list + bounce else {
                val hiddenY = capsule.cy() - floatArrayOf(0f, 37f, 91f)[i]
                mix(mix(listY, hiddenY, g(f, 28f, 54f - i * 5)),
                    listY, g(f, 95f + 1.5f * i, 125f + i)) +
                    (if (i == 0) 8 * rebound(54f) else 0f) - 8 * rebound(125f + i)
            }
            val alpha = if (i == 0) 1f else if (stacked)
                CapsuleDemoMotion.smooth(visible / .45f) * mix(.73f - i * .17f, 1f, list)
                else 1 - g(f, 45f - 6 * i, 57f - 7 * i) + g(f, 95f + 2 * i, 104f + 3 * i)
            val color = if (i == 0) NativeColor.rgb(
                kotlin.math.round(mix(194f, 23f, visible)).toInt(),
                kotlin.math.round(mix(226f, 48f, visible)).toInt(),
                kotlin.math.round(mix(230f, 69f, visible)).toInt()) else 0xff173045.toInt()
            rect(c, 300 - width / 2, cy - height / 2, width, height, mix(ch / 2, 14f, visible),
                color, alpha)
        }
        if (stacked) {
            touch(c, f, 27f, 47f, 300f, 256f, 300f, 412f)
            touch(c, f, 82f, 104f, 300f, 454f, 300f, 552f)
            touch(c, f, 147f, 167f, 300f, 554f, 300f, 499f)
            touch(c, f, 207f, 237f, 300f, 491f, 300f, 306f)
        } else {
            touch(c, f, 25f, 53f, 300f, 266f, 300f, 524f)
            touch(c, f, 92f, 120f, 300f, 573f, 300f, 333f)
        }
    }

    private fun shortcut(c: NativeCanvas, b: CoverMorphMotion.Box, ride: Boolean, alpha: Float) {
        if (alpha <= 0) return
        val r = min(b.h * .38f, b.w * .32f)
        val x = b.x + r + b.h * .12f
        circle(c, x, b.cy(), r, if (ride) 0xff655bc5.toInt() else 0xff96607f.toInt(), alpha)
        val tx = x + 1.5f * r
        val width = (b.x + b.w - b.h * .18f - tx).coerceAtLeast(0f)
        rect(c, tx, b.cy() - r * .38f, width * .88f, r * .3f, r * .15f,
            0xff64758e.toInt(), .9f * alpha)
        rect(c, tx, b.cy() + r * .15f, width * .58f, r * .24f, r * .12f,
            0xff64758e.toInt(), .5f * alpha)
    }

    private fun carousel(c: NativeCanvas, f: Float) {
        val left = f < 175f
        val t = if (left) p(f, 82f, 116f) else p(f, 186f, 208f)
        val part = ColorOsPill.frame(capsule, t, left, 0f, 5f, 2f)
        val impact = if (left) 108f else 200f
        val squeeze = p(f, impact - 14, impact) - p(f, impact, impact + 15)
        val stretch = 1 - .075f * squeeze
        val save = c.save()
        c.scale(stretch, 1 - (stretch - 1) * .22f, 300f, 592f)
        path.reset()
        val settled = t == 0f || t == 1f
        if (settled) {
            path.addRoundRect(capsule.x, capsule.y, capsule.x + capsule.w, capsule.y + capsule.h,
                17f, 17f, Path.Direction.CW)
            ColorOsPill.neighbourPath(capsule, true, 1f, 5f, 2f, path)
            ColorOsPill.neighbourPath(capsule, false, 1f, 5f, 2f, path)
        } else {
            val leaving = part.oldBean < 0
            val arriving = part.newBean < 0
            if (leaving && arriving) ColorOsPill.gooPath(part.new, part.old, path, part.reach)
            else {
                if (leaving) ColorOsPill.gooPath(part.old, null, path)
                else ColorOsPill.earGlass(part.old, left, capsule, 2f, path)
                if (arriving) ColorOsPill.gooPath(part.new, null, path)
                else ColorOsPill.earGlass(part.new, !left, capsule, 2f, path)
            }
            ColorOsPill.neighbourPath(capsule, left, part.bracket, 5f, 2f, path)
            ColorOsPill.neighbourPath(capsule, !left, part.oldBracket, 5f, 2f, path)
        }
        fill(0xffece5f6.toInt())
        c.drawPath(path, paint)
        if (settled) shortcut(c, capsule, left == (t == 0f), 1f)
        else {
            if (part.oldBean < 0) shortcut(c, part.old, left, ColorOsPill.oldContent(t).second)
            if (part.newBean < 0) shortcut(c, part.new, !left,
                ColorOsPill.newContent(t, ColorOsPill.roundAt(capsule, 2f)).second)
        }
        c.restoreToCount(save)
        touch(c, f, 81f, 113f, 343f, 592f, 274f, 592f)
        touch(c, f, 175f, 207f, 283f, 592f, 341f, 592f)
    }

    /** Native stack scroll: whole cards retain their gaps while the capsule fades out. */
    private fun hyperOsNotifications(c: NativeCanvas, f: Float) {
        val travel = CapsuleDemoMotion.hyperOsListProgress(f)
        val visible = travel.coerceIn(0f, 1f)
        for (i in 2 downTo 0) {
            val y = 200f + 59 * i + 350f * (1 - travel)
            rect(c, 183f, y, 234f, 49f, 14f, 0xff173045.toInt(), visible)
        }
        val left = 1 - visible
        val lift = 22f * visible
        rect(c, 241f, 575f - lift, 118f, 34f, 17f, 0xffe9eef7.toInt(), left)
        circle(c, 257f, 592f - lift, 10f, 0xff70caa8.toInt(), left)
        rect(c, 273f, 585f - lift, 68f, 5f, 2.5f, 0xff64758e.toInt(), .9f * left)
        rect(c, 273f, 595f - lift, 42f, 4f, 2f, 0xff64758e.toInt(), .5f * left)
        touch(c, f, 33f, 76f, 290f, 589f, 290f, 356f)
        touch(c, f, 174f, 209f, 300f, 275f, 300f, 526f)
    }

    private fun hyperOs(c: NativeCanvas, f: Float) {
        val d = 336f * .42f
        val cy = 336f * .56f
        disc(c, 60 + d / 2, cy, d, false)
        disc(c, 1140 - 60 - d / 2, cy, d, true)
        fun motion(start: Float, duration: Float = .7f) = CapsuleDemoMotion.spring(f, start, duration)
        val appear = (motion(.8f) - motion(4f)).coerceIn(0f, 1f)
        val share = motion(.8f) - motion(4f)
        val big = CoverMorphMotion.Box(60 + d + 66, cy - d / 2, 1140 - 2 * (60 + d + 66) - d * 1.18f * share, d)
        val small = CoverMorphMotion.Box(1140 - (60 + d + 66) - d, cy - d / 2, d, d)
        val back = f >= 186 && f < 222
        val t = if (f >= 222) 0f else motion(if (back) 3.1f else 1.7f, .6f).coerceIn(0f, 1f)
        fun contents(b: CoverMorphMotion.Box, compact: Boolean, index: Int) {
            blurred(c, index, 12 * t, 1 - t) { target ->
                val save = target.save()
                target.scale(1 - .8f * t, 1 - .8f * t, b.cx(), b.cy())
                content(target, b, back, compact, f)
                target.restoreToCount(save)
            }
            blurred(c, index + 1, 12 * (1 - t), t) { target ->
                content(target, b, !back, compact, f)
            }
        }
        rect(c, big.x, big.y, big.w, big.h, min(big.w, big.h) / 2, 0xffe9eef7.toInt())
        contents(big, false, 0)
        if (appear > 0) {
            val save = c.save()
            c.scale(.6f + .4f * appear, .6f + .4f * appear, small.cx(), small.cy())
            val layer = c.saveLayerAlpha(0f, 0f, 1140f, 336f,
                (255 * appear * (.25f + .75f * appear)).toInt())
            rect(c, small.x, small.y, small.w, small.h, d / 2, 0xffe9eef7.toInt())
            // The same content swap, including the arrival blur of the small capsule.
            val arrivalBlur = 24 * (1 - appear)
            blurred(c, 2, 12 * t + arrivalBlur, 1 - t) { target ->
                val old = target.save()
                target.scale(1 - .8f * t, 1 - .8f * t, small.cx(), small.cy())
                content(target, small, back, true, f)
                target.restoreToCount(old)
            }
            blurred(c, 3, 12 * (1 - t) + arrivalBlur, t) { target ->
                content(target, small, !back, true, f)
            }
            c.restoreToCount(layer)
            c.restoreToCount(save)
        }
        for (start in floatArrayOf(1.7f, 3.1f)) {
            val elapsed = f / 60 - start
            if (elapsed <= 0 || elapsed >= .6f) continue
            val move = CapsuleDemoMotion.spring(f, start + .1f, .38f, true).coerceIn(0f, 1f)
            val alpha = CapsuleDemoMotion.spring(f, start, .1f, true).coerceIn(0f, 1f) *
                (1 - CapsuleDemoMotion.spring(f, start + .48f, .12f, true).coerceIn(0f, 1f))
            val y = cy + 12 - 5 * move
            circle(c, small.cx(), y, 22 + 2 * (1 - alpha), 0x42ffffff, alpha)
            path.reset()
            path.addCircle(small.cx(), y, 22 + 2 * (1 - alpha), Path.Direction.CW)
            line(c, path, 0x59ffffff, 1.5f, alpha)
        }
    }

    private fun blurred(c: NativeCanvas, index: Int, radius: Float, alpha: Float, draw: (NativeCanvas) -> Unit) {
        if (alpha <= 0) return
        val layer = c.saveLayerAlpha(0f, 0f, 1140f, 336f, (255 * alpha.coerceIn(0f, 1f)).toInt())
        if (radius > .01f && c.isHardwareAccelerated) {
            val node = nodes[index]
            node.setPosition(0, 0, 1140, 336)
            val recording = node.beginRecording()
            draw(recording)
            node.endRecording()
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
            c.drawRenderNode(node)
        } else draw(c)
        c.restoreToCount(layer)
    }

    private fun content(c: NativeCanvas, b: CoverMorphMotion.Box, notification: Boolean,
                        compact: Boolean, f: Float) {
        val d = 336f * .42f
        if (compact) {
            if (notification) rect(c, b.cx() - d * .28f, b.cy() - d * .28f,
                d * .56f, d * .56f, 22f, -1, shader = LinearGradient(
                    b.cx() - d * .28f, b.cy() - d * .28f,
                    b.cx() + d * .28f, b.cy() + d * .28f,
                    0xffffb86b.toInt(), 0xffe2557a.toInt(), Shader.TileMode.CLAMP))
            else circle(c, b.cx(), b.cy(), d * .28f, 0xff7fb2ff.toInt())
            return
        }
        val pad = b.h * .18f
        val icon = b.h - pad * 2
        val tx = b.x + pad * 1.8f + icon
        val bar = b.h * .11f
        val width = (b.x + b.w - pad - icon - tx).coerceAtLeast(0f)
        if (!notification) {
            fill(-1, shader = LinearGradient(b.x + pad, b.y + pad,
                b.x + pad + icon, b.y + pad + icon, 0xffffb86b.toInt(), 0xffe2557a.toInt(),
                Shader.TileMode.CLAMP))
            c.drawRoundRect(b.x + pad, b.y + pad, b.x + pad + icon, b.y + pad + icon, icon * .22f, icon * .22f, paint)
        } else circle(c, b.x + pad + icon / 2, b.cy(), icon / 2, 0xff6fd39a.toInt())
        val save = c.save()
        c.clipRect(tx, b.y, tx + width, b.y + b.h)
        val marquee = if (notification && f < 222) ((f / 60 - 1.7f).coerceAtLeast(0f)) * 120 else 0f
        rect(c, tx - marquee, b.cy() - bar * 1.6f, width * (if (notification) 1.9f else .75f), bar, bar / 2, 0xff64758e.toInt(), .9f)
        rect(c, tx, b.cy() + bar * .6f, width * .5f, bar, bar / 2, 0xff64758e.toInt(), .55f)
        if (notification) for (offset in floatArrayOf(.65f, 1.25f))
            rect(c, tx + width * offset - marquee, b.cy() - bar * 1.6f, 12f, bar, 0f, 0xff899aaa.toInt())
        c.restoreToCount(save)
        if (notification) text(c, "3+", b.x + b.w - pad - icon / 2, b.cy() + 13, 39f, 0xff64758e.toInt())
        else {
            rect(c, b.x + b.w - pad - icon / 2 - 11, b.cy() - 14, 7f, 28f, 3f, 0xff64758e.toInt(), .9f)
            rect(c, b.x + b.w - pad - icon / 2 + 4, b.cy() - 14, 7f, 28f, 3f, 0xff64758e.toInt(), .9f)
        }
    }

    private fun disc(c: NativeCanvas, x: Float, y: Float, d: Float, camera: Boolean) {
        rect(c, x - d / 2, y - d / 2, d, d, d / 2, -1, shader = glass)
        val save = c.save()
        c.translate(x - d / 4, y - d / 4)
        c.scale(d / 48, d / 48)
        path.reset()
        if (camera) {
            path.moveTo(5f, 6.5f)
            path.lineTo(7.3f, 6.5f)
            path.lineTo(9f, 4f)
            path.lineTo(15f, 4f)
            path.lineTo(16.7f, 6.5f)
            path.lineTo(19f, 6.5f)
            path.quadTo(21f, 6.5f, 21f, 8.5f)
            path.lineTo(21f, 18f)
            path.quadTo(21f, 20f, 19f, 20f)
            path.lineTo(5f, 20f)
            path.quadTo(3f, 20f, 3f, 18f)
            path.lineTo(3f, 8.5f)
            path.quadTo(3f, 6.5f, 5f, 6.5f)
            path.close()
            path.addCircle(12f, 12.5f, 4f, Path.Direction.CW)
            path.moveTo(17.7f, 9f)
            path.lineTo(18f, 9f)
        } else {
            path.moveTo(7f, 3f)
            path.lineTo(17f, 3f)
            path.lineTo(17f, 7f)
            path.lineTo(14.5f, 10.5f)
            path.lineTo(14.5f, 20f)
            path.quadTo(14.5f, 21f, 13.5f, 21f)
            path.lineTo(10.5f, 21f)
            path.quadTo(9.5f, 21f, 9.5f, 20f)
            path.lineTo(9.5f, 10.5f)
            path.lineTo(7f, 7f)
            path.close()
            path.moveTo(7.5f, 6.5f)
            path.lineTo(16.5f, 6.5f)
            path.moveTo(12f, 13f)
            path.lineTo(12f, 16f)
        }
        line(c, path, -1, 1.7f, .9f)
        c.restoreToCount(save)
    }
}
