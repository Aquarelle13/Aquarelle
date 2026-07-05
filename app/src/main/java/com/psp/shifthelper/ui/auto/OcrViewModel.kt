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
    private val equipmentDao = app.database.equipmentDao()
    private val aliasDao = app.database.equipmentAliasDao()
    private val templateDao = app.database.ocrTemplateDao()
    private val statusKeywordDao = app.database.statusKeywordDao()
    private val ocrService = OcrService(application, app.database.ocrCacheDao())

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri

    val templates: StateFlow<List<OcrTemplate>> = templateDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statusKeywords: StateFlow<List<com.psp.shifthelper.data.model.StatusKeyword>> = statusKeywordDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun processImage(uri: Uri, date: String? = null, shift: String = "주간", templateId: Long? = null) {
        _selectedImageUri.value = uri
        viewModelScope.launch {
            _uiState.value = OcrUiState.Loading
            try {
                val allEquipments = equipmentDao.getAllList()
                val aliases = aliasDao.getAll()
                val keywords = statusKeywordDao.getAllList()
                val currentTemplates = if (templateId != null) {
                    templates.value.filter { it.id == templateId }
                } else {
                    templates.value
                }
                val result = ocrService.processImage(uri, allEquipments, aliases, currentTemplates, keywords, date, shift)
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
            val result = currentState.result
            val updatedMap = result.equipmentStates.toMutableMap()
            updatedMap[equipmentId] = isRunning
            
            // 사용자의 판정 결과를 키워드로 학습
            val rawStatus = result.details[equipmentId]?.rawStatusText
            if (rawStatus != null) {
                viewModelScope.launch {
                    statusKeywordDao.upsert(com.psp.shifthelper.data.model.StatusKeyword(rawStatus, isRunning, isLocked = true))
                }
            }

            _uiState.value = OcrUiState.Success(
                result.copy(equipmentStates = updatedMap)
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

    fun saveTemplate(name: String, equipmentIds: List<Long>, id: Long = 0) {
        viewModelScope.launch {
            templateDao.upsert(OcrTemplate(id = id, name = name, equipmentIds = equipmentIds))
        }
    }

    fun deleteTemplate(template: OcrTemplate) {
        viewModelScope.launch {
            templateDao.delete(template)
        }
    }

    fun deleteStatusKeyword(keyword: com.psp.shifthelper.data.model.StatusKeyword) {
        viewModelScope.launch {
            statusKeywordDao.delete(keyword)
        }
    }

    fun saveStatusKeyword(rawText: String, isRunning: Boolean) {
        viewModelScope.launch {
            statusKeywordDao.upsert(com.psp.shifthelper.data.model.StatusKeyword(rawText, isRunning, isLocked = true))
        }
    }
}
