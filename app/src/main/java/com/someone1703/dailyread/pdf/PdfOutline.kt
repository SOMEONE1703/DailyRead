package com.someone1703.dailyread.pdf

import com.someone1703.dailyread.data.Reading
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import java.io.File

/** A bookmark from the PDF's outline. [depth] 1 = top level (e.g. chapters), 2 = nested (e.g. topics). */
data class OutlineEntry(val title: String, val depth: Int, val page: Int)

data class PdfInfo(val title: String?, val pageCount: Int, val outline: List<OutlineEntry>) {
    val maxDepth: Int get() = outline.maxOfOrNull { it.depth } ?: 0
}

object PdfOutline {

    fun read(file: File): PdfInfo = PDDocument.load(file).use { doc ->
        val entries = mutableListOf<OutlineEntry>()
        doc.documentCatalog.documentOutline?.let { collect(it, doc, 1, entries) }
        PdfInfo(
            title = doc.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() },
            pageCount = doc.numberOfPages,
            outline = entries.sortedBy { it.page },
        )
    }

    private fun collect(node: PDOutlineNode, doc: PDDocument, depth: Int, out: MutableList<OutlineEntry>) {
        for (item in node.children()) {
            val page = try {
                item.findDestinationPage(doc)?.let { doc.pages.indexOf(it) } ?: -1
            } catch (e: Exception) {
                -1
            }
            val title = item.title?.trim().orEmpty()
            if (page >= 0 && title.isNotEmpty()) out += OutlineEntry(title, depth, page)
            if (depth < 6) collect(item, doc, depth + 1, out)
        }
    }

    /**
     * Turns bookmarks down to [depth] into readings. Each runs until the next bookmark starts.
     * A parent bookmark immediately followed by its first child (e.g. a chapter's short intro
     * page before Topic 1) is folded into that child so you don't get a one-page "day".
     */
    fun readingsFromOutline(info: PdfInfo, depth: Int): List<Reading> {
        val entries = info.outline.filter { it.depth <= depth }.sortedBy { it.page }
        if (entries.isEmpty()) return emptyList()

        val result = mutableListOf<Reading>()
        val titleAtDepth = mutableMapOf<Int, String>()  // most recent bookmark seen at each level
        var pendingStart: Int? = null                     // start page carried over from a folded parent

        entries.forEachIndexed { i, entry ->
            titleAtDepth[entry.depth] = entry.title
            titleAtDepth.keys.filter { it > entry.depth }.forEach { titleAtDepth.remove(it) }

            val next = entries.getOrNull(i + 1)
            val start = minOf(pendingStart ?: entry.page, entry.page)
            // At least its own start page, so two bookmarks on one page share it rather than one vanishing.
            val end = maxOf((next?.page ?: info.pageCount) - 1, entry.page)

            if (next != null && next.depth > entry.depth) {
                // Parent whose first child follows: fold its pages into the child.
                pendingStart = start
                return@forEachIndexed
            }
            pendingStart = null

            result += Reading(
                title = entry.title,
                subtitle = titleAtDepth[entry.depth - 1],
                startPage = start,
                endPage = end,
            )
        }

        // Pages before the first bookmark (cover, copyright...) are skipped; users can untick others.
        return result
    }

    /** Fallback when there are no bookmarks, or the user prefers a fixed amount per day. */
    fun readingsByPages(pageCount: Int, pagesPerDay: Int, startPage: Int = 0): List<Reading> {
        val size = pagesPerDay.coerceAtLeast(1)
        return (startPage until pageCount step size).mapIndexed { day, start ->
            val end = minOf(start + size - 1, pageCount - 1)
            Reading(title = "Day ${day + 1}", subtitle = null, startPage = start, endPage = end)
        }
    }
}
