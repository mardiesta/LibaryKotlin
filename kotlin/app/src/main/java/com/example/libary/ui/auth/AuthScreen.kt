package com.example.libary.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun AuthScreen(
    repository: LibraryRepository,
    onAuthenticated: (UserDto) -> Unit,
    notice: String? = null,
) {
    val viewModel: AuthViewModel = viewModel(factory = viewModelFactory {
        initializer { AuthViewModel(repository) }
    })
    LaunchedEffect(notice) {
        if (!notice.isNullOrBlank()) viewModel.error = notice
    }
    val scroll = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("Библиотека", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Найдите книгу, забронируйте её и следите за сроком возврата.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Row {
            FilterChip(
                selected = !viewModel.isRegister,
                onClick = { viewModel.isRegister = false },
                label = { Text("Вход") },
            )
            Spacer(Modifier.size(8.dp))
            FilterChip(
                selected = viewModel.isRegister,
                onClick = { viewModel.isRegister = true },
                label = { Text("Регистрация") },
            )
        }
        Spacer(Modifier.height(20.dp))
        if (viewModel.isRegister) {
            OutlinedTextField(
                value = viewModel.fullName,
                onValueChange = { viewModel.fullName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = if (viewModel.passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { viewModel.passwordVisible = !viewModel.passwordVisible }) {
                    Icon(
                        imageVector = if (viewModel.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (viewModel.passwordVisible) "Скрыть пароль" else "Показать пароль",
                    )
                }
            },
        )
        viewModel.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.submit(onAuthenticated) },
            enabled = !viewModel.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (viewModel.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(if (viewModel.isRegister) "Создать аккаунт" else "Войти")
            }
        }
    }
}

class AuthViewModel(private val repository: LibraryRepository) : ViewModel() {
    var fullName by mutableStateOf("")
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordVisible by mutableStateOf(false)
    var isRegister by mutableStateOf(false)
    var submitting by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun submit(onSuccess: (UserDto) -> Unit) {
        val name = fullName.trim()
        val mail = email.trim()
        if (mail.isBlank() || password.isBlank() || (isRegister && name.length < 2)) {
            error = "Заполните все поля"
            return
        }
        if (password.length < 6) {
            error = "Пароль должен быть не короче 6 символов"
            return
        }
        viewModelScope.launch {
            submitting = true
            error = null
            val result = if (isRegister) {
                repository.register(name, mail, password)
            } else {
                repository.login(mail, password)
            }
            submitting = false
            result.fold(
                onSuccess = onSuccess,
                onFailure = { error = it.message ?: "Не удалось выполнить запрос" },
            )
        }
    }
}
