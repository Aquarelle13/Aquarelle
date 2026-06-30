package com.psp.shifthelper.data.model

data class EquipmentSet(
    val id: Long = 0,
    val name: String,                    // 세트 이름 (예: 정밀 가공 라인)
    val equipmentIds: List<Long>         // 묶음 장비 ID 목록
)