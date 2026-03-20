package com.example.kotlinmod4

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var outputText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        outputText = findViewById(R.id.outputText)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        runTaskOne()
    }

    private fun runTaskOne() {
        screenScope.launch {
            outputText.text = "Запуск task1...\nЗагружаю 3 источника параллельно"

            var report = ""
            val elapsedMs = measureTimeMillis {
                report = processSources()
            }

            outputText.text = buildString {
                append(report)
                appendLine()
                appendLine("Общее время: ${elapsedMs} мс")
            }
        }
    }

    private suspend fun processSources(): String = supervisorScope {
        val usersDeferred = async(Dispatchers.IO) {
            runCatching { loadUsers() }
        }

        val salesDeferred = async(Dispatchers.IO) {
            runCatching { loadSalesByProduct() }
        }

        val weatherDeferred = async(Dispatchers.IO) {
            runCatching { loadWeatherLines() }
        }

        val usersResult = usersDeferred.await()
        val salesResult = salesDeferred.await()
        val weatherResult = weatherDeferred.await()

        val errors = mutableListOf<String>()
        usersResult.exceptionOrNull()?.let {
            errors += "Пользователи: ${it.message}"
        }
        salesResult.exceptionOrNull()?.let {
            errors += "Продажи: ${it.message}"
        }
        weatherResult.exceptionOrNull()?.let {
            errors += "Погода: ${it.message}"
        }

        buildString {
            appendLine("Результат выполнения task1")
            appendLine("-------------------------")

            usersResult.getOrNull()?.let { users ->
                appendLine("Пользователи (${users.size}): ${users.joinToString()}")
            }

            salesResult.getOrNull()?.let { sales ->
                appendLine("Продажи за день:")
                sales.forEach { (product, qty) ->
                    appendLine("- $product: $qty шт.")
                }
            }

            weatherResult.getOrNull()?.let { weather ->
                appendLine("Погода:")
                weather.forEach { line ->
                    appendLine("- $line")
                }
            }

            if (errors.isNotEmpty()) {
                appendLine()
                appendLine("Есть ошибки (программа не упала):")
                errors.forEach { error ->
                    appendLine("- $error")
                }
            } else {
                appendLine()
                append("Все 3 задачи завершились успешно")
            }
        }
    }

    private suspend fun loadUsers(): List<String> {
        delay(1_800)
        maybeFail("users.json")

        val raw = readAssetText("users.json")
        val array = JSONArray(raw)

        return List(array.length()) { index ->
            array.getJSONObject(index).getString("name")
        }
    }

    private suspend fun loadSalesByProduct(): Map<String, Int> {
        delay(1_200)
        maybeFail("sales.json")

        val raw = readAssetText("sales.json")
        val json = JSONObject(raw)
        val items = json.getJSONArray("items")
        val sales = linkedMapOf<String, Int>()

        for (index in 0 until items.length()) {
            val item = items.getJSONObject(index)
            val product = item.getString("product")
            val qty = item.getInt("qty")
            sales[product] = qty
        }

        return sales
    }

    private suspend fun loadWeatherLines(): List<String> {
        delay(2_500)
        maybeFail("weather.json")

        val raw = readAssetText("weather.json")
        val array = JSONArray(raw)

        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val city = item.getString("city")
            val temp = item.getInt("temp")
            "$city: ${temp}°C"
        }
    }

    private fun maybeFail(sourceName: String) {
        if (Random.nextInt(100) < 30) {
            throw IllegalStateException("случайный сбой при чтении $sourceName")
        }
    }

    private fun readAssetText(fileName: String): String {
        return assets.open(fileName)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenScope.cancel()
    }
}
