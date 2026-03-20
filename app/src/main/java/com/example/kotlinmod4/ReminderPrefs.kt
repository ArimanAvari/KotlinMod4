package com.example.kotlinmod4

import android.content.Context

object ReminderPrefs {

    private const val PREFS_NAME = "pill_reminder_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NEXT_REMINDER_AT = "next_reminder_at"

    fun setReminderEnabled(context: Context, enabled: Boolean, nextReminderAt: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_NEXT_REMINDER_AT, nextReminderAt)
            .apply()
    }

    fun disableReminder(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .putLong(KEY_NEXT_REMINDER_AT, 0L)
            .apply()
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun getNextReminderAt(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_NEXT_REMINDER_AT, 0L)
    }
}
