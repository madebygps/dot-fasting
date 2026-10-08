package com.madebygps.dotfasting.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.madebygps.dotfasting.domain.DotArt
import com.madebygps.dotfasting.domain.RingGeometry

object CounterBitmaps {
    private val flame = listOf(
        "00000100000",
        "00000110000",
        "00001110000",
        "00011110000",
        "00011111000",
        "00111111010",
        "01111111110",
        "01111111111",
        "11111011111",
        "11110001111",
        "01100000110",
        "01110001110",
        "00111111100",
        "00011111000",
        "00001110000",
    )

    fun draw(
        time: String,
        width: Int = 640,
        progress: Float = 0f,
        highlight: Int = Color.rgb(232, 52, 58),
        idleFlame: Boolean = false,
    ): Bitmap {
        require(width > 0) { "Counter width must be positive" }
        val columns = time.length * 4 - 1
        val cell = width * 0.585f / columns.coerceAtLeast(1)
        val bitmap = createBitmap(width, width)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width * RingGeometry.STROKE_FRACTION
            color = Color.rgb(38, 38, 38)
        }
        val inset = paint.strokeWidth / 2 + 1f
        val bounds = RectF(inset, inset, width - inset, width - inset)
        canvas.drawOval(bounds, paint)
        paint.color = highlight
        val sweep = RingGeometry.sweep(progress)
        if (sweep > 0) canvas.drawArc(bounds, RingGeometry.START_DEGREES, sweep, false, paint)
        paint.apply {
            style = Paint.Style.FILL
            color = Color.rgb(242, 242, 242)
        }
        if (idleFlame) {
            val flameCell = width * 0.36f / flame.size
            val flameLeft = (width - flame.first().length * flameCell) / 2
            val flameTop = (width - flame.size * flameCell) / 2
            flame.forEachIndexed { y, row ->
                row.forEachIndexed { x, pixel ->
                    if (pixel == '1') canvas.drawCircle(
                        flameLeft + (x + 0.5f) * flameCell,
                        flameTop + (y + 0.5f) * flameCell,
                        flameCell * 0.42f,
                        paint,
                    )
                }
            }
            return bitmap
        }
        val left = (width - columns * cell) / 2
        val top = (width - 5 * cell) / 2
        var column = 0
        time.forEach { character ->
            DotArt.digits[character]?.forEachIndexed { y, row ->
                row.forEachIndexed { x, pixel ->
                    if (pixel == '1') canvas.drawCircle(
                        left + (column + x + 0.5f) * cell, top + (y + 0.5f) * cell, cell * 0.42f, paint,
                    )
                }
            }
            column += 4
        }
        return bitmap
    }
}
