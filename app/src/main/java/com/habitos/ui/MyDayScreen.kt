package com.habitos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class SortMode(val label: String) {
    PRIORITY("Priority (P1-P4)"),
    DUE_TIME("Due Time"),
    DURATION("Duration (Shortest)"),
    TITLE("Title (A-Z)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDayScreen(
    state: PersistedState,
    searchQuery: String,
    onOpenSuggestions: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onAddTask: (isHabit: Boolean, title: String, priority: Priority, listId: String?, dueDate: String?, dueTime: String?, durationMinutes: Int?, recurrence: RecurrenceType, labels: List<String>, cue: String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onToggleHabitToday: (HabitItem) -> Unit,
    onClickTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteHabit: (HabitItem) -> Unit,
    onPostponeTask: (TaskItem, String) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()
    val weekDays = remember { WeekHelper.currentWeekDays(today) }

    var inputText by remember { mutableStateOf("") }
    var isHabitMode by remember { mutableStateOf(false) }
    var selectedPriority by remember { mutableStateOf(Priority.P4) }
    var selectedListId by remember { mutableStateOf(state.customLists.firstOrNull()?.id) }
    var showListDropdown by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var selectedDateStr by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedTime by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf<Int?>(null) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var selectedRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }
    var cueText by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SortMode.PRIORITY) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showCompletedAccordion by remember { mutableStateOf(false) }

    val allMyDayTasks = state.tasks.filter {
        (it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr
    }
    val overdueTasks = state.tasks.filter { !it.isCompleted && !it.dueDate.isNullOrBlank() && it.dueDate < todayStr && !(it.isMyDay && it.myDayDate == todayStr) }

    fun sortTasks(list: List<TaskItem>): List<TaskItem> = when (sortMode) {
        SortMode.PRIORITY -> list.sortedBy { it.priority.ordinal }
        SortMode.DUE_TIME -> list.sortedWith(compareBy(nullsLast()) { it.dueTime })
        SortMode.DURATION -> list.sortedWith(compareBy(nullsLast()) { it.durationMinutes })
        SortMode.TITLE -> list.sortedBy { it.title.lowercase() }
    }

    val incompleteTasks = sortTasks(allMyDayTasks.filter { !it.isCompleted && (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)) })
    val completedTasks = sortTasks(allMyDayTasks.filter { it.isCompleted && (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)) })
    val filteredHabits = state.habits.filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }

    val doneMyDay = allMyDayTasks.count { it.isCompleted }
    val doneTodayHabits = state.habits.count { it.completedDates.contains(todayStr) }
    val doneCombined = doneMyDay + doneTodayHabits
    val totalCombined = allMyDayTasks.size + state.habits.size
    val myDayPct = if (totalCombined == 0) 0 else (doneCombined * 100) / totalCombined

    val consistencyDays = WeekHelper.consistencyStreakDays(state.habits, today)
    val recordStreak = state.habits.maxOfOrNull { maxOf(it.recordStreak, it.currentStreak(today)) } ?: 0
    val totalWeekChecks = weekDays.sumOf { d -> state.habits.count { it.completedDates.contains(d.toString()) } }
    val possibleWeekChecks = state.habits.size * 7
    val weeklyPct = if (possibleWeekChecks == 0) 0 else (totalWeekChecks * 100) / possibleWeekChecks
    val perfectDaysCount = weekDays.count { WeekHelper.isPerfectDay(state.habits, it) }

    val totalScheduledMinutes = incompleteTasks.sumOf { it.durationMinutes ?: 0 }
    val workloadStr = if (totalScheduledMinutes > 0) DateFormatHelper.formatDuration(totalScheduledMinutes) else null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        // ─── Progress Dashboard ───────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onOpenSuggestions() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("MY DAY PROGRESS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("💡 Suggest", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("$myDayPct%", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                            Text("$doneCombined / $totalCombined done", color = TextMuted, fontSize = 11.sp)
                            if (workloadStr != null) {
                                Spacer(Modifier.height(4.dp))
                                Surface(color = TealSecondary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                                    Text("⏱️ $workloadStr scheduled today", color = TealSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { myDayPct / 100f },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                                color = EmeraldPrimary,
                                trackColor = ObsidianBorder
                            )
                        }
                    }

                    Surface(
                        color = ObsidianCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onNavigateToHabits() }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("HABIT MATRIX", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("$weeklyPct%", color = AmberStreak, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                            Text("Week consistency", color = TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("🔥 ${consistencyDays}d streak  👑 $perfectDaysCount perfect", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text("🏆 Record: ${recordStreak}d", color = AmberStreak, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // ─── Overdue Banner ───────────────────────────────────────────────
        if (overdueTasks.isNotEmpty()) {
            item {
                Surface(
                    color = RoseAlert.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("🚨 OVERDUE (${overdueTasks.size})", color = RoseAlert, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            Surface(
                                color = RoseAlert.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { overdueTasks.forEach { onPostponeTask(it, todayStr) } }
                            ) {
                                Text("Reschedule All to Today", color = RoseAlert, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        overdueTasks.forEach { task ->
                            TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
                            Row(modifier = Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PostponeChip("Today") { onPostponeTask(task, todayStr) }
                                PostponeChip("+1 Day") { onPostponeTask(task, today.plusDays(1).toString()) }
                                PostponeChip("This Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                                PostponeChip("Next Week") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
                            }
                        }
                    }
                }
            }
        }

        // ─── Quick-Add Card ───────────────────────────────────────────────
        item {
            Surface(
                color = ObsidianCard, shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isHabitMode) EmeraldPrimary.copy(alpha = 0.6f) else ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            singleLine = true,
                            textStyle = ComposeTextStyle(color = TextPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(EmeraldPrimary),
                            decorationBox = { inner ->
                                if (inputText.isEmpty()) Text(
                                    if (isHabitMode) "New daily habit (e.g. Code 1 hr)..." else "Add task (p1, @label, today, every day)...",
                                    color = TextMuted, fontSize = 14.sp
                                )
                                inner()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    if (isHabitMode) {
                                        onAddTask(true, inputText.trim(), Priority.P4, selectedListId, null, null, null, RecurrenceType.NONE, emptyList(), cueText)
                                    } else {
                                        val parsed = SmartTaskParser.parse(inputText)
                                        onAddTask(
                                            false,
                                            parsed.title,
                                            selectedPriority.takeIf { it != Priority.P4 } ?: parsed.priority ?: Priority.P4,
                                            selectedListId,
                                            selectedDateStr,
                                            selectedTime,
                                            selectedDuration ?: parsed.durationMinutes,
                                            selectedRecurrence.takeIf { it != RecurrenceType.NONE } ?: parsed.recurrence ?: RecurrenceType.NONE,
                                            parsed.labels,
                                            cueText
                                        )
                                    }
                                    inputText = ""
                                    selectedTime = null
                                    selectedDuration = null
                                    selectedRecurrence = RecurrenceType.NONE
                                    selectedPriority = Priority.P4
                                    selectedDateStr = todayStr
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (isHabitMode) EmeraldPrimary else EmeraldPrimary),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = ObsidianBg)
                        }
                    }

                    // ─── Mode & Attribute Chips ───────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mode toggle
                        FilterChip(
                            selected = isHabitMode,
                            onClick = { isHabitMode = !isHabitMode },
                            label = { Text(if (isHabitMode) "Habit" else "Task", fontSize = 10.sp) },
                            leadingIcon = { Icon(if (isHabitMode) Icons.Default.LocalFireDepartment else Icons.Default.CheckBox, contentDescription = null, modifier = Modifier.size(12.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EmeraldPrimary.copy(alpha = 0.2f), selectedLabelColor = EmeraldPrimary, selectedLeadingIconColor = EmeraldPrimary)
                        )

                        if (!isHabitMode) {
                            // Priority
                            Box {
                                Surface(
                                    color = Color(selectedPriority.colorHex).copy(alpha = 0.18f),
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
                                Text("📅 ${DateFormatHelper.shortDate(selectedDateStr)}", color = AmberStreak, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
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
                                    if (selectedDuration != null) "⏱️ ${DateFormatHelper.formatDuration(selectedDuration!!)}" else "⏱️ Dur",
                                    color = if (selectedDuration != null) TealSecondary else TextMuted,
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }

                            // Recurrence chip
                            Box {
                                Surface(
                                    color = if (selectedRecurrence != RecurrenceType.NONE) EmeraldPrimary.copy(alpha = 0.15f) else ObsidianNav,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (selectedRecurrence != RecurrenceType.NONE) EmeraldPrimary.copy(alpha = 0.5f) else ObsidianBorder),
                                    modifier = Modifier.clickable { showRecurrenceDropdown = true }
                                ) {
                                    Text(
                                        if (selectedRecurrence != RecurrenceType.NONE) "🔁 ${selectedRecurrence.label.take(10)}" else "🔁 Repeat",
                                        color = if (selectedRecurrence != RecurrenceType.NONE) EmeraldPrimary else TextMuted,
                                        fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                                DropdownMenu(expanded = showRecurrenceDropdown, onDismissRequest = { showRecurrenceDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                                    RecurrenceType.entries.forEach { rec ->
                                        DropdownMenuItem(text = { Text(rec.label, color = TextPrimary, fontSize = 12.sp) }, onClick = { selectedRecurrence = rec; showRecurrenceDropdown = false })
                                    }
                                }
                            }
                        }

                        // List selector
                        Box {
                            val selList = state.customLists.find { it.id == selectedListId }
                            Surface(
                                color = if (selList != null) Color(selList.colorHex).copy(alpha = 0.15f) else ObsidianNav,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (selList != null) Color(selList.colorHex).copy(alpha = 0.5f) else ObsidianBorder),
                                modifier = Modifier.clickable { showListDropdown = true }
                            ) {
                                Text(
                                    selList?.name?.take(10) ?: "List",
                                    color = if (selList != null) Color(selList.colorHex) else TextMuted,
                                    fontSize = 10.sp, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                )
                            }
                            DropdownMenu(expanded = showListDropdown, onDismissRequest = { showListDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                                state.customLists.forEach { list ->
                                    DropdownMenuItem(text = { Text(list.name, color = Color(list.colorHex), fontSize = 12.sp) }, onClick = { selectedListId = list.id; showListDropdown = false })
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Sort / Filter Bar ────────────────────────────────────────────
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sort:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box {
                    Surface(
                        color = ObsidianCard, shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.clickable { showSortDropdown = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(sortMode.label, color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(expanded = showSortDropdown, onDismissRequest = { showSortDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                        SortMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Text(mode.label, color = if (sortMode == mode) EmeraldPrimary else TextPrimary, fontSize = 12.sp) }, onClick = { sortMode = mode; showSortDropdown = false })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (completedTasks.isNotEmpty()) {
                    Surface(
                        color = ObsidianCard, shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.clickable { showCompletedAccordion = !showCompletedAccordion }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✓ Done (${completedTasks.size})", color = TextMuted, fontSize = 10.sp)
                            Icon(if (showCompletedAccordion) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // ─── Habits Section ───────────────────────────────────────────────
        if (filteredHabits.isNotEmpty()) {
            item { Text("DAILY HABITS (${filteredHabits.count { it.completedDates.contains(todayStr) }}/${filteredHabits.size})", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            items(filteredHabits, key = { "myday_h_${it.id}" }) { habit ->
                val done = habit.completedDates.contains(todayStr)
                Surface(
                    color = ObsidianCard, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (done) EmeraldPrimary.copy(alpha = 0.4f) else ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().clickable { onToggleHabitToday(habit) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = done, onCheckedChange = { onToggleHabitToday(habit) }, colors = CheckboxDefaults.colors(checkedColor = EmeraldPrimary, uncheckedColor = TextMuted, checkmarkColor = ObsidianBg))
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            Text(habit.name, color = if (done) TextMuted else TextPrimary, textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("🔥 Streak: ${habit.currentStreak(today)}d${if (habit.cue.isNotBlank()) " • 🎯 ${habit.cue}" else ""}", color = AmberStreak, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onDeleteHabit(habit) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert) }
                    }
                }
            }
        }

        // ─── Incomplete Tasks ─────────────────────────────────────────────
        if (incompleteTasks.isNotEmpty()) {
            item { Text("MY DAY TASKS (${doneMyDay}/${allMyDayTasks.size})", color = AmberStreak, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            items(incompleteTasks, key = { "myday_t_${it.id}" }) { task ->
                TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
            }
        }

        // ─── Completed Accordion ──────────────────────────────────────────
        if (completedTasks.isNotEmpty() && showCompletedAccordion) {
            item {
                Text("✓ COMPLETED TODAY (${completedTasks.size})", color = TextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            items(completedTasks, key = { "myday_done_${it.id}" }) { task ->
                TaskRowCard(task = task, customLists = state.customLists, onToggle = { onToggleTask(task) }, onClick = { onClickTask(task) }, onDelete = { onDeleteTask(task) })
            }
        }
    }

    // ─── Date Picker Dialog ───────────────────────────────────────────────
    if (showDatePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedDateStr = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() }
                    showDatePicker = false
                }) { Text("Select", color = EmeraldPrimary) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) } }
        ) { DatePicker(state = pickerState) }
    }

    // ─── Time Picker Dialog ───────────────────────────────────────────────
    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m -> selectedTime = "%02d:%02d".format(h, m); showTimePicker = false },
            onClear = { selectedTime = null; showTimePicker = false }
        )
    }

    // ─── Duration Picker Dialog ───────────────────────────────────────────
    if (showDurationPicker) {
        DurationPickerDialog(
            current = selectedDuration,
            onDismiss = { showDurationPicker = false },
            onSelect = { selectedDuration = it; showDurationPicker = false },
            onClear = { selectedDuration = null; showDurationPicker = false }
        )
    }
}

// ─── TaskRowCard ──────────────────────────────────────────────────────────────

@Composable
fun TaskRowCard(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val today = LocalDate.now()
    val listTag = customLists.find { it.id == task.listId }
    val prioColor = Color(task.priority.colorHex)
    val timeBlockStr = DateFormatHelper.formatTimeBlock(task.dueTime, task.durationMinutes)
    val deadlineInfo = DateFormatHelper.deadlinePill(task.deadlineDate, today)

    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (task.priority == Priority.P1 && !task.isCompleted) prioColor.copy(alpha = 0.6f) else ObsidianBorder),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = if (task.priority != Priority.P4) prioColor else EmeraldPrimary, uncheckedColor = if (task.priority != Priority.P4) prioColor else TextMuted, checkmarkColor = ObsidianBg)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = task.title,
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Metadata row
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (task.priority != Priority.P4) {
                        Surface(color = prioColor.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
                            Text(task.priority.shortLabel, color = prioColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    if (listTag != null) {
                        Text("● ${listTag.name}", color = Color(listTag.colorHex), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!task.dueDate.isNullOrBlank()) {
                        Text("📅 ${DateFormatHelper.shortDate(task.dueDate)}", color = AmberStreak, fontSize = 11.sp, maxLines = 1)
                    }
                    if (timeBlockStr.isNotBlank()) {
                        Text(timeBlockStr, color = TealSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (task.recurrence != RecurrenceType.NONE) {
                        Surface(color = EmeraldPrimary.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                            Text("🔁", fontSize = 9.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
                        }
                    }
                }
                // Labels & deadline row
                val hasLabels = task.labels.isNotEmpty()
                val hasDeadline = deadlineInfo != null
                if (hasLabels || hasDeadline) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.labels.take(3).forEach { label ->
                            Surface(color = TealSecondary.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                                Text("@$label", color = TealSecondary, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                        if (deadlineInfo != null) {
                            val (deadlineText, isUrgent) = deadlineInfo
                            Surface(color = (if (isUrgent) RoseAlert else AmberStreak).copy(alpha = 0.18f), shape = RoundedCornerShape(4.dp)) {
                                Text(deadlineText, color = if (isUrgent) RoseAlert else AmberStreak, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─── PostponeChip ─────────────────────────────────────────────────────────────

@Composable
fun PostponeChip(label: String, onClick: () -> Unit) {
    Surface(
        color = ObsidianNav, shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ─── Time Picker Dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onClear: () -> Unit
) {
    val timePickerState = rememberTimePickerState(is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("🕒 Set Time", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)) {
                Text("Set", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear", color = RoseAlert) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        }
    )
}

// ─── Duration Picker Dialog ───────────────────────────────────────────────────

@Composable
fun DurationPickerDialog(
    current: Int?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onClear: () -> Unit
) {
    val presets = listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h", 90 to "1.5h", 120 to "2h")
    var customInput by remember { mutableStateOf(current?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        title = { Text("⏱️ Duration", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick chips
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(3).forEach { (mins, label) ->
                        Surface(
                            color = if (current == mins) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (current == mins) EmeraldPrimary else ObsidianBorder),
                            modifier = Modifier.weight(1f).clickable { onSelect(mins) }
                        ) {
                            Text(label, color = if (current == mins) EmeraldPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.drop(3).forEach { (mins, label) ->
                        Surface(
                            color = if (current == mins) EmeraldPrimary.copy(alpha = 0.2f) else ObsidianNav,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (current == mins) EmeraldPrimary else ObsidianBorder),
                            modifier = Modifier.weight(1f).clickable { onSelect(mins) }
                        ) {
                            Text(label, color = if (current == mins) EmeraldPrimary else TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
                // Custom input
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it.filter { c -> c.isDigit() }.take(4) },
                    label = { Text("Custom minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val custom = customInput.toIntOrNull()
                    if (custom != null && custom > 0) onSelect(custom)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Set Custom", color = ObsidianBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear", color = RoseAlert) }
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
            }
        }
    )
}
