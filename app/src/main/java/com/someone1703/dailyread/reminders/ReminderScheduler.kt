package com.someone1703.dailyread.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.someone1703.dailyread.data.Book
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * One alarm per book, re-armed for its next chosen day each time it fires.
 * Uses an exact alarm when the user has allowed it, otherwise a 10-minute window.
 */
object ReminderScheduler {
    private const val ACTION_REMIND = "com.someone1703.dailyread.action.REMIND"

    fun schedule(context: Context, book: Book) {
        cancel(context, book.id)
        if (!book.reminderEnabled || book.isFinished || book.reminderDays.isEmpty()) return

        val alarms = context.getSystemService(AlarmManager::class.java)
        val triggerAt = nextTrigger(book.reminderHour, book.reminderMinute, book.reminderDays)
        val pi = pendingIntent(context, book.id)

        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 10 * 60 * 1000L, pi)
        }
    }

    fun cancel(context: Context, bookId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, bookId))
    }

    fun rescheduleAll(context: Context, books: List<Book>) = books.forEach { schedule(context, it) }

    /** The first hh:mm still ahead that falls on one of [days]. [days] must not be empty. */
    fun nextTrigger(hour: Int, minute: Int, days: Set<Int>, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val nowLocal = now.toLocalDateTime()
        var at = LocalDateTime.of(nowLocal.toLocalDate(), LocalTime.of(hour, minute))
        if (!at.isAfter(nowLocal.plusSeconds(5))) at = at.plusDays(1)
        while (at.dayOfWeek.value !in days) at = at.plusDays(1)
        return at.atZone(now.zone).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, bookId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(Notifications.EXTRA_BOOK_ID, bookId)
        }
        return PendingIntent.getBroadcast(
            context,
            bookId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
