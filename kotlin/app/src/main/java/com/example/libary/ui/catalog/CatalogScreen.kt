package com.example.libary.ui.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.libary.data.BookDto
import com.example.libary.data.LibraryRepository
import com.example.libary.data.LoanDto
import com.example.libary.ui.common.EmptyPane
import com.example.libary.ui.common.ErrorPane
import com.example.libary.ui.common.LoadingPane
import com.example.libary.ui.common.RefreshOnResume
import com.example.libary.ui.common.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CatalogData(val books: List<BookDto>, val loans: List<LoanDto>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(repository: LibraryRepository, onOpenBook: (Int) -> Unit) {
    val viewModel: CatalogViewModel = viewModel(factory = viewModelFactory {
        initializer { CatalogViewModel(repository) }
    })
    RefreshOnResume(viewModel::load)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Каталог") },
            actions = {
                IconButton(onClick = viewModel::load) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                }
            },
        )
        OutlinedTextField(
            value = viewModel.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Название или автор") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.load() }),
        )
        Spacer(Modifier.height(8.dp))
        if (viewModel.refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when (val state = viewModel.state) {
            UiState.Loading -> LoadingPane()
            is UiState.Error -> ErrorPane(state.message, viewModel::load)
            is UiState.Ready -> {
                if (state.value.books.isEmpty()) {
                    EmptyPane(
                        if (viewModel.query.isBlank()) "В каталоге пока нет книг" else "Ничего не найдено",
                    )
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(state.value.books, key = { it.id }) { book ->
                            BookRow(book, state.value.loans.firstOrNull { it.bookId == book.id }?.status, onClick = { onOpenBook(book.id) })
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: BookDto, ownStatus: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(book.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (ownStatus == "reserved" || ownStatus == "issued") {
                Text(if (ownStatus == "reserved") "Забронирована вами" else "У вас на руках", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = if (book.availableCopies > 0) {
                    "Свободных экземпляров: ${book.availableCopies}"
                } else {
                    "Нет в наличии"
                },
                color = if (book.availableCopies > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

class CatalogViewModel(private val repository: LibraryRepository) : ViewModel() {
    var query by mutableStateOf("")
    var state by mutableStateOf<UiState<CatalogData>>(UiState.Loading)
    var refreshing by mutableStateOf(false)
    private var request = 0
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            load()
        }
    }

    fun load() {
        val id = ++request
        val showFullScreen = state !is UiState.Ready
        viewModelScope.launch {
            if (showFullScreen) state = UiState.Loading else refreshing = true
            val result = repository.books(query).fold(
                onSuccess = { books -> repository.loans("active").map { loans -> CatalogData(books, loans) } },
                onFailure = { Result.failure(it) },
            )
            if (id != request) return@launch
            refreshing = false
            state = result.fold(
                onSuccess = { UiState.Ready(it) },
                onFailure = { UiState.Error(it.message ?: "Нет соединения с сервером") },
            )
        }
    }
}
