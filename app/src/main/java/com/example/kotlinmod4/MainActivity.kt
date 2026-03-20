package com.example.kotlinmod4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F4EE)
                ) {
                    GithubSearchScreen(repository = GithubRepository(this))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GithubSearchScreen(repository: GithubRepository) {
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var repositories by remember { mutableStateOf(emptyList<GithubRepo>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusText by remember {
        mutableStateOf("Начни вводить название репозитория, язык или описание")
    }

    var searchJob by remember { mutableStateOf<Job?>(null) }

    val debouncedSearch = remember(scope, repository) {
        scope.debounce<String>(waitMs = 500L) { text ->
            searchJob?.cancel()
            searchJob = scope.launch {
                if (text.isBlank()) {
                    repositories = emptyList()
                    isLoading = false
                    statusText = "Начни вводить название репозитория, язык или описание"
                    return@launch
                }

                isLoading = true
                statusText = "Ищу репозитории по запросу \"$text\""

                try {
                    val loadReposDeferred = async {
                        repository.loadRepos()
                    }

                    val filteredRepos = withContext(Dispatchers.Default) {
                        delay(350)
                        loadReposDeferred.await()
                            .filter { repo ->
                                val searchValue = text.trim().lowercase()
                                repo.fullName.lowercase().contains(searchValue) ||
                                    repo.description.lowercase().contains(searchValue) ||
                                    repo.language.lowercase().contains(searchValue)
                            }
                            .sortedByDescending { it.stars }
                    }

                    repositories = filteredRepos
                    statusText = if (filteredRepos.isEmpty()) {
                        "Ничего не найдено"
                    } else {
                        "Найдено ${filteredRepos.size} репозиториев"
                    }
                } catch (_: CancellationException) {
                    statusText = "Поиск отменён: введён новый запрос"
                    throw CancellationException()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            searchJob?.cancel()
        }
    }

    Scaffold(
        containerColor = Color(0xFFF7F4EE),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "GitHub Search",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                    debouncedSearch(newQuery)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                label = { Text("Поиск репозиториев") },
                placeholder = { Text("Например: kotlin, android, compose") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Загрузка...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Text(
                    text = statusText,
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF5C5247)
                )
            }

            if (!isLoading && repositories.isEmpty() && query.isBlank()) {
                SearchHint(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repositories, key = { it.id }) { repo ->
                    RepoCard(repo = repo)
                }
            }
        }
    }
}

@Composable
private fun SearchHint(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE9DFC9))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Как это работает",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("1. Ты вводишь текст в поле поиска.")
            Text("2. Приложение ждёт 500 мс, пока ты перестанешь печатать.")
            Text("3. Старый поиск отменяется, новый запускается только по свежему запросу.")
        }
    }
}

@Composable
private fun RepoCard(repo: GithubRepo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = repo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5C5247)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(repo.language) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("★ ${repo.stars}") }
                )
            }
        }
    }
}

private fun <T> CoroutineScope.debounce(
    waitMs: Long = 500L,
    destinationFunction: (T) -> Unit
): (T) -> Unit {
    var debounceJob: Job? = null

    return { param: T ->
        debounceJob?.cancel()
        debounceJob = launch {
            delay(waitMs)
            destinationFunction(param)
        }
    }
}

private class GithubRepository(private val activity: ComponentActivity) {
    private var cachedRepos: List<GithubRepo>? = null

    suspend fun loadRepos(): List<GithubRepo> {
        cachedRepos?.let { return it }

        val loadedRepos = withContext(Dispatchers.IO) {
            val jsonText = activity.assets.open("github_repos.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val jsonArray = JSONArray(jsonText)
            List(jsonArray.length()) { index ->
                val item = jsonArray.getJSONObject(index)
                GithubRepo(
                    id = item.getLong("id"),
                    fullName = item.getString("full_name"),
                    description = item.getString("description"),
                    stars = item.getInt("stargazers_count"),
                    language = item.getString("language")
                )
            }
        }

        cachedRepos = loadedRepos
        return loadedRepos
    }
}

private data class GithubRepo(
    val id: Long,
    val fullName: String,
    val description: String,
    val stars: Int,
    val language: String
)
