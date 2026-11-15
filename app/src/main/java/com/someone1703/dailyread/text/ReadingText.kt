package com.someone1703.dailyread.text

import com.someone1703.dailyread.data.Reading

/** Something the reader shows, in order. */
sealed interface ReaderItem {
    /** The PDF page this came from (0-based), for "page X" labels. */
    val page: Int
}

/**
 * A heading, paragraph or code block of today's reading. [index] counts blocks within the
 * reading, so text to speech can later resume from a block.
 */
data class ReadBlock(
    val index: Int,
    val kind: BlockKind,
    val text: String,
    val level: Int,
    override val page: Int,
) : ReaderItem

/** Scanned pages whose text is still being recognized. */
data class PendingPages(val pages: List<Int>) : ReaderItem {
    override val page: Int get() = pages.first()
}

/** Builds the text of one reading from the cached pages. */
object ReadingText {

    /**
     * @param next the reading after this one. When it starts on this reading's last page or the
     *   page after, the shared page is cut at its heading so no text is shown twice or skipped.
     * @param load the cached text of a page, or null if there is none (blank, or not ready yet).
     * @param isPending whether a page without text is waiting for text recognition.
     */
    fun assemble(
        reading: Reading,
        next: Reading?,
        load: (Int) -> PageText?,
        isPending: (Int) -> Boolean,
    ): List<ReaderItem> {
        val tailPage = next?.startPage?.takeIf { it == reading.endPage + 1 }
        val pages = (reading.startPage..reading.endPage).toList() + listOfNotNull(tailPage)

        val items = mutableListOf<ReaderItem>()
        val pending = mutableListOf<Int>()
        var index = 0
        var lastWasPageEnd = false  // the previous block ended a page, so a paragraph may run on

        fun flushPending() {
            if (pending.isNotEmpty()) items += PendingPages(pending.toList())
            pending.clear()
        }

        for (p in pages) {
            val text = load(p)
            if (text == null) {
                if (isPending(p) && p != tailPage) pending += p
                continue
            }
            var blocks = text.blocks
            if (p == reading.startPage) {
                val at = anchorIndex(blocks, reading.anchorTitle)
                if (at > 0) blocks = blocks.drop(at)
            }
            if (next != null && p == next.startPage) {
                val from = if (p == reading.startPage) 1 else 0
                val at = anchorIndex(blocks, next.anchorTitle, from)
                blocks = when {
                    p == tailPage -> if (at > 0) blocks.take(at) else emptyList()
                    at >= 0 -> blocks.take(at)
                    else -> blocks
                }
            }
            if (blocks.isEmpty()) continue
            if (pending.isNotEmpty()) {
                flushPending()
                lastWasPageEnd = false
            }

            blocks.forEachIndexed { i, b ->
                val prev = items.lastOrNull() as? ReadBlock
                val runsOn = i == 0 && lastWasPageEnd && prev != null &&
                    prev.kind == BlockKind.PARAGRAPH && b.kind == BlockKind.PARAGRAPH &&
                    TextClean.continues(prev.text, b.text)
                if (runsOn) {
                    items[items.lastIndex] = prev!!.copy(text = TextClean.joinParagraphs(prev.text, b.text))
                } else {
                    items += ReadBlock(index++, b.kind, b.text, b.level, p)
                }
            }
            lastWasPageEnd = true
        }
        flushPending()
        return items
    }

    /** Where a reading's title appears among a page's blocks: a heading first, else a short line. */
    fun anchorIndex(blocks: List<TextBlock>, title: String, from: Int = 0): Int {
        if (title.isBlank()) return -1
        for (i in from until blocks.size) {
            val b = blocks[i]
            if (b.kind == BlockKind.HEADING && TextClean.titlesMatch(title, b.text)) return i
        }
        for (i in from until blocks.size) {
            val b = blocks[i]
            if (b.kind == BlockKind.PARAGRAPH && b.text.length <= 150 &&
                TextClean.normalize(b.text) == TextClean.normalize(title)
            ) return i
        }
        return -1
    }
}
