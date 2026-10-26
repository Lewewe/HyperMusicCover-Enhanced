// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Developed with the assistance of OpenAI Codex and Anthropic Claude.
package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsSpreadGeometryTest {
    @Test fun theWindowOpensFromCapsuleHeightToCardHeightWithoutAnotherClock() {
        var previous = 0f
        for (step in 0..100) {
            val width = 400f + 600f * step / 100f
            val height = ColorOsSpreadGeometry.windowHeight(400f, 90f, 1000f, 240f, width)
            assertTrue(height >= previous)
            assertTrue(height in 90f..240f)
            previous = height
        }
        assertEquals(90f, ColorOsSpreadGeometry.windowHeight(400f, 90f, 1000f, 240f, 400f), 0f)
        assertEquals(240f, previous, 0f)
    }

    @Test fun scaledContentStaysCenteredEvenWhenWindowAndContentHaveDifferentAspectRatios() {
        for (step in 0..100) {
            val width = 400f + 600f * step / 100f
            val height = ColorOsSpreadGeometry.windowHeight(400f, 90f, 1000f, 240f, width)
            val scaledHeight = 240f * width / 1000f
            val top = ColorOsSpreadGeometry.contentTop(height, 240f, width / 1000f)
            assertEquals(height / 2f, top + scaledHeight / 2f, 0.0001f)
        }
        assertEquals(0f, ColorOsSpreadGeometry.contentTop(240f, 240f, 1f), 0f)
    }

    @Test fun laterCardsRespondLaterWithoutAThreeCardPlateau() {
        for (index in 0..8) {
            assertTrue(ColorOsSpreadGeometry.widthResponse(index + 1) > ColorOsSpreadGeometry.widthResponse(index))
            assertTrue(ColorOsSpreadGeometry.positionResponse(index) > ColorOsSpreadGeometry.widthResponse(index))
        }
    }

    @Test fun theRevealUsesTheNativeAnimationClockAndSettlesWithinItsDuration() {
        val first = Jelly(ColorOsSpreadGeometry.widthResponse(0), 0.999f).apply { target = 1f }
        val sixth = Jelly(ColorOsSpreadGeometry.widthResponse(5), 0.999f).apply { target = 1f }
        repeat(24) { first.step(1f / 120f); sixth.step(1f / 120f) }
        assertTrue("first card must reveal promptly", first.value > 0.9f)
        assertTrue("later cards should still follow", sixth.value < first.value)
        repeat(42) { first.step(1f / 120f); sixth.step(1f / 120f) }
        assertEquals(1f, sixth.value, 0.01f)
    }

    @Test fun nativeHandoffHasNoHardStartOrStop() {
        var previous = 0f
        for (step in 0..100) {
            val value = ColorOsSpreadGeometry.handoffMix(step / 100f)
            assertTrue(value >= previous)
            assertTrue(value in 0f..1f)
            previous = value
        }
        val epsilon = 0.001f
        assertTrue(ColorOsSpreadGeometry.handoffMix(epsilon) / epsilon < 0.001f)
        assertTrue((1f - ColorOsSpreadGeometry.handoffMix(1f - epsilon)) / epsilon < 0.001f)
    }

    @Test fun crossingTheFootDoesNotSuddenlyStartShrinkingWidth() {
        assertEquals(0f, ColorOsSpreadGeometry.footNarrowing(0f), 0f)
        assertEquals(1f, ColorOsSpreadGeometry.footNarrowing(1f), 0f)
        val epsilon = 0.001f
        assertTrue(ColorOsSpreadGeometry.footNarrowing(epsilon) / epsilon < 0.004f)
    }
}
