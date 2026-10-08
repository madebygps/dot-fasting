package com.madebygps.dotfasting.glyph

import kotlin.math.atan2
import kotlin.math.sqrt

enum class GlyphView {
    PROGRESS,
    ELAPSED,
    REMAINING;

    fun next(): GlyphView = when (this) {
        PROGRESS -> ELAPSED
        ELAPSED -> REMAINING
        REMAINING -> PROGRESS
    }
}

data class GlyphFastState(
    val active: Boolean,
    val elapsedMillis: Long,
    val goalMillis: Long,
    val sampledAtElapsedRealtime: Long,
    val needsTimeReview: Boolean = false,
) {
    init {
        require(elapsedMillis >= 0L)
        require(sampledAtElapsedRealtime >= 0L)
        require(!active || goalMillis > 0L)
    }

    fun elapsedAt(nowElapsedRealtime: Long): Long {
        if (!active || needsTimeReview || nowElapsedRealtime <= sampledAtElapsedRealtime) {
            return elapsedMillis.coerceAtLeast(0L)
        }
        val additional = nowElapsedRealtime - sampledAtElapsedRealtime
        return if (Long.MAX_VALUE - elapsedMillis.coerceAtLeast(0L) < additional) {
            Long.MAX_VALUE
        } else {
            elapsedMillis.coerceAtLeast(0L) + additional
        }
    }

    companion object {
        fun idle() = GlyphFastState(
            active = false,
            elapsedMillis = 0L,
            goalMillis = 0L,
            sampledAtElapsedRealtime = 0L,
        )
    }
}

class GlyphFrame internal constructor(
    private val pixels: BooleanArray,
) {
    fun isLit(x: Int, y: Int): Boolean {
        require(x in 0 until GlyphFrameRenderer.SIZE && y in 0 until GlyphFrameRenderer.SIZE)
        return pixels[y * GlyphFrameRenderer.SIZE + x]
    }

    fun toBooleanArray(): BooleanArray = pixels.copyOf()

    override fun equals(other: Any?): Boolean =
        other is GlyphFrame && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = pixels.contentHashCode()
}

class GlyphFrameChanges {
    private var previous: GlyphFrame? = null

    fun differs(frame: GlyphFrame): Boolean = previous != frame

    fun markDisplayed(frame: GlyphFrame) {
        previous = frame
    }

    fun reset() {
        previous = null
    }
}

object GlyphFrameRenderer {
    const val SIZE = 25
    private const val CENTER = SIZE / 2
    private const val RING_RADIUS = 10.0
    private const val RING_TOLERANCE = 0.58
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L

    fun render(view: GlyphView, state: GlyphFastState, nowElapsedRealtime: Long): GlyphFrame {
        val pixels = BooleanArray(SIZE * SIZE)
        if (!state.active) {
            drawIdle(pixels)
            return GlyphFrame(pixels)
        }

        val elapsed = state.elapsedAt(nowElapsedRealtime)
        val goal = state.goalMillis.coerceAtLeast(0L)
        val progress = if (goal == 0L) 1.0 else (elapsed.toDouble() / goal).coerceIn(0.0, 1.0)
        if (state.needsTimeReview) {
            drawText(pixels, "CHK")
            return GlyphFrame(pixels)
        }

        when (view) {
            GlyphView.PROGRESS -> {
                drawRing(pixels, progress)
                drawText(pixels, progressPercent(progress).toString().padStart(2, '0'))
            }
            GlyphView.ELAPSED -> drawTime(pixels, elapsed)
            GlyphView.REMAINING -> {
                if (elapsed >= goal) {
                    drawText(pixels, "MET")
                } else {
                    drawTime(pixels, goal - elapsed)
                }
            }
        }
        return GlyphFrame(pixels)
    }

    private fun progressPercent(progress: Double): Int = (progress * 100.0).toInt().coerceIn(0, 100)

    private fun drawTime(pixels: BooleanArray, durationMillis: Long) {
        val totalMinutes = durationMillis.coerceAtLeast(0L) / MILLIS_PER_MINUTE
        val hours = totalMinutes / MINUTES_PER_HOUR
        val minutes = totalMinutes % MINUTES_PER_HOUR
        if (hours < 100) {
            drawText(pixels, "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}")
        } else {
            // Keep arbitrarily long valid goals exact: total minutes, split into two rows.
            drawText(pixels, "MIN", 1)
            val digits = totalMinutes.toString().chunked(6)
            digits.forEachIndexed { row, text -> drawText(pixels, text, 7 + row * 6) }
        }
    }

    private fun drawRing(pixels: BooleanArray, progress: Double) {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val dx = x - CENTER
                val dy = y - CENTER
                val distance = sqrt((dx * dx + dy * dy).toDouble())
                if (kotlin.math.abs(distance - RING_RADIUS) <= RING_TOLERANCE) {
                    val angle = ((atan2(dx.toDouble(), -dy.toDouble()) / (2.0 * Math.PI)) + 1.0) % 1.0
                    if (progress >= 1.0 || angle < progress) {
                        pixels[y * SIZE + x] = true
                    }
                }
            }
        }
    }

    private fun drawText(pixels: BooleanArray, text: String, startY: Int = CENTER - 2) {
        val glyphWidth = 3
        val spacing = 1
        val width = text.length * (glyphWidth + spacing) - spacing
        val startX = (SIZE - width) / 2
        text.forEachIndexed { index, char ->
            val rows = glyphs[char] ?: glyphs['-']!!
            rows.forEachIndexed { row, bits ->
                for (column in 0 until glyphWidth) {
                    if (bits and (1 shl (glyphWidth - column - 1)) != 0) {
                        val x = startX + index * (glyphWidth + spacing) + column
                        val y = startY + row
                        if (x in 0 until SIZE && y in 0 until SIZE) {
                            pixels[y * SIZE + x] = true
                        }
                    }
                }
            }
        }
    }

    private fun drawIdle(pixels: BooleanArray) {
        val idle = arrayOf(
            "00100",
            "01110",
            "11111",
            "01110",
            "00100",
        )
        idle.forEachIndexed { y, row ->
            row.forEachIndexed { x, pixel ->
                if (pixel == '1') {
                    pixels[(CENTER - 2 + y) * SIZE + CENTER - 2 + x] = true
                }
            }
        }
    }

    private val glyphs = mapOf(
        '0' to intArrayOf(7, 5, 5, 5, 7),
        '1' to intArrayOf(2, 6, 2, 2, 7),
        '2' to intArrayOf(7, 1, 7, 4, 7),
        '3' to intArrayOf(7, 1, 7, 1, 7),
        '4' to intArrayOf(5, 5, 7, 1, 1),
        '5' to intArrayOf(7, 4, 7, 1, 7),
        '6' to intArrayOf(7, 4, 7, 5, 7),
        '7' to intArrayOf(7, 1, 1, 1, 1),
        '8' to intArrayOf(7, 5, 7, 5, 7),
        '9' to intArrayOf(7, 5, 7, 1, 7),
        'C' to intArrayOf(7, 4, 4, 4, 7),
        'H' to intArrayOf(5, 5, 7, 5, 5),
        'K' to intArrayOf(5, 5, 6, 5, 5),
        'M' to intArrayOf(5, 7, 7, 5, 5),
        'E' to intArrayOf(7, 4, 6, 4, 7),
        'T' to intArrayOf(7, 2, 2, 2, 2),
        'I' to intArrayOf(7, 2, 2, 2, 7),
        'N' to intArrayOf(5, 7, 7, 7, 5),
        ':' to intArrayOf(0, 2, 0, 2, 0),
        '-' to intArrayOf(0, 0, 7, 0, 0),
    )
}
