package com.someone1703.dailyread.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.readingStreak
import com.someone1703.dailyread.pdf.PdfPageRenderer
import com.someone1703.dailyread.reminders.Notifications
import com.someone1703.dailyread.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f)
private val TOP_BAR_CLEARANCE = 72.dp
private val BOTTOM_BAR_CLEARANCE = 72.dp

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

    var pdfRestores by remember { mutableIntStateOf(0) }
    val renderer = remember(bookId, pdfRestores) {
        runCatching { PdfPageRenderer(repo.fileFor(book)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }

    val scope = rememberCoroutineScope()
    var reimportFailed by remember { mutableStateOf(false) }
    val reimportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val restored = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { repo.restorePdf(book, it) } == true
                }.getOrDefault(false)
            }
            reimportFailed = !restored
            if (restored) pdfRestores++
        }
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var showBars by remember { mutableStateOf(true) }
    var showCompleted by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val pages = remember(reading.startPage, reading.endPage) { (reading.startPage..reading.endPage).toList() }
    val currentPage by remember(pages) {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.size) }
    }
    val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
    val pageProgress = if (atEnd) 1f else (currentPage - 1).toFloat() / pages.size

    // The page gets the whole screen while you read; the controls return at the end, or on a tap.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && listState.lastScrolledForward }
            .collect { if (it) showBars = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }
            .collect { if (it) showBars = true }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (renderer == null) {
            MessageState(
                icon = Icons.Filled.ErrorOutline,
                title = "Couldn't find this book",
                message = if (reimportFailed) {
                    "That file doesn't match ${book.title}. Please choose the same PDF you imported before."
                } else {
                    "Couldn't find the PDF for ${book.title}. Please reimport it to keep reading. Your progress is saved."
                },
            ) {
                Button(onClick = { reimportPicker.launch(arrayOf("application/pdf")) }) { Text("Reimport PDF") }
                TextButton(onClick = onBack) { Text("Go back") }
            }
        } else {
            val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { showBars = !showBars } }
                    .semantics {
                        onClick(label = if (showBars) "Hide controls" else "Show controls") {
                            showBars = !showBars
                            true
                        }
                    },
            ) {
                val pageWidth = maxWidth * zoom
                val pageWidthPx = with(LocalDensity.current) { pageWidth.roundToPx() }

                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        top = systemTop + TOP_BAR_CLEARANCE,
                        bottom = systemBottom + BOTTOM_BAR_CLEARANCE + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pages, key = { it }) { page ->
                        PdfPage(renderer, page, pageWidthPx, Modifier.width(pageWidth), zoom > 1f)
                    }
                }
            }
        }

        // A solid strip keeps the status bar readable over the page once the top bar hides.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.surface)
        )

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AnimatedVisibility(
                visible = showBars || renderer == null,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                reading.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Day ${index + 1} of ${book.readings.size} · page $currentPage of ${pages.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        TextButton(onClick = {
                            zoom = ZOOM_LEVELS[(ZOOM_LEVELS.indexOf(zoom) + 1) % ZOOM_LEVELS.size]
                        }) {
                            Icon(
                                Icons.Filled.ZoomIn,
                                contentDescription = "Zoom",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(if (zoom % 1f == 0f) "${zoom.toInt()}x" else "${zoom}x")
                        }
                    },
                )
            }
            if (!showBars && renderer != null) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }
            if (renderer != null) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }

        AnimatedVisibility(
            visible = showBars,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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
                        val label = if (book.readToday()) "Mark as done" else "Done for today"
                        Button(onClick = {
                            repo.setDone(bookId, index, true)
                            Notifications.cancel(context, bookId)
                            repo.book(bookId)?.let { ReminderScheduler.schedule(context, it) }
                            showCompleted = true
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(label)
                        }
                    }
                    IconButton(onClick = { onOpen(bookId, index + 1) }, enabled = index < book.readings.lastIndex) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next reading")
                    }
                }
            }
        }
    }

    if (showCompleted) {
        CompletedSheet(
            book = book,
            index = index,
            streak = readingStreak(books),
            onDismiss = { showCompleted = false },
            onHome = onBack,
            onNext = { onOpen(bookId, index + 1) },
        )
    }
}

/** The small reward for finishing, and the choice of what to do next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompletedSheet(
    book: Book,
    index: Int,
    streak: Int,
    onDismiss: () -> Unit,
    onHome: () -> Unit,
    onNext: () -> Unit,
) {
    val hasNext = index < book.readings.lastIndex
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (book.isFinished) "You finished the book" else "Day ${index + 1} done",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            if (streak > 0) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("$streak-day streak", style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (book.reminderEnabled && !book.isFinished) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "See you ${nextReminderDayLabel(book.reminderDays)} at ${timeLabel(book.reminderHour, book.reminderMinute)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onHome, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Back to my books") }
            if (hasNext && !book.isFinished) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Keep reading")
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
            Box(
                pageModifier.aspectRatio(0.72f).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(32.dp))
            }
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
