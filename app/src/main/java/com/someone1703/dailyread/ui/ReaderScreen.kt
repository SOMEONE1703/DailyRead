package com.someone1703.dailyread.ui

import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.readingStreak
import com.someone1703.dailyread.pdf.PdfPageRenderer
import com.someone1703.dailyread.reminders.Notifications
import com.someone1703.dailyread.reminders.ReminderScheduler
import com.someone1703.dailyread.text.BlockKind
import com.someone1703.dailyread.text.PendingPages
import com.someone1703.dailyread.text.ReadBlock
import com.someone1703.dailyread.text.ReaderItem
import com.someone1703.dailyread.text.ReadingText
import com.someone1703.dailyread.text.TextPrep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ZOOM_LEVELS = listOf(1f, 1.5f, 2f)
private val TEXT_SCALES = listOf(0.9f, 1f, 1.15f, 1.3f)
private val TOP_BAR_CLEARANCE = 72.dp
private val BOTTOM_BAR_CLEARANCE = 72.dp

/** The text of a reading, or why there isn't any yet. */
private sealed interface ReadingContent {
    data object Loading : ReadingContent
    /** The PdfBox pass hasn't finished, so no page has text yet. */
    data object Preparing : ReadingContent
    data class Ready(val items: List<ReaderItem>) : ReadingContent
}

private fun readerPrefs(context: Context) = context.getSharedPreferences("reader", Context.MODE_PRIVATE)

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
    val textStore = rememberTextStore()
    val books by repo.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    val reading = book?.readings?.getOrNull(index)

    LaunchedEffect(reading == null) { if (reading == null) onBack() }
    if (book == null || reading == null) return

    var pdfRestores by remember { mutableIntStateOf(0) }
    val hasPdf = remember(bookId, pdfRestores) { repo.fileFor(book).exists() }

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
            if (restored) {
                pdfRestores++
                TextPrep.start(context, book.id, book.fileName, book.title)
            }
        }
    }

    // Text, reloaded whenever more of this book's text is prepared (e.g. a scanned page recognized).
    val versions by textStore.versions.collectAsStateWithLifecycle()
    val version = versions[bookId] ?: 0
    val next = book.readings.getOrNull(index + 1)
    val content by produceState<ReadingContent>(ReadingContent.Loading, bookId, reading, next, version) {
        value = withContext(Dispatchers.IO) {
            val meta = textStore.meta(bookId)
            if (meta?.extracted != true) {
                ReadingContent.Preparing
            } else {
                val scanned = meta.scannedPages.toSet()
                ReadingContent.Ready(
                    ReadingText.assemble(
                        reading = reading,
                        next = next,
                        load = { textStore.page(bookId, it) },
                        isPending = { it in scanned && !textStore.hasPage(bookId, it) },
                    )
                )
            }
        }
    }
    val prep by textStore.progress.collectAsStateWithLifecycle()

    // Scanned pages of this reading jump the queue.
    val pendingHere = (content as? ReadingContent.Ready)?.items?.filterIsInstance<PendingPages>()
        ?.flatMap { it.pages }.orEmpty()
    LaunchedEffect(pendingHere) {
        if (pendingHere.isNotEmpty()) TextPrep.requestPages(context, book, pendingHere)
    }

    val prefs = remember { readerPrefs(context) }
    var textScale by remember { mutableFloatStateOf(prefs.getFloat("text_scale", 1f)) }
    var showOriginal by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var showBars by remember { mutableStateOf(true) }
    var showCompleted by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val textState = rememberLazyListState()
    val pageState = rememberLazyListState()
    val listState = if (showOriginal) pageState else textState

    val pages = remember(reading.startPage, reading.endPage) { (reading.startPage..reading.endPage).toList() }
    val items = (content as? ReadingContent.Ready)?.items.orEmpty()
    val currentPage by remember(pages, items, showOriginal) {
        derivedStateOf {
            if (showOriginal) {
                pages.getOrElse(listState.firstVisibleItemIndex) { reading.endPage }
            } else {
                items.getOrNull(listState.firstVisibleItemIndex)?.page ?: reading.startPage
            }
        }
    }
    val atEnd by remember(listState) { derivedStateOf { !listState.canScrollForward } }
    val itemCount = if (showOriginal) pages.size else items.size
    val progress = when {
        atEnd -> 1f
        itemCount == 0 -> 0f
        else -> listState.firstVisibleItemIndex.toFloat() / itemCount
    }

    // The text gets the whole screen while you read; the controls return at the end, or on a tap.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress && listState.lastScrolledForward }
            .collect { if (it) showBars = false }
    }
    LaunchedEffect(listState) {
        snapshotFlow { !listState.canScrollForward }
            .collect { if (it) showBars = true }
    }

    val background = if (showOriginal) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface
    // A Surface, so text picks up the theme's on-surface color in light and dark.
    Surface(color = background, modifier = Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize()) {
        val systemTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val padding = PaddingValues(
            top = systemTop + TOP_BAR_CLEARANCE,
            bottom = systemBottom + BOTTOM_BAR_CLEARANCE + 8.dp,
        )
        val tapToToggle = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { showBars = !showBars } }
            .semantics {
                onClick(label = if (showBars) "Hide controls" else "Show controls") {
                    showBars = !showBars
                    true
                }
            }

        when {
            !hasPdf -> MessageState(
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
            showOriginal -> OriginalPages(book, pdfRestores, pages, zoom, pageState, padding, tapToToggle)
            else -> when (val c = content) {
                ReadingContent.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ReadingContent.Preparing -> MessageState(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "Preparing the text",
                    message = prep[bookId]?.let { "Reading page ${it.done} of ${it.total}. You can read the original pages meanwhile." }
                        ?: "This book's text is being prepared. You can read the original pages meanwhile.",
                ) {
                    Button(onClick = { showOriginal = true }) { Text("Show original pages") }
                }
                is ReadingContent.Ready -> if (c.items.isEmpty()) {
                    MessageState(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = "No text here",
                        message = "These pages don't seem to have any text. They may be pictures or diagrams.",
                    ) {
                        Button(onClick = { showOriginal = true }) { Text("Show original pages") }
                    }
                } else {
                    Box(tapToToggle) {
                        LazyColumn(
                            Modifier.fillMaxSize().wrapContentWidth().widthIn(max = MaxContentWidth),
                            state = textState,
                            contentPadding = PaddingValues(
                                start = 24.dp, end = 24.dp,
                                top = padding.calculateTopPadding() + 8.dp,
                                bottom = padding.calculateBottomPadding() + 16.dp,
                            ),
                        ) {
                            items(c.items, key = { itemKey(it) }) { item ->
                                when (item) {
                                    is ReadBlock -> TextBlockView(item, textScale)
                                    is PendingPages -> PendingPagesCard(item, onShowOriginal = { showOriginal = true })
                                }
                            }
                        }
                    }
                }
            }
        }

        // A solid strip keeps the status bar readable over the text once the top bar hides.
        Spacer(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.surface)
        )

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AnimatedVisibility(
                visible = showBars || !hasPdf,
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
                                "Day ${index + 1} of ${book.readings.size} · page ${currentPage + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                    },
                    actions = {
                        if (hasPdf && showOriginal) {
                            TextButton(onClick = {
                                zoom = ZOOM_LEVELS[(ZOOM_LEVELS.indexOf(zoom) + 1) % ZOOM_LEVELS.size]
                            }) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom", modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text(if (zoom % 1f == 0f) "${zoom.toInt()}x" else "${zoom}x")
                            }
                        } else if (hasPdf) {
                            IconButton(onClick = {
                                textScale = TEXT_SCALES[(TEXT_SCALES.indexOf(textScale).coerceAtLeast(0) + 1) % TEXT_SCALES.size]
                                prefs.edit().putFloat("text_scale", textScale).apply()
                            }) {
                                Icon(Icons.Filled.FormatSize, contentDescription = "Text size")
                            }
                        }
                        if (hasPdf) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (showOriginal) "Show text" else "Show original pages") },
                                        leadingIcon = {
                                            Icon(
                                                if (showOriginal) Icons.AutoMirrored.Filled.Article else Icons.Filled.PictureAsPdf,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showOriginal = !showOriginal
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
            if (!showBars && hasPdf) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }
            if (hasPdf) {
                LinearProgressIndicator(
                    progress = { progress },
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
                            repo.book(bookId)?.let {
                                ReminderScheduler.schedule(context, it)
                                // The next readings move up: recognize their scanned pages now.
                                TextPrep.refresh(context, bookId, notify = false)
                            }
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
    } }

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

private fun itemKey(item: ReaderItem): String = when (item) {
    is ReadBlock -> "b${item.index}"
    is PendingPages -> "p${item.pages.first()}"
}

/** One heading, paragraph or code listing, set like a book page. */
@Composable
private fun TextBlockView(block: ReadBlock, scale: Float) {
    val type = MaterialTheme.typography
    when (block.kind) {
        BlockKind.HEADING -> {
            val style = when (block.level) {
                1 -> type.headlineSmall
                2 -> type.titleLarge
                else -> type.titleMedium.copy(fontFamily = FontFamily.Serif)
            }
            Text(
                block.text,
                style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
                modifier = Modifier
                    .padding(top = if (block.index == 0) 8.dp else 28.dp, bottom = 12.dp)
                    .semantics { heading() },
            )
        }
        BlockKind.PARAGRAPH -> Text(
            block.text,
            style = type.bodyLarge.copy(
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp * scale,
                lineHeight = 1.6.em,
            ),
            modifier = Modifier.padding(bottom = 14.dp),
        )
        BlockKind.CODE -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        ) {
            Text(
                block.text,
                style = type.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp * scale, lineHeight = 1.4.em),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}

@Composable
private fun PendingPagesCard(item: PendingPages, onShowOriginal: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                val first = item.pages.first() + 1
                val last = item.pages.last() + 1
                Text(
                    if (first == last) "Recognizing the text of page $first" else "Recognizing the text of pages $first–$last",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "This page is a scan. Its text appears here when it's ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onShowOriginal, contentPadding = PaddingValues(0.dp)) { Text("Show original pages") }
            }
        }
    }
}

/** The rendered PDF pages, for figures, tables or when the text isn't ready. */
@Composable
private fun OriginalPages(
    book: Book,
    restores: Int,
    pages: List<Int>,
    zoom: Float,
    state: LazyListState,
    padding: PaddingValues,
    modifier: Modifier,
) {
    val repo = rememberRepository()
    val renderer = remember(book.id, restores) { runCatching { PdfPageRenderer(repo.fileFor(book)) }.getOrNull() }
    DisposableEffect(renderer) { onDispose { renderer?.close() } }
    if (renderer == null) {
        MessageState(Icons.Filled.ErrorOutline, "Couldn't open the PDF", "The original pages can't be shown right now.")
        return
    }
    BoxWithConstraints(modifier) {
        val pageWidth = maxWidth * zoom
        val pageWidthPx = with(LocalDensity.current) { pageWidth.roundToPx() }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = state,
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pages, key = { it }) { page ->
                PdfPage(renderer, page, pageWidthPx, Modifier.width(pageWidth), zoom > 1f)
            }
        }
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
