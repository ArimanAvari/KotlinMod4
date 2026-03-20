package com.example.kotlinmod4

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class CurrencyViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        CurrencyUiState(
            rate = 90.50,
            direction = 0,
            statusText = "Автообновление каждые 5 секунд"
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                updateRate("Курс обновлён автоматически")
            }
        }
    }

    fun refreshNow() {
        updateRate("Курс обновлён вручную")
    }

    private fun updateRate(statusText: String) {
        _uiState.update { current ->
            val delta = Random.nextDouble(-2.0, 2.0)
            val newRate = (current.rate + delta).coerceAtLeast(80.0)
            val direction = when {
                newRate > current.rate -> 1
                newRate < current.rate -> -1
                else -> 0
            }

            current.copy(
                rate = newRate,
                direction = direction,
                statusText = statusText
            )
        }
    }
}

data class CurrencyUiState(
    val rate: Double,
    val direction: Int,
    val statusText: String
)
