package com.example.kotlinmod4

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class CompressPhotoWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val fileName = inputData.getString(ChainKeys.KEY_FILE_NAME) ?: "photo.jpg"

            for (progress in listOf(10, 30, 50, 70, 100)) {
                setProgress(
                    Data.Builder()
                        .putString(ChainKeys.KEY_STATUS, "Сжимаем фото...")
                        .putInt(ChainKeys.KEY_PROGRESS, progress)
                        .build()
                )
                delay(250)
            }

            Result.success(
                Data.Builder()
                    .putString(ChainKeys.KEY_PROCESSED_NAME, "compressed_$fileName")
                    .putString(ChainKeys.KEY_STATUS, "Сжатие завершено")
                    .putInt(ChainKeys.KEY_PROGRESS, 100)
                    .build()
            )
        } catch (e: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(ChainKeys.KEY_ERROR, "Ошибка на этапе сжатия: ${e.message}")
                    .build()
            )
        }
    }
}
