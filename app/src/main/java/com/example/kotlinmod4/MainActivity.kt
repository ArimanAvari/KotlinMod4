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
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var startButton: Button

    private var currentWorkId: UUID? = null

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
            startProcessingChain()
        }
    }

    private fun startProcessingChain() {
        startButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        statusText.text = "Сжимаем фото..."
        resultText.text = ""

        val inputData = Data.Builder()
            .putString(ChainKeys.KEY_FILE_NAME, "photo_001.jpg")
            .build()

        val compressWork = OneTimeWorkRequestBuilder<CompressPhotoWorker>()
            .setInputData(inputData)
            .build()

        val watermarkWork = OneTimeWorkRequestBuilder<AddWatermarkWorker>().build()
        val uploadWork = OneTimeWorkRequestBuilder<UploadPhotoWorker>().build()

        currentWorkId = uploadWork.id

        WorkManager.getInstance(this)
            .beginWith(compressWork)
            .then(watermarkWork)
            .then(uploadWork)
            .enqueue()

        observeResult(uploadWork.id)
    }

    private fun observeResult(workId: UUID) {
        val liveData = WorkManager.getInstance(this).getWorkInfoByIdLiveData(workId)

        liveData.observe(this) { workInfo ->
            if (workInfo == null) return@observe

            val status = workInfo.progress.getString(ChainKeys.KEY_STATUS)
            val progress = workInfo.progress.getInt(ChainKeys.KEY_PROGRESS, 0)

            if (!status.isNullOrBlank()) {
                statusText.text = status
            }
            progressBar.progress = progress

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    progressBar.progress = 100
                    startButton.isEnabled = true
                    resultText.text = "Готово! Фото загружено\n${workInfo.outputData.getString(ChainKeys.KEY_RESULT_PATH)}"
                    liveData.removeObservers(this)
                }

                WorkInfo.State.FAILED -> {
                    startButton.isEnabled = true
                    progressBar.visibility = View.INVISIBLE
                    resultText.text = workInfo.outputData.getString(ChainKeys.KEY_ERROR)
                        ?: "Ошибка обработки"
                    liveData.removeObservers(this)
                }

                WorkInfo.State.CANCELLED -> {
                    startButton.isEnabled = true
                    progressBar.visibility = View.INVISIBLE
                    resultText.text = "Цепочка была отменена"
                    liveData.removeObservers(this)
                }

                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.BLOCKED -> {
                    progressBar.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            currentWorkId = workId
        }
    }
}
