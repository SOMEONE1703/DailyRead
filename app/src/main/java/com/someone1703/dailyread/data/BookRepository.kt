package com.someone1703.dailyread.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores the library as a single JSON file in the app's private storage.
 * The PDFs themselves are copied into filesDir/books/ and never leave the device.
 */
class BookRepository(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val libraryFile = File(context.filesDir, "library.json")
    val booksDir: File = File(context.filesDir, "books").apply { mkdirs() }

    private val _books = MutableStateFlow(load())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    fun book(id: String): Book? = _books.value.firstOrNull { it.id == id }

    fun fileFor(book: Book): File = File(booksDir, book.fileName)

    fun add(book: Book) = mutate { it + book }

    fun delete(id: String) {
        book(id)?.let { fileFor(it).delete() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    fun setDone(bookId: String, index: Int, done: Boolean) = updateBook(bookId) { book ->
        book.copy(readings = book.readings.mapIndexed { i, r ->
            if (i == index) r.copy(completedAt = if (done) System.currentTimeMillis() else null) else r
        })
    }

    fun setReminder(bookId: String, enabled: Boolean, hour: Int, minute: Int) = updateBook(bookId) {
        it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute)
    }

    private fun updateBook(id: String, change: (Book) -> Book) = mutate { list ->
        list.map { if (it.id == id) change(it) else it }
    }

    @Synchronized
    private fun mutate(change: (List<Book>) -> List<Book>) {
        val updated = change(_books.value)
        save(updated)
        _books.value = updated
    }

    private fun load(): List<Book> = try {
        if (libraryFile.exists()) json.decodeFromString<List<Book>>(libraryFile.readText()) else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Write to a temp file and rename, so a crash mid-write can't corrupt the library. */
    private fun save(books: List<Book>) {
        val tmp = File(libraryFile.parentFile, "library.json.tmp")
        tmp.writeText(json.encodeToString(books))
        if (!tmp.renameTo(libraryFile)) {
            libraryFile.writeText(tmp.readText())
            tmp.delete()
        }
    }
}
