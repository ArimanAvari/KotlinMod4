package com.example.kotlinmod4

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        if (ReminderPrefs.isEnabled(context)) {
            val nextReminder = ReminderScheduler.scheduleNextReminder(context)
            ReminderPrefs.setReminderEnabled(context, true, nextReminder)
        }
    }
}
