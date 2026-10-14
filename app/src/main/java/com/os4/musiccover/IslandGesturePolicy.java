package com.os4.musiccover;

/** Expanded content and unknown OEM states keep their own controls. */
public final class IslandGesturePolicy {
    /**
     * Concentric with the Xiaomi 17 Pro's screen corners, as iPhone's expanded island is: a
     * 190 px display corner less the card's even 29 px margin, at 3.25 px per dp.
     */
    public static final int DEFAULT_RADIUS_DP = 50;
    public static final int MIN_RADIUS_DP = 12;
    public static final int MAX_RADIUS_DP = 64;

    private IslandGesturePolicy() {
    }

    static boolean remap(boolean enabled, String state) {
        return enabled && ("BigIsland".equals(state) || "SmallIsland".equals(state)
                || "ShowOnceBigIsland".equals(state));
    }

    public static int radius(int dp) {
        return Math.max(MIN_RADIUS_DP, Math.min(MAX_RADIUS_DP, dp));
    }

    /** How far in from the bottom edge a corner of [radius] reaches, [x] in from the side. */
    static float cornerReach(float radius, float x) {
        if (x >= radius) return 0f;
        float dx = radius - Math.max(0f, x);
        return radius - (float) Math.sqrt(radius * radius - dx * dx);
    }

    /**
     * Upward shift that keeps a box ending at [bottom] inside both bottom corners of a card
     * [width] by [height]; zero when the box already clears them.
     */
    static float bottomCornerLift(float radius, float width, float height, float left, float right,
                                  float bottom) {
        float reach = Math.max(cornerReach(radius, left), cornerReach(radius, width - right));
        return Math.max(0f, bottom - (height - reach));
    }

    /** How far a row starting at [contentTop] must move down to sit [gap] below the lens. */
    static float cameraPadding(float lensBottom, float contentTop, float gap) {
        return Math.max(0f, lensBottom + gap - contentTop);
    }

    /**
     * Clip radius while the island morphs between pill and expanded card. A radius at least the
     * pill's half height simply caps the native min(height / 2, radius); a smaller one eases in
     * from the pill so the compact island never loses its round ends.
     */
    static float clipRadius(float height, float pillHeight, float expandedHeight, float radius) {
        float half = height / 2f;
        float pillHalf = pillHeight / 2f;
        if (radius >= pillHalf) return Math.min(half, radius);
        float span = expandedHeight - pillHeight;
        float p = span <= 0f ? 1f : Math.max(0f, Math.min(1f, (height - pillHeight) / span));
        return Math.min(half, pillHalf + (radius - pillHalf) * p);
    }
}
