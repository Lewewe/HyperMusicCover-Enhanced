package com.os4.musiccover

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerAutoEntryTest {
    @Test fun existingSettingsKeepAutomaticEntry() {
        assertFalse(MiniPlayerConfig.mediaCollapsedByDefault(null))
        assertFalse(MiniPlayerConfig.mediaCollapsedByDefault("""{"enabled":true}"""))
        assertFalse(MiniPlayerConfig.mediaCollapsedByDefault("invalid"))
    }

    @Test fun collapsedEntryRequiresAnEnabledIsland() {
        assertFalse(MiniPlayerConfig.mediaCollapsedByDefault(
            """{"enabled":false,"mediaCollapsedDefault":true}"""))
        assertTrue(MiniPlayerConfig.mediaCollapsedByDefault(
            """{"enabled":true,"mediaCollapsedDefault":true}"""))
    }

    @Test fun settingSurvivesNormalizationWithExistingPillPreferences() {
        val normalized = JSONObject(MiniPlayerConfig.normalizedJson(
            """{"enabled":true,"mediaCollapsedDefault":true,"backgroundBlur":true,"notificationDropDp":24}"""))
        assertTrue(normalized.getBoolean(MiniPlayerConfig.MEDIA_COLLAPSED_DEFAULT))
        assertTrue(normalized.getBoolean(MiniPlayerConfig.BACKGROUND_BLUR))
        org.junit.Assert.assertEquals(24.0, normalized.getDouble(MiniPlayerConfig.NOTIFICATION_DROP), 0.0)
    }

    @Test fun listWithoutPlayerDefaultsOffAndSurvives() {
        assertFalse(JSONObject(MiniPlayerConfig.defaultJson()).getBoolean(MiniPlayerConfig.LIST_WITHOUT_PLAYER))
        assertTrue(JSONObject(MiniPlayerConfig.normalizedJson("""{"listWithoutPlayer":true}"""))
            .getBoolean(MiniPlayerConfig.LIST_WITHOUT_PLAYER))
    }
}
