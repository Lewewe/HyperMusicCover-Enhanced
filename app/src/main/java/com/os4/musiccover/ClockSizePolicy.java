package com.os4.musiccover;

/** Native clock measurements belong to one editor geometry, not just one renderer class. */
final class ClockSizePolicy {
    private ClockSizePolicy() {}

    static String editorKey(String template, int style, double height, double width,
                            int font, int weight, boolean doubleRow) {
        return template + "/" + style + "/" + height + "/" + width + "/" + font
                + "/" + weight + "/" + doubleRow;
    }

    static String measurementKey(String renderer, String geometry, int width, int height, int dpi) {
        return renderer + "#geometry-v1/" + geometry + "/" + width + "/" + height + "/" + dpi;
    }

    static boolean retainLargest(String savedKey, String currentKey, float saved, float measured) {
        return currentKey.equals(savedKey) && Float.isFinite(saved) && saved > 0f
                && measured < saved + 0.5f;
    }

    static float nativeUnit(float full, float live, boolean notificationCompact) {
        return notificationCompact && Float.isFinite(live) && live > 0f
                ? Math.min(full, live) : full;
    }

    static float coverUnit(float requested, float reference, float nativeUnit, float artworkScale,
                           float minimumFraction, float nativeProgress) {
        float compact = Math.max(minimumFraction * reference,
                Math.min(reference, requested * artworkScale));
        return compact + (nativeUnit - compact) * nativeProgress;
    }

    /**
     * How wide a time of one row is against its digit height on all_in_one, the face the clock
     * size is read against: "07:30" in its small-clock font, about 3.2 digit heights.
     */
    static final float ROW_ASPECT = 3.2f;

    /**
     * The cover unit for a face whose digits are already shorter than the size asked for, read
     * against all_in_one: the height alone would leave it at its own full size. The eastern B
     * face sets 136px digits 929px apart, so it stayed full width over the cover. Such a face is
     * held to the width an all_in_one clock of the requested size takes instead; a face narrow
     * enough to fit keeps its own unit.
     */
    static float wideFaceUnit(float requested, float unit, float width) {
        if (!(requested > 0f) || !(unit > 0f) || !(width > 0f)) return unit;
        return Math.min(unit, unit * requested * ROW_ASPECT / width);
    }

    static float transitionUnit(float drawnUnit, float destination, float progress) {
        return drawnUnit + (destination - drawnUnit) * Math.max(0f, Math.min(1f, progress));
    }
}
