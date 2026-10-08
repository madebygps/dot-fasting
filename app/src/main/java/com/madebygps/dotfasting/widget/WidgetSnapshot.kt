package com.madebygps.dotfasting.widget

import android.content.Context
import com.madebygps.dotfasting.data.FastingRepository
import com.madebygps.dotfasting.data.SettingsStore
import com.madebygps.dotfasting.domain.project
import com.madebygps.dotfasting.domain.counterMillis
import com.madebygps.dotfasting.domain.counterText
import com.madebygps.dotfasting.system.AndroidFastingClock
import kotlinx.coroutines.flow.first

internal suspend fun readWidgetSnapshot(context: Context): WidgetSnapshot {
    val sessions = FastingRepository(context).sessions.first()
    val settings = SettingsStore(context).settings.first()
    val clock = AndroidFastingClock(context).snapshot()
    val activeSessions = sessions.filter { it.endEpochMillis == null }
    check(activeSessions.size <= 1) { "More than one active fast in storage" }
    val active = activeSessions.singleOrNull()
    if (active == null) {
        val time = counterText(if (settings.countDown) settings.lastGoalMillis ?: 0L else 0L, seconds = false)
        return WidgetSnapshot(false, "No active fast. $time. Tap to open.", time, highlight = settings.highlightArgb.toInt())
    }
    val state = project(active, clock)
    val time = if (state.needsTimeReview) "—" else counterText(counterMillis(state, settings.countDown), seconds = false)
    return WidgetSnapshot(
        true,
        if (state.needsTimeReview) "Time needs review. Open Dot Fasting to edit start time."
        else "$time ${if (settings.countDown) "remaining" else "elapsed"}. Snapshot at ${WidgetTime.timestamp(clock.wallEpochMillis)}. Tap to open.",
        time,
        state.progress,
        settings.highlightArgb.toInt(),
    )
}
