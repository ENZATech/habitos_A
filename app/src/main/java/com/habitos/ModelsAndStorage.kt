package com.habitos.app

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

// ─── Priority ────────────────────────────────────────────────────────────────

enum class Priority(val label: String, val shortLabel: String, val colorHex: Long) {
    P1("P1 Urgent", "P1", 0xFFDC2626),
    P2("P2 High", "P2", 0xFFEA580C),
    P3("P3 Medium", "P3", 0xFF2563EB),
    P4("P4 Normal", "P4", 0xFF78716C)
}

// ─── Recurrence ──────────────────────────────────────────────────────────────

enum class RecurrenceType(val label: String) {
    NONE("Does not repeat"),
    DAILY("Every day"),
    WEEKDAYS("Every weekday (Mon-Fri)"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year"),
    AFTER_COMPLETION("Every X days after completion")
}

object RecurrenceEngine {
    fun calculateNextDueDate(task: TaskItem, today: LocalDate): LocalDate? {
        val base = if (task.dueDate.isNullOrBlank()) today
        else runCatching { LocalDate.parse(task.dueDate) }.getOrElse { today }

        return when (task.recurrence) {
            RecurrenceType.NONE -> null
            RecurrenceType.DAILY -> base.plusDays(1)
            RecurrenceType.WEEKDAYS -> {
                var next = base.plusDays(1)
                while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                    next = next.plusDays(1)
                }
                next
            }
            RecurrenceType.WEEKLY -> base.plusWeeks(1)
            RecurrenceType.MONTHLY -> base.plusMonths(1)
            RecurrenceType.YEARLY -> base.plusYears(1)
            RecurrenceType.AFTER_COMPLETION -> {
                val days = task.customRecurrenceDays.coerceAtLeast(1)
                today.plusDays(days.toLong())
            }
        }
    }
}

// ─── SmartTaskParser ──────────────────────────────────────────────────────────

data class ParsedTask(
    val title: String,
    val priority: Priority?,
    val durationMinutes: Int?,
    val dueDate: String?,
    val recurrence: RecurrenceType?,
    val labels: List<String>
)

object SmartTaskParser {
    private val P_PRIORITY = Regex("""\bp([1-4])\b""", RegexOption.IGNORE_CASE)
    private val P_DURATION = Regex("""(?:for\s+)?(\d+(?:\.\d+)?)\s*(m(?:in(?:utes?)?)?|h(?:ours?)?)\b""", RegexOption.IGNORE_CASE)
    private val P_TODAY = Regex("""\btoday\b""", RegexOption.IGNORE_CASE)
    private val P_TOMORROW = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
    private val P_NEXT_WEEK = Regex("""\bnext\s+week\b""", RegexOption.IGNORE_CASE)
    private val P_LABEL = Regex("""@(\w+)""")
    private val P_RECURRENCE_DAILY = Regex("""\bevery\s+day\b""", RegexOption.IGNORE_CASE)
    private val P_RECURRENCE_WEEKDAY = Regex("""\bevery\s+weekday\b""", RegexOption.IGNORE_CASE)
    private val P_RECURRENCE_WEEKLY = Regex("""\bevery\s+week\b""", RegexOption.IGNORE_CASE)
    private val P_RECURRENCE_MONTHLY = Regex("""\bevery\s+month\b""", RegexOption.IGNORE_CASE)

    fun parse(rawInput: String): ParsedTask {
        var text = rawInput.trim()
        val today = LocalDate.now()

        val prioMatch = P_PRIORITY.find(text)
        val priority: Priority? = prioMatch?.groupValues?.get(1)?.toIntOrNull()?.let {
            Priority.entries.getOrNull(it - 1)
        }
        if (prioMatch != null) text = text.replace(prioMatch.value, "").trim()

        var durationMinutes: Int? = null
        val durMatch = P_DURATION.find(text)
        if (durMatch != null) {
            val amount = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = durMatch.groupValues[2].lowercase()
            durationMinutes = if (unit.startsWith("h")) (amount * 60).toInt() else amount.toInt()
            text = text.replace(durMatch.value, "").trim()
        }

        var dueDate: String? = null
        when {
            P_TODAY.containsMatchIn(text) -> { dueDate = today.toString(); text = P_TODAY.replace(text, "").trim() }
            P_TOMORROW.containsMatchIn(text) -> { dueDate = today.plusDays(1).toString(); text = P_TOMORROW.replace(text, "").trim() }
            P_NEXT_WEEK.containsMatchIn(text) -> { dueDate = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toString(); text = P_NEXT_WEEK.replace(text, "").trim() }
        }

        var recurrence: RecurrenceType? = null
        when {
            P_RECURRENCE_WEEKDAY.containsMatchIn(text) -> { recurrence = RecurrenceType.WEEKDAYS; text = P_RECURRENCE_WEEKDAY.replace(text, "").trim() }
            P_RECURRENCE_DAILY.containsMatchIn(text) -> { recurrence = RecurrenceType.DAILY; text = P_RECURRENCE_DAILY.replace(text, "").trim() }
            P_RECURRENCE_WEEKLY.containsMatchIn(text) -> { recurrence = RecurrenceType.WEEKLY; text = P_RECURRENCE_WEEKLY.replace(text, "").trim() }
            P_RECURRENCE_MONTHLY.containsMatchIn(text) -> { recurrence = RecurrenceType.MONTHLY; text = P_RECURRENCE_MONTHLY.replace(text, "").trim() }
        }

        val labels = P_LABEL.findAll(text).map { it.groupValues[1] }.toList()
        text = P_LABEL.replace(text, "").trim()

        val cleanTitle = text.replace(Regex("""\s{2,}"""), " ").trim().ifBlank { rawInput.trim() }

        return ParsedTask(
            title = cleanTitle,
            priority = priority,
            durationMinutes = durationMinutes,
            dueDate = dueDate,
            recurrence = recurrence,
            labels = labels
        )
    }
}

// ─── Data Models ──────────────────────────────────────────────────────────────

data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)

data class TaskComment(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val priority: Priority = Priority.P4,
    val isImportant: Boolean = false,
    val isMyDay: Boolean = false,
    val myDayDate: String = "",
    val dueDate: String? = null,
    val dueTime: String? = null,
    val durationMinutes: Int? = null,
    val deadlineDate: String? = null,
    val recurrence: RecurrenceType = RecurrenceType.NONE,
    val customRecurrenceDays: Int = 1,
    val reminderOffsetMinutes: Int? = null,
    val listId: String? = null,
    val cue: String = "",
    val steps: List<SubTask> = emptyList(),
    val notes: String = "",
    val labels: List<String> = emptyList(),
    val comments: List<TaskComment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class HabitItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val listId: String? = null,
    val cue: String = "",
    val completedDates: Set<String> = emptySet(),
    val recordStreak: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun currentStreak(today: LocalDate = LocalDate.now()): Int {
        var streak = 0
        var cursor = if (completedDates.contains(today.toString())) today else today.minusDays(1)
        while (completedDates.contains(cursor.toString())) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun updatedWithToggle(dateStr: String): HabitItem {
        val nextSet = completedDates.toMutableSet()
        if (nextSet.contains(dateStr)) nextSet.remove(dateStr) else nextSet.add(dateStr)
        val temp = copy(completedDates = nextSet)
        return temp.copy(recordStreak = maxOf(recordStreak, temp.currentStreak()))
    }
}

// ─── Birthday Models ──────────────────────────────────────────────────────────

data class BirthdayPerson(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relation: String = "Friend",
    val dayOfMonth: Int,
    val monthValue: Int,
    val birthYear: Int = 2008,
    val phone: String = "",
    val customWish: String? = null,
    val wishedYear: Int = 0
) {
    fun hasCustomWish(): Boolean = !customWish.isNullOrBlank()

    fun isBirthdayOn(date: LocalDate): Boolean =
        date.monthValue == monthValue && date.dayOfMonth == dayOfMonth

    fun daysUntilNext(today: LocalDate = LocalDate.now()): Long {
        var next = LocalDate.of(
            today.year,
            monthValue,
            dayOfMonth.coerceAtMost(safeDay(today.year, monthValue, dayOfMonth))
        )
        if (next.isBefore(today)) {
            val nextYear = today.year + 1
            next = LocalDate.of(
                nextYear,
                monthValue,
                dayOfMonth.coerceAtMost(safeDay(nextYear, monthValue, dayOfMonth))
            )
        }
        return ChronoUnit.DAYS.between(today, next)
    }

    fun turningAge(today: LocalDate = LocalDate.now()): Int? {
        if (birthYear !in 1900..today.year) return null
        var nextYear = today.year
        val thisYearBday = LocalDate.of(
            today.year,
            monthValue,
            dayOfMonth.coerceAtMost(safeDay(today.year, monthValue, dayOfMonth))
        )
        if (thisYearBday.isBefore(today.minusDays(1))) nextYear += 1
        return (nextYear - birthYear).coerceAtLeast(0)
    }

    private fun safeDay(year: Int, month: Int, day: Int): Int =
        if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) 28 else day
}

object BirthdayWishTemplates {
    const val TEMPLATE_COUNT = 6

    fun generateWish(person: BirthdayPerson, isBelated: Boolean, index: Int = 0): String {
        val rel = person.relation.ifBlank { "Friend" }
        val name = person.name
        val age = person.turningAge()?.let { " (turning $it!)" } ?: ""

        val pool = if (isBelated) {
            when (rel) {
                "Brother" -> listOf(
                    "Hey bro $name! 🚨 Happy Belated Birthday! Sorry I missed it, but wishing you all the best belatedly. You're awesome! 💪🎂",
                    "Better late than never, $name! 🎉 Happy Belated Birthday, bro! Hope your day was legendary. Miss you! 🔥",
                    "Oops, I'm late bro! 😅 Happy Belated Birthday $name! You deserve all the celebrations, even delayed ones! 🎂💚",
                    "Big bro vibes — $name, belated birthday wishes! May this year be your strongest yet. Love you bro! 🏆🎉",
                    "Late to the party but here with love! Happy Belated Birthday $name 🎂 You're one of a kind, bro! 🔥✨",
                    "My bad bro! Happy Belated Birthday $name! 🚨 Hope you had an epic day and this year brings you everything you want! 💫"
                )
                "Sister" -> listOf(
                    "Belated Birthday sis $name! 🌸 You deserve all the love in the world, even if it arrives a little late! 🎂💜",
                    "Late but heartfelt! Happy Belated Birthday $name! 🌺 You make everything better. So glad to have you as my sister! ✨",
                    "Oops! Belated Birthday $name sis! 🚨🎉 Hope yesterday was magical. This whole year should celebrate you! 💜🌸",
                    "No amount of lateness can dim my love for you! Happy Belated Birthday $name! 🎂 You shine bright always! 💖",
                    "Belated wishes for the best sister $name! 🌷 Sorry I'm late—I was busy celebrating you in my heart! 😄💜",
                    "Sister $name, it's belated but it's sincere! 🌸 Happy Birthday! May joy follow you all year long! 🎂✨"
                )
                "Best Friend" -> listOf(
                    "Best friend alert! 🚨 Happy Belated Birthday $name! You know I love you even when I'm late! 🎂🔥",
                    "Late to the party but first in your heart! Happy Belated Birthday $name bestie! 🎉💚",
                    "My bad bestie! 😅 Happy Belated Birthday $name! You deserve a whole week of celebrations! 🥳🎂",
                    "Better late than never! $name, Happy Belated Birthday! You're the best human I know. 🙌🎉",
                    "Bestie $name — fashionably late, but always here! Happy Belated Birthday! 🎂✨ You rock! 💚",
                    "BELATED but BEYOND heartfelt! Happy Birthday $name! You're irreplaceable in my life! 🌟🎉"
                )
                "Mother" -> listOf(
                    "Mom $name, belated birthday wishes! 🌺 Your love is timeless, like this wish that knows no bounds! 💜🎂",
                    "Late but filled with love! Happy Belated Birthday Mom $name! You're my hero every single day! 🌸💐",
                    "Dear Mom $name, sorry I'm a little late — but my love for you is never late! 🎂 Happy Birthday! 🌹",
                    "Happy Belated Birthday $name! 🌺 To the most wonderful Mom — your day deserved a whole month of joy! 💜",
                    "Mom, $name — belated but sincere! 🌸 Wishing you all the happiness you've brought to our lives! 🎂💐",
                    "It's late but my gratitude and love for you, Mom $name, is eternal! Happy Belated Birthday! 🌹✨"
                )
                "Father" -> listOf(
                    "Dad $name, belated birthday! 🎩 Your guidance never comes late, and neither does my love! 🎂🙏",
                    "Happy Belated Birthday Dad $name! 🎉 Sorry I missed the day — you deserve endless celebrations! 🏆",
                    "Late but proud to call you Dad! $name, Happy Belated Birthday! You're my greatest inspiration! 💪🎂",
                    "Dear Dad $name — a belated wish from the heart! 🎩 You make every day feel like a celebration! 🎉✨",
                    "Dad $name, I'm late but my respect and love for you is never late! Happy Belated Birthday! 🙏🎂",
                    "Belated Birthday Dad $name! 🎉 Thank you for being my guiding star. This year, all the best to you! 🌟"
                )
                "Partner" -> listOf(
                    "My love, belated birthday! 💕 $name, sorry for the delay — you deserve all the stars! 🌟🎂",
                    "Late but loving! Happy Belated Birthday $name! You're the best thing in my life! 💖🎉",
                    "Oops my love! 😅 Happy Belated Birthday $name! I owe you extra celebration this week! 💕🥳",
                    "$name, belated wishes from your most devoted admirer! 💕 Happy Birthday — you are everything! 🌹🎂",
                    "Late but never lapsed in love! Happy Belated Birthday $name! 💖 You deserve the world! ✨",
                    "My darling $name — fashionably late! 😄 Happy Belated Birthday! Every day with you is a gift! 💕🎂"
                )
                else -> listOf(
                    "Hey $name! 🚨 Happy Belated Birthday! Hope it was amazing even without my wishes on time! 🎂🎉",
                    "Better late than never! Happy Belated Birthday $name! 🎉 Hope this year brings you joy! 🌟",
                    "Oops! Missed the day but not the love! Happy Belated Birthday $name! 🎂 Wishing you the best! ✨",
                    "$name, belated wishes but genuine ones! 🎉 Hope your birthday was fantastic! 🎂🌟",
                    "Late birthday wishes $name! 🚨 But my care for you is always on time! Happy Belated Birthday! 🎂",
                    "Happy Belated Birthday $name! 🎉 Sorry for the delay — you deserve only the best, always! 🌟🎂"
                )
            }
        } else {
            when (rel) {
                "Brother" -> listOf(
                    "Happy Birthday bro $name!$age 🎂🔥 You're the best brother — wishing you an epic year ahead! 💪🎉",
                    "To my awesome brother $name! 🎉 Happy Birthday! May this year level you up in every way! 🚀✨",
                    "Bro $name, it's your day! 🎂🔥 Happy Birthday! Wishing you hustle, happiness, and all the wins! 💚",
                    "Happy Birthday $name!$age 🏆 You inspire me daily. Have the best year of your life, bro! 🎉💪",
                    "Big day for a big bro! Happy Birthday $name! 🎂 May today be just the start of an amazing year! 🔥",
                    "$name bro — HBD! 🎉 Wishing you health, wealth, and all the success you deserve! 🏆🎂"
                )
                "Sister" -> listOf(
                    "Happy Birthday sis $name!$age 🌸🎂 You light up every room you enter! Wishing you the most magical year! 💜✨",
                    "To my incredible sister $name! 🌺 Happy Birthday! You deserve all the love and joy today and always! 💜🎉",
                    "$name sis, it's YOUR day! 🌸🎂 Wishing you sunshine, laughter, and all your dreams come true! 💜",
                    "Happy Birthday to the most amazing sister $name!$age 💜 Today is all about you — celebrate big! 🌺✨",
                    "Sister $name, you make life beautiful! 🌸 Happy Birthday! May this year bring you everything you wish! 💜🎂",
                    "HBD $name! 🌺💜 You're not just my sister, you're my best friend. Have the best birthday ever! 🎉✨"
                )
                "Best Friend" -> listOf(
                    "HAPPY BIRTHDAY BESTIE $name!$age 🎉🔥 Today is all about you! Wishing you the most epic day! 🎂💚",
                    "To my ride-or-die $name! 🙌 Happy Birthday! You make life a 10/10 experience! 🎉🌟",
                    "$name bestie! HBD! 🎂💚 You are literally the best human alive. Have the best day ever! 🔥🎉",
                    "Happy Birthday to my person, $name!$age 🌟 Wishing you all the joy and adventure in the world! 🎂💚",
                    "Bestie $name, you deserve ALL the celebrations! 🎉🎂 Happy Birthday! Life is better with you! 💚✨",
                    "HBD $name! 🙌🎉 May your birthday be as awesome as you are — and that's saying A LOT! 🔥🎂"
                )
                "Mother" -> listOf(
                    "Happy Birthday Mom $name!$age 🌺💜 You are the heart of our family. Wishing you all the love today! 🎂💐",
                    "To the most wonderful Mom $name! 🌸 Happy Birthday! Your love and strength inspire me every day! 💜🌺",
                    "Mom $name — Happy Birthday! 🎂🌹 Thank you for everything you do. Today, let us celebrate YOU! 💜✨",
                    "Wishing my incredible Mom $name a magical birthday!$age 🌺 You deserve all the happiness in the world! 💜🎂",
                    "Happy Birthday to the best Mom $name! 🌸💜 You are my hero. May this year bring you endless joy! 🎉🌹",
                    "Mom $name, today is YOUR day! 🌺🎂 Wishing you health, happiness, and all things beautiful! 💜✨"
                )
                "Father" -> listOf(
                    "Happy Birthday Dad $name!$age 🎩🎉 You are my greatest inspiration. Wishing you a wonderful year! 🏆🎂",
                    "To the strongest man I know, Dad $name! 🎉 Happy Birthday! May this year be your best yet! 💪🎂",
                    "Dad $name — Happy Birthday! 🎩 Thank you for your wisdom and love. You mean the world to me! 🎉✨",
                    "Wishing my incredible Dad $name a fantastic birthday!$age 🎂 You make every day better! 🏆💪",
                    "Happy Birthday Dad $name! 🎉 Your guidance shapes who I am. Here's to you today and always! 🎩🎂",
                    "Dad $name — HBD! 🏆🎂 You've always been my rock. Wishing you health and joy today! 🎉💪"
                )
                "Partner" -> listOf(
                    "Happy Birthday my love $name!$age 💕🎂 Every day with you is a gift. Today is all yours! 🌹✨",
                    "To the love of my life $name! 💖 Happy Birthday! You make me the luckiest person alive! 🎂🌹",
                    "$name, Happy Birthday! 💕 You are my sunshine, my joy, my everything. Celebrate big today! 🎉🌹",
                    "Happy Birthday darling $name!$age 💖 Wishing you a day as magical as the joy you bring me! 🎂✨",
                    "My love $name — it's your special day! 🌹💕 Wishing you all the happiness you deserve! 🎂🎉",
                    "HBD $name! 💖 You are extraordinary in every way. May this year bring you all you dream of! 🌹✨"
                )
                else -> listOf(
                    "Happy Birthday $name!$age 🎂🎉 Wishing you an amazing day filled with joy and good vibes! 🙌✨",
                    "HBD $name! 🎉🌟 May this new year of your life be your best one yet! Celebrate big! 🎂✨",
                    "Happy Birthday $name!$age 🎂 Sending you the best wishes and positive vibes today! 🙌🎉",
                    "It's your day $name! 🎉🎂 Wishing you joy, laughter, and success! 🌟✨",
                    "Happy Birthday $name! 🎂🌟 Hope today brings great memories, good food, and a brilliant start to the year! 🥳✨",
                    "Cheers to you on your birthday, $name!$age 🎉 Wishing you 365 days of good health and big achievements! 🎂✨"
                )
            }
        }
        return pool[index.mod(pool.size)]
    }
}

// ─── Date Format Helper ───────────────────────────────────────────────────────

object DateFormatHelper {
    fun shortDate(dateStr: String?, today: LocalDate = LocalDate.now()): String {
        if (dateStr.isNullOrBlank()) return ""
        val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return dateStr
        return when (parsed) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> "${parsed.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${parsed.dayOfMonth}"
        }
    }

    fun formatTimeBlock(dueTime: String?, durationMinutes: Int?): String {
        if (dueTime.isNullOrBlank()) {
            return if (durationMinutes != null) "⏱️ ${formatDuration(durationMinutes)}" else ""
        }
        val parts = dueTime.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return "🕒 $dueTime"
        val startH = parts[0]; val startM = parts[1]
        return if (durationMinutes != null) {
            val totalEnd = startH * 60 + startM + durationMinutes
            val endH = totalEnd / 60 % 24; val endM = totalEnd % 60
            "🕒 %02d:%02d – %02d:%02d (%s)".format(startH, startM, endH, endM, formatDuration(durationMinutes))
        } else "🕒 %02d:%02d".format(startH, startM)
    }

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun deadlinePill(deadlineDate: String?, today: LocalDate = LocalDate.now()): Pair<String, Boolean>? {
        if (deadlineDate.isNullOrBlank()) return null
        val dl = runCatching { LocalDate.parse(deadlineDate) }.getOrNull() ?: return null
        val diff = ChronoUnit.DAYS.between(today, dl)
        return when {
            diff < 0 -> "🚨 Deadline Passed" to true
            diff == 0L -> "🚨 Deadline Today" to true
            diff <= 3L -> "🎯 ${diff}d left" to false
            else -> "🎯 ${diff}d left" to false
        }
    }
}

// ─── App State Models ─────────────────────────────────────────────────────────

data class CustomTaskList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: Long = 0xFF10B981
)

data class UserProfile(
    val isOnboarded: Boolean = false,
    val name: String = "",
    val dob: String = "",
    val gender: String = "",
    val avatarSeed: Int = Random.nextInt(0, 6),
    val permissionsPromptShown: Boolean = false
)

data class AppSettings(
    val vaultPin: String = "1234",
    val eodHour: Int = 21,
    val eodMinute: Int = 0
)

data class PersistedState(
    val tasks: List<TaskItem> = emptyList(),
    val habits: List<HabitItem> = emptyList(),
    val birthdays: List<BirthdayPerson> = emptyList(),
    val customLists: List<CustomTaskList> = listOf(
        CustomTaskList(id = "default_routine", name = "Daily Routine", colorHex = 0xFF10B981),
        CustomTaskList(id = "default_code", name = "C Language & Web Dev", colorHex = 0xFF3B82F6),
        CustomTaskList(id = "default_fitness", name = "Fitness & Health", colorHex = 0xFFF43F5E),
        CustomTaskList(id = "default_goals", name = "Personal & Goals", colorHex = 0xFFA855F7)
    ),
    val userProfile: UserProfile = UserProfile(),
    val settings: AppSettings = AppSettings()
)

object HabitOSStorage {
    private const val PREF_NAME = "habitos_native_vault"
    private const val KEY_STATE = "persisted_state_v3"
    private val gson = Gson()

    fun load(context: Context): PersistedState {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null) ?: return PersistedState()
        return try {
            val type = object : TypeToken<PersistedState>() {}.type
            gson.fromJson(raw, type) ?: PersistedState()
        } catch (e: Exception) {
            PersistedState()
        }
    }

    fun save(context: Context, state: PersistedState) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_STATE, gson.toJson(state)).apply()
    }

    fun clearAllResettingKeepPinAndProfile(
        context: Context,
        currentPin: String,
        currentProfile: UserProfile
    ): PersistedState {
        val clean = PersistedState(
            userProfile = currentProfile,
            settings = AppSettings(vaultPin = currentPin)
        )
        save(context, clean)
        return clean
    }
}

// ─── Week Helper ──────────────────────────────────────────────────────────────

object WeekHelper {
    fun currentWeekDays(today: LocalDate = LocalDate.now()): List<LocalDate> =
        weekDaysForOffset(0, today)

    fun weekDaysForOffset(weekOffset: Int, today: LocalDate = LocalDate.now()): List<LocalDate> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(weekOffset.toLong())
        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    fun isPerfectDay(habits: List<HabitItem>, date: LocalDate): Boolean =
        habits.isNotEmpty() && habits.all { it.completedDates.contains(date.toString()) }

    fun consistencyStreakDays(habits: List<HabitItem>, today: LocalDate = LocalDate.now()): Int {
        if (habits.isEmpty()) return 0
        var streak = 0
        fun hit65(d: LocalDate): Boolean {
            val done = habits.count { it.completedDates.contains(d.toString()) }
            return (done.toDouble() / habits.size.toDouble()) >= 0.65
        }
        var cursor = if (hit65(today)) today else today.minusDays(1)
        while (hit65(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
