package com.psp.shifthelper.data.local

import android.content.Context
import android.content.SharedPreferences
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.Worker
import com.psp.shifthelper.data.model.Assignment
import com.psp.shifthelper.data.model.EquipmentSet
import org.json.JSONArray
import org.json.JSONObject

class LocalDataSource(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("psp_data", Context.MODE_PRIVATE)

    // ========== 장비 ==========

    fun saveEquipments(equipments: List<Equipment>) {
        val array = JSONArray()
        equipments.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("code", e.code)
            obj.put("name", e.name)
            obj.put("isRunning", e.isRunning)
            obj.put("isSpecial", e.isSpecial)
            obj.put("displayOrder", e.displayOrder)
            obj.put("posX", e.posX)
            obj.put("posY", e.posY)
            obj.put("isManualCheck", e.isManualCheck)
            array.put(obj)
        }
        prefs.edit().putString("equipments", array.toString()).apply()
    }

    fun loadEquipments(): List<Equipment> {
        val json = prefs.getString("equipments", null) ?: return emptyList()
        val array = JSONArray(json)
        val result = mutableListOf<Equipment>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Equipment(
                    id = obj.getLong("id"),
                    code = obj.getString("code"),
                    name = obj.getString("name"),
                    isRunning = obj.getBoolean("isRunning"),
                    isSpecial = obj.getBoolean("isSpecial"),
                    displayOrder = obj.optInt("displayOrder", 0),
                    posX = (obj.optDouble("posX", 0.0)).toFloat(),
                    posY = (obj.optDouble("posY", 0.0)).toFloat(),
                    isManualCheck = obj.optBoolean("isManualCheck", false)
                )
            )
        }
        return result
    }

    // ========== 조원 ==========

    fun saveWorkers(workers: List<Worker>) {
        val array = JSONArray()
        workers.forEach { w ->
            val obj = JSONObject()
            obj.put("id", w.id)
            obj.put("name", w.name)
            obj.put("role", w.role)
            obj.put("group", w.group)
            obj.put("skillLevel", w.skillLevel)
            obj.put("yearsExp", w.yearsExp)
            val prefArray = JSONArray()
            w.preferredEquipmentIds.forEach { prefArray.put(it) }
            obj.put("preferredEquipmentIds", prefArray)
            array.put(obj)
        }
        prefs.edit().putString("workers", array.toString()).apply()
    }

    fun loadWorkers(): List<Worker> {
        val json = prefs.getString("workers", null) ?: return emptyList()
        val array = JSONArray(json)
        val result = mutableListOf<Worker>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val prefArray = obj.optJSONArray("preferredEquipmentIds")
            val prefIds = mutableListOf<Long>()
            if (prefArray != null) {
                for (j in 0 until prefArray.length()) {
                    prefIds.add(prefArray.getLong(j))
                }
            }
            result.add(
                Worker(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    role = obj.getString("role"),
                    group = obj.getString("group"),
                    skillLevel = obj.getInt("skillLevel"),
                    yearsExp = obj.getInt("yearsExp"),
                    preferredEquipmentIds = prefIds
                )
            )
        }
        return result
    }

    // ========== 배정 ==========

    fun saveAssignments(assignments: List<Assignment>) {
        val array = JSONArray()
        assignments.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("date", a.date)
            obj.put("shift", a.shift)
            obj.put("workerId", a.workerId)
            obj.put("equipmentId", a.equipmentId)
            obj.put("status", a.status)
            array.put(obj)
        }
        prefs.edit().putString("assignments", array.toString()).apply()
    }

    fun loadAssignments(): List<Assignment> {
        val json = prefs.getString("assignments", null) ?: return emptyList()
        val array = JSONArray(json)
        val result = mutableListOf<Assignment>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Assignment(
                    id = obj.getLong("id"),
                    date = obj.getString("date"),
                    shift = obj.getString("shift"),
                    workerId = obj.getLong("workerId"),
                    equipmentId = obj.getLong("equipmentId"),
                    status = obj.getString("status")
                )
            )
        }
        return result
    }

    // ========== 세트 장비 ==========

    fun saveEquipmentSets(sets: List<EquipmentSet>) {
        val array = JSONArray()
        sets.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            
            val equipArray = JSONArray()
            s.equipmentIds.forEach { equipArray.put(it) }
            obj.put("equipmentIds", equipArray)
            
            array.put(obj)
        }
        prefs.edit().putString("equipment_sets", array.toString()).apply()
    }

    fun loadEquipmentSets(): List<EquipmentSet> {
        val json = prefs.getString("equipment_sets", null) ?: return emptyList()
        val array = JSONArray(json)
        val result = mutableListOf<EquipmentSet>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val equipArray = obj.getJSONArray("equipmentIds")
            val equipIds = mutableListOf<Long>()
            for (j in 0 until equipArray.length()) {
                equipIds.add(equipArray.getLong(j))
            }
            result.add(
                EquipmentSet(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    equipmentIds = equipIds
                )
            )
        }
        return result
    }

    // ========== 임시 배정 데이터 ==========

    fun saveTempManualAssignments(assignments: Map<Long, Long>) {
        val obj = JSONObject()
        assignments.forEach { (equipId, workerId) ->
            obj.put(equipId.toString(), workerId)
        }
        prefs.edit().putString("temp_manual_assignments", obj.toString()).apply()
    }

    fun loadTempManualAssignments(): Map<Long, Long> {
        val json = prefs.getString("temp_manual_assignments", null) ?: return emptyMap()
        val obj = JSONObject(json)
        val result = mutableMapOf<Long, Long>()
        obj.keys().forEach { key ->
            result[key.toLong()] = obj.getLong(key)
        }
        return result
    }

    fun clearTempManualAssignments() {
        prefs.edit().remove("temp_manual_assignments").apply()
    }
}