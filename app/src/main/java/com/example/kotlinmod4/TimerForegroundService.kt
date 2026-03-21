package com.example.kotlinmod4

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TimerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private var secondsPassed = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildNotification(secondsPassed))

        if (timerJob?.isActive == true) {
            return START_STICKY
        }

        timerJob = serviceScope.launch {
            while (true) {
                sendTick(secondsPassed)
                updateNotification(secondsPassed)
                delay(1_000)
                secondsPassed++
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        timerJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(seconds: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(seconds))
    }

    private fun buildNotification(seconds: Int): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Таймер работает")
            .setContentText("Прошло $seconds секунд")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun sendTick(seconds: Int) {
        sendBroadcast(
            Intent(ACTION_TICK).putExtra(EXTRA_SECONDS, seconds)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer foreground service",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "Показывает, сколько секунд уже работает таймер"

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_TICK = "com.example.kotlinmod4.ACTION_TICK"
        const val EXTRA_SECONDS = "seconds"

        private const val CHANNEL_ID = "timer_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
