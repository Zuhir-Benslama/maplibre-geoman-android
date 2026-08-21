package com.geoman.maplibre.geoman.core.options

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StyleThemesTest {

    @Test
    fun `light theme matches library defaults`() {
        val resolved = StyleThemes.resolve(StyleTheme.LIGHT)

        assertEquals(LayerStyles(), resolved)
    }

    @Test
    fun `dark theme differs from light on every shape`() {
        val light = StyleThemes.resolve(StyleTheme.LIGHT)
        val dark = StyleThemes.resolve(StyleTheme.DARK)

        assertNotEquals(light.marker.color, dark.marker.color)
        assertNotEquals(light.line.color, dark.line.color)
        assertNotEquals(light.polygon.color, dark.polygon.color)
        assertNotEquals(light.circle.color, dark.circle.color)
        assertNotEquals(light.rectangle.color, dark.rectangle.color)
        assertNotEquals(light.circleMarker.fillColor, dark.circleMarker.fillColor)
    }

    @Test
    fun `applyTheme replaces layer styles`() {
        val options = GmOptions()

        options.applyTheme(StyleTheme.DARK)

        assertEquals(StyleThemes.dark, options.data.layerStyles)

        options.applyTheme(StyleTheme.LIGHT)

        assertEquals(StyleThemes.light, options.data.layerStyles)
    }

    @Test
    fun `applyTheme leaves other option groups untouched`() {
        val options = GmOptions(GmOptionsData(settings = SettingsOptions(enableSnap = false)))

        options.applyTheme(StyleTheme.DARK)

        assertEquals(false, options.data.settings.enableSnap)
    }
}
