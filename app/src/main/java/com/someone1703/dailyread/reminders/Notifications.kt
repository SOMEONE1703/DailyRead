package com.someone1703.dailyread.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.someone1703.dailyread.MainActivity
import com.someone1703.dailyread.R
import com.someone1703.dailyread.data.Book

object Notifications {
    const val CHANNEL_ID = "daily_reading"
    const val PREP_CHANNEL_ID = "book_prep"
    const val READY_CHANNEL_ID = "book_ready"
    const val EXTRA_BOOK_ID = "book_id"
    const val EXTRA_READING_INDEX = "reading_index"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val prep = NotificationChannel(
            PREP_CHANNEL_ID,
            context.getString(R.string.prep_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val ready = NotificationChannel(
            READY_CHANNEL_ID,
            context.getString(R.string.ready_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(channel, prep, ready))
    }

    fun prepNotificationId(bookId: String) = "prep:$bookId".hashCode()

    /** The ongoing notification shown while a book's text is prepared. */
    fun prepProgress(context: Context, bookId: String, title: String, text: String, done: Int, total: Int) =
        NotificationCompat.Builder(context, PREP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Preparing $title")
            .setContentText(text)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openBook(context, bookId))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()

    fun showBookReady(context: Context, book: Book, scannedLeft: Int) {
        if (!canPost(context)) return
        val text = if (scannedLeft > 0) {
            "Your first readings are ready. The other $scannedLeft scanned pages will be recognized while charging."
        } else {
            "${book.readings.size} readings are ready to start."
        }
        val notification = NotificationCompat.Builder(context, READY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${book.title} is ready")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openBook(context, book.id))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(prepNotificationId(book.id) + 1, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    private fun openBook(context: Context, bookId: String): PendingIntent {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_BOOK_ID, bookId)
        }
        return PendingIntent.getActivity(
            context,
            prepNotificationId(bookId),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showReadingReminder(context: Context, book: Book, index: Int) {
        if (!canPost(context)) return
        val reading = book.readings[index]
        val remaining = book.readings.size - book.doneCount

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_BOOK_ID, book.id)
            putExtra(EXTRA_READING_INDEX, index)
        }
        val pending = PendingIntent.getActivity(
            context,
            book.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pages = if (reading.pageCount == 1) "1 page" else "${reading.pageCount} pages"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Today: ${reading.title}")
            .setContentText("${book.title} · $pages · $remaining left")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(book.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    fun cancel(context: Context, bookId: String) {
        NotificationManagerCompat.from(context).cancel(bookId.hashCode())
    }
}
