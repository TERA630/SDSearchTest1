package io.github.tera630.sdsearchtest1.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tera630.sdsearchtest1.data.AppSearchRepository
import io.github.tera630.sdsearchtest1.data.IndexStateStore
import io.github.tera630.sdsearchtest1.data.SearchHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repo: AppSearchRepository,
    private val store: IndexStateStore
) : ViewModel() {

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing

    private val _progress = MutableStateFlow<IndexProgress?>(null)
    val progress: StateFlow<IndexProgress?> = _progress

    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    val hits: StateFlow<List<SearchHit>> = _hits

    val lastIndexedAt = store.lastIndexedAtFlow // 既存のまま

    fun reindexAll(treeUri: Uri) {
        viewModelScope.launch {
            _isIndexing.value = true
            _progress.value = IndexProgress(total = 0, processed = 0)
            runCatching {
                repo.indexAllFromTree(treeUri) { processed, total ->
                    _progress.value = IndexProgress(total, processed)
                }
            }.onSuccess {
                store.setLastIndexedAt(System.currentTimeMillis())
            }
            _isIndexing.value = false
            _progress.value = null
        }
    }

    fun search(q: String) {
        viewModelScope.launch { _hits.value = repo.search(q) }
    }
}
