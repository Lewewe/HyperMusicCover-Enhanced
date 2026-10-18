package com.os4.musiccover;

/**
 * Five real FFT bands shown the way an ear hears them, with one slowly decaying gain reference
 * shared by all five and an actual silence floor.
 *
 * Three things make it move like iPhone's Now Playing bars rather than like a meter:
 * - Unrounded power. The internal capture hands over each bin's power before the Visualizer
 *   byte format rounds it; in bytes, treble 40 dB under the bass is 0 and the fifth bar stayed
 *   down whatever played.
 * - Power per octave. The bands are not equally wide (2.3 octaves of bass, 1 of treble on the
 *   16 kHz capture), so each is compared by its density, not its total: a mix that is even
 *   to the ear - roughly equal power per octave - stands its five bars evenly.
 * - Loudness, not amplitude. A bar is the band's share of the reference raised to 0.6, Stevens'
 *   exponent for loudness against sound pressure: a passage 14 dB quieter is a bar about
 *   40% as tall, as it sounds, not the 20% linear amplitude made of it.
 *
 * - Onsets. iPhone's bars dance: each jumps when something new comes in on its band and
 *   settles back. Each bar is lifted by half of how far its loudness stands above its own
 *   last ~third of a second, so a kick, a snare or a vocal entry kicks its bar, while a held
 *   note sinks to its steady height.
 *
 * The reference stays shared, so a band that is truly weak stays short; nothing is raised to
 * fill the bar and absent treble is not made up - an onset only lifts what is there.
 */
final class IslandSpectrum {
    // The internal 16 kHz capture ends at 8 kHz. Start the final band at 4 kHz so it
    // includes useful treble there; higher-rate Visualizer capture still includes upper highs.
    private static final float[] EDGES = {30f, 150f, 500f, 1600f, 4000f, 20000f};
    private static final float DISPLAY_GAIN = .85f;
    /** Stevens' power law: loudness grows with sound pressure to the 0.6. */
    private static final double LOUDNESS = .6;
    // Even after the per-octave comparison, mixed music still slopes down a little towards the
    // top; a light lift keeps the upper two bars about as lively as they sound.
    private static final float[] BAND_GAIN = {1f, 1f, 1f, 1.15f, 1.3f};
    /** How much of a rise over a band's recent loudness is added on top of it. */
    private static final float ONSET = .5f;
    /** The recent loudness an onset is measured against: about a third of a second. */
    private static final double ONSET_TAU_NS = 350_000_000d;
    private final float[] recent = new float[5];
    private static final double PEAK_RELEASE_NS = 5_000_000_000d;
    /** Integrated amplitude, in byte-format units, below which the capture is silence. */
    private static final float SILENCE = .8f;
    private final float[] energy = new float[5];
    private final float[] levels = new float[5];
    private float peak = 3f;
    private long lastSampleNs = Long.MIN_VALUE;

    synchronized void sample(byte[] fft, int rateMilliHz) {
        sample(fft, rateMilliHz, null, System.nanoTime());
    }

    synchronized void sample(byte[] fft, int rateMilliHz, float[] power) {
        sample(fft, rateMilliHz, power, System.nanoTime());
    }

    synchronized void sample(byte[] fft, int rateMilliHz, long nowNs) {
        sample(fft, rateMilliHz, null, nowNs);
    }

    synchronized void sample(byte[] fft, int rateMilliHz, float[] power, long nowNs) {
        java.util.Arrays.fill(energy, 0f);
        if (fft == null || fft.length < 4 || rateMilliHz <= 0) {
            java.util.Arrays.fill(levels, 0f); return;
        }
        // The unrounded power only when it is the same transform's, bin for bin.
        if (power != null && power.length != fft.length / 2) power = null;
        float binWidth = rateMilliHz / (1000f * fft.length);
        for (int bin = 1; bin < fft.length / 2; bin++) {
            float hz = bin * binWidth;
            for (int band = 0; band < 5; band++) if (hz >= EDGES[band] && hz < EDGES[band + 1]) {
                if (power != null) {
                    energy[band] += power[bin];
                } else {
                    int real = fft[bin * 2], imaginary = fft[bin * 2 + 1];
                    energy[band] += real * real + imaginary * imaginary;
                }
                break;
            }
        }
        float nyquist = rateMilliHz / 2000f;
        float loudest = 0f, highest = 0f;
        for (int band = 0; band < 5; band++) {
            // Integrate each band's power: averaging across its bins would unfairly attenuate
            // the wider bands. Then per octave, over the part of the band the capture reaches.
            float total = (float) Math.sqrt(energy[band]);
            loudest = Math.max(loudest, total);
            float top = Math.min(EDGES[band + 1], nyquist);
            float octaves = top > EDGES[band] ? (float) (Math.log(top / EDGES[band]) / Math.log(2)) : 0f;
            energy[band] = octaves > 0f ? total / (float) Math.sqrt(octaves) * BAND_GAIN[band] : 0f;
            highest = Math.max(highest, energy[band]);
        }
        // Use elapsed time so AOD's lower sample rate does not change the gain response.
        double elapsedNs = lastSampleNs == Long.MIN_VALUE ? 0d : Math.max(0d, (double) nowNs - lastSampleNs);
        lastSampleNs = nowNs;
        peak = Math.max(3f, Math.max(highest, peak * (float) Math.exp(-elapsedNs / PEAK_RELEASE_NS)));
        float follow = (float) (1d - Math.exp(-elapsedNs / ONSET_TAU_NS));
        for (int band = 0; band < 5; band++) {
            if (loudest < SILENCE) {
                levels[band] = 0f;
                recent[band] = 0f;
                continue;
            }
            float loud = (float) Math.pow(energy[band] / peak, LOUDNESS);
            // The first frame has nothing to rise from: it is its own recent loudness.
            float base = elapsedNs == 0d ? loud : recent[band];
            float onset = Math.max(0f, loud - base) * ONSET;
            recent[band] = elapsedNs == 0d ? loud : recent[band] + (loud - recent[band]) * follow;
            levels[band] = Math.min(1f, (loud + onset) * DISPLAY_GAIN);
        }
    }

    synchronized void copyTo(float[] destination) { System.arraycopy(levels, 0, destination, 0, 5); }
    synchronized void reset() {
        java.util.Arrays.fill(levels, 0f);
        java.util.Arrays.fill(recent, 0f);
        peak = 3f;
        lastSampleNs = Long.MIN_VALUE;
    }
}
