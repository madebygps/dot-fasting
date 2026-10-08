package com.madebygps.dotfasting.glyph

import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import com.madebygps.dotfasting.system.RefreshCoordinator
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class NothingFastToyService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceMessenger = Messenger(Handler(Looper.getMainLooper(), ::handleToyMessage))
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Unable to read persisted Glyph state", error)
            dataReadFailed = true
            enabled = false
            render()
            stopMinuteUpdate()
            GlyphIntegration.reportServiceAvailability(false)
        },
    )
    private var stateContext: GlyphContext? = null
    private var receiverRegistered = false
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stateContext?.refresh()
        }
    }
    private var manager: GlyphMatrixManager? = null
    private var view = GlyphView.PROGRESS
    private var latestState = GlyphFastState.idle()
    private var enabled = false
    private var aodMode = false
    private var managerConnected = false
    private var dataReadFailed = false
    private var bindingGeneration = 0
    private var frameObserver: Job? = null
    private var enabledObserver: Job? = null
    private val frameChanges = GlyphFrameChanges()
    private var connectionTimeout: Runnable? = null

    override fun onBind(intent: Intent?): IBinder {
        initialize()
        return serviceMessenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return false
    }

    override fun onDestroy() {
        release()
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun initialize() {
        if (stateContext != null) return
        if (!GlyphPlatformAdapter.supportsHardware()) {
            GlyphIntegration.reportServiceAvailability(false)
            return
        }
        val generation = ++bindingGeneration
        val stateContext = GlyphIntegration.context(applicationContext)
        this.stateContext = stateContext
        registerReceiver(
            refreshReceiver,
            IntentFilter(RefreshCoordinator.ACTION_REFRESH),
            Context.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        enabledObserver = serviceScope.launch {
            stateContext.enabled.collect {
                enabled = it
                render()
                if (it) scheduleMinuteUpdate() else stopMinuteUpdate()
            }
        }
        frameObserver = serviceScope.launch {
            stateContext.fastState.collect {
                latestState = it
                render()
                if (it.active) scheduleMinuteUpdate() else stopMinuteUpdate()
            }
        }

        val glyphManager = GlyphMatrixManager.getInstance(applicationContext)
        if (glyphManager == null) {
            Log.e(TAG, "Glyph Matrix manager is unavailable")
            GlyphIntegration.reportServiceAvailability(false)
            return
        }
        manager = glyphManager
        GlyphIntegration.reportServiceAvailability(false)
        val timeout = Runnable {
            if (manager === glyphManager && generation == bindingGeneration) {
                Log.e(TAG, "Glyph Matrix service connection timed out")
                GlyphIntegration.reportServiceAvailability(false)
            }
        }
        connectionTimeout = timeout
        handler.postDelayed(timeout, SERVICE_CONNECTION_TIMEOUT_MILLIS)
        sdkCall {
            glyphManager.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    if (manager !== glyphManager || generation != bindingGeneration) return
                    connectionTimeout?.let(handler::removeCallbacks)
                    connectionTimeout = null
                    if (!sdkCall { glyphManager.register(Glyph.DEVICE_23112) }) return
                    managerConnected = true
                    GlyphIntegration.reportServiceAvailability(!dataReadFailed)
                    aodMode = false
                    render()
                    scheduleMinuteUpdate()
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    if (manager === glyphManager && generation == bindingGeneration) {
                        managerConnected = false
                        GlyphIntegration.reportServiceAvailability(false)
                        stopMinuteUpdate()
                        frameChanges.reset()
                    }
                }
            })
        }
    }

    private fun release() {
        bindingGeneration++
        connectionTimeout?.let(handler::removeCallbacks)
        connectionTimeout = null
        stopMinuteUpdate()
        frameObserver?.cancel()
        frameObserver = null
        enabledObserver?.cancel()
        enabledObserver = null
        if (receiverRegistered) {
            unregisterReceiver(refreshReceiver)
            receiverRegistered = false
        }
        stateContext = null
        manager?.let { activeManager -> sdkCall { activeManager.unInit() } }
        manager = null
        managerConnected = false
        frameChanges.reset()
        enabled = false
        dataReadFailed = false
        latestState = GlyphFastState.idle()
        aodMode = false
        GlyphIntegration.reportServiceAvailability(null)
    }

    private fun handleToyMessage(message: Message): Boolean {
        if (message.what != GlyphToy.MSG_GLYPH_TOY) return false
        val event = message.data.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
        when (event) {
            GlyphToy.EVENT_CHANGE -> {
                view = view.next()
                stateContext?.refresh()
                render()
            }
            GlyphToy.EVENT_AOD -> {
                aodMode = true
                stopMinuteUpdate()
                stateContext?.refresh()
            }
        }
        return true
    }

    private fun scheduleMinuteUpdate() {
        stopMinuteUpdate()
        if (aodMode || manager == null || !managerConnected || !enabled ||
            dataReadFailed || !latestState.active
        ) return
        val delay = MINUTE_MILLIS - (SystemClock.elapsedRealtime() % MINUTE_MILLIS)
        handler.postDelayed(minuteUpdate, delay)
    }

    private val minuteUpdate = Runnable {
        stateContext?.refresh()
    }

    private fun stopMinuteUpdate() {
        handler.removeCallbacks(minuteUpdate)
    }

    private fun render() {
        val activeManager = manager ?: return
        if (!managerConnected) return
        val current = if (enabled && !dataReadFailed) {
            GlyphFrameRenderer.render(view, latestState, SystemClock.elapsedRealtime())
        } else {
            GlyphFrame(BooleanArray(GlyphFrameRenderer.SIZE * GlyphFrameRenderer.SIZE))
        }
        if (frameChanges.differs(current)) {
            val pixels = current.toBooleanArray()
            val nextFrame = IntArray(pixels.size) { index -> if (pixels[index]) GLYPH_ON else GLYPH_OFF }
            if (sdkCall { activeManager.setMatrixFrame(nextFrame) }) {
                frameChanges.markDisplayed(current)
            }
        }
    }

    private fun sdkCall(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (error: RuntimeException) {
        Log.e(TAG, "Glyph Matrix SDK operation failed", error)
        managerConnected = false
        frameChanges.reset()
        stopMinuteUpdate()
        GlyphIntegration.reportServiceAvailability(false)
        false
    }

    private companion object {
        const val TAG = "DotFastingGlyph"
        const val GLYPH_ON = -1
        const val GLYPH_OFF = 0
        const val MINUTE_MILLIS = 60_000L
        const val SERVICE_CONNECTION_TIMEOUT_MILLIS = 5_000L
    }
}
