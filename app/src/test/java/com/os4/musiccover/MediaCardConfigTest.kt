package com.os4.musiccover

import org.junit.Assert.*
import org.junit.Test

class MediaCardConfigTest {
    @Test fun bluetoothSyncAdjustmentKeepsItsSignAndBounds() {
        val defaults = MediaCardConfig.parse(null)
        assertEquals(1, defaults.getValue("island.btSync"))
        assertEquals(0, defaults.getValue("island.btOffset"))
        val changed = defaults + ("island.btOffset" to -100)
        assertEquals(changed, MediaCardConfig.parse(MediaCardConfig.encode(changed)))
        assertEquals(-250, MediaCardConfig.limit("btOffset", -999))
        assertEquals(250, MediaCardConfig.limit("btOffset", 999))
    }
    @Test fun waveColorDefaultsToTheArtworkGradientAndKeepsTheCustomColour() {
        val defaults = MediaCardConfig.parse(null)
        assertEquals(MediaCardConfig.WAVE_GRADIENT, defaults.getValue("island.waveColor"))
        assertEquals(1 to 2, MediaCardConfig.waveColors(MediaCardConfig.WAVE_GRADIENT, 0, 1, 2))
        assertEquals(-1 to -1, MediaCardConfig.waveColors(MediaCardConfig.WAVE_WHITE, 0, 1, 2))
        val dark = MediaCardConfig.WAVE_DARK_COLOR
        assertEquals(dark to dark, MediaCardConfig.waveColors(MediaCardConfig.WAVE_DARK, 0, 1, 2))
        val red = 0xFFFF0000.toInt()
        assertEquals(red to red, MediaCardConfig.waveColors(MediaCardConfig.WAVE_CUSTOM, red, 1, 2))
        assertEquals(0x40FF0000, MediaCardConfig.limit("waveCustom", 0x40FF0000))
        assertEquals(3, MediaCardConfig.limit("waveColor", 9))
        val changed = defaults + ("island.waveColor" to 3) + ("island.waveCustom" to red)
        assertEquals(changed, MediaCardConfig.parse(MediaCardConfig.encode(changed)))
    }
    @Test fun independentProfilesRoundTripWithoutChangingNativeDefaults() {
        val defaults = MediaCardConfig.parse(null)
        assertEquals(0, defaults.getValue("notification.background"))
        assertEquals(0, defaults.getValue("island.flow"))
        val changed = defaults + ("notification.cover" to 2) + ("island.background" to 5)
        assertEquals(changed, MediaCardConfig.parse(MediaCardConfig.encode(changed)))
        assertEquals(0, changed.getValue("island.cover"))
        assertEquals(0, changed.getValue("notification.background"))
    }
    @Test fun expandedIslandDefaultsToOledAndCanBeGlass() {
        val oled = MediaCardConfig.ISLAND_THEME_OLED
        val native = MediaCardConfig.ISLAND_THEME_DEFAULT
        assertEquals(native, MediaCardConfig.parse(null).getValue("island.theme"))
        // What older versions saved: dark stays black, the rest are HyperOS's own.
        assertEquals(native, MediaCardConfig.parse("""{"island.theme":0}""").getValue("island.theme"))
        assertEquals(oled, MediaCardConfig.parse("""{"island.theme":2}""").getValue("island.theme"))
        assertEquals(native, MediaCardConfig.parse("""{"island.theme":1}""").getValue("island.theme"))
        val saved = MediaCardConfig.parse(null) + ("island.theme" to oled)
        assertEquals(saved, MediaCardConfig.parse(MediaCardConfig.encode(saved)))
        // The notification keeps its three themes.
        assertEquals(2, MediaCardConfig.parse("""{"notification.theme":2}""").getValue("notification.theme"))
    }
    @Test fun malformedAndOutOfRangeSettingsAreBounded() {
        assertEquals(MediaCardConfig.parse(null), MediaCardConfig.parse("not json"))
        val values = MediaCardConfig.parse("""{"notification.blur":100,"island.cover":-9,"island.background":7,"notification.hideSource":2,"unknown":99}""")
        assertEquals(20, values.getValue("notification.blur"))
        assertEquals(0, values.getValue("island.cover"))
        assertEquals(5, values.getValue("island.background"))
        assertEquals(1, values.getValue("notification.hideSource"))
        assertFalse(values.containsKey("unknown"))
    }
    @Test fun hiddenAndScreenOffCardsNeverAnimate() {
        assertTrue(MediaCardConfig.animate(true, true, true, true))
        assertFalse(MediaCardConfig.animate(true, true, false, true))
        assertFalse(MediaCardConfig.animate(true, true, true, false))
        assertFalse(MediaCardConfig.animate(false, true, true, true))
        assertTrue(MediaCardConfig.animate(false, false, true, true))
        assertFalse(MediaCardConfig.animate(false, false, true, false))
    }
    @Test fun nativeAndDisabledFlowDoNotCreateAnOverlay() {
        assertFalse(MediaCardConfig.customBackground(0, 0))
        assertFalse(MediaCardConfig.customBackground(0, 4))
        for (flow in 1..3) assertTrue(MediaCardConfig.customBackground(0, flow))
        for (style in 1..5) assertTrue(MediaCardConfig.customBackground(style, 0))
    }
    @Test fun outputWavesHaveIndependentLocationsAndDefaultToOff() {
        val defaults = MediaCardConfig.parse(null)
        assertEquals("notification.lockOutputWave", MediaCardConfig.outputWaveKey("notification", true))
        assertEquals("notification.outputWave", MediaCardConfig.outputWaveKey("notification", false))
        assertEquals("island.outputWave", MediaCardConfig.outputWaveKey("island", true))
        assertEquals("island.outputWave", MediaCardConfig.outputWaveKey("island", false))
        assertEquals(0, defaults.getValue("notification.lockOutputWave"))
        assertEquals(0, defaults.getValue("notification.outputWave"))
        assertEquals(0, defaults.getValue("island.outputWave"))
        val changed = defaults + ("notification.lockOutputWave" to 1)
        assertEquals(changed, MediaCardConfig.parse(MediaCardConfig.encode(changed)))
        assertEquals(0, changed.getValue("notification.outputWave"))
        assertEquals(0, changed.getValue("island.outputWave"))
    }

}
