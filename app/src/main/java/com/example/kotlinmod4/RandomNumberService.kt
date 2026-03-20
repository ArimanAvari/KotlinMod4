package com.example.kotlinmod4

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class RandomNumberService : Service() {

    private val binder = RandomBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var randomJob: Job? = null
    private var onNumberChanged: ((Int) -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder {
        startGeneratorIfNeeded()
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopGenerator()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopGenerator()
        serviceScope.cancel()
        super.onDestroy()
    }

    fun setOnNumberChangedListener(listener: ((Int) -> Unit)?) {
        onNumberChanged = listener
    }

    private fun startGeneratorIfNeeded() {
        if (randomJob?.isActive == true) return

        randomJob = serviceScope.launch {
            while (isActive) {
                val value = Random.nextInt(0, 101)
                onNumberChanged?.invoke(value)
                delay(1_000)
            }
        }
    }

    private fun stopGenerator() {
        randomJob?.cancel()
        randomJob = null
    }

    inner class RandomBinder : Binder() {
        fun getService(): RandomNumberService = this@RandomNumberService
    }
}
