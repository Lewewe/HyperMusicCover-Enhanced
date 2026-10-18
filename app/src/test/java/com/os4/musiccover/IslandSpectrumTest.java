package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandSpectrumTest {
    @Test public void silenceAndResetClearPreviousAudio() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[4] = 100;
        spectrum.sample(fft, 48000000);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertTrue(levels[0] > 0);
        spectrum.sample(new byte[1024], 48000000);
        spectrum.copyTo(levels);
        assertArrayEquals(new float[5], levels, 0f);
        spectrum.sample(fft, 48000000);
        spectrum.reset();
        spectrum.copyTo(levels);
        assertArrayEquals(new float[5], levels, 0f);
    }

    @Test public void frequenciesFollowSamplingRateAndDoNotLeakIntoOtherBands() {
        for (int rate : new int[] {44100000, 48000000}) {
            IslandSpectrum spectrum = new IslandSpectrum();
            byte[] fft = new byte[1024];
            int bin = Math.round(1000f * fft.length * 1000f / rate);
            fft[bin * 2] = 100;
            spectrum.sample(fft, rate);
            float[] levels = new float[5];
            spectrum.copyTo(levels);
            assertTrue(levels[2] > 0);
            for (int i = 0; i < 5; i++) if (i != 2) assertEquals(0f, levels[i], 0f);
        }
    }

    @Test public void invalidCaptureAndDcDoNotProduceFakeMovement() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[0] = 127;
        fft[1] = 127;
        spectrum.sample(fft, 48000000);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertArrayEquals(new float[5], levels, 0f);
        spectrum.sample(null, 0);
        spectrum.copyTo(levels);
        assertArrayEquals(new float[5], levels, 0f);
    }
    /** Pink noise, equal power per octave, is even to the ear: the bars stand about level. */
    @Test public void equalPowerPerOctaveStandsTheBarsEvenly() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[PcmSpectrum.SIZE];
        float[] power = new float[PcmSpectrum.SIZE / 2];
        float binWidth = 16000f / PcmSpectrum.SIZE;
        // Power density falling as 1/f: the same power in every octave.
        for (int bin = 1; bin < power.length; bin++) power[bin] = 4000f / (bin * binWidth);
        spectrum.sample(fft, 16000000, power, 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        float low = 1f, high = 0f;
        for (float level : levels) { low = Math.min(low, level); high = Math.max(high, level); }
        assertTrue(low > 0f);
        assertTrue("bars " + java.util.Arrays.toString(levels), low > high * .75f);
    }

    /** Treble far under the bass is lost to the byte format's rounding; the power keeps it. */
    @Test public void unroundedPowerKeepsQuietTrebleThatBytesLose() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[PcmSpectrum.SIZE];
        float[] power = new float[PcmSpectrum.SIZE / 2];
        int bass = Math.round(100f * PcmSpectrum.SIZE / 16000f);
        int treble = Math.round(6000f * PcmSpectrum.SIZE / 16000f);
        fft[bass * 2] = 100;
        power[bass] = 100f * 100f;
        power[treble] = .16f; // Amplitude .4: 0 once rounded to a byte.
        float[] levels = new float[5];
        spectrum.sample(fft, 16000000, null, 0L);
        spectrum.copyTo(levels);
        assertEquals(0f, levels[4], 0f);
        spectrum.reset();
        spectrum.sample(fft, 16000000, power, 0L);
        spectrum.copyTo(levels);
        assertTrue(levels[4] > 0f);
        assertTrue(levels[4] < levels[0] * .2f);
    }

    @Test public void powerOfAnotherSizeIsIgnored() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[4] = 100;
        spectrum.sample(fft, 48000000, new float[7], 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertTrue(levels[0] > 0f);
    }

    @Test public void quietPassageStaysQuietAfterLoudPassage() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[42] = 100;
        spectrum.sample(fft, 48000000, 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        float loud = levels[2];
        fft[42] = 20;
        for (int i = 1; i <= 20; i++) spectrum.sample(fft, 48000000, i * 50_000_000L);
        spectrum.copyTo(levels);
        // 14 dB down sounds a good deal quieter, not gone: about 40% as loud.
        assertTrue(levels[2] > loud * .25f);
        assertTrue(levels[2] < loud * .6f);
    }

    @Test public void gainResponseDoesNotDependOnCaptureRate() {
        IslandSpectrum fast = new IslandSpectrum();
        IslandSpectrum slow = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[42] = 100;
        fast.sample(fft, 48000000, 0L);
        slow.sample(fft, 48000000, 0L);
        fft[42] = 20;
        for (int i = 1; i <= 100; i++) fast.sample(fft, 48000000, i * 10_000_000L);
        for (int i = 1; i <= 5; i++) slow.sample(fft, 48000000, i * 200_000_000L);
        float[] fastLevels = new float[5], slowLevels = new float[5];
        fast.copyTo(fastLevels);
        slow.copyTo(slowLevels);
        // The onset follower integrates a reference that decays meanwhile; its step size shows
        // only in the third decimal.
        assertArrayEquals(fastLevels, slowLevels, .02f);
    }

    @Test public void weakTrebleIsNotRaisedToMatchStrongBass() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[4] = 100;
        fft[426] = 10;
        spectrum.sample(fft, 48000000, 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertTrue(levels[4] > 0f);
        assertTrue(levels[4] < levels[0] * .5f);
    }

    @Test public void bassHeavyMixLeavesAudibleMidsAndHighsVisible() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[4] = 100;   // Bass at about 94 Hz.
        fft[42] = 70;   // Mid at about 984 Hz.
        fft[426] = 45;  // Treble at about 10 kHz.
        spectrum.sample(fft, 48000000, 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        // Keep the real mix ordering while leaving quieter instruments visible.
        assertTrue(levels[0] > levels[2]);
        assertTrue(levels[2] > levels[4]);
        assertTrue(levels[2] > levels[0] * .6f);
        assertTrue(levels[4] > levels[0] * .45f);
        assertEquals(0f, levels[1], 0f);
        assertEquals(0f, levels[3], 0f);
    }

    @Test public void genuinelyDominantBassStillRemainsDominant() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[4] = 100;
        fft[42] = 20;
        fft[426] = 10;
        spectrum.sample(fft, 48000000, 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertTrue(levels[0] > levels[2] * 1.5f);
        assertTrue(levels[0] > levels[4] * 2f);
    }

    @Test public void internalCaptureShowsLowerTrebleInTheFifthBar() {
        for (int hz : new int[] {4000, 4500, 6000, 7500}) {
            IslandSpectrum spectrum = new IslandSpectrum();
            byte[] fft = new byte[PcmSpectrum.SIZE];
            int bin = Math.round(hz * fft.length / 16000f);
            fft[bin * 2] = 60;
            spectrum.sample(fft, 16000000, 0L);
            float[] levels = new float[5];
            spectrum.copyTo(levels);
            assertTrue(levels[4] > .5f);
            for (int band = 0; band < 4; band++) assertEquals(0f, levels[band], 0f);
        }
    }

    @Test public void upperMidRemainsSeparateFromTheTrebleBar() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[PcmSpectrum.SIZE];
        fft[2 * 96] = 60; // 3 kHz at the internal capture rate.
        spectrum.sample(fft, 16000000, 0L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertTrue(levels[3] > 0f);
        assertEquals(0f, levels[4], 0f);
    }

    @Test public void trebleAboveFourteenKilohertzIsNotDiscarded() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        int bin = Math.round(18000f * fft.length / 48000f);
        fft[bin * 2] = 80;
        spectrum.sample(fft, 48000000);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        assertTrue(levels[4] > 0f);
        for (int band = 0; band < 4; band++) assertEquals(0f, levels[band], 0f);
    }


    @Test public void aNewHitJumpsItsBarAndAHeldNoteSettles() {
        IslandSpectrum spectrum = new IslandSpectrum();
        byte[] fft = new byte[1024];
        fft[4] = 100;  // A steady bass line sets the reference.
        fft[42] = 20;  // A quiet held mid.
        for (int i = 0; i <= 20; i++) spectrum.sample(fft, 48000000, i * 33_000_000L);
        float[] levels = new float[5];
        spectrum.copyTo(levels);
        float held = levels[2];
        fft[42] = 60;  // A vocal comes in.
        spectrum.sample(fft, 48000000, 21 * 33_000_000L);
        spectrum.copyTo(levels);
        float hit = levels[2];
        for (int i = 22; i <= 50; i++) spectrum.sample(fft, 48000000, i * 33_000_000L);
        spectrum.copyTo(levels);
        float settled = levels[2];
        assertTrue(hit > settled * 1.15f);
        assertTrue(settled > held);
    }
}
