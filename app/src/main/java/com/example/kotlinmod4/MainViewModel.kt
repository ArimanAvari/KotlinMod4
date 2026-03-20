package com.example.kotlinmod4

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel : ViewModel() {

    private val animalFacts = listOf(
        "У осьминога три сердца и голубая кровь.",
        "Коалы спят до 20 часов в сутки.",
        "Дельфины дают друг другу имена с помощью свиста.",
        "Жирафы могут чистить уши своим длинным языком.",
        "Выдры во сне держатся за лапы, чтобы не уплыть друг от друга.",
        "Слоны умеют узнавать себя в зеркале.",
        "Пингвины делают предложения, принося камешек.",
        "У ленивцев шерсть может зеленеть из-за водорослей.",
        "Тигры имеют полоски не только на шерсти, но и на коже.",
        "Пчёлы умеют сообщать другим, где искать цветы, с помощью танца.",
        "Фламинго становятся розовыми из-за пигментов в еде.",
        "У коров есть лучшие подруги, и они меньше нервничают рядом с ними.",
        "Кошки могут поворачивать уши почти на 180 градусов.",
        "Акулы существовали раньше, чем появились деревья.",
        "Белки иногда делают фальшивые тайники, чтобы запутать наблюдателей."
    )

    private val _uiState = MutableStateFlow(
        FactUiState(
            statusText = "Нажми кнопку и получи новый факт"
        )
    )
    val uiState = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun getRandomFact(): Flow<String> = flow {
        delay(Random.nextLong(1_500L, 3_001L))
        emit(animalFacts.random())
    }

    fun loadNewFact() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    statusText = "Генерируем новый факт..."
                )
            }

            getRandomFact().collect { fact ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fact = fact,
                        statusText = "Факт готов"
                    )
                }
            }
        }
    }
}

data class FactUiState(
    val isLoading: Boolean = false,
    val fact: String? = null,
    val statusText: String
)
