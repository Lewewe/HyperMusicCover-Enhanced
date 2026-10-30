// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Developed with the assistance of OpenAI Codex.
package com.os4.musiccover

/** The summary capsule crosses complete faces; a group's individual messages are not anchors. */
internal object ColorOsNotificationMotion {
    const val OPEN_RESPONSE = 0.54f
    const val OPEN_DAMPING = 0.999f

    fun openingResponse(notificationCount: Int) = if (notificationCount in 1..3) 0.45f else OPEN_RESPONSE

    // Keep release speed below the non-overshooting spring's remaining stopping distance.
    fun openingVelocity(progress: Float, velocity: Float, response: Float = OPEN_RESPONSE): Float = velocity.coerceIn(0f,
        minOf(4f, (1f - progress).coerceIn(0f, 1f) * (2f * Math.PI.toFloat() / response)))

    fun materialOut(progress: Float) = 1f - ease(0.08f, 0.425f, progress)
    fun nativeIn(progress: Float) = ease(0f, 0.08f, progress)
    fun contentOut(progress: Float) = 1f - ease(0f, 0.25f, progress)
    fun rearAlpha(progress: Float) = ease(0f, 0.3f, progress)
    fun rearWidthLimit(anchor: Float, foldedScale: Float, separation: Float) =
        anchor * (foldedScale + (1f - foldedScale) * separation.coerceIn(0f, 1f))
    fun blurRemaining(size: Float) = 1f - ease(0.25f, 0.95f, size)
    fun foldContentAlpha(progress: Float) = ease(0f, 1f, progress)
    fun foldBlur(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return 4f * p * (1f - p)
    }

    /** Use the unstacked row's destination, not its current folded visibility. */
    fun entersViewport(top: Float, height: Float, remainingScroll: Float,
                       viewportHeight: Float): Boolean =
        top - remainingScroll < viewportHeight && top + height - remainingScroll > 0f

    /** A NUMBER hide retained by showSpreadRow is not a fresh hide of the opened list. */
    fun restoreHidden(requestedHidden: Boolean, prepared: Boolean,
                      visibleAtHandback: Boolean, inViewport: Boolean): Boolean =
        requestedHidden && !(prepared && visibleAtHandback && inViewport)

    fun retainedVisibility(pastEnd: Boolean, prepared: Boolean, opening: Boolean, mix: Float): Float = when {
        !pastEnd -> 1f
        opening -> if (prepared) 1f else 0f
        else -> 1f - mix.coerceIn(0f, 1f)
    }

    private fun ease(from: Float, to: Float, value: Float): Float {
        val t = ((value - from) / (to - from)).coerceIn(0f, 1f)
        return t * t * t * (10f + t * (-15f + 6f * t))
    }
}
