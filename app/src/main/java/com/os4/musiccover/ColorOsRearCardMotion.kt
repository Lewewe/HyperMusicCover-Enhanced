// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Behaviour modelled on ColorOS 16; no ColorOS implementation is included.
// Developed with the assistance of OpenAI Codex and Anthropic Claude.
package com.os4.musiccover

/** A rear card's starting pose is independent of the destination already requested by a fling. */
internal class ColorOsRearCardMotion(startedOpen: Boolean, private val growIntoNative: Boolean = false) {
    val place = Jelly(0.5f, 0.84f)
    val width = Jelly(0.41f, 0.999f)
    val height = Jelly(0.45f, 0.999f)
    val corner = Jelly(0.35f, 0.999f)
    val blur = Jelly(0.3f, 0.89f)
    val alpha = Jelly(0.3f, 0.89f)
    val all = arrayOf(place, width, height, corner, blur, alpha)
    val revealedSize get() = ColorOsSpreadGeometry.revealGrowth(width.value, place.value)
    fun footerGrowth(progress: Float) = width.value.coerceIn(0f, progress)

    fun step(progress: Float, opening: Boolean, dt: Float) {
        for (spring in all) spring.target = progress
        blur.target = 1f
        alpha.target = if (opening) ColorOsNotificationMotion.rearAlpha(progress) else 1f
        corner.target = 1f
        for (spring in all) spring.step(dt)
    }

    /** Footer growth reaches the native shape during touch; other rows retain their growth clock. */
    fun nativeMix(columnMix: Float, footerProgress: Float? = null): Float = footerProgress ?: if (growIntoNative)
        minOf(columnMix, ColorOsSpreadGeometry.handoffMix(revealedSize)) else columnMix

    init {
        val initial = if (startedOpen) 1f else 0f
        for (spring in all) {
            spring.value = initial
            spring.target = initial
        }
    }
}

/** Once ownership starts returning to the native list, a late row must not rewind the column. */
internal class ColorOsSpreadHandoff {
    var started = false
        private set
    private var openedGesture: Long? = null
    var scrolling = false
        private set

    fun listOpened(gesture: Long) { openedGesture = gesture }

    /** The opening flick is not a second gesture scrolling an already open list. */
    fun scroll(gesture: Long, upward: Boolean): Boolean {
        if (upward && openedGesture?.let { gesture > it } == true) scrolling = true
        return scrolling
    }

    fun canReturnToNative(cardsRest: Boolean, mix: Float, piled: Boolean, busy: Boolean): Boolean =
        cardsRest && !piled && mix >= 0.995f && (!busy || scrolling)

    fun target(piled: Boolean, grown: Boolean): Float {
        if (!piled && grown) started = true
        return if (!piled && started) 1f else 0f
    }
}

/** Native pile clipping is animated too; restoring it in one frame makes bottom cards snap. */
internal class ColorOsSpreadClip(var nativeTop: Int, var nativeBottom: Int) {
    var nativeExtBottom = 0
    private val top = Jelly(0.16f, 0.999f)
    private val bottom = Jelly(0.16f, 0.999f)
    private val visibility = Jelly(0.16f, 0.999f).apply { value = 1f; target = 1f }
    var nativeVisible = true
    val visibilityAmount get() = visibility.value.coerceIn(0f, 1f)
    var appliedTop = nativeTop
    var appliedBottom = nativeBottom
    val topAmount get() = kotlin.math.round(top.value).toInt().coerceAtLeast(0)
    val bottomAmount get() = kotlin.math.round(bottom.value).toInt().coerceAtLeast(0)

    fun step(pileCut: Int, handoff: Float, dt: Float) {
        val p = handoff.coerceIn(0f, 1f)
        top.target = pileCut * (1f - p) + nativeTop * p
        bottom.target = maxOf(nativeBottom, nativeExtBottom) * p
        visibility.target = if (p > 0.001f && !nativeVisible) 0f else 1f
        top.step(dt)
        bottom.step(dt)
        visibility.step(dt)
    }

    fun settled() = arrayOf(top, bottom).all {
        kotlin.math.abs(it.value - it.target) < 0.5f && kotlin.math.abs(it.velocity) < 5f
    } && visibility.nearRest()
}
