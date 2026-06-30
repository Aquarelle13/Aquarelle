package com.psp.shifthelper.ui.auto

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psp.shifthelper.PSPApplication
import com.psp.shifthelper.data.model.Assignment
import com.psp.shifthelper.data.model.AssignmentWeight
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.Worker
import com.psp.shifthelper.data.model.ShiftData
import com.psp.shifthelper.domain.AssignInput
import com.psp.shifthelper.domain.AssignOutput
import com.psp.shifthelper.domain.AutoAssignUseCase
import com.psp.shifthelper.domain.OcrResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AssignStep {
    OCR_SCAN,      // Step 1: 스케줄 이미지 인식
    DATE_SHIFT,    // Step 2: 날짜 및 근무조 확정
    WORKER_CHECK,  // Step 3: 출근 인원 확인
    RESULT         // Step 4: 배정 결과 확인
}

sealed class AssignUiState {
    object Idle : AssignUiState()
    object Loading : AssignUiState()
    data class Success(val output: AssignOutput) : AssignUiState()
    data class Error(val message: String) : AssignUiState()
}

class AutoAssignViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PSPApplication
    private val localDataSource = app.localDataSource
    private val weightDao = app.database.assignmentWeightDao()
    private val shiftDataDao = app.database.shiftDataDao()
    private val useCase = AutoAssignUseCase()

    // 현재 진행 스텝 (OCR_SCAN 부터 시작)
    private val _currentStep = MutableStateFlow(AssignStep.OCR_SCAN)
    val currentStep: StateFlow<AssignStep> = _currentStep

    private val _assignState = MutableStateFlow<AssignUiState>(AssignUiState.Idle)
    val assignState: StateFlow<AssignUiState> = _assignState

    private val _storedShiftDates = MutableStateFlow<List<String>>(emptyList())
    val storedShiftDates: StateFlow<List<String>> = _storedShiftDates

    init {
        loadStoredShiftDates()
    }

    private fun loadStoredShiftDates() {
        viewModelScope.launch {
            shiftDataDao.getAll().collect { list ->
                _storedShiftDates.value = list.map { it.date }.distinct()
            }
        }
    }

    private val _attendance = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val attendance: StateFlow<Map<Long, Boolean>> = _attendance

    private val _otGroup = MutableStateFlow("C")
    val otGroup: StateFlow<String> = _otGroup

    fun setStep(step: AssignStep) {
        _currentStep.value = step
    }

    fun initAttendance(mainGroup: String, otGroup: String) {
        viewModelScope.launch {
            val allWorkers = localDataSource.loadWorkers()
            val newAttendance = mutableMapOf<Long, Boolean>()
            allWorkers.forEach {
                if (it.group == mainGroup) newAttendance[it.id] = true
                else if (it.group == otGroup) newAttendance[it.id] = false
            }
            _attendance.value = newAttendance
            _otGroup.value = otGroup
        }
    }

    fun toggleAttendance(workerId: Long) {
        val current = _attendance.value.toMutableMap()
        current[workerId] = !(current[workerId] ?: false)
        _attendance.value = current
    }

    fun applyOcrResult(result: OcrResult) {
        viewModelScope.launch {
            val allEquips = localDataSource.loadEquipments().map { equip ->
                if (result.equipmentStates.containsKey(equip.id)) {
                    equip.copy(isRunning = result.equipmentStates[equip.id] ?: false)
                } else equip
            }
            localDataSource.saveEquipments(allEquips)
        }
    }

    // 특정 근무 데이터 저장
    fun saveShiftData(date: String, shift: String, team: String, states: Map<Long, Boolean>) {
        viewModelScope.launch {
            shiftDataDao.upsert(
                ShiftData(
                    date = date,
                    shift = shift,
                    team = team,
                    equipmentStates = states
                )
            )
        }
    }

    // 특정 근무 데이터 존재 여부 확인
    suspend fun getExistingShiftData(date: String, shift: String, team: String): ShiftData? {
        return shiftDataDao.getByUniqueKey(date, shift, team)
    }

    fun startAutoAssign(date: String, shift: String, group: String, manualAssignments: Map<Long, Long> = emptyMap()) {
        viewModelScope.launch {
            _assignState.value = AssignUiState.Loading
            try {
                val allWorkers = localDataSource.loadWorkers()
                val allEquipments = localDataSource.loadEquipments()
                val allAssignments = localDataSource.loadAssignments()
                val weights = weightDao.getAll().first()

                val input = AssignInput(
                    date = date,
                    shift = shift,
                    runningEquipments = allEquipments.filter { it.isRunning },
                    availableWorkers = allWorkers.filter { _attendance.value[it.id] == true },
                    recentAssignments = allAssignments,
                    weights = weights,
                    fixedAssignments = manualAssignments
                )
                
                val output = useCase.execute(input)
                _assignState.value = AssignUiState.Success(output)
                _currentStep.value = AssignStep.RESULT
            } catch (e: Exception) {
                _assignState.value = AssignUiState.Error(e.message ?: "배정 중 오류 발생")
            }
        }
    }

    fun confirmAssignments(results: List<Assignment>) {
        viewModelScope.launch {
            val current = localDataSource.loadAssignments().toMutableList()
            current.addAll(results)
            localDataSource.saveAssignments(current)
            
            results.forEach { assign ->
                if (assign.status == "Fixed") {
                    updateWeightAndStats(assign)
                }
            }
            reset()
        }
    }

    fun confirmAssignmentsFromManual(manualMap: Map<Long, Long>, date: String, shift: String) {
        val assignments = manualMap.map { (equipId, workerId) ->
            Assignment(date = date, shift = shift, workerId = workerId, equipmentId = equipId, status = "Fixed")
        }
        confirmAssignments(assignments)
    }

    private suspend fun updateWeightAndStats(assign: Assignment) {
        val current = weightDao.getByWorkerAndEquipment(assign.workerId, assign.equipmentId)
        val weightDelta = if (assign.status == "Fixed") 0.1f else 0.0f
        
        if (current != null) {
            val updated = current.copy(
                weight = current.weight + weightDelta,
                cumulativeCount = current.cumulativeCount + 1,
                lastAssignedDate = assign.date
            )
            weightDao.upsert(updated)
        } else {
            weightDao.upsert(
                AssignmentWeight(
                    workerId = assign.workerId,
                    equipmentId = assign.equipmentId,
                    weight = 1.0f + weightDelta,
                    cumulativeCount = 1,
                    lastAssignedDate = assign.date
                )
            )
        }
    }

    fun reset() {
        _assignState.value = AssignUiState.Idle
        _currentStep.value = AssignStep.OCR_SCAN
    }

    fun resetAssignState() {
        _assignState.value = AssignUiState.Idle
    }
}
