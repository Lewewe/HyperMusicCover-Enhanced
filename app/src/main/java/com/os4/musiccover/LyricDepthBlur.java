package com.os4.musiccover;

/** Relative strength of the existing distance-based lyric blur; 100 preserves its radii. */
final class LyricDepthBlur {
    static final int DEFAULT = 100;
    static final int MAX = 200;

    static int clamp(int percent) { return Math.max(0, Math.min(MAX, percent)); }

    static float radiusDp(int rows, int percent) {
        if (rows <= 0) return 0f;
        return (0.9f + 1.6f * (Math.min(4, rows) - 1)) * clamp(percent) / 100f;
    }
}
