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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
                    color = Color(0xFFF5F0E8)
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
    var feedMessage by remember { mutableStateOf("Готово к загрузке") }

    fun cancelAllJobs() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }

    fun refreshFeed() {
        cancelAllJobs()
        isRefreshing = true
        feedMessage = "Загружаю ленту..."
        posts.clear()

        val feedJob = scope.launch {
            val loadedPosts = repository.loadPosts()
            posts.addAll(
                loadedPosts.map { post ->
                    PostCardUiState(post = post, status = LoadStatus.Loading)
                }
            )

            isRefreshing = false
            feedMessage = "Посты читаются по мере готовности карточек"

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
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Social Feed",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = feedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6F6253)
                )
            }
            Button(
                onClick = { refreshFeed() }
            ) {
                Text("Обновить")
            }
        }

        if (isRefreshing && posts.isEmpty()) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator()
                Text("Загружаю список постов...")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "userId: ${cardState.post.userId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7E7368)
                    )
                }
                StatusBadge(status = cardState.status)
            }

            Text(
                text = cardState.post.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF4D4338)
            )

            CommentsBlock(cardState = cardState)
        }
    }
}

@Composable
private fun AvatarBlock(cardState: PostCardUiState) {
    when {
        cardState.status == LoadStatus.Loading -> {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }

        cardState.avatar != null -> {
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD2C8BD)),
                contentAlignment = Alignment.Center
            ) {
                Text("ERR", color = Color(0xFF7A1E1E), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CommentsBlock(cardState: PostCardUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Комментарии",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        when {
            cardState.status == LoadStatus.Loading -> {
                Text("Loading...", color = Color(0xFF7E7368))
            }

            cardState.commentsFailed -> {
                Text("Не удалось загрузить комментарии", color = Color(0xFFB53E3E))
            }

            cardState.comments.isEmpty() -> {
                Text("Комментариев пока нет", color = Color(0xFF7E7368))
            }

            else -> {
                cardState.comments.take(3).forEach { comment ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF6F1E9))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = comment.name,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = comment.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF5B5146)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: LoadStatus) {
    val background = when (status) {
        LoadStatus.Loading -> Color(0xFFFFE5B5)
        LoadStatus.Ready -> Color(0xFFD8F0D2)
        LoadStatus.Error -> Color(0xFFF6D2D2)
    }

    val label = when (status) {
        LoadStatus.Loading -> "Loading"
        LoadStatus.Ready -> "Ready"
        LoadStatus.Error -> "Error"
    }

    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
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
            Color(0xFF7C9A92),
            Color(0xFFC96F5D),
            Color(0xFF6D83B3),
            Color(0xFFAD8B73),
            Color(0xFF6B9080),
            Color(0xFFB56576)
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
