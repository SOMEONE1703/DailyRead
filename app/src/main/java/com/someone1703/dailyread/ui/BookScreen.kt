package com.someone1703.dailyread.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    LaunchedEffect(book == null) { if (book == null) onBack() }
    if (book == null) return

    fun reschedule() = repo.book(bookId)?.let { ReminderScheduler.schedule(context, it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete book") }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    val total = book.readings.size
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else book.doneCount.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${book.doneCount} of $total readings done",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Daily reminder", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        if (book.reminderEnabled) {
                            OutlinedButton(onClick = { showTime = true }) {
                                Text(timeLabel(book.reminderHour, book.reminderMinute))
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Switch(
                            checked = book.reminderEnabled,
                            onCheckedChange = {
                                repo.setReminder(bookId, it, book.reminderHour, book.reminderMinute)
                                reschedule()
                            },
                        )
                    }
                }
                HorizontalDivider()
            }

            itemsIndexed(book.readings) { i, r ->
                val isNext = i == book.nextIndex
                Row(
                    Modifier.fillMaxWidth().clickable { onRead(bookId, i) }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = r.isDone,
                        onCheckedChange = { done ->
                            repo.setDone(bookId, i, done)
                            reschedule()
                        },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Day ${i + 1} · ${r.title}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isNext) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (r.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        val meta = listOfNotNull(r.subtitle, "${r.pageCount}p", "up next".takeIf { isNext })
                        Text(
                            meta.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
                TextButton(onClick = {
                    ReminderScheduler.cancel(context, bookId)
                    Notifications.cancel(context, bookId)
                    repo.delete(bookId)
                    confirmDelete = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
