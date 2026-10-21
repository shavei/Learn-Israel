package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.MapTestEntity
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.Grade
import com.shavei.mapa.learning.MapTest
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.StudyCard
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.learning.fsrs.FsrsCard
import com.shavei.mapa.learning.fsrs.Rating
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Phase 1d: the pre/post map test and the local learning stats. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class MeasurementTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()
    private val dao get() = app.database.progressDao()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** Taps the middle of the map for every question until the summary shows. */
    private fun answerAllTwenty() {
        for (n in 1..MapTest.ITEMS.size) {
            waitForText("מבחן מפה · שאלה $n מתוך 20")
            compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.5f, height * 0.55f)) }
        }
        waitForText("סיום מבחן המפה")
    }

    private fun review(placeId: String, timestamp: Long, mode: String = "LOCATE", rating: Int = 3, session: String = "s") = ReviewEntity(
        placeId = placeId, facet = "LOCATE", mode = mode, sessionId = session, timestamp = timestamp, rating = rating,
        errorMeters = 1000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3000, appVersion = "test",
    )

    @Test
    fun preTestAtOnboardingMeasuresWithoutTeaching() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("מבחן מפה קצר לפני שמתחילים")
            compose.onNodeWithText("מבחן מפה קצר לפני שמתחילים").performClick()
            waitForText("סמנו על המפה: חיפה")
            Screenshots.save(scenario, "d1-map-test-ask")
            answerAllTwenty()
            // No feedback came between questions, and the summary promises a second test.
            compose.onNodeWithText("אחרי חודש של אימון נציע את אותו מבחן שוב, ותראו כמה למדתם.").assertExists()
            Screenshots.save(scenario, "d2-map-test-summary")

            // Back on the welcome screen: the test is not onboarding, the choice is still to make.
            // The button says where it leads.
            compose.onNodeWithText("חזרה למסך הפתיחה").performClick()
            waitForText("מבחן המפה נשמר. בעוד חודש נשווה.")
            compose.onNodeWithText("בואו נתחיל").assertExists()
        }
        runBlocking {
            val tests = dao.mapTests()
            assertEquals("PRE", tests.single().kind)
            assertEquals(20, tests.single().total)
            // Every answer is logged, no card changed, and none of it counts as practice.
            assertEquals(20, dao.reviews().count { it.mode == "MAP_TEST" })
            assertTrue(dao.cards().isEmpty())
            assertTrue(dao.practiceTimes().isEmpty())
            assertEquals(0, dao.countIntroducedSince(0))
        }
    }

    @Test
    fun postTestIsOfferedAfterAMonthAndComparedWithThePreTest() {
        val now = System.currentTimeMillis()
        runBlocking {
            dao.insert(MapTestEntity("pre", "PRE", now - 40 * DAY_MS, found = 3, total = 20, medianErrorMeters = 25_000.0))
            dao.insert(review("city.haifa", now - 40 * DAY_MS))
            dao.upsert(CardEntity("city.haifa", "LOCATE", CardState.REVIEW.value, null, 30.0, 5.0, now + DAY_MS, now - DAY_MS, 0))
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("חודש של אימון: כמה למדתם?")
            Screenshots.save(scenario, "d3-home-map-test-offer")
            compose.onNodeWithText("למבחן המפה").performClick()
            answerAllTwenty()
            compose.onNodeWithText("בפעם הראשונה: 3 מתוך 20, סטייה חציונית 25 ק״מ").assertExists()
            compose.onNodeWithText("חזרה למפה").performClick()
            // Taken: not offered again for another month.
            waitForText("התחילו אימון יומי")
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("חודש של אימון: כמה למדתם?")).fetchSemanticsNodes().isEmpty() }
        }
        runBlocking {
            assertEquals(listOf("PRE", "POST"), dao.mapTests().map { it.kind })
            // The card practiced before is untouched by the test.
            assertEquals(30.0, dao.cards().single().stability!!, 0.0)
        }
    }

    @Test
    fun laterSnoozesTheOffer() {
        val now = System.currentTimeMillis()
        runBlocking { dao.insert(review("city.haifa", now - 31 * DAY_MS)) }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForText("חודש של אימון: כמה למדתם?")
            compose.onNodeWithText("אחר כך").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("חודש של אימון: כמה למדתם?")).fetchSemanticsNodes().isEmpty() }
        }
        assertTrue(app.settings.current.mapTestSnoozedUntilDay > java.time.LocalDate.now().toEpochDay())
    }

    @Test
    fun answersLogWhatTheStatsNeed() = runBlocking {
        val now = System.currentTimeMillis()
        val haifa = app.content.place("city.haifa")!!
        val tiberias = app.content.place("city.tiberias")!!
        val before = StudyCard(FsrsCard(CardState.REVIEW, null, 10.0, 5.0, due = now - DAY_MS, lastReview = now - 11 * DAY_MS))
        val answer = Answer(
            Question.Locate(haifa, isNew = false, hint = true), Response.Tap(haifa.label), Grade(Rating.AGAIN, 30_000.0, 4_000.0),
            responseMs = 2_000, before = before, after = before, at = now, tappedNear = tiberias,
        )
        app.progress.save(answer, "s1")
        with(dao.reviews().single()) {
            assertEquals(CardState.REVIEW.value, stateBefore)
            assertEquals(now - DAY_MS, dueBefore)
            assertEquals(11.0, elapsedDays!!, 1e-6)
            assertTrue(predictedRecall!! in 0.5..0.95)
            assertTrue(hint)
            assertEquals("city.tiberias", tappedNear)
        }
        val record = app.progress.reviewRecords().single()
        assertTrue(record.dueReview)
        assertTrue(record.practice)

        // A new card has nothing before it.
        app.progress.save(answer.copy(before = null, tappedNear = null), "s1")
        assertNull(dao.reviews().last().stateBefore)
    }

    @Test
    fun progressShowsTheStatsAndTheMapTests() {
        val now = System.currentTimeMillis()
        runBlocking {
            dao.insert(MapTestEntity("pre", "PRE", now - 35 * DAY_MS, found = 4, total = 20, medianErrorMeters = 21_000.0))
            dao.insert(MapTestEntity("post", "POST", now - DAY_MS, found = 13, total = 20, medianErrorMeters = 3_500.0))
            // Four due reviews in the last days, three remembered.
            for ((i, rating) in listOf(3, 3, 4, 1).withIndex()) {
                dao.insert(
                    review("city.haifa", now - (i + 1) * DAY_MS, rating = rating, session = "s$i").copy(
                        stateBefore = CardState.REVIEW.value, dueBefore = now - (i + 2) * DAY_MS, elapsedDays = 5.0, predictedRecall = 0.9,
                    ),
                )
            }
            dao.upsert(CardEntity("city.haifa", "LOCATE", CardState.REVIEW.value, null, 8.0, 5.0, now + DAY_MS, now - DAY_MS, 0))
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התקדמות")
            compose.onNodeWithText("התקדמות").performClick()
            waitForText("סטייה ממוצעת בערים לפי יום (ק״מ)")
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("stats"))
            waitForText("נזכרתם ב־75% מהחזרות (4 חזרות). היעד: 85 עד 92 אחוז.")
            compose.onNodeWithText("מנוע החזרות צפה 90%").assertExists()
            compose.onNodeWithText("ימי אימון: 4 ב־7 הימים האחרונים, 4 ב־30").assertExists()
            compose.onNodeWithText("לפני האימון: 4 מתוך 20, סטייה חציונית 21 ק״מ").assertExists()
            compose.onNodeWithText("מאז הפעם הראשונה: 13 מקומות במקום 4").assertExists()
            compose.onNodeWithText("שיתוף התוצאות").assertExists()
            Screenshots.save(scenario, "d4-progress-stats")
        }
    }
}
