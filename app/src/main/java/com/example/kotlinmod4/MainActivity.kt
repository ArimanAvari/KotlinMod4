package com.example.kotlinmod4

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var secondsInput: EditText
    private lateinit var startTimerButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        secondsInput = findViewById(R.id.secondsInput)
        startTimerButton = findViewById(R.id.startTimerButton)
        statusText = findViewById(R.id.statusText)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startTimerButton.setOnClickListener {
            val seconds = secondsInput.text.toString().trim().toIntOrNull()

            if (seconds == null || seconds <= 0) {
                statusText.text = "Введи положительное количество секунд"
                return@setOnClickListener
            }

            val intent = Intent(this, OneShotTimerService::class.java).apply {
                putExtra(OneShotTimerService.EXTRA_SECONDS, seconds)
            }

            startService(intent)
            statusText.text = "Таймер запущен на $seconds сек."
        }
    }
}
