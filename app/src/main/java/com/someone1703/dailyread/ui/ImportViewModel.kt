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
import com.someone1703.dailyread.data.Reading
import com.someone1703.dailyread.pdf.PdfInfo
import com.someone1703.dailyread.pdf.PdfOutline
import com.someone1703.dailyread.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
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
) {
    val selectedCount: Int get() = included.size
}

class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DailyReadApp).repository
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
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Couldn't open that PDF. It may be damaged or password-protected.") }
            }
        }
    }

    fun setTitle(title: String) = _state.update { it.copy(title = title) }

    fun setMode(mode: SplitMode) {
        val info = _state.value.info ?: return
        val readings = when (mode) {
            is SplitMode.Bookmarks -> PdfOutline.readingsFromOutline(info, mode.depth)
            is SplitMode.Pages -> PdfOutline.readingsByPages(info.pageCount, mode.perDay)
        }
        _state.update { it.copy(mode = mode, readings = readings, included = readings.indices.toSet()) }
    }

    fun toggle(index: Int) = _state.update {
        it.copy(included = if (index in it.included) it.included - index else it.included + index)
    }

    fun setReminder(enabled: Boolean, hour: Int, minute: Int) = _state.update {
        it.copy(reminderEnabled = enabled, reminderHour = hour, reminderMinute = minute)
    }

    /** Creates the book and arms its reminder. Returns the new book's id. */
    fun save(): String? {
        val s = _state.value
        val file = copiedFile ?: return null
        val info = s.info ?: return null
        val readings = s.readings.filterIndexed { i, _ -> i in s.included }
        if (readings.isEmpty()) return null

        val book = Book(
            id = UUID.randomUUID().toString(),
            title = s.title.ifBlank { "Untitled" },
            fileName = file.name,
            pageCount = info.pageCount,
            readings = readings,
            reminderEnabled = s.reminderEnabled,
            reminderHour = s.reminderHour,
            reminderMinute = s.reminderMinute,
        )
        repo.add(book)
        ReminderScheduler.schedule(getApplication(), book)
        saved = true
        return book.id
    }

    override fun onCleared() {
        // Backed out without saving: don't leave an orphaned copy of the PDF behind.
        if (!saved) copiedFile?.delete()
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
