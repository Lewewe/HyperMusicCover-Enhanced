// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Behaviour modelled on ColorOS 16; no ColorOS implementation is included.
// Developed with the assistance of OpenAI Codex and Anthropic Claude.
package com.os4.musiccover

/** Geometry of the rear cards, whose background window and content have separate transforms. */
internal object ColorOsSpreadGeometry {
    /** An already open player shares the lead's clock and always leaves room below it. */
    fun mediaTop(from: Float, nativeTop: Float, progress: Float, leadTop: Float,
                 height: Float, gap: Float): Float = minOf(
        from + (nativeTop - from) * progress.coerceIn(0f, 1f), leadTop - height - gap)

    /** Both card edges travel toward the pill along the same reversible, height-led guide. */
    fun routeWidth(pillTop: Float, pillWidth: Float, cardTop: Float, cardWidth: Float, top: Float): Float {
        val distance = pillTop - cardTop
        if (distance <= 1f) return cardWidth
        val p = ((pillTop - top) / distance).coerceIn(0f, 1f)
        // Reach full width midway, then travel vertically; ease the join in either direction.
        return pillWidth + (cardWidth - pillWidth) * footNarrowing(p / 0.55f)
    }

    // Keep the stagger bounded: the tail must not trail a fast flick into the shortcut row.
    fun widthResponse(rearIndex: Int) =
        0.18f + 0.1f * rearIndex.coerceAtLeast(0) / (rearIndex.coerceAtLeast(0) + 3f)
    fun positionResponse(rearIndex: Int) =
        widthResponse(rearIndex) + 0.04f

    fun footBottom(pillTop: Float, pillHeight: Float, clearance: Float, progress: Float): Float =
        pillTop + pillHeight - (pillHeight + clearance) * progress.coerceIn(0f, 1f)

    fun clipHeightAtFoot(height: Float, top: Float, scale: Float, footBottom: Float): Float =
        minOf(height, ((footBottom - top) / scale).coerceAtLeast(0f))

    /** Rear cards make room only after the unpushed lead crosses the native footer. */
    fun footerFill(pillTop: Float, footer: Float, leadHeight: Float, progress: Float,
                   nativeTop: Float = footer - leadHeight.coerceAtLeast(1f)): Float {
        val h = leadHeight.coerceAtLeast(1f)
        val top = pillTop + (nativeTop - pillTop) * progress.coerceIn(0f, 1f)
        val fill = ((footer - top) / h).coerceIn(0f, 1f)
        return fill * fill * (3f - 2f * fill)
    }

    fun footerLeadOffset(nativeTop: Float, height: Float, footer: Float, fill: Float): Float =
        (footer - height - nativeTop).coerceAtLeast(0f) * (1f - fill.coerceIn(0f, 1f))

    fun footerRowProgress(fill: Float, rearIndex: Int): Float {
        val index = rearIndex.coerceAtLeast(0).toFloat()
        val delay = 0.08f * index / (index + 3f)
        return ((fill - delay) / (1f - delay)).coerceIn(0f, 1f)
    }

    fun foldMorphEnd(pillTop: Float, footer: Float, leadTop: Float, leadHeight: Float): Float =
        ((pillTop - footer + leadHeight) / (pillTop - leadTop).coerceAtLeast(1f)).coerceIn(0.1f, 0.9f)

    fun foldGather(progress: Float, morphEnd: Float): Float =
        ((progress - morphEnd) / (1f - morphEnd)).coerceIn(0f, 1f)

    fun foldLeadProgress(progress: Float, morphEnd: Float): Float =
        (progress / morphEnd).coerceIn(0f, 1f)

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
