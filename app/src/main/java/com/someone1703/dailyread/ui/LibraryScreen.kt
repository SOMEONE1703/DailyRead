package com.someone1703.dailyread.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.readingDays
import com.someone1703.dailyread.data.readingStreak
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onImport: (Uri) -> Unit,
    onOpenBook: (String) -> Unit,
    onRead: (String, Int) -> Unit,
) {
    val repo = rememberRepository()
    val books by repo.books.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }
    val pickPdf = { picker.launch(arrayOf("application/pdf")) }

    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Daily Read", style = MaterialTheme.typography.headlineSmall) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (books.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = pickPdf,
                    expanded = atTop,
                    icon = { Icon(Icons.Filled.Add, contentDescription = "Add PDF") },
                    text = { Text("Add PDF") },
                )
            }
        },
    ) { padding ->
        if (books.isEmpty()) {
            MessageState(
                icon = Icons.AutoMirrored.Filled.MenuBook,
                title = "Read a little every day",
                message = "Add a PDF you own and it'll be split into short daily readings, with a reminder each day.",
                modifier = Modifier.padding(padding),
            ) {
                Button(onClick = pickPdf) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Add your first PDF")
                }
            }
        } else {
            val todayBook = books.firstOrNull { !it.isFinished && !it.readToday() }
            LazyColumn(
                modifier = Modifier.fillMaxSize().wrapContentWidth().widthIn(max = MaxContentWidth),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "today") {
                    if (todayBook != null) {
                        TodayCard(todayBook, onRead = { i -> onRead(todayBook.id, i) })
                    } else {
                        AllDoneCard(allFinished = books.all { it.isFinished })
                    }
                }
                item(key = "week") { WeekCard(books) }
                item(key = "header") {
                    Text(
                        "Your books",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .semantics { heading() },
                    )
                }
                items(books, key = { it.id }) { book ->
                    BookRow(book, onOpen = { onOpenBook(book.id) }, onRead = { i -> onRead(book.id, i) })
                }
            }
        }
    }
}

/** The one thing to do today, front and center. */
@Composable
private fun TodayCard(book: Book, onRead: (Int) -> Unit) {
    val next = book.nextIndex ?: return
    val reading = book.readings[next]
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "TODAY · DAY ${next + 1} OF ${book.readings.size}",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                reading.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                book.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onRead(next) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Start reading · ${minutesLabel(reading.pageCount)}")
            }
        }
    }
}

@Composable
private fun AllDoneCard(allFinished: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (allFinished) "Every book finished" else "Done for today",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    if (allFinished) "Add another PDF to keep going." else "Nice work. See you tomorrow.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Streak plus the last seven days, each marked with a check as well as color. */
@Composable
private fun WeekCard(books: List<Book>) {
    val streak = readingStreak(books)
    val days = readingDays(books)
    val today = LocalDate.now()
    val week = (6L downTo 0L).map { today.minusDays(it) }
    val readThisWeek = week.count { it in days }

    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (streak > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (streak > 0) "$streak-day streak" else "Start a streak today",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = "Read on $readThisWeek of the last 7 days" },
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                week.forEach { day -> DayMark(day, read = day in days, isToday = day == today) }
            }
        }
    }
}

@Composable
private fun DayMark(day: LocalDate, read: Boolean, isToday: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(32.dp)
                .background(
                    if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                )
                .then(
                    if (isToday && !read) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (read) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BookRow(book: Book, onOpen: () -> Unit, onRead: (Int) -> Unit) {
    val next = book.nextIndex
    OutlinedCard(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(book.progress)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${book.doneCount} of ${book.readings.size} readings" +
                            if (book.reminderEnabled && !book.isFinished) {
                                " · ${reminderLabel(book.reminderDays, book.reminderHour, book.reminderMinute)}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (next == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Finished", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val reading = book.readings[next]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (book.readToday()) "Read today · up next" else "Up next",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            reading.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    FilledTonalButton(onClick = { onRead(next) }) {
                        Text("Read")
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                }
            }
        }
    }
}
