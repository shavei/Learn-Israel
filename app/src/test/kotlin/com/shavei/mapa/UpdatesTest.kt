package com.shavei.mapa

import com.shavei.mapa.update.DownloadProgress
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

    @Test
    fun showsHowFarTheDownloadIs() {
        assertEquals(42, DownloadProgress(42, 100, 5_000).percent)
        assertEquals(100, DownloadProgress(100, 100, 5_000).percent)
        assertNull(DownloadProgress(42, 0, 5_000).percent)
    }

    @Test
    fun guessesTheTimeLeftFromTheAverageSpeed() {
        // A quarter in 10 seconds: 30 seconds to go.
        assertEquals(30L, DownloadProgress(25, 100, 10_000).secondsLeft)
        // Rounded up to 5 seconds, then to whole minutes.
        assertEquals(5L, DownloadProgress(99, 100, 10_000).secondsLeft)
        assertEquals(180L, DownloadProgress(10, 100, 15_000).secondsLeft)
        // Too early, or no size to go by.
        assertNull(DownloadProgress(10, 100, 500).secondsLeft)
        assertNull(DownloadProgress(10, 0, 15_000).secondsLeft)
    }
}
