package com.example.libary.ui.book

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.libary.data.BookDto
import com.example.libary.data.LibraryRepository
import com.example.libary.data.LoanDto
import com.example.libary.ui.common.ErrorPane
import com.example.libary.ui.common.LoadingPane
import com.example.libary.ui.common.RefreshOnResume
import com.example.libary.ui.common.UiState
import com.example.libary.ui.common.formatIsoDate
import kotlinx.coroutines.launch

data class BookDetails(val book: BookDto, val ownLoan: LoanDto?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(repository: LibraryRepository, bookId: Int, onBack: () -> Unit) {
    val viewModel: BookViewModel = viewModel(
        key = "book-$bookId",
        factory = viewModelFactory { initializer { BookViewModel(repository, bookId) } },
    )
    RefreshOnResume(viewModel::load)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Книга") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
        )
        when (val state = viewModel.state) {
            UiState.Loading -> LoadingPane()
            is UiState.Error -> ErrorPane(state.message, viewModel::load)
            is UiState.Ready -> BookContent(state.value, viewModel)
        }
    }
}

@Composable
private fun BookContent(details: BookDetails, viewModel: BookViewModel) {
    val book = details.book
    val loan = details.ownLoan
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(book.title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(book.author, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        MetaLine("Год издания", book.year.toString())
        MetaLine("Издательство", book.publisher)
        MetaLine("Свободно", "${book.availableCopies} из ${book.totalCopies}")
        viewModel.notice?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.primary)
        }
        viewModel.actionError?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        when {
            loan?.status == "issued" -> {
                Text(
                    "Книга у вас до ${formatIsoDate(loan.dueDate)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            loan?.status == "reserved" -> {
                Text("Забронирована", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Заберите книгу в библиотеке", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = viewModel::cancel,
                    enabled = !viewModel.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ActionLabel(viewModel.submitting, "Отменить бронь")
                }
            }
            book.availableCopies == 0 -> {
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Нет свободных экземпляров")
                }
            }
            else -> {
                Button(
                    onClick = viewModel::reserve,
                    enabled = !viewModel.submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ActionLabel(viewModel.submitting, "Забронировать")
                }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    Text(value, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ActionLabel(submitting: Boolean, title: String) {
    if (submitting) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        Text(title)
    }
}

class BookViewModel(
    private val repository: LibraryRepository,
    private val bookId: Int,
) : ViewModel() {
    var state by mutableStateOf<UiState<BookDetails>>(UiState.Loading)
    var submitting by mutableStateOf(false)
    var actionError by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    private var request = 0

    fun load() {
        val id = ++request
        viewModelScope.launch {
            state = UiState.Loading
            val loaded = fetch()
            if (id != request) return@launch
            state = loaded
        }
    }

    fun reserve() {
        viewModelScope.launch {
            submitting = true
            actionError = null
            repository.reserve(bookId).fold(
                onSuccess = {
                    notice = "Книга забронирована. Заберите её в библиотеке."
                    state = fetch()
                },
                onFailure = {
                    notice = null
                    actionError = it.message ?: "Не удалось забронировать"
                },
            )
            submitting = false
        }
    }

    fun cancel() {
        val loanId = (state as? UiState.Ready)?.value?.ownLoan?.id ?: return
        viewModelScope.launch {
            submitting = true
            actionError = null
            repository.cancel(loanId).fold(
                onSuccess = {
                    notice = "Бронь отменена"
                    state = fetch()
                },
                onFailure = {
                    notice = null
                    actionError = it.message ?: "Не удалось отменить бронь"
                },
            )
            submitting = false
        }
    }

    private suspend fun fetch(): UiState<BookDetails> {
        val book = repository.book(bookId).getOrElse {
            return UiState.Error(it.message ?: "Не удалось загрузить данные")
        }
        val loans = repository.loans("active").getOrElse {
            return UiState.Error(it.message ?: "Не удалось загрузить данные")
        }
        val own = loans.firstOrNull { it.bookId == bookId && it.status in setOf("reserved", "issued") }
        return UiState.Ready(BookDetails(book, own))
    }
}
