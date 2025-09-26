package io.github.tera630.sdsearchtest1.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.tera630.sdsearchtest1.data.SearchHit
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onOpen: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val hits by vm.hits.collectAsState()
    val indexing by vm.isIndexing.collectAsState()
    val lastIndexedAt by vm.lastIndexedAt.collectAsState(initial = null)
    val progress by vm.progress.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("医療メモ検索") },
                actions = { FolderPickerButton { uri: Uri -> vm.reindexAll(uri) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {

            // ① インデックス作成前 → 作成UIのみ
            if (lastIndexedAt == null) {
                Text("インデックス未作成です。SDカードのフォルダを選んでインデックスを作成してください。")
                Spacer(Modifier.height(8.dp))
                if (indexing) {
                    val p = progress
                    if (p == null || p.total == 0) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("インデックス作成中…")
                    } else {
                        LinearProgressIndicator(
                            progress = { p.fraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("インデックス作成中… ${p.processed} / ${p.total}")
                    }
                }
                return@Column
            }

            // ② 作成済み → 検索UI表示 + 日付表示
            val dateStr = remember(lastIndexedAt) {
                lastIndexedAt?.let { millis ->
                    java.text.SimpleDateFormat("yyyy/M/d", java.util.Locale.JAPAN).format(java.util.Date(millis))
                } ?: ""
            }
            Text("インデックス作成: $dateStr")

            // 再作成中なら上部に進捗を出す
            if (indexing) {
                val p = progress
                Spacer(Modifier.height(6.dp))
                if (p == null || p.total == 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("${p.processed} / ${p.total}")
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("検索語（スペースで AND）") }
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = { vm.search(q) }, enabled = !indexing) { Text("検索") }
                // 右側は TopBar のアクションにあるためボタンは置かない（要件通り「検索ボタンのみ」）
            }

            Spacer(Modifier.height(12.dp))
            ResultList(hits, onOpen)
        }
    }
}

@Composable
private fun ResultList(hits: List<SearchHit>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(hits) { h ->
            ListItem(
                headlineContent = { Text(h.title) },
                supportingContent = {
                    Text(h.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable( enabled = true,
                        onClick = {
                            onOpen(h.path)
                        }
                    )
            )
            HorizontalDivider()
        }
    }
}
