package com.example.libary.data

import android.content.Context
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class UnauthorizedException(message: String) : Exception(message)

data class SessionRejection(val token: String, val message: String)

class LibraryRepository private constructor(
    private val api: LibraryApi,
    private val tokenStore: TokenStore,
    private val json: Json,
) {
    private val invalidations = Channel<SessionRejection>(capacity = Channel.CONFLATED)
    val sessionInvalid: Flow<SessionRejection> = invalidations.receiveAsFlow()
    suspend fun token(): String? = tokenStore.read()

    suspend fun logout() {
        tokenStore.clear()
    }

    suspend fun register(fullName: String, email: String, password: String): Result<UserDto> {
        return call { api.register(RegisterRequest(fullName, email, password)) }
            .onSuccess { tokenStore.write(it.accessToken) }
            .map { it.user }
    }

    suspend fun login(email: String, password: String): Result<UserDto> {
        return call { api.login(LoginRequest(email, password)) }
            .onSuccess { tokenStore.write(it.accessToken) }
            .map { it.user }
    }

    suspend fun me(): Result<UserDto> = call { api.me() }

    suspend fun books(query: String): Result<List<BookDto>> {
        return call { api.books(query.trim().ifBlank { null }) }
    }

    suspend fun book(id: Int): Result<BookDto> = call { api.book(id) }

    suspend fun reserve(id: Int): Result<LoanDto> = call { api.reserve(id) }

    suspend fun loans(scope: String): Result<List<LoanDto>> = call { api.loans(scope) }

    suspend fun cancel(id: Int): Result<Unit> {
        return try {
            val response = api.cancel(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(httpFailure(response.code(), response.errorBody()?.string().orEmpty()))
            }
        } catch (_: IOException) {
            Result.failure(IOException("Нет соединения с сервером"))
        }
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: HttpException) {
            Result.failure(httpFailure(error.code(), error.response()?.errorBody()?.string().orEmpty()))
        } catch (_: IOException) {
            Result.failure(IOException("Нет соединения с сервером"))
        }
    }

    private fun httpFailure(code: Int, raw: String): Exception {
        val message = detail(raw) ?: "Не удалось выполнить запрос"
        if (code != 401) return IllegalStateException(message)
        if (message != "Неверный email или пароль") {
            val token = runBlocking { tokenStore.read() }.orEmpty()
            if (token.isNotEmpty()) invalidations.trySend(SessionRejection(token, message))
        }
        return UnauthorizedException(message)
    }

    private fun detail(raw: String): String? {
        return try {
            val value = json.parseToJsonElement(raw).jsonObject["detail"]
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun create(context: Context): LibraryRepository {
            val tokenStore = TokenStore(context.applicationContext)
            val json = Json { ignoreUnknownKeys = true }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(tokenStore))
                .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return LibraryRepository(retrofit.create(LibraryApi::class.java), tokenStore, json)
        }
    }
}

private class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = runBlocking { tokenStore.read() }
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(request)
    }
}

object LibraryGraph {
    @Volatile
    private var repository: LibraryRepository? = null

    fun repository(context: Context): LibraryRepository {
        return repository ?: synchronized(this) {
            repository ?: LibraryRepository.create(context).also { repository = it }
        }
    }
}
