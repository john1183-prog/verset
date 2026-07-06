package com.johndev.verset.data

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray

/**
 * Parses the bundled assets/kjv_verses.json and assets/kjv_books.json into Room, once.
 * Uses org.json (built into Android, no extra dependency) to keep the app lightweight.
 * Runs off the main thread — call from a coroutine.
 *
 * The whole import runs inside a single Room transaction (db.withTransaction), so if
 * the process is killed partway through, nothing is committed — the next launch
 * retries the full import instead of silently ending up with a half-populated Bible.
 * Completion is tracked via Prefs.bibleLoaded, set only after the transaction commits.
 */
object BibleLoader {

    suspend fun loadIfNeeded(context: Context, db: AppDatabase, prefs: Prefs) {
        if (prefs.bibleLoaded) {
            BibleLoadState.setLoading(false)
            return
        }

        BibleLoadState.setLoading(true)
        try {
            db.withTransaction {
                val booksJson = context.assets.open("kjv_books.json").bufferedReader().use { it.readText() }
                val booksArray = JSONArray(booksJson)
                val books = ArrayList<BookMeta>(booksArray.length())
                for (i in 0 until booksArray.length()) {
                    val o = booksArray.getJSONObject(i)
                    books.add(
                        BookMeta(
                            bookIndex = o.getInt("index"),
                            name = o.getString("name"),
                            testament = o.getString("testament"),
                            chapterCount = o.getInt("chapters")
                        )
                    )
                }
                db.bookDao().insertAll(books)

                val versesJson = context.assets.open("kjv_verses.json").bufferedReader().use { it.readText() }
                val versesArray = JSONArray(versesJson)
                val batch = ArrayList<Verse>(2000)
                for (i in 0 until versesArray.length()) {
                    val o = versesArray.getJSONObject(i)
                    val bookIndex = o.getInt("bookIndex")
                    val chapter = o.getInt("chapter")
                    val verseNum = o.getInt("verse")
                    batch.add(
                        Verse(
                            id = verseId(bookIndex, chapter, verseNum),
                            bookIndex = bookIndex,
                            book = o.getString("book"),
                            chapter = chapter,
                            verse = verseNum,
                            text = o.getString("text")
                        )
                    )
                    if (batch.size >= 2000) {
                        db.verseDao().insertAll(batch)
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) db.verseDao().insertAll(batch)
            }
            // Only reached if the transaction above committed successfully.
            prefs.bibleLoaded = true
        } finally {
            BibleLoadState.setLoading(false)
        }
    }
}
