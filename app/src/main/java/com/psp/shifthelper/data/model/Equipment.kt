package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipment")
data class Equipment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val code: String,
    val name: String,
    val isRunning: Boolean,
    val currentWorkerId: Long? = null,
    val isSpecial: Boolean = false,
    val isManualCheck: Boolean = false, // 스케줄 별도 확인 장비
    val avoidConsecutive: Boolean = false, // 동일 직원 연속배정 피하기
    val isReference: Boolean = false,   // 세트 장비의 기준 여부
    val referenceEquipId: Long? = null, // 기준 장비 ID (세트 설정 시)
    val displayOrder: Int = 0,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val width: Float = 75f,            // 편집 모드용 크기
    val height: Float = 42f            // 편집 모드용 크기
)