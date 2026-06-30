package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OCR에서 인식된 텍스트와 실제 장비 ID를 매핑하는 학습 데이터
 */
@Entity(tableName = "equipment_alias")
data class EquipmentAlias(
    @PrimaryKey
    val rawText: String,    // OCR로 인식된 원본 텍스트
    val equipmentId: Long   // 매핑할 실제 장비 ID
)
