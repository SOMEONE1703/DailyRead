package com.someone1703.dailyread.text

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.Writer

/**
 * Reads the text layer of a PDF with PdfBox, a chunk of pages at a time. The document is reopened
 * for every chunk so the parsed pages of earlier chunks can be freed, which keeps memory flat on
 * large books. Only light line records are kept between chunks.
 */
object PdfTextExtractor {

    const val CHUNK_PAGES = 20

    /** Pages with fewer visible characters than this, and an image on them, count as scanned. */
    private const val MIN_TEXT_CHARS = 25

    fun pageCount(file: File): Int =
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }

    /** Reads pages [from, to) and returns their raw lines. */
    fun readChunk(file: File, from: Int, to: Int): List<RawPage> =
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = LineStripper(from)
            stripper.startPage = from + 1  // PdfBox counts from 1
            stripper.endPage = to
            stripper.writeText(doc, NullWriter)
            stripper.pages
        }

    fun isScanned(page: RawPage): Boolean = page.hasImages && page.charCount < MIN_TEXT_CHARS

    private class LineStripper(private val firstPage: Int) : PDFTextStripper() {
        val pages = mutableListOf<RawPage>()

        private var lines = mutableListOf<RawLine>()
        private val text = StringBuilder()
        private val positions = mutableListOf<TextPosition>()
        private var width = 0f
        private var height = 0f
        private var hasImages = false

        override fun startPage(page: PDPage) {
            lines = mutableListOf()
            text.setLength(0)
            positions.clear()
            width = page.cropBox?.width ?: page.mediaBox.width
            height = page.cropBox?.height ?: page.mediaBox.height
            hasImages = try {
                val res = page.resources
                res?.xObjectNames?.iterator()?.hasNext() == true
            } catch (e: Exception) {
                false
            }
            super.startPage(page)
        }

        override fun writeString(string: String, textPositions: MutableList<TextPosition>) {
            text.append(string)
            positions.addAll(textPositions)
        }

        override fun writeWordSeparator() {
            text.append(' ')
        }

        override fun writeLineSeparator() = flushLine()

        override fun writeParagraphEnd() {
            flushLine()
            super.writeParagraphEnd()
        }

        override fun endPage(page: PDPage) {
            flushLine()
            pages += RawPage(
                page = firstPage + pages.size,
                lines = lines,
                width = width,
                height = height,
                hasImages = hasImages,
            )
            super.endPage(page)
        }

        private fun flushLine() {
            val str = text.toString()
            if (str.isNotBlank() && positions.isNotEmpty()) {
                val visible = positions.filter { it.unicode?.isNotBlank() == true }.ifEmpty { positions }
                val first = visible.first()
                val last = visible.last()
                val sizes = visible.map { p -> p.fontSizeInPt.takeIf { it > 0f } ?: p.heightDir }.sorted()
                val bold = visible.count { isBold(it) } >= visible.size * 0.8f
                val mono = visible.count { isMono(it) } >= visible.size * 0.8f
                lines += RawLine(
                    text = str,
                    x = first.xDirAdj,
                    xEnd = last.xDirAdj + last.widthDirAdj,
                    y = first.yDirAdj,
                    size = sizes[sizes.size / 2],
                    bold = bold,
                    mono = mono,
                )
            }
            text.setLength(0)
            positions.clear()
        }

        private fun isBold(p: TextPosition): Boolean {
            val font = p.font ?: return false
            val name = font.name?.lowercase().orEmpty()
            if (BOLD_NAMES.any { it in name }) return true
            val d = font.fontDescriptor ?: return false
            return d.isForceBold || d.fontWeight >= 600f
        }

        private fun isMono(p: TextPosition): Boolean {
            val font = p.font ?: return false
            val name = font.name?.lowercase().orEmpty()
            if (MONO_NAMES.any { it in name }) return true
            return font.fontDescriptor?.isFixedPitch == true
        }
    }

    private val BOLD_NAMES = listOf("bold", "black", "heavy", "semibold", "demi")
    private val MONO_NAMES = listOf("mono", "courier", "consol", "menlo", "inconsolata", "code", "typewriter")

    private object NullWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) = Unit
        override fun flush() = Unit
        override fun close() = Unit
    }
}
