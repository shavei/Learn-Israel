package com.shavei.mapa

import com.shavei.mapa.update.Updates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The published version code that "check for updates" compares with the installed one. */
class UpdatesTest {
    @Test
    fun readsThePublishedVersionCode() {
        assertEquals(905_123L, Updates.parseVersion("905123\n"))
        assertNull(Updates.parseVersion(""))
        assertNull(Updates.parseVersion("<html>Not Found</html>"))
        assertNull(Updates.parseVersion("0"))
    }
}
