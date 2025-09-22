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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm: MainViewModel,
    onOpen: (String) -> Unit
) {
    var q by remember { mutableStateOf("") }
    val hits by vm.hits.collectAsState()
    val indexing by vm.isIndexing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("医療メモ検索") },
                actions = { FolderPickerButton { uri: Uri -> vm.reindexAll(uri) } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            OutlinedTextField(
                value = q,
                onValueChange = { q = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("検索語（スペースで AND）") }
            )
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = { vm.search(q) }, enabled = !indexing) { Text("検索") }
                if (indexing) Text("  インデックス作成中…", modifier = Modifier.padding(start = 12.dp))
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
                    .clickable { onOpen(h.path) }
            )
            Divider()
        }
    }
}
