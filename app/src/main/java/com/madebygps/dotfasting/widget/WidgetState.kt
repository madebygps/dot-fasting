package com.madebygps.dotfasting.widget

import android.content.Context
import com.madebygps.dotfasting.data.FastingRepository
import com.madebygps.dotfasting.data.SettingsStore
import com.madebygps.dotfasting.domain.ClockSnapshot
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.project
import com.madebygps.dotfasting.system.AndroidFastingClock
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

internal enum class WidgetStatus { IDLE, RUNNING, REVIEW, UNAVAILABLE }

internal data class WidgetState(
    val status: WidgetStatus,
    val elapsedMillis: Long = 0,
    val progress: Int = 0,
    val goalMillis: Long = 0,
    val highlight: Int = 0xFFE8343A.toInt(),
    val startEpochMillis: Long? = null,
) {
    val active: Boolean get() = status == WidgetStatus.RUNNING || status == WidgetStatus.REVIEW
}

internal fun widgetState(active: FastSession?, clock: ClockSnapshot, highlight: Int): WidgetState {
    if (active == null) return WidgetState(WidgetStatus.IDLE, highlight = highlight)
    val state = project(active, clock)
    return WidgetState(
        status = if (state.needsTimeReview) WidgetStatus.REVIEW else WidgetStatus.RUNNING,
        elapsedMillis = state.elapsedMillis,
        progress = (state.progress * 1000).roundToInt(),
        goalMillis = active.goalMillis,
        highlight = highlight,
        startEpochMillis = active.startEpochMillis,
    )
}

internal suspend fun readWidgetState(context: Context): WidgetState {
    val sessions = FastingRepository(context).sessions.first()
    val settings = SettingsStore(context).settings.first()
    val clock = AndroidFastingClock(context).snapshot()
    val activeSessions = sessions.filter { it.endEpochMillis == null }
    check(activeSessions.size <= 1) { "More than one active fast in storage" }
    return widgetState(activeSessions.singleOrNull(), clock, settings.highlightArgb.toInt())
}
