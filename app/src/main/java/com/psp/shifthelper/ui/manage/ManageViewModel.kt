package com.psp.shifthelper.ui.manage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psp.shifthelper.PSPApplication
import com.psp.shifthelper.data.model.Worker
import com.psp.shifthelper.data.model.EquipmentSet
import com.psp.shifthelper.data.model.AssignmentWeight
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ManageViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PSPApplication
    private val localDataSource = app.localDataSource
    private val weightDao = app.database.assignmentWeightDao()

    private val _workers = MutableStateFlow<List<Worker>>(emptyList())
    val workers: StateFlow<List<Worker>> = _workers

    private val _selectedGroup = MutableStateFlow("A")
    val selectedGroup: StateFlow<String> = _selectedGroup

    private val _equipmentSets = MutableStateFlow<List<EquipmentSet>>(emptyList())
    val equipmentSets: StateFlow<List<EquipmentSet>> = _equipmentSets

    // 학습 가중치 상태
    val allWeights: StateFlow<List<AssignmentWeight>> = weightDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadWorkers()
        loadEquipmentSets()
    }

    private fun loadEquipmentSets() {
        viewModelScope.launch {
            var data = localDataSource.loadEquipmentSets()
            if (data.isEmpty()) {
                data = defaultEquipmentSets()
                localDataSource.saveEquipmentSets(data)
            }
            _equipmentSets.value = data
        }
    }

    fun upsertEquipmentSet(set: EquipmentSet) {
        viewModelScope.launch {
            val current = _equipmentSets.value.toMutableList()
            val index = current.indexOfFirst { it.id == set.id }
            if (index >= 0) {
                current[index] = set
            } else {
                val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                current.add(set.copy(id = newId))
            }
            _equipmentSets.value = current
            localDataSource.saveEquipmentSets(current)
        }
    }

    fun deleteEquipmentSet(set: EquipmentSet) {
        viewModelScope.launch {
            val updated = _equipmentSets.value.filter { it.id != set.id }
            _equipmentSets.value = updated
            localDataSource.saveEquipmentSets(updated)
        }
    }

    private fun defaultEquipmentSets() = listOf(
        EquipmentSet(1L, "정밀 가공 라인", listOf(8L, 12L)),
        EquipmentSet(2L, "범용 절식 라인", listOf(4L, 6L))
    )

    fun setSelectedGroup(group: String) {
        _selectedGroup.value = group
    }

    private fun loadWorkers() {
        viewModelScope.launch {
            var data = localDataSource.loadWorkers()
            if (data.isEmpty()) {
                data = defaultWorkers()
                localDataSource.saveWorkers(data)
            }
            _workers.value = data
        }
    }

    fun getWorkersByGroup(group: String): List<Worker> {
        return _workers.value.filter { it.group == group }
    }

    fun upsertWorker(worker: Worker) {
        viewModelScope.launch {
            val current = _workers.value.toMutableList()
            if (worker.id == 0L) {
                // 신규 등록: 겹치지 않는 새 ID 생성
                val nextId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                val nextOrder = (current.maxOfOrNull { it.displayOrder } ?: -1) + 1
                current.add(worker.copy(id = nextId, displayOrder = nextOrder))
            } else {
                // 기존 수정
                val index = current.indexOfFirst { it.id == worker.id }
                if (index >= 0) {
                    current[index] = worker
                } else {
                    current.add(worker)
                }
            }
            _workers.value = current
            localDataSource.saveWorkers(current)
        }
    }

    fun deleteWorker(worker: Worker) {
        viewModelScope.launch {
            val updated = _workers.value.filter { it.id != worker.id }
            _workers.value = updated
            localDataSource.saveWorkers(updated)
        }
    }

    fun moveWorker(fromWorker: Worker, toWorker: Worker) {
        viewModelScope.launch {
            val current = _workers.value.toMutableList()
            val fromIndex = current.indexOfFirst { it.id == fromWorker.id }
            val toIndex = current.indexOfFirst { it.id == toWorker.id }
            
            if (fromIndex >= 0 && toIndex >= 0) {
                val fromOrder = fromWorker.displayOrder
                val toOrder = toWorker.displayOrder
                
                current[fromIndex] = fromWorker.copy(displayOrder = toOrder)
                current[toIndex] = toWorker.copy(displayOrder = fromOrder)
                
                _workers.value = current
                localDataSource.saveWorkers(current)
            }
        }
    }

    private fun defaultWorkers() = listOf(
        Worker(1L, "김철수", "기사", "A", 3, 5, listOf(1L), 0),
        Worker(2L, "이영희", "기사", "A", 3, 4, listOf(2L), 1),
        Worker(3L, "박민호", "주임", "A", 3, 8, listOf(4L), 2),
        Worker(4L, "정수진", "사원", "A", 1, 1, listOf(5L), 3),
        Worker(5L, "홍길동", "사원", "A", 2, 2, listOf(6L), 4),
        Worker(6L, "최도현", "사원", "A", 1, 1, listOf(8L), 5),
        Worker(7L, "한지우", "사원", "A", 2, 3, listOf(12L), 6),
        Worker(8L, "박지훈", "사원", "B", 2, 2, listOf(1L), 7),
        Worker(9L, "최수연", "기사", "B", 3, 6, listOf(2L), 8),
        Worker(10L, "김민준", "사원", "B", 1, 1, listOf(4L), 9)
    )
}
