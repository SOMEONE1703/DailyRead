package com.someone1703.dailyread.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.someone1703.dailyread.DailyReadApp

/** Fires at the book's reminder time: notify about the next reading, then arm the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as DailyReadApp).repository
        val bookId = intent.getStringExtra(Notifications.EXTRA_BOOK_ID) ?: return
        val book = repo.book(bookId) ?: return

        val next = book.nextIndex
        // Skip the nudge if today's reading is already done, but keep the next alarm.
        if (next != null && !book.readToday()) {
            Notifications.showReadingReminder(context, book, next)
        }
        ReminderScheduler.schedule(context, book)
    }
}

/** Alarms are wiped on reboot, app update and clock changes, so re-arm them all. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as DailyReadApp).repository
        ReminderScheduler.rescheduleAll(context, repo.books.value)
    }
}
