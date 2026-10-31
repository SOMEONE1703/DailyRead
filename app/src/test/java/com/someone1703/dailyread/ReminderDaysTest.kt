package com.someone1703.dailyread

import com.someone1703.dailyread.data.Book
import com.someone1703.dailyread.data.EVERY_DAY
import com.someone1703.dailyread.reminders.ReminderScheduler
import com.someone1703.dailyread.ui.nextReminderDayLabel
import com.someone1703.dailyread.ui.reminderDaysLabel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderDaysTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private val json = Json { ignoreUnknownKeys = true }

    /** 2026-11-02 is a Monday. */
    private fun monday(hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, 2, hour, minute, 0, 0, zone)

    private fun trigger(hour: Int, days: Set<Int>, now: ZonedDateTime) =
        ReminderScheduler.nextTrigger(hour, 0, days, now)

    private fun expected(day: Int, hour: Int) =
        ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun everyDay_firesTodayIfStillAhead() {
        assertEquals(expected(2, 9), trigger(9, EVERY_DAY, monday(8)))
    }

    @Test
    fun everyDay_rollsToTomorrowOncePassed() {
        assertEquals(expected(3, 9), trigger(9, EVERY_DAY, monday(10)))
    }

    @Test
    fun chosenDay_laterToday_firesToday() {
        assertEquals(expected(2, 9), trigger(9, setOf(1, 4), monday(8)))
    }

    @Test
    fun skipsDaysNotChosen() {
        assertEquals(expected(5, 9), trigger(9, setOf(1, 4), monday(10)))
    }

    @Test
    fun todayNotChosen_movesToNextChosenDay() {
        assertEquals(expected(7, 9), trigger(9, setOf(6), monday(8)))
    }

    @Test
    fun onlyDayIsToday_andPassed_waitsAWeek() {
        assertEquals(expected(9, 9), trigger(9, setOf(1), monday(10)))
    }

    @Test
    fun oldSavedBook_loadsAsEveryDay() {
        val saved = """[{"id":"a","title":"T","fileName":"f.pdf","pageCount":10,"readings":[],
            "reminderEnabled":true,"reminderHour":6,"reminderMinute":30,"createdAt":1}]"""
        val book = json.decodeFromString<List<Book>>(saved).single()
        assertEquals(EVERY_DAY, book.reminderDays)
        assertEquals(6, book.reminderHour)
    }

    @Test
    fun chosenDays_surviveSaving() {
        val book = Book("a", "T", "f.pdf", 10, emptyList(), reminderDays = setOf(1, 3, 5), createdAt = 1)
        assertEquals(book, json.decodeFromString<Book>(json.encodeToString(book)))
    }

    @Test
    fun daysLabel() {
        assertEquals("Every day", reminderDaysLabel(EVERY_DAY))
        assertEquals("Weekdays", reminderDaysLabel(setOf(5, 4, 3, 2, 1)))
        assertEquals("Weekends", reminderDaysLabel(setOf(6, 7)))
        assertEquals("Mon, Wed, Fri", reminderDaysLabel(setOf(5, 1, 3)))
        assertEquals("Sun, Mon, Sat", reminderDaysLabel(setOf(1, 6, 7)))
    }

    @Test
    fun nextDayLabel() {
        val mon = LocalDate.of(2026, 11, 2)
        assertEquals("tomorrow", nextReminderDayLabel(EVERY_DAY, mon))
        assertEquals("Friday", nextReminderDayLabel(setOf(1, 5), mon))
        assertEquals("next Monday", nextReminderDayLabel(setOf(1), mon))
    }
}
