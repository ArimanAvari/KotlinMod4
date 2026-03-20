package com.example.kotlinmod4

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var factCard: MaterialCardView
    private lateinit var factText: TextView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var newFactButton: Button

    private var lastRenderedFact: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        factCard = findViewById(R.id.factCard)
        factText = findViewById(R.id.factText)
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        newFactButton = findViewById(R.id.newFactButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        newFactButton.setOnClickListener {
            viewModel.loadNewFact()
        }

        collectUiState()
    }

    private fun collectUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    newFactButton.isEnabled = !state.isLoading
                    statusText.text = state.statusText

                    if (state.fact == null) {
                        factCard.visibility = View.INVISIBLE
                    } else {
                        factText.text = state.fact
                        factCard.visibility = View.VISIBLE

                        if (state.fact != lastRenderedFact) {
                            animateFactCard()
                            lastRenderedFact = state.fact
                        }
                    }
                }
            }
        }
    }

    private fun animateFactCard() {
        factCard.alpha = 0f
        factCard.translationY = 40f
        factCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .start()
    }
}
