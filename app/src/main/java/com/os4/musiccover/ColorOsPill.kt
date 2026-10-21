// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// ColorOS-style lock screen capsule, written for this project. Its geometry, springs and timings
// are modelled on the behaviour and parameter values of ColorOS 16 (OPPO); no ColorOS code is
// included. If you reuse this file, keep this notice and the AGPL-3.0 terms (publish your source).
package com.os4.musiccover

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.min

/**
 * The ColorOS 16/17 lock screen capsule: one island at a time, swiped between.
 *
 * Read off recordings of ColorOS 16 (2026-10-18, at full speed, slowed down, and with a pointer
 * showing the finger):
 * - Either side of the capsule stands a bracket, "( )": a thin crescent of the capsule's own
 *   glass. It is the neighbour on that side, folded away.
 * - Pulled, the capsule narrows from the side it is pulled toward and its content goes out of
 *   focus; the bracket on that side thickens and straightens out of its curve into a bean, then
 *   a circle, its outer side staying where the bracket was, a gap clear of the capsule.
 * - On, the two run together through a waist: two blobs about the same size, and the glass
 *   flows across - the arriving one swells, the leaving one shrinks, the waist moves with them.
 *   The arriving island's content shows in its blob from early on, out of focus.
 * - The leaving one, down to a circle against the far bracket, pulls the waist thin until it
 *   breaks, then curls back into that bracket; the capsule widens over the place and comes into
 *   focus, and a new bracket grows on the side the arriving one came from. No bounce.
 * - The finger drives all of it: a drag across the capsule's width is one switch, back runs it
 *   back, and on goes through the next island.
 *
 * Everything here is geometry in screen pixels; MiniPlayerController draws it with the pill, a
 * stand-in pill for the island leaving, and one [GooGlass] behind both for the glass.
 */
internal object ColorOsPill {
    /** How far out of the capsule's round end the brackets stand, in dp. */
    const val BRACKET_OUT_DP = 9f

    /** A bracket's stroke, in dp. */
    const val BRACKET_STROKE_DP = 2.4f


    /** Out of focus at most, in dp, for both contents. */
    const val BLUR_DP = 12f


    /**
     * One frame of a switch toward [next] (the neighbour on the right) at [u], 0..1 of the way.
     *
     * Read off ColorOS 16 at 120 fps: one edge does everything. The arriving island's leading
     * edge runs from its bracket across the row to the capsule's far end, one to one with the
     * finger. Its other side stays at the bracket. The leaving island keeps the room left of
     * that edge, a small gap short of it, with a waist across the gap - narrowing until it is a
     * circle against its own bracket. From there the circle shrinks faster than the edge
     * closes in, so the gap opens, the waist thins and breaks, and the circle curls back into
     * its bracket. Nothing moves sideways on its own, so a held finger holds the frame.
     */
    class Frame(
        /** The leaving island's frame (its content) and as glass: a capsule, or [oldBean]. */
        val old: CoverMorphMotion.Box,
        /** Its ear's process (width over earEnd) while drawn as its ear; -1 while a capsule. */
        val oldBean: Float,
        val new: CoverMorphMotion.Box,
        val newBean: Float,
        /** How far apart the two can be and still hold a waist; 0 none. */
        val reach: Float,
        /** The scale of the ear the arriving island came out of: 0 up to 1. */
        val bracket: Float,
        /** The scale of the ear the leaving island goes to: 1 down to 0. */
        val oldBracket: Float = 1f,
    )

    /** Its ear's width (ColorOS earWidth): 2.5 strokes, so the bracket's band is one stroke across. */
    fun earWidth(stroke: Float): Float = 2.5f * stroke

    /** ColorOS's capsule starts this tall when it comes out of its ear (getCapsuleStartHeight). */
    private const val START_HEIGHT = 0.8f

    /** Narrower than this, an island is drawn as its ear (getEarEndHeightForCapsule). */
    fun earEnd(rest: CoverMorphMotion.Box): Float {
        val start = rest.h * START_HEIGHT
        return rest.w * start / (rest.w - rest.h + start)
    }

    /** The [u] at which the arriving island is as wide as its ear's end: from there it has the capsule. */
    fun roundAt(rest: CoverMorphMotion.Box, stroke: Float): Float =
        ((earEnd(rest) - earWidth(stroke)) / rest.w).coerceIn(0.02f, 0.9f)

    /**
     * One frame of a switch toward [next] (the neighbour on the right) at [u], 0..1 of the way:
     * ColorOS 16's ContainerAlgorithm with its metaball scroll calculator (read off a OnePlus,
     * 2026-10-20), [u] of the capsule's width scrolled.
     * - The leaving card keeps its place and is cut from the arriving side down to its ear's
     *   width, while it moves out by up to [out] (the ear's distance) and loses height down to
     *   START_HEIGHT; narrow enough, its ear clip makes it the bracket where the bracket stands.
     * - The arriving card is the scrolled width plus its ear's, held at its outer side, which
     *   comes in from the ear's place as it widens, and grows from START_HEIGHT to the height.
     * - The ears (the brackets as their own views) scale: the one the leaving card goes to down
     *   from 1, the one the arriving card came from up from 0, over the width less the height.
     */
    fun frame(rest: CoverMorphMotion.Box, u: Float, next: Boolean, gap: Float, out: Float,
              stroke: Float): Frame {
        val w = rest.w
        val h = rest.h
        val ear = earWidth(stroke)
        val start = h * START_HEIGHT
        val d = u.coerceIn(0f, 1f) * w
        val cutMax = (w - ear - 1f).coerceAtLeast(1f)
        val cut = min(d, cutMax)
        val oldW = w - cut
        val oldH = h - (h - start) * cut / cutMax
        val oldX = rest.x - out * min(1f, d / (w - out).coerceAtLeast(1f))
        val newW = min(d + ear, w)
        val newH = h - (h - start) * (w - newW) / w
        val newR = rest.x + w + out * (1f - d / w)
        fun box(x: Float, bw: Float, bh: Float) = CoverMorphMotion.Box(x, rest.cy() - bh / 2f, bw, bh)
        val o = box(oldX, oldW, oldH)
        val n = box(newR - newW, newW, newH)
        val end = earEnd(rest)
        val oBean = if (oldW < end) oldW / end else -1f
        val nBean = if (newW < end) newW / end else -1f
        val earScale = min(d, w - h) / (w - h).coerceAtLeast(1f)
        fun m(b: CoverMorphMotion.Box) = if (next) b else CoverMorphMotion.Box(2f * rest.cx() - b.x - b.w, b.y, b.w, b.h)
        return Frame(m(o), oBean, m(n), nBean, if (oBean < 0f && nBean < 0f) 1f else 0f, earScale, 1f - earScale)
    }

    /** The leaving island's content at [u]: in focus 0..1 (1 sharp), and seen 0..1. */
    fun oldContent(u: Float): Pair<Float, Float> =
        (1f - smooth((u / 0.3f).coerceIn(0f, 1f))) to (1f - smooth(((u - 0.35f) / 0.25f).coerceIn(0f, 1f)))

    /** The arriving island's content at [u]: shown out of focus once round, sharp at the end. */
    fun newContent(u: Float, roundAt: Float): Pair<Float, Float> =
        smooth(((u - 0.7f) / 0.3f).coerceIn(0f, 1f)) to smooth(((u - roundAt) / 0.25f).coerceIn(0f, 1f))

    internal fun monotone(x: FloatArray, y: FloatArray, t: Float): Float {
        val n = x.size
        if (t <= x[0]) return y[0]
        if (t >= x[n - 1]) return y[n - 1]
        val delta = FloatArray(n - 1) { (y[it + 1] - y[it]) / (x[it + 1] - x[it]) }
        val m = FloatArray(n)
        m[0] = delta[0]
        m[n - 1] = delta[n - 2]
        for (i in 1 until n - 1) {
            m[i] = if (delta[i - 1] * delta[i] <= 0f) 0f else (delta[i - 1] + delta[i]) / 2f
        }
        for (i in 0 until n - 1) {
            if (delta[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / delta[i]
            val b = m[i + 1] / delta[i]
            val h = a * a + b * b
            if (h > 9f) {
                val k = 3f / kotlin.math.sqrt(h)
                m[i] = k * a * delta[i]
                m[i + 1] = k * b * delta[i]
            }
        }
        var i = 0
        while (i < n - 2 && t > x[i + 1]) i++
        val h = x[i + 1] - x[i]
        val s = (t - x[i]) / h
        val s2 = s * s
        val s3 = s2 * s
        return (2 * s3 - 3 * s2 + 1) * y[i] + (s3 - 2 * s2 + s) * h * m[i] +
            (-2 * s3 + 3 * s2) * y[i + 1] + (s3 - s2) * h * m[i + 1]
    }

    /**
     * The ear on the [right] side at rest, added to [into], scaled by [grow] about its middle as
     * ColorOS scales its ear views while a switch goes on (updateEarState). It is the ear-clipped
     * card at its ear's width, so a card arriving out of it or leaving into it starts and ends
     * as exactly this shape.
     */
    fun neighbourPath(rest: CoverMorphMotion.Box, right: Boolean, grow: Float, out: Float,
                      stroke: Float, into: Path) {
        val g = grow.coerceIn(0f, 1f)
        if (g <= 0.01f) return
        val box = earBox(rest, right, out, stroke)
        val piece = Path()
        earGlass(box, outerLeft = !right, rest, stroke, piece)
        if (g < 1f) {
            val m = android.graphics.Matrix()
            m.setScale(g, g, box.cx(), box.cy())
            piece.transform(m)
        }
        into.op(piece, Path.Op.UNION)
    }

    /** The card as its ear stands at rest: its ear's width, START_HEIGHT tall, its outer side [out] out. */
    fun earBox(rest: CoverMorphMotion.Box, right: Boolean, out: Float, stroke: Float): CoverMorphMotion.Box {
        val ear = earWidth(stroke)
        val h = rest.h * START_HEIGHT
        val x = if (right) rest.x + rest.w + out - ear else rest.x - out
        return CoverMorphMotion.Box(x, rest.cy() - h / 2f, ear, h)
    }

    /**
     * A card narrower than earEnd as ColorOS draws it, added to [into]: its rounded glass cut by
     * the EarShape clip - a band along an arc as round as the capsule's end, its outer side at the
     * card's [outerLeft]/right side, its half width and half angle from earArc at the card's width
     * over earEnd, its ends rounded.
     */
    fun earGlass(box: CoverMorphMotion.Box, outerLeft: Boolean, rest: CoverMorphMotion.Box, stroke: Float,
                 into: Path) {
        if (box.w < 0.5f || box.h < 0.5f) return
        val end = earEnd(rest)
        val glass = Path().also { addCapsule(it, box) }
        if (box.w >= end) {
            into.op(glass, Path.Op.UNION)
            return
        }
        val radius = rest.h / 2f
        val (rb, ang) = earArc(box.w / end, end, stroke)
        val outer = if (outerLeft) box.x else box.x + box.w
        val dir = if (outerLeft) -1f else 1f
        val cx = outer - dir * (radius + rb)
        val cy = box.cy()
        val band = Path()
        val deg = Math.toDegrees(ang.toDouble()).toFloat()
        if (deg < 0.5f) {
            band.addCircle(cx + dir * radius, cy, rb, Path.Direction.CW)
        } else {
            val arc = Path()
            arc.addArc(RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                (if (outerLeft) 180f else 0f) - deg, 2f * deg)
            STROKE.strokeWidth = 2f * rb
            STROKE.getFillPath(arc, band)
        }
        glass.op(band, Path.Op.INTERSECT)
        into.op(glass, Path.Op.UNION)
    }

    /**
     * ColorOS's ear keys at [s] (0 the bracket, 1 a disc [h] tall): the band's half width and the
     * arc's half angle. Its ear width is 2.5 strokes, so the bracket's band is one stroke across.
     */
    internal fun earArc(s: Float, h: Float, stroke: Float): Pair<Float, Float> {
        val ear = earWidth(stroke)
        val turn = (2f * ear / h).coerceIn(0.05f, 0.9f)
        val p = s.coerceIn(0f, 1f)
        return if (p < turn) {
            val t = gainEar(smoothstepF(0f, turn, p), 0.4f)
            (ear / 5f + (ear / 2.15f - ear / 5f) * t) to EAR_HALF_ANGLE
        } else {
            val t = gainEar(smoothstepF(turn, 1f, p), 0.4f)
            (ear / 2.15f + (h / 2f - ear / 2.15f) * t) to EAR_HALF_ANGLE * (1f - t)
        }
    }

    /** The bracket arc's half angle, radians (ColorOS: 0.47). */
    private const val EAR_HALF_ANGLE = 0.47f

    private fun gainEar(x: Float, k: Float): Float {
        val a = 0.5f * Math.pow((2f * if (x < 0.5f) x else 1f - x).toDouble(), k.toDouble()).toFloat()
        return if (x < 0.5f) a else 1f - a
    }

    // Made when first drawn: the geometry above is also run on the JVM, where Paint is a stub.
    private val STROKE by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    /**
     * The glass of two capsules as one liquid shape, added to [out]. [b] may be null: one shape;
     * [reach] 0: two shapes, as while the finger pulls.
     *
     * ColorOS 16's own metaball (SystemUI MetaBallBlendManager + the posteffect shader's
     * blend_pair, read off a OnePlus, 2026-10-20): near the joint each body is sampled as if
     * pulled toward the other - by up to its blend range, most on the middle line and fading
     * out above and below - and the two are joined with a smooth minimum. The blend range grows
     * with how much longer than round the narrower of the two is, so a circle does not stick
     * and a circle folding away lets go. Here the outline of that field is traced across the
     * joint; elsewhere the two are their plain capsules.
     */
    fun gooPath(a: CoverMorphMotion.Box, b: CoverMorphMotion.Box?, out: Path, reach: Float = 0f) {
        out.op(Path().also { addCapsule(it, a) }, Path.Op.UNION)
        if (b == null || b.w < 1f || b.h < 1f) return
        out.op(Path().also { addCapsule(it, b) }, Path.Op.UNION)
        if (reach <= 0f) return
        val left = if (a.cx() <= b.cx()) a else b
        val right = if (left === a) b else a
        val f = Field(left, right)
        if (f.shapeChange <= 0f && f.k <= 0f) return
        // Across the band the pull reaches, a step every ~1.5% of the height.
        val x0 = f.xc - f.band
        val x1 = f.xc + f.band
        val n = 48
        val xs = FloatArray(n + 1) { x0 + (x1 - x0) * it / n }
        val hs = FloatArray(n + 1) { f.halfHeight(xs[it]) }
        var start = -1
        for (i in 0..n) {
            val inside = hs[i] > 0.5f
            if (inside && start < 0) start = i
            if ((!inside || i == n) && start >= 0) {
                val end = if (inside) i else i - 1
                if (end > start) {
                    val run = Path()
                    run.moveTo(xs[start], f.cy - hs[start])
                    for (q in start + 1..end) run.lineTo(xs[q], f.cy - hs[q])
                    for (q in end downTo start) run.lineTo(xs[q], f.cy + hs[q])
                    run.close()
                    out.op(run, Path.Op.UNION)
                }
                start = -1
            }
        }
    }

    /**
     * How joined the two are, 0..1: the share of its blend range the narrower one has (Field),
     * 0 while either is round or narrower. The capsules' own Soft Glass gives way to the one
     * liquid glass by this much, since Soft Glass is drawn only on a view's rounded frame.
     */
    fun joinStrength(a: CoverMorphMotion.Box, b: CoverMorphMotion.Box?, reach: Float): Float {
        if (b == null || reach <= 0f) return 0f
        val n = if (a.w < b.w) a else b
        return smooth((n.w - n.h) / (0.4f * n.h))
    }

    /** ColorOS's blend range at most (50 px on a ~110 px capsule) and its smooth minimum (15 px), of the height. */
    private const val BLEND_SHAPE = 0.45f
    private const val BLEND_K = 0.14f

    /** The two capsules' merged distance field across their joint, ColorOS's blend_pair. */
    internal class Field(private val l: CoverMorphMotion.Box, private val r: CoverMorphMotion.Box) {
        val cy = l.cy()
        private val h = min(l.h, r.h)
        private val gap = r.x - (l.x + l.w)
        val xc = (l.x + l.w + r.x) / 2f
        // The blend range follows the narrower one: 0 a circle, full once it is 0.4 heights longer.
        private val narrow = if (l.w < r.w) l else r
        private val blend = h * BLEND_SHAPE * smooth((narrow.w - narrow.h) / (0.4f * narrow.h))
        private val space = gap.coerceAtLeast(0f)
        val shapeChange = if (blend <= 0.5f) 0f else
            smoothstepF(2f * blend + 0.055f * h, 2f * blend, space) *
                smoothstepF(min(l.w, r.w) / 2f, -2f * blend, -gap)
        private val diff = -gap + shapeChange * 2f * blend
        val k = h * BLEND_K * gain(smoothstepF(0f, 1f, diff), 0.3f)
        val band = 2f * blend * 2f + space * 0.5f + k

        private fun pull(x: Float, y: Float): Float {
            if (shapeChange <= 0f) return 0f
            val rr = h / 2f
            val rd = smoothstepF(blend * 2f + space * 0.5f, 0f, kotlin.math.abs(x - xc)) *
                smoothstepF(rr * 1.2f, 0f, kotlin.math.abs(y))
            return blend * rd * shapeChange
        }

        fun sdf(x: Float, y: Float): Float {
            val s = pull(x, y)
            val d1 = rbox(x - s - l.cx(), y, l.w / 2f, l.h / 2f, min(l.w, l.h) / 2f)
            val d2 = rbox(x + s - r.cx(), y, r.w / 2f, r.h / 2f, min(r.w, r.h) / 2f)
            if (k <= 0.001f) return min(d1, d2)
            val hh = (0.5f + 0.5f * (d1 - d2) / k).coerceIn(0f, 1f)
            return d1 + (d2 - d1) * hh - k * hh * (1f - hh)
        }

        /** The field's outline above the middle line at [x] (0 outside), by bisection. */
        fun halfHeight(x: Float): Float {
            if (sdf(x, 0f) > 0f) return 0f
            var lo = 0f
            var hi = h
            repeat(18) {
                val m = (lo + hi) / 2f
                if (sdf(x, m) <= 0f) lo = m else hi = m
            }
            return lo
        }
    }

    private fun rbox(px: Float, py: Float, bx: Float, by: Float, rad: Float): Float {
        val qx = kotlin.math.abs(px) - bx + rad
        val qy = kotlin.math.abs(py) - by + rad
        return min(kotlin.math.max(qx, qy), 0f) +
            kotlin.math.hypot(kotlin.math.max(qx, 0f), kotlin.math.max(qy, 0f)) - rad
    }

    private fun smoothstepF(e0: Float, e1: Float, x: Float): Float {
        if (e0 == e1) return if (x < e0) 0f else 1f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun gain(x: Float, k: Float): Float {
        val a = 0.5f * Math.pow((2f * if (x < 0.5f) x else 1f - x).toDouble(), k.toDouble()).toFloat()
        return if (x < 0.5f) a else 1f - a
    }

    private fun addCapsule(path: Path, b: CoverMorphMotion.Box) {
        val r = min(b.w, b.h) / 2f
        path.addRoundRect(RectF(b.x, b.y, b.x + b.w, b.y + b.h), r, r, Path.Direction.CW)
    }

    fun lerp(a: CoverMorphMotion.Box, b: CoverMorphMotion.Box, t: Float) = CoverMorphMotion.Box(
        a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.w + (b.w - a.w) * t, a.h + (b.h - a.h) * t)

    fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}

/**
 * The glass of ColorOS's row: the brackets at rest, and through a pull or a switch both islands,
 * the liquid between them and the brackets, as one shape. Worn on the card's material as the
 * pill's own glass is (MiniPlayerRuntime.material: this frame the blur container, [element] the
 * card's recorded calls). Its outline is any path - non-convex outlines clip from API 33 - so the
 * rim follows the waist and the crescents too.
 */
internal class GooGlass(context: Context) : FrameLayout(context) {
    private val element = MaterialElement(context)
    private val local = Path()
    private var dressWith: ((ImageView) -> Unit)? = null
    private var dressedAs = -1
    private var dressedSize = false
    private val shape = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (local.isEmpty) outline.setEmpty() else outline.setPath(local)
        }
    }

    init {
        // A child of keyguard_root_view's ConstraintLayout: every child there needs an id
        // (ShortcutDisc).
        id = View.generateViewId()
        outlineProvider = shape
        clipToOutline = true
        outlineAmbientShadowColor = Color.TRANSPARENT
        outlineSpotShadowColor = Color.TRANSPARENT
        element.scaleType = ImageView.ScaleType.FIT_XY
        element.shape = shape
        addView(element, LayoutParams(-1, -1))
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun dress(generation: Int, apply: (ImageView) -> Unit) {
        dressWith = apply
        if (generation == dressedAs && dressedSize) return
        dressedAs = generation
        if (width > 1 && height > 1) {
            dressedSize = true
            apply(element)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // A material applied before there was a size draws nothing (ShortcutDisc).
        if (w > 1 && h > 1 && !dressedSize) {
            dressedSize = true
            dressWith?.invoke(element)
        }
    }

    /** The shape this frame, [path] in screen pixels; [originX]/[originY] are this view's on screen. */
    fun setPath(path: Path, originX: Float, originY: Float) {
        local.set(path)
        local.offset(-originX, -originY)
        invalidateOutline()
        element.invalidateOutline()
    }

    fun clearShape() {
        local.reset()
        invalidateOutline()
        element.invalidateOutline()
    }
}
