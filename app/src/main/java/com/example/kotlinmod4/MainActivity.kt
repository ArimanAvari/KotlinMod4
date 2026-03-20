package com.example.kotlinmod4

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var titleText: TextView
    private lateinit var nextReminderText: TextView
    private lateinit var statusDot: View
    private lateinit var statusValueText: TextView
    private lateinit var toggleButton: Button
    private lateinit var hintText: TextView

    private var pendingEnableAfterPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableReminderFlow()
        } else {
            hintText.text = "Разрешение на уведомления не выдано"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        titleText = findViewById(R.id.titleText)
        nextReminderText = findViewById(R.id.nextReminderText)
        statusDot = findViewById(R.id.statusDot)
        statusValueText = findViewById(R.id.statusValueText)
        toggleButton = findViewById(R.id.toggleButton)
        hintText = findViewById(R.id.hintText)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        titleText.text = "Напоминание о таблетке"

        toggleButton.setOnClickListener {
            if (ReminderPrefs.isEnabled(this)) {
                disableReminder()
            } else {
                requestNotificationPermissionIfNeeded()
            }
        }

        renderState()
    }

    override fun onResume() {
        super.onResume()
        if (pendingEnableAfterPermission && canScheduleExactAlarm()) {
            pendingEnableAfterPermission = false
            enableReminderFlow()
        } else {
            renderState()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        enableReminderFlow()
    }

    private fun enableReminderFlow() {
        if (!canScheduleExactAlarm()) {
            pendingEnableAfterPermission = true
            hintText.text = "Разреши точные будильники, чтобы поставить напоминание на 20:00"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
            return
        }

        val triggerAtMillis = ReminderScheduler.scheduleNextReminder(this)
        ReminderPrefs.setReminderEnabled(this, true, triggerAtMillis)
        hintText.text = "Напоминание включено"
        renderState()
    }

    private fun disableReminder() {
        ReminderScheduler.cancelReminder(this)
        ReminderPrefs.disableReminder(this)
        hintText.text = "Напоминание выключено"
        renderState()
    }

    private fun canScheduleExactAlarm(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = getSystemService(AlarmManager::class.java)
        return alarmManager.canScheduleExactAlarms()
    }

    private fun renderState() {
        val enabled = ReminderPrefs.isEnabled(this)
        val nextReminderAt = ReminderPrefs.getNextReminderAt(this)

        val color = if (enabled) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        statusDot.background = shape

        statusValueText.text = if (enabled) "Включено" else "Выключено"
        toggleButton.text = if (enabled) "Выключить напоминание" else "Включить напоминание"

        nextReminderText.text = if (enabled && nextReminderAt > 0L) {
            "Следующее напоминание: ${ReminderScheduler.formatReminderTime(nextReminderAt)}"
        } else {
            "Следующее напоминание не установлено"
        }
    }
}
