package com.someone1703.dailyread.ui

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.Reading
import com.someone1703.dailyread.reminders.Notifications
import com.someone1703.dailyread.reminders.ReminderScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(bookId: String, onBack: () -> Unit, onRead: (String, Int) -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val books by repo.books.collectAsStateWithLifecycle()
    val book = books.firstOrNull { it.id == bookId }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(book == null) { if (book == null) onBack() }
    if (book == null) return

    fun reschedule() = repo.book(bookId)?.let { ReminderScheduler.schedule(context, it) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete book", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    showMenu = false
                                    confirmDelete = true
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().wrapContentWidth().widthIn(max = MaxContentWidth),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "summary") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BookSummary(book, onRead = { i -> onRead(bookId, i) })
                    ReminderCard(
                        enabled = book.reminderEnabled,
                        days = book.reminderDays,
                        hour = book.reminderHour,
                        minute = book.reminderMinute,
                        onEnabledChange = {
                            repo.setReminder(bookId, it, book.reminderHour, book.reminderMinute)
                            reschedule()
                        },
                        onDaysChange = {
                            repo.setReminderDays(bookId, it)
                            reschedule()
                        },
                        onPickTime = { showTime = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Reading plan",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
            }

            itemsIndexed(book.readings, key = { i, _ -> i }) { i, r ->
                ReadingRow(
                    day = i + 1,
                    reading = r,
                    isNext = i == book.nextIndex,
                    onOpen = { onRead(bookId, i) },
                    onDoneChange = { done ->
                        repo.setDone(bookId, i, done)
                        reschedule()
                    },
                )
            }
        }
    }

    if (showTime) {
        ReminderTimeDialog(
            hour = book.reminderHour,
            minute = book.reminderMinute,
            onDismiss = { showTime = false },
            onConfirm = { h, m ->
                repo.setReminder(bookId, true, h, m)
                reschedule()
                showTime = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this book?") },
            text = { Text("Removes the plan, your progress and the app's copy of the PDF. Your original file isn't touched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ReminderScheduler.cancel(context, bookId)
                        Notifications.cancel(context, bookId)
                        repo.delete(bookId)
                        confirmDelete = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BookSummary(book: Book, onRead: (Int) -> Unit) {
    val total = book.readings.size
    val next = book.nextIndex
    val pagesLeft = book.readings.filterNot { it.isDone }.sumOf { it.pageCount }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(book.progress, size = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text("${book.doneCount} of $total readings", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (book.isFinished) "All finished" else "${pageCountLabel(pagesLeft)} to go",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (next != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onRead(next) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Continue with day ${next + 1}")
            }
        }
    }
}

@Composable
private fun ReadingRow(
    day: Int,
    reading: Reading,
    isNext: Boolean,
    onOpen: () -> Unit,
    onDoneChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (isNext) colors.secondaryContainer else colors.surface)
            .clickable(onClick = onOpen)
            .padding(end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The larger target keeps the marker easy to tap while the circle itself stays small.
        Box(
            Modifier
                .size(56.dp)
                .toggleable(value = reading.isDone, role = Role.Checkbox, onValueChange = onDoneChange)
                .semantics { contentDescription = "Day $day done" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(if (reading.isDone) colors.primary else colors.surface, CircleShape)
                    .then(
                        if (reading.isDone) Modifier
                        else Modifier.border(1.5.dp, if (isNext) colors.primary else colors.outline, CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (reading.isDone) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text("$day", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
                }
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                reading.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                color = if (reading.isDone) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (reading.isDone) TextDecoration.LineThrough else null,
            )
            val meta = listOfNotNull("Up next".takeIf { isNext }, reading.subtitle, pageCountLabel(reading.pageCount))
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
