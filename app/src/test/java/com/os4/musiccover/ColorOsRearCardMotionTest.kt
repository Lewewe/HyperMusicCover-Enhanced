// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Developed with the assistance of OpenAI Codex and Anthropic Claude.
package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsRearCardMotionTest {
    @Test fun aUserFlingDoesNotKeepSettledCardsInMixedOwnershipUntilItStops() {
        val handoff = ColorOsSpreadHandoff()
        handoff.listOpened(1L)
        assertTrue(!handoff.canReturnToNative(true, 1f, false, true))
        handoff.scroll(2L, true)
        assertTrue(handoff.canReturnToNative(true, 1f, false, true))
        assertTrue("scrolling cannot hand back unfinished geometry", !handoff.canReturnToNative(false, 1f, false, true))
        assertTrue("the capsule transition still has to finish", !handoff.canReturnToNative(true, 0.8f, false, true))
        assertTrue("a resting pile remains ours", !handoff.canReturnToNative(true, 1f, true, true))
    }

    @Test fun scrollingTheOpenedListDoesNotWaitForTheOpeningHandoff() {
        val handoff = ColorOsSpreadHandoff()
        handoff.listOpened(3L)
        assertTrue("the original opening flick keeps its flash guard", !handoff.scroll(3L, true))
        assertTrue("a new touch alone must not release rows", !handoff.scroll(4L, false))
        assertTrue(handoff.scroll(4L, true))
        assertTrue("the following fling keeps native viewport entries visible", handoff.scroll(4L, false))
        assertTrue("no handoff spring needs to finish first", !handoff.started)
    }

    @Test fun aRowRevealedFromThePileGrowsDuringAnAlreadyRunningHandoff() {
        val card = ColorOsRearCardMotion(startedOpen = false, growIntoNative = true)
        card.all.forEach { it.target = 1f }
        assertEquals(0f, card.nativeMix(1f), 0f)
        repeat(12) { card.all.forEach { it.step(1f / 120f) } }
        assertTrue("the row must be growing before the column settles", card.nativeMix(1f) > 0f)
        assertTrue("the old hand-off must not make the new row full size", card.nativeMix(1f) < 1f)
        repeat(180) { card.all.forEach { it.step(1f / 120f) } }
        assertEquals(1f, card.nativeMix(1f), 0.001f)
        assertEquals(0.4f, card.nativeMix(0.4f), 0.001f)
    }

    @Test fun aFlingDestinationDoesNotBecomeTheInitialSize() {
        val card = ColorOsRearCardMotion(startedOpen = false)
        card.width.response = ColorOsSpreadGeometry.widthResponse(0)
        card.all.forEach { it.target = 1f }
        assertEquals(0f, card.width.value, 0f)
        card.all.forEach { it.step(1f / 120f) }
        assertTrue("the first drawn frame must still be small", card.width.value < 0.1f)
        val firstWidth = card.width.value
        repeat(24) { card.all.forEach { it.step(1f / 120f) } }
        assertTrue(card.width.value > firstWidth)
        assertTrue(card.width.value > 0.9f)

        // A native row arriving late must not inherit its fully grown neighbour's size.
        val late = ColorOsRearCardMotion(startedOpen = false)
        late.all.forEach { it.target = 1f }
        assertEquals(0f, late.width.value, 0f)
    }

    @Test fun foldingAnExistingListStartsAtTheOpenSize() {
        val card = ColorOsRearCardMotion(startedOpen = true)
        card.all.forEach { it.target = 0f }
        assertEquals(1f, card.width.value, 0f)
        card.all.forEach { it.step(1f / 120f) }
        assertTrue(card.width.value > 0.9f)
        assertTrue(card.width.value < 1f)
    }

    @Test fun aFastSizeSpringCannotFinishGrowthBehindTheFirstCard() {
        val card = ColorOsRearCardMotion(startedOpen = false)
        card.width.value = 1f
        card.place.value = 0.5f
        assertEquals(0.25f, card.revealedSize, 0.0001f)
        card.place.value = 0.8f
        assertEquals(0.64f, card.revealedSize, 0.0001f)
        assertTrue("native hand-off must wait", card.revealedSize < 0.985f)
        card.place.value = 1f
        assertEquals(1f, card.revealedSize, 0f)
    }

    @Test fun aLateBottomRowCannotReverseTheHandoff() {
        val handoff = ColorOsSpreadHandoff()
        assertEquals(0f, handoff.target(piled = false, grown = false), 0f)
        assertEquals(1f, handoff.target(piled = false, grown = true), 0f)
        repeat(10) {
            assertEquals(1f, handoff.target(piled = false, grown = false), 0f)
            assertEquals(1f, handoff.target(piled = false, grown = true), 0f)
        }
    }

    @Test fun aShortPullKeepsItsPileUntilItActuallyOpensToTheList() {
        val handoff = ColorOsSpreadHandoff()
        assertEquals(0f, handoff.target(piled = true, grown = true), 0f)
        assertTrue(!handoff.started)
        assertEquals(1f, handoff.target(piled = false, grown = true), 0f)
    }

    @Test fun nativePileClippingDoesNotSnapOnAtTheHandoff() {
        val clip = ColorOsSpreadClip(200, 40)
        clip.step(pileCut = 0, handoff = 0f, dt = 1f / 120f)
        assertEquals(0, clip.topAmount)
        clip.step(pileCut = 0, handoff = 1f, dt = 1f / 120f)
        assertTrue(clip.topAmount in 1..20)
        assertTrue(!clip.settled())
        repeat(120) { clip.step(0, 1f, 1f / 120f) }
        assertEquals(200, clip.topAmount)
        assertEquals(40, clip.bottomAmount)
        assertTrue(clip.settled())
    }

    @Test fun aLateNativeClipUpdateIsAlsoSmoothed() {
        val clip = ColorOsSpreadClip(0, 0)
        repeat(60) { clip.step(0, 1f, 1f / 120f) }
        clip.nativeTop = 230
        clip.step(0, 1f, 1f / 120f)
        assertTrue(clip.topAmount < 20)
        repeat(120) { clip.step(0, 1f, 1f / 120f) }
        assertEquals(230, clip.topAmount)
    }

    @Test fun aNativeHiddenBottomCardFadesInsteadOfDisappearingInOneFrame() {
        val clip = ColorOsSpreadClip(0, 0)
        clip.nativeVisible = false
        clip.step(0, 1f, 1f / 120f)
        assertTrue(clip.visibilityAmount > 0.9f)
        assertTrue(!clip.settled())
        repeat(120) { clip.step(0, 1f, 1f / 120f) }
        assertEquals(0f, clip.visibilityAmount, 0.004f)
        assertTrue(clip.settled())
    }

    @Test fun hyperOsExtendedPileClipIsBlendedAlongsideTheOrdinaryClip() {
        val clip = ColorOsSpreadClip(0, 20)
        clip.nativeExtBottom = 200
        clip.step(0, 1f, 1f / 120f)
        assertTrue(clip.bottomAmount in 1..20)
        repeat(120) { clip.step(0, 1f, 1f / 120f) }
        assertEquals(200, clip.bottomAmount)
        clip.nativeExtBottom = 0
        repeat(120) { clip.step(0, 1f, 1f / 120f) }
        assertEquals(20, clip.bottomAmount)
    }
}
