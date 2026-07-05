package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 특정 장비 영역의 좌표 정보
 */
data class EquipmentRegion(
    val equipmentId: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * 스케줄표의 장비 리스트 순서 및 좌표 정보를 정의하는 템플릿
 */
@Entity(tableName = "ocr_template")
data class OcrTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val equipmentIds: List<Long>,
    val regions: List<EquipmentRegion> = emptyList() // 장비별 좌표 정보 추가
)
