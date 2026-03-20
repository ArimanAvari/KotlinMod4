package com.example.kotlinmod4

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class AddWatermarkWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val fileName = inputData.getString(ChainKeys.KEY_PROCESSED_NAME)
                ?: return Result.failure(
                    Data.Builder()
                        .putString(ChainKeys.KEY_ERROR, "Нет файла после этапа сжатия")
                        .build()
                )

            for (progress in listOf(15, 40, 65, 85, 100)) {
                setProgress(
                    Data.Builder()
                        .putString(ChainKeys.KEY_STATUS, "Добавляем водяной знак...")
                        .putInt(ChainKeys.KEY_PROGRESS, progress)
                        .build()
                )
                delay(250)
            }

            Result.success(
                Data.Builder()
                    .putString(ChainKeys.KEY_PROCESSED_NAME, "watermarked_$fileName")
                    .putString(ChainKeys.KEY_STATUS, "Водяной знак добавлен")
                    .putInt(ChainKeys.KEY_PROGRESS, 100)
                    .build()
            )
        } catch (e: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(ChainKeys.KEY_ERROR, "Ошибка на этапе водяного знака: ${e.message}")
                    .build()
            )
        }
    }
}
