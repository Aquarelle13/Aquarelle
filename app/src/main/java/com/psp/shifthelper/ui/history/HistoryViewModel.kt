package com.psp.shifthelper.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psp.shifthelper.PSPApplication
import com.psp.shifthelper.data.model.Assignment
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.ShiftData
import com.psp.shifthelper.data.model.Worker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryItem(
    val date: String,
    val shift: String,
    val team: String? = null,
    val assignments: List<HistoryDetail>,
    val equipmentStates: Map<Long, Boolean>? = null
)

data class HistoryDetail(
    val workerName: String,
    val equipmentCode: String,
    val status: String
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val localDataSource = (application as PSPApplication).localDataSource

    private val _historyItems = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyItems: StateFlow<List<HistoryItem>> = _historyItems

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            val assignments = localDataSource.loadAssignments()
            val workers = localDataSource.loadWorkers()
            val equipments = localDataSource.loadEquipments()
            val shiftDataDao = (getApplication() as PSPApplication).database.shiftDataDao()
            val storedShiftData = shiftDataDao.getAll().first()

            // 배정 내역 그룹화
            val assignmentGrouped = assignments.groupBy { "${it.date}|${it.shift}" }
            
            // 모든 고유한 (날짜, 쉬프트, 팀) 조합 찾기
            val shiftDataKeys = storedShiftData.map { "${it.date}|${it.shift}|${it.team}" }
            val assignmentKeys = assignmentGrouped.keys.map { key ->
                val (date, shift) = key.split("|")
                val team = assignmentGrouped[key]?.firstOrNull()?.let { a -> 
                    workers.find { it.id == a.workerId }?.group 
                } ?: ""
                "$date|$shift|$team"
            }
            
            val allKeys = (shiftDataKeys + assignmentKeys).distinct()

            val items = allKeys.map { key ->
                val parts = key.split("|")
                val date = parts[0]
                val shift = parts[1]
                val team = if (parts.size > 2) parts[2] else ""
                
                val shiftInfo = storedShiftData.find { it.date == date && it.shift == shift && it.team == team }
                val assigns = assignmentGrouped["$date|$shift"]?.filter { a ->
                    if (team.isEmpty()) true 
                    else workers.find { it.id == a.workerId }?.group == team
                } ?: emptyList()
                
                HistoryItem(
                    date = date,
                    shift = shift,
                    team = if (team.isNotEmpty()) team else shiftInfo?.team,
                    assignments = assigns.map { assign ->
                        HistoryDetail(
                            workerName = workers.find { it.id == assign.workerId }?.name ?: "알 수 없음",
                            equipmentCode = equipments.find { it.id == assign.equipmentId }?.code ?: "알 수 없음",
                            status = assign.status
                        )
                    },
                    equipmentStates = shiftInfo?.equipmentStates
                )
            }.sortedByDescending { it.date }

            _historyItems.value = items
        }
    }

    fun deleteHistoryByGroup(date: String, shift: String, team: String?) {
        viewModelScope.launch {
            // 배정 내역 삭제
            val currentAssigns = localDataSource.loadAssignments().toMutableList()
            currentAssigns.removeAll { it.date == date && it.shift == shift }
            localDataSource.saveAssignments(currentAssigns)
            
            // 시프트 데이터 삭제
            val db = (getApplication() as PSPApplication).database
            if (team != null) {
                db.shiftDataDao().deleteByUniqueKey(date, shift, team)
            }

            loadHistory()
        }
    }
}
