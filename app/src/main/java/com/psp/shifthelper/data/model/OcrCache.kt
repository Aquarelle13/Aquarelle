package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ocr_cache")
data class OcrCache(
    @PrimaryKey
    val imageHash: String,
    val ocrResultJson: String,
    val timestamp: Long = System.currentTimeMillis()
)
