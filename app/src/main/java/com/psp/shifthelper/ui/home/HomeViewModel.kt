package com.psp.shifthelper.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psp.shifthelper.PSPApplication
import com.psp.shifthelper.data.model.Equipment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val localDataSource = (application as PSPApplication).localDataSource

    private val _equipments = MutableStateFlow<List<Equipment>>(emptyList())
    val equipments: StateFlow<List<Equipment>> = _equipments

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    // 맵 설정 (기본 14x25)
    private val _gridCols = MutableStateFlow(14)
    val gridCols: StateFlow<Int> = _gridCols
    
    private val _gridRows = MutableStateFlow(25)
    val gridRows: StateFlow<Int> = _gridRows

    init {
        loadEquipments()
    }

    fun setMapSize(cols: Int, rows: Int) {
        _gridCols.value = cols
        _gridRows.value = rows
    }

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun updateEquipmentPosition(id: Long, posX: Float, posY: Float) {
        viewModelScope.launch {
            val current = _equipments.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(posX = posX, posY = posY)
                _equipments.value = current
            }
        }
    }

    fun updateEquipmentSize(id: Long, posX: Float, posY: Float, width: Float, height: Float) {
        viewModelScope.launch {
            val current = _equipments.value.toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(
                    posX = posX, 
                    posY = posY,
                    width = width,
                    height = height
                )
                _equipments.value = current
            }
        }
    }

    fun saveAllChanges() {
        viewModelScope.launch {
            localDataSource.saveEquipments(_equipments.value)
            _isEditMode.value = false
        }
    }

    private fun loadEquipments() {
        viewModelScope.launch {
            val data = localDataSource.loadEquipments()
            // 좌표 정보가 없는 이전 버전 데이터이거나 비어있으면 초기화
            if (data.isEmpty() || data.any { it.posX == 0f && it.posY == 0f }) {
                val defaultData = defaultEquipments()
                localDataSource.saveEquipments(defaultData)
                _equipments.value = defaultData
            } else {
                _equipments.value = data.sortedBy { it.displayOrder }
            }
        }
    }

    fun moveEquipmentUp(equipment: Equipment) {
        viewModelScope.launch {
            val list = _equipments.value.toMutableList()
            val index = list.indexOfFirst { it.id == equipment.id }
            if (index > 0) {
                // 이전 항목과 displayOrder 교체
                val prev = list[index - 1]
                val currentOrder = equipment.displayOrder
                val prevOrder = prev.displayOrder
                
                // 만약 displayOrder가 동일하다면 인덱스 기반으로 강제 재설정
                val newCurrentOrder = if (currentOrder <= prevOrder) index - 1 else prevOrder
                val newPrevOrder = if (currentOrder <= prevOrder) index else currentOrder

                list[index] = equipment.copy(displayOrder = newCurrentOrder)
                list[index - 1] = prev.copy(displayOrder = newPrevOrder)
                
                updateAndSave(list)
            }
        }
    }

    fun moveEquipmentDown(equipment: Equipment) {
        viewModelScope.launch {
            val list = _equipments.value.toMutableList()
            val index = list.indexOfFirst { it.id == equipment.id }
            if (index >= 0 && index < list.size - 1) {
                val next = list[index + 1]
                val currentOrder = equipment.displayOrder
                val nextOrder = next.displayOrder

                val newCurrentOrder = if (currentOrder >= nextOrder) index + 1 else nextOrder
                val newNextOrder = if (currentOrder >= nextOrder) index else currentOrder

                list[index] = equipment.copy(displayOrder = newCurrentOrder)
                list[index + 1] = next.copy(displayOrder = newNextOrder)
                
                updateAndSave(list)
            }
        }
    }

    private fun updateAndSave(newList: List<Equipment>) {
        val sorted = newList.sortedBy { it.displayOrder }
        _equipments.value = sorted
        localDataSource.saveEquipments(sorted)
    }

    fun upsertEquipment(equipment: Equipment) {
        viewModelScope.launch {
            val current = _equipments.value.toMutableList()
            val index = current.indexOfFirst { it.id == equipment.id }
            if (index >= 0) {
                current[index] = equipment
            } else {
                val newId = (current.maxOfOrNull { it.id } ?: 0L) + 1L
                val nextOrder = (current.maxOfOrNull { it.displayOrder } ?: -1) + 1
                current.add(equipment.copy(id = newId, displayOrder = nextOrder))
            }
            updateAndSave(current)
        }
    }

    fun deleteEquipment(equipment: Equipment) {
        viewModelScope.launch {
            val updated = _equipments.value.filter { it.id != equipment.id }
            updateAndSave(updated)
        }
    }

    private fun defaultEquipments() = listOf(
        // Cluster 1 (Left Top)
        Equipment(1L, "HYB 25/30p", "비가동", false, posX = 50f, posY = 200f),
        Equipment(2L, "88p 신형", "비가동", false, posX = 180f, posY = 200f),
        Equipment(3L, "Head Lamp 28p", "비가동", false, posX = 310f, posY = 200f),
        Equipment(4L, "HYB 24/32p", "비가동", false, posX = 110f, posY = 280f),
        Equipment(5L, "94p", "비가동", false, posX = 80f, posY = 400f),
        Equipment(6L, "모듈", "비가동", false, posX = 50f, posY = 460f),

        // Cluster 2 (Middle Left)
        Equipment(7L, "MT2 26p", "비가동", false, posX = 350f, posY = 300f),
        Equipment(8L, "MCP 4/6p", "비가동", false, posX = 350f, posY = 360f),
        Equipment(9L, "MQS LEVER", "비가동", false, posX = 350f, posY = 420f),
        Equipment(10L, "플렉시블 NEW", "비가동", false, posX = 360f, posY = 490f),
        Equipment(11L, "ACU 52p", "비가동", false, posX = 360f, posY = 580f),
        Equipment(12L, "025 26p/48p", "비가동", false, posX = 320f, posY = 650f),
        
        // Bottom Row
        Equipment(13L, "NX4", "비가동", false, posX = 60f, posY = 730f),
        Equipment(14L, "스키브", "비가동", false, posX = 160f, posY = 670f),
        Equipment(15L, "RF 54p", "비가동", false, posX = 200f, posY = 730f),
        Equipment(16L, "LIF", "비가동", false, posX = 360f, posY = 730f),
        Equipment(17L, "MQS 4p/14p", "비가동", false, posX = 360f, posY = 800f),
        Equipment(18L, "HPF 4p", "비가동", false, posX = 150f, posY = 810f),
        Equipment(19L, "HPF 3p", "비가동", false, posX = 260f, posY = 810f),

        // Cluster 3 (Middle Right)
        Equipment(20L, "ACU 구형", "비가동", false, posX = 550f, posY = 100f),
        Equipment(21L, "47/66/88p", "비가동", false, posX = 550f, posY = 180f),
        Equipment(22L, "JOINT #2", "비가동", false, posX = 530f, posY = 260f),
        Equipment(23L, "GM 30P", "비가동", false, posX = 550f, posY = 320f),
        Equipment(24L, "119P", "비가동", false, posX = 550f, posY = 380f),
        Equipment(25L, "MQS 40/81", "비가동", false, posX = 550f, posY = 440f),
        Equipment(26L, "플렉시블", "비가동", false, posX = 550f, posY = 510f),
        Equipment(27L, "GET/HYB", "비가동", false, posX = 680f, posY = 570f), // Long bar
        Equipment(28L, "38p 46p 덕산", "비가동", false, posX = 520f, posY = 630f),
        Equipment(29L, "090 10-12p", "비가동", false, posX = 520f, posY = 720f),
        Equipment(30L, "APS/BPS", "비가동", false, posX = 520f, posY = 780f),
        Equipment(31L, "MT2 6p", "비가동", false, posX = 520f, posY = 840f),

        // Cluster 4 & 5 (Far Right)
        Equipment(32L, "025 중국", "비가동", false, posX = 780f, posY = 140f),
        Equipment(33L, "EJ 16p/MCP", "비가동", false, posX = 780f, posY = 210f),
        Equipment(34L, "JOINT #1", "비가동", false, posX = 650f, posY = 260f),
        Equipment(35L, "59p Plug/Cap", "비가동", false, posX = 780f, posY = 300f),
        Equipment(36L, "HYB 34/36p", "비가동", false, posX = 780f, posY = 380f),
        Equipment(37L, "04016p", "비가동", false, posX = 780f, posY = 440f),
        Equipment(38L, "Joint #3", "비가동", false, posX = 780f, posY = 500f),
        Equipment(39L, "040 16p CAP", "비가동", false, posX = 780f, posY = 560f),
        
        Equipment(40L, "EJ 2P (O)", "비가동", false, posX = 680f, posY = 630f),
        Equipment(41L, "35p/38p", "비가동", false, posX = 680f, posY = 700f),
        Equipment(42L, "090 2p-4p", "비가동", false, posX = 680f, posY = 760f),
        Equipment(43L, "JPT 3p(N)", "비가동", false, posX = 680f, posY = 820f),
        Equipment(44L, "JPT 2P(N)", "비가동", false, posX = 680f, posY = 880f),
        
        Equipment(45L, "MT2 20/36p", "비가동", false, posX = 820f, posY = 630f),
        Equipment(46L, "025WW", "비가동", false, posX = 820f, posY = 700f),
        Equipment(47L, "MCON", "비가동", false, posX = 820f, posY = 760f),
        Equipment(48L, "025G", "비가동", false, posX = 820f, posY = 820f),
        Equipment(49L, "EJ 2P (N)", "비가동", false, posX = 820f, posY = 880f),
        Equipment(50L, "JPT 4p(o)", "비가동", false, posX = 820f, posY = 940f)
    )
}