package com.os4.musiccover;

import java.net.URI;
import java.util.LinkedHashMap;

/** Secure known artwork hosts and bound retries after an unavailable image. */
final class ArtworkFetchPolicy {
    private static final long RETRY_MS = 10_000L;
    private static final int MAX_FAILURES = 32;
    private final LinkedHashMap<String, Long> failures = new LinkedHashMap<>();

    static String networkUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if ("http".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("kugou.com")
                        || host.toLowerCase(java.util.Locale.ROOT).endsWith(".kugou.com"))) {
                return "https" + value.substring(value.indexOf(':'));
            }
        } catch (IllegalArgumentException ignored) { }
        return value;
    }

    boolean coolingDown(String key, long now) {
        Long failedAt = failures.get(key);
        if (failedAt == null) return false;
        if (now - failedAt < RETRY_MS) return true;
        failures.remove(key);
        return false;
    }

    void failed(String key, long now) {
        failures.remove(key);
        failures.put(key, now);
        while (failures.size() > MAX_FAILURES) {
            failures.remove(failures.keySet().iterator().next());
        }
    }

    void succeeded(String key) {
        failures.remove(key);
    }
}
