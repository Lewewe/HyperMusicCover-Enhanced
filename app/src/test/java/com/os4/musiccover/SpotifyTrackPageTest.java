package com.os4.musiccover;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SpotifyTrackPageTest {
    @Test
    public void readsAlbumAndLengthFromThePage() {
        SpotifyTrackPage.Info info = SpotifyTrackPage.parse(
                "<meta property=\"og:description\" content=\"Connor Kauffman · Never Worth Saving · Song · 2022\"/>"
                        + "<meta name=\"music:duration\" content=\"145\"/>");
        assertEquals("Never Worth Saving", info.album);
        assertEquals(145_000L, info.durationMs);
    }

    @Test
    public void keepsAnAlbumWithEscapesAndSeveralArtists() {
        SpotifyTrackPage.Info info = SpotifyTrackPage.parse(
                "<meta property=\"og:description\" content=\"vally.exe, Miyolophone · Rock &amp; Roll · Song · 2024\"/>");
        assertEquals("Rock & Roll", info.album);
        assertEquals(0L, info.durationMs);
    }

    @Test
    public void nothingUsefulIsNull() {
        assertNull(SpotifyTrackPage.parse("<html></html>"));
        assertNull(SpotifyTrackPage.parse(null));
    }
}
