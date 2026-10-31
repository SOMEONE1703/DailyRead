# DailyRead: project notes

Android app that splits a PDF I own (starting with *The Pragmatic Programmer*, 20th Anniversary
Edition, DRM-free PDF) into short daily readings, with a daily reminder, an in-app reader and
support for multiple books.

## Decisions
- **Stack:** Kotlin + Jetpack Compose (Material 3) on the Android Studio template: AGP 9.5 with
  built-in Kotlin 2.2, version catalog in `gradle/libs.versions.toml`. minSdk raised 24 → 26 for
  `java.time` and notification channels.
- **Content:** only the user's own PDF, on-device. Never bundle or reproduce book text. The app
  stores the plan (titles, page ranges) and a private copy of the PDF in app storage.
- **Splitting:** by PDF bookmarks via `pdfbox-android` at a chosen depth (Chapters / Sections /
  Sub-sections), or fixed N pages per day as a fallback. A parent bookmark directly followed by its
  child is folded into the child; bookmarks sharing a page share that page.
- **Rendering:** Android's `PdfRenderer` (serialised with a Mutex, LRU bitmap cache).
- **Persistence:** one JSON file (`filesDir/library.json`) via kotlinx.serialization, written
  atomically. No Room unless the data model grows.
- **Reminders:** one `AlarmManager` alarm per book, re-armed each time it fires. Exact if allowed,
  else a 10-minute window. Skips the notification if something was already read today. Re-armed on
  boot, app update and time/timezone changes. Tapping the notification opens the next reading.
- **Progress:** "next reading" = first not-done reading, so missed days roll forward. Streak =
  consecutive days with at least one reading done, across all books.

## Status
- **Builds:** `assembleDebug` and `testDebugUnitTest` pass (2026-10-31) with Android Studio's JBR as
  `JAVA_HOME`. No version bumps or code changes were needed; the catalog resolved as written.
- Not yet run on a device or emulator.
- Bookmark-splitting logic was checked against a sample outline; no unit tests yet.

## Layout (`app/src/main/java/com/someone1703/dailyread/`)
```
DailyReadApp.kt     Application: inits PDFBox, repository, notification channel
MainActivity.kt     Nav graph: library, import/{uri}, book/{id}, reader/{id}/{index}; notification deep link
data/               Models.kt (Book, Reading, streak), BookRepository.kt (JSON store, StateFlow)
pdf/                PdfOutline.kt (bookmarks -> readings), PdfPageRenderer.kt
reminders/          ReminderScheduler.kt, ReminderReceiver.kt (+ RescheduleReceiver), Notifications.kt
ui/                 LibraryScreen, ImportScreen + ImportViewModel, BookScreen, ReaderScreen, Common
ui/theme/           Template theme (Color, Theme, Type)
```

## Running it
- Android 13+: allow notifications when asked.
- Android 14+: for on-the-minute reminders allow *Settings → Apps → DailyRead → Alarms & reminders*;
  otherwise they arrive within about 10 minutes.
- Some phones (Xiaomi, Huawei, Samsung deep sleep) kill alarms; exclude the app from battery optimisation.

## Not built yet
- Unit tests for `PdfOutline` and `readingStreak`.
- Pinch-to-zoom in the reader (currently a button cycling 1x / 1.5x / 2x).
- Per-day "what to look for" note and reflection question.
- Remembering scroll position within a reading.
- EPUB import.
