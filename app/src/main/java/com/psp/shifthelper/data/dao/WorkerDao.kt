package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.Worker
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkerDao {
    @Query("SELECT * FROM worker")
    fun getAll(): Flow<List<Worker>>

    @Query("SELECT * FROM worker WHERE `group` = :group")
    fun getByGroup(group: String): Flow<List<Worker>>

    @Upsert
    suspend fun upsert(worker: Worker)

    @Delete
    suspend fun delete(worker: Worker)
}