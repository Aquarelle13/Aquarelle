package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 특정 날짜/교대조의 확정된 장비 가동 상태 데이터
 */
@Entity(tableName = "shift_data")
data class ShiftData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,    // "05.05"
    val shift: String,   // "주간", "야간"
    val team: String,    // "A", "B", "C", "D"
    val equipmentStates: Map<Long, Boolean> // 장비 ID -> 가동 여부
)
