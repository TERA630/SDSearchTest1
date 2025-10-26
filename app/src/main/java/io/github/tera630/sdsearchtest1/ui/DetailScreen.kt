package io.github.tera630.sdsearchtest1.ui


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText
import io.github.tera630.sdsearchtest1.data.AppSearchRepository
import io.github.tera630.sdsearchtest1.data.NoteDoc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    id: String,
    repo: AppSearchRepository,
    onBack: () -> Unit
) {
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }

    LaunchedEffect(id) {
        state = UiState.Loading
        runCatching { repo.getNoteById(id) }
            .onSuccess { note ->
                state = if (note != null) UiState.Ready(note) else UiState.NotFound
            }
            .onFailure { state = UiState.Error(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("本文") }, navigationIcon = { BackButton(onBack) }) }
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> Box(Modifier.padding(pad).fillMaxSize()) { CircularProgressIndicator() }
            UiState.NotFound -> Text("本文が見つかりませんでした。", Modifier.padding(pad).padding(16.dp))
            is UiState.Error -> Text("読み込みに失敗しました: ${s.err.message}", Modifier.padding(pad).padding(16.dp))
            is UiState.Ready -> {
                // s.note.content をお好みの Markdown コンポーザで表示
                // 例: compose-markdown を利用
                LazyColumn(Modifier.padding(pad)) {
                    item { Text(s.note.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
                    item {
                        MarkdownText(
                            markdown = s.note.content,   // ← インデックスに保存した本文を表示
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

private sealed interface UiState {
    data object Loading : UiState
    data object NotFound : UiState
    data class Ready(val note: NoteDoc) : UiState
    data class Error(val err: Throwable) : UiState
}
