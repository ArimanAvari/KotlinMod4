package com.example.kotlinmod4

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class BuildWeatherReportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            setProgress(
                Data.Builder()
                    .putString(WeatherKeys.KEY_STATUS, "Все данные получены, формируем отчёт...")
                    .putInt(WeatherKeys.KEY_PROGRESS, 90)
                    .build()
            )

            setForeground(
                WeatherNotificationHelper.createForegroundInfo(
                    applicationContext,
                    "Все данные получены, формируем отчёт...",
                    90
                )
            )

            delay(1_200)

            val cities = inputData.getStringArray(WeatherKeys.KEY_CITY_LIST)?.toList().orEmpty()
            val temperatures = inputData.getIntArray(WeatherKeys.KEY_TEMPERATURES)?.toList().orEmpty()

            if (cities.isEmpty() || temperatures.isEmpty()) {
                return Result.failure(
                    Data.Builder()
                        .putString(WeatherKeys.KEY_ERROR, "Не удалось получить все данные о погоде")
                        .build()
                )
            }

            val average = temperatures.average().toInt()
            val resultText = buildString {
                appendLine("Отчёт готов! Средняя температура ${formatTemperature(average)}")
                append("Города: ${cities.joinToString()}")
            }

            WeatherNotificationHelper.showFinished(applicationContext, average)

            Result.success(
                Data.Builder()
                    .putString(WeatherKeys.KEY_RESULT_TEXT, resultText)
                    .putString(WeatherKeys.KEY_STATUS, "Отчёт готов")
                    .putInt(WeatherKeys.KEY_PROGRESS, 100)
                    .build()
            )
        } catch (e: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(WeatherKeys.KEY_ERROR, "Ошибка при сборке отчёта: ${e.message}")
                    .build()
            )
        }
    }

    private fun formatTemperature(value: Int): String {
        return if (value > 0) "+$value°C" else "$value°C"
    }
}
