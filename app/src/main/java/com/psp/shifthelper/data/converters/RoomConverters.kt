package com.psp.shifthelper.data.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class RoomConverters {
    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> {
        val listType = object : TypeToken<List<Long>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromEquipmentStates(value: Map<Long, Boolean>?): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toEquipmentStates(value: String): Map<Long, Boolean> {
        val mapType = object : TypeToken<Map<Long, Boolean>>() {}.type
        return Gson().fromJson(value, mapType) ?: emptyMap()
    }
}
