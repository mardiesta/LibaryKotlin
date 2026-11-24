package com.example.libary.data


data class UserDto(
    val id: Int,
    val fullName: String,
    val email: String,
    val role: String,
)

data class BookDto(
    val id: Int,
    val title: String,
    val author: String,
    val year: Int,
    val publisher: String,
    val totalCopies: Int,
    val availableCopies: Int,
)

data class LoanDto(
    val id: Int,
    val bookId: Int,
    val title: String,
    val author: String,
    val year: Int,
    val publisher: String,
    val status: String,
    val reservedAt: String,
    val issuedAt: String? = null,
    val dueDate: String? = null,
    val returnedAt: String? = null,
    val readerName: String = "",
    val readerEmail: String = "",
)
