package com.shavei.mapa

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shavei.mapa.data.AnswerSource
import com.shavei.mapa.data.CardEntity
import com.shavei.mapa.data.DailySessionEntity
import com.shavei.mapa.data.ReviewEntity
import com.shavei.mapa.learning.Answer
import com.shavei.mapa.learning.Grade
import com.shavei.mapa.learning.Question
import com.shavei.mapa.learning.Response
import com.shavei.mapa.learning.StudyCard
import com.shavei.mapa.learning.center
import com.shavei.mapa.learning.fsrs.CardState
import com.shavei.mapa.learning.fsrs.Rating
import com.shavei.mapa.learning.fsrs.DAY_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * What makes a day: the welcome screen stays until the first answer, a day counts only
 * once its daily session is finished, and home says where the day and the streak stand.
 * Also the reminder offer's answers, and that old screens' ViewModels go.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class DailyHabitTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<MapaApplication>()
    private val dao get() = app.database.progressDao()

    private fun has(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun waitForText(text: String) = compose.waitUntil(10_000) { has(text) }

    private fun back(scenario: ActivityScenario<MainActivity>) = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun review(placeId: String, sessionId: String, at: Long) = ReviewEntity(
        placeId = placeId, facet = "LOCATE", mode = "LOCATE", sessionId = sessionId, timestamp = at,
        rating = 3, errorMeters = 1_000.0, tapX = 0.0, tapY = 0.0, answer = null, responseMs = 3_000, appVersion = "test",
    )

    /** A finished daily session [daysAgo] days ago, with one answer. */
    private fun dailyDone(daysAgo: Long) = runBlocking {
        val at = System.currentTimeMillis() - daysAgo * DAY_MS
        dao.insert(DailySessionEntity("d$daysAgo", at - 60_000, 1, finishedAt = at))
        dao.insert(review("city.haifa", "d$daysAgo", at - 30_000))
    }

    @Test
    fun theWelcomeScreenStaysUntilTheFirstAnswer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // A look at the first card, then back: the welcome screen is still there.
            waitForText("בואו נתחיל")
            compose.onNodeWithText("בואו נתחיל").performClick()
            waitForText("מקום חדש")
            back(scenario)
            waitForText("כבר מכירים את הארץ? בדיקת רמה")

            // The same for the placement test, backed out of at its first question.
            compose.onNodeWithText("כבר מכירים את הארץ? בדיקת רמה").performClick()
            waitForText("בדיקת רמה · שאלה 1")
            back(scenario)
            waitForText("מבחן מפה קצר לפני שמתחילים")
            Screenshots.save(scenario, "h1-welcome-after-back")
            assertFalse(app.settings.current.onboarded)

            // One answer in the placement test, and the player is in.
            compose.onNodeWithText("כבר מכירים את הארץ? בדיקת רמה").performClick()
            waitForText("בדיקת רמה · שאלה 1")
            compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.08f, height * 0.45f)) }
            waitForText("המשך")
            back(scenario)
            waitForText("דרגה: ")
        }
        assertTrue(app.settings.current.onboarded)
    }

    @Test
    fun leavingTheDailySessionEarlyLeavesTheDayOpenToContinue() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("התחילו אימון יומי")
            compose.onNodeWithText("התחילו אימון יומי").performClick()
            waitForText("הבנתי, בואו נתרגל")
            compose.onNodeWithText("הבנתי, בואו נתרגל").performClick()
            waitForText("סמנו על המפה")
            compose.onNodeWithTag("map").performTouchInput { click(Offset(width * 0.5f, height * 0.4f)) }
            waitForText("המשך")
            back(scenario)

            // One of fifteen answered: not a day yet, and the rest is offered.
            waitForText("עצרתם באמצע האימון היומי. נשארו עוד 14 שאלות.")
            assertTrue(has("אימון היום יתחיל רצף"))
            assertFalse(has("סיימתם להיום"))
            Screenshots.save(scenario, "h2-home-stopped-early")

            // Going on plans just what was left.
            compose.onNodeWithText("המשך האימון היומי").performClick()
            compose.waitUntil(10_000) { has("סמנו על המפה") || has("מקום חדש") }
        }
        runBlocking {
            assertEquals(14, dao.dailySessionsSince(0).first().size)
            assertTrue(app.progress.daysPlayed().isEmpty())
        }
    }

    @Test
    fun aDayCountsOnlyWhenTheDailySessionIsFinished() = runBlocking {
        val now = System.currentTimeMillis()
        val progress = app.progress
        // Answers outside a daily session (Focus, Map Builder, free play) do not make a day.
        dao.insert(review("city.haifa", "focus", now))
        assertTrue(progress.daysPlayed().isEmpty())
        assertEquals(null, progress.dailyToday(now).questionsLeft)

        // A daily session of two, left after one answer: one question left.
        assertEquals(2, progress.startDailySession("daily", 2, now))
        dao.insert(review("city.haifa", "daily", now))
        assertEquals(1, progress.dailyToday(now).questionsLeft)
        assertFalse(progress.dailyToday(now).done)

        // Continued with what was left; reaching its summary makes the day.
        assertEquals(1, progress.startDailySession("daily2", 15, now))
        progress.finishDailySession("daily2", now)
        assertTrue(progress.dailyToday(now).done)
        assertEquals(setOf(LocalDate.now()), progress.daysPlayed())
    }

    /** A right Pin It answer on [placeId], seeded as the placement test seeds a place found. */
    private fun rightAnswer(placeId: String, now: Long): Answer {
        val place = app.content.place(placeId)!!
        return Answer(
            Question.Locate(place, isNew = false, hint = false), Response.Tap(place.shape.center), Grade(Rating.GOOD, 0.0, points = 5_000),
            responseMs = 2_000, before = null, after = StudyCard(app.scheduler.graduate(Rating.GOOD, now)), at = now,
        )
    }

    @Test
    fun answeringAsFarAsPlannedCountsEvenWithoutTheSummary() = runBlocking {
        val now = System.currentTimeMillis()
        app.progress.startDailySession("daily", 1, now)
        app.progress.save(rightAnswer("city.haifa", now), "daily", AnswerSource.PRACTICE)
        assertTrue(app.progress.dailyToday(now).done)
    }

    @Test
    fun retakingThePlacementTestKeepsCardsAlreadyLearned() = runBlocking {
        val now = System.currentTimeMillis()
        dao.upsert(CardEntity("city.haifa", "LOCATE", CardState.REVIEW.value, null, 40.0, 5.0, now + 9 * DAY_MS, now, 0))
        app.progress.save(rightAnswer("city.haifa", now), "placement", AnswerSource.PLACEMENT)
        app.progress.save(rightAnswer("city.tel_aviv", now), "placement", AnswerSource.PLACEMENT)
        val cards = dao.cards().associateBy { it.placeId }
        // The card already in practice keeps its schedule; a new place found is seeded.
        assertEquals(40.0, cards.getValue("city.haifa").stability!!, 0.0)
        assertTrue("city.tel_aviv" in cards)
    }

    @Test
    fun withNothingDueHomeNeverSaysDoneBeforeTheDayIs() {
        val now = System.currentTimeMillis()
        runBlocking {
            for (place in app.content.places) {
                dao.upsert(CardEntity(place.id, "LOCATE", CardState.REVIEW.value, null, 30.0, 5.0, now + 5 * DAY_MS, now - DAY_MS, 0))
            }
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("אין כרגע מקומות שמחכים לחזרה")
            assertFalse(has("סיימתם להיום"))
            compose.onNodeWithText("תרגול נוסף").assertExists()
            Screenshots.save(scenario, "h3-home-nothing-due")
        }
    }

    @Test
    fun withNothingToAskHomeSaysWhyAndOffersNoEmptySession() {
        val now = System.currentTimeMillis()
        runBlocking {
            // Four places in their first steps, not due for hours: no session to offer now.
            for (place in app.content.places.take(4)) {
                dao.upsert(CardEntity(place.id, "LOCATE", CardState.LEARNING.value, 1, 2.3, 5.0, now + 3 * 60 * 60_000L, now - 60_000, 0))
            }
        }
        app.settings.update { it.copy(onboarded = true, reminderOffered = true, pace = com.shavei.mapa.data.LearningPace.UNLIMITED) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("המקומות החדשים הבאים יגיעו כשהמקומות שבלמידה ייקלטו")
            assertFalse(has("התחילו אימון יומי"))
            assertFalse(has("תרגול נוסף"))
            Screenshots.save(scenario, "h7-home-new-waits")
        }
    }

    @Test
    fun theStreakSaysWhenAFreezeSavedItTheBestAndHowToKeepIt() {
        // A week of days two to eight days ago earns a freeze, used yesterday; before them,
        // a longer streak of ten days that broke.
        (2L..8L).forEach(::dailyDone)
        (11L..20L).forEach(::dailyDone)
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("רצף: 7 ימים")
            assertTrue(has("שיא: 10 ימים"))
            assertTrue(has("הקפאה שמרה על הרצף ביום שלא התאמנתם."))
            assertTrue(has("סיימו את האימון היומי כדי לשמור על הרצף."))
            Screenshots.save(scenario, "h4-home-streak-saved")
        }
    }

    @Test
    fun aSavedFreezeIsExplained() {
        (1L..7L).forEach(::dailyDone)
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("הקפאות שמורות: 1")
            compose.onNodeWithText("מה זה?").performScrollTo().performClick()
            waitForText("הקפאה שומרת על הרצף ביום שלא התאמנתם. על כל 7 ימים ברצף מקבלים הקפאה, ואפשר לשמור עד 2.")
            Screenshots.save(scenario, "h5-home-freeze-explained")
        }
    }

    @Test
    fun aNoInAndroidsQuestionIsANotNow() {
        runBlocking { dao.insert(review("city.haifa", "s", System.currentTimeMillis())) }
        app.settings.update { it.copy(onboarded = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("תזכורת יומית?")
            compose.onNodeWithText("כן, הזכירו לי").performClick()
            scenario.onActivity { activity ->
                val request = checkNotNull(shadowOf(activity).lastRequestedPermission)
                // Android showed its question and the player said no: it now offers a rationale.
                shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS, true)
                activity.onRequestPermissionsResult(request.requestCode, request.requestedPermissions, intArrayOf(PackageManager.PERMISSION_DENIED))
            }
            compose.waitUntil(10_000) { !has("תזכורת יומית?") }
            assertFalse(has("פתיחת הגדרות ההתראות"))
        }
        assertTrue(app.settings.current.reminderOffered)
        assertFalse(app.settings.current.reminderOn)
    }

    @Test
    fun theReminderOfferAsksForATime() {
        runBlocking { dao.insert(review("city.haifa", "s", System.currentTimeMillis())) }
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.settings.update { it.copy(onboarded = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText("באיזו שעה?")
            compose.onNodeWithText("שעת התזכורת: 20:00").performClick()
            waitForText("אישור")
            Screenshots.save(scenario, "h6-reminder-time")
            // The clock face: 21 o'clock, then the minutes stay at 00.
            compose.onNode(hasContentDescription("21 hours")).performClick()
            compose.onNodeWithText("אישור").performClick()
            waitForText("שעת התזכורת: 21:00")
            compose.onNodeWithText("כן, הזכירו לי").performClick()
            waitForText("התזכורת נקבעה ל־21:00")
        }
        assertEquals(21 * 60, app.settings.current.reminderMinutes)
        assertTrue(app.settings.current.reminderOn)
    }

    @Test
    fun aFinishedSessionsViewModelIsCleared() {
        app.settings.update { it.copy(onboarded = true, reminderOffered = true) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            repeat(2) {
                waitForText("התחילו אימון יומי")
                compose.onNodeWithText("התחילו אימון יומי").performClick()
                waitForText("הבנתי, בואו נתרגל")
                back(scenario)
            }
            waitForText("התחילו אימון יומי")
            scenario.onActivity { activity ->
                val keys = activity.viewModelStore.keys()
                assertTrue(keys.toString(), keys.none { "Session" in it || "Home" in it })
            }
        }
    }
}
