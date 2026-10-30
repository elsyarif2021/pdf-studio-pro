package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String = "application/pdf",
    val size: Long = 0,
    val pageCount: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long? = null,
    val lastReadPage: Int = 0,
    val isFavorite: Boolean = false,
    val collectionId: Long? = null,
    val isScanned: Boolean = false
)
