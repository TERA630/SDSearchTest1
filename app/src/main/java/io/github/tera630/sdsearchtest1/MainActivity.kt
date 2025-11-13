package io.github.tera630.sdsearchtest1

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.tera630.sdsearchtest1.data.AppSearchRepository
import io.github.tera630.sdsearchtest1.data.IndexStateStore
import io.github.tera630.sdsearchtest1.data.appsearch.AppSearchNoteRepository
import io.github.tera630.sdsearchtest1.data.local.AndroidFileRepository
import io.github.tera630.sdsearchtest1.domain.usecase.IndexNotesUseCase
import io.github.tera630.sdsearchtest1.ui.DetailScreen
import io.github.tera630.sdsearchtest1.ui.MainViewModel
import io.github.tera630.sdsearchtest1.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fileRepo = AndroidFileRepository(this)
        val noteIndexRepo = AppSearchNoteRepository(this)
        val noteParser = io.github.tera630.sdsearchtest1.domain.service.NoteParser(normalizedTitle->null)

        val indexNotesUseCase = IndexNotesUseCase(
            fileRepo = fileRepo,
            noteParser = noteParser,
            indexRepo = noteIndexRepo
        )
        val searchNotesUseCase = io.github.tera630.sdsearchtest1.domain.usecase.SearchNotesUseCase(
            indexRepo = noteIndexRepo
        )
        val findByIdUseCase = io.github.tera630.sdsearchtest1.domain.usecase.FindNoteByIdUseCase(
            indexRepo = noteIndexRepo
        )

        val store = IndexStateStore(this)

        setContent {
            val nav = rememberNavController()
            val vm: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(
                        indexNotes = indexNotesUseCase,
                        searchNotes = searchNotesUseCase,
                        findById = findByIdUseCase, store = store) as T
                }
            })
            NavHost(navController = nav, startDestination = "search") {
                composable("search") {
                    SearchScreen(vm = vm, onOpen = { id ->
                        nav.navigate("detail?id=${Uri.encode(id)}")

                    }) // SearchScreen内部で検索結果のアイテムクリック時で起動するラムダを渡す｡
                }
                composable(
                    route = "detail?id={id}",
                    arguments = listOf(
                        navArgument("id") {
                            type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("id").orEmpty()
                    DetailScreen(id = id,
                        vm = vm,
                        onBack = { nav.popBackStack() },
                        onOpen = { newId ->
                            nav.navigate("detail?$id")
                        })
                }
            }
        }
    }
}
