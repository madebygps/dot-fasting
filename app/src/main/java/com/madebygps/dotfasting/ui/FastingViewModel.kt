package com.madebygps.dotfasting.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.madebygps.dotfasting.data.FastingRepository
import com.madebygps.dotfasting.data.SettingsStore
import com.madebygps.dotfasting.domain.AppSettings
import com.madebygps.dotfasting.domain.FastProjection
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.project
import com.madebygps.dotfasting.system.AndroidFastingClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FastingUiState(
    val sessions: List<FastSession> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val sessionsLoaded: Boolean = false,
    val settingsLoaded: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val ready: Boolean get() = sessionsLoaded && settingsLoaded
}

class FastingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FastingRepository(application)
    private val settings = SettingsStore(application)
    private val clock = AndroidFastingClock(application)
    private val mutableState = MutableStateFlow(FastingUiState())
    val state: StateFlow<FastingUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessions.catch { reportError(it) }.collect { records ->
                mutableState.update { it.copy(sessions = records, sessionsLoaded = true) }
            }
        }
        viewModelScope.launch {
            settings.settings.catch { reportError(it) }.collect { preferences ->
                mutableState.update { it.copy(settings = preferences, settingsLoaded = true) }
            }
        }
    }

    fun start(goalMillis: Long) = mutate { repository.start(goalMillis) }
    fun end() = mutate { repository.end() }
    fun edit(id: Long, start: Long, end: Long?) = mutate { repository.edit(id, start, end) }
    fun highlight(argb: Long) = mutate { settings.setHighlightArgb(argb) }
    fun notifications(enabled: Boolean) = mutate { settings.setNotificationsEnabled(enabled) }
    fun glyph(enabled: Boolean) = mutate { settings.setGlyphEnabled(enabled) }
    fun countDown(enabled: Boolean) = mutate { settings.setCountDown(enabled) }

    fun reconcile() {
        viewModelScope.launch {
            try {
                repository.reconcile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            }
        }
    }

    fun sample(session: FastSession): FastProjection? = try {
        project(session, clock.snapshot())
    } catch (error: Exception) {
        reportError(error)
        null
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun reportError(error: Throwable) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(error = error.message ?: "This operation failed. Please try again.") }
    }

    fun showMessage(message: String) = mutableState.update { it.copy(error = message) }

    private fun mutate(operation: suspend () -> Unit) {
        if (mutableState.value.busy || !mutableState.value.ready) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
