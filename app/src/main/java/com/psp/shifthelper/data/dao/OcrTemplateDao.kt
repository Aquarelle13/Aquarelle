package com.psp.shifthelper.data.dao

import androidx.room.*
import com.psp.shifthelper.data.model.OcrTemplate
import kotlinx.coroutines.flow.Flow

@Dao
interface OcrTemplateDao {
    @Query("SELECT * FROM ocr_template")
    fun getAll(): Flow<List<OcrTemplate>>

    @Upsert
    suspend fun upsert(template: OcrTemplate)

    @Delete
    suspend fun delete(template: OcrTemplate)
}
