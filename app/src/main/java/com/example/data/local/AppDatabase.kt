package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.BookmarkDao
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.ScanDao
import com.example.data.local.entity.BookmarkEntity
import com.example.data.local.entity.CollectionEntity
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.ScanPageEntity
import com.example.data.local.entity.ScanSessionEntity

@Database(
    entities = [
        DocumentEntity::class,
        BookmarkEntity::class,
        ScanSessionEntity::class,
        ScanPageEntity::class,
        CollectionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun scanDao(): ScanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pdf_studio_pro.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
