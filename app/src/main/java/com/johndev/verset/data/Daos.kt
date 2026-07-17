package com.johndev.verset.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VerseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(verses: List<Verse>)

    @Query("SELECT COUNT(*) FROM verses")
    suspend fun count(): Int

    @Query("SELECT * FROM verses WHERE bookIndex = :bookIndex AND chapter = :chapter ORDER BY verse ASC")
    fun versesInChapter(bookIndex: Int, chapter: Int): Flow<List<Verse>>

    @Query("SELECT * FROM verses WHERE bookIndex = :bookIndex AND chapter = :chapter ORDER BY verse ASC")
    suspend fun versesInChapterOnce(bookIndex: Int, chapter: Int): List<Verse>

    @Query("SELECT * FROM verses WHERE text LIKE '%' || :query || '%' LIMIT 200")
    fun search(query: String): Flow<List<Verse>>

    @Query("SELECT * FROM verses WHERE id = :id")
    suspend fun byId(id: Long): Verse?
}

@Dao
interface BookDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(books: List<BookMeta>)

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books ORDER BY bookIndex ASC")
    fun allBooks(): Flow<List<BookMeta>>
}

@Dao
interface ReadingHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordView(entry: ReadingHistoryEntry)

    @Query("SELECT * FROM reading_history ORDER BY viewedAt DESC LIMIT :limit")
    fun recentFlow(limit: Int = 20): Flow<List<ReadingHistoryEntry>>

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()
}

@Dao
interface TagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun allTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Tag?

    @Update
    suspend fun update(tag: Tag)

    @Delete
    suspend fun delete(tag: Tag)
}

@Dao
interface VerseTagEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VerseTagEntry): Long

    @Update
    suspend fun update(entry: VerseTagEntry)

    @Delete
    suspend fun delete(entry: VerseTagEntry)

    @Query("SELECT * FROM verse_tag_entries WHERE tagId = :tagId ORDER BY createdAt DESC")
    fun entriesForTag(tagId: Long): Flow<List<VerseTagEntry>>

    @Query("SELECT * FROM verse_tag_entries WHERE verseId = :verseId")
    fun entriesForVerse(verseId: Long): Flow<List<VerseTagEntry>>

    @Query("SELECT DISTINCT verseId FROM verse_tag_entries")
    fun taggedVerseIds(): Flow<List<Long>>

    @Query("SELECT * FROM verse_tag_entries WHERE dirty = 1")
    suspend fun dirtyEntries(): List<VerseTagEntry>

    @Query("SELECT * FROM verse_tag_entries")
    suspend fun allEntriesOnce(): List<VerseTagEntry>

    @Query("SELECT * FROM verse_tag_entries")
    fun allEntriesFlow(): Flow<List<VerseTagEntry>>
}
