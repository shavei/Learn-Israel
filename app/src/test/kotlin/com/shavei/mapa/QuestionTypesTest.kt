package com.shavei.mapa

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A returning player gets the Phase 1 question types: name by choice, name by typing, and relate. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class QuestionTypesTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun card(placeId: String, facet: String, stability: Double, dueInDays: Int): CardEntity {
        val now = System.currentTimeMillis()
        return CardEntity(placeId, facet, CardState.REVIEW.value, null, stability, 5.0, now + dueInDays * DAY_MS, now - 10 * DAY_MS, 0)
    }

    @Test
    fun nameChoiceThenTypedThenRelate() {
        val app = ApplicationProvider.getApplicationContext<MapaApplication>()
        runBlocking {
            val dao = app.database.progressDao()
            for (city in listOf("city.tel_aviv", "city.haifa", "city.jerusalem")) dao.upsert(card(city, "LOCATE", 9.0, 5))
            // Due skills, weakest first: Tel Aviv's name (choice), Haifa's name (typed), Jerusalem's relations.
            dao.upsert(card("city.tel_aviv", "NAME", 1.0, -1))
            dao.upsert(card("city.haifa", "NAME", 5.0, -1))
            dao.upsert(card("city.jerusalem", "RELATE", 8.0, -1))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()

            waitForText("מה שם המקום המסומן?")
            Screenshots.save(scenario, "p1-name-choice")
            compose.onNodeWithText("תל אביב-יפו").performClick()
            waitForText("מצוין!")
            compose.onNodeWithText("המשך").performClick()

            waitForText("כתבו את שם המקום המסומן")
            compose.onNodeWithTag("name-input").performTextInput("חיפה")
            Screenshots.save(scenario, "p1-name-typed")
            compose.onNodeWithText("בדיקה").performClick()
            waitForText("מצוין!")
            compose.onNodeWithText("המשך").performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("יותר", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            Screenshots.save(scenario, "p1-relate")
            compose.onNodeWithText("ירושלים").performClick()
            compose.waitUntil(10_000) {
                listOf("מצוין!", "לא הפעם").any { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }
            }
            Screenshots.save(scenario, "p1-relate-feedback")
        }
    }

    @Test
    fun namingARegionOffersRegionNames() {
        val app = ApplicationProvider.getApplicationContext<MapaApplication>()
        runBlocking {
            val dao = app.database.progressDao()
            dao.upsert(card("region.sharon", "LOCATE", 9.0, 5))
            dao.upsert(card("region.sharon", "NAME", 1.0, -1))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()

            waitForText("מה שם המקום המסומן?")
            // Wrong options are other regions, never cities.
            waitForText("השרון")
            assertTrue(compose.onAllNodes(hasText("נתניה")).fetchSemanticsNodes().isEmpty())
            Screenshots.save(scenario, "p1b-name-region")
            compose.onNodeWithText("השרון").performClick()
            waitForText("מצוין!")
            Screenshots.save(scenario, "p1b-region-feedback")
        }
    }
}
