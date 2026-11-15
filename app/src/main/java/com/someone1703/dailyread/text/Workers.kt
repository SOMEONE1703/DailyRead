package com.someone1703.dailyread.text

import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.someone1703.dailyread.DailyReadApp
import com.someone1703.dailyread.reminders.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "TextPrep"

private val Context.app get() = applicationContext as DailyReadApp

/** Shows the progress notification and keeps the job alive as a foreground service, if allowed. */
private suspend fun CoroutineWorker.foreground(bookId: String, title: String, text: String, done: Int, total: Int) {
    val notification = Notifications.prepProgress(applicationContext, bookId, title, text, done, total)
    val id = Notifications.prepNotificationId(bookId)
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }
    try {
        setForeground(info)
    } catch (e: Exception) {
        // Not allowed to start a foreground service right now (e.g. started from the background).
        // The work still runs, just without the notification.
        Log.w(TAG, "Couldn't run in the foreground", e)
    }
}

/** Reads the text layer of every page, finds the scanned ones, and caches cleaned blocks. */
class TextPrepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = TextPrep.bookId(inputData) ?: return Result.failure()
        val fileName = TextPrep.fileName(inputData) ?: return Result.failure()
        val store = applicationContext.app.textStore
        val repo = applicationContext.app.repository
        val file = File(repo.booksDir, fileName)
        if (!file.exists()) return Result.failure()
        fun title() = repo.book(bookId)?.title ?: TextPrep.title(inputData)

        if (store.meta(bookId)?.extracted != true) {
            try {
                extract(bookId, file, store, ::title)
            } catch (e: Exception) {
                Log.e(TAG, "Text extraction failed, falling back to text recognition", e)
                // Unreadable text layer: treat every page as scanned so recognition still gets the text.
                val count = withContext(Dispatchers.IO) { rendererPageCount(file) } ?: return Result.failure()
                store.writeMeta(bookId, TextMeta(count, extracted = true, scannedPages = (0 until count).toList()))
            } finally {
                store.setProgress(bookId, null)
            }
            if (isStopped) return Result.retry()
        }
        TextPrep.refresh(applicationContext, bookId, notify = true)
        return Result.success()
    }

    private suspend fun extract(bookId: String, file: File, store: TextStore, title: () -> String) {
        val total = withContext(Dispatchers.IO) { PdfTextExtractor.pageCount(file) }
        foreground(bookId, title(), "Reading the text", 0, total)
        val raw = ArrayList<RawPage>(total)
        var scanned = 0
        for (from in 0 until total step PdfTextExtractor.CHUNK_PAGES) {
            if (isStopped) return
            val to = minOf(from + PdfTextExtractor.CHUNK_PAGES, total)
            val chunk = withContext(Dispatchers.IO) {
                try {
                    PdfTextExtractor.readChunk(file, from, to)
                } catch (e: Exception) {
                    // A bad chunk shouldn't sink the book: recognize those pages instead.
                    Log.w(TAG, "Pages $from-$to failed, will recognize them", e)
                    (from until to).map { RawPage(it, emptyList(), 0f, 0f, hasImages = true) }
                }
            }
            raw += chunk
            scanned += chunk.count { PdfTextExtractor.isScanned(it) }
            store.setProgress(bookId, PrepProgress(PrepProgress.Phase.EXTRACTING, to, total, scanned))
            val note = if (scanned > 0) "Page $to of $total · $scanned scanned" else "Page $to of $total"
            foreground(bookId, title(), note, to, total)
        }

        withContext(Dispatchers.Default) {
            val scannedPages = raw.filter { PdfTextExtractor.isScanned(it) }.map { it.page }.toSet()
            val textPages = raw.filterNot { it.page in scannedPages }
            val layout = PageLayout(textPages)
            val headings = mutableListOf<DetectedHeading>()
            for (p in textPages) {
                val blocks = layout.blocks(p)
                if (blocks.isEmpty()) continue
                store.writePage(bookId, PageText(p.page, blocks), notify = false)
                headings += layout.headings(p.page, blocks)
            }
            store.writeMeta(bookId, TextMeta(total, extracted = true, scannedPages.sorted(), headings))
        }
    }

    private fun rendererPageCount(file: File): Int? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Recognizes the text of scanned pages: either the pages given (the next readings, run in the
 * foreground) or every pending page in reading order (queued to run while charging).
 */
class OcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = TextPrep.bookId(inputData) ?: return Result.failure()
        val fileName = TextPrep.fileName(inputData) ?: return Result.failure()
        val store = applicationContext.app.textStore
        val repo = applicationContext.app.repository
        val file = File(repo.booksDir, fileName)
        if (!file.exists()) return Result.failure()
        fun title() = repo.book(bookId)?.title ?: TextPrep.title(inputData)

        val wanted = TextPrep.pages(inputData)
        val pending = store.pendingScans(bookId).let { all ->
            if (wanted != null) all.filter { it in wanted } else TextPrep.readingOrder(repo.book(bookId), all)
        }
        if (pending.isEmpty()) {
            TextPrep.refresh(applicationContext, bookId, notify = true)
            return Result.success()
        }
        val showProgress = TextPrep.foreground(inputData)
        if (showProgress) foreground(bookId, title(), "Recognizing scanned pages", 0, pending.size)

        try {
            withContext(Dispatchers.Default) {
                OcrEngine(file).use { engine ->
                    pending.forEachIndexed { i, page ->
                        if (isStopped) return@use
                        if (store.hasPage(bookId, page)) return@forEachIndexed
                        val text = try {
                            engine.recognize(page)
                        } catch (e: Exception) {
                            Log.w(TAG, "Recognition failed on page $page", e)
                            PageText(page, emptyList(), TextSource.OCR)  // don't retry it forever
                        }
                        store.writePage(bookId, text)
                        store.setProgress(bookId, PrepProgress(PrepProgress.Phase.RECOGNIZING, i + 1, pending.size))
                        if (showProgress) {
                            foreground(bookId, title(), "Recognizing scanned page ${i + 1} of ${pending.size}", i + 1, pending.size)
                        }
                    }
                }
            }
        } finally {
            store.setProgress(bookId, null)
        }
        if (isStopped) return Result.retry()
        TextPrep.refresh(applicationContext, bookId, notify = true)
        return Result.success()
    }
}
