// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
package com.os4.musiccover

/**
 * The super island's big island opening into its card: the choreography the morph has always
 * had, its curves kept in MiniCardMorph's companion where their tests read them.
 */
internal object HyperOsPillMorph : PillMorphStyle {
    override fun containerFrame(a: CoverMorphMotion.Box, b: CoverMorphMotion.Box, progress: Float) =
        MiniCardMorph.containerFrame(a, b, progress)

    override fun cornerRadius(pill: Float, card: Float, progress: Float) =
        MiniCardMorph.lerp(pill, card, progress.coerceIn(0f, 1f))

    override fun faceProgress(progress: Float, spreadLed: Boolean) =
        if (spreadLed) kotlin.math.min(1f, progress * 2f) else progress

    override fun nativeIn(face: Float) = MiniCardMorph.nativeIn(face)
    override fun materialOut(face: Float) = MiniCardMorph.materialOut(face)
    override fun earlyOut(face: Float) = MiniCardMorph.earlyOut(face)
    override fun pieceMix(progress: Float) = MiniCardMorph.pieceMix(progress)
    override fun pairedOut(face: Float) = MiniCardMorph.pairedOut(face)
    override fun circleIn(face: Float) = MiniCardMorph.circleIn(face)

    override fun cardBlurFactor(progress: Float, media: Boolean, largeArtwork: Boolean, opening: Boolean,
                                mediaPillTap: Boolean) =
        MiniCardMorph.cardBlurFactor(progress, media, largeArtwork, opening, mediaPillTap)

    override fun pillBlurFactor(face: Float, media: Boolean, largeArtwork: Boolean, opening: Boolean) =
        MiniCardMorph.pillBlurFactor(face, media, largeArtwork, opening)

    override val landedFocusAt = Float.NaN
    override val focusFadeMs = 200L
    override fun focusLeft(t: Float) = MiniCardMorph.mediaFocusFade(t)

    override val blurDp = MiniCardMorph.BLUR_DP
    override val nativeTextLead = MiniCardMorph.NATIVE_TEXT_LEAD
    override val circleIconShare = MiniCardMorph.CIRCLE_ICON_SHARE
}
