package com.example.kotlinmod4

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var pathText: TextView
    private lateinit var outputText: TextView
    private lateinit var startSearchButton: Button
    private lateinit var timeoutSearchButton: Button
    private lateinit var cancelButton: Button

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        pathText = findViewById(R.id.pathText)
        outputText = findViewById(R.id.outputText)
        startSearchButton = findViewById(R.id.startSearchButton)
        timeoutSearchButton = findViewById(R.id.timeoutSearchButton)
        cancelButton = findViewById(R.id.cancelButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val rootDir = prepareDemoDirectory()
        pathText.text = "Папка для проверки:\n${rootDir.absolutePath}"
        outputText.text = buildString {
            appendLine("task2 готов к запуску")
            appendLine()
            appendLine("Что лежит в папке:")
            appendLine("- 5 json-файлов")
            appendLine("- 2 группы дублей")
            appendLine("- 1 txt-файл, который должен игнорироваться")
            appendLine()
            append("Можно запустить обычный поиск или короткий таймаут для демонстрации отмены.")
        }

        startSearchButton.setOnClickListener {
            runDuplicateSearch(rootDir, timeoutMs = 5_000, title = "Обычный поиск")
        }

        timeoutSearchButton.setOnClickListener {
            runDuplicateSearch(rootDir, timeoutMs = 600, title = "Поиск с коротким таймаутом")
        }

        cancelButton.setOnClickListener {
            searchJob?.cancel(CancellationException("Поиск остановлен вручную"))
        }
    }

    private fun runDuplicateSearch(rootDir: File, timeoutMs: Long, title: String) {
        searchJob?.cancel()
        searchJob = screenScope.launch {
            setButtonsEnabled(isSearching = true)
            outputText.text = "$title...\nСканирую директорию и считаю SHA-256"

            try {
                var result: SearchReport? = null
                val elapsedMs = measureTimeMillis {
                    result = withTimeoutOrNull(timeoutMs) {
                        findDuplicateGroups(rootDir)
                    }
                }

                outputText.text = if (result == null) {
                    buildString {
                        appendLine("Поиск прерван по таймауту")
                        appendLine("Лимит: ${timeoutMs} мс")
                        append("Прошло: ${elapsedMs} мс")
                    }
                } else {
                    formatReport(
                        title = title,
                        timeoutMs = timeoutMs,
                        elapsedMs = elapsedMs,
                        report = result!!
                    )
                }
            } catch (_: CancellationException) {
                outputText.text = "Поиск отменен вручную"
            } finally {
                setButtonsEnabled(isSearching = false)
            }
        }
    }

    private suspend fun findDuplicateGroups(rootDir: File): SearchReport = coroutineScope {
        val jsonFiles = rootDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .toList()

        val fileHashes = jsonFiles.map { file ->
            async(Dispatchers.IO) {
                file to computeSha256(file)
            }
        }.awaitAll()

        val duplicateGroups = fileHashes
            .groupBy(keySelector = { it.second }, valueTransform = { it.first })
            .filterValues { files -> files.size > 1 }
            .map { (hash, files) -> DuplicateGroup(hash = hash, files = files) }
            .sortedByDescending { it.files.size }

        SearchReport(
            scannedDirectory = rootDir,
            totalJsonFiles = jsonFiles.size,
            duplicateGroups = duplicateGroups
        )
    }

    private suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        delay(900)

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

            while (true) {
                currentCoroutineContext().ensureActive()
                val readCount = input.read(buffer)
                if (readCount == -1) break
                digest.update(buffer, 0, readCount)
            }
        }

        digest.digest().joinToString("") { byte ->
            "%02x".format(byte)
        }
    }

    private fun prepareDemoDirectory(): File {
        val rootDir = File(filesDir, "task2_demo")
        val nestedDir = File(rootDir, "archive")
        val deepDir = File(nestedDir, "backup")

        rootDir.mkdirs()
        nestedDir.mkdirs()
        deepDir.mkdirs()

        File(rootDir, "orders_day1.json").writeText(
            """
            {"orders":[{"id":1,"sum":450},{"id":2,"sum":890}]}
            """.trimIndent()
        )
        File(nestedDir, "orders_day1_copy.json").writeText(
            """
            {"orders":[{"id":1,"sum":450},{"id":2,"sum":890}]}
            """.trimIndent()
        )
        File(rootDir, "users_export.json").writeText(
            """
            [{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]
            """.trimIndent()
        )
        File(deepDir, "users_export_backup.json").writeText(
            """
            [{"id":1,"name":"Alice"},{"id":2,"name":"Bob"}]
            """.trimIndent()
        )
        File(rootDir, "weather_unique.json").writeText(
            """
            [{"city":"Moscow","temp":-8},{"city":"Tokyo","temp":11}]
            """.trimIndent()
        )
        File(rootDir, "readme.txt").writeText("Этот файл не должен участвовать в поиске дублей")

        return rootDir
    }

    private fun formatReport(
        title: String,
        timeoutMs: Long,
        elapsedMs: Long,
        report: SearchReport
    ): String {
        return buildString {
            appendLine(title)
            appendLine("Папка: ${report.scannedDirectory.absolutePath}")
            appendLine("Таймаут: ${timeoutMs} мс")
            appendLine("Обработано json-файлов: ${report.totalJsonFiles}")
            appendLine("Время: ${elapsedMs} мс")
            appendLine()

            if (report.duplicateGroups.isEmpty()) {
                append("Дубликаты не найдены")
            } else {
                appendLine("Найдены группы дублей:")
                report.duplicateGroups.forEachIndexed { index, group ->
                    appendLine()
                    appendLine("Группа ${index + 1}:")
                    appendLine("SHA-256: ${group.hash}")
                    group.files.forEach { file ->
                        appendLine("- ${file.relativeTo(report.scannedDirectory)}")
                    }
                }
            }
        }
    }

    private fun setButtonsEnabled(isSearching: Boolean) {
        startSearchButton.isEnabled = !isSearching
        timeoutSearchButton.isEnabled = !isSearching
        cancelButton.isEnabled = isSearching
    }

    override fun onDestroy() {
        super.onDestroy()
        screenScope.cancel()
    }
}

private data class SearchReport(
    val scannedDirectory: File,
    val totalJsonFiles: Int,
    val duplicateGroups: List<DuplicateGroup>
)

private data class DuplicateGroup(
    val hash: String,
    val files: List<File>
)
