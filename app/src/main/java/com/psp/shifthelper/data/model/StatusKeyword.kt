package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "status_keywords")
data class StatusKeyword(
    @PrimaryKey
    val rawText: String,
    val isRunning: Boolean,
    val isLocked: Boolean = false // 사용자가 직접 등록한 키워드는 자동 학습으로 덮어쓰지 않도록 보호
)
