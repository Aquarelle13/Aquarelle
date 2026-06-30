package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 스케줄표의 장비 리스트 순서를 정의하는 템플릿
 */
@Entity(tableName = "ocr_template")
data class OcrTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val equipmentIds: List<Long>
)
