package com.example.libary.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    @SerialName("full_name") val fullName: String,
    val email: String,
    val role: String,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    val user: UserDto,
)

@Serializable
data class RegisterRequest(
    @SerialName("full_name") val fullName: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class BookDto(
    val id: Int,
    val title: String,
    val author: String,
    val year: Int,
    val publisher: String,
    @SerialName("total_copies") val totalCopies: Int,
    @SerialName("available_copies") val availableCopies: Int,
)

@Serializable
data class LoanDto(
    val id: Int,
    @SerialName("book_id") val bookId: Int,
    val title: String,
    val author: String,
    val year: Int,
    val publisher: String,
    val status: String,
    @SerialName("reserved_at") val reservedAt: String,
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("returned_at") val returnedAt: String? = null,
)
