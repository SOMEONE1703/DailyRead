package com.someone1703.dailyread.text

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The prepared text of each book: one small JSON file per page plus a meta file, in
 * filesDir/text/<bookId>/. Per-page files let text recognition fill in scanned pages one at a
 * time, and the reader loads only the pages of today's reading. Excluded from cloud backup, like
 * the PDFs: it can always be prepared again from the PDF.
 */
class TextStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val root = File(context.filesDir, "text").apply { mkdirs() }

    private val _versions = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Bumped for a book each time any of its text changes, so open screens can reload. */
    val versions: StateFlow<Map<String, Int>> = _versions.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, PrepProgress>>(emptyMap())
    /** Progress of preparations running in this process. */
    val progress: StateFlow<Map<String, PrepProgress>> = _progress.asStateFlow()

    private fun dir(bookId: String) = File(root, bookId)
    private fun pageFile(bookId: String, page: Int) = File(dir(bookId), "p$page.json")
    private fun metaFile(bookId: String) = File(dir(bookId), "meta.json")

    fun meta(bookId: String): TextMeta? = read(metaFile(bookId))

    fun writeMeta(bookId: String, meta: TextMeta) {
        write(metaFile(bookId), json.encodeToString(TextMeta.serializer(), meta))
        bump(bookId)
    }

    fun page(bookId: String, page: Int): PageText? = read(pageFile(bookId, page))

    fun hasPage(bookId: String, page: Int): Boolean = pageFile(bookId, page).exists()

    fun writePage(bookId: String, text: PageText, notify: Boolean = true) {
        write(pageFile(bookId, text.page), json.encodeToString(PageText.serializer(), text))
        if (notify) bump(bookId)
    }

    /** Scanned pages that text recognition hasn't done yet. */
    fun pendingScans(bookId: String): List<Int> {
        val meta = meta(bookId) ?: return emptyList()
        return meta.scannedPages.filterNot { hasPage(bookId, it) }
    }

    fun delete(bookId: String) {
        dir(bookId).deleteRecursively()
        setProgress(bookId, null)
        bump(bookId)
    }

    fun setProgress(bookId: String, progress: PrepProgress?) = _progress.update {
        if (progress == null) it - bookId else it + (bookId to progress)
    }

    fun bump(bookId: String) = _versions.update { it + (bookId to (it[bookId] ?: 0) + 1) }

    private inline fun <reified T> read(file: File): T? = try {
        if (file.exists()) json.decodeFromString<T>(file.readText()) else null
    } catch (e: Exception) {
        null
    }

    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
