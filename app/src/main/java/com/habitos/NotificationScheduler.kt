package com.habitos.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationScheduler {
    const val CHANNEL_ID = "habitos_alerts_channel"
    const val ACTION_EOD_CHECK = "com.habitos.app.ACTION_EOD_CHECK"
    const val ACTION_BDAY_ALERT = "com.habitos.app.ACTION_BDAY_ALERT"
    const val ACTION_TASK_REMINDER = "com.habitos.app.ACTION_TASK_REMINDER"
    const val ACTION_COMPLETE_TASK_FROM_NOTIF = "com.habitos.app.ACTION_COMPLETE_TASK_FROM_NOTIF"
    const val ACTION_SNOOZE_TASK_FROM_NOTIF = "com.habitos.app.ACTION_SNOOZE_TASK_FROM_NOTIF"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TASK_TITLE = "task_title"
    const val EXTRA_PERSON_NAME = "person_name"
    const val EXTRA_IS_DAY_BEFORE = "is_day_before"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "habitOS Reminders & Birthdays",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "EOD habit/task alerts, task reminders, and Midnight/Day-before birthday reminders"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun scheduleAllAlarms(context: Context, state: PersistedState) {
        ensureChannel(context)
        scheduleDailyEodAlarm(context, state.settings.eodHour, state.settings.eodMinute)
        scheduleBirthdayAlarms(context, state.birthdays)
        scheduleTaskReminderAlarms(context, state.tasks)
    }

    fun scheduleDailyEodAlarm(context: Context, hour: Int, minute: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
            action = ACTION_EOD_CHECK
        }
        val pi = PendingIntent.getBroadcast(
            context, 9001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        var target = LocalDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (target.isBefore(LocalDateTime.now())) target = target.plusDays(1)
        val triggerMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setSafeExactAlarm(am, triggerMillis, pi)
    }

    fun scheduleBirthdayAlarms(context: Context, birthdays: List<BirthdayPerson>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()

        birthdays.forEach { person ->
            val bdayMidnight = nextOccurrenceDateTime(person.monthValue, person.dayOfMonth, 0, 0, now)
            val midnightIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                action = ACTION_BDAY_ALERT
                putExtra(EXTRA_PERSON_NAME, person.name)
                putExtra(EXTRA_IS_DAY_BEFORE, false)
            }
            val midnightPi = PendingIntent.getBroadcast(
                context,
                (person.id.hashCode() and 0xFFFFFFF) + 1,
                midnightIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSafeExactAlarm(am, bdayMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), midnightPi)

            var dayBefore = bdayMidnight.minusDays(1).withHour(9).withMinute(0)
            if (dayBefore.isBefore(now)) dayBefore = dayBefore.plusYears(1)
            val beforeIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                action = ACTION_BDAY_ALERT
                putExtra(EXTRA_PERSON_NAME, person.name)
                putExtra(EXTRA_IS_DAY_BEFORE, true)
            }
            val beforePi = PendingIntent.getBroadcast(
                context,
                (person.id.hashCode() and 0xFFFFFFF) + 2,
                beforeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setSafeExactAlarm(am, dayBefore.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), beforePi)
        }
    }

    fun scheduleTaskReminderAlarms(context: Context, tasks: List<TaskItem>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = LocalDateTime.now()

        tasks.filter { task ->
            !task.isCompleted
                && !task.dueDate.isNullOrBlank()
                && !task.dueTime.isNullOrBlank()
                && task.reminderOffsetMinutes != null
        }.forEach { task ->
            runCatching {
                val dueDate = LocalDate.parse(task.dueDate!!)
                val timeParts = task.dueTime!!.split(":").mapNotNull { it.toIntOrNull() }
                if (timeParts.size < 2) return@forEach
                var fireTime = LocalDateTime.of(dueDate, java.time.LocalTime.of(timeParts[0], timeParts[1]))
                    .minusMinutes(task.reminderOffsetMinutes!!.toLong())
                if (fireTime.isBefore(now)) return@forEach

                val intent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                    action = ACTION_TASK_REMINDER
                    putExtra(EXTRA_TASK_ID, task.id)
                    putExtra(EXTRA_TASK_TITLE, task.title)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    task.id.hashCode() and 0x7FFFFFFF,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setSafeExactAlarm(am, fireTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
            }
        }
    }

    private fun nextOccurrenceDateTime(month: Int, day: Int, hour: Int, minute: Int, now: LocalDateTime): LocalDateTime {
        val safeDay = if (month == 2 && day == 29 && !java.time.Year.isLeap(now.year.toLong())) 28 else day
        var candidate = LocalDateTime.of(now.year, month, safeDay, hour, minute, 0)
        if (candidate.isBefore(now)) {
            val nextYear = now.year + 1
            val safeDayNext = if (month == 2 && day == 29 && !java.time.Year.isLeap(nextYear.toLong())) 28 else day
            candidate = LocalDateTime.of(nextYear, month, safeDayNext, hour, minute, 0)
        }
        return candidate
    }

    fun setSafeExactAlarm(am: AlarmManager, triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }
}

class HabitOSAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationScheduler.ensureChannel(context)
        val state = HabitOSStorage.load(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPi = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when (intent.action) {
            NotificationScheduler.ACTION_EOD_CHECK -> {
                val today = LocalDate.now()
                val todayStr = today.toString()
                val yesterday = today.minusDays(1)

                val pendingHabits = state.habits.count { !it.completedDates.contains(todayStr) }
                val pendingMyDay = state.tasks.count {
                    !it.isCompleted && ((it.isMyDay && it.myDayDate == todayStr) || it.dueDate == todayStr)
                }
                val pendingBdays = state.birthdays.count {
                    (it.isBirthdayOn(today) || it.isBirthdayOn(yesterday)) && it.wishedYear != today.year
                }

                if (pendingHabits > 0 || pendingMyDay > 0 || pendingBdays > 0) {
                    val summaryParts = mutableListOf<String>()
                    if (pendingHabits > 0) summaryParts.add("$pendingHabits habit(s)")
                    if (pendingMyDay > 0) summaryParts.add("$pendingMyDay My Day task(s)")
                    if (pendingBdays > 0) summaryParts.add("$pendingBdays birthday wish(es)")

                    val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle("habitOS • Evening Check-In")
                        .setContentText("Still incomplete: ${summaryParts.joinToString(", ")}. Keep your streak alive!")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(contentPi)
                        .setAutoCancel(true)
                        .build()

                    nm.notify(9001, notification)
                }
                NotificationScheduler.scheduleDailyEodAlarm(context, state.settings.eodHour, state.settings.eodMinute)
            }

            NotificationScheduler.ACTION_BDAY_ALERT -> {
                val name = intent.getStringExtra(NotificationScheduler.EXTRA_PERSON_NAME) ?: "A friend"
                val isDayBefore = intent.getBooleanExtra(NotificationScheduler.EXTRA_IS_DAY_BEFORE, false)
                val title = if (isDayBefore) "🎂 Birthday Tomorrow: $name" else "🎉 It's $name's Birthday Today! (Midnight)"
                val body = if (isDayBefore) "Heads up! $name's birthday is tomorrow. Keep it in mind!"
                else "It's 12:00 Midnight! Open habitOS to send a 1-tap wish to $name."

                val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .build()

                nm.notify(name.hashCode(), notification)
                NotificationScheduler.scheduleBirthdayAlarms(context, state.birthdays)
            }

            NotificationScheduler.ACTION_TASK_REMINDER -> {
                val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
                val taskTitle = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_TITLE) ?: "Task"
                val task = state.tasks.find { it.id == taskId } ?: return
                if (task.isCompleted) return

                val completeIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                    action = NotificationScheduler.ACTION_COMPLETE_TASK_FROM_NOTIF
                    putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
                }
                val completePi = PendingIntent.getBroadcast(
                    context, taskId.hashCode() xor 0x1000,
                    completeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val snoozeIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                    action = NotificationScheduler.ACTION_SNOOZE_TASK_FROM_NOTIF
                    putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationScheduler.EXTRA_TASK_TITLE, taskTitle)
                }
                val snoozePi = PendingIntent.getBroadcast(
                    context, taskId.hashCode() xor 0x2000,
                    snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val reminderLabel = task.reminderOffsetMinutes.let { offset ->
                    when (offset) {
                        0 -> "Due now"
                        in 1..59 -> "${offset}m before due"
                        1440 -> "1 day before due"
                        else -> "${offset?.div(60)}h before due"
                    }
                }

                val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("⏰ Task Reminder: $taskTitle")
                    .setContentText(reminderLabel)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(contentPi)
                    .setAutoCancel(true)
                    .addAction(android.R.drawable.ic_menu_send, "✓ Complete", completePi)
                    .addAction(android.R.drawable.ic_menu_recent_history, "💤 Snooze 15m", snoozePi)
                    .build()

                nm.notify(taskId.hashCode(), notification)
            }

            NotificationScheduler.ACTION_COMPLETE_TASK_FROM_NOTIF -> {
                val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
                val today = LocalDate.now()
                val updatedTasks = state.tasks.map { task ->
                    if (task.id != taskId) return@map task
                    if (task.recurrence != RecurrenceType.NONE) {
                        val nextDate = RecurrenceEngine.calculateNextDueDate(task, today)
                        task.copy(
                            dueDate = nextDate?.toString() ?: task.dueDate,
                            myDayDate = if (nextDate == today) today.toString() else task.myDayDate,
                            isMyDay = if (nextDate == today) true else task.isMyDay,
                            steps = task.steps.map { it.copy(isDone = false) }
                        )
                    } else {
                        task.copy(isCompleted = true)
                    }
                }
                HabitOSStorage.save(context, state.copy(tasks = updatedTasks))
                NotificationScheduler.scheduleTaskReminderAlarms(context, updatedTasks)
                val nm2 = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm2.cancel(taskId.hashCode())
            }

            NotificationScheduler.ACTION_SNOOZE_TASK_FROM_NOTIF -> {
                val taskId = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_ID) ?: return
                val taskTitle = intent.getStringExtra(NotificationScheduler.EXTRA_TASK_TITLE) ?: "Task"
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val snoozeMillis = System.currentTimeMillis() + 15 * 60 * 1000L

                val snoozeFireIntent = Intent(context, HabitOSAlarmReceiver::class.java).apply {
                    action = NotificationScheduler.ACTION_TASK_REMINDER
                    putExtra(NotificationScheduler.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationScheduler.EXTRA_TASK_TITLE, taskTitle)
                }
                val snoozePi = PendingIntent.getBroadcast(
                    context, taskId.hashCode() xor 0x3000,
                    snoozeFireIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                NotificationScheduler.setSafeExactAlarm(am, snoozeMillis, snoozePi)
                val nm2 = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm2.cancel(taskId.hashCode())
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val state = HabitOSStorage.load(context)
            NotificationScheduler.scheduleAllAlarms(context, state)
        }
    }
}
