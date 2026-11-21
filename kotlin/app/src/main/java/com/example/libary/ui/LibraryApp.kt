package com.example.libary.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.libary.data.LibraryRepository
import com.example.libary.data.UnauthorizedException
import com.example.libary.data.UserDto
import com.example.libary.ui.auth.AuthScreen
import com.example.libary.ui.book.BookScreen
import com.example.libary.ui.catalog.CatalogScreen
import com.example.libary.ui.common.ErrorPane
import com.example.libary.ui.common.LoadingPane
import com.example.libary.ui.history.HistoryScreen
import com.example.libary.ui.loans.MyBooksScreen
import com.example.libary.ui.profile.ProfileScreen
import kotlinx.coroutines.launch
import java.io.IOException

private object Routes {
    const val Catalog = "catalog"
    const val MyBooks = "my-books"
    const val History = "history"
    const val Profile = "profile"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.Catalog, "Каталог", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Routes.MyBooks, "Мои книги", Icons.Filled.Bookmark),
    Tab(Routes.History, "История", Icons.Filled.History),
    Tab(Routes.Profile, "Профиль", Icons.Filled.Person),
)

@Composable
fun LibraryRoot(repository: LibraryRepository) {
    val session: SessionViewModel = viewModel(factory = viewModelFactory {
        initializer { SessionViewModel(repository) }
    })
    Surface(Modifier.fillMaxSize()) {
        when (val state = session.state) {
            SessionState.Checking -> LoadingPane()
            is SessionState.Offline -> ErrorPane(state.message, session::restore)
            is SessionState.LoggedOut -> AuthScreen(repository, session::onAuthenticated, state.notice)
            is SessionState.LoggedIn -> ReaderApp(repository, session::logout)
        }
    }
}

@Composable
private fun ReaderApp(repository: LibraryRepository, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in tabs.map { it.route }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.Catalog,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.Catalog) {
                CatalogScreen(repository) { bookId -> navController.navigate("book/$bookId") }
            }
            composable(
                route = "book/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.IntType }),
            ) { entry ->
                BookScreen(
                    repository = repository,
                    bookId = entry.arguments?.getInt("bookId") ?: return@composable,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MyBooks) { MyBooksScreen(repository) }
            composable(Routes.History) { HistoryScreen(repository) }
            composable(Routes.Profile) { ProfileScreen(repository, onLogout) }
        }
    }
}

sealed interface SessionState {
    data object Checking : SessionState
    data class LoggedOut(val notice: String? = null) : SessionState
    data class LoggedIn(val user: UserDto) : SessionState
    data class Offline(val message: String) : SessionState
}

class SessionViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf<SessionState>(SessionState.Checking)
        private set

    init {
        viewModelScope.launch {
            repository.sessionInvalid.collect { rejection ->
                if (repository.token() != rejection.token) return@collect
                repository.logout()
                state = SessionState.LoggedOut(sessionNotice(rejection.message))
            }
        }
        restore()
    }

    fun restore() {
        viewModelScope.launch {
            state = SessionState.Checking
            if (repository.token().isNullOrBlank()) {
                state = SessionState.LoggedOut()
                return@launch
            }
            repository.me().fold(
                onSuccess = { state = SessionState.LoggedIn(it) },
                onFailure = { error ->
                    when (error) {
                        is UnauthorizedException -> {
                            repository.logout()
                            state = SessionState.LoggedOut(sessionNotice(error.message))
                        }
                        is IOException -> state = SessionState.Offline(error.message ?: "Нет соединения с сервером")
                        else -> state = SessionState.Offline(error.message ?: "Не удалось выполнить запрос")
                    }
                },
            )
        }
    }

    fun onAuthenticated(user: UserDto) {
        state = SessionState.LoggedIn(user)
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            state = SessionState.LoggedOut()
        }
    }

    private fun sessionNotice(message: String?): String {
        return if (message.isNullOrBlank() || message == "Сессия недействительна" || message == "Требуется вход") {
            "Сессия истекла. Войдите снова."
        } else {
            message
        }
    }
}
