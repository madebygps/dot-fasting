package com.madebygps.dotfasting.widget

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object WidgetTime {
    fun duration(millis: Long): String {
        require(millis >= 0) { "Duration cannot be negative" }
        val minutes = millis / 60_000
        return "${minutes / 60}h ${minutes % 60}m"
    }

    fun clock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    fun timestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("MMM d HH:mm").format(Instant.ofEpochMilli(epochMillis).atZone(zone))
}
