package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE documentId = :docId ORDER BY pageIndex ASC")
    fun getBookmarksForDocument(docId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentId = :docId AND pageIndex = :pageIndex")
    suspend fun removeBookmarkAtPage(docId: Long, pageIndex: Int)

    @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE documentId = :docId AND pageIndex = :pageIndex")
    suspend fun isPageBookmarked(docId: Long, pageIndex: Int): Boolean
}
