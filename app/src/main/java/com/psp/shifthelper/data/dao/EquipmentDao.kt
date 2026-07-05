package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.Equipment
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment")
    fun getAll(): Flow<List<Equipment>>

    @Query("SELECT * FROM equipment")
    suspend fun getAllList(): List<Equipment>

    @Query("SELECT * FROM equipment WHERE id = :id")
    fun getById(id: Long): Flow<Equipment>

    @Upsert
    suspend fun upsert(equipment: Equipment)

    @Delete
    suspend fun delete(equipment: Equipment)
}