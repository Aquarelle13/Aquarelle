package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.StatusKeyword
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusKeywordDao {
    @Query("SELECT * FROM status_keywords")
    fun getAll(): Flow<List<StatusKeyword>>

    @Query("SELECT * FROM status_keywords")
    suspend fun getAllList(): List<StatusKeyword>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(keyword: StatusKeyword)

    @Delete
    suspend fun delete(keyword: StatusKeyword)
}
