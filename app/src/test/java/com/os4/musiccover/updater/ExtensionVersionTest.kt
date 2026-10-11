package com.os4.musiccover.updater
import org.junit.Assert.*
import org.junit.Test
class ExtensionVersionTest {
    @Test fun installedAndDownloadedVersionsAreNotOfferedAgain() {
        assertFalse(ExtensionVersion.isNewer("0.1.0-beta", "0.1.0-beta"))
        assertFalse(ExtensionVersion.isNewer("0.1.0-beta", "0.2.0"))
        assertTrue(ExtensionVersion.isNewer("0.1.0-beta", null))
    }
    @Test fun betaAndStableUpdatesAreOrderedNumerically() {
        assertTrue(ExtensionVersion.isNewer("0.1.0-beta.10", "0.1.0-beta.2"))
        assertTrue(ExtensionVersion.isNewer("0.1.0", "0.1.0-beta"))
        assertFalse(ExtensionVersion.isNewer("0.1.0-beta", "0.1.0"))
        assertTrue(ExtensionVersion.isNewer("0.10.0-beta", "0.9.0"))
    }
}
