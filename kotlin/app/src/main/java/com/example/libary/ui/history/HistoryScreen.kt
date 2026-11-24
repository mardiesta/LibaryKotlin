package com.example.libary.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.libary.data.LoanDto
import com.example.libary.ui.common.EmptyPane
import com.example.libary.ui.common.ErrorPane
import com.example.libary.ui.common.LoadingPane
import com.example.libary.ui.common.RefreshOnResume
import com.example.libary.ui.common.UiState
import com.example.libary.ui.common.formatIsoDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repository: LibraryRepository) {
    val viewModel: HistoryViewModel = viewModel(factory = viewModelFactory {
        initializer { HistoryViewModel(repository) }
    })
    RefreshOnResume(viewModel::load)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("История") },
            actions = {
                IconButton(onClick = viewModel::load) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
            },
        )
        if (viewModel.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        when (val state = viewModel.state) {
            UiState.Loading -> LoadingPane()
            is UiState.Error -> ErrorPane(state.message, viewModel::load)
            is UiState.Ready -> {
                if (state.value.isEmpty()) {
                    EmptyPane("Вы ещё не возвращали книги")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(state.value, key = { it.id }) { loan ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(loan.title, style = MaterialTheme.typography.titleMedium)
                                    Text(loan.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Возвращена ${formatIsoDate(loan.returnedAt)}")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

class HistoryViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf<UiState<List<LoanDto>>>(UiState.Loading)
    var refreshing by mutableStateOf(false)
    private var request = 0

    fun load() {
        val id = ++request
        viewModelScope.launch {
            if (state !is UiState.Ready) state = UiState.Loading else refreshing = true
            val result = repository.loans("history")
            if (id != request) return@launch
            refreshing = false
            state = result.fold(
                onSuccess = { UiState.Ready(it) },
                onFailure = { UiState.Error(it.message ?: "Нет соединения с сервером") },
            )
        }
    }
}
