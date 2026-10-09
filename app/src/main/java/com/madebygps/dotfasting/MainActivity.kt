package com.madebygps.dotfasting

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.madebygps.dotfasting.domain.FastProjection
import com.madebygps.dotfasting.glyph.GlyphCapabilityState
import com.madebygps.dotfasting.glyph.GlyphIntegration
import com.madebygps.dotfasting.glyph.GlyphSetupResult
import com.madebygps.dotfasting.system.GoalNotificationCapability
import com.madebygps.dotfasting.system.GoalNotifications
import com.madebygps.dotfasting.ui.DotFastingApp
import com.madebygps.dotfasting.ui.FastingViewModel
import com.madebygps.dotfasting.ui.GlyphUiCapability
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: FastingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[FastingViewModel::class.java]
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val active = state.sessions.firstOrNull { it.endEpochMillis == null }
            var projection by remember { mutableStateOf<FastProjection?>(null) }
            var notificationCapability by remember { mutableStateOf(GoalNotifications(this).capability()) }
            var capability by remember {
                mutableStateOf(GlyphIntegration.capability(this@MainActivity, state.settings.glyphEnabled))
            }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notificationCapability = GoalNotifications(this).capability()
                if (granted && notificationCapability == GoalNotificationCapability.AVAILABLE) viewModel.notifications(true)
                else if (granted) viewModel.showMessage("Notifications are blocked in Android settings. Enable Dot Fasting notifications there, then try again.")
                else viewModel.showMessage("Goal notification stays off: notification permission was not granted. You can enable permission in Android app settings.")
            }
            DisposableEffect(Unit) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationCapability = GoalNotifications(this@MainActivity).capability()
                        capability = GlyphIntegration.capability(this@MainActivity, viewModel.state.value.settings.glyphEnabled)
                        viewModel.reconcile()
                    } else if (event == Lifecycle.Event.ON_STOP) {
                        viewModel.reconcile()
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(state.settings.glyphEnabled) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        capability = GlyphIntegration.capability(this@MainActivity, state.settings.glyphEnabled)
                        delay(5000L)
                    }
                }
            }
            LaunchedEffect(active) {
                projection = null
                if (active != null) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) {
                            projection = viewModel.sample(active)
                            delay(1000L)
                        }
                    }
                }
            }
            val glyphUi = when (capability.state) {
                GlyphCapabilityState.SDK_NOT_INCLUDED -> GlyphUiCapability(
                    false, "Glyph SDK not included",
                    "This build works without the optional Nothing SDK. A separate SDK-enabled build and supported Phone (3) are required for Glyph Toys.",
                    showInSettings = false,
                )
                GlyphCapabilityState.UNSUPPORTED_HARDWARE -> GlyphUiCapability(
                    false, "Unsupported hardware", "Glyph Matrix Toys require a supported Nothing Phone (3).",
                    showInSettings = false,
                )
                GlyphCapabilityState.SERVICE_UNAVAILABLE -> GlyphUiCapability(
                    false, "Glyph service unavailable", "Nothing’s Glyph service is not available. Check system settings and try again.",
                )
                GlyphCapabilityState.DISABLED -> GlyphUiCapability(
                    true, "Off · opt in to enable", "Glyph Toy is optional and currently off.",
                )
                GlyphCapabilityState.SETUP_REQUIRED -> GlyphUiCapability(
                    true, "Enabled · select Dot Fasting in Toys",
                    "Open Glyph Toys settings and select Dot Fasting. Service availability is verified only while selected.",
                )
                GlyphCapabilityState.READY -> GlyphUiCapability(
                    true, "Glyph service connected", "Dot Fasting is connected to Nothing’s Glyph service.",
                )
            }
            DotFastingApp(
                sessions = state.sessions,
                projection = projection,
                highlightArgb = state.settings.highlightArgb,
                lastGoalMillis = state.settings.lastGoalMillis,
                notificationsEnabled = state.settings.notificationsEnabled,
                glyphEnabled = state.settings.glyphEnabled,
                countDown = state.settings.countDown,
                onCountDown = viewModel::countDown,
                glyphCapability = glyphUi,
                busy = state.busy || !state.ready,
                error = state.error,
                onDismissError = viewModel::dismissError,
                onStart = viewModel::start,
                onEnd = viewModel::end,
                onEdit = viewModel::edit,
                onDelete = viewModel::delete,
                onHighlight = viewModel::highlight,
                onNotifications = { enabled ->
                    notificationCapability = GoalNotifications(this@MainActivity).capability()
                    when {
                        !enabled -> viewModel.notifications(false)
                        notificationCapability == GoalNotificationCapability.AVAILABLE -> viewModel.notifications(true)
                        notificationCapability == GoalNotificationCapability.PERMISSION_REQUIRED -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> viewModel.showMessage("Goal notifications are blocked. Enable Dot Fasting notifications in Android Settings, then try again.")
                    }
                },
                onGlyph = viewModel::glyph,
                onGlyphSetup = {
                    try {
                        if (GlyphIntegration.openSetup(this@MainActivity) == GlyphSetupResult.MANUAL_STEPS_REQUIRED) {
                            viewModel.showMessage("Toys settings could not be opened. Open Android Settings → Glyph Interface → Glyph Toys, then select Dot Fasting.")
                        }
                    } catch (error: ActivityNotFoundException) {
                        viewModel.reportError(error)
                    } catch (error: SecurityException) {
                        viewModel.reportError(error)
                    }
                },
            )
        }
    }

}
