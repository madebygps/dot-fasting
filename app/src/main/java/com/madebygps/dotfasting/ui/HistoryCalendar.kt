package com.madebygps.dotfasting.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.calendarCells
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryMonth(
    month: YearMonth,
    selectedDate: LocalDate,
    days: Map<LocalDate, List<FastSession>>,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.semantics { contentDescription = "Previous month" }) { Text("<") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DotLabel(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)).uppercase(Locale.ENGLISH))
            }
            TextButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.semantics { contentDescription = "Next month" }) { Text(">") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { DotCaption(it) }
            }
        }
        calendarCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (date == null) Box(Modifier.size(48.dp)) else {
                            val records = days[date].orEmpty()
                            val completed = records.count { it.endEpochMillis != null }
                            val active = records.any { it.endEpochMillis == null }
                            Box(
                                Modifier.size(48.dp)
                                    .semantics {
                                        contentDescription = "$date, $completed completed fasts${if (active) ", active fast" else ""}"
                                        selected = selectedDate == date
                                    }
                                    .clickable(role = Role.Button) { onSelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(32.dp)
                                        .background(if (completed > 0) accent else Color.Transparent, CircleShape)
                                        .border(
                                            if (selectedDate == date || active || date == LocalDate.now()) 1.dp else 0.dp,
                                            if (selectedDate == date) DotColors.Text else if (active) accent
                                            else if (date == LocalDate.now()) DotColors.Dim else Color.Transparent,
                                            CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (completed > 0) DotColors.Background else DotColors.Text,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
