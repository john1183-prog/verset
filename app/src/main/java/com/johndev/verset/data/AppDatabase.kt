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

@Database(
    entities = [Verse::class, BookMeta::class, Tag::class, VerseTagEntry::class, ReadingHistoryEntry::class],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
