package com.johndev.verset.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reading_history (
                bookIndex INTEGER NOT NULL PRIMARY KEY,
                book TEXT NOT NULL,
                chapter INTEGER NOT NULL,
                viewedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

// Backs a unique index on tags.name (see Tag.kt) so getOrCreateTag()'s check-then-insert
// can't create duplicate tags. Any tags that already share a name are merged first —
// their verse_tag_entries get repointed to the surviving (lowest-id) row — since a plain
// CREATE UNIQUE INDEX would otherwise fail on existing duplicate data.
private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE verse_tag_entries
            SET tagId = (
                SELECT MIN(t2.id) FROM tags t2
                WHERE t2.name = (SELECT t1.name FROM tags t1 WHERE t1.id = verse_tag_entries.tagId)
            )
            WHERE tagId IN (SELECT id FROM tags WHERE id NOT IN (SELECT MIN(id) FROM tags GROUP BY name))
            """.trimIndent()
        )
        db.execSQL("DELETE FROM tags WHERE id NOT IN (SELECT MIN(id) FROM tags GROUP BY name)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
    }
}

@Database(
    entities = [Verse::class, BookMeta::class, Tag::class, VerseTagEntry::class, ReadingHistoryEntry::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun verseDao(): VerseDao
    abstract fun bookDao(): BookDao
    abstract fun tagDao(): TagDao
    abstract fun entryDao(): VerseTagEntryDao
    abstract fun historyDao(): ReadingHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "verset.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
