package com.example.kotlinmod4

import android.os.Bundle
import android.widget.Button
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

    private lateinit var resultText: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        resultText = findViewById(R.id.resultText)
        startButton = findViewById(R.id.startButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        startButton.setOnClickListener {
            runTaskOne()
        }
    }

    private fun runTaskOne() {
        startButton.isEnabled = false
        resultText.text = "Запускаю параллельную загрузку..."

        screenScope.launch {
            var report = ""
            val elapsedMs = measureTimeMillis {
                report = processSources()
            }

            resultText.text = buildString {
                append(report)
                appendLine()
                append("⏱ Общее время: ${elapsedMs} мс (ожидаемо ~2500 мс)")
            }

            startButton.isEnabled = true
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

        buildString {
            appendLine("=== Результаты ===")
            appendLine()

            usersResult.onSuccess { users ->
                appendLine("👤 Пользователи:")
                appendLine(users.joinToString(prefix = "- ", separator = "\n- "))
                appendLine()
            }.onFailure {
                appendLine("❌ Ошибка загрузки пользователей: ${it.message}")
                appendLine()
            }

            salesResult.onSuccess { sales ->
                appendLine("📊 Статистика продаж:")
                sales.forEach { (product, qty) ->
                    appendLine("- $product: $qty шт.")
                }
                appendLine()
            }.onFailure {
                appendLine("❌ Ошибка загрузки продаж: ${it.message}")
                appendLine()
            }

            weatherResult.onSuccess { weather ->
                appendLine("☀ Погода:")
                weather.forEach { line ->
                    appendLine("- $line")
                }
                appendLine()
            }.onFailure {
                appendLine("❌ Ошибка загрузки погоды: ${it.message}")
                appendLine()
            }
        }.trimEnd()
    }

    private suspend fun loadUsers(): List<String> {
        delay(1_800)
        maybeFail("Сервер недоступен (users)")

        val raw = readAssetText("users.json")
        val array = JSONArray(raw)

        return List(array.length()) { index ->
            array.getJSONObject(index).getString("name")
        }
    }

    private suspend fun loadSalesByProduct(): Map<String, Int> {
        delay(1_200)
        maybeFail("Сервер недоступен (sales)")

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
        maybeFail("Сервер недоступен (weather)")

        val raw = readAssetText("weather.json")
        val array = JSONArray(raw)

        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val city = item.getString("city")
            val temp = item.getInt("temp")
            val condition = item.getString("condition")
            "$city: ${temp}°C, $condition"
        }
    }

    private fun maybeFail(message: String) {
        if (Random.nextInt(100) < 30) {
            throw IllegalStateException(message)
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
