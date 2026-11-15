package com.someone1703.dailyread.ui

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.someone1703.dailyread.DailyReadApp
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.EVERY_DAY
import com.someone1703.dailyread.data.Reading
import com.someone1703.dailyread.pdf.PdfInfo
import com.someone1703.dailyread.pdf.PdfOutline
import com.someone1703.dailyread.reminders.ReminderScheduler
import com.someone1703.dailyread.text.PrepProgress
import com.someone1703.dailyread.text.TextMeta
import com.someone1703.dailyread.text.TextPrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface SplitMode {
    data class Bookmarks(val depth: Int) : SplitMode
    data class Pages(val perDay: Int) : SplitMode
    /** Headings found in the text, for PDFs without bookmarks. */
    data class Headings(val depth: Int) : SplitMode
}

data class ImportState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val info: PdfInfo? = null,
    val mode: SplitMode = SplitMode.Pages(10),
    val readings: List<Reading> = emptyList(),
    val included: Set<Int> = emptySet(),
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = true,
    val reminderDays: Set<Int> = EVERY_DAY,
    /** Live progress of the text preparation running for this file. */
    val prep: PrepProgress? = null,
    val meta: TextMeta? = null,
) {
    val selectedCount: Int get() = included.size
    val extracted: Boolean get() = meta?.extracted == true
    val scannedCount: Int get() = meta?.scannedPages?.size ?: prep?.scanned ?: 0
    /** Depth to split at by detected headings, or 0 if none were found (yet). */
    val headingDepth: Int get() = meta?.takeIf { it.extracted }?.let { TextPrep.headingDepth(it) } ?: 0

    /** Whether saving now gives a book that's ready to read, rather than one still preparing. */
    val readyOnSave: Boolean get() {
        val m = meta?.takeIf { it.extracted } ?: return false
        if (m.scannedPages.isEmpty()) return true
        val ahead = readings.filterIndexed { i, _ -> i in included }.take(TextPrep.READY_AHEAD)
            .flatMap { TextPrep.pagesOf(it) }.toSet()
        return m.scannedPages.none { it in ahead }
    }
}

/** What happened on save: the new book, and whether it still has text to prepare. */
data class SaveResult(val bookId: String, val ready: Boolean)

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DailyReadApp).repository
    private val textStore = (app as DailyReadApp).textStore
    /** The book's id from the start, so its text can be prepared while the plan is set up. */
    private val bookId = UUID.randomUUID().toString()
    private var userPickedMode = false
    private val _state = MutableStateFlow(ImportState())
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var copiedFile: File? = null
    private var saved = false
    private var started = false

    fun load(uri: Uri) {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val (file, displayName) = withContext(Dispatchers.IO) { copyIntoApp(uri) }
                copiedFile = file
                val info = withContext(Dispatchers.IO) { readInfo(file) }
                val defaultMode = when {
                    info.maxDepth >= 2 -> SplitMode.Bookmarks(2)
                    info.maxDepth == 1 -> SplitMode.Bookmarks(1)
                    else -> SplitMode.Pages(10)
                }
                val title = info.title ?: displayName.removeSuffix(".pdf").removeSuffix(".PDF")
                _state.update { it.copy(loading = false, title = title, info = info) }
                setMode(defaultMode)
                TextPrep.start(getApplication(), bookId, file.name, title)
                watchText()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Couldn't open that PDF. It may be damaged or password-protected.") }
            }
        }
    }

    private fun watchText() = viewModelScope.launch {
        val metaFlow = textStore.versions.map { it[bookId] }.distinctUntilChanged()
            .map { withContext(Dispatchers.IO) { textStore.meta(bookId) } }
        val progressFlow = textStore.progress.map { it[bookId] }.distinctUntilChanged()
        combine(metaFlow, progressFlow) { meta, prep -> meta to prep }.collect { (meta, prep) ->
            val wasExtracted = _state.value.extracted
            _state.update { it.copy(meta = meta, prep = prep) }
            // No bookmarks: once headings are found, offer them as the plan unless the user chose otherwise.
            val s = _state.value
            if (!wasExtracted && s.extracted && (s.info?.maxDepth ?: 0) == 0 && !userPickedMode && s.headingDepth > 0) {
                setMode(SplitMode.Headings(s.headingDepth))
            }
        }
    }

    /** A mode chosen on screen, as opposed to the default. */
    fun pickMode(mode: SplitMode) {
        userPickedMode = true
        setMode(mode)
    }

    fun setTitle(title: String) = _state.update { it.copy(title = title) }

    fun setMode(mode: SplitMode) {
        val info = _state.value.info ?: return
        val readings = when (mode) {
            is SplitMode.Bookmarks -> PdfOutline.readingsFromOutline(info, mode.depth)
            is SplitMode.Pages -> PdfOutline.readingsByPages(info.pageCount, mode.perDay)
            is SplitMode.Headings -> _state.value.meta?.let { TextPrep.headingReadings(it, mode.depth) }.orEmpty()
        }
        _state.update { it.copy(mode = mode, readings = readings, included = readings.indices.toSet()) }
    }

    fun toggle(index: Int) = _state.update {
        it.copy(included = if (index in it.included) it.included - index else it.included + index)
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = _state.update {
        it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute)
    }

    fun setReminderDays(days: Set<Int>) = _state.update { it.copy(reminderDays = days) }

    /** Creates the book and arms its reminder. Text preparation carries on in the background. */
    fun save(): SaveResult? {
        val s = _state.value
        val file = copiedFile ?: return null
        val info = s.info ?: return null
        val readings = s.readings.filterIndexed { i, _ -> i in s.included }
        if (readings.isEmpty()) return null

        val book = Book(
            id = bookId,
            title = s.title.ifBlank { "Untitled" },
            fileName = file.name,
            pageCount = info.pageCount,
            readings = readings,
            reminderEnabled = s.reminderEnabled,
            reminderHour = s.reminderHour,
            reminderMinute = s.reminderMinute,
            reminderDays = s.reminderDays,
            // Still only split by page count because the text isn't read yet: use headings when it is.
            autoSplit = (info.maxDepth == 0) && !userPickedMode && !s.extracted,
        )
        repo.add(book)
        saved = true
        TextPrep.refresh(getApplication(), book.id, notify = false)
        repo.book(book.id)?.let { ReminderScheduler.schedule(getApplication(), it) }
        return SaveResult(book.id, ready = repo.book(book.id)?.textReady == true)
    }

    override fun onCleared() {
        // Backed out without saving: stop preparing and don't leave orphaned files behind.
        if (!saved) {
            TextPrep.forget(getApplication(), bookId)
            copiedFile?.delete()
        }
    }

    private fun copyIntoApp(uri: Uri): Pair<File, String> {
        val resolver = getApplication<Application>().contentResolver
        var name = "Book.pdf"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val target = File(repo.booksDir, "${UUID.randomUUID()}.pdf")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Can't read $uri" }
            target.outputStream().use { input.copyTo(it) }
        }
        return target to name
    }

    /** Bookmarks via PDFBox; if that fails, fall back to page count only from PdfRenderer. */
    private fun readInfo(file: File): PdfInfo = try {
        PdfOutline.read(file)
    } catch (e: Exception) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { PdfInfo(title = null, pageCount = it.pageCount, outline = emptyList()) }
        }
    }
}
