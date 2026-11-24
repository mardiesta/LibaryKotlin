package com.example.libary.ui.loans

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.libary.data.LibraryRepository
import com.example.libary.data.LoanDto
import com.example.libary.ui.common.*
import kotlinx.coroutines.launch

data class MyBooks(val reserved: List<LoanDto>, val issued: List<LoanDto>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyBooksScreen(repository: LibraryRepository) {
    val model: MyBooksViewModel = viewModel(factory = viewModelFactory { initializer { MyBooksViewModel(repository) } })
    var selected by rememberSaveable { mutableIntStateOf(0) }
    RefreshOnResume(model::load)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Брони и книги") }, actions = {
            IconButton(onClick = model::load) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить") }
        })
        val books = (model.state as? UiState.Ready)?.value
        TabRow(selectedTabIndex = selected) {
            listOf("Бронирования" to books?.reserved?.size, "На руках" to books?.issued?.size).forEachIndexed { index, (title, count) ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(if (count == null) title else "$title · $count") })
            }
        }
        if (model.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.notice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
        model.actionError?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
        when (val state = model.state) {
            UiState.Loading -> LoadingPane()
            is UiState.Error -> ErrorPane(state.message, model::load)
            is UiState.Ready -> {
                val loans = if (selected == 0) state.value.reserved else state.value.issued
                if (loans.isEmpty()) {
                    EmptyPane(if (selected == 0) "Нет бронирований. Выберите книгу в каталоге, чтобы забронировать её" else "У вас пока нет книг на руках")
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(loans, key = { it.id }) { loan ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(if (selected == 0) "Забронирована" else "На руках", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text(loan.title, style = MaterialTheme.typography.titleMedium)
                                    Text(loan.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(12.dp))
                                    if (selected == 0) {
                                        Text("Дата бронирования: ${formatIsoDate(loan.reservedAt)}", style = MaterialTheme.typography.bodyMedium)
                                        Text("Заберите книгу в библиотеке", style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = { model.cancel(loan.id) }, enabled = model.cancellingId == null) { Text("Отменить бронь") }
                                    } else {
                                        Text(if (isPastDue(loan.dueDate)) "Срок возврата истёк · ${formatIsoDate(loan.dueDate)}" else "Вернуть до ${formatIsoDate(loan.dueDate)}", color = if (isPastDue(loan.dueDate)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class MyBooksViewModel(private val repository: LibraryRepository) : ViewModel() {
    var state by mutableStateOf<UiState<MyBooks>>(UiState.Loading); private set
    var refreshing by mutableStateOf(false); private set
    var cancellingId by mutableStateOf<Int?>(null); private set
    var actionError by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    private var request = 0

    fun load() {
        val id = ++request
        viewModelScope.launch {
            if (state !is UiState.Ready) state = UiState.Loading else refreshing = true
            val result = repository.loans("active")
            if (id != request) return@launch
            refreshing = false
            result.fold(
                onSuccess = { loans -> actionError = null; state = UiState.Ready(MyBooks(loans.filter { it.status == "reserved" }, loans.filter { it.status == "issued" })) },
                onFailure = { if (state is UiState.Ready) actionError = it.message ?: "Не удалось обновить список" else state = UiState.Error(it.message ?: "Не удалось загрузить данные") },
            )
        }
    }

    fun cancel(loanId: Int) {
        if (cancellingId != null) return
        ++request
        refreshing = false
        viewModelScope.launch {
            cancellingId = loanId
            actionError = null
            notice = null
            repository.cancel(loanId).fold(
                onSuccess = { notice = "Бронь отменена"; load() },
                onFailure = { actionError = it.message ?: "Не удалось отменить бронь" },
            )
            cancellingId = null
        }
    }
}
