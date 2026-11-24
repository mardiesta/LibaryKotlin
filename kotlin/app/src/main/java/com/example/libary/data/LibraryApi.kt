package com.example.libary.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LibraryApi {
    @POST("auth/demo")
    suspend fun demoLogin(@Body body: DemoLoginRequest): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(): UserDto

    @GET("books")
    suspend fun books(@Query("q") query: String?): List<BookDto>

    @GET("books/{id}")
    suspend fun book(@Path("id") id: Int): BookDto

    @POST("books/{id}/reservations")
    suspend fun reserve(@Path("id") id: Int): LoanDto

    @DELETE("loans/{id}")
    suspend fun cancel(@Path("id") id: Int): Response<Unit>

    @GET("librarian/loans")
    suspend fun librarianLoans(): List<LoanDto>

    @POST("librarian/loans/{id}/issue")
    suspend fun issue(@Path("id") id: Int): LoanDto

    @POST("librarian/loans/{id}/return")
    suspend fun acceptReturn(@Path("id") id: Int): LoanDto

    @GET("me/loans")
    suspend fun loans(@Query("scope") scope: String): List<LoanDto>
}
