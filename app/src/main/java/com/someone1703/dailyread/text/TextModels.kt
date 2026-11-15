package com.someone1703.dailyread.text

import kotlinx.serialization.Serializable

/** What a block of text is. Kept small so text to speech can later step through blocks one by one. */
@Serializable
enum class BlockKind { HEADING, PARAGRAPH, CODE }

/**
 * One heading, paragraph or code listing on a page.
 * [level] is only used for headings: 1 = chapter-sized, 2 = section, 3 = minor (bold run-in titles).
 */
@Serializable
data class TextBlock(
    val kind: BlockKind,
    val text: String,
    val level: Int = 0,
)

@Serializable
enum class TextSource { PDF, OCR }

/** The cleaned text of one page, cached in filesDir/text/<bookId>/. */
@Serializable
data class PageText(
    val page: Int,
    val blocks: List<TextBlock>,
    val source: TextSource = TextSource.PDF,
)

/** A heading found while preparing the text, used as a split point when the PDF has no bookmarks. */
@Serializable
data class DetectedHeading(val title: String, val level: Int, val page: Int)

/** What the preparation of a book's text has found so far. */
@Serializable
data class TextMeta(
    val pageCount: Int,
    /** The PdfBox pass over every page has finished. */
    val extracted: Boolean = false,
    /** Pages with no usable text layer that need text recognition. */
    val scannedPages: List<Int> = emptyList(),
    val headings: List<DetectedHeading> = emptyList(),
)

/** Live progress of a running preparation, kept in memory for the UI. */
data class PrepProgress(
    val phase: Phase,
    val done: Int,
    val total: Int,
    /** Image-only pages found so far. */
    val scanned: Int = 0,
) {
    enum class Phase { EXTRACTING, RECOGNIZING }

    val fraction: Float get() = if (total <= 0) 0f else done.toFloat() / total
}

/** Rough on-device text recognition speed (render + recognize), for the estimate shown to the user. */
const val OCR_SECONDS_PER_PAGE = 1.5

fun ocrEstimateLabel(pages: Int): String {
    val minutes = Math.round(pages * OCR_SECONDS_PER_PAGE / 60.0).toInt()
    return when {
        minutes < 1 -> "under a minute"
        minutes == 1 -> "about 1 minute"
        minutes < 60 -> "about $minutes minutes"
        else -> "about ${"%.1f".format(minutes / 60.0)} hours"
    }
}
