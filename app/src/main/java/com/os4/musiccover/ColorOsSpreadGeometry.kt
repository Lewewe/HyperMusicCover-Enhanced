// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Behaviour modelled on ColorOS 16; no ColorOS implementation is included.
// Developed with the assistance of OpenAI Codex and Anthropic Claude.
package com.os4.musiccover

/** Geometry of the rear cards, whose background window and content have separate transforms. */
internal object ColorOsSpreadGeometry {
    // Clip width uses a COUI spring interpolator on a 550ms ValueAnimator. Its time runs
    // from 0 to 1 over that duration, so these responses must be converted to wall seconds.
    private const val ANIMATION_SECONDS = 0.55f
    // Slightly more breathing room on HyperOS, whose glass and native layout also move.
    private const val MOTION_TIME_SCALE = 1.12f
    fun widthResponse(rearIndex: Int) =
        (0.35f + 0.06f * (rearIndex.coerceAtLeast(0) + 1)) * ANIMATION_SECONDS * MOTION_TIME_SCALE
    fun positionResponse(rearIndex: Int) =
        (0.44f + 0.06f * (rearIndex.coerceAtLeast(0) + 1)) * ANIMATION_SECONDS * MOTION_TIME_SCALE

    /** Grow while emerging, rather than finishing the growth while hidden behind the lead. */
    fun revealGrowth(size: Float, separation: Float): Float {
        val p = separation.coerceIn(0f, 1f)
        return minOf(size.coerceIn(0f, 1f), p * p)
    }

    /** Zero slope and acceleration at each end of the native-layout hand-off. */
    fun handoffMix(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p * p * p * (10f + p * (-15f + 6f * p))
    }

    /** Width starts narrowing without a velocity jump as a card enters the capsule's foot. */
    fun footNarrowing(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    fun windowHeight(foldedWidth: Float, foldedHeight: Float, width: Float, height: Float,
                     visibleWidth: Float): Float {
        val removedWidth = width - foldedWidth
        if (removedWidth <= 0f) return height
        val clipped = ((width - visibleWidth) / removedWidth).coerceIn(0f, 1f)
        return height - (height - foldedHeight) * clipped
    }

    fun contentTop(windowHeight: Float, contentHeight: Float, contentScale: Float) =
        (windowHeight - contentHeight * contentScale) / 2f
}
