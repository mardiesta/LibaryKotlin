package com.example.libary.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.libary.data.LibraryRepository
import com.example.libary.data.UserDto
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(repository: LibraryRepository, onAuthenticated: (UserDto) -> Unit, notice: String? = null) {
    val model: AuthViewModel = viewModel(factory = viewModelFactory {
        initializer { AuthViewModel(repository) }
    })
    LaunchedEffect(notice) { if (!notice.isNullOrBlank()) model.error = notice }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("Библиотека", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Выберите роль, чтобы продолжить.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Text("Войти как", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { model.enter("reader", onAuthenticated) }, enabled = !model.submitting, modifier = Modifier.fillMaxWidth()) {
            Text("Читатель")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { model.enter("librarian", onAuthenticated) }, enabled = !model.submitting, modifier = Modifier.fillMaxWidth()) {
            Text("Библиотекарь")
        }
        if (model.submitting) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.size(24.dp))
        }
        model.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

class AuthViewModel(private val repository: LibraryRepository) : ViewModel() {
    var submitting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun enter(role: String, onSuccess: (UserDto) -> Unit) {
        if (submitting) return
        viewModelScope.launch {
            submitting = true
            error = null
            repository.demoLogin(role).fold(
                onSuccess = onSuccess,
                onFailure = { error = it.message ?: "Не удалось выполнить вход" },
            )
            submitting = false
        }
    }
}
