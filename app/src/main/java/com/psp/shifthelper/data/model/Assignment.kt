package com.psp.shifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignment")
data class Assignment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val shift: String,
    val workerId: Long,
    val equipmentId: Long,
    val status: String = "Ok"
)