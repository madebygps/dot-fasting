package com.madebygps.dotfasting.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.madebygps.dotfasting.domain.FastProjection
import com.madebygps.dotfasting.domain.FastSession
import com.madebygps.dotfasting.domain.counterMillis
import com.madebygps.dotfasting.domain.counterText
import com.madebygps.dotfasting.domain.historyDays
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class GlyphUiCapability(
    val available: Boolean,
    val status: String,
    val explanation: String,
)

/** Repository-free public screen API, also usable by previews and instrumented tests. */
@Composable
fun DotFastingApp(
    sessions: List<FastSession>,
    projection: FastProjection?,
    highlightArgb: Long,
    lastGoalMillis: Long?,
    notificationsEnabled: Boolean,
    notificationPermissionGranted: Boolean,
    notificationStatus: String,
    glyphEnabled: Boolean,
    glyphCapability: GlyphUiCapability,
    busy: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onStart: (Long) -> Unit,
    onEnd: () -> Unit,
    onEdit: (Long, Long, Long?) -> Unit,
    onDelete: (Long) -> Unit,
    onHighlight: (Long) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onNotificationSettings: () -> Unit,
    onGlyph: (Boolean) -> Unit,
    onGlyphSetup: () -> Unit,
    countDown: Boolean = false,
    onCountDown: (Boolean) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf("Home") }
    var goalPicker by rememberSaveable { mutableStateOf(false) }
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var help by rememberSaveable { mutableStateOf<String?>(null) }
    val active = sessions.firstOrNull { it.endEpochMillis == null }
    val detail = sessions.firstOrNull { it.id == detailId }
    fun back() { if (detailId != null) detailId = null else page = "Home" }
    LaunchedEffect(sessions, detailId) {
        if (detailId != null && detail == null) detailId = null
    }
    BackHandler(enabled = page != "Home" && !goalPicker && !confirmEnd && editingId == null && deletingId == null && help == null) { back() }

    DotFastingTheme(highlightArgb) {
        Scaffold(
            containerColor = DotColors.Background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (page == "Home") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { page = "History" }, modifier = Modifier.semantics { contentDescription = "History" }) {
                            NavigationIcon(calendar = true)
                        }
                        IconButton(onClick = { page = "Settings" }, modifier = Modifier.semantics { contentDescription = "Settings" }) {
                            NavigationIcon(calendar = false)
                        }
                    }
                }
            },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp)) {
                if (page != "Home") Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (detail != null) "Fast" else page,
                        Modifier.weight(1f), fontSize = 24.sp, color = DotColors.Text,
                    )
                    if (detail != null) TextButton(
                        enabled = !busy,
                        onClick = { editingId = detail.id },
                        modifier = Modifier.semantics { contentDescription = "Edit timestamps" },
                    ) { Text("EDIT") }
                }
                error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = DotColors.Raised)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(it, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onDismissError) { Text("Dismiss") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                when (page) {
                    "Home" -> Home(
                        active, projection, lastGoalMillis, busy, countDown,
                        onStart = { goalPicker = true },
                        onEnd = { confirmEnd = true },
                        onEdit = { editingId = it.id },
                    )
                    "History" -> if (detail != null) SessionDetail(
                        detail, if (detail.id == active?.id) projection else null, busy,
                        onDelete = { deletingId = detail.id },
                    )
                        else History(sessions) { detailId = it.id }
                    else -> Settings(
                        highlightArgb, notificationsEnabled, notificationPermissionGranted, notificationStatus, glyphEnabled,
                        glyphCapability, busy, onHighlight, onNotifications, onGlyph, onGlyphSetup,
                        onNotificationSettings = onNotificationSettings,
                        onHelp = { help = it }, countDown = countDown, onCountDown = onCountDown,
                    )
                }
            }
        }
        if (goalPicker) GoalPicker(lastGoalMillis, busy, { goalPicker = false }) { goal ->
            goalPicker = false
            onStart(goal)
        }
        if (confirmEnd && active != null) AlertDialog(
            onDismissRequest = { if (!busy) confirmEnd = false },
            title = { Text("End this fast?") },
            text = { Text("Save this session to History.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmEnd = false; onEnd() }) { Text("End fast") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmEnd = false }) { Text("Keep fasting") } },
        )
        sessions.firstOrNull { it.id == editingId }?.let { record ->
            TimestampEditor(record, busy, { editingId = null }) { start, end ->
                onEdit(record.id, start, end)
                editingId = null
            }
        }
        sessions.firstOrNull { it.id == deletingId }?.let { record ->
            AlertDialog(
                onDismissRequest = { if (!busy) deletingId = null },
                title = { Text("Delete this fast?") },
                text = {
                    Text(if (record.endEpochMillis == null)
                        "This removes the active timer and its record. This cannot be undone."
                    else "This permanently removes the record from History. This cannot be undone.")
                },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        deletingId = null
                        onDelete(record.id)
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("Cancel") }
                },
            )
        }
        help?.let { kind ->
            val text = when (kind) {
                else -> "TIMER\nCount up shows elapsed time; count down shows time remaining. Reaching the goal never ends a fast. Tap Started to edit its time, or open a History session to correct timestamps. All records stay on this device.\n\nNOTIFICATIONS\nOptional goal alerts require Android notification permission. Battery restrictions can delay delivery. Force-stopping prevents alerts until the app is reopened.\n\nWIDGET\nThe dotted counter and circle are snapshots, refreshed on changes and periodically while active. Android may delay updates. Tap to open the app.\n\nGLYPH\n${glyphCapability.explanation}\nSelect Dot Fasting in Nothing’s Glyph Toys settings after enabling it here. Long press switches progress, elapsed and remaining views; it never starts or ends a fast. Nothing OS controls availability and brightness.\n\nTIME CHANGES\nReview flagged timestamps after clock changes. Timezone changes affect display only."
            }
            AlertDialog(
                onDismissRequest = { help = null },
                title = { Text("About Dot Fasting") },
                text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = { TextButton(onClick = { help = null }) { Text("Got it") } },
            )
        }
    }
}

@Composable
private fun Home(
    active: FastSession?,
    projection: FastProjection?,
    lastGoalMillis: Long?,
    busy: Boolean,
    countDown: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onEdit: (FastSession) -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        Box(Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            ProgressRing(
                projection?.progress ?: 0f,
                when {
                    active == null -> "No active fast"
                    projection == null -> "Timing unavailable"
                    else -> "${(projection.progress * 100).toInt()} percent of goal"
                },
                Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DotTime(
                    if (active != null && projection == null) "—" else counterText(
                        projection?.let { counterMillis(it, countDown) }
                            ?: if (countDown) lastGoalMillis ?: 0L else 0L,
                    ),
                    Modifier.fillMaxWidth(0.72f).height(44.dp),
                )
                DotCaption(if (countDown) "REMAINING" else "ELAPSED")
            }
            TimerControl(
                active != null, !busy, if (active == null) onStart else onEnd,
                Modifier.align(Alignment.BottomEnd),
            )
        }
        when {
            projection?.needsTimeReview == true -> {
                Text("Time needs review", color = MaterialTheme.colorScheme.error)
                Text("Review the start time: the device clock changed.", color = DotColors.Muted)
            }
            active != null && projection == null -> Text("Waiting for verified timer data", color = DotColors.Muted)
        }
        if (active != null) {
            DotLabel("GOAL ${goalText(active.goalMillis)}")
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DotColors.Line)
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.Button) { onEdit(active) }
                    .padding(vertical = 12.dp).semantics { contentDescription = "Edit start time" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DotCaption("STARTED")
                    Text(timestampText(active.startEpochMillis), color = DotColors.Text, style = MaterialTheme.typography.bodyMedium)
                }
                DotLabel("EDIT")
            }
        } else {
            lastGoalMillis?.let { DotLabel("LAST GOAL ${goalText(it)}") }
        }
        Spacer(Modifier.height(12.dp))
        }
        }
    }
}

@Composable
private fun TimerControl(active: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        color = if (active) accent else DotColors.Background,
        border = BorderStroke(2.dp, if (enabled) accent else DotColors.Dim),
        modifier = modifier.size(54.dp).semantics {
            text = AnnotatedString(if (active) "End fast" else "Start fast")
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(54.dp * 0.38f)) {
                val ink = if (!enabled) DotColors.Dim else if (active) DotColors.Background else accent
                if (active) {
                    drawRect(ink)
                } else {
                    drawPath(Path().apply {
                        moveTo(size.width * 0.12f, 0f)
                        lineTo(size.width, size.height / 2)
                        lineTo(size.width * 0.12f, size.height)
                        close()
                    }, ink)
                }
            }
        }
    }
}

@Composable
private fun History(sessions: List<FastSession>, onOpen: (FastSession) -> Unit) {
    val zone = ZoneId.systemDefault()
    val days = historyDays(sessions, zone)
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var visibleMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedDate = LocalDate.parse(selectedDay)
    val month = YearMonth.parse(visibleMonth)
    val records = days[selectedDate].orEmpty()
    LazyColumn {
        item {
            HistoryMonth(month, selectedDate, days, onMonth = {
                visibleMonth = it.toString()
                selectedDay = it.atDay(selectedDate.dayOfMonth.coerceAtMost(it.lengthOfMonth())).toString()
            }, onSelect = { selectedDay = it.toString() })
            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = DotColors.Line)
            DotLabel(selectedDate.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)))
        }
        if (records.isEmpty()) item {
            Text(
                if (sessions.isEmpty()) "Completed fasts will appear here." else "No fasts on this day.",
                Modifier.padding(vertical = 20.dp), color = DotColors.Muted,
            )
        }
        items(records, key = { it.id }) { session ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(session) }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sessionTimeText(session), color = DotColors.Text, style = MaterialTheme.typography.bodyMedium)
                        if (session.needsTimeReview) DotCaption("TIME NEEDS REVIEW")
                    }
                    session.endEpochMillis?.let { end ->
                        Text(goalText(session.completedDurationMillis ?: (end - session.startEpochMillis)), fontFamily = FontFamily.Monospace)
                    }
                }
                HorizontalDivider(color = DotColors.Line)
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun SessionDetail(session: FastSession, projection: FastProjection?, busy: Boolean, onDelete: () -> Unit) {
    val duration = session.endEpochMillis?.let { end ->
        session.completedDurationMillis ?: (end - session.startEpochMillis)
    } ?: projection?.elapsedMillis
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DotTime(
                duration?.let(::durationText) ?: "—",
                Modifier.widthIn(max = 260.dp).fillMaxWidth().height(40.dp),
            )
            DotCaption(if (session.endEpochMillis == null) "ELAPSED" else "DURATION")
        }
        HorizontalDivider(color = DotColors.Line)
        DetailRow("Started", timestampText(session.startEpochMillis))
        DetailRow("Ended", session.endEpochMillis?.let(::timestampText) ?: "In progress")
        HorizontalDivider(color = DotColors.Line)
        DetailRow("Goal", goalText(session.goalMillis))
        if (session.needsTimeReview || projection?.needsTimeReview == true) {
            Text("Time needs review", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(
            enabled = !busy,
            onClick = onDelete,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Delete fast", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = DotColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Text(value, color = DotColors.Text, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun GoalPicker(lastGoalMillis: Long?, busy: Boolean, onDismiss: () -> Unit, onStart: (Long) -> Unit) {
    val presets = listOf(12L, 16L, 18L, 24L).map { it * 3_600_000L }
    var selected by rememberSaveable { mutableStateOf(lastGoalMillis) }
    var custom by rememberSaveable { mutableStateOf(lastGoalMillis != null && lastGoalMillis !in presets) }
    var hours by rememberSaveable { mutableStateOf(if (custom) "${lastGoalMillis!! / 3_600_000}" else "") }
    var minutes by rememberSaveable { mutableStateOf(if (custom) "${lastGoalMillis!! / 60_000 % 60}" else "") }
    var validation by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Choose a goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { goal ->
                    OutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().semantics { this.selected = !custom && selected == goal },
                        onClick = { selected = goal; custom = false; validation = null },
                    ) { Text("${if (!custom && selected == goal) "✓ " else ""}${goalText(goal)}") }
                }
                OutlinedButton(enabled = !busy, onClick = { custom = true; selected = null; validation = null }) { Text("Custom") }
                if (custom) {
                    OutlinedTextField(
                        hours, { hours = it; validation = null }, label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    )
                    OutlinedTextField(
                        minutes, { minutes = it; validation = null }, label = { Text("Minutes (0–59)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    )
                }
                validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (custom || selected != null), onClick = {
                val goal = if (custom) parseCustomGoal(hours, minutes) else selected
                if (goal == null) validation = "Enter a positive duration with whole hours and minutes (0–59). The duration must fit in a timer."
                else onStart(goal)
            }) { Text("Start fast") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

internal fun parseCustomGoal(hours: String, minutes: String): Long? {
    val h = if (hours.isBlank()) 0L else hours.toLongOrNull() ?: return null
    val m = if (minutes.isBlank()) 0L else minutes.toLongOrNull() ?: return null
    if (h < 0L || m !in 0L..59L) return null
    return try {
        Math.addExact(Math.multiplyExact(h, 3_600_000L), Math.multiplyExact(m, 60_000L)).takeIf { it > 0L }
    } catch (_: ArithmeticException) {
        null
    }
}

@Composable
private fun Settings(
    highlightArgb: Long,
    notificationsEnabled: Boolean,
    permissionGranted: Boolean,
    notificationStatus: String,
    glyphEnabled: Boolean,
    glyph: GlyphUiCapability,
    busy: Boolean,
    onHighlight: (Long) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onGlyph: (Boolean) -> Unit,
    onGlyphSetup: () -> Unit,
    onNotificationSettings: () -> Unit,
    onHelp: (String) -> Unit,
    countDown: Boolean,
    onCountDown: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        DotLabel("TIMER")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Count up" to false, "Count down" to true).forEach { (label, down) ->
                OutlinedButton(
                    enabled = !busy, onClick = { onCountDown(down) },
                    modifier = Modifier.semantics { selected = countDown == down },
                ) { Text(label, color = if (countDown == down) MaterialTheme.colorScheme.primary else DotColors.Muted) }
            }
        }
        HorizontalDivider(color = DotColors.Line)
        DotLabel("HIGHLIGHT")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DotColors.Highlights.forEach { (name, argb) ->
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .border(2.dp, if (highlightArgb == argb) DotColors.Text else Color.Transparent, CircleShape)
                            .semantics { contentDescription = name; selected = highlightArgb == argb }
                            .clickable(enabled = !busy, role = Role.RadioButton) { onHighlight(argb) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(34.dp).background(Color(argb.toInt()), CircleShape),
                        )
                }
            }
        }
        HorizontalDivider(color = DotColors.Line)
        SettingToggle("GOAL NOTIFICATION", notificationsEnabled, !busy, onNotifications)
        Text(notificationStatus, style = MaterialTheme.typography.bodySmall, color = DotColors.Muted)
        if (notificationsEnabled || !permissionGranted) {
            TextButton(onClick = onNotificationSettings) { Text("ANDROID SETTINGS") }
        }
        HorizontalDivider(color = DotColors.Line)
        SettingToggle("GLYPH TOY", glyphEnabled, !busy && (glyph.available || glyphEnabled), onGlyph)
        Text(glyph.status, style = MaterialTheme.typography.bodySmall, color = DotColors.Muted)
        if (glyph.available && glyphEnabled) {
            TextButton(enabled = !busy, onClick = onGlyphSetup) { Text("TOYS SETTINGS") }
        }
        HorizontalDivider(color = DotColors.Line)
        TextButton(onClick = { onHelp("about") }) { Text("Help") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().sizeIn(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        DotLabel(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
