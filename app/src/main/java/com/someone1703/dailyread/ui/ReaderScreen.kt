package com.someone1703.dailyread.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.someone1703.dailyread.pdf.PdfPageRenderer
import com.someone1703.dailyread.reminders.Notifications
import com.someone1703.dailyread.reminders.ReminderScheduler

private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: String,
    index: Int,
    onBack: () -> Unit,
    onOpen: (String, Int) -> Unit,
) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val books by repo.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    val reading = book?.readings?.getOrNull(index)

    LaunchedEffect(reading == null) { if (reading == null) onBack() }
    if (book == null || reading == null) return

    val renderer = remember(bookId) {
        runCatching { PdfPageRenderer(repo.fileFor(book)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }

    var zoom by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(reading.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Day ${index + 1} of ${book.readings.size} · ${pagesLabel(reading.startPage, reading.endPage)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        zoom = ZOOM_LEVELS[(ZOOM_LEVELS.indexOf(zoom) + 1) % ZOOM_LEVELS.size]
                    }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom (${zoom}x)")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { onOpen(bookId, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous reading")
                    }
                    if (reading.isDone) {
                        OutlinedButton(onClick = {
                            repo.setDone(bookId, index, false)
                            repo.book(bookId)?.let { ReminderScheduler.schedule(context, it) }
                        }) { Text("Mark as unread") }
                    } else {
                        Button(onClick = {
                            repo.setDone(bookId, index, true)
                            Notifications.cancel(context, bookId)
                            repo.book(bookId)?.let { ReminderScheduler.schedule(context, it) }
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Text("  Done for today")
                        }
                    }
                    IconButton(onClick = { onOpen(bookId, index + 1) }, enabled = index < book.readings.lastIndex) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next reading")
                    }
                }
            }
        },
    ) { padding ->
        if (renderer == null) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text("The PDF for this book is missing or can't be opened.")
            }
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val viewportWidth = maxWidth
            val pageWidth = viewportWidth * zoom
            val pageWidthPx = with(LocalDensity.current) { pageWidth.roundToPx() }

            LazyColumn(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items((reading.startPage..reading.endPage).toList(), key = { it }) { page ->
                    PdfPage(renderer, page, pageWidthPx, Modifier.width(pageWidth), zoom > 1f)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfPageRenderer,
    page: Int,
    widthPx: Int,
    pageModifier: Modifier,
    zoomed: Boolean,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, page, widthPx) {
        value = runCatching { renderer.render(page, widthPx) }.getOrNull()
    }
    val hScroll = rememberScrollState()
    val rowModifier = if (zoomed) Modifier.fillMaxWidth().horizontalScroll(hScroll) else Modifier.fillMaxWidth()
    Row(rowModifier) {
        val bmp = bitmap
        if (bmp == null) {
            Box(pageModifier.aspectRatio(0.72f).background(Color.White))
        } else {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = pageModifier,
            )
        }
    }
}
