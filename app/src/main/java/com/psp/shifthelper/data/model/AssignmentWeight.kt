package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignment_weight")
data class AssignmentWeight(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workerId: Long,
    val equipmentId: Long,
    val weight: Float = 1.0f,          // 학습된 가중치 (수동 수정 시 변동)
    val cumulativeCount: Int = 0,      // 누적 가동 횟수
    val lastAssignedDate: String? = null // 최근 배정일
)
