package com.psp.shifthelper.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.psp.shifthelper.data.model.AssignmentWeight
import kotlinx.coroutines.flow.Flow

@Dao
interface AssignmentWeightDao {
    @Query("SELECT * FROM assignment_weight")
    fun getAll(): Flow<List<AssignmentWeight>>

    @Query("SELECT * FROM assignment_weight WHERE workerId = :workerId AND equipmentId = :equipmentId LIMIT 1")
    suspend fun getByWorkerAndEquipment(workerId: Long, equipmentId: Long): AssignmentWeight?

    @Upsert
    suspend fun upsert(weight: AssignmentWeight)

    @Delete
    suspend fun delete(weight: AssignmentWeight)
}
