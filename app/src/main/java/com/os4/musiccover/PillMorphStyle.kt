// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
package com.os4.musiccover

/**
 * How a pill turns into its card and back: the choreography, apart from MiniCardMorph's engine.
 *
 * The engine - the springs, the finger, catching a morph mid-way, the geometry and putting the
 * card back as it was - is one for every pill. What the eye sees on the way is the style's own:
 * ColorOS's capsule ([ColorOsPillMorph]) and HyperOS's super island ([HyperOsPillMorph]) each
 * keep theirs, so tuning one never moves the other, and either can go without touching the rest.
 *
 * Progress runs 0 at the pill to 1 at the card. The "face" progress is the one the hand-over
 * between the two faces runs on (see [faceProgress]); the rest run on the plain progress.
 */
internal interface PillMorphStyle {
    /** The container between the pill's box and the card's, past either end too. */
    fun containerFrame(a: CoverMorphMotion.Box, b: CoverMorphMotion.Box, progress: Float): CoverMorphMotion.Box

    /** The container's corners, from the pill's (half its height) to the card's. */
    fun cornerRadius(pill: Float, card: Float, progress: Float): Float

    /** The progress the faces hand over on: led by the notification list, sooner. */
    fun faceProgress(progress: Float, spreadLed: Boolean): Float

    /** The card's own content coming in under the pill's material. */
    fun nativeIn(face: Float): Float

    /** The pill's material, leaving last and alone. */
    fun materialOut(face: Float): Float

    /** The pill's own controls, with no card element to land on. */
    fun earlyOut(face: Float): Float

    /** How far the paired pieces have gone from the pill's layout onto the card's elements. */
    fun pieceMix(progress: Float): Float

    /** The paired pieces' share once they lie over the card's own. */
    fun pairedOut(face: Float): Float

    /** Out of a small island's circle, the lines joining its picture. */
    fun circleIn(face: Float): Float

    /** The card's content blur, as a share of its far end. */
    fun cardBlurFactor(progress: Float, media: Boolean, largeArtwork: Boolean, opening: Boolean,
                       mediaPillTap: Boolean): Float

    /** The pill's content blur, as a share of its far end. */
    fun pillBlurFactor(face: Float, media: Boolean, largeArtwork: Boolean, opening: Boolean): Float

    /**
     * Tapped open into the big cover, the card lands blurred and comes into focus: the share
     * of the way at which that starts, or NaN for once the morph has settled.
     */
    val landedFocusAt: Float

    /** How long the card takes to come into focus. */
    val focusFadeMs: Long

    /** The share of the card's blur left [t] of the way through its focus fade. */
    fun focusLeft(t: Float): Float

    /** Both ends' content blur at its far end. */
    val blurDp: Float

    /** How much faster the card's line comes in than the pill's leaves it. */
    val nativeTextLead: Float

    /** A small island's picture against its circle. */
    val circleIconShare: Float

    companion object {
        /** The style the row is drawn in now. */
        fun of(colorOs: Boolean): PillMorphStyle = if (colorOs) ColorOsPillMorph else HyperOsPillMorph
    }
}
