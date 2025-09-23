package io.github.tera630.sdsearchtest1

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
import io.github.tera630.sdsearchtest1.ui.DetailScreen
import io.github.tera630.sdsearchtest1.ui.MainViewModel
import io.github.tera630.sdsearchtest1.ui.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppSearchRepository(this)
        val store = IndexStateStore(this)

        setContent {
            val nav = rememberNavController()
            val vm: MainViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(repo, store = store) as T
                }
            })

            NavHost(navController = nav, startDestination = "search") {
                composable("search") {
                    SearchScreen(vm = vm, onOpen = { path ->
                        nav.navigate("detail?path=$path")
                    })
                }
                composable(
                    route = "detail?path={path}",
                    arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val path = backStackEntry.arguments?.getString("path").orEmpty()
                    DetailScreen(path = path, repo = repo) { nav.popBackStack() }
                }
            }
        }
    }
}
