package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.Assignment
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignment")
    fun getAll(): Flow<List<Assignment>>

    @Query("SELECT * FROM assignment WHERE date = :date AND shift = :shift")
    fun getByDateAndShift(date: String, shift: String): Flow<List<Assignment>>

    @Query("SELECT * FROM assignment WHERE workerId = :workerId ORDER BY date DESC LIMIT 3")
    fun getRecentByWorker(workerId: Long): Flow<List<Assignment>>

    @Upsert
    suspend fun upsert(assignment: Assignment)

    @Delete
    suspend fun delete(assignment: Assignment)
}