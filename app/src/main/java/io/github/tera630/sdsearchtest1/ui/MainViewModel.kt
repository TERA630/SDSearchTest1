package io.github.tera630.sdsearchtest1.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tera630.sdsearchtest1.data.AppSearchRepository
import io.github.tera630.sdsearchtest1.data.IndexStateStore
import io.github.tera630.sdsearchtest1.data.SearchHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repo: AppSearchRepository,
    private val store: IndexStateStore
) : ViewModel() {

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing

    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    val hits: StateFlow<List<SearchHit>> = _hits

    // null = まだインデックスなし／不明
    val lastIndexedAt: StateFlow<Long?> =
        store.lastIndexedAtFlow.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null
        )

    fun reindexAll(treeUri: Uri) {
        viewModelScope.launch {
            _isIndexing.value = true
            runCatching { repo.indexAllFromTree(treeUri) }
                .onSuccess {
                    // 完了時刻を保存
                    store.setLastIndexedAt(System.currentTimeMillis())
                }
            _isIndexing.value = false
        }
    }

    fun search(q: String) {
        viewModelScope.launch {
            _hits.value = repo.search(q)
        }
    }
}
