package com.example.kotlinmod4

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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
    private val outputLines = mutableListOf<String>()

    private lateinit var pathText: TextView
    private lateinit var outputText: TextView
    private lateinit var timeoutInput: EditText
    private lateinit var startSearchButton: Button
    private lateinit var cancelButton: Button

    private lateinit var searchRootDir: File

    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        pathText = findViewById(R.id.pathText)
        outputText = findViewById(R.id.outputText)
        timeoutInput = findViewById(R.id.timeoutInput)
        startSearchButton = findViewById(R.id.startSearchButton)
        cancelButton = findViewById(R.id.cancelButton)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        searchRootDir = prepareDemoDirectory()
        pathText.text = "Директория: ${searchRootDir.absolutePath}"
        timeoutInput.setText(DEFAULT_TIMEOUT_SECONDS.toString())
        outputText.text = "Результат появится здесь после запуска поиска."

        startSearchButton.setOnClickListener {
            runDuplicateSearch(searchRootDir, readTimeoutSeconds())
        }

        cancelButton.setOnClickListener {
            searchJob?.cancel(CancellationException("Поиск остановлен вручную"))
        }
    }

    private fun runDuplicateSearch(rootDir: File, timeoutSeconds: Long) {
        searchJob?.cancel()
        searchJob = screenScope.launch {
            setControlsState(isSearching = true)
            clearOutput()
            appendOutputLine("Сканируем директорию...")
            appendOutputLine("Таймаут: $timeoutSeconds сек")

            try {
                var result: SearchReport? = null
                val elapsedMs = measureTimeMillis {
                    result = withTimeoutOrNull(timeoutSeconds * 1_000) {
                        findDuplicateGroups(
                            rootDir = rootDir,
                            onFilesFound = { count ->
                                appendOutputLine("Найдено файлов: $count")
                            },
                            onHashingStarted = { fileName ->
                                appendOutputLine("Хэшируем: $fileName")
                            }
                        )
                    }
                }

                if (result == null) {
                    appendOutputLine("")
                    appendOutputLine("Поиск остановлен по таймауту")
                    appendOutputLine("Прошло времени: ${elapsedMs} мс")
                } else {
                    appendOutputLine("Анализ завершён")
                    appendOutputLine("")
                    appendReport(result!!, elapsedMs)
                }
            } catch (_: CancellationException) {
                appendOutputLine("")
                appendOutputLine("Поиск остановлен вручную")
            } finally {
                setControlsState(isSearching = false)
            }
        }
    }

    private suspend fun findDuplicateGroups(
        rootDir: File,
        onFilesFound: suspend (Int) -> Unit,
        onHashingStarted: suspend (String) -> Unit
    ): SearchReport = coroutineScope {
        val jsonFiles = rootDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .toList()

        onFilesFound(jsonFiles.size)

        val fileHashes = jsonFiles.map { file ->
            async(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    onHashingStarted(file.name)
                }
                file to computeSha256(file)
            }
        }.awaitAll()

        val duplicateGroups = fileHashes
            .groupBy(keySelector = { it.second }, valueTransform = { it.first })
            .filterValues { files -> files.size > 1 }
            .map { (hash, files) ->
                DuplicateGroup(
                    hash = hash,
                    files = files.sortedBy { it.name }
                )
            }
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
        val rootDir = File(filesDir, "search_test")
        val sub1Dir = File(rootDir, "sub1")
        val sub2Dir = File(rootDir, "sub2")

        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }

        rootDir.mkdirs()
        sub1Dir.mkdirs()
        sub2Dir.mkdirs()

        val duplicateGroupOne = """
            {"items":[{"id":1,"title":"Notebook"},{"id":2,"title":"Phone"}]}
        """.trimIndent()

        val duplicateGroupTwo = """
            {"users":[{"id":7,"name":"Max"},{"id":8,"name":"Ann"}]}
        """.trimIndent()

        File(rootDir, "file_a.json").writeText(duplicateGroupOne)
        File(sub1Dir, "file_b.json").writeText(duplicateGroupOne)
        File(rootDir, "extra.json").writeText(duplicateGroupOne)
        File(sub1Dir, "file_c.json").writeText(duplicateGroupTwo)
        File(sub2Dir, "file_d.json").writeText(duplicateGroupTwo)
        File(rootDir, "file_e.json").writeText("""{"city":"Moscow","temp":-6}""")
        File(rootDir, "notes.txt").writeText("Этот файл нужен, чтобы показать фильтрацию по json.")

        return rootDir
    }

    private fun appendReport(report: SearchReport, elapsedMs: Long) {
        if (report.duplicateGroups.isEmpty()) {
            appendOutputLine("=== Дубликаты не найдены ===")
            appendOutputLine("")
            appendOutputLine("Общее время: ${elapsedMs} мс")
            return
        }

        appendOutputLine("=== Найдены дубликаты ===")
        appendOutputLine("")

        report.duplicateGroups.forEachIndexed { index, group ->
            val shortHash = if (group.hash.length > 16) {
                "${group.hash.take(16)}..."
            } else {
                group.hash
            }

            appendOutputLine("Хэш: $shortHash")
            group.files.forEach { file ->
                appendOutputLine("📄 ${file.name} (${folderLabel(file, report.scannedDirectory)})")
            }

            if (index != report.duplicateGroups.lastIndex) {
                appendOutputLine("")
            }
        }

        appendOutputLine("")
        appendOutputLine("Общее время: ${elapsedMs} мс")
    }

    private fun folderLabel(file: File, rootDir: File): String {
        val parent = file.parentFile ?: return rootDir.name
        return if (parent.absolutePath == rootDir.absolutePath) {
            rootDir.name
        } else {
            parent.name
        }
    }

    private fun readTimeoutSeconds(): Long {
        val parsedValue = timeoutInput.text.toString().trim().toLongOrNull()
        return when {
            parsedValue == null -> DEFAULT_TIMEOUT_SECONDS
            parsedValue < 1 -> 1L
            else -> parsedValue
        }
    }

    private fun clearOutput() {
        outputLines.clear()
        outputText.text = ""
    }

    private fun appendOutputLine(line: String) {
        outputLines += line
        outputText.text = outputLines.joinToString("\n")
    }

    private fun setControlsState(isSearching: Boolean) {
        startSearchButton.isEnabled = !isSearching
        timeoutInput.isEnabled = !isSearching
        cancelButton.isEnabled = isSearching
        cancelButton.visibility = if (isSearching) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        screenScope.cancel()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 5L
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
