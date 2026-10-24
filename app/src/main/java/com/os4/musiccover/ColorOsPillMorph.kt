// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced),
// modelled on ColorOS 16 (OPPO) behaviour; no ColorOS code is included.
package com.os4.musiccover

/**
 * ColorOS's capsule opening into its card: its own choreography, apart from the super island's,
 * so the capsule can be tuned without moving HyperOS's pill and HyperOS's can go without
 * touching it. The engine is MiniCardMorph's, shared.
 *
 * It starts from the same curves the two styles shared until now (2026-10-24); every number
 * below is the capsule's own from here.
 */
internal object ColorOsPillMorph : PillMorphStyle {
    override fun containerFrame(a: CoverMorphMotion.Box, b: CoverMorphMotion.Box,
                                progress: Float): CoverMorphMotion.Box {
        // Straight between the two, past either end along the same line, the size only a little.
        val p = progress.coerceIn(-0.3f, 1.3f)
        val sizeP = progress.coerceIn(-0.025f, 1.025f)
        val cx = lerp(a.cx(), b.cx(), p)
        val w = lerp(a.w, b.w, sizeP)
        val h = lerp(a.h, b.h, sizeP)
        // Held by the top edge: both layouts begin at the top, so their content lines up there.
        val top = lerp(a.y, b.y, p)
        return CoverMorphMotion.Box(cx - w / 2f, top, w, h)
    }

    override fun cornerRadius(pill: Float, card: Float, progress: Float) =
        lerp(pill, card, progress.coerceIn(0f, 1f))

    /** Led by the notification list, the capsule is its first card: handed over by halfway. */
    override fun faceProgress(progress: Float, spreadLed: Boolean) =
        if (spreadLed) kotlin.math.min(1f, progress * 2f) else progress

    override fun nativeIn(face: Float) = smooth(0.1f, 0.5f, face)

    /** Last and alone: both materials part-transparent at once read as a dip in the glass. */
    override fun materialOut(face: Float) = 1f - smooth(0.45f, 0.85f, face)

    override fun earlyOut(face: Float) = 1f - smooth(0f, 0.3f, face)

    override fun pieceMix(progress: Float) = smooth(0f, 0.5f, progress)

    override fun pairedOut(face: Float) = 1f - smooth(0.85f, 0.93f, face)

    override fun circleIn(face: Float) = smooth(0.12f, 0.4f, face)

    override fun cardBlurFactor(progress: Float, media: Boolean, largeArtwork: Boolean, opening: Boolean,
                                mediaPillTap: Boolean) = when {
        !media -> 1f - smooth(0.4f, 0.95f, progress)
        // Tapped open into the big cover: blurred all the way; it comes into focus as it lands
        // (landedFocusAt).
        largeArtwork && opening && mediaPillTap -> 1f
        else -> 0f
    }

    /**
     * The card in focus as it lands, not once the spring has all but stopped: that came half a
     * second after the card was there, and brought into focus on the way instead, the blur was
     * gone altogether - it is the design, only slow (the user, 2026-10-24).
     */
    override val landedFocusAt = 0.9f
    /** Held a little blurred, then eased into focus: 220ms out of it read as too quick (2026-10-24). */
    override val focusFadeMs = 360L

    override fun focusLeft(t: Float) = 1f - smooth(0f, 1f, t)

    override fun pillBlurFactor(face: Float, media: Boolean, largeArtwork: Boolean, opening: Boolean) =
        if (!media) smooth(0f, 0.6f, face) else 0f

    /** The capsule's content blur at its far end, 9dp. */
    override val blurDp = 9f
    override val nativeTextLead = 3f
    override val circleIconShare = 0.62f

    private fun smooth(start: Float, end: Float, value: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
