package io.github.tera630.sdsearchtest1.ui


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.tera630.sdsearchtest1.data.AppSearchRepository
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    path: String,
    repo: AppSearchRepository,
    onBack: () -> Unit
) {
    var md by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) { md = repo.getMarkdownByPath(path) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文書") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).padding(12.dp)) {
            when (val text = md) {
                null -> CircularProgressIndicator()
                else -> MarkdownText(
                    markdown = text, modifier = Modifier
                )
            }
        }
    }
}
