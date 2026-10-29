// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Developed with the assistance of OpenAI Codex.
package com.os4.musiccover

import android.graphics.Outline
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import kotlin.math.roundToInt

/** Own the material element's window too: vendor blur does not follow every ancestor clip. */
internal class NotificationBackdropMorph {
    private class State(val view: View) {
        val alpha = view.alpha
        val overlap = view.getHasOverlappingRendering()
        val clip = view.clipToOutline
        val outline = view.outlineProvider
        val bounds = view.clipBounds?.let(::Rect)
        val drawableAlpha = runCatching { (Xp.getObjectField(view, "mDrawableAlpha") as Number).toInt() }.getOrDefault(255)
        val alphaSetter = runCatching { view.javaClass.getMethod("setDrawableAlpha", Int::class.javaPrimitiveType) }.getOrNull()
        var appliedAlpha = -1
        val window = Rect()
        var radius = -1f
        val provider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, out: Outline) { out.setRoundRect(window, radius.coerceAtLeast(0f)) }
        }
    }
    private val states = HashMap<View, State>()

    fun apply(row: View, left: Float, top: Float, width: Float, height: Float, radius: Float, alpha: Float) {
        val state = states[row] ?: run {
            val bg = runCatching { Xp.getObjectField(row, "mBackgroundNormal") as? View }.getOrNull() ?: return
            State(bg).also {
                states[row] = it
                bg.forceHasOverlappingRendering(false)
            }
        }
        val bg = state.view
        val l = (left - bg.left).roundToInt()
        val t = (top - bg.top).roundToInt()
        val r = l + width.roundToInt().coerceAtLeast(1)
        val b = t + height.roundToInt().coerceAtLeast(1)
        val corner = radius.coerceAtLeast(0f)
        val changed = state.window.left != l || state.window.top != t ||
            state.window.right != r || state.window.bottom != b || state.radius != corner
        state.window.set(l, t, r, b)
        state.radius = corner
        if (bg.outlineProvider !== state.provider) bg.outlineProvider = state.provider
        if (!bg.clipToOutline) bg.clipToOutline = true
        if (changed) { bg.clipBounds = state.window; bg.invalidateOutline() }
        val a = alpha.coerceIn(0f, 1f)
        // NUMBER can have the background at alpha zero already. That is a native state to
        // restore, not the opacity of the fully opened material we are morphing toward.
        bg.alpha = a
        NativeNotificationMaterial.fadeBlend(bg, a)
        val drawableAlpha = (255 * a).roundToInt()
        if (state.appliedAlpha != drawableAlpha) {
            runCatching { state.alphaSetter?.invoke(bg, drawableAlpha) }
            state.appliedAlpha = drawableAlpha
        }
    }

    fun clear() {
        for (state in states.values) {
            val bg = state.view
            bg.forceHasOverlappingRendering(state.overlap)
            bg.alpha = state.alpha
            NativeNotificationMaterial.fadeBlend(bg, 1f)
            runCatching { state.alphaSetter?.invoke(bg, state.drawableAlpha) }
            if (bg.outlineProvider === state.provider) {
                bg.outlineProvider = state.outline
                bg.clipToOutline = state.clip
            }
            bg.clipBounds = state.bounds
        }
        states.clear()
    }
}
