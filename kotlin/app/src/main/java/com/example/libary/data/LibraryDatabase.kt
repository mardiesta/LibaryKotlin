package com.example.libary.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val fullName: String, val email: String, val passwordHash: String, val role: String)

@Entity(tableName = "books")
data class BookEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val title: String, val author: String, val year: Int, val publisher: String, val totalCopies: Int)

@Entity(tableName = "loans", foreignKeys = [
    ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["readerId"]),
    ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"]),
], indices = [Index("readerId"), Index("bookId")])
data class LoanEntity(@PrimaryKey(autoGenerate = true) val id: Int = 0, val readerId: Int, val bookId: Int, val status: String, val reservedAt: String, val issuedAt: String? = null, val dueDate: String? = null, val returnedAt: String? = null)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM users WHERE id = :id") suspend fun user(id: Int): UserEntity?
    @Query("SELECT * FROM users WHERE email = :email") suspend fun userByEmail(email: String): UserEntity?
    @Insert suspend fun insertUser(user: UserEntity): Long
    @Query("SELECT * FROM books") suspend fun books(): List<BookEntity>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun book(id: Int): BookEntity?
    @Insert suspend fun insertBooks(books: List<BookEntity>)
    @Query("SELECT * FROM loans WHERE id = :id") suspend fun loan(id: Int): LoanEntity?
    @Query("SELECT * FROM loans WHERE readerId = :readerId ORDER BY reservedAt DESC") suspend fun readerLoans(readerId: Int): List<LoanEntity>
    @Query("SELECT * FROM loans WHERE status IN ('reserved', 'issued') ORDER BY status DESC, reservedAt ASC") suspend fun activeLoans(): List<LoanEntity>
    @Query("SELECT COUNT(*) FROM loans WHERE bookId = :bookId AND status IN ('reserved', 'issued')") suspend fun taken(bookId: Int): Int
    @Query("SELECT COUNT(*) FROM loans WHERE bookId = :bookId AND readerId = :readerId AND status IN ('reserved', 'issued')") suspend fun existing(bookId: Int, readerId: Int): Int
    @Insert suspend fun insertLoan(loan: LoanEntity): Long
    @Update suspend fun updateLoan(loan: LoanEntity)
}

@Database(entities = [UserEntity::class, BookEntity::class, LoanEntity::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}
