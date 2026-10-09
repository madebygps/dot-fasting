package com.madebygps.dotfasting.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.madebygps.dotfasting.system.RefreshCoordinator
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetRefresh {
    private const val WORK_NAME = "dot_fasting_widget_refresh"

    suspend fun update(context: Context) {
        val widgets = AppWidgetManager.getInstance(context)
        val instances = WidgetVariant.entries.associateWith { variant ->
            widgets.getAppWidgetIds(ComponentName(context, variant.provider))
        }
        if (instances.values.all(IntArray::isEmpty)) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            WidgetMinuteRefresh.cancel(context)
            return
        }
        val state = try {
            readWidgetState(context)
        } catch (error: IOException) {
            instances.forEach { (variant, ids) ->
                widgets.updateAppWidget(ids, widgetViews(context, WidgetState(WidgetStatus.UNAVAILABLE), variant))
            }
            WidgetMinuteRefresh.cancel(context)
            throw error
        } catch (error: SQLiteException) {
            instances.forEach { (variant, ids) ->
                widgets.updateAppWidget(ids, widgetViews(context, WidgetState(WidgetStatus.UNAVAILABLE), variant))
            }
            WidgetMinuteRefresh.cancel(context)
            throw error
        }
        instances.forEach { (variant, ids) ->
            widgets.updateAppWidget(ids, widgetViews(context, state, variant))
        }
        val manager = WorkManager.getInstance(context)
        if (state.active) {
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            manager.cancelUniqueWork(WORK_NAME)
        }
        if (state.status == WidgetStatus.RUNNING) {
            WidgetMinuteRefresh.schedule(context, state.counterMillis, state.countDown)
        } else {
            WidgetMinuteRefresh.cancel(context)
        }
    }
}

class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        WidgetRefresh.update(applicationContext)
        Result.success()
    } catch (error: IOException) {
        failed(error)
    } catch (error: SQLiteException) {
        failed(error)
    }

    private fun failed(error: Exception): Result {
        Log.e("DotFastingWidget", "Widget refresh failed (attempt $runAttemptCount)", error)
        return if (runAttemptCount < 3) Result.retry()
        else Result.failure(workDataOf("error" to "Widget data unavailable; open Dot Fasting"))
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                RefreshCoordinator.ACTION_REFRESH,
                WidgetMinuteRefresh.ACTION_REFRESH,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRefresh.update(context.applicationContext)
            } catch (error: IOException) {
                Log.e("DotFastingWidget", "Cannot refresh widget; storage unavailable", error)
            } catch (error: SQLiteException) {
                Log.e("DotFastingWidget", "Cannot refresh widget; database unavailable", error)
            } finally {
                pending.finish()
            }
        }
    }
}
