package com.example.kotlinmod4

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.ArrayCreatingInputMerger
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkContinuation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        progressBar = findViewById(R.id.progressBar)
        startButton = findViewById(R.id.startButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startButton.setOnClickListener {
            startWeatherCollection()
        }
    }

    private fun startWeatherCollection() {
        val cities = listOf(
            WeatherCity("Москва", 3, 1_400L),
            WeatherCity("Лондон", 10, 2_200L),
            WeatherCity("Нью-Йорк", 5, 2_800L)
        )

        WeatherProgressStore.reset(this, cities.map { it.name })
        WeatherNotificationHelper.showInitial(this, cities.size)

        startButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Собираем прогноз для ${cities.size} городов..."
        resultText.text = ""

        val cityRequests = cities.map { city ->
            OneTimeWorkRequestBuilder<LoadWeatherWorker>()
                .addTag(WeatherKeys.UNIQUE_WORK_NAME)
                .setInputData(
                    Data.Builder()
                        .putString(WeatherKeys.KEY_CITY, city.name)
                        .putInt(WeatherKeys.KEY_TEMPERATURE, city.temperature)
                        .putLong(WeatherKeys.KEY_DELAY_MS, city.delayMs)
                        .putInt(WeatherKeys.KEY_TOTAL_COUNT, cities.size)
                        .build()
                )
                .build()
        }

        val finalWorker = OneTimeWorkRequestBuilder<BuildWeatherReportWorker>()
            .addTag(WeatherKeys.UNIQUE_WORK_NAME)
            .setInputMerger(ArrayCreatingInputMerger::class.java)
            .build()

        val workManager = WorkManager.getInstance(this)
        workManager.cancelAllWorkByTag(WeatherKeys.UNIQUE_WORK_NAME)

        val continuations = cityRequests.map { request ->
            workManager.beginWith(request)
        }

        WorkContinuation.combine(continuations)
            .then(finalWorker)
            .enqueue()

        observeFinalWorker(finalWorker.id)
    }

    private fun observeFinalWorker(workId: UUID) {
        val liveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId)

        liveData.observe(this) { workInfo ->
            if (workInfo == null) return@observe

            val progress = workInfo.progress.getInt(WeatherKeys.KEY_PROGRESS, progressBar.progress)
            val status = workInfo.progress.getString(WeatherKeys.KEY_STATUS)
            if (!status.isNullOrBlank()) {
                statusText.text = status
            }
            progressBar.progress = progress

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    progressBar.progress = 100
                    startButton.isEnabled = true
                    resultText.text = workInfo.outputData.getString(WeatherKeys.KEY_RESULT_TEXT)
                    statusText.text = "Отчёт готов"
                    liveData.removeObservers(this)
                }

                WorkInfo.State.FAILED -> {
                    startButton.isEnabled = true
                    progressBar.visibility = View.INVISIBLE
                    resultText.text = workInfo.outputData.getString(WeatherKeys.KEY_ERROR)
                        ?: "Не удалось собрать отчёт"
                    statusText.text = "Ошибка"
                    liveData.removeObservers(this)
                }

                WorkInfo.State.CANCELLED -> {
                    startButton.isEnabled = true
                    progressBar.visibility = View.INVISIBLE
                    resultText.text = "Работа была отменена"
                    statusText.text = "Отменено"
                    liveData.removeObservers(this)
                }

                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }
    }
}

private data class WeatherCity(
    val name: String,
    val temperature: Int,
    val delayMs: Long
)
