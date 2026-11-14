package com.example.libary.ui.loans

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
import androidx.compose.material3.TextButton
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
import com.example.libary.ui.common.UiState
import com.example.libary.ui.common.formatIsoDate
import com.example.libary.ui.common.isPastDue
import kotlinx.coroutines.launch

data class MyBooks(val reserved: List<LoanDto>, val issued: List<LoanDto>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBooksScreen(repository: LibraryRepository) {
    val viewModel: MyBooksViewModel = viewModel(factory = viewModelFactory {
        initializer { MyBooksViewModel(repository) }
    })
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Мои книги") },
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
                val books = state.value
                if (books.reserved.isEmpty() && books.issued.isEmpty()) {
                    EmptyPane("Вы ещё не бронировали книги")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        if (books.reserved.isNotEmpty()) {
                            item { SectionTitle("Брони") }
                            items(books.reserved.size) { index ->
                                val loan = books.reserved[index]
                                LoanCard(loan) {
                                    Text("Заберите книгу в библиотеке")
                                    TextButton(
                                        onClick = { viewModel.cancel(loan.id) },
                                        enabled = viewModel.cancellingId != loan.id,
                                    ) { Text("Отменить бронь") }
                                }
                            }
                        }
                        if (books.issued.isNotEmpty()) {
                            item { SectionTitle("На руках") }
                            items(books.issued.size) { index ->
                                val loan = books.issued[index]
                                LoanCard(loan) {
                                    val due = formatIsoDate(loan.dueDate)
                                    Text(
                                        text = if (isPastDue(loan.dueDate)) "Срок возврата истёк · $due" else "Вернуть до $due",
                                        color = if (isPastDue(loan.dueDate)) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun LoanCard(loan: LoanDto, extra: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(loan.title, style = MaterialTheme.typography.titleMedium)
            Text(loan.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            extra()
        }
    }
    Spacer(Modifier.height(10.dp))
}

class MyBooksViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf<UiState<MyBooks>>(UiState.Loading)
    var refreshing by mutableStateOf(false)
    var cancellingId by mutableStateOf<Int?>(null)
    private var request = 0

    init {
        load()
    }

    fun load() {
        val id = ++request
        viewModelScope.launch {
            if (state !is UiState.Ready) state = UiState.Loading else refreshing = true
            val result = repository.loans("active")
            if (id != request) return@launch
            refreshing = false
            state = result.fold(
                onSuccess = { loans ->
                    UiState.Ready(
                        MyBooks(
                            reserved = loans.filter { it.status == "reserved" },
                            issued = loans.filter { it.status == "issued" },
                        ),
                    )
                },
                onFailure = { UiState.Error(it.message ?: "Нет соединения с сервером") },
            )
        }
    }

    fun cancel(loanId: Int) {
        viewModelScope.launch {
            cancellingId = loanId
            val result = repository.cancel(loanId)
            cancellingId = null
            result.fold(
                onSuccess = { load() },
                onFailure = { state = UiState.Error(it.message ?: "Не удалось отменить бронь") },
            )
        }
    }
}
