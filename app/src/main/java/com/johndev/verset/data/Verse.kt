package com.johndev.verset.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single KJV verse. This table is populated once, on first launch, from the
 * bundled assets/kjv_verses.json file — never edited by the user.
 *
 * id is a stable composite-free key derived at load time: bookIndex*1_000_000 + chapter*1_000 + verse
 * so the same verse always gets the same id across app reinstalls (important for syncing tags).
 */
@Entity(tableName = "verses")
data class Verse(
    @PrimaryKey val id: Long,
    val bookIndex: Int,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String
)

@Entity(tableName = "books")
data class BookMeta(
    @PrimaryKey val bookIndex: Int,
    val name: String,
    val testament: String, // "OT" or "NT"
    val chapterCount: Int
)

@Entity(tableName = "reading_history")
data class ReadingHistoryEntry(
    @PrimaryKey val bookIndex: Int, // one row per book — re-viewing a chapter just bumps its timestamp/chapter, no duplicate spam
    val book: String,
    val chapter: Int,
    val viewedAt: Long
)

fun verseId(bookIndex: Int, chapter: Int, verse: Int): Long =
    bookIndex.toLong() * 1_000_000L + chapter.toLong() * 1_000L + verse.toLong()
