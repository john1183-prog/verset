package com.johndev.verset.repository

import com.johndev.verset.data.*
import kotlinx.coroutines.flow.Flow

data class SearchResult(
    val verses: List<Verse>,
    val totalCount: Int,
    /** wordTyped -> wordCorrectedTo, only populated if the literal search found nothing */
    val correctedWords: Map<String, String> = emptyMap()
)

class BibleRepository(private val db: AppDatabase) {

    fun booksFlow(): Flow<List<BookMeta>> = db.bookDao().allBooks()
    fun chapterFlow(bookIndex: Int, chapter: Int): Flow<List<Verse>> =
        db.verseDao().versesInChapter(bookIndex, chapter)

    suspend fun chapterOnce(bookIndex: Int, chapter: Int): List<Verse> =
        db.verseDao().versesInChapterOnce(bookIndex, chapter)

    suspend fun getVerse(bookIndex: Int, chapter: Int, verse: Int): Verse? =
        db.verseDao().byId(verseId(bookIndex, chapter, verse))

    // ── Search ──────────────────────────────────────────────────────────────
    // Every word in the query must appear somewhere in the verse (multi-word
    // AND, any order — fixes the old behavior of only matching one exact
    // phrase). No artificial cap on the count; results themselves are capped
    // at [limit] for UI performance, with the true total shown separately.
    // If the literal search finds nothing, falls back to a typo-tolerant
    // "closest word in the KJV" correction per unmatched word.

    private var vocabularyCache: Set<String>? = null

    private suspend fun vocabulary(): Set<String> {
        vocabularyCache?.let { return it }
        val wordRegex = Regex("[A-Za-z']+")
        val words = HashSet<String>()
        for (text in db.verseDao().allVerseTexts()) {
            wordRegex.findAll(text).forEach { words.add(it.value.lowercase()) }
        }
        vocabularyCache = words
        return words
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1]
                else 1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
            }
        }
        return dp[a.length][b.length]
    }

    private suspend fun closestWord(word: String): String? {
        if (word.length < 3) return null
        val vocab = vocabulary()
        if (word in vocab) return null
        val maxDist = if (word.length <= 4) 1 else 2
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (candidate in vocab) {
            if (kotlin.math.abs(candidate.length - word.length) > maxDist) continue
            val d = levenshtein(word, candidate)
            if (d < bestDist) { bestDist = d; best = candidate }
            if (bestDist == 0) break
        }
        return if (bestDist in 1..maxDist) best else null
    }

    suspend fun searchVerses(query: String, bookIndex: Int? = null, limit: Int = 2000): SearchResult {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return SearchResult(emptyList(), 0)

        var searchWords = words
        var total = db.verseDao().countRaw(SearchQueryBuilder.build(searchWords, bookIndex, countOnly = true))
        var corrections: Map<String, String> = emptyMap()

        if (total == 0) {
            val correctionMap = words.mapNotNull { w -> closestWord(w)?.let { w to it } }.toMap()
            if (correctionMap.isNotEmpty()) {
                val correctedWords = words.map { correctionMap[it] ?: it }
                val correctedTotal = db.verseDao().countRaw(SearchQueryBuilder.build(correctedWords, bookIndex, countOnly = true))
                if (correctedTotal > 0) {
                    searchWords = correctedWords
                    total = correctedTotal
                    corrections = correctionMap
                }
            }
        }

        val verses = db.verseDao().searchRaw(SearchQueryBuilder.build(searchWords, bookIndex, countOnly = false, limit = limit))
        return SearchResult(verses, total, corrections)
    }

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
