package com.madebygps.dotfasting.domain

object RingGeometry {
    const val START_DEGREES = -90f
    const val STROKE_FRACTION = 0.075f
    const val ICON_FRACTION = 0.40f

    fun sweep(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f) * 360f
}
