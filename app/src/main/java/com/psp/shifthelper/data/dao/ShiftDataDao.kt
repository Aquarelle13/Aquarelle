package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.ShiftData
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDataDao {
    @Query("SELECT * FROM shift_data")
    fun getAll(): Flow<List<ShiftData>>

    @Query("SELECT * FROM shift_data WHERE date = :date AND shift = :shift AND team = :team LIMIT 1")
    suspend fun getByUniqueKey(date: String, shift: String, team: String): ShiftData?

    @Upsert
    suspend fun upsert(shiftData: ShiftData)

    @Delete
    suspend fun delete(shiftData: ShiftData)

    @Query("DELETE FROM shift_data WHERE date = :date AND shift = :shift AND team = :team")
    suspend fun deleteByUniqueKey(date: String, shift: String, team: String)
}
