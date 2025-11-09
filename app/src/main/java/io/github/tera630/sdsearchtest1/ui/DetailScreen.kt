package io.github.tera630.sdsearchtest1.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        state = UiState.Loading // 初回実行時やIdが変更されたときにここが実行される。
        runCatching { repo.findNoteById(id) }
            .onSuccess { note ->
                state = if (note != null) UiState.Ready(note) else UiState.NotFound
            }
            .onFailure { state = UiState.Error(it) }
    }
    // stateがUiState.Readyの場合にタイトルを取得し、それ以外は(Error)デフォルトのテキストにする
    val topBarTitle = when (val s = state) {
        is UiState.Ready -> s.note.title
        else -> "本文"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(topBarTitle) }, navigationIcon = { BackButton(onBack) }) }
    ) { pad ->
        when (val s = state) {
            UiState.Loading -> Box(Modifier.padding(pad).fillMaxSize()) { CircularProgressIndicator() }
            UiState.NotFound -> Text("本文が見つかりませんでした。", Modifier.padding(pad).padding(16.dp))
            is UiState.Error -> Text("読み込みに失敗しました: ${s.err.message}", Modifier.padding(pad).padding(16.dp))
            is UiState.Ready -> {

                LazyColumn(Modifier.padding(pad)) {
                    // 本文内のタイトル表示は不要であれば削除しても良い
                    item { Text(s.note.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp)) }
                    item {
                        MarkdownText(
                            markdown = s.note.content,   // ← インデックスに保存した本文を表示
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            onLinkClicked = { href ->
                                when{
                                    href.startsWith("doc:") ->{
                                        val title = href.removePrefix("doc:").trim()
                                        Log.d("onLinkClicked","onLinkClicked: $title")
                                    }

                                }
                            }

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
