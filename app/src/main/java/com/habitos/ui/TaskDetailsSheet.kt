package com.habitos.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitos.app.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailBottomSheet(
    task: TaskItem,
    customLists: List<CustomTaskList>,
    onDismiss: () -> Unit,
    onUpdateTask: (TaskItem) -> Unit,
    onDeleteTask: () -> Unit,
    onPostponeTask: (TaskItem, String) -> Unit,
    onShowRecurrenceSnackbar: (String) -> Unit
) {
    val today = LocalDate.now()
    val todayStr = today.toString()

    var stepInput by remember { mutableStateOf("") }
    var showRecurrenceDropdown by remember { mutableStateOf(false) }
    var showListDropdown by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDurationPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showReminderDropdown by remember { mutableStateOf(false) }
    var newLabelInput by remember { mutableStateOf("") }
    var showLabelInput by remember { mutableStateOf(false) }
    var newCommentText by remember { mutableStateOf("") }
    var showCustomRecurrenceInput by remember { mutableStateOf(false) }
    var customRecurrenceDaysStr by remember { mutableStateOf(task.customRecurrenceDays.toString()) }

    val reminderOptions = listOf(
        null to "No reminder",
        0 to "At due time",
        15 to "15 min before",
        30 to "30 min before",
        60 to "1 hour before",
        1440 to "1 day before"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp)
        ) {
            // ─── Title & Delete ───────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(task.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onDeleteTask) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = RoseAlert) }
            }
            if (task.recurrence != RecurrenceType.NONE) {
                Surface(color = EmeraldPrimary.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text("🔁 ${task.recurrence.label}", color = EmeraldPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ─── 4-Tier Priority Selector ─────────────────────────────────
            Text("Priority", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Priority.entries.forEach { prio ->
                    val sel = task.priority == prio
                    val col = Color(prio.colorHex)
                    Surface(
                        color = if (sel) col.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (sel) col else ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onUpdateTask(task.copy(priority = prio)) }
                    ) {
                        Text(prio.shortLabel, color = if (sel) col else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── My Day + List ────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val inMyDay = task.isMyDay && task.myDayDate == todayStr
                FilterChip(
                    selected = inMyDay,
                    onClick = { onUpdateTask(task.copy(isMyDay = !inMyDay, myDayDate = if (!inMyDay) todayStr else "")) },
                    label = { Text(if (inMyDay) "In My Day" else "+ My Day", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = task.isImportant,
                    onClick = { onUpdateTask(task.copy(isImportant = !task.isImportant)) },
                    label = { Text(if (task.isImportant) "Important" else "Mark Important", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberStreak.copy(alpha = 0.2f), selectedLabelColor = AmberStreak, selectedLeadingIconColor = AmberStreak)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── Category / List selector ──────────────────────────────────
            Text("Category", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box {
                val selList = customLists.find { it.id == task.listId }
                Surface(
                    color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selList != null) Color(selList.colorHex).copy(alpha = 0.5f) else ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().clickable { showListDropdown = true }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(selList?.name ?: "No Category", color = if (selList != null) Color(selList.colorHex) else TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(expanded = showListDropdown, onDismissRequest = { showListDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                    DropdownMenuItem(text = { Text("No Category", color = TextMuted, fontSize = 12.sp) }, onClick = { onUpdateTask(task.copy(listId = null)); showListDropdown = false })
                    customLists.forEach { list ->
                        DropdownMenuItem(text = { Text(list.name, color = Color(list.colorHex), fontSize = 12.sp) }, onClick = { onUpdateTask(task.copy(listId = list.id)); showListDropdown = false })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── Time Blocking ────────────────────────────────────────────
            Text("⏰ Time Blocking", color = TealSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Due Date
                Surface(
                    color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (!task.dueDate.isNullOrBlank()) AmberStreak.copy(alpha = 0.6f) else ObsidianBorder),
                    modifier = Modifier.weight(1f).clickable { showDatePicker = true }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("📅 Due Date", color = TextMuted, fontSize = 10.sp)
                        Text(if (!task.dueDate.isNullOrBlank()) DateFormatHelper.shortDate(task.dueDate) else "Not set", color = AmberStreak, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Time
                Surface(
                    color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (!task.dueTime.isNullOrBlank()) TealSecondary.copy(alpha = 0.6f) else ObsidianBorder),
                    modifier = Modifier.weight(1f).clickable { showTimePicker = true }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text("🕒 Time", color = TextMuted, fontSize = 10.sp)
                        Text(task.dueTime ?: "Not set", color = TealSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Duration chips
            Text("⏱️ Duration", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val durationPresets = listOf(15 to "15m", 30 to "30m", 45 to "45m", 60 to "1h", 90 to "1.5h", 120 to "2h")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                durationPresets.take(3).forEach { (mins, label) ->
                    val sel = task.durationMinutes == mins
                    Surface(
                        color = if (sel) TealSecondary.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, if (sel) TealSecondary else ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onUpdateTask(task.copy(durationMinutes = if (sel) null else mins)) }
                    ) {
                        Text(label, color = if (sel) TealSecondary else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                durationPresets.drop(3).forEach { (mins, label) ->
                    val sel = task.durationMinutes == mins
                    Surface(
                        color = if (sel) TealSecondary.copy(alpha = 0.2f) else ObsidianNav,
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, if (sel) TealSecondary else ObsidianBorder),
                        modifier = Modifier.weight(1f).clickable { onUpdateTask(task.copy(durationMinutes = if (sel) null else mins)) }
                    ) {
                        Text(label, color = if (sel) TealSecondary else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }

            // Show computed time block
            val timeBlock = DateFormatHelper.formatTimeBlock(task.dueTime, task.durationMinutes)
            if (timeBlock.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Surface(color = TealSecondary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(timeBlock, color = TealSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // ─── Hard Deadline ────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🎯 Hard Deadline", color = AmberStreak, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (!task.deadlineDate.isNullOrBlank()) {
                    Surface(
                        color = RoseAlert.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { onUpdateTask(task.copy(deadlineDate = null)) }
                    ) {
                        Text("× Clear", color = RoseAlert, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Surface(
                color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (!task.deadlineDate.isNullOrBlank()) AmberStreak.copy(alpha = 0.5f) else ObsidianBorder),
                modifier = Modifier.fillMaxWidth().clickable { showDeadlinePicker = true }
            ) {
                val deadlineInfo = DateFormatHelper.deadlinePill(task.deadlineDate, today)
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (!task.deadlineDate.isNullOrBlank()) DateFormatHelper.shortDate(task.deadlineDate) else "No hard deadline set", color = if (!task.deadlineDate.isNullOrBlank()) AmberStreak else TextMuted, fontSize = 13.sp)
                    if (deadlineInfo != null) {
                        val (pillText, isUrgent) = deadlineInfo
                        Surface(color = (if (isUrgent) RoseAlert else AmberStreak).copy(alpha = 0.18f), shape = RoundedCornerShape(5.dp)) {
                            Text(pillText, color = if (isUrgent) RoseAlert else AmberStreak, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── 1-Tap Postpone ───────────────────────────────────────────
            Text("📅 Quick Postpone", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                PostponeChip("Today") { onPostponeTask(task, today.toString()) }
                PostponeChip("+1 Day") { onPostponeTask(task, today.plusDays(1).toString()) }
                PostponeChip("Weekend") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).toString()) }
                PostponeChip("Next Week") { onPostponeTask(task, today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString()) }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── Recurrence ───────────────────────────────────────────────
            Text("🔁 Repeat", color = EmeraldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Box {
                Surface(
                    color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (task.recurrence != RecurrenceType.NONE) EmeraldPrimary.copy(alpha = 0.5f) else ObsidianBorder),
                    modifier = Modifier.fillMaxWidth().clickable { showRecurrenceDropdown = true }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(task.recurrence.label, color = if (task.recurrence != RecurrenceType.NONE) EmeraldPrimary else TextMuted, fontSize = 12.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
                DropdownMenu(expanded = showRecurrenceDropdown, onDismissRequest = { showRecurrenceDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                    RecurrenceType.entries.forEach { rec ->
                        DropdownMenuItem(
                            text = { Text(rec.label, color = if (task.recurrence == rec) EmeraldPrimary else TextPrimary, fontSize = 12.sp) },
                            onClick = {
                                onUpdateTask(task.copy(recurrence = rec))
                                showRecurrenceDropdown = false
                                showCustomRecurrenceInput = rec == RecurrenceType.AFTER_COMPLETION
                            }
                        )
                    }
                }
            }
            if (task.recurrence == RecurrenceType.AFTER_COMPLETION || showCustomRecurrenceInput) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = customRecurrenceDaysStr,
                    onValueChange = { customRecurrenceDaysStr = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Every X days after completion") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            val d = customRecurrenceDaysStr.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            onUpdateTask(task.copy(customRecurrenceDays = d))
                        }) { Text("Set", color = EmeraldPrimary, fontSize = 11.sp) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(10.dp))

            // ─── Reminder ─────────────────────────────────────────────────
            if (!task.dueDate.isNullOrBlank() && !task.dueTime.isNullOrBlank()) {
                Text("🔔 Reminder", color = AmberStreak, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Box {
                    val currentReminderLabel = reminderOptions.find { it.first == task.reminderOffsetMinutes }?.second ?: "No reminder"
                    Surface(
                        color = ObsidianNav, shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (task.reminderOffsetMinutes != null) AmberStreak.copy(alpha = 0.5f) else ObsidianBorder),
                        modifier = Modifier.fillMaxWidth().clickable { showReminderDropdown = true }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(currentReminderLabel, color = if (task.reminderOffsetMinutes != null) AmberStreak else TextMuted, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = showReminderDropdown, onDismissRequest = { showReminderDropdown = false }, modifier = Modifier.background(ObsidianCard)) {
                        reminderOptions.forEach { (offset, label) ->
                            DropdownMenuItem(
                                text = { Text(label, color = if (task.reminderOffsetMinutes == offset) AmberStreak else TextPrimary, fontSize = 12.sp) },
                                onClick = { onUpdateTask(task.copy(reminderOffsetMinutes = offset)); showReminderDropdown = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── Labels / Tags ────────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("🏷️ Labels", color = TealSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showLabelInput = !showLabelInput }, contentPadding = PaddingValues(0.dp)) {
                    Text("+ Add Label", color = TealSecondary, fontSize = 11.sp)
                }
            }
            if (task.labels.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    task.labels.forEach { label ->
                        Surface(
                            color = TealSecondary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TealSecondary.copy(alpha = 0.4f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 7.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)) {
                                Text("@$label", color = TealSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(2.dp))
                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TealSecondary.copy(alpha = 0.7f), modifier = Modifier.size(12.dp).clickable {
                                    onUpdateTask(task.copy(labels = task.labels.filterNot { it == label }))
                                })
                            }
                        }
                    }
                }
            }
            if (showLabelInput) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = newLabelInput,
                        onValueChange = { newLabelInput = it.replace(" ", "").replace("@", "") },
                        placeholder = { Text("label (no spaces)", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newLabelInput.isNotBlank() && !task.labels.contains(newLabelInput)) {
                                onUpdateTask(task.copy(labels = task.labels + newLabelInput.trim()))
                                newLabelInput = ""
                                showLabelInput = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealSecondary)
                    ) {
                        Text("Add", color = ObsidianBg, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── Subtasks (Steps) ─────────────────────────────────────────
            Text("Subtasks (Steps)", color = EmeraldPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            task.steps.forEach { step ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = step.isDone,
                        onCheckedChange = { onUpdateTask(task.copy(steps = task.steps.map { s -> if (s.id == step.id) s.copy(isDone = !s.isDone) else s })) }
                    )
                    Text(step.title, color = if (step.isDone) TextMuted else TextPrimary, textDecoration = if (step.isDone) TextDecoration.LineThrough else TextDecoration.None, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onUpdateTask(task.copy(steps = task.steps.filterNot { it.id == step.id })) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = stepInput, onValueChange = { stepInput = it },
                    placeholder = { Text("Add next step...", color = TextMuted, fontSize = 13.sp) },
                    singleLine = true, modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (stepInput.isNotBlank()) {
                            onUpdateTask(task.copy(steps = task.steps + SubTask(title = stepInput.trim())))
                            stepInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Add", color = ObsidianBg)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── Notes ────────────────────────────────────────────────────
            Text("Notes", color = AmberStreak, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            OutlinedTextField(
                value = task.notes,
                onValueChange = { onUpdateTask(task.copy(notes = it)) },
                placeholder = { Text("Auto-saving notes...", color = TextMuted) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ObsidianBorder)
            Spacer(Modifier.height(10.dp))

            // ─── Comments & Activity Log ───────────────────────────────────
            Text("💬 Comments & Activity", color = TealSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))

            task.comments.forEach { comment ->
                val dateStr = java.time.Instant.ofEpochMilli(comment.createdAtMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                Surface(color = ObsidianNav, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, ObsidianBorder), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(comment.text, color = TextPrimary, fontSize = 13.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(dateStr, color = TextMuted, fontSize = 10.sp)
                        }
                        IconButton(
                            onClick = { onUpdateTask(task.copy(comments = task.comments.filterNot { it.id == comment.id })) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete comment", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    placeholder = { Text("Write a progress note...", color = TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newCommentText.isNotBlank()) {
                            onUpdateTask(task.copy(comments = task.comments + TaskComment(text = newCommentText.trim())))
                            newCommentText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealSecondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Post", color = ObsidianBg, fontWeight = FontWeight.Bold)
                }
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
                    pickerState.selectedDateMillis?.let {
                        val newDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        onUpdateTask(task.copy(dueDate = newDate))
                    }
                    showDatePicker = false
                }) { Text("Select", color = EmeraldPrimary) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextMuted) } }
        ) { DatePicker(state = pickerState) }
    }

    // ─── Deadline Picker Dialog ───────────────────────────────────────────
    if (showDeadlinePicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDeadlinePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val newDate = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString()
                        onUpdateTask(task.copy(deadlineDate = newDate))
                    }
                    showDeadlinePicker = false
                }) { Text("Set Deadline", color = AmberStreak) }
            },
            dismissButton = { TextButton(onClick = { showDeadlinePicker = false }) { Text("Cancel", color = TextMuted) } }
        ) { DatePicker(state = pickerState) }
    }

    // ─── Time Picker Dialog ───────────────────────────────────────────────
    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { h, m -> onUpdateTask(task.copy(dueTime = "%02d:%02d".format(h, m))); showTimePicker = false },
            onClear = { onUpdateTask(task.copy(dueTime = null)); showTimePicker = false }
        )
    }

    // ─── Duration Picker Dialog ───────────────────────────────────────────
    if (showDurationPicker) {
        DurationPickerDialog(
            current = task.durationMinutes,
            onDismiss = { showDurationPicker = false },
            onSelect = { onUpdateTask(task.copy(durationMinutes = it)); showDurationPicker = false },
            onClear = { onUpdateTask(task.copy(durationMinutes = null)); showDurationPicker = false }
        )
    }
}
