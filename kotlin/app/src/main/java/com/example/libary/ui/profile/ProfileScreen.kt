package com.example.libary.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.libary.data.LibraryRepository
import com.example.libary.data.UserDto
import com.example.libary.ui.common.ErrorPane
import com.example.libary.ui.common.LoadingPane
import com.example.libary.ui.common.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(repository: LibraryRepository, onLogout: () -> Unit) {
    val viewModel: ProfileViewModel = viewModel(factory = viewModelFactory {
        initializer { ProfileViewModel(repository) }
    })
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Профиль") })
        when (val state = viewModel.state) {
            UiState.Loading -> LoadingPane()
            is UiState.Error -> ErrorPane(state.message, viewModel::load)
            is UiState.Ready -> {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(state.value.fullName, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(state.value.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(28.dp))
                    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                        Text("Выйти")
                    }
                }
            }
        }
    }
}

class ProfileViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf<UiState<UserDto>>(UiState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state = UiState.Loading
            state = repository.me().fold(
                onSuccess = { UiState.Ready(it) },
                onFailure = { UiState.Error(it.message ?: "Нет соединения с сервером") },
            )
        }
    }
}
