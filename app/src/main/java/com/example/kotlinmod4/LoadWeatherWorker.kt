package com.example.kotlinmod4

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class LoadWeatherWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val city = inputData.getString(WeatherKeys.KEY_CITY)
                ?: return failure("Не передан город")
            val temperature = inputData.getInt(WeatherKeys.KEY_TEMPERATURE, 0)
            val delayMs = inputData.getLong(WeatherKeys.KEY_DELAY_MS, 1_500L)
            val totalCount = inputData.getInt(WeatherKeys.KEY_TOTAL_COUNT, 3)

            setForeground(
                WeatherNotificationHelper.createForegroundInfo(
                    applicationContext,
                    "Загружаем погоду для $totalCount городов...",
                    10
                )
            )

            delay(delayMs)

            val snapshot = WeatherProgressStore.markDone(applicationContext, city)
            val progress = (snapshot.doneCities.size * 100 / snapshot.totalCount).coerceAtLeast(20)
            val text = if (snapshot.pendingCities.isEmpty()) {
                "Все данные получены, формируем отчёт..."
            } else {
                "Готово: ${snapshot.doneCities.joinToString()}, ${snapshot.pendingCities.joinToString()} в процессе..."
            }

            setForeground(
                WeatherNotificationHelper.createForegroundInfo(
                    applicationContext,
                    text,
                    progress
                )
            )

            Result.success(
                Data.Builder()
                    .putStringArray(WeatherKeys.KEY_CITY_LIST, arrayOf(city))
                    .putIntArray(WeatherKeys.KEY_TEMPERATURES, intArrayOf(temperature))
                    .build()
            )
        } catch (e: Exception) {
            failure("Ошибка загрузки погоды: ${e.message}")
        }
    }

    private fun failure(message: String): Result {
        return Result.failure(
            Data.Builder()
                .putString(WeatherKeys.KEY_ERROR, message)
                .build()
        )
    }
}
