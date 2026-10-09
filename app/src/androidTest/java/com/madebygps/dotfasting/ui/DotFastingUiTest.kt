package com.madebygps.dotfasting.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import android.view.KeyEvent
import android.graphics.Bitmap
import java.io.File
import com.madebygps.dotfasting.domain.FastProjection
import com.madebygps.dotfasting.domain.FastSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DotFastingUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun idleRingIsBoundedAndNavigationRemainsAccessible() {
        render()
        compose.onNodeWithContentDescription("No active fast").assertWidthIsEqualTo(300.dp)
        val ring = compose.onNodeWithContentDescription("No active fast").fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("Timer should be near the screen center", ring.center.y / screen.height in 0.4f..0.65f)
        compose.onNodeWithText("Start fast").assertIsEnabled()
        compose.onNodeWithText("?").assertDoesNotExist()
        compose.onNodeWithText("FASTING").assertDoesNotExist()
        compose.onNodeWithText("Dot Fasting").assertDoesNotExist()
        compose.onNodeWithText("TIMER STAGE").assertDoesNotExist()
        capture("idle")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("?").assertDoesNotExist()
        compose.onNodeWithText("Goal notification").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Glyph Toy").assertDoesNotExist()
        compose.onNodeWithText("About Dot Fasting").performScrollTo().performClick()
        compose.onNodeWithText("About Dot Fasting").assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        capture("settings")
        compose.onNodeWithText("BACK").assertDoesNotExist()
        back()
        compose.onNodeWithContentDescription("Progress").performClick()
        compose.onNodeWithText("Overview").assertIsDisplayed()
        compose.onNodeWithText("NO HISTORY").performScrollTo().assertIsDisplayed()
        back()
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText("Completed fasts will appear here.").assertIsDisplayed()
        capture("history")
    }

    @Test
    fun directionCanBeChangedAndActiveStartIsEditable() {
        render(
            sessions = listOf(FastSession(id = 1, startEpochMillis = 0, goalMillis = 10_800_000)),
            projection = FastProjection(3_600_000, 7_200_000, 1f / 3, false, false),
        )
        compose.onNodeWithText("01:00:00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Remaining").performClick().assertIsSelected()
        back()
        compose.onNodeWithText("02:00:00").assertIsDisplayed()
        compose.onNodeWithText("ESTIMATED PHASE").assertDoesNotExist()
        compose.onNodeWithContentDescription("About estimated fasting phases").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit start time").performClick()
        compose.onNodeWithText("START").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose start date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose start time").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun firstUseRequiresExplicitPresetSelection() {
        var started: Long? = null
        render(onStart = { started = it })
        compose.onNodeWithText("Start fast").performClick()
        compose.onAllNodesWithText("Start fast").onLast().assertIsNotEnabled()
        compose.onNodeWithText("12h").performScrollTo().performClick()
        compose.onAllNodesWithText("Start fast").onLast().assertIsEnabled().performClick()
        assertEquals(12L * 3_600_000L, started)
    }

    @Test
    fun customGoalRejectsZeroThenAcceptsPositiveMinutes() {
        var started: Long? = null
        render(onStart = { started = it })
        compose.onNodeWithText("Start fast").performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onAllNodesWithText("Start fast").onLast().performClick()
        compose.onNodeWithText(
            "Enter a positive duration with whole hours and minutes (0–59). The duration must fit in a timer.",
        ).performScrollTo().assertIsDisplayed()
        assertNull(started)
        compose.onNodeWithText("Minutes (0–59)").performScrollTo().performTextInput("30")
        compose.onAllNodesWithText("Start fast").onLast().performClick()
        assertEquals(30L * 60_000L, started)
    }

    @Test
    fun customGoalRejectsCheckedArithmeticOverflow() {
        var started: Long? = null
        render(onStart = { started = it })
        compose.onNodeWithText("Start fast").performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Hours").performScrollTo().performTextInput(Long.MAX_VALUE.toString())
        compose.onAllNodesWithText("Start fast").onLast().performClick()
        compose.onNodeWithText(
            "Enter a positive duration with whole hours and minutes (0–59). The duration must fit in a timer.",
        ).performScrollTo().assertIsDisplayed()
        assertNull(started)
    }

    @Test
    fun elapsedBeyondGoalKeepsManualEndWithConfirmation() {
        var ended = false
        val session = FastSession(id = 1, startEpochMillis = 0, goalMillis = 3_600_000)
        render(
            sessions = listOf(session),
            projection = FastProjection(7_200_000, 0, 1f, true, false),
            onEnd = { ended = true },
        )
        compose.onNodeWithText("02:00:00").assertIsDisplayed()
        compose.onNodeWithText("Goal met").assertDoesNotExist()
        capture("active")
        assertEquals(false, ended)
        compose.onNodeWithText("End fast").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("End this fast?").assertIsDisplayed()
        compose.onNodeWithText("Keep fasting").performClick()
        assertEquals(false, ended)
    }

    private fun capture(name: String) {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "ui-$name.png").outputStream().use {
            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test
    fun historySurfacesRepositoryValidationErrorAndKeepsRecordEditable() {
        val end = System.currentTimeMillis()
        val session = FastSession(
            id = 1, startEpochMillis = end - 3_600_000, endEpochMillis = end,
            goalMillis = 3_600_000, completedDurationMillis = 3_600_000,
        )
        render(sessions = listOf(session), error = "This interval overlaps another fast, including an active fast.")
        compose.onNodeWithText("This interval overlaps another fast, including an active fast.").assertIsDisplayed()
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText(sessionTimeText(session)).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit timestamps").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Choose start date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose start time").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose end date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose end time").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Save").fetchSemanticsNodes().isEmpty() }
        back()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("EDIT").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(sessionTimeText(session)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GOAL 1h").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit timestamps").assertDoesNotExist()
    }

    @Test
    fun calendarShowsCompletedDaysAndFiltersSelectedDay() {
        val now = java.time.ZonedDateTime.now()
        val end = now.toInstant().toEpochMilli()
        render(sessions = listOf(FastSession(1, end - 60_000, end, 3_600_000, completedDurationMillis = 60_000)))
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithContentDescription("${now.toLocalDate()}, 1 completed fasts").assertIsSelected().performClick()
        val other = if (now.dayOfMonth == 1) now.toLocalDate().plusDays(1) else now.toLocalDate().minusDays(1)
        compose.onNodeWithContentDescription("$other, 0 completed fasts").performClick()
        compose.onNodeWithText("No fasts on this day.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Next month").performClick()
    }

    @Test
    fun historyDeletionRequiresConfirmationAndReturnsToCalendar() {
        var deleted: Long? = null
        val end = System.currentTimeMillis()
        val session = FastSession(1, end - 60_000, end, 3_600_000, completedDurationMillis = 60_000)
        render(sessions = listOf(session), onDelete = { deleted = it })
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText(sessionTimeText(session)).performScrollTo().performClick()
        compose.onNodeWithText("Delete fast").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("Delete this fast?").isDisplayed() }
        compose.onNodeWithText("Delete this fast?").assertIsDisplayed()
        assertNull(deleted)
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Delete this fast?").fetchSemanticsNodes().isEmpty() }
        assertNull(deleted)
        compose.onNodeWithContentDescription("Edit timestamps").assertIsDisplayed()
        compose.onNodeWithText("Delete fast").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("Delete").isDisplayed() }
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("EDIT").fetchSemanticsNodes().isEmpty() }
        assertEquals(1L, deleted)
        compose.onNodeWithText("Completed fasts will appear here.").assertIsDisplayed()
    }

    @Test
    fun activeDeletionWarnsThatTimerWillBeRemoved() {
        val start = System.currentTimeMillis()
        val session = FastSession(1, start, goalMillis = 3_600_000)
        render(sessions = listOf(session))
        compose.onNodeWithContentDescription("History").performClick()
        compose.onNodeWithText(sessionTimeText(session)).performScrollTo().performClick()
        compose.onNodeWithText("Delete fast").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onNodeWithText("Delete this fast?").isDisplayed() }
        compose.onNodeWithText("This removes the active timer and its record. This cannot be undone.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Delete this fast?").fetchSemanticsNodes().isEmpty() }
        back()
        back()
        compose.onNodeWithText("End fast").assertIsDisplayed()
    }

    private fun render(
        sessions: List<FastSession> = emptyList(),
        projection: FastProjection? = null,
        error: String? = null,
        onStart: (Long) -> Unit = {},
        onEnd: () -> Unit = {},
        onDelete: (Long) -> Unit = {},
    ) {
        compose.setContent {
            var countDown by remember { mutableStateOf(false) }
            var records by remember { mutableStateOf(sessions) }
            DotFastingApp(
                sessions = records,
                projection = projection,
                highlightArgb = 0xFFE8343A,
                lastGoalMillis = null,
                notificationsEnabled = false,
                glyphEnabled = false,
                glyphCapability = GlyphUiCapability(
                    false,
                    "Glyph SDK not included",
                    "Optional SDK missing.",
                    showInSettings = false,
                ),
                busy = false,
                error = error,
                onDismissError = {},
                onStart = onStart,
                onEnd = onEnd,
                onEdit = { _, _, _ -> },
                onDelete = { id ->
                    onDelete(id)
                    records = records.filterNot { it.id == id }
                },
                onHighlight = {},
                onNotifications = {},
                onGlyph = {},
                onGlyphSetup = {},
                countDown = countDown,
                onCountDown = { countDown = it },
            )
        }
    }

    private fun back() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }
}
