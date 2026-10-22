package com.shavei.mapa

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.shavei.mapa.ui.settings.UpdateSection
import com.shavei.mapa.update.DownloadProgress
import com.shavei.mapa.update.UpdateDownload
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** While the update downloads, settings shows how far it is and how long is left. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateSectionTest {
    @get:Rule val rule = createComposeRule()

    @After
    fun idle() = UpdateDownload.set(UpdateDownload.State.Idle)

    @Test
    fun showsThePercentAndTheTimeLeft() {
        UpdateDownload.set(UpdateDownload.State.Running(DownloadProgress(25, 100, 10_000)))
        rule.setContent { UpdateSection() }
        rule.onNodeWithTag("update-progress").assertTextEquals("25% · נותרו כ־30 שניות")

        UpdateDownload.set(UpdateDownload.State.Running(DownloadProgress(10, 100, 15_000)))
        rule.onNodeWithTag("update-progress").assertTextEquals("10% · נותרו כ־3 דקות")

        UpdateDownload.set(UpdateDownload.State.Running(DownloadProgress(10, 100, 500)))
        rule.onNodeWithTag("update-progress").assertTextEquals("10% · מחשבים כמה זמן נותר…")
    }
}
