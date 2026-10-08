package com.madebygps.dotfasting.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.madebygps.dotfasting.domain.FastSession
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun timestampText(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).let { time ->
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(time)
    }

fun dateText(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun sessionTimeText(session: FastSession, zone: ZoneId = ZoneId.systemDefault()): String {
    val start = Instant.ofEpochMilli(session.startEpochMillis).atZone(zone)
    val end = session.endEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    val startText = if (end != null && start.toLocalDate() != end.toLocalDate()) {
        start.format(DateTimeFormatter.ofPattern("MMM d")) + ", " + time.format(start)
    } else time.format(start)
    return "$startText – ${end?.let(time::format) ?: "now"}"
}

@Composable
fun TimestampEditor(session: FastSession, busy: Boolean, onDismiss: () -> Unit, onSave: (Long, Long?) -> Unit) {
    val context = LocalContext.current
    var start by remember(session.id) { mutableLongStateOf(session.startEpochMillis) }
    var end by remember(session.id) { mutableStateOf(session.endEpochMillis) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (session.endEpochMillis == null) "Edit start time" else "Correct timestamps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DotLabel("START")
                OutlinedButton(enabled = !busy, onClick = {
                    pickTimestamp(context, start, { start = it; error = null }, { error = it })
                }) { Text(timestampText(start)) }
                end?.let { value ->
                    DotLabel("END")
                    OutlinedButton(enabled = !busy, onClick = {
                        pickTimestamp(context, value, { end = it; error = null }, { error = it })
                    }) { Text(timestampText(value)) }
                }
                Text("Local time (${ZoneId.systemDefault().id}). Times cannot be in the future or overlap another fast.")
                error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && error == null, onClick = {
                if (start > System.currentTimeMillis() || (end != null && end!! > System.currentTimeMillis())) {
                    error = "Choose a time that is not in the future."
                } else if (end != null && end!! < start) {
                    error = "End must be at or after start."
                } else onSave(start, end)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun pickTimestamp(context: Context, epochMillis: Long, onSelected: (Long) -> Unit, onError: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val original = Instant.ofEpochMilli(epochMillis).atZone(zone)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                val local = LocalDateTime.of(year, month + 1, day, hour, minute)
                val offsets = zone.rules.getValidOffsets(local)
                if (offsets.isEmpty()) {
                    onError("This local time does not exist because of a daylight-saving change. Choose another time.")
                } else {
                    // During the repeated DST hour preserve the record's offset when possible.
                    val offset = offsets.firstOrNull { it == original.offset } ?: offsets.first()
                    onSelected(local.toInstant(offset).toEpochMilli())
                }
            }, original.hour, original.minute, DateFormat.is24HourFormat(context)).show()
        },
        original.year, original.monthValue - 1, original.dayOfMonth,
    ).show()
}
