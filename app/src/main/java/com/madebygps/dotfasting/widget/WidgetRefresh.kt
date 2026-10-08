package com.madebygps.dotfasting.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetRefresh {
    private const val WORK_NAME = "dot_fasting_widget_refresh"

    suspend fun update(context: Context) {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(FastingWidget::class.java)
        if (ids.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        val active = readWidgetSnapshot(context).active
        FastingWidget().updateAll(context)
        val manager = WorkManager.getInstance(context)
        if (active) {
            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).build(),
            )
        } else {
            manager.cancelUniqueWork(WORK_NAME)
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
        if (intent.action != "com.madebygps.dotfasting.REFRESH") return
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
