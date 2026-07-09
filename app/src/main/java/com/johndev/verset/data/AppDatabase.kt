package com.johndev.verset.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                    // Fine while the app is still pre-release/testing-only. Once this
                    // ships to real users, replace with a proper Migration so their
                    // tagged verses aren't wiped on a schema change.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
