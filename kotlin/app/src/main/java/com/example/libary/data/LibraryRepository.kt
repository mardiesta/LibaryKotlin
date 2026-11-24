package com.example.libary.data

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class UnauthorizedException(message: String) : Exception(message)
data class SessionRejection(val token: String, val message: String)

class LibraryRepository internal constructor(private val db: LibraryDatabase, private val tokenStore: TokenStore) {
    private val dao = db.libraryDao()
    private val invalidations = Channel<SessionRejection>(Channel.CONFLATED)
    val sessionInvalid: Flow<SessionRejection> = invalidations.receiveAsFlow()
    suspend fun token(): String? = tokenStore.read()
    suspend fun logout() = tokenStore.clear()

    private suspend fun initialize() = db.withTransaction {
        if (dao.books().isEmpty()) dao.insertBooks(listOf(
            BookEntity(title = "Мастер и Маргарита", author = "Михаил Булгаков", year = 1967, publisher = "Художественная литература", totalCopies = 3),
            BookEntity(title = "Собачье сердце", author = "Михаил Булгаков", year = 1925, publisher = "Азбука", totalCopies = 2),
            BookEntity(title = "Преступление и наказание", author = "Фёдор Достоевский", year = 1866, publisher = "Эксмо", totalCopies = 2),
            BookEntity(title = "Война и мир", author = "Лев Толстой", year = 1869, publisher = "АСТ", totalCopies = 2),
            BookEntity(title = "Евгений Онегин", author = "Александр Пушкин", year = 1833, publisher = "Наука", totalCopies = 1),
            BookEntity(title = "Сто лет одиночества", author = "Габриэль Гарсиа Маркес", year = 1967, publisher = "Азбука", totalCopies = 0),
            BookEntity(title = "1984", author = "Джордж Оруэлл", year = 1949, publisher = "АСТ", totalCopies = 2),
            BookEntity(title = "Маленький принц", author = "Антуан де Сент-Экзюпери", year = 1943, publisher = "Эксмо", totalCopies = 4),
        ))
        if (dao.userByEmail("librarian@library.local") == null) dao.insertUser(UserEntity(fullName = "Библиотекарь", email = "librarian@library.local", passwordHash = hashPassword("librarian"), role = "librarian"))
    }
    private suspend fun <T> call(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            initialize()
            Result.success(block())
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    private suspend fun current(): UserEntity = tokenStore.read()?.toIntOrNull()?.let { dao.user(it) } ?: throw UnauthorizedException("Сессия недействительна")
    private suspend fun reader(): UserEntity = current().also { check(it.role == "reader") { "Требуется роль читателя" } }
    private suspend fun librarian() { check(current().role == "librarian") { "Требуется роль библиотекаря" } }
    private fun UserEntity.dto() = UserDto(id, fullName, email, role)
    private suspend fun loginAs(user: UserEntity): UserDto { tokenStore.write(user.id.toString()); return user.dto() }

    suspend fun demoLogin(role: String): Result<UserDto> = call {
        require(role == "reader" || role == "librarian") { "Неизвестная роль" }
        val email = if (role == "reader") "demo-reader@library.local" else "librarian@library.local"
        val user = db.withTransaction {
            dao.userByEmail(email) ?: run {
                val entity = UserEntity(fullName = "Читатель", email = email, passwordHash = hashPassword(java.util.UUID.randomUUID().toString()), role = role)
                entity.copy(id = dao.insertUser(entity).toInt())
            }
        }
        check(user.role == role) { "Учётная запись демо занята другой ролью" }
        loginAs(user)
    }
    suspend fun register(fullName: String, email: String, password: String): Result<UserDto> = call {
        val name = fullName.trim(); val normalized = email.trim().lowercase(Locale.ROOT)
        require(name.length in 2..200) { "Укажите имя" }
        require(normalized.length in 3..200 && '@' in normalized) { "Укажите email" }
        require(password.length in 6..100) { "Пароль должен содержать от 6 до 100 символов" }
        val user = db.withTransaction {
            check(dao.userByEmail(normalized) == null) { "Пользователь с таким email уже зарегистрирован" }
            val entity = UserEntity(fullName = name, email = normalized, passwordHash = hashPassword(password), role = "reader")
            entity.copy(id = dao.insertUser(entity).toInt())
        }
        loginAs(user)
    }
    suspend fun login(email: String, password: String, role: String): Result<UserDto> = call {
        val user = dao.userByEmail(email.trim().lowercase(Locale.ROOT))
        if (user == null || !verifyPassword(password, user.passwordHash)) throw UnauthorizedException("Неверный email или пароль")
        require(user.role == role) { "Роль аккаунта не совпадает с выбранной" }
        loginAs(user)
    }
    suspend fun me(): Result<UserDto> = call { current().dto() }
    private suspend fun BookEntity.dto() = BookDto(id, title, author, year, publisher, totalCopies, (totalCopies - dao.taken(id)).coerceAtLeast(0))
    private suspend fun LoanEntity.dto(): LoanDto {
        val book = dao.book(bookId) ?: error("Книга не найдена")
        val user = dao.user(readerId) ?: error("Читатель не найден")
        return LoanDto(id, bookId, book.title, book.author, book.year, book.publisher, status, reservedAt, issuedAt, dueDate, returnedAt, user.fullName, user.email)
    }
    suspend fun books(query: String): Result<List<BookDto>> = call {
        current(); val needle = query.trim()
        dao.books().filter { it.title.contains(needle, true) || it.author.contains(needle, true) }.sortedBy { it.title }.map { it.dto() }
    }
    suspend fun book(id: Int): Result<BookDto> = call { current(); (dao.book(id) ?: error("Книга не найдена")).dto() }
    suspend fun reserve(id: Int): Result<LoanDto> = call { db.withTransaction {
        val user = reader(); val book = dao.book(id) ?: error("Книга не найдена")
        check(dao.existing(id, user.id) == 0) { "Эта книга уже забронирована вами" }
        check(dao.taken(id) < book.totalCopies) { "Нет свободных экземпляров" }
        val loan = LoanEntity(readerId = user.id, bookId = id, status = "reserved", reservedAt = now())
        loan.copy(id = dao.insertLoan(loan).toInt()).dto()
    } }
    suspend fun cancel(id: Int): Result<Unit> = call { db.withTransaction {
        val user = reader(); val loan = dao.loan(id)?.takeIf { it.readerId == user.id } ?: error("Бронь не найдена")
        check(loan.status == "reserved") { "Отменить можно только бронь" }; dao.updateLoan(loan.copy(status = "cancelled"))
    } }
    suspend fun loans(scope: String): Result<List<LoanDto>> = call {
        val user = reader(); require(scope == "active" || scope == "history") { "Неизвестный раздел" }
        dao.readerLoans(user.id).filter { if (scope == "active") it.status in listOf("reserved", "issued") else it.status == "returned" }
            .let { if (scope == "history") it.sortedByDescending { loan -> loan.returnedAt } else it }.map { it.dto() }
    }
    suspend fun librarianLoans(): Result<List<LoanDto>> = call { librarian(); dao.activeLoans().map { it.dto() } }
    suspend fun issue(id: Int): Result<LoanDto> = call { db.withTransaction {
        librarian(); val loan = dao.loan(id) ?: error("Запись не найдена")
        check(loan.status == "reserved") { "Выдать можно только забронированную книгу" }
        val due = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { add(Calendar.DAY_OF_YEAR, 14) }.time
        loan.copy(status = "issued", issuedAt = now(), dueDate = format(due, "yyyy-MM-dd")).also { dao.updateLoan(it) }.dto()
    } }
    suspend fun acceptReturn(id: Int): Result<LoanDto> = call { db.withTransaction {
        librarian(); val loan = dao.loan(id) ?: error("Запись не найдена")
        check(loan.status == "issued") { "Принять можно только выданную книгу" }
        loan.copy(status = "returned", returnedAt = now()).also { dao.updateLoan(it) }.dto()
    } }
    companion object {
        fun create(context: Context) = LibraryRepository(Room.databaseBuilder(context.applicationContext, LibraryDatabase::class.java, "library.db").build(), TokenStore(context.applicationContext))
    }
}
private fun format(date: Date, pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date)
private fun now() = format(Date(), "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
private fun derive(password: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, 120000, 256)
    return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
}
private fun hashPassword(password: String): String {
    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
    return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(derive(password, salt), Base64.NO_WRAP)
}
private fun verifyPassword(password: String, stored: String): Boolean {
    val parts = stored.split(':'); if (parts.size != 2) return false
    return MessageDigest.isEqual(derive(password, Base64.decode(parts[0], Base64.NO_WRAP)), Base64.decode(parts[1], Base64.NO_WRAP))
}
object LibraryGraph {
    @Volatile private var repository: LibraryRepository? = null
    fun repository(context: Context): LibraryRepository = repository ?: synchronized(this) {
        repository ?: LibraryRepository.create(context).also { repository = it }
    }
}
