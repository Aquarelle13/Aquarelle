package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "worker")
data class Worker(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val role: String,
    val group: String,
    val skillLevel: Int,               // 1:주황, 2:노랑, 3:녹색
    val yearsExp: Int = 0,
    val preferredEquipmentIds: List<Long> = emptyList(), // 다중 선택 지원
    val displayOrder: Int = 0          // 드래그 앤 드롭 순서
)