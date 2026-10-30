package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.ScanPageEntity
import com.example.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Update
    suspend fun updateSession(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    suspend fun getActiveSession(): ScanSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: ScanPageEntity): Long

    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    fun getPagesForSession(sessionId: Long): Flow<List<ScanPageEntity>>

    @Query("SELECT * FROM scan_pages WHERE sessionId = :sessionId ORDER BY pageIndex ASC")
    suspend fun getPagesListForSession(sessionId: Long): List<ScanPageEntity>

    @Query("DELETE FROM scan_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    @Query("DELETE FROM scan_pages WHERE sessionId = :sessionId")
    suspend fun deleteAllPagesForSession(sessionId: Long)

    @Update
    suspend fun updatePage(page: ScanPageEntity)
}
