package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.OcrCache

@Dao
interface OcrCacheDao {
    @Query("SELECT * FROM ocr_cache WHERE imageHash = :hash")
    suspend fun getCache(hash: String): OcrCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: OcrCache)

    @Query("DELETE FROM ocr_cache WHERE timestamp < :expiry")
    suspend fun deleteOldCache(expiry: Long)
}
