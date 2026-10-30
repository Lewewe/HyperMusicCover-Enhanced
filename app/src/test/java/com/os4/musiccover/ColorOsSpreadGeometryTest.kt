// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Lewewe (HyperMusicCover-Enhanced, https://github.com/Lewewe/HyperMusicCover-Enhanced)
// Developed with the assistance of OpenAI Codex and Anthropic Claude.
package com.os4.musiccover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorOsSpreadGeometryTest {
    @Test fun oneToThreeNotificationsFollowStraightEdgesUntilTheListAndReverseTheSameGuide() {
        for (count in 1..3) for (cardTop in listOf(1293f, 1798f, 2057f)) {
            val widths = (0..120).map { frame ->
                val p = frame / 120f
                val top = 2416f + (cardTop - 2416f) * p
                val width = ColorOsSpreadGeometry.routeWidth(2416f, 622f, cardTop, 1128f, top, count)
                assertEquals(622f + 506f * p, width, 0.001f)
                if (frame < 120) assertTrue(width < 1128f)
                width
            }
            assertEquals(622f, widths.first(), 0f)
            assertEquals(1128f, widths.last(), 0f)
            assertTrue(widths.asReversed().zipWithNext().all { (a, b) -> b < a })
        }
    }

    @Test fun longerOrUnknownListsKeepTheirExistingEarlyFullWidthGuide() {
        for (count in listOf(0, 4, 10, 100)) for (frame in 0..120) {
            val top = 2416f - 618f * frame / 120f
            assertEquals(ColorOsSpreadGeometry.routeWidth(2416f, 622f, 1798f, 1128f, top),
                ColorOsSpreadGeometry.routeWidth(2416f, 622f, 1798f, 1128f, top, count), 0f)
        }
    }

    @Test fun closingMediaFollowsTheLeadBackToItsOriginalSeat() {
        var previous = 866f
        for (i in 0..120) {
            val p = 1f - i / 120f
            val lead = 2416f + (1473f - 2416f) * p
            val top = ColorOsSpreadGeometry.mediaTop(1709f, 866f, p, lead, 582f, 23f)
            assertTrue(top >= previous - 0.001f)
            assertTrue(top - previous < 10f)
            assertTrue(lead - top - 582f >= 23f - 0.001f)
            previous = top
        }
        assertEquals(1709f, previous, 0f)
    }

    @Test fun turningAnOpeningAroundStartsFromTheCurrentlyDrawnMediaPosition() {
        val turnAt = 0.6f
        val drawnTop = 1012f
        assertEquals(drawnTop, ColorOsSpreadGeometry.mediaTop(1709f, drawnTop,
            turnAt / turnAt, 1633f, 582f, 23f), 0.001f)
        assertEquals(1709f, ColorOsSpreadGeometry.mediaTop(1709f, drawnTop,
            0f / turnAt, 2416f, 582f, 23f), 0f)
    }

    @Test fun expandedMediaSharesOpeningProgressAndKeepsTheNotificationGap() {
        var previous = 1710f
        for (i in 0..120) {
            val p = i / 120f
            val lead = 2416f + (1600f - 2416f) * p
            val top = ColorOsSpreadGeometry.mediaTop(1710f, 993f, p, lead, 582f, 25f)
            assertTrue(top <= previous)
            assertTrue(lead - top - 582f >= 25f)
            assertTrue(previous - top < 10f)
            previous = top
        }
        assertEquals(1710f, ColorOsSpreadGeometry.mediaTop(1710f, 993f, 0f, 2416f, 582f, 25f), 0f)
        assertEquals(993f, previous, 0f)
    }

    @Test fun mediaReturnsToNativeWithoutAnExtraGapAtHandoff() {
        val top = ColorOsSpreadGeometry.mediaTop(1709f, 686f, 1f, 1293f, 582f, 23f)
        assertEquals(686f, top, 0f)
        assertEquals(25f, 1293f - top - 582f, 0f)
    }

    @Test fun expandedMediaMakesRoomForALeadThatArrivesEarlier() {
        assertEquals(1393f, ColorOsSpreadGeometry.mediaTop(1710f, 993f, 0.2f, 2000f, 582f, 25f), 0.001f)
    }

    @Test fun openingRearFillFollowsActualLeadTravelEvenForLongLists() {
        for (nativeTop in listOf(700f, 1293f, 2100f)) {
            var previous = 0f
            for (frame in 0..120) {
                val p = frame / 120f
                val top = 2420f + (nativeTop - 2420f) * p
                val fill = ColorOsSpreadGeometry.footerFill(2420f, 2291f, 234f, p, nativeTop)
                if (top >= 2291f) assertEquals(0f, fill, 0f)
                if (top <= 2057f) assertEquals(1f, fill, 0f)
                assertTrue(fill >= previous)
                previous = fill
            }
            val crossing = (2420f - 2291f) / (2420f - nativeTop)
            assertEquals(0f, ColorOsSpreadGeometry.footerFill(2420f, 2291f, 234f, crossing, nativeTop), 0.00001f)
        }
    }

    @Test fun bothEdgesFollowTheGuideInEitherDirectionForLongAndShortLists() {
        for (cardTop in listOf(700f, 1293f, 2100f)) {
            val widths = (0..120).map { frame ->
                val p = frame / 120f
                val top = 2420f + (cardTop - 2420f) * p
                val width = ColorOsSpreadGeometry.routeWidth(2420f, 600f, cardTop, 1128f, top)
                if (p >= 0.55f) assertEquals(1128f, width, 0.001f)
                assertEquals(1220f, (610f - width / 2f) + (610f + width / 2f), 0.001f)
                assertTrue(width in 600f..1128f)
                width
            }
            assertTrue(widths.zipWithNext().all { (a, b) -> b >= a })
            assertTrue(widths.asReversed().zipWithNext().all { (a, b) -> b <= a })
        }
        assertEquals(600f, ColorOsSpreadGeometry.routeWidth(2420f, 600f, 700f, 1128f, 3000f), 0f)
        assertEquals(1128f, ColorOsSpreadGeometry.routeWidth(2420f, 600f, 700f, 1128f, 100f), 0f)
        assertEquals(1128f, ColorOsSpreadGeometry.routeWidth(2420f, 600f, 3000f, 1128f, 2500f), 0f)
    }

    @Test fun routeJoinsFullWidthWithoutAnEdgeVelocityJump() {
        val pill = 2420f
        val top = 700f
        fun width(p: Float) = ColorOsSpreadGeometry.routeWidth(pill, 600f, top, 1128f,
            pill + (top - pill) * p)
        assertEquals(600f, width(0f), 0f)
        assertEquals(864f, width(0.275f), 0.001f)
        assertTrue("join should ease out", width(0.55f) - width(0.549f) < 0.01f)
        for (p in listOf(0.55f, 0.7f, 0.9f, 1f)) assertEquals(1128f, width(p), 0.001f)
    }

    @Test fun footerPushDoesNotConcentrateItsTravelIntoAQuickMiddleKick() {
        // Normalize the crossing-to-seat interval: no more than 1.51x its average fill speed.
        val start = (2420f - 2291f) / (2420f - 2291f + 234f)
        var previous = 0f
        for (i in 1..120) {
            val p = start + (1f - start) * i / 120f
            val fill = ColorOsSpreadGeometry.footerFill(2420f, 2291f, 234f, p)
            assertTrue(fill >= previous)
            assertTrue((fill - previous) * 120f < 1.51f)
            previous = fill
        }
        assertEquals(1f, previous, 0.00001f)
    }

    @Test fun foldingGathersBehindAWholeLeadBeforeMorphingAtTheFooter() {
        for (top in listOf(1293f, 1800f, 2200f)) {
            val end = ColorOsSpreadGeometry.foldMorphEnd(2420f, 2291f, top, 234f)
            assertTrue(end in 0.1f..0.9f)
            var previousGather = 1f
            var previousMorph = 1f
            for (frame in 120 downTo 0) {
                val p = frame / 120f
                val gather = ColorOsSpreadGeometry.foldGather(p, end)
                val morph = ColorOsSpreadGeometry.foldLeadProgress(p, end)
                assertTrue(gather <= previousGather)
                assertTrue(morph <= previousMorph)
                if (gather > 0f) assertEquals(1f, morph, 0f)
                if (morph < 1f) assertEquals(0f, gather, 0f)
                for (rear in 0..20) {
                    if (gather == 0f) assertEquals(0f, ColorOsSpreadGeometry.footerRowProgress(gather, rear), 0f)
                }
                previousGather = gather
                previousMorph = morph
            }
            assertEquals(1f, ColorOsSpreadGeometry.foldLeadProgress(end, end), 0f)
            assertEquals(0f, ColorOsSpreadGeometry.foldGather(end, end), 0f)
            assertEquals(0f, previousMorph, 0f)
        }
    }

    @Test fun footerRowsWaitForTheLeadToCrossTheNativeBorder() {
        val pillTop = 2420f
        val footer = 2293f
        val height = 234f
        val crossing = (pillTop - footer) / (pillTop - footer + height)
        assertEquals(0f, ColorOsSpreadGeometry.footerFill(pillTop, footer, height, crossing), 0.00001f)
        assertEquals(0f, ColorOsSpreadGeometry.footerFill(pillTop, footer, height, crossing - 0.1f), 0f)
        assertTrue(ColorOsSpreadGeometry.footerFill(pillTop, footer, height, crossing + 0.001f) < 0.00001f)
        assertEquals(1f, ColorOsSpreadGeometry.footerFill(pillTop, footer, height, 1f), 0f)
    }

    @Test fun fillingTheFooterPushesTheLeadUpAndEndsAtItsExactNativePosition() {
        for (nativeTop in listOf(1293f, 1800f, 2059f)) {
            var previousTop = 2420f
            var previousOffset = Float.POSITIVE_INFINITY
            for (frame in 0..120) {
                val p = frame / 120f
                val fill = ColorOsSpreadGeometry.footerFill(2420f, 2293f, 234f, p)
                val offset = ColorOsSpreadGeometry.footerLeadOffset(nativeTop, 234f, 2293f, fill)
                val top = 2420f + (nativeTop + offset - 2420f) * p
                assertTrue("the column must never push the lead back down", top <= previousTop + 0.001f)
                assertTrue(offset <= previousOffset)
                if (fill == 0f) assertEquals(2059f, nativeTop + offset, 0.001f)
                previousTop = top
                previousOffset = offset
            }
            assertEquals(nativeTop, previousTop, 0.001f)
            assertEquals(0f, previousOffset, 0f)
        }
        assertEquals("a native row not laid out yet must keep its parked guard", 0f,
            ColorOsSpreadGeometry.footerLeadOffset(3000f, 234f, 2293f, 0f), 0f)
    }

    @Test fun footerStaggerSharesTheReversibleFillAndCannotLeaveALateTail() {
        for (frame in 0..120) {
            val fill = frame / 120f
            var previous = 1f
            for (index in 0..100) {
                val p = ColorOsSpreadGeometry.footerRowProgress(fill, index)
                assertTrue(p in 0f..previous)
                if (frame == 0) assertEquals(0f, p, 0f)
                if (frame == 120) assertEquals(1f, p, 0.00001f)
                previous = p
            }
        }
        assertTrue(ColorOsSpreadGeometry.footerRowProgress(0.5f, 100) > 0.45f)
    }

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

    @Test fun longColumnsKeepTheirStaggerWithinAFastFlick() {
        for (index in 0..500) {
            assertTrue(ColorOsSpreadGeometry.widthResponse(index) <= 0.28f)
            assertTrue(ColorOsSpreadGeometry.positionResponse(index) <= 0.32f)
        }
        for (hz in listOf(60, 120)) {
            val card = ColorOsRearCardMotion(startedOpen = false)
            card.place.response = ColorOsSpreadGeometry.positionResponse(30)
            card.width.response = ColorOsSpreadGeometry.widthResponse(30)
            repeat(hz / 3) { card.step(1f, opening = true, dt = 1f / hz) }
            assertTrue("even the tail must finish growing promptly", card.revealedSize > 0.98f)
        }
    }

    @Test fun rearFootClearsTheShortcutsOnTheLeadsProgressWithoutWaitingForRearSprings() {
        val top = 2420f
        val height = 140f
        val gap = 25f
        var previous = top + height
        for (frame in 0..120) {
            val foot = ColorOsSpreadGeometry.footBottom(top, height, gap, frame / 120f)
            assertTrue(foot <= previous)
            assertTrue(foot >= top - gap)
            previous = foot
        }
        assertEquals(top + height, ColorOsSpreadGeometry.footBottom(top, height, gap, 0f), 0f)
        assertEquals(top - gap, previous, 0f)
        assertEquals(top - gap, ColorOsSpreadGeometry.footBottom(top, height, gap, 1.2f), 0f)
        assertEquals(top + height, ColorOsSpreadGeometry.footBottom(top, height, gap, -0.2f), 0f)
    }

    @Test fun nativeHandoffCannotDrawContentOrBackdropBelowTheMovingFoot() {
        for (scale in listOf(0.1f, 0.85f, 1f)) {
            for (frame in 0..120) {
                val foot = ColorOsSpreadGeometry.footBottom(2420f, 140f, 25f, frame / 120f)
                for (top in listOf(2000f, 2300f, 2400f, 2600f)) {
                    val nativeHeight = 280f
                    val clipped = ColorOsSpreadGeometry.clipHeightAtFoot(nativeHeight, top, scale, foot)
                    assertTrue(clipped in 0f..nativeHeight)
                    if (top >= foot) assertEquals(0f, clipped, 0f)
                    else assertTrue("the rendered foot must clear the buttons", top + clipped * scale <= foot + 0.001f)
                }
            }
        }
        assertEquals("native clipping must remain tighter when needed", 20f,
            ColorOsSpreadGeometry.clipHeightAtFoot(20f, 2000f, 1f, 2395f), 0f)
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
