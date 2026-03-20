package com.example.kotlinmod4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.absoluteValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF7F6FB)
                ) {
                    SocialFeedScreen(repository = SocialFeedRepository(this))
                }
            }
        }
    }
}

@Composable
private fun SocialFeedScreen(repository: SocialFeedRepository) {
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<PostCardUiState>() }
    val activeJobs = remember { mutableStateListOf<Job>() }
    var isRefreshing by remember { mutableStateOf(false) }

    fun cancelAllJobs() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun refreshFeed() {
        cancelAllJobs()
        isRefreshing = true
        posts.clear()

        val feedJob = scope.launch {
            val loadedPosts = repository.loadPosts().take(5)
            posts.addAll(
                loadedPosts.map { post ->
                    PostCardUiState(post = post, status = LoadStatus.Loading)
                }
            )

            isRefreshing = false

            loadedPosts.forEach { post ->
                val postJob = launchPostLoading(
                    scope = scope,
                    repository = repository,
                    post = post,
                    updateCard = { updatedCard ->
                        val index = posts.indexOfFirst { it.post.id == updatedCard.post.id }
                        if (index >= 0) {
                            posts[index] = updatedCard
                        }
                    }
                )
                activeJobs += postJob
                postJob.invokeOnCompletion {
                    activeJobs.remove(postJob)
                }
            }
        }

        activeJobs += feedJob
        feedJob.invokeOnCompletion {
            activeJobs.remove(feedJob)
        }
    }

    LaunchedEffect(Unit) {
        refreshFeed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Социальная лента",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF222227)
            )
            TextButton(onClick = { refreshFeed() }) {
                Text("Обновить")
            }
        }

        if (isRefreshing && posts.isEmpty()) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Загружаю посты...",
                    color = Color(0xFF6D6A74)
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(posts, key = { it.post.id }) { cardState ->
                PostCard(cardState = cardState)
            }
        }
    }
}

private fun launchPostLoading(
    scope: CoroutineScope,
    repository: SocialFeedRepository,
    post: SocialPost,
    updateCard: (PostCardUiState) -> Unit
): Job {
    val job = scope.launch {
        val currentCard = PostCardUiState(post = post, status = LoadStatus.Loading)
        updateCard(currentCard)

        try {
            val finalCard = supervisorScope {
                val avatarDeferred = async {
                    try {
                        repository.loadAvatar(post)
                    } catch (_: Exception) {
                        null
                    }
                }

                val commentsDeferred = async {
                    try {
                        repository.loadComments(post.id)
                    } catch (_: Exception) {
                        null
                    }
                }

                val avatar = avatarDeferred.await()
                val comments = commentsDeferred.await()

                val status = if (avatar != null && comments != null) {
                    LoadStatus.Ready
                } else {
                    LoadStatus.Error
                }

                PostCardUiState(
                    post = post,
                    status = status,
                    avatar = avatar,
                    comments = comments ?: emptyList(),
                    avatarFailed = avatar == null,
                    commentsFailed = comments == null
                )
            }

            updateCard(finalCard)
        } catch (_: CancellationException) {
        }
    }

    job.invokeOnCompletion {
        if (it != null && it !is CancellationException) {
            updateCard(
                PostCardUiState(
                    post = post,
                    status = LoadStatus.Error,
                    comments = emptyList(),
                    avatarFailed = true,
                    commentsFailed = true
                )
            )
        }
    }

    return job
}

@Composable
private fun PostCard(cardState: PostCardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7E5EC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AvatarBlock(cardState = cardState)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cardState.post.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF26262C)
                    )
                    Text(
                        text = buildUserSubtitle(cardState),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF88858E),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = cardState.post.body,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF34343A)
            )

            HorizontalDivider(color = Color(0xFFCAC7D1), thickness = 1.dp)

            CommentsBlock(cardState = cardState)
        }
    }
}

@Composable
private fun AvatarBlock(cardState: PostCardUiState) {
    when {
        cardState.status == LoadStatus.Loading -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD2D0D9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "...",
                    color = Color(0xFF5E5C64),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        cardState.avatar != null -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cardState.avatar.color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cardState.avatar.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        else -> {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFBBB8C2)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CommentsBlock(cardState: PostCardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            cardState.status == LoadStatus.Loading -> {
                Text(
                    text = "Комментарии загружаются...",
                    color = Color(0xFF75727C)
                )
            }

            cardState.commentsFailed -> {
                Text(
                    text = "Комментарии сейчас недоступны",
                    color = Color(0xFF8D5D5D)
                )
            }

            cardState.comments.isEmpty() -> {
                Text(
                    text = "Комментариев пока нет",
                    color = Color(0xFF75727C)
                )
            }

            else -> {
                cardState.comments.take(3).forEach { comment ->
                    Text(
                        text = "${comment.name}: ${comment.body}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF414149)
                    )
                }
            }
        }
    }
}

private fun buildUserSubtitle(cardState: PostCardUiState): String {
    return when (cardState.status) {
        LoadStatus.Loading -> "Пользователь #${cardState.post.userId} • загрузка..."
        LoadStatus.Ready -> "Пользователь #${cardState.post.userId}"
        LoadStatus.Error -> "Пользователь #${cardState.post.userId} • часть данных недоступна"
    }
}

private class SocialFeedRepository(private val activity: ComponentActivity) {
    private var postsCache: List<SocialPost>? = null
    private var commentsCache: List<PostComment>? = null

    suspend fun loadPosts(): List<SocialPost> = withContext(Dispatchers.IO) {
        postsCache ?: run {
            delay(500)
            val raw = activity.assets.open("social_posts.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                SocialPost(
                    id = item.getInt("id"),
                    userId = item.getInt("userId"),
                    title = item.getString("title"),
                    body = item.getString("body"),
                    avatarUrl = item.getString("avatarUrl")
                )
            }.also { postsCache = it }
        }
    }

    suspend fun loadComments(postId: Int): List<PostComment> = withContext(Dispatchers.IO) {
        delay(900)
        if (postId == 6 || postId == 11) {
            error("comments failed")
        }

        val comments = commentsCache ?: run {
            val raw = activity.assets.open("comments.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                PostComment(
                    postId = item.getInt("postId"),
                    id = item.getInt("id"),
                    name = item.getString("name"),
                    body = item.getString("body")
                )
            }.also { commentsCache = it }
        }

        comments.filter { it.postId == postId }
    }

    suspend fun loadAvatar(post: SocialPost): AvatarInfo = withContext(Dispatchers.IO) {
        delay(650)
        if (post.id == 4 || post.id == 9) {
            error("avatar failed")
        }

        AvatarInfo(
            label = post.title.first().uppercase(),
            color = avatarColorFor(post.avatarUrl)
        )
    }

    private fun avatarColorFor(seed: String): Color {
        val palette = listOf(
            Color(0xFFE91E63),
            Color(0xFF3F51B5),
            Color(0xFF4CAF50),
            Color(0xFFFF5722),
            Color(0xFF9C27B0)
        )
        val index = seed.hashCode().absoluteValue % palette.size
        return palette[index]
    }
}

private enum class LoadStatus {
    Loading, Ready, Error
}

private data class PostCardUiState(
    val post: SocialPost,
    val status: LoadStatus,
    val avatar: AvatarInfo? = null,
    val comments: List<PostComment> = emptyList(),
    val avatarFailed: Boolean = false,
    val commentsFailed: Boolean = false
)

private data class SocialPost(
    val id: Int,
    val userId: Int,
    val title: String,
    val body: String,
    val avatarUrl: String
)

private data class PostComment(
    val postId: Int,
    val id: Int,
    val name: String,
    val body: String
)

private data class AvatarInfo(
    val label: String,
    val color: Color
)
