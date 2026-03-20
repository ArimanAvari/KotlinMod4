package com.example.kotlinmod4

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: CurrencyViewModel by viewModels()

    private lateinit var rateText: TextView
    private lateinit var arrowText: TextView
    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        rateText = findViewById(R.id.rateText)
        arrowText = findViewById(R.id.arrowText)
        statusText = findViewById(R.id.statusText)
        refreshButton = findViewById(R.id.refreshButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        refreshButton.setOnClickListener {
            viewModel.refreshNow()
        }

        observeCurrency()
    }

    private fun observeCurrency() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    rateText.text = "%.2f RUB".format(state.rate)
                    statusText.text = state.statusText

                    when {
                        state.direction > 0 -> {
                            arrowText.text = "↑"
                            arrowText.setTextColor(Color.parseColor("#2E7D32"))
                        }

                        state.direction < 0 -> {
                            arrowText.text = "↓"
                            arrowText.setTextColor(Color.parseColor("#C62828"))
                        }

                        else -> {
                            arrowText.text = "•"
                            arrowText.setTextColor(Color.parseColor("#757575"))
                        }
                    }
                }
            }
        }
    }
}
