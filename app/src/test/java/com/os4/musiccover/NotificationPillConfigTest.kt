package com.os4.musiccover

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NotificationPillConfigTest {
    @Test fun ordinaryNotificationModesAndSizesAreBackwardCompatible() {
        val defaults = JSONObject(MiniPlayerConfig.normalizedJson("{}"))
        assertEquals(MiniPlayerConfig.NOTIFICATIONS_CAPSULE, defaults.getInt(MiniPlayerConfig.NOTIFICATION_STYLE))
        assertFalse(defaults.getBoolean(MiniPlayerConfig.ALLOW_BOTTOM_STACK))
        assertEquals(100.0, defaults.getDouble(MiniPlayerConfig.PILL_SIZE), 0.0)
        assertEquals(100.0, defaults.getDouble(MiniPlayerConfig.SHORTCUT_SIZE), 0.0)

        val changed = JSONObject(MiniPlayerConfig.normalizedJson(JSONObject()
            .put(MiniPlayerConfig.NOTIFICATION_STYLE, MiniPlayerConfig.NOTIFICATIONS_STACK)
            .put(MiniPlayerConfig.PILL_SIZE, 90)
            .put(MiniPlayerConfig.SHORTCUT_SIZE, 30).toString()))
        assertEquals(MiniPlayerConfig.NOTIFICATIONS_STACK, changed.getInt(MiniPlayerConfig.NOTIFICATION_STYLE))
        assertTrue(changed.getBoolean(MiniPlayerConfig.ALLOW_BOTTOM_STACK))
        assertEquals(90.0, changed.getDouble(MiniPlayerConfig.PILL_SIZE), 0.0)
        assertEquals(80.0, changed.getDouble(MiniPlayerConfig.SHORTCUT_SIZE), 0.0)
        changed.put(MiniPlayerConfig.NOTIFICATION_STYLE, 99)
        assertEquals(MiniPlayerConfig.NOTIFICATIONS_CAPSULE,
            JSONObject(MiniPlayerConfig.normalizedJson(changed.toString())).getInt(MiniPlayerConfig.NOTIFICATION_STYLE))
    }

    @Test fun disablingStackRestoresTheLastNonStackStyle() {
        for (style in listOf(MiniPlayerConfig.NOTIFICATIONS_CAPSULE, MiniPlayerConfig.NOTIFICATIONS_LIST)) {
            val config = JSONObject(MiniPlayerConfig.normalizedJson(
                JSONObject().put(MiniPlayerConfig.NOTIFICATION_STYLE, style).toString()))
            MiniPlayerConfig.setBottomStack(config, true)
            val stacked = JSONObject(MiniPlayerConfig.normalizedJson(config.toString()))
            assertEquals(MiniPlayerConfig.NOTIFICATIONS_STACK, stacked.getInt(MiniPlayerConfig.NOTIFICATION_STYLE))
            MiniPlayerConfig.setBottomStack(stacked, false)
            val restored = JSONObject(MiniPlayerConfig.normalizedJson(stacked.toString()))
            assertEquals(style, restored.getInt(MiniPlayerConfig.NOTIFICATION_STYLE))
        }
    }

    @Test fun rowSizesOnlyApplyWhenCustomSizingIsActive() {
        val config = JSONObject(MiniPlayerConfig.defaultJson())
            .put(MiniPlayerConfig.PILL_SIZE, 85)
            .put(MiniPlayerConfig.SHORTCUT_SIZE, 95)
        assertEquals(1f, MiniPlayerConfig.rowSizeScale(config, MiniPlayerConfig.PILL_SIZE), 0f)
        config.put(MiniPlayerConfig.CUSTOM_ROW_SIZE, true)
        assertEquals(.85f, MiniPlayerConfig.rowSizeScale(config, MiniPlayerConfig.PILL_SIZE), 0f)
        assertEquals(.95f, MiniPlayerConfig.rowSizeScale(config, MiniPlayerConfig.SHORTCUT_SIZE), 0f)
        config.put(MiniPlayerConfig.COLOROS_FULL_SIZE, true)
        assertEquals(1f, MiniPlayerConfig.rowSizeScale(config, MiniPlayerConfig.PILL_SIZE), 0f)
        assertEquals(1f, MiniPlayerConfig.rowSizeScale(config, MiniPlayerConfig.SHORTCUT_SIZE), 0f)
        config.put(MiniPlayerConfig.PILL_STYLE, MiniPlayerConfig.STYLE_HYPEROS)
        assertEquals(.85f, MiniPlayerConfig.rowSizeScale(config, MiniPlayerConfig.PILL_SIZE), 0f)
    }
    @Test fun coverSceneCompactsNotificationsWithoutANativeSelection() {
        val expanded = MiniPlayerPresentationPolicy.mediaExpanded(
            cardPresent = true, sessionUsable = true, musicInRow = false,
            nativeRequested = false, coverActive = true)
        assertTrue(NotificationPillPolicy.active(true, true, expanded, true, false))
        val collapsed = MiniPlayerPresentationPolicy.mediaExpanded(
            cardPresent = true, sessionUsable = true, musicInRow = true,
            nativeRequested = false, coverActive = true)
        assertFalse(NotificationPillPolicy.active(true, true, collapsed, true, false))
    }

    @Test fun oldFingerprintSettingIsDroppedWithoutLosingBlurPreferences() {
        val old = JSONObject().put("fodLift", true)
            .put(MiniPlayerConfig.BACKGROUND_BLUR, true)
            .put(MiniPlayerConfig.BACKGROUND_BLUR_RADIUS, 42)
        val result = JSONObject(MiniPlayerConfig.normalizedJson(old.toString()))
        assertFalse(result.has("fodLift"))
        assertFalse(result.getBoolean(MiniPlayerConfig.COMPACT_NOTIFICATIONS))
        assertTrue(result.getBoolean(MiniPlayerConfig.BACKGROUND_BLUR))
        assertEquals(42.0, result.getDouble(MiniPlayerConfig.BACKGROUND_BLUR_RADIUS), 0.0)
    }
    @Test fun compactSettingsAreBoundedAndSurviveNormalization() {
        val input = JSONObject().put(MiniPlayerConfig.COMPACT_NOTIFICATIONS, true)
            .put(MiniPlayerConfig.NOTIFICATION_SIZE, 10)
            .put(MiniPlayerConfig.NOTIFICATION_DROP, 90)
        val result = JSONObject(MiniPlayerConfig.normalizedJson(input.toString()))
        assertTrue(result.getBoolean(MiniPlayerConfig.COMPACT_NOTIFICATIONS))
        assertEquals(70.0, result.getDouble(MiniPlayerConfig.NOTIFICATION_SIZE), 0.0)
        assertEquals(32.0, result.getDouble(MiniPlayerConfig.NOTIFICATION_DROP), 0.0)
        assertEquals(80f, MiniPlayerConfig.notificationSize(Double.NaN), 0f)
        assertEquals(16f, MiniPlayerConfig.notificationDrop(Double.POSITIVE_INFINITY), 0f)
        assertEquals(result.toString(), MiniPlayerConfig.normalizedJson(result.toString()))
    }
}
