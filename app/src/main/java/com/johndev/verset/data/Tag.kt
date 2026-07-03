package com.johndev.verset.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-defined classification, e.g. "Promise", "Comfort", "Warning".
 * Created on the fly from the tag picker when it doesn't already exist.
 */
@Entity(tableName = "tags")
data class Tag @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val colorHex: String = "#4A6FA5",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Links one verse to one tag, with an optional note.
 * A verse can have many entries (one per tag it's classified under), matching
 * the "multiple tags per verse" requirement — e.g. Genesis 22:8 can be both
 * "Promise" AND "Comfort", each with its own note.
 */
@Entity(
    tableName = "verse_tag_entries",
    foreignKeys = [
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("tagId"), Index("verseId")]
)
data class VerseTagEntry @JvmOverloads constructor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val verseId: Long = 0,
    val book: String = "",
    val chapter: Int = 0,
    val verse: Int = 0,
    val verseText: String = "",
    val tagId: Long = 0,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Firestore sync bookkeeping
    val remoteId: String? = null,
    val dirty: Boolean = true // true until successfully synced
)
