package com.os4.musiccover;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The album and length of a Spotify track, read from its public page.
 *
 * Spotify's queue entries carry a title, the artists and the track id and nothing else, while a
 * by-name lyric search leans on the album and the length: they rank the candidates, and with
 * them a search that came back noisy is asked again and an album's track list is tried. So a
 * queued song is searched with less than the same song once it plays, and some only matched
 * then. The page's og:description reads "artist · album · Song · year" and music:duration is
 * the length in seconds.
 */
final class SpotifyTrackPage {
    private SpotifyTrackPage() {}

    private static final Pattern DESCRIPTION =
            Pattern.compile("<meta property=\"og:description\" content=\"([^\"]*)\"");
    private static final Pattern DURATION =
            Pattern.compile("<meta name=\"music:duration\" content=\"(\\d+)\"");

    static final class Info {
        final String album;
        final long durationMs;
        Info(String album, long durationMs) { this.album = album; this.durationMs = durationMs; }
    }

    /** Null when the page could not be read; never thrown. */
    static Info fetch(String trackId) {
        if (trackId == null) return null;
        Http.Reply reply = Http.get("https://open.spotify.com/track/" + trackId, "MCSpotifyPage");
        return reply.ok() ? parse(reply.body) : null;
    }

    static Info parse(String html) {
        if (html == null) return null;
        String album = null;
        Matcher d = DESCRIPTION.matcher(html);
        if (d.find()) {
            String[] parts = unescape(d.group(1)).split(" · ");
            // From the end: an artist list is the one field that could hold the separator.
            if (parts.length >= 4) album = parts[parts.length - 3].trim();
        }
        long duration = 0L;
        Matcher m = DURATION.matcher(html);
        if (m.find()) {
            try { duration = Long.parseLong(m.group(1)) * 1000L; } catch (NumberFormatException ignored) { }
        }
        return album == null && duration == 0L ? null
                : new Info(album == null || album.isEmpty() ? null : album, duration);
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'")
                .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }
}
