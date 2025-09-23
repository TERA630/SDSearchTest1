package io.github.tera630.sdsearchtest1.ui

import android.net.Uri
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
    val lastIndexedAt by vm.lastIndexedAt.collectAsState()

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
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("インデックス作成中…")
                }
                return@Column
            }

            // ② 作成済み → 検索UI表示 + 日付表示
            val dateStr = remember(lastIndexedAt) {
                val fmt = SimpleDateFormat("yyyy/M/d", Locale.JAPAN)
                lastIndexedAt?.let { "インデックス作成: ${fmt.format(Date(it))}" } ?: ""
            }

            // 上部にステータス
            Text(dateStr)
            if (indexing) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Divider()
        }
    }
}
