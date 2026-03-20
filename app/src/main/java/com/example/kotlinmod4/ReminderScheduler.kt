package com.example.kotlinmod4

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object ReminderScheduler {

    private const val REQUEST_CODE = 5011

    fun scheduleNextReminder(context: Context): Long {
        val triggerAtMillis = calculateNextTrigger()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = createPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }

        return triggerAtMillis
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(createPendingIntent(context))
    }

    fun formatReminderTime(triggerAtMillis: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = triggerAtMillis }
        val label = when {
            isSameDay(now, target) -> "сегодня"
            isTomorrow(now, target) -> "завтра"
            else -> SimpleDateFormat("dd.MM", Locale("ru")).format(Date(triggerAtMillis))
        }

        return "$label в 20:00"
    }

    private fun calculateNextTrigger(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return calendar.timeInMillis
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, PillReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun isTomorrow(now: Calendar, target: Calendar): Boolean {
        val tomorrow = now.clone() as Calendar
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)
        return isSameDay(tomorrow, target)
    }
}
