package com.os4.musiccover;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkFetchPolicyTest {
    @Test public void kugouArtworkUsesHttpsWithTheSamePathAndQuery() {
        String path = "imge.kugou.com/stdmusic/400/20260819/cover.jpg?size=400";
        assertEquals("https://" + path, ArtworkFetchPolicy.networkUrl("http://" + path));
        assertEquals("https://" + path, ArtworkFetchPolicy.networkUrl("https://" + path));
    }

    @Test public void unrelatedHostsAndLocalProvidersRemainUnchanged() {
        for (String url : new String[]{"http://example.com/cover.jpg",
                "http://imge.kugou.com.example.com/cover.jpg", "content://player/art/1"}) {
            assertEquals(url, ArtworkFetchPolicy.networkUrl(url));
        }
    }

    @Test public void failedReadDoesNotKeepEveryMetadataUpdateWaiting() {
        ArtworkFetchPolicy policy = new ArtworkFetchPolicy();
        policy.failed("cover-a", 100);
        assertTrue(policy.coolingDown("cover-a", 101));
        assertTrue(policy.coolingDown("cover-a", 10_099));
        assertFalse(policy.coolingDown("cover-b", 101));
        assertFalse(policy.coolingDown("cover-a", 10_100));
        policy.failed("cover-a", 10_101);
        policy.succeeded("cover-a");
        assertFalse(policy.coolingDown("cover-a", 10_102));
    }
}
