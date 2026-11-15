package com.someone1703.dailyread.text

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Text recognition for image-only pages: renders the page with PdfRenderer and runs ML Kit's
 * on-device Latin recognizer. Line heights stand in for font sizes when spotting headings.
 */
class OcrEngine(file: File) : Closeable {

    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(page: Int): PageText {
        val bitmap = render(page)
        val height = bitmap.height
        val result = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } finally {
            bitmap.recycle()
        }
        return PageText(page, blocksFrom(result, height), TextSource.OCR)
    }

    /** About 200 dpi for a typical book page, which ML Kit reads well without huge bitmaps. */
    private fun render(index: Int): Bitmap = renderer.openPage(index).use { page ->
        val scale = (2000f / maxOf(page.width, page.height)).coerceIn(1f, 4f)
        val w = (page.width * scale).toInt()
        val h = (page.height * scale).toInt()
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.WHITE)
            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    override fun close() {
        recognizer.close()
        renderer.close()
        fd.close()
    }

    companion object {
        fun blocksFrom(text: Text, pageHeight: Int): List<TextBlock> {
            val all = text.textBlocks.filter { it.text.isNotBlank() }
            val heights = all.flatMap { b -> b.lines.mapNotNull { it.boundingBox?.height() } }.sorted()
            val median = heights.getOrNull(heights.size / 2)?.toFloat() ?: return emptyList()
            val margin = pageHeight * 0.06f

            val out = mutableListOf<TextBlock>()
            for (block in all) {
                val lines = block.lines.map { TextClean.line(it.text) }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) continue
                val box = block.boundingBox
                val joined = lines.joinToString(" ")
                // Page numbers and running headers sit in the top and bottom margins.
                val atEdge = box != null && (box.bottom < margin || box.top > pageHeight - margin)
                if (TextClean.isPageNumber(joined) || (atEdge && lines.size == 1 && joined.length < 80)) continue

                val blockHeights = block.lines.mapNotNull { it.boundingBox?.height() }
                val avg = if (blockHeights.isEmpty()) median else blockHeights.average().toFloat()
                val ratio = avg / median
                val isHeading = lines.size <= 3 && joined.length <= 120 &&
                    (ratio >= 1.25f || TextClean.CHAPTER_LABEL.matches(joined))
                if (isHeading) {
                    val level = when {
                        TextClean.CHAPTER_LABEL.matches(joined) || ratio >= 1.8f -> 1
                        ratio >= 1.25f -> 2
                        else -> 3
                    }
                    out += TextBlock(BlockKind.HEADING, joined, level)
                } else {
                    val sb = StringBuilder()
                    lines.forEach { TextClean.appendLine(sb, it) }
                    val text = sb.toString()
                    val prev = out.lastOrNull()
                    // ML Kit sometimes splits a paragraph into blocks: rejoin an unfinished one.
                    if (prev?.kind == BlockKind.PARAGRAPH && TextClean.continues(prev.text, text)) {
                        out[out.lastIndex] = prev.copy(text = TextClean.joinParagraphs(prev.text, text))
                    } else {
                        out += TextBlock(BlockKind.PARAGRAPH, text)
                    }
                }
            }
            return out
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
