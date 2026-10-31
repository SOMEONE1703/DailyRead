package com.someone1703.dailyread.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.someone1703.dailyread.data.Book
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One alarm per book, re-armed for the next day each time it fires.
 * Uses an exact alarm when the user has allowed it, otherwise a 10-minute window.
 */
object ReminderScheduler {
    private const val ACTION_REMIND = "com.someone1703.dailyread.action.REMIND"

    fun schedule(context: Context, book: Book) {
        cancel(context, book.id)
        if (!book.reminderEnabled || book.isFinished) return

        val alarms = context.getSystemService(AlarmManager::class.java)
        val triggerAt = nextTrigger(book.reminderHour, book.reminderMinute)
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

    /** Today at hh:mm if that's still ahead, otherwise tomorrow. */
    fun nextTrigger(hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
        val now = LocalDateTime.now(zone)
        var at = LocalDateTime.of(LocalDate.now(zone), LocalTime.of(hour, minute))
        if (!at.isAfter(now.plusSeconds(5))) at = at.plusDays(1)
        return at.atZone(zone).toInstant().toEpochMilli()
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
