package com.someone1703.dailyread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.someone1703.dailyread.DailyReadApp
import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.BookRepository
import com.someone1703.dailyread.data.EVERY_DAY
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Keeps content at a comfortable width on tablets and in landscape. */
val MaxContentWidth = 640.dp

/** Rough reading pace, used only for the "about N min" estimates. */
private const val MINUTES_PER_PAGE = 2

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

/** The reminder on/off switch with its days and time, shared by the import and book screens. */
@Composable
fun ReminderCard(
    enabled: Boolean,
    days: Set<Int>,
    hour: Int,
    minute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onDaysChange: (Set<Int>) -> Unit,
    onPickTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (enabled) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (enabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Reading reminder", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (enabled) "Skipped on days you've already read" else "Off. You won't be nudged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled) {
            ReminderDayPicker(days, onDaysChange, Modifier.padding(horizontal = 16.dp))
            TextButton(
                onClick = onPickTime,
                modifier = Modifier.padding(start = 28.dp, bottom = 4.dp),
            ) {
                Text("${reminderLabel(days, hour, minute)} · Change")
            }
        }
    }
}

/** One toggle per weekday. The last chosen day can't be cleared: the switch is how to turn reminders off. */
@Composable
private fun ReminderDayPicker(days: Set<Int>, onDaysChange: (Set<Int>) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth()) {
        (0L..6L).map { DayOfWeek.SUNDAY.plus(it) }.forEach { day ->
            val selected = day.value in days
            // The larger target keeps each day easy to tap while the circle itself stays small.
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .toggleable(
                        value = selected,
                        enabled = !selected || days.size > 1,
                        role = Role.Checkbox,
                        onValueChange = { onDaysChange(if (it) days + day.value else days - day.value) },
                    )
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .background(if (selected) colors.primary else Color.Transparent, CircleShape)
                        .then(if (selected) Modifier else Modifier.border(1.dp, colors.outline, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) colors.onPrimary else colors.onSurface,
                    )
                }
            }
        }
    }
}

/** Ring showing how far through a book you are, with the percentage in the middle. */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val percent = (fraction * 100).toInt()
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = "$percent percent read" },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { fraction },
            modifier = Modifier.size(size),
            strokeWidth = 5.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
        )
        Text("$percent%", style = MaterialTheme.typography.labelMedium)
    }
}

/** Centered icon, headline and message, used for empty and error states. */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

@Composable
fun rememberRepository(): BookRepository =
    (LocalContext.current.applicationContext as DailyReadApp).repository

val Book.progress: Float
    get() = if (readings.isEmpty()) 0f else doneCount.toFloat() / readings.size

fun pagesLabel(start: Int, end: Int): String =
    if (start == end) "p. ${start + 1}" else "pp. ${start + 1}–${end + 1}"

fun pageCountLabel(count: Int): String = if (count == 1) "1 page" else "$count pages"

fun minutesLabel(pages: Int): String = "about ${pages * MINUTES_PER_PAGE} min"

fun timeLabel(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** "Every day", "Weekdays", "Weekends", or the days themselves, such as "Mon, Wed, Fri". */
fun reminderDaysLabel(days: Set<Int>): String = when (days) {
    EVERY_DAY -> "Every day"
    setOf(1, 2, 3, 4, 5) -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    // % 7 puts Sunday (7) first.
    else -> days.sortedBy { it % 7 }.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
}

fun reminderLabel(days: Set<Int>, hour: Int, minute: Int): String =
    "${reminderDaysLabel(days)} at ${timeLabel(hour, minute)}"

/** When the next reminder after today falls: "tomorrow", "Friday", or "next Tuesday" a full week away. */
fun nextReminderDayLabel(days: Set<Int>, today: LocalDate = LocalDate.now()): String {
    val gap = (1L..7L).firstOrNull { today.plusDays(it).dayOfWeek.value in days } ?: 1L
    val name = today.plusDays(gap).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return when (gap) {
        1L -> "tomorrow"
        7L -> "next $name"
        else -> name
    }
}
