package com.example.libary.ui.librarian

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarianScreen(repository: LibraryRepository, onLogout: () -> Unit) {
    val model: LibrarianViewModel = viewModel(factory = viewModelFactory { initializer { LibrarianViewModel(repository) } })
    var selected by rememberSaveable { mutableIntStateOf(0) }
    RefreshOnResume(model::load)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TopAppBar(title = { Text("Библиотекарь") }, actions = {
            IconButton(onClick = model::load, enabled = !model.busy) { Icon(Icons.Filled.Refresh, contentDescription = "Обновить") }
            TextButton(onClick = onLogout) { Text("Выйти") }
        })
        val reserved = model.loans.filter { it.status == "reserved" }
        val issued = model.loans.filter { it.status == "issued" }
        TabRow(selectedTabIndex = selected) {
            listOf("Выдача" to reserved.size, "Возврат" to issued.size).forEachIndexed { index, (title, count) ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(if (model.loaded) "$title · $count" else title) })
            }
        }
        if (model.busy && model.loaded) LinearProgressIndicator(Modifier.fillMaxWidth())
        model.notice?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary) }
        model.error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
        when {
            model.loading -> LoadingPane()
            !model.loaded -> ErrorPane(model.error ?: "Не удалось загрузить записи", model::load)
            else -> {
                val loans = if (selected == 0) reserved else issued
                Column(Modifier.fillMaxSize()) {
                    Text(if (selected == 0) "Книги, которые ожидают выдачи читателям" else "Книги на руках у читателей", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    if (loans.isEmpty()) {
                        EmptyPane(if (selected == 0) "Нет бронирований для выдачи" else "Нет книг, ожидающих возврата")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(loans, key = { it.id }) { loan ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp)) {
                                        val overdue = selected == 1 && isPastDue(loan.dueDate)
                                        Text(if (selected == 0) "Ожидает выдачи" else if (overdue) "Просрочен возврат" else "На руках", color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                        Spacer(Modifier.height(8.dp))
                                        Text(loan.title, style = MaterialTheme.typography.titleMedium)
                                        Text(loan.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(12.dp))
                                        Text(loan.readerName, style = MaterialTheme.typography.bodyLarge)
                                        Text(loan.readerEmail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(8.dp))
                                        Text(if (selected == 0) "Забронирована ${formatIsoDate(loan.reservedAt)}" else "Вернуть до ${formatIsoDate(loan.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = { model.act(loan) }, enabled = !model.busy) { Text(if (selected == 0) "Выдать книгу" else "Принять возврат") }
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

class LibrarianViewModel(private val repository: LibraryRepository) : ViewModel() {
    var loans by mutableStateOf<List<LoanDto>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var loaded by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set

    fun load() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            error = null
            refresh()
            busy = false
        }
    }

    private suspend fun refresh() {
        repository.librarianLoans().fold(
            onSuccess = { loans = it; loaded = true; error = null },
            onFailure = { error = it.message ?: "Не удалось загрузить записи" },
        )
        loading = false
    }

    fun act(loan: LoanDto) {
        if (busy || loan.status !in setOf("reserved", "issued")) return
        viewModelScope.launch {
            busy = true
            error = null
            notice = null
            val result = if (loan.status == "reserved") repository.issue(loan.id) else repository.acceptReturn(loan.id)
            result.fold(
                onSuccess = { updated ->
                    loans = if (updated.status == "issued") loans.map { if (it.id == updated.id) updated else it } else loans.filterNot { it.id == updated.id }
                    notice = if (loan.status == "reserved") "Книга выдана" else "Возврат принят"
                    refresh()
                },
                onFailure = { error = it.message ?: "Не удалось выполнить операцию" },
            )
            busy = false
        }
    }
}
