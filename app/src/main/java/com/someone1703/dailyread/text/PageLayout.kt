package com.someone1703.dailyread.text

import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of text as the PDF text pass saw it. Coordinates are in points, y grows downwards. */
data class RawLine(
    val text: String,
    val x: Float,
    val xEnd: Float,
    /** Baseline, from the top of the page. */
    val y: Float,
    val size: Float,
    val bold: Boolean = false,
    val mono: Boolean = false,
)

data class RawPage(
    val page: Int,
    val lines: List<RawLine>,
    val width: Float,
    val height: Float,
    val hasImages: Boolean,
) {
    val charCount: Int get() = lines.sumOf { l -> l.text.count { !it.isWhitespace() } }
}

/**
 * Turns raw lines into headings, paragraphs and code blocks. Works on the whole book at once so it
 * can learn the body font size and spot running headers and footers that repeat across pages.
 */
class PageLayout(pages: List<RawPage>) {

    /** The size most of the text is set in, weighted by characters. */
    val bodySize: Float = run {
        val weights = HashMap<Int, Int>()
        for (p in pages) for (l in p.lines) {
            if (l.mono) continue
            val key = (l.size * 2).roundToInt()  // half-point buckets
            weights[key] = (weights[key] ?: 0) + l.text.length
        }
        (weights.maxByOrNull { it.value }?.key ?: 20) / 2f
    }

    /** Running headers/footers: short lines near the page edge that repeat (ignoring digits) on 3+ pages. */
    private val running: Set<String> = run {
        val counts = HashMap<String, Int>()
        for (p in pages) {
            edgeLines(p).map { signature(it.text) }.filter { it.isNotEmpty() }.toSet().forEach {
                counts[it] = (counts[it] ?: 0) + 1
            }
        }
        val min = maxOf(3, pages.size / 50)
        counts.filterValues { it >= min }.keys
    }

    private fun signature(text: String) = text.lowercase().filter { it.isLetter() }

    private fun edgeLines(p: RawPage): List<RawLine> {
        if (p.lines.isEmpty()) return emptyList()
        val sorted = p.lines.sortedBy { it.y }
        val band = p.height * 0.1f
        val top = sorted.take(2).filter { it.y <= band + it.size }
        val bottom = sorted.takeLast(2).filter { it.y >= p.height - band }
        // Running headers are set at or below body size; a big "Chapter 3" at the top is a heading.
        return (top + bottom).filter { it.text.length <= 100 && it.size <= bodySize + 0.5f }
    }

    fun blocks(p: RawPage): List<TextBlock> {
        val edges = edgeLines(p).toSet()
        val lines = p.lines
            .filterNot { it in edges && isRunning(it) }
            .map { it.copy(text = TextClean.line(it.text)) }
            .filter { it.text.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val body = lines.filter { !it.mono && abs(it.size - bodySize) < 1f }
        val left = (body.ifEmpty { lines }).minOf { it.x }
        val right = (body.ifEmpty { lines }).maxOf { it.xEnd }
        val spacing = lineSpacing(body.ifEmpty { lines })

        val out = mutableListOf<TextBlock>()
        val para = StringBuilder()
        val code = mutableListOf<RawLine>()
        var prev: RawLine? = null
        var prevKind: BlockKind? = null

        fun flushPara() {
            if (para.isNotEmpty()) out += TextBlock(BlockKind.PARAGRAPH, para.toString())
            para.setLength(0)
        }
        fun flushCode() {
            if (code.isNotEmpty()) {
                val minX = code.minOf { it.x }
                val text = code.joinToString("\n") { l ->
                    val indent = ((l.x - minX) / (l.size * 0.6f)).roundToInt().coerceIn(0, 40)
                    " ".repeat(indent) + l.text
                }
                out += TextBlock(BlockKind.CODE, text)
            }
            code.clear()
        }

        for (line in lines) {
            val gap = prev?.let { line.y - it.y } ?: Float.MAX_VALUE
            val level = headingLevel(line, gap, spacing)
            when {
                line.mono -> {
                    flushPara()
                    code += line
                    prevKind = BlockKind.CODE
                }
                level > 0 -> {
                    flushPara(); flushCode()
                    val last = out.lastOrNull()
                    val p0 = prev
                    val sameStyle = p0 != null && abs(p0.size - line.size) < 0.6f && p0.bold == line.bold
                    val chapterLabel = last != null && TextClean.CHAPTER_LABEL_ONLY.matches(last.text)
                    val continuesHeading = prevKind == BlockKind.HEADING && last != null && gap < line.size * 2.5f &&
                        (sameStyle || chapterLabel) && last.text.length + line.text.length <= 150 &&
                        !TextClean.endsSentence(last.text)
                    if (continuesHeading && last != null) {
                        // A heading set over two lines, or a "Chapter 3" label followed by its title.
                        val joined = if (chapterLabel) {
                            "${last.text}: ${line.text}"
                        } else {
                            "${last.text} ${line.text}"
                        }
                        out[out.lastIndex] = last.copy(text = joined, level = minOf(last.level, level))
                    } else {
                        out += TextBlock(BlockKind.HEADING, line.text, level)
                    }
                    prevKind = BlockKind.HEADING
                }
                else -> {
                    flushCode()
                    val p0 = prev
                    val newPara = prevKind != BlockKind.PARAGRAPH || p0 == null ||
                        gap > spacing * 1.6f || gap < -spacing ||  // big gap, or a jump back up (new column)
                        (line.x > left + line.size * 0.9f && p0.x <= left + line.size * 0.5f) ||  // indented first line
                        (p0.xEnd < right - line.size * 5f && TextClean.endsSentence(p0.text)) ||  // short last line
                        TextClean.isListItem(line.text)
                    if (newPara) flushPara()
                    TextClean.appendLine(para, line.text)
                    prevKind = BlockKind.PARAGRAPH
                }
            }
            prev = line
        }
        flushPara(); flushCode()
        return out
    }

    private fun isRunning(l: RawLine): Boolean =
        TextClean.isPageNumber(l.text) || l.size <= bodySize + 0.5f && signature(l.text).let { sig ->
            // Exact repeats, or a repeated header with something added ("Book Title · Part One").
            sig.isNotEmpty() && (sig in running || running.any { it.length >= 6 && sig.startsWith(it) })
        }

    /** 0 = body text, otherwise the heading level. */
    private fun headingLevel(line: RawLine, gapAbove: Float, spacing: Float): Int {
        val text = line.text
        if (text.length > 150 || line.mono) return 0
        if (text.first().let { !it.isLetterOrDigit() && it != '"' && it != '“' }) return 0  // bullets, symbols
        if (TextClean.CHAPTER_LABEL.matches(text) && text.length <= 80) return 1
        val ratio = line.size / bodySize
        if (ratio >= 1.8f) return 1
        if (ratio >= 1.4f) return 2
        if (ratio >= 1.18f && text.length <= 100) return 3
        if (line.bold && text.length <= 80 && gapAbove > spacing * 1.3f && !text.endsWith(",")) return 3
        return 0
    }

    private fun lineSpacing(lines: List<RawLine>): Float {
        val max = bodySize.coerceAtLeast(4f) * 3
        val deltas = lines.zipWithNext { a, b -> b.y - a.y }.filter { it > 0 && it < max }.sorted()
        return deltas.getOrNull(deltas.size / 2) ?: (bodySize * 1.25f)
    }

    /** Headings worth offering as reading boundaries. */
    fun headings(page: Int, blocks: List<TextBlock>): List<DetectedHeading> =
        blocks.filter { it.kind == BlockKind.HEADING && it.level <= 2 }
            .map { DetectedHeading(it.text, it.level, page) }
}
