package com.os4.musiccover;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BpmEstimatorTest {
    @Test
    public void tempoCombPrefersBeatOverHalfTime() {
        float rate = 20f;
        float[] onset = new float[360];
        for (int i = 0; i < onset.length; i++) {
            if (i % 7 == 0 || i % 14 == 0) onset[i] = 1f;
        }

        assertEquals(171, BpmEstimator.estimateTempo(onset, rate), 3);
    }

    @Test
    public void quietOrFlatSignalDoesNotInventTempo() {
        assertEquals(0, BpmEstimator.estimateTempo(new float[360], 20f));
    }

    @Test
    public void halfTimeCandidateCanResolveToOneHundredThirtyBpm() {
        float rate = 20f;
        float[] onset = new float[360];
        int beatSpacing = Math.round(rate * 60f / 130f);
        for (int i = 0; i < onset.length; i++) {
            if (i % beatSpacing == 0) onset[i] = 1f;
        }

        assertEquals(130, BpmEstimator.estimateTempo(onset, rate), 4);
    }

    @Test
    public void tempoSupportCoversTheFullCandidateRange() {
        float rate = 20f;
        float[] onset = new float[360];
        int beatSpacing = Math.round(rate * 60f / 190f);
        for (int i = 0; i < onset.length; i++) {
            if (i % beatSpacing == 0) onset[i] = 1f;
        }

        assertEquals(190, BpmEstimator.estimateTempo(onset, rate), 5);
    }

    @Test
    public void upperCandidateRangeDoesNotCrash() {
        float rate = 20f;
        float[] onset = new float[360];
        int beatSpacing = Math.round(rate * 60f / 220f);
        for (int i = 0; i < onset.length; i++) {
            if (i % beatSpacing == 0) onset[i] = 1f;
        }

        int bpm = BpmEstimator.estimateTempo(onset, rate);
        assertTrue(bpm >= 40 && bpm <= 220);
    }

    @Test
    public void strongSlowBeatWinsAgainstWeakDoubleTimeSubdivisions() {
        float rate = 20f;
        float[] onset = new float[360];
        int beatSpacing = Math.round(rate * 60f / 99f);
        for (int i = 0; i < onset.length; i++) {
            if (i % beatSpacing == 0) onset[i] = 1f;
            else if (i % Math.max(1, beatSpacing / 2) == 0) onset[i] = .25f;
        }

        assertEquals(99, BpmEstimator.estimateTempo(onset, rate), 8);
    }

    @Test
    public void beatGateDropsWeakHitsAndFastRolls() {
        // 97 BPM: a beat is ~619 ms, so hits closer than ~278 ms are a roll.
        assertTrue(BpmEstimator.showsBeat(1f, .1f, 1f, 300L, 97));
        assertFalse(BpmEstimator.showsBeat(.4f, .1f, 1f, 300L, 97));
        assertFalse(BpmEstimator.showsBeat(1f, .1f, 1f, 200L, 97));
        assertFalse(BpmEstimator.showsBeat(1f, .1f, 1f, 200L, 0));
        assertFalse(BpmEstimator.showsBeat(.05f, .1f, .05f, 600L, 97));
    }

    @Test
    public void quickHitsBounceInsteadOfTurning() {
        // 97 BPM: 3/4 of a beat is ~464 ms.
        assertTrue(BpmEstimator.flips(470L, 97));
        assertFalse(BpmEstimator.flips(300L, 97));
        assertFalse(BpmEstimator.flips(300L, 0));
        assertTrue(BpmEstimator.flips(400L, 0));
    }
}
