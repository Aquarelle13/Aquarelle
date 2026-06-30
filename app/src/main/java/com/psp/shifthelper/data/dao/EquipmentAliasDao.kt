package com.psp.shifthelper.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.psp.shifthelper.data.model.EquipmentAlias

@Dao
interface EquipmentAliasDao {
    @Query("SELECT * FROM equipment_alias")
    suspend fun getAll(): List<EquipmentAlias>

    @Upsert
    suspend fun upsert(alias: EquipmentAlias)
}
