package com.shavei.mapa

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.DailySessionEntity
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.data.ThemeChoice
import com.shavei.mapa.geo.MapPoint
import com.shavei.mapa.geo.Shape
import com.shavei.mapa.learning.Grader
import com.shavei.mapa.learning.PlacementTest
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.DAY_MS
import com.shavei.mapa.ui.session.SessionUi
import com.shavei.mapa.ui.session.SessionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.random.Random

/** Phase 1c: the placement test, the progress screen, and settings (including dark mode). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class PlayerExperienceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    /** The open session's ViewModel, to answer exactly where a tap on the screen cannot. */
    private fun session(scenario: ActivityScenario<MainActivity>): SessionViewModel = sessionOrNull(scenario)!!

    private fun sessionOrNull(scenario: ActivityScenario<MainActivity>): SessionViewModel? {
        var vm: SessionViewModel? = null
        scenario.onActivity { a ->
            // Each visit to a screen has its own store, kept by the activity's VisitStores.
            val visits = a.viewModelStore.keys().firstNotNullOfOrNull { a.viewModelStore[it] as? VisitStores }
            val store: ViewModelStore = visits?.latest() ?: return@onActivity
            val key = store.keys().filter { it.startsWith("session-") }.maxByOrNull { it.removePrefix("session-").toInt() }
            vm = key?.let { store[it] } as? SessionViewModel
        }
        return vm
    }

    /** The open session's question, once it asks one. */
    private fun waitForAsk(scenario: ActivityScenario<MainActivity>): SessionUi.Ask {
        compose.waitUntil(10_000) { sessionOrNull(scenario)?.ui?.value is SessionUi.Ask }
        return session(scenario).ui.value as SessionUi.Ask
    }

    @After
    fun drawQuestionsFreely() {
        SessionViewModel.placementRandom = Random.Default
    }

    @Test
    fun placementTestSeedsWhatThePlayerFinds() {
        // A draw where the tap far out at sea misses the first place asked (not the Mediterranean).
        val seed = (0 until 1_000).first { seed ->
            val first = PlacementTest(app.content, app.scheduler, random = Random(seed)).next()!!.place
            !Grader.grade(first, FAR_AT_SEA, 3_000, firstReview = true).isCorrect
        }
        SessionViewModel.placementRandom = Random(seed)
        lateinit var missed: String
        lateinit var found: String
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("כבר מכירים? דלגו על מה שאתם יודעים")
            compose.onNodeWithText("כבר מכירים? דלגו על מה שאתם יודעים").performClick()

            // No Discover card: the test asks straight away. Which places it asks depends on the answers.
            val first = waitForAsk(scenario)
            missed = first.question.place.id
            waitForText("סמנו על המפה: ${first.question.place.name}")
            val max = PlacementTest(app.content, app.scheduler).maxQuestions
            compose.onNodeWithText("בדיקת רמה\u00A0· שאלה 1 מתוך $max לכל היותר").assertExists()
            Screenshots.save(scenario, "c1-placement-ask")
            // Far out at sea: not the place asked.
            compose.runOnIdle { session(scenario).onTap(FAR_AT_SEA) }
            waitForText("לא הפעם")
            compose.onNodeWithText("המשך").performClick()

            // The next place, found exactly: a point at its spot, a road on its line, an area at its label.
            val second = waitForAsk(scenario)
            found = second.question.place.id
            val at = when (val shape = second.question.place.shape) {
                is Shape.Point -> shape.at
                is Shape.Lines -> shape.lines.first().let { MapPoint(it.coords[0], it.coords[1]) }
                is Shape.Area -> second.question.place.label
            }
            compose.runOnIdle { session(scenario).onTap(at) }
            waitForText("מצוין!")
            compose.onNodeWithText("יציאה").performClick()
            waitForText("לצאת מבדיקת הרמה?")
            compose.onNodeWithTag("leave-confirm").performClick()

            // Only the place found has a card.
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("1 בלמידה · 0 ידועים · מתוך 209")).fetchSemanticsNodes().isNotEmpty() }
            // The placement test is not a day of practice: no streak and no reminder offer yet.
            compose.onNodeWithText("אימון היום יתחיל רצף").assertExists()
            assertTrue(compose.onAllNodes(hasText("תזכורת יומית?")).fetchSemanticsNodes().isEmpty())
            Screenshots.save(scenario, "c2-home-after-placement")
        }
        runBlocking {
            val cards = app.database.progressDao().cards()
            assertTrue(missed != found)
            assertEquals(listOf(found), cards.map { it.placeId })
            assertEquals(CardState.REVIEW.value, cards.single().state)
            assertEquals(0, app.database.progressDao().countIntroducedSince(0))
        }
    }

    @Test
    fun progressShowsRegionsAndTheErrorHistory() {
        val now = System.currentTimeMillis()
        runBlocking {
            val dao = app.database.progressDao()
            for ((city, stability) in listOf("city.tel_aviv" to 30.0, "city.ramat_gan" to 5.0, "city.haifa" to 2.0)) {
                dao.upsert(CardEntity(city, "LOCATE", CardState.REVIEW.value, null, stability, 5.0, now + DAY_MS, now - DAY_MS, 0))
            }
            // Three days of daily sessions with Pin It answers, getting more precise.
            for ((daysAgo, km) in listOf(2 to 9.0, 1 to 5.5, 0 to 2.0)) {
                dao.insert(DailySessionEntity("s$daysAgo", now - daysAgo * DAY_MS, 2, finishedAt = now - daysAgo * DAY_MS))
                for (city in listOf("city.tel_aviv", "city.haifa")) {
                    dao.insert(
                        ReviewEntity(
                            placeId = city, facet = "LOCATE", mode = "LOCATE", sessionId = "s$daysAgo",
                            timestamp = now - daysAgo * DAY_MS, rating = 3, errorMeters = km * 1000, tapX = 0.0, tapY = 0.0,
                            answer = null, responseMs = 3000, appVersion = "test",
                        ),
                    )
                }
            }
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("רצף: 3 ימים")
            compose.onNodeWithText("התקדמות").performClick()
            waitForText("סטייה ממוצעת בערים לפי יום (ק״מ)")
            compose.onNodeWithTag("error-chart").assertExists()
            compose.onNode(hasContentDescription("1 ידוע · ", substring = true)).assertExists()
            Screenshots.save(scenario, "c3-progress")
            compose.onNodeWithTag("tab-regions").performClick()
            compose.onNodeWithTag("progress").performScrollToNode(hasContentDescription("גוש דן", substring = true))
            Screenshots.save(scenario, "c4-progress-regions")
            // After the regions: nature, ports and heritage, not started yet, so folded until asked for.
            compose.onNodeWithTag("progress").performScrollToNode(hasTestTag("themes-fold"))
            compose.onNodeWithTag("themes-fold").performClick()
            compose.onNodeWithTag("progress").performScrollToNode(hasContentDescription("מורשת: 0 מתוך 22", substring = true))
            compose.onNode(hasContentDescription("טבע: 0 מתוך 22", substring = true)).assertExists()
            Screenshots.save(scenario, "c4b-progress-themes")
            compose.onNodeWithText("חזרה").performClick()
            // Practiced today already: home offers another session.
            waitForText("אימון נוסף")
        }
    }

    @Test
    fun settingsSwitchToDarkModeAndTheGreenLine() {
        app.settings.update { it.copy(onboarded = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("הגדרות")
            compose.onNodeWithText("הגדרות").performClick()
            waitForText("אורך אימון")
            // The settings scroll: bring each row on screen before tapping it.
            compose.onNodeWithText("קצר, 10 עד 18 שאלות").performScrollTo().performClick()
            compose.onNodeWithText("הקו הירוק").performScrollTo().performClick()
            compose.onNodeWithText("כהה").performScrollTo().performClick()
            compose.waitForIdle()
            Screenshots.save(scenario, "c5-settings-dark")
            compose.onNodeWithText("חזרה").performClick()
            waitForText("התחילו אימון יומי")
            Screenshots.save(scenario, "c6-home-dark-green-line")
        }
        with(app.settings.current) {
            assertEquals(ThemeChoice.DARK, theme)
            assertTrue(showGreenLine)
            assertEquals(10, sessionLength.questions)
        }
    }

    private companion object {
        /** Far out in the sea, west of everything: a miss for any place on land. */
        val FAR_AT_SEA = MapPoint(-30_000.0, 600_000.0)
    }
}
