package com.psp.shifthelper.ui.auto

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psp.shifthelper.PSPApplication
import com.psp.shifthelper.data.model.EquipmentAlias
import com.psp.shifthelper.data.model.OcrTemplate
import com.psp.shifthelper.domain.OcrResult
import com.psp.shifthelper.domain.OcrService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class OcrUiState {
    object Idle : OcrUiState()
    object Loading : OcrUiState()
    data class Success(val result: OcrResult) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}

class OcrViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PSPApplication
    private val localDataSource = app.localDataSource
    private val aliasDao = app.database.equipmentAliasDao()
    private val templateDao = app.database.ocrTemplateDao()
    private val ocrService = OcrService(application)

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri

    val templates: StateFlow<List<OcrTemplate>> = templateDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun processImage(uri: Uri, date: String? = null, shift: String = "주간") {
        _selectedImageUri.value = uri
        viewModelScope.launch {
            _uiState.value = OcrUiState.Loading
            try {
                val allEquipments = localDataSource.loadEquipments()
                val aliases = aliasDao.getAll()
                val currentTemplates = templates.value
                val result = ocrService.processImage(uri, allEquipments, aliases, currentTemplates, date, shift)
                _uiState.value = OcrUiState.Success(result)
            } catch (e: Exception) {
                _uiState.value = OcrUiState.Error(
                    e.message ?: "OCR 처리 중 오류가 발생했습니다"
                )
            }
        }
    }

    // 매칭 결과 학습 (Alias 저장)
    fun learnAlias(rawText: String, equipmentId: Long) {
        viewModelScope.launch {
            aliasDao.upsert(EquipmentAlias(rawText, equipmentId))
            // 재처리하여 반영 가능 (선택적)
        }
    }

    // 인식 결과 수동 수정
    fun updateManualState(equipmentId: Long, isRunning: Boolean) {
        val currentState = _uiState.value
        if (currentState is OcrUiState.Success) {
            val updatedMap = currentState.result.equipmentStates.toMutableMap()
            updatedMap[equipmentId] = isRunning
            
            _uiState.value = OcrUiState.Success(
                currentState.result.copy(equipmentStates = updatedMap)
            )
        }
    }

    fun updateShift(shift: String) {
        val currentState = _uiState.value
        if (currentState is OcrUiState.Success) {
            val result = currentState.result
            val newStates = if (shift == "주간") result.dayStates else result.nightStates
            _uiState.value = OcrUiState.Success(result.copy(equipmentStates = newStates))
        }
    }

    fun reset() {
        _uiState.value = OcrUiState.Idle
        _selectedImageUri.value = null
    }

    fun saveTemplate(name: String, equipmentIds: List<Long>) {
        viewModelScope.launch {
            templateDao.upsert(OcrTemplate(name = name, equipmentIds = equipmentIds))
        }
    }

    fun deleteTemplate(template: OcrTemplate) {
        viewModelScope.launch {
            templateDao.delete(template)
        }
    }
}
