package com.example.libary.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {
    private lateinit var db: LibraryDatabase
    private lateinit var repository: LibraryRepository

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
        repository = LibraryRepository(db, TokenStore(context))
        repository.logout()
    }
    @After fun cleanup() = runBlocking { repository.logout(); db.close() }

    @Test fun reservationIssueAndReturnPersistAcrossRoles() = runBlocking {
        repository.demoLogin("reader").getOrThrow()
        val book = repository.books("1984").getOrThrow().single()
        val loan = repository.reserve(book.id).getOrThrow()
        assertEquals(book.availableCopies - 1, repository.book(book.id).getOrThrow().availableCopies)
        assertTrue(repository.reserve(book.id).isFailure)
        repository.demoLogin("librarian").getOrThrow()
        assertEquals(loan.id, repository.librarianLoans().getOrThrow().single().id)
        assertEquals("issued", repository.issue(loan.id).getOrThrow().status)
        assertTrue(repository.issue(loan.id).isFailure)
        assertEquals("returned", repository.acceptReturn(loan.id).getOrThrow().status)
        repository.demoLogin("reader").getOrThrow()
        assertEquals(book.availableCopies, repository.book(book.id).getOrThrow().availableCopies)
        assertEquals(loan.id, repository.loans("history").getOrThrow().single().id)
    }

    @Test fun cancelledReservationRestoresAvailabilityAndChecksRole() = runBlocking {
        repository.demoLogin("reader").getOrThrow()
        val book = repository.books("1984").getOrThrow().single()
        val loan = repository.reserve(book.id).getOrThrow()
        assertTrue(repository.issue(loan.id).isFailure)
        repository.cancel(loan.id).getOrThrow()
        assertTrue(repository.cancel(loan.id).isFailure)
        assertEquals(book.availableCopies, repository.book(book.id).getOrThrow().availableCopies)
        assertTrue(repository.loans("active").getOrThrow().isEmpty())
    }
}
