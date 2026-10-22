package com.shavei.mapa

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.update.DownloadProgress
import com.shavei.mapa.update.UpdateDownload
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** While the update downloads, settings and the notification show how far it is and how long is left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateSectionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun text(done: Long, size: Long, elapsedMs: Long) = UpdateDownload.describe(context, DownloadProgress(done, size, elapsedMs))

    @Test
    fun showsThePercentAndTheTimeLeft() {
        assertEquals("25% · נותרו כ־30 שניות", text(25, 100, 10_000))
        assertEquals("10% · נותרו כ־3 דקות", text(10, 100, 15_000))
        assertEquals("10% · מחשבים כמה זמן נותר…", text(10, 100, 500))
        assertEquals("הורדו 2.5 MB", text(2_500_000, 0, 5_000))
    }
}
