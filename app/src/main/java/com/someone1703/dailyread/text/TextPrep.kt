package com.someone1703.dailyread.text

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.someone1703.dailyread.DailyReadApp
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.Reading
import com.someone1703.dailyread.pdf.OutlineEntry
import com.someone1703.dailyread.pdf.PdfInfo
import com.someone1703.dailyread.pdf.PdfOutline
import com.someone1703.dailyread.reminders.Notifications

/**
 * Schedules and tracks the preparation of a book's text:
 * 1. [TextPrepWorker] reads the PDF's text layer and finds image-only pages (foreground, with a
 *    progress notification).
 * 2. [OcrWorker] recognizes the scanned pages of the next readings straight away, then the rest
 *    of the book while the phone is charging.
 * A book is ready once its text is extracted and its next readings have no scanned pages left.
 */
object TextPrep {

    /** How many upcoming readings must be fully prepared for a book to count as ready. */
    const val READY_AHEAD = 2

    private const val KEY_BOOK = "book"
    private const val KEY_FILE = "file"
    private const val KEY_TITLE = "title"
    private const val KEY_PAGES = "pages"
    private const val KEY_FOREGROUND = "foreground"

    private fun tag(bookId: String) = "book:$bookId"

    private fun store(context: Context) = (context.applicationContext as DailyReadApp).textStore
    private fun repo(context: Context) = (context.applicationContext as DailyReadApp).repository

    /** Starts reading the text of a freshly copied PDF. Safe to call again: a running job is kept. */
    fun start(context: Context, bookId: String, fileName: String, title: String) {
        val request = OneTimeWorkRequestBuilder<TextPrepWorker>()
            .setInputData(workDataOf(KEY_BOOK to bookId, KEY_FILE to fileName, KEY_TITLE to title))
            .addTag(tag(bookId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("extract:$bookId", ExistingWorkPolicy.KEEP, request)
    }

    /** Stops all work for a book and removes its prepared text. */
    fun forget(context: Context, bookId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tag(bookId))
        store(context).delete(bookId)
    }

    /** On app start: prepare books saved before the text reader, and resume anything unfinished. */
    fun resumeAll(context: Context) {
        val repo = repo(context)
        for (book in repo.books.value) {
            if (!repo.fileFor(book).exists()) continue
            if (store(context).meta(book.id)?.extracted == true) refresh(context, book.id, notify = false)
            else start(context, book.id, book.fileName, book.title)
        }
    }

    /**
     * Brings a book's state up to date with its text: applies the heading-based plan, queues text
     * recognition for scanned pages, and marks the book ready (notifying if [notify]).
     */
    @Synchronized
    fun refresh(context: Context, bookId: String, notify: Boolean) {
        val repo = repo(context)
        val store = store(context)
        var book = repo.book(bookId) ?: return
        val meta = store.meta(bookId)?.takeIf { it.extracted } ?: return

        if (book.autoSplit) {
            val depth = headingDepth(meta)
            repo.replaceReadings(bookId, if (depth > 0) headingReadings(meta, depth) else emptyList())
            book = repo.book(bookId) ?: return
        }

        val pending = store.pendingScans(bookId)
        val ahead = upcomingPages(book)
        val urgent = pending.filter { it in ahead }
        val rest = pending.filterNot { it in ahead }
        if (urgent.isNotEmpty()) recognize(context, book, urgent, foreground = true)
        if (rest.isNotEmpty()) recognizeWhileCharging(context, book)

        if (urgent.isEmpty() && !book.textReady) {
            repo.setTextReady(bookId, true)
            if (notify) repo.book(bookId)?.let { Notifications.showBookReady(context, it, rest.size) }
        }
    }

    /** Asks for specific pages now, e.g. the reading open on screen. */
    fun requestPages(context: Context, book: Book, pages: List<Int>) {
        val pending = store(context).pendingScans(book.id).filter { it in pages }
        if (pending.isNotEmpty()) recognize(context, book, pending, foreground = true)
    }

    private fun recognize(context: Context, book: Book, pages: List<Int>, foreground: Boolean) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(
                workDataOf(
                    KEY_BOOK to book.id,
                    KEY_FILE to book.fileName,
                    KEY_TITLE to book.title,
                    KEY_PAGES to pages.toIntArray(),
                    KEY_FOREGROUND to foreground,
                )
            )
            .addTag(tag(book.id))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("ocr-next:${book.id}", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Everything else, in reading order, only while charging and with a healthy battery. */
    private fun recognizeWhileCharging(context: Context, book: Book) {
        val request = OneTimeWorkRequestBuilder<OcrWorker>()
            .setInputData(workDataOf(KEY_BOOK to book.id, KEY_FILE to book.fileName, KEY_TITLE to book.title))
            .setConstraints(Constraints.Builder().setRequiresCharging(true).setRequiresBatteryNotLow(true).build())
            .addTag(tag(book.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("ocr-rest:${book.id}", ExistingWorkPolicy.KEEP, request)
    }

    /** Pages of the next [READY_AHEAD] unread readings, plus the page each one may run onto. */
    fun upcomingPages(book: Book, ahead: Int = READY_AHEAD): Set<Int> {
        val start = book.nextIndex ?: return emptySet()
        return book.readings.drop(start).take(ahead).flatMap { pagesOf(it) }.toSet()
    }

    fun pagesOf(r: Reading): List<Int> = (r.startPage..r.endPage + 1).toList()

    /** Scanned pages in the order they'll be read: unread readings first, then the rest. */
    fun readingOrder(book: Book?, pages: List<Int>): List<Int> {
        if (book == null) return pages.sorted()
        val order = book.readings.filterNot { it.isDone }.flatMap { pagesOf(it) }.distinct()
        val rank = order.withIndex().associate { it.value to it.index }
        return pages.sortedWith(compareBy({ rank[it] ?: Int.MAX_VALUE }, { it }))
    }

    /** Chapters if there are enough of them at a sensible length, otherwise sections. 0 = none usable. */
    fun headingDepth(meta: TextMeta): Int {
        val chapters = meta.headings.count { it.level == 1 }
        val sections = meta.headings.count { it.level <= 2 }
        return when {
            chapters >= 3 && meta.pageCount / chapters <= 40 -> 1
            sections >= 3 -> 2
            chapters >= 2 -> 1
            else -> 0
        }
    }

    fun headingReadings(meta: TextMeta, depth: Int): List<Reading> {
        val info = PdfInfo(
            title = null,
            pageCount = meta.pageCount,
            outline = meta.headings.map { OutlineEntry(it.title, it.level, it.page) },
        )
        return PdfOutline.readingsFromOutline(info, depth)
    }

    internal fun bookId(data: androidx.work.Data) = data.getString(KEY_BOOK)
    internal fun fileName(data: androidx.work.Data) = data.getString(KEY_FILE)
    internal fun title(data: androidx.work.Data) = data.getString(KEY_TITLE) ?: "your book"
    internal fun pages(data: androidx.work.Data) = data.getIntArray(KEY_PAGES)?.toList()
    internal fun foreground(data: androidx.work.Data) = data.getBoolean(KEY_FOREGROUND, false)
}
