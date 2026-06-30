package com.psp.shifthelper.ui.auto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psp.shifthelper.PSPApplication
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.Worker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ManualAssignUiState(
    val selectedWorkerId: Long? = null,
    val selectedEquipmentId: Long? = null,
    val currentAssignments: Map<Long, Long> = emptyMap(), // equipmentId -> workerId
    val availableWorkers: List<Worker> = emptyList(),
    val equipments: List<Equipment> = emptyList()
)

class ManualAssignViewModel(application: Application) : AndroidViewModel(application) {
    private val localDataSource = (application as PSPApplication).localDataSource

    private val _uiState = MutableStateFlow(ManualAssignUiState())
    val uiState: StateFlow<ManualAssignUiState> = _uiState

    fun init(date: String, shift: String, group: String, attendance: Map<Long, Boolean>) {
        viewModelScope.launch {
            val allWorkers = localDataSource.loadWorkers()
            val allEquips = localDataSource.loadEquipments()
            val tempAssignments = localDataSource.loadTempManualAssignments()
            
            _uiState.value = _uiState.value.copy(
                availableWorkers = allWorkers.filter { attendance[it.id] == true },
                equipments = allEquips,
                currentAssignments = tempAssignments
            )
        }
    }

    fun initWithExisting(
        date: String,
        shift: String,
        group: String,
        attendance: Map<Long, Boolean>,
        existing: Map<Long, Long>
    ) {
        viewModelScope.launch {
            val allWorkers = localDataSource.loadWorkers()
            val allEquips = localDataSource.loadEquipments()
            
            // 기존 배정이 있으면 임시 저장소도 업데이트
            localDataSource.saveTempManualAssignments(existing)
            
            _uiState.value = _uiState.value.copy(
                availableWorkers = allWorkers.filter { it.group == group || attendance[it.id] == true },
                equipments = allEquips,
                currentAssignments = existing
            )
        }
    }

    fun selectWorker(workerId: Long?) {
        val current = _uiState.value
        if (current.selectedEquipmentId != null && workerId != null) {
            assign(current.selectedEquipmentId, workerId)
        } else {
            _uiState.value = current.copy(selectedWorkerId = workerId, selectedEquipmentId = null)
        }
    }

    fun selectEquipment(equipmentId: Long?) {
        val current = _uiState.value
        if (current.selectedWorkerId != null && equipmentId != null) {
            assign(equipmentId, current.selectedWorkerId)
        } else {
            _uiState.value = current.copy(selectedEquipmentId = equipmentId, selectedWorkerId = null)
        }
    }

    private fun assign(equipmentId: Long, workerId: Long) {
        val currentMap = _uiState.value.currentAssignments.toMutableMap()
        currentMap[equipmentId] = workerId
        
        // 임시 저장
        localDataSource.saveTempManualAssignments(currentMap)
        
        _uiState.value = _uiState.value.copy(
            currentAssignments = currentMap,
            selectedWorkerId = null,
            selectedEquipmentId = null
        )
    }

    fun removeAssignment(equipmentId: Long) {
        val currentMap = _uiState.value.currentAssignments.toMutableMap()
        currentMap.remove(equipmentId)
        
        // 임시 저장 업데이트
        localDataSource.saveTempManualAssignments(currentMap)
        
        _uiState.value = _uiState.value.copy(currentAssignments = currentMap)
    }

    fun clearTempData() {
        localDataSource.clearTempManualAssignments()
        _uiState.value = _uiState.value.copy(currentAssignments = emptyMap())
    }
}
