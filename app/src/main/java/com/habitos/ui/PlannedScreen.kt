package com.habitos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedScreen(
    state: PersistedState,
    onAddPlannedTask: (title: String, priority: Priority, dueDateStr: String, dueTime: String?, durationMinutes: Int?) -> Unit,
    onPostponeTask: (task: TaskItem, newDueDate: String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()

    var title by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(todayStr) }
    var selectedPriority by remember { mutableStateOf(Priority.P4) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf<Int?>(null) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showCompletedAccordion by remember { mutableStateOf(false) }

    val plannedTasks = state.tasks.filter { !it.dueDate.isNullOrBlank() }
    val overdue = plannedTasks.filter { !it.isCompleted && it.dueDate!! < todayStr }
    val dueToday = plannedTasks.filter { it.dueDate == todayStr && !it.isCompleted }
    val dueTodayCompleted = plannedTasks.filter { it.dueDate == todayStr && it.isCompleted }
    val upcoming = plannedTasks.filter { it.dueDate!! > todayStr && !it.isCompleted }.sortedBy { it.dueDate }
    val upcomingCompleted = plannedTasks.filter { it.dueDate!! > todayStr && it.isCompleted }.sortedBy { it.dueDate }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Planned", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(top = 8.dp))
        Text("Overdue • Today • Upcoming", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(bottom = 10.dp))

        // ─── Quick-Add Card ───────────────────────────────────────────────
        Surface(color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(EmeraldPrimary),
                        decorationBox = { inner ->
                            if (title.isEmpty()) Text("Add scheduled task...", color = TextMuted, fontSize = 14.sp)
                            inner()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onAddPlannedTask(title.trim(), selectedPriority, selectedDate, selectedTime, selectedDuration)
                                title = ""
                                selectedTime = null
                                selectedDuration = null
                                selectedPriority = Priority.P4
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                    }
                }

                // Attribute chips row
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Priority chip
                    Box {
                        Surface(
                            color = Color(selectedPriority.colorHex).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(selectedPriority.colorHex).copy(alpha = 0.5f)),
                            modifier = Modifier.clickable { showPriorityDropdown = true }
                        ) {
                            Text(selectedPriority.shortLabel, color = Color(selectedPriority.colorHex), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                        }
                        DropdownMenu(expanded = showPriorityDropdown, onDismissRequest = { showPriorityDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                            Priority.entries.forEach { p ->
                                DropdownMenuItem(text = { Text(p.label, color = Color(p.colorHex), fontSize = 12.sp) }, onClick = { selectedPriority = p; showPriorityDropdown = false })
                            }
                        }
                    }

                    // Date chip
                    Surface(
                        color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberStreak.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { showDatePicker = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = AmberStreak, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(DateFormatHelper.shortDate(selectedDate), color = AmberStreak, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Time chip
                    Surface(
                        color = if (selectedTime != null) TealSecondary.copy(alpha = 0.18f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedTime != null) TealSecondary.copy(alpha = 0.6f) else ObsidianBorder),
                        modifier = Modifier.clickable { showTimePicker = true }
                    ) {
                        Text(
                            if (selectedTime != null) "🕒 $selectedTime" else "🕒 Time",
                            color = if (selectedTime != null) TealSecondary else TextMuted,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }

                    // Duration chip
                    Surface(
                        color = if (selectedDuration != null) TealSecondary.copy(alpha = 0.18f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selectedDuration != null) TealSecondary.copy(alpha = 0.6f) else ObsidianBorder),
                        modifier = Modifier.clickable { showDurationPicker = true }
                    ) {
                        Text(
                            if (selectedDuration != null) "⏱️ ${DateFormatHelper.formatDuration(selectedDuration!!)}" else "⏱️ Duration",
                            color = if (selectedDuration != null) TealSecondary else TextMuted,
                            fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ─── Completed toggle ─────────────────────────────────────────────
        val completedCount = dueTodayCompleted.size + upcomingCompleted.size
        if (completedCount > 0) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = ObsidianCard, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.clickable { showCompletedAccordion = !showCompletedAccordion }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✓ Completed ($completedCount)", color = TextMuted, fontSize = 10.sp)
                        Icon(if (showCompletedAccordion) Icons.Default.ArrowDropDown else Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            // ─── Overdue ──────────────────────────────────────────────────
            if (overdue.isNotEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("OVERDUE (${overdue.size})", color = RoseAlert, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Surface(color = RoseAlert.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp), modifier = Modifier.clickable {
                            overdue.forEach { onPostponeTask(it, todayStr) }
                        }) {
                            Text("Reschedule All to Today", color = RoseAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                items(overdue, key = { "ov_${it.id}" }) { task ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PostponeChip("Today") { onPostponeTask(task, todayStr) }
                            PostponeChip("+1 Day") { onPostponeTask(task, today.plusDays(1).toString()) }
                            PostponeChip("This Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                            PostponeChip("Next Week") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
                        }
                    }
                }
            }

            // ─── Today ────────────────────────────────────────────────────
            if (dueToday.isNotEmpty()) {
                item { Text("TODAY (${dueToday.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(dueToday, key = { "td_${it.id}" }) { task ->
                    TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }

            // ─── Upcoming ─────────────────────────────────────────────────
            if (upcoming.isNotEmpty()) {
                item { Text("UPCOMING (${upcoming.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(upcoming, key = { "up_${it.id}" }) { task ->
                    TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }

            // ─── Completed Accordion ──────────────────────────────────────
            if (showCompletedAccordion) {
                if (dueTodayCompleted.isNotEmpty()) {
                    item { Text("✓ COMPLETED TODAY (${dueTodayCompleted.size})", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    items(dueTodayCompleted, key = { "td_done_${it.id}" }) { task ->
                        TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                    }
                }
                if (upcomingCompleted.isNotEmpty()) {
                    item { Text("✓ COMPLETED UPCOMING (${upcomingCompleted.size})", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    items(upcomingCompleted, key = { "up_done_${it.id}" }) { task ->
                        TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                    }
                }
            }
        }
    }

    // ─── Date Picker ─────────────────────────────────────────────────────
    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                    showDatePicker = false
                }) { Text("Select", color = EmeraldPrimary) }
            }
        ) { DatePicker(state = pickerState) }
    }

    // ─── Time Picker ─────────────────────────────────────────────────────
    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m -> selectedTime = "%02d:%02d".format(h, m); showTimePicker = false },
            onClear = { selectedTime = null; showTimePicker = false }
        )
    }

    // ─── Duration Picker ──────────────────────────────────────────────────
    if (showDurationPicker) {
        DurationPickerDialog(
            current = selectedDuration,
            onDismiss = { showDurationPicker = false },
            onSelect = { selectedDuration = it; showDurationPicker = false },
            onClear = { selectedDuration = null; showDurationPicker = false }
        )
    }
}
