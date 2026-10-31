package com.someone1703.dailyread.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.someone1703.dailyread.DailyReadApp
import com.someone1703.dailyread.data.BookRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun rememberRepository(): BookRepository =
    (LocalContext.current.applicationContext as DailyReadApp).repository

fun pagesLabel(start: Int, end: Int): String =
    if (start == end) "p. ${start + 1}" else "pp. ${start + 1}–${end + 1}"

fun timeLabel(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
