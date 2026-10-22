package com.shavei.mapa

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.MapTestEntity
import com.shavei.mapa.learning.Question
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

/**
 * The session screen around the answers: leaving midway, what feedback shows and where,
 * the summary of each kind of round, and the layout at large text and in landscape.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class SessionScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()
    private val dao get() = app.database.progressDao()

    private fun has(text: String, substring: Boolean = false) =
        compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String, substring: Boolean = false) = compose.waitUntil(10_000) { has(text, substring) }

    private fun shot(scenario: ActivityScenario<MainActivity>, name: String) {
        compose.waitForIdle()
        Screenshots.save(scenario, name)
    }

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

    /** The open session's state, once it is one of [kinds]. */
    private fun waitForUi(scenario: ActivityScenario<MainActivity>, vararg kinds: Class<out SessionUi>) =
        compose.waitUntil(10_000) { sessionOrNull(scenario)?.ui?.value?.let { ui -> kinds.any { it.isInstance(ui) } } == true }

    private fun back(scenario: ActivityScenario<MainActivity>) = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun card(placeId: String, facet: String = "LOCATE", stability: Double = 12.0, dueInDays: Int = 5): CardEntity {
        val now = System.currentTimeMillis()
        return CardEntity(placeId, facet, CardState.REVIEW.value, null, stability, 5.0, now + dueInDays * DAY_MS, now - 10 * DAY_MS, 0)
    }

    private fun seed(vararg cards: CardEntity) {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        runBlocking { cards.forEach { dao.upsert(it) } }
    }

    /** A tap far out in the sea, west of everything: a miss for any place on land. */
    private fun missTap() = compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.03f, height * 0.3f)) }

    private fun openMode(name: String) {
        waitForText("עוד דרכים לתרגל")
        compose.onNodeWithText("עוד דרכים לתרגל").performScrollTo().performClick()
        waitForText(name)
        compose.onNodeWithText(name).performScrollTo().performClick()
    }

    @After
    fun resetClock() {
        SessionViewModel.blitzDurationMs = 60_000
    }

    @Test
    fun leavingMidwayIsAskedFirstAndBackBeforeAnyAnswerJustLeaves() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForText("הבנתי, בואו נתרגל")
            // Nothing answered yet: back goes straight home.
            back(scenario)
            waitForText("התחילו אימון יומי")

            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForText("הבנתי, בואו נתרגל")
            compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
            waitForText("סמנו על המפה", substring = true)
            missTap()
            waitForText("המשך")
            // With an answer given, back asks; staying keeps the session where it was.
            back(scenario)
            waitForText("לצאת מהאימון?")
            shot(scenario, "s1-leave-dialog")
            compose.onNodeWithTag("leave-stay").performClick()
            compose.waitUntil(10_000) { !has("לצאת מהאימון?") }
            assertTrue(has("המשך"))
            back(scenario)
            waitForText("לצאת מהאימון?")
            compose.onNodeWithTag("leave-confirm").performClick()
            waitForText("רצף", substring = true)
        }
    }

    @Test
    fun leavingTheMapTestDiscardsItsAnswers() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("מדדו מאיפה אתם מתחילים")
            compose.onNodeWithText("מדדו מאיפה אתם מתחילים").performClick()
            waitForText("מבחן מפה\u00A0· שאלה 1 מתוך 20")
            compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.5f, height * 0.55f)) }
            waitForText("מבחן מפה\u00A0· שאלה 2 מתוך 20")
            // The tap stays on the map with a plain note: it counted, right or wrong unsaid.
            waitForText("הסימון נקלט")
            shot(scenario, "s2-map-test-tap-noted")
            compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.4f, height * 0.5f)) }
            waitForText("מבחן מפה\u00A0· שאלה 3 מתוך 20")
            compose.waitUntil(10_000) { runBlocking { dao.reviews() }.size == 2 }

            compose.onNodeWithText("יציאה").performClick()
            waitForText("לצאת ממבחן המפה?")
            waitForText("המבחן נשמר רק כשמסיימים אותו", substring = true)
            shot(scenario, "s3-map-test-leave")
            compose.onNodeWithText("יציאה בלי לשמור").performClick()
            waitForText("בואו נתחיל")
            // No test saved and no answers left behind.
            compose.waitUntil(10_000) { runBlocking { dao.reviews() }.isEmpty() }
            assertTrue(runBlocking { dao.mapTests() }.isEmpty())
        }
    }

    @Test
    fun aLowerPostTestIsFramedKindly() {
        val now = System.currentTimeMillis()
        runBlocking { dao.insert(MapTestEntity("pre", "PRE", now - 40 * DAY_MS, found = 18, total = 20, medianErrorMeters = 3_000.0)) }
        seed(card("city.haifa"))
        runBlocking {
            dao.insert(
                com.shavei.mapa.data.ReviewEntity(
                    placeId = "city.haifa", facet = "LOCATE", mode = "LOCATE", sessionId = "s", timestamp = now - 40 * DAY_MS, rating = 3,
                    errorMeters = 1000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3000, appVersion = "test",
                ),
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("למבחן המפה")
            compose.onNodeWithText("למבחן המפה").performClick()
            for (n in 1..20) {
                waitForText("מבחן מפה\u00A0· שאלה $n מתוך 20")
                missTap()
            }
            waitForText("סיום מבחן המפה")
            waitForText("אותם 20 מקומות, בפעם הראשונה ועכשיו:")
            waitForText("הפעם נמצאו פחות מקומות", substring = true)
            shot(scenario, "s4-map-test-fewer")
            compose.onNodeWithText("חזרה למפה").assertExists()
        }
    }

    @Test
    fun whichWayFeedbackExplainsTheRelationWithTheRightPlacesFact() {
        seed(card("city.tel_aviv"), card("city.haifa"), card("city.jerusalem"), card("city.jerusalem", "RELATE", 8.0, -1))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForUi(scenario, SessionUi.Ask::class.java)
            val q = (session(scenario).ui.value as SessionUi.Ask).question as Question.Relate
            val wrong = q.options.first { it != q.correct }
            compose.onNodeWithText(wrong.name).performClick()
            waitForText("לא הפעם")
            waitForText("${q.correct.name} ", substring = true)
            assertTrue(has("יותר מ", substring = true) || has("יותר ל", substring = true))
            // The fact is the right place's, never the wrong pick's.
            assertTrue(has(q.correct.fact))
            if (wrong.fact != q.correct.fact) assertTrue(!has(wrong.fact))
            shot(scenario, "s5-relate-feedback")
        }
    }

    @Test
    fun aWrongNameIsShownWhereThatPlaceIs() {
        seed(card("city.tel_aviv"), card("city.tel_aviv", "NAME", 1.0, -1))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForText("מה שם המקום המסומן?")
            shot(scenario, "s6-name-choice-close")
            val q = (session(scenario).ui.value as SessionUi.Ask).question as Question.NameChoice
            val wrong = q.options.first { it != q.place }
            compose.onNodeWithText(wrong.name).performClick()
            waitForText("בחרתם: ${wrong.name}")
            shot(scenario, "s7-name-choice-miss")
        }
    }

    @Test
    fun theTypedNameFieldIsLabeled() {
        seed(card("city.haifa"), card("city.haifa", "NAME", 5.0, -1))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForText("כתבו את שם המקום המסומן")
            waitForText("שם המקום")
            shot(scenario, "s8-name-typed")
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi", fontScale = 2.0f)
    fun theCounterAndScoreStayApartAtLargeText() {
        seed(card("region.sharon"), card("region.negev"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openMode("הרכבת המפה")
            waitForText("גררו למקום", substring = true)
            shot(scenario, "s9-build-font200")
        }
    }

    @Test
    fun aRoadTripWithEveryStopMissedDoesNotArrive() {
        val towns = listOf("city.hadera", "city.netanya", "city.herzliya", "city.tel_aviv", "city.rishon_lezion", "city.rehovot", "city.ashdod", "city.jerusalem")
        seed(*towns.map { card(it) }.toTypedArray())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openMode("מסע בדרכים")
            waitForText("יוצאים לדרך")
            // "From Jerusalem" reads as one word in Hebrew.
            assertTrue(has("יוצאים מ", substring = true) && !has("יוצאים מ־", substring = true))
            compose.onNodeWithText("יוצאים לדרך").performClick()
            var stops = 0
            while (true) {
                compose.waitUntil(10_000) { has("תחנה", substring = true) || has("סוף המסע") || has("הגעתם ליעד!") }
                if (has("סוף המסע") || has("הגעתם ליעד!")) break
                missTap()
                waitForText("המשך")
                if (stops == 1) shot(scenario, "s10-trip-missed-leg")
                compose.onNodeWithText("המשך").performClick()
                stops++
            }
            assertTrue(has("סוף המסע") && !has("הגעתם ליעד!"))
            waitForText("מצאתם 0 מתוך $stops תחנות")
            shot(scenario, "s11-trip-none-found")
            // Another trip, planned afresh from the places still learned.
            // Shown at once, enabled once the trip is saved.
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("מסע נוסף") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("מסע נוסף").performClick()
            compose.waitForIdle()
            shot(scenario, "s11b-trip-again")
            waitForText("יוצאים לדרך")
        }
    }

    @Test
    fun aBlitzWithNoAnswersSaysSoAndCanBePlayedAgain() {
        seed(*listOf("city.hadera", "city.netanya", "city.herzliya", "city.tel_aviv", "city.jerusalem").map { card(it) }.toTypedArray())
        SessionViewModel.blitzDurationMs = 1_000
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openMode("בליץ")
            waitForText("התחילו!")
            compose.onNodeWithText("התחילו!").performClick()
            Thread.sleep(1_500)
            waitForText("נגמר הזמן!")
            waitForText("הפעם לא סימנתם אף מקום", substring = true)
            assertTrue(!has("מתוך 0", substring = true))
            // One way back, at the bottom.
            assertTrue(!has("יציאה"))
            waitForText("סבב נוסף")
            shot(scenario, "s12-blitz-none")
        }
    }

    @Test
    fun anAllWrongSessionEndsKindly() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            while (true) {
                compose.waitUntil(10_000) { has("הבנתי, בואו נתרגל") || has("סמנו על המפה", substring = true) || has("סיום אימון") }
                when {
                    has("סיום אימון") -> break
                    has("הבנתי, בואו נתרגל") -> compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
                    else -> {
                        // Far inland in Jordan: a miss for the first places, seas and cities alike.
                        compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.97f, height * 0.62f)) }
                        waitForText("המשך")
                        compose.onNodeWithText("המשך").performClick()
                    }
                }
            }
            waitForText("התחלה קשה היא חלק מהדרך", substring = true)
            shot(scenario, "s13-summary-all-wrong")
        }
    }

    @Test
    fun placementListsThePlacesFound() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("כבר מכירים? דלגו על מה שאתם יודעים")
            compose.onNodeWithText("כבר מכירים? דלגו על מה שאתם יודעים").performClick()
            var found: String? = null
            while (true) {
                waitForUi(scenario, SessionUi.Ask::class.java, SessionUi.Feedback::class.java, SessionUi.Finished::class.java)
                when (val s = session(scenario).ui.value) {
                    is SessionUi.Finished -> break
                    is SessionUi.Feedback -> compose.onNodeWithText("המשך").performClick()
                    is SessionUi.Ask -> {
                        // The first place is found exactly, the rest are missed.
                        if (found == null) {
                            found = s.question.place.name
                            compose.runOnIdle { session(scenario).onTap(s.question.place.label) }
                        } else {
                            compose.runOnIdle { session(scenario).onTap(com.shavei.mapa.geo.MapPoint(-30_000.0, 600_000.0)) }
                        }
                        compose.waitUntil(10_000) { session(scenario).ui.value is SessionUi.Feedback }
                    }
                    else -> Unit
                }
            }
            waitForText("מצאתם: $found")
            shot(scenario, "s14-placement-found")
        }
    }

    @Test
    @Config(qualifiers = "w860dp-h400dp-land-xxhdpi")
    fun inLandscapeTheCardsStandBesideTheMap() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForText("הבנתי, בואו נתרגל")
            shot(scenario, "s15-discover-land")
            compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
            waitForText("סמנו על המפה", substring = true)
            compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.25f, height * 0.6f)) }
            waitForText("המשך")
            shot(scenario, "s16-feedback-land")
        }
    }

    @Test
    fun theSummaryLightsUpThePlacesPracticed() {
        seed(card("region.sharon"), card("region.negev"))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openMode("הרכבת המפה")
            while (true) {
                waitForUi(scenario, SessionUi.Ask::class.java, SessionUi.Feedback::class.java, SessionUi.Finished::class.java)
                when (val s = session(scenario).ui.value) {
                    is SessionUi.Finished -> break
                    is SessionUi.Feedback -> compose.onNodeWithText("המשך").performClick()
                    is SessionUi.Ask -> {
                        compose.runOnIdle { session(scenario).onMoved(0.0, 0.0) }
                        compose.waitUntil(10_000) { session(scenario).ui.value !== s }
                    }
                    else -> Unit
                }
            }
            waitForText("סיום הרכבת המפה")
            waitForText("מצאתם 2 מתוך 2 מקומות")
            shot(scenario, "s17-build-summary")
            val summary = (session(scenario).ui.value as SessionUi.Finished).summary
            assertEquals(setOf("region.sharon", "region.negev"), summary.practiced.map { it.id }.toSet())
            // Another round, planned afresh.
            compose.waitUntil(10_000) { compose.onAllNodes(hasText("סבב נוסף") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("סבב נוסף").performClick()
            waitForText("הרכבת המפה\u00A0· שאלה 1 מתוך", substring = true)
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun theWholeCountryKnownStaysReadable() {
        seed(*app.content.places.map { card(it.id, stability = 40.0) }.toTypedArray())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("209 ידועים", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            shot(scenario, "s18-home-all-known-small")
        }
    }
}
