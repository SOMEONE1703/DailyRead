package com.someone1703.dailyread.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** All seven days, as ISO numbers (1 = Monday ... 7 = Sunday). */
val EVERY_DAY: Set<Int> = (1..7).toSet()

/** One day's reading: a contiguous page range of the PDF (0-based, inclusive). */
@Serializable
data class Reading(
    val title: String,
    val subtitle: String? = null,
    val startPage: Int,
    val endPage: Int,
    val completedAt: Long? = null,
) {
    val pageCount: Int get() = endPage - startPage + 1
    val isDone: Boolean get() = completedAt != null
}

@Serializable
data class Book(
    val id: String,
    val title: String,
    /** File name inside the app's private books/ folder. */
    val fileName: String,
    val pageCount: Int,
    val readings: List<Reading>,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 7,
    val reminderMinute: Int = 0,
    /** Days the reminder fires. Books saved before this field existed load as every day. */
    val reminderDays: Set<Int> = EVERY_DAY,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** The first reading not yet done. Missed days simply roll forward, nothing piles up. */
    val nextIndex: Int? get() = readings.indexOfFirst { !it.isDone }.takeIf { it >= 0 }
    val doneCount: Int get() = readings.count { it.isDone }
    val isFinished: Boolean get() = readings.isNotEmpty() && readings.all { it.isDone }

    fun readToday(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val today = LocalDate.now(zone)
        return readings.any { r -> r.completedAt?.let { it.toLocalDate(zone) == today } == true }
    }
}

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** Every day with at least one reading done, across all books. */
fun readingDays(books: List<Book>, zone: ZoneId = ZoneId.systemDefault()): Set<LocalDate> =
    books.flatMap { b -> b.readings.mapNotNull { it.completedAt?.toLocalDate(zone) } }.toSet()

/** Consecutive days (ending today, or yesterday if nothing read yet today) with at least one reading done. */
fun readingStreak(books: List<Book>, zone: ZoneId = ZoneId.systemDefault()): Int {
    val days = readingDays(books, zone)
    var day = LocalDate.now(zone)
    if (day !in days) day = day.minusDays(1)
    var streak = 0
    while (day in days) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}
