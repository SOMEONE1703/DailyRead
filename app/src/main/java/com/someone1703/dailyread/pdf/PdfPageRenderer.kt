package com.someone1703.dailyread.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Renders PDF pages to bitmaps with Android's built-in PdfRenderer.
 * PdfRenderer allows only one open page at a time, so access is serialised with a mutex.
 */
class PdfPageRenderer(file: File) : Closeable {

    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val pageCount: Int get() = renderer.pageCount

    /** Width/height ratio of a page, used to reserve layout space before the bitmap is ready. */
    suspend fun aspectRatio(index: Int): Float = mutex.withLock {
        withContext(Dispatchers.IO) {
            renderer.openPage(index).use { it.width.toFloat() / it.height.toFloat() }
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap {
        val width = widthPx.coerceIn(200, 2400)
        val key = "$index@$width"
        cache.get(key)?.let { return it }
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                renderer.openPage(index).use { page ->
                    val height = (width.toFloat() * page.height / page.width).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }.also { cache.put(key, it) }
    }

    override fun close() {
        cache.evictAll()
        renderer.close()
        fd.close()
    }
}
