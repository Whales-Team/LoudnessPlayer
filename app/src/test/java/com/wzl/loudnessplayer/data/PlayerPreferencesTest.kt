package com.wzl.loudnessplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPreferencesTest {
    @Test
    fun exposesBrownAsAPersistableThemeChoice() {
        assertEquals(AppTheme.BROWN, AppTheme.valueOf("BROWN"))
        assertEquals("棕色", AppTheme.BROWN.displayName)
    }
}
