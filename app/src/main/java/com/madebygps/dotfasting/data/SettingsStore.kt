package com.madebygps.dotfasting.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.madebygps.dotfasting.domain.AppSettings
import com.madebygps.dotfasting.domain.FastingException
import com.madebygps.dotfasting.domain.validateGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.fastingSettings by preferencesDataStore(name = "dot_fasting_settings")

class SettingsStore(
    context: Context,
    private val store: DataStore<Preferences> = context.applicationContext.fastingSettings,
    private val refreshEffects: suspend () -> Unit = { FastingRepository(context.applicationContext).reconcile() },
) {

    val settings: Flow<AppSettings> = store.data.map {
        AppSettings(
            highlightArgb = it[HIGHLIGHT] ?: 0xFFE8343A,
            lastGoalMillis = it[LAST_GOAL],
            notificationsEnabled = it[NOTIFICATIONS] ?: false,
            glyphEnabled = it[GLYPH] ?: false,
            countDown = it[COUNT_DOWN] ?: false,
        )
    }

    suspend fun setHighlightArgb(argb: Long) {
        if (argb !in 0xFF000000..0xFFFFFFFF) throw FastingException.InvalidHighlight()
        store.edit { it[HIGHLIGHT] = argb }
        refresh()
    }

    suspend fun setLastGoalMillis(goalMillis: Long?) {
        rememberGoal(goalMillis)
        refresh()
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        store.edit { it[NOTIFICATIONS] = enabled }
        refresh()
    }

    suspend fun setGlyphEnabled(enabled: Boolean) {
        store.edit { it[GLYPH] = enabled }
        refresh()
    }

    suspend fun setCountDown(enabled: Boolean) {
        store.edit { it[COUNT_DOWN] = enabled }
        refresh()
    }

    internal suspend fun rememberGoal(goalMillis: Long?) {
        goalMillis?.let(::validateGoal)
        store.edit {
            if (goalMillis == null) it.remove(LAST_GOAL) else it[LAST_GOAL] = goalMillis
        }
    }

    private suspend fun refresh() {
        refreshEffects()
    }

    private companion object {
        val HIGHLIGHT = longPreferencesKey("highlight_argb")
        val LAST_GOAL = longPreferencesKey("last_goal_millis")
        val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val GLYPH = booleanPreferencesKey("glyph_enabled")
        val COUNT_DOWN = booleanPreferencesKey("count_down")
    }
}
