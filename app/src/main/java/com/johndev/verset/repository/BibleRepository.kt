package com.johndev.verset.repository

import com.johndev.verset.data.*
import kotlinx.coroutines.flow.Flow

class BibleRepository(private val db: AppDatabase) {

    fun booksFlow(): Flow<List<BookMeta>> = db.bookDao().allBooks()
    fun chapterFlow(bookIndex: Int, chapter: Int): Flow<List<Verse>> =
        db.verseDao().versesInChapter(bookIndex, chapter)

    suspend fun chapterOnce(bookIndex: Int, chapter: Int): List<Verse> =
        db.verseDao().versesInChapterOnce(bookIndex, chapter)
    fun search(query: String): Flow<List<Verse>> = db.verseDao().search(query)
    suspend fun getVerse(bookIndex: Int, chapter: Int, verse: Int): Verse? =
        db.verseDao().byId(verseId(bookIndex, chapter, verse))

    fun tagsFlow(): Flow<List<Tag>> = db.tagDao().allTags()
    fun entriesForTag(tagId: Long): Flow<List<VerseTagEntry>> = db.entryDao().entriesForTag(tagId)
    fun entriesForVerse(verseId: Long): Flow<List<VerseTagEntry>> = db.entryDao().entriesForVerse(verseId)
    fun taggedVerseIds(): Flow<List<Long>> = db.entryDao().taggedVerseIds()
    fun allEntriesFlow(): Flow<List<VerseTagEntry>> = db.entryDao().allEntriesFlow()

    fun historyFlow(): Flow<List<ReadingHistoryEntry>> = db.historyDao().recentFlow()
    suspend fun recordChapterView(bookIndex: Int, book: String, chapter: Int) {
        db.historyDao().recordView(ReadingHistoryEntry(bookIndex, book, chapter, System.currentTimeMillis()))
    }
    suspend fun clearHistory() = db.historyDao().clearAll()

    suspend fun getOrCreateTag(name: String, colorHex: String = "#4A6FA5"): Tag {
        val trimmed = name.trim()
        db.tagDao().byName(trimmed)?.let { return it }
        val id = db.tagDao().insert(Tag(name = trimmed, colorHex = colorHex))
        return db.tagDao().byName(trimmed) ?: Tag(id = id, name = trimmed, colorHex = colorHex)
    }

    suspend fun deleteTag(tag: Tag) = db.tagDao().delete(tag)

    suspend fun updateTag(tag: Tag) = db.tagDao().update(tag)

    suspend fun saveEntry(verse: Verse, tagId: Long, note: String) {
        db.entryDao().insert(
            VerseTagEntry(
                verseId = verse.id,
                book = verse.book,
                chapter = verse.chapter,
                verse = verse.verse,
                verseText = verse.text,
                tagId = tagId,
                note = note,
                dirty = true
            )
        )
    }

    suspend fun updateEntry(entry: VerseTagEntry) =
        db.entryDao().update(entry.copy(updatedAt = System.currentTimeMillis(), dirty = true))

    suspend fun deleteEntry(entry: VerseTagEntry) = db.entryDao().delete(entry)
}
