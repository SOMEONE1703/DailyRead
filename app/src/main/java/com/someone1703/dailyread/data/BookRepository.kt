package com.someone1703.dailyread.data

import android.content.Context
import com.someone1703.dailyread.pdf.PdfPageRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

/**
 * Stores the library as a single JSON file in the app's private storage.
 * The PDFs themselves are copied into filesDir/books/, which is excluded from cloud backup,
 * so after a restore a book can exist here without its PDF until the user reimports it.
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

    /**
     * Puts back the PDF of a book that lost it, keeping the plan and progress.
     * Returns false if the file can't be read or has a different page count, since the plan's
     * page ranges wouldn't line up with another file.
     */
    fun restorePdf(book: Book, source: InputStream): Boolean {
        val tmp = File(booksDir, "${book.fileName}.tmp")
        return try {
            tmp.outputStream().use { source.copyTo(it) }
            val pages = PdfPageRenderer(tmp).use { it.pageCount }
            pages == book.pageCount && tmp.renameTo(fileFor(book))
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    fun setDone(bookId: String, index: Int, done: Boolean) = updateBook(bookId) { book ->
        book.copy(readings = book.readings.mapIndexed { i, r ->
            if (i == index) r.copy(completedAt = if (done) System.currentTimeMillis() else null) else r
        })
    }

    fun setReminder(bookId: String, enabled: Boolean, hour: Int, minute: Int) = updateBook(bookId) {
        it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute)
    }

    fun setReminderDays(bookId: String, days: Set<Int>) = updateBook(bookId) {
        it.copy(reminderDays = days)
    }

    fun setTextReady(bookId: String, ready: Boolean) = updateBook(bookId) { it.copy(textReady = ready) }

    /** Swaps in a new plan, but only while nothing has been read, so progress is never lost. */
    fun replaceReadings(bookId: String, readings: List<Reading>) = updateBook(bookId) { book ->
        if (book.doneCount == 0 && readings.isNotEmpty()) book.copy(readings = readings, autoSplit = false)
        else book.copy(autoSplit = false)
    }

    fun renameReading(bookId: String, index: Int, title: String) = updateBook(bookId) { book ->
        val clean = title.trim()
        if (clean.isEmpty()) return@updateBook book
        book.copy(readings = book.readings.mapIndexed { i, r -> if (i == index) r.copy(title = clean, anchor = r.anchorTitle) else r })
    }

    /**
     * Joins a reading with the one after it into a single day. It keeps the first title, and counts
     * as done only if both were.
     */
    fun mergeWithNext(bookId: String, index: Int) = updateBook(bookId) { book ->
        val a = book.readings.getOrNull(index) ?: return@updateBook book
        val b = book.readings.getOrNull(index + 1) ?: return@updateBook book
        val merged = a.copy(
            startPage = minOf(a.startPage, b.startPage),
            endPage = maxOf(a.endPage, b.endPage),
            completedAt = if (a.isDone && b.isDone) maxOf(a.completedAt!!, b.completedAt!!) else null,
        )
        book.copy(readings = book.readings.take(index) + merged + book.readings.drop(index + 2))
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
