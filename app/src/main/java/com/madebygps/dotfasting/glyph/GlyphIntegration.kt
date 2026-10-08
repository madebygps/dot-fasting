package com.madebygps.dotfasting.glyph

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.madebygps.dotfasting.data.FastingRepository
import com.madebygps.dotfasting.data.SettingsStore
import com.madebygps.dotfasting.domain.project
import com.madebygps.dotfasting.system.AndroidFastingClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

enum class GlyphSetupResult {
    MANAGER_OPENED,
    MANUAL_STEPS_REQUIRED,
}

class GlyphContext(context: Context) {
    private val appContext = context.applicationContext
    private val repository = FastingRepository(appContext)
    private val settings = SettingsStore(appContext)
    private val clock = AndroidFastingClock(appContext)
    private val refresh = MutableStateFlow(0L)

    val enabled: Flow<Boolean> = settings.settings.map { it.glyphEnabled }
    val fastState: Flow<GlyphFastState> = combine(repository.sessions, refresh) { sessions, _ ->
        val active = sessions.firstOrNull { it.endEpochMillis == null }
            ?: return@combine GlyphFastState.idle()
        val clockSnapshot = clock.snapshot()
        val projection = project(active, clockSnapshot)
        GlyphFastState(
            active = true,
            elapsedMillis = projection.elapsedMillis,
            goalMillis = active.goalMillis,
            sampledAtElapsedRealtime = clockSnapshot.elapsedRealtimeMillis,
            needsTimeReview = projection.needsTimeReview,
        )
    }

    fun refresh() {
        refresh.update { it + 1 }
    }
}

internal interface GlyphPlatform {
    fun capability(context: Context, enabled: Boolean): GlyphCapability
}

object GlyphIntegration {
    private const val TOYS_MANAGER_PACKAGE = "com.nothing.thirdparty"
    private const val TOYS_MANAGER_ACTIVITY =
        "com.nothing.thirdparty.matrix.toys.manager.ToysManagerActivity"

    @Volatile
    private var serviceAvailable: Boolean? = null

    fun capability(context: Context, enabled: Boolean): GlyphCapability {
        val result = GlyphPlatformAdapter.capability(context.applicationContext, enabled)
        return result.withServiceAvailability(serviceAvailable)
    }

    fun context(context: Context): GlyphContext = GlyphContext(context.applicationContext)

    fun openSetup(context: Context): GlyphSetupResult {
        val intent = Intent().setComponent(ComponentName(TOYS_MANAGER_PACKAGE, TOYS_MANAGER_ACTIVITY))
        if (intent.resolveActivity(context.packageManager) == null) {
            return GlyphSetupResult.MANUAL_STEPS_REQUIRED
        }
        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            GlyphSetupResult.MANAGER_OPENED
        } catch (_: ActivityNotFoundException) {
            GlyphSetupResult.MANUAL_STEPS_REQUIRED
        }
    }

    internal fun reportServiceAvailability(available: Boolean?) {
        serviceAvailable = available
    }
}
