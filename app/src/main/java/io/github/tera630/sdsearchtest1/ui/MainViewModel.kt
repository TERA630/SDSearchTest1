package io.github.tera630.sdsearchtest1.ui
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tera630.sdsearchtest1.domain.repo.SearchHit
import io.github.tera630.sdsearchtest1.data.IndexStateStore
import io.github.tera630.sdsearchtest1.domain.usecase.FindNoteByIdUseCase
import io.github.tera630.sdsearchtest1.domain.usecase.IndexNotesUseCase
import io.github.tera630.sdsearchtest1.domain.usecase.SearchNotesUseCase
import io.github.tera630.sdsearchtest1.ui.IndexPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val indexNotes: IndexNotesUseCase,
    private val searchNotes: SearchNotesUseCase,
    private val findById: FindNoteByIdUseCase,
    val store: IndexStateStore
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
            _progress.value = IndexProgress(
                phase = IndexPhase.FILE_SCANNING,
                total = 0,
                processed = 0)

            runCatching {
                indexNotes(treeUri){ ph,t,pro -> // indexNoteUseCase.Invoke
                    _progress.value = IndexProgress(
                        phase = ph,
                        total = t,
                        processed = pro
                    )
                }
            }.onSuccess {
                // 成功した場合のみ最終更新日時を保存
               store.setLastIndexedAt(System.currentTimeMillis())
            }.onFailure{
                android.util.Log.e("MainViewModel", "Re-indexing failed")
            }
            _isIndexing.value = false
            _progress.value = null
        }
    }
    fun search(q: String) {
        viewModelScope.launch { _hits.value = searchNotes(q) }
    }
    suspend fun loadNote(id:String) = findById(id) // findNoteByIdUseCase.Invoke = NoteIndexRepository.findById = AppSearchNoteRepository.findById
}
