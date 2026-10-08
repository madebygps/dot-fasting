package com.madebygps.dotfasting.widget

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.appwidget.AppWidgetManager
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.madebygps.dotfasting.MainActivity
import java.io.IOException

data class WidgetSnapshot(
    val active: Boolean,
    val description: String,
    val time: String = "00:00",
    val progress: Float = 0f,
    val highlight: Int = 0xFFE8343A.toInt(),
    val idleFlame: Boolean = false,
)

class FastingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = try {
            readWidgetSnapshot(context)
        } catch (error: IOException) {
            unavailable(error)
        } catch (error: SQLiteException) {
            unavailable(error)
        }
        val counter = CounterBitmaps.draw(
            snapshot.time, progress = snapshot.progress, highlight = snapshot.highlight,
            idleFlame = snapshot.idleFlame,
        )
        provideContent {
            val size = LocalSize.current
            val counterWidth = (size.width.value - 20f).coerceAtLeast(1f)
                .coerceAtMost((size.height.value - 20f).coerceAtLeast(1f) * counter.width / counter.height)
            Box(
                modifier = GlanceModifier.fillMaxSize().background(Color.Black)
                    .cornerRadius(28.dp).padding(10.dp)
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(counter),
                    contentDescription = snapshot.description,
                    modifier = GlanceModifier.size(counterWidth.dp, (counterWidth * counter.height / counter.width).dp),
                )
            }
        }
    }

    private fun unavailable(error: Exception): WidgetSnapshot {
        Log.e("DotFastingWidget", "Cannot read fasting state", error)
        return WidgetSnapshot(false, "Timer unavailable. Open Dot Fasting.", "—")
    }
}

class FastingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = FastingWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        refresh(context)
    }

    private fun refresh(context: Context) {
        context.sendBroadcast(
            Intent("com.madebygps.dotfasting.REFRESH").setPackage(context.packageName),
        )
    }
}
