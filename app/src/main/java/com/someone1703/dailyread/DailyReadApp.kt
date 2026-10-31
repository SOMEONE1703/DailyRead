package com.someone1703.dailyread

import android.app.Application
import com.someone1703.dailyread.data.BookRepository
import com.someone1703.dailyread.reminders.Notifications
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class DailyReadApp : Application() {
    lateinit var repository: BookRepository
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        repository = BookRepository(this)
        Notifications.createChannel(this)
    }
}
