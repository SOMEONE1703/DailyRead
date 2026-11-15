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
- **Text reader (2026-11-15):** the reader shows extracted text, not rendered pages. PdfBox-Android
  reads the text layer in chunks of 20 pages (document reopened per chunk to keep memory flat);
  pages with an image and under 25 characters of text count as scanned and go to ML Kit's bundled
  on-device recognizer. Cleaned text (hyphenation fixed, ligatures, running headers and page numbers
  removed, paragraphs joined across pages) is cached as heading/paragraph/code blocks per page in
  `filesDir/text/<bookId>/`. Blocks are the unit text to speech will step through later.
- **Preparation:** a WorkManager job (`TextPrepWorker`, foreground with a progress notification)
  starts as soon as a PDF is picked. The import screen shows progress and how many pages are images
  with a time estimate; if it isn't done, "Continue in background" saves the book and the library
  shows it as "Preparing…" until its next 2 readings have text, then a "ready" notification fires.
  Scanned pages of the next readings are recognized straight away (`OcrWorker`), the rest only
  while charging; a reading opened with unrecognized pages jumps the queue.
- **Titles:** bookmarks first. Without them, headings found by font size/bold and "Chapter N"/"Part"
  patterns (OCR: line height) become the plan. Book title from metadata, else file name. Readings
  can be renamed or merged with the next; a renamed reading keeps its original heading as `anchor`
  so shared pages are still cut at the right heading.
- **Cloud OCR** (DeepSeek-OCR) was considered and set aside.
- **APK:** only `arm64-v8a` and `x86_64` are packaged, since ML Kit's native library is ~11 MB per ABI.
- **Backup:** cloud backup keeps `library.json` but excludes `books/` and `text/` (PDFs could exceed the 25 MB
  quota and stop the whole backup). A restored book has no PDF until reimported from the reader,
  which accepts only a file with the same page count and keeps the plan and progress.
- **Rendering:** Android's `PdfRenderer` (serialised with a Mutex, LRU bitmap cache), now only for
  the reader's "Show original pages" option (figures, tables) and for rendering pages to recognize.
- **Persistence:** one JSON file (`filesDir/library.json`) via kotlinx.serialization, written
  atomically. No Room unless the data model grows.
- **Reminders:** one `AlarmManager` alarm per book, re-armed each time it fires for the book's next
  chosen weekday (`reminderDays`, ISO numbers, every day by default; at least one stays selected).
  Exact if allowed, else a 10-minute window. Skips the notification if something was already read
  today. Re-armed on boot, app update and time/timezone changes. Tapping the notification opens the
  next reading.
- **Progress:** "next reading" = first not-done reading, so missed days roll forward. Streak =
  consecutive days with at least one reading done, across all books.

- **Look:** warm paper and ink-green palette with amber for the streak, light and dark, in
  `ui/theme/`. Dynamic color is off on purpose. System serif for titles, sans for everything else.
- **Home:** one "Today" card with the next reading, then a 7-day streak strip, then the books.
- **Reader:** serif text at a size cycled from the top bar (saved), code in a monospace box,
  scanned pages still being recognized shown as a card. Controls hide on scroll down and return on tap or at the end. Finishing a reading
  opens a sheet with the streak and what to do next.
- **Time estimates** assume 2 minutes per page (`MINUTES_PER_PAGE` in `ui/Common.kt`).

## Status
- **Builds:** `assembleDebug` and `testDebugUnitTest` pass (2026-10-31) with Android Studio's JBR as
  `JAVA_HOME`. No version bumps or code changes were needed; the catalog resolved as written.
- Runs on the `Pixel_9_Pro` emulator: home, book, reader and the done sheet checked. The import
  screen and hide-on-scroll haven't been exercised since the redesign.
- Bookmark-splitting logic was checked against a sample outline; no unit tests yet.

## Layout (`app/src/main/java/com/someone1703/dailyread/`)
```
DailyReadApp.kt     Application: inits PDFBox, repository, notification channel
MainActivity.kt     Nav graph: library, import/{uri}, book/{id}, reader/{id}/{index}; notification deep link
text/               TextPrep (scheduling), Workers, PdfTextExtractor, PageLayout, OcrEngine,
                    TextStore (per-page cache), ReadingText (blocks of one reading), TextClean
data/               Models.kt (Book, Reading, streak), BookRepository.kt (JSON store, StateFlow)
pdf/                PdfOutline.kt (bookmarks -> readings), PdfPageRenderer.kt
reminders/          ReminderScheduler.kt, ReminderReceiver.kt (+ RescheduleReceiver), Notifications.kt
ui/                 LibraryScreen, ImportScreen + ImportViewModel, BookScreen, ReaderScreen, Common
ui/theme/           App theme (Color, Theme, Type)
```

## Running it
- Android 13+: allow notifications when asked.
- Android 14+: for on-the-minute reminders allow *Settings → Apps → DailyRead → Alarms & reminders*;
  otherwise they arrive within about 10 minutes.
- Some phones (Xiaomi, Huawei, Samsung deep sleep) kill alarms; exclude the app from battery optimisation.

- **Text reader:** `testDebugUnitTest` passes, including `TextLayoutTest`. Checked on the emulator
  with generated test PDFs: bookmarked book with scanned pages and a code listing, a book without
  bookmarks (chapters detected), a 148-page book whose first chapter is scanned ("Continue in
  background", Preparing…, progress and ready notifications), an existing book prepared on upgrade,
  and rename/merge. Not yet tried on a real book or a physical phone.

## Not built yet
- Text to speech over the reader's blocks.
- Unit tests for `PdfOutline` and `readingStreak`.
- Pinch-to-zoom in "Show original pages" (currently a button cycling 1x / 1.5x / 2x).
- Per-day "what to look for" note and reflection question.
- Remembering scroll position within a reading.
- EPUB import.
