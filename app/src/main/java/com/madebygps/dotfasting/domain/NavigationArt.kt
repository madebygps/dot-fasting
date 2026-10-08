package com.madebygps.dotfasting.domain

import kotlin.math.cos
import kotlin.math.sin

data class IconDot(val x: Float, val y: Float, val radius: Float)

object NavigationArt {
    // Settings geometry adapted from Dot Habits' MIT-licensed HomeScreen.BarButton.
    fun settings(): List<IconDot> = (0 until 12).map { index ->
        val angle = Math.toRadians(index * 30.0)
        IconDot(0.5f + (0.38f * cos(angle)).toFloat(), 0.5f + (0.38f * sin(angle)).toFloat(), 0.55f / 9)
    } + IconDot(0.5f, 0.5f, 0.9f / 9)

    fun calendar(): List<IconDot> {
        val points = buildSet {
            for (x in 1..7) { add(x to 2); add(x to 4); add(x to 8) }
            for (y in 2..8) { add(1 to y); add(7 to y) }
            add(3 to 1); add(5 to 1)
            for (x in listOf(3, 5)) for (y in listOf(6, 7)) add(x to y)
        }
        return points.map { (x, y) -> IconDot((x + 0.5f) / 9, (y + 0.5f) / 9, 0.45f / 9) }
    }
}
