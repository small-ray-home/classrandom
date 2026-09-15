package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

data class ClassPeriod(
    val periodNumber: Int,
    val name: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    val startSecondsOfDay: Int = startHour * 3600 + startMinute * 60
    val endSecondsOfDay: Int = endHour * 3600 + endMinute * 60

    val timeRangeString: String = String.format(
        "%02d:%02d ~ %02d:%02d",
        startHour, startMinute, endHour, endMinute
    )
}

sealed interface ScheduleStatus {
    val isClass: Boolean
    val label: String
    val detail: String

    data class InClass(
        val period: ClassPeriod,
        val elapsedSeconds: Int,
        val totalSeconds: Int
    ) : ScheduleStatus {
        val remainingMinutes: Int
            get() = ((totalSeconds - elapsedSeconds) / 60).coerceAtLeast(0)
        override val isClass: Boolean = true
        override val label: String
            get() = "上課中：${period.name} (${period.timeRangeString})"
        override val detail: String
            get() = "距離下課還有約 $remainingMinutes 分鐘，抽籤功能開放中。"
    }

    data class InBreak(
        val previousPeriod: ClassPeriod?,
        val nextPeriod: ClassPeriod?,
        override val label: String
    ) : ScheduleStatus {
        override val isClass: Boolean = false
        override val detail: String
            get() = if (nextPeriod != null) {
                "下課休息中，距離 ${nextPeriod.name} (${String.format("%02d:%02d", nextPeriod.startHour, nextPeriod.startMinute)}) 開始上課時將自動重設名單。"
            } else {
                "目前為非上課時間。"
            }
    }
}

object ClassScheduleManager {

    const val REQUIRED_PASSWORD = "034565"
    private const val PREFS_NAME = "schedule_reset_prefs"
    private const val KEY_LAST_RESET_PERIOD = "last_reset_period_key"

    val PERIODS: List<ClassPeriod> = listOf(
        ClassPeriod(1, "第 1 節", 8, 30, 9, 15),
        ClassPeriod(2, "第 2 節", 9, 25, 10, 10),
        ClassPeriod(3, "第 3 節", 10, 20, 11, 5),
        ClassPeriod(4, "第 4 節", 11, 15, 12, 0),
        ClassPeriod(5, "第 5 節", 12, 5, 13, 10),
        ClassPeriod(6, "第 6 節", 13, 15, 14, 0),
        ClassPeriod(7, "第 7 節", 14, 10, 14, 55),
        ClassPeriod(8, "第 8 節", 15, 5, 15, 50),
        ClassPeriod(9, "第 9 節", 16, 0, 16, 45)
    )

    fun getCurrentStatus(calendar: Calendar = Calendar.getInstance()): ScheduleStatus {
        val currentSeconds = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                calendar.get(Calendar.MINUTE) * 60 +
                calendar.get(Calendar.SECOND)

        // Check if current time falls within any class period
        for (period in PERIODS) {
            if (currentSeconds in period.startSecondsOfDay until period.endSecondsOfDay) {
                val elapsed = currentSeconds - period.startSecondsOfDay
                val total = period.endSecondsOfDay - period.startSecondsOfDay
                return ScheduleStatus.InClass(
                    period = period,
                    elapsedSeconds = elapsed,
                    totalSeconds = total
                )
            }
        }

        // Otherwise, it is break / non-class time
        var prevPeriod: ClassPeriod? = null
        var nextPeriod: ClassPeriod? = null

        for (i in PERIODS.indices) {
            val p = PERIODS[i]
            if (currentSeconds < p.startSecondsOfDay) {
                nextPeriod = p
                if (i > 0) prevPeriod = PERIODS[i - 1]
                break
            }
            if (currentSeconds >= p.endSecondsOfDay) {
                prevPeriod = p
            }
        }

        val label = when {
            prevPeriod != null && nextPeriod != null -> {
                "${prevPeriod.name} 下課休息中"
            }
            prevPeriod == null && nextPeriod != null -> {
                "早自習 / 課前準備時間"
            }
            else -> {
                "放學非上課時間"
            }
        }

        return ScheduleStatus.InBreak(
            previousPeriod = prevPeriod,
            nextPeriod = nextPeriod,
            label = label
        )
    }

    fun isClassTime(calendar: Calendar = Calendar.getInstance()): Boolean {
        return getCurrentStatus(calendar) is ScheduleStatus.InClass
    }

    fun isBreakTime(calendar: Calendar = Calendar.getInstance()): Boolean {
        return !isClassTime(calendar)
    }

    /**
     * Checks if current time entered a new class period that hasn't been reset yet.
     * If so, executes repository.resetCurrentRound() and records the period key.
     * Returns true if a reset was performed.
     */
    suspend fun checkAndAutoReset(context: Context, repository: StudentRepository): Boolean {
        val calendar = Calendar.getInstance()
        val status = getCurrentStatus(calendar)

        if (status is ScheduleStatus.InClass) {
            val year = calendar.get(Calendar.YEAR)
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val periodKey = "$year-$dayOfYear-period-${status.period.periodNumber}"

            val prefs = getPrefs(context)
            val lastResetKey = prefs.getString(KEY_LAST_RESET_PERIOD, null)

            if (lastResetKey != periodKey) {
                repository.resetCurrentRound()
                prefs.edit().putString(KEY_LAST_RESET_PERIOD, periodKey).apply()
                return true
            }
        }
        return false
    }

    fun verifyPassword(input: String): Boolean {
        return input.trim() == REQUIRED_PASSWORD
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
