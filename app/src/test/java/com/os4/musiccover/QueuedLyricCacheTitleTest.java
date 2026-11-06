package com.os4.musiccover;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class QueuedLyricCacheTitleTest {
    private static QueuedLyricCache.Entry stored(String title) {
        return new QueuedLyricCache.Entry(Collections.<LyricLine>emptyList(), 0, false, title);
    }

    @Test public void anotherTitleUnderTheSameIdIsAnotherSong() {
        assertFalse(stored("Song A").sameSong("Song B"));
        assertTrue(stored("Song A").sameSong("Song A"));
    }

    @Test public void anUnknownTitleOnEitherSideStillMatches() {
        assertTrue(stored(null).sameSong("Song A"));
        assertTrue(stored("Song A").sameSong(null));
    }
}
