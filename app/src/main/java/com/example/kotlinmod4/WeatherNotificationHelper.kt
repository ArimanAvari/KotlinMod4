package com.example.kotlinmod4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object WeatherNotificationHelper {

    private const val CHANNEL_ID = "weather_report_channel"
    private const val NOTIFICATION_ID = 3001

    fun showInitial(context: Context, totalCount: Int) {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                context = context,
                title = "Сбор погоды",
                text = "Загружаем погоду для $totalCount городов...",
                ongoing = true,
                progress = 5
            )
        )
    }

    fun createForegroundInfo(
        context: Context,
        text: String,
        progress: Int,
        ongoing: Boolean = true
    ): ForegroundInfo {
        createChannel(context)
        val notification = buildNotification(
            context = context,
            title = "Сбор погоды",
            text = text,
            ongoing = ongoing,
            progress = progress
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    fun showFinished(context: Context, average: Int) {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(
                context = context,
                title = "Отчёт готов",
                text = "Отчёт готов! Средняя температура ${formatTemperature(average)}",
                ongoing = false,
                progress = 100
            )
        )
    }

    private fun buildNotification(
        context: Context,
        title: String,
        text: String,
        ongoing: Boolean,
        progress: Int
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .build()
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weather report",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun formatTemperature(value: Int): String {
        return if (value > 0) "+$value°C" else "$value°C"
    }
}
