package com.example.kotlinmod4

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay

class UploadPhotoWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val fileName = inputData.getString(ChainKeys.KEY_PROCESSED_NAME)
                ?: return Result.failure(
                    Data.Builder()
                        .putString(ChainKeys.KEY_ERROR, "Нет готового файла для загрузки")
                        .build()
                )

            for (progress in listOf(20, 45, 70, 90, 100)) {
                setProgress(
                    Data.Builder()
                        .putString(ChainKeys.KEY_STATUS, "Загружаем фото в облако...")
                        .putInt(ChainKeys.KEY_PROGRESS, progress)
                        .build()
                )
                delay(300)
            }

            Result.success(
                Data.Builder()
                    .putString(ChainKeys.KEY_RESULT_PATH, "cloud/$fileName")
                    .putString(ChainKeys.KEY_STATUS, "Готово! Фото загружено")
                    .putInt(ChainKeys.KEY_PROGRESS, 100)
                    .build()
            )
        } catch (e: Exception) {
            Result.failure(
                Data.Builder()
                    .putString(ChainKeys.KEY_ERROR, "Ошибка на этапе загрузки: ${e.message}")
                    .build()
            )
        }
    }
}
