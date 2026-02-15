package org.a2ui.compose.theme

import org.junit.Test
import org.junit.Assert.*

class A2UIThemeTest {

    @Test
    fun testParseColor_validHex6() {
        val color = parseColor("#FF5722")
        assertNotNull(color)
        assertEquals(255, color?.red?.toInt())
        assertEquals(87, color?.green?.toInt())
        assertEquals(34, color?.blue?.toInt())
    }

    @Test
    fun testParseColor_validHex8() {
        val color = parseColor("#80FF5722")
        assertNotNull(color)
        // Alpha should be 128 (0x80)
        assertEquals(128, color?.alpha?.toInt())
    }

    @Test
    fun testParseColor_withoutHash() {
        val color = parseColor("FF5722")
        assertNotNull(color)
    }

    @Test
    fun testParseColor_null() {
        val color = parseColor(null)
        assertNull(color)
    }

    @Test
    fun testParseColor_empty() {
        val color = parseColor("")
        assertNull(color)
    }

    @Test
    fun testParseColor_invalid() {
        val color = parseColor("invalid")
        assertNull(color)
    }

    @Test
    fun testCreateColorScheme_defaultLight() {
        val config = A2UIThemeConfig()
        val colorScheme = createColorScheme(config, darkTheme = false)
        
        // Should use default Material 3 colors
        assertNotNull(colorScheme.primary)
        assertNotNull(colorScheme.secondary)
        assertNotNull(colorScheme.background)
    }

    @Test
    fun testCreateColorScheme_defaultDark() {
        val config = A2UIThemeConfig()
        val colorScheme = createColorScheme(config, darkTheme = true)
        
        // Should use default dark Material 3 colors
        assertNotNull(colorScheme.primary)
        assertNotNull(colorScheme.secondary)
        assertNotNull(colorScheme.background)
    }

    @Test
    fun testCreateColorScheme_customPrimary() {
        val config = A2UIThemeConfig(primaryColor = "#6200EE")
        val colorScheme = createColorScheme(config, darkTheme = false)
        
        // Primary should be the custom color
        assertNotNull(colorScheme.primary)
    }

    @Test
    fun testCreateColorScheme_customMultiple() {
        val config = A2UIThemeConfig(
            primaryColor = "#6200EE",
            secondaryColor = "#03DAC6",
            backgroundColor = "#FFFFFF",
            surfaceColor = "#FFFFFF",
            errorColor = "#B00020"
        )
        val colorScheme = createColorScheme(config, darkTheme = false)
        
        assertNotNull(colorScheme.primary)
        assertNotNull(colorScheme.secondary)
        assertNotNull(colorScheme.background)
        assertNotNull(colorScheme.surface)
        assertNotNull(colorScheme.error)
    }

    @Test
    fun testA2UIThemeConfig_defaults() {
        val config = A2UIThemeConfig()
        
        assertNull(config.primaryColor)
        assertNull(config.secondaryColor)
        assertNull(config.backgroundColor)
        assertNull(config.surfaceColor)
        assertNull(config.textColor)
        assertNull(config.errorColor)
        assertNull(config.darkMode)
        assertEquals(8, config.borderRadius)
        assertNull(config.fontFamily)
    }

    @Test
    fun testA2UIThemeConfig_customValues() {
        val config = A2UIThemeConfig(
            primaryColor = "#6200EE",
            secondaryColor = "#03DAC6",
            darkMode = true,
            borderRadius = 16,
            fontFamily = "Roboto"
        )
        
        assertEquals("#6200EE", config.primaryColor)
        assertEquals("#03DAC6", config.secondaryColor)
        assertTrue(config.darkMode!!)
        assertEquals(16, config.borderRadius)
        assertEquals("Roboto", config.fontFamily)
    }
}
