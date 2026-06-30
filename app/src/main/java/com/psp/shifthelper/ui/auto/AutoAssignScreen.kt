package com.psp.shifthelper.ui.auto

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.OcrTemplate
import com.psp.shifthelper.data.model.Worker
import com.psp.shifthelper.data.model.ShiftData
import com.psp.shifthelper.ui.home.HomeViewModel
import com.psp.shifthelper.ui.manage.ManageViewModel
import com.psp.shifthelper.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoAssignScreen(
    autoViewModel: AutoAssignViewModel = viewModel(),
    ocrViewModel: OcrViewModel = viewModel(),
    manageViewModel: ManageViewModel = viewModel(),
    manualViewModel: ManualAssignViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val currentStep by autoViewModel.currentStep.collectAsState()
    val attendance by autoViewModel.attendance.collectAsState()
    val otGroup by autoViewModel.otGroup.collectAsState()
    val assignState by autoViewModel.assignState.collectAsState()
    
    var selectedDate by rememberSaveable { 
        mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("MM.dd"))) 
    }
    var selectedShift by rememberSaveable { mutableStateOf("주간") }
    var selectedGroup by rememberSaveable { mutableStateOf("A") }

    val workers by manageViewModel.workers.collectAsState()
    val ocrUiState by ocrViewModel.uiState.collectAsState()
    val manualState by manualViewModel.uiState.collectAsState()
    val allEquipments by homeViewModel.equipments.collectAsState()
    val templates by ocrViewModel.templates.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showConflictDialog by remember { mutableStateOf(false) }
    var existingShiftData by remember { mutableStateOf<ShiftData?>(null) }
    var pendingOcrStates by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    var showResultDialog by remember { mutableStateOf(false) }

    val recognizedDates = if (ocrUiState is OcrUiState.Success) (ocrUiState as OcrUiState.Success).result.recognizedDates else emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "AUTO ASSIGN", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Foreground)

        // STEP 1: 스케줄 인식 및 정보 확정
        StepContainer(
            number = "01",
            title = "스케줄 인식 및 정보 확정",
            summary = "$selectedDate / $selectedGroup 조 $selectedShift",
            isCompleted = currentStep > AssignStep.OCR_SCAN,
            onEdit = { autoViewModel.setStep(AssignStep.OCR_SCAN) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OcrSection(
                    selectedDate = selectedDate,
                    selectedShift = selectedShift,
                    onApplyToAssign = { },
                    ocrViewModel = ocrViewModel
                )

                if (ocrUiState is OcrUiState.Success) {
                    val result = (ocrUiState as OcrUiState.Success).result
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("배정 날짜 선택", fontSize = 12.sp, color = MutedForeground, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    recognizedDates.forEach { date ->
                                        val isSelected = selectedDate == date
                                        SelectionChip(
                                            text = date,
                                            isSelected = isSelected,
                                            onClick = { selectedDate = date }
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("근무조 및 쉬프트 선택", fontSize = 12.sp, color = MutedForeground, fontWeight = FontWeight.Bold)
                                val dateData = result.multiDateResults[selectedDate]
                                val availableShifts = dateData?.keys?.toList() ?: emptyList()
                                
                                if (availableShifts.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        availableShifts.forEach { shiftKey ->
                                            val isSelected = (selectedShift + selectedGroup) == shiftKey || 
                                                           (shiftKey.contains(selectedShift) && shiftKey.contains(selectedGroup))
                                            
                                            SelectionChip(
                                                text = shiftKey,
                                                isSelected = isSelected,
                                                onClick = {
                                                    // shiftKey가 "주간A" 형태라고 가정하고 분리 시도
                                                    if (shiftKey.contains("주간")) selectedShift = "주간"
                                                    else if (shiftKey.contains("야간")) selectedShift = "야간"
                                                    
                                                    listOf("A", "B", "C", "D").find { shiftKey.contains(it) }?.let {
                                                        selectedGroup = it
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    // 기존 수동 선택 UI 유지 (인식된 데이터가 없을 경우 대비)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("A", "B", "C", "D").forEach { group ->
                                            SelectionChip(
                                                text = "${group}조",
                                                isSelected = selectedGroup == group,
                                                onClick = { selectedGroup = group }
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("주간", "야간").forEach { shift ->
                                            SelectionChip(
                                                text = shift,
                                                isSelected = selectedShift == shift,
                                                onClick = { selectedShift = shift }
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = Border.copy(alpha = 0.3f))

                            val dateData = result.multiDateResults[selectedDate]
                            val shiftKey = dateData?.keys?.find { it.contains(selectedShift) && it.contains(selectedGroup) } 
                                          ?: dateData?.keys?.find { it.contains(selectedShift) }
                                          ?: dateData?.keys?.firstOrNull()
                            val states = dateData?.get(shiftKey) ?: result.equipmentStates
                            val runningCount = states.values.count { it }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("가동 판정 결과", fontSize = 12.sp, color = MutedForeground)
                                    Text("가동: ${runningCount}대 / 비가동: ${states.size - runningCount}대", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                                }
                                OutlinedButton(
                                    onClick = { showResultDialog = true },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("상세 확인 및 수정", fontSize = 11.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        val existing = autoViewModel.getExistingShiftData(selectedDate, selectedShift, selectedGroup)
                                        if (existing != null) {
                                            existingShiftData = existing
                                            pendingOcrStates = states
                                            showConflictDialog = true
                                        } else {
                                            autoViewModel.saveShiftData(selectedDate, selectedShift, selectedGroup, states)
                                            autoViewModel.applyOcrResult(result.copy(equipmentStates = states))
                                            proceedToNextStep(autoViewModel, selectedGroup)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusOk)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("검토 완료 - 인원 확인으로 이동", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // STEP 2: 출근 인원 확인
        StepContainer(
            number = "02",
            title = "출근 인원 확인",
            summary = "출근 ${attendance.values.count { it }}명",
            isCompleted = currentStep > AssignStep.WORKER_CHECK,
            onEdit = { autoViewModel.setStep(AssignStep.WORKER_CHECK) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AttendanceGroup(
                    title = "$selectedGroup 조 (기본)",
                    workers = workers.filter { it.group == selectedGroup }.sortedBy { it.displayOrder },
                    attendance = attendance,
                    onToggle = { autoViewModel.toggleAttendance(it) }
                )
                
                AttendanceGroup(
                    title = "$otGroup 조 (OT 특근)",
                    workers = workers.filter { it.group == otGroup }.sortedBy { it.displayOrder },
                    attendance = attendance,
                    onToggle = { autoViewModel.toggleAttendance(it) },
                    onTitleClick = { }
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            manualViewModel.init(selectedDate, selectedShift, selectedGroup, attendance)
                            autoViewModel.setStep(AssignStep.RESULT) 
                            autoViewModel.resetAssignState()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = Foreground)
                    ) { Text("수동 배정", fontSize = 12.sp) }

                    Button(
                        onClick = { autoViewModel.startAutoAssign(selectedDate, selectedShift, selectedGroup) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) { Text("자동 배정 시작", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // STEP 3: 배정 결과 확인
        StepContainer(
            number = "03",
            title = "배정 결과 확인",
            summary = if (assignState is AssignUiState.Success) "배정 완료" else "진행 중",
            isCompleted = false,
            onEdit = {}
        ) {
            if (currentStep == AssignStep.RESULT) {
                if (assignState is AssignUiState.Idle) {
                    Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                        ManualAssignSection(
                            state = manualState,
                            onWorkerClick = { w: Worker -> manualViewModel.selectWorker(w.id) },
                            onEquipmentClick = { e: Equipment -> manualViewModel.selectEquipment(e.id) },
                            onSave = { 
                                autoViewModel.confirmAssignmentsFromManual(manualState.currentAssignments, selectedDate, selectedShift) 
                            },
                            onSemiAuto = {
                                autoViewModel.startAutoAssign(selectedDate, selectedShift, selectedGroup, manualState.currentAssignments)
                            },
                            onCancel = { autoViewModel.setStep(AssignStep.WORKER_CHECK) }
                        )
                    }
                } else {
                    AssignResultSection(
                        date = selectedDate,
                        shift = selectedShift,
                        group = selectedGroup,
                        onManualEdit = {
                            if (assignState is AssignUiState.Success) {
                                val current = (assignState as AssignUiState.Success).output.assignments
                                val existingMap = current.associate { it.equipmentId to it.workerId }
                                manualViewModel.initWithExisting(selectedDate, selectedShift, selectedGroup, attendance, existingMap)
                                autoViewModel.resetAssignState()
                            }
                        },
                        viewModel = autoViewModel
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showResultDialog && ocrUiState is OcrUiState.Success) {
        val result = (ocrUiState as OcrUiState.Success).result
        val dateData = result.multiDateResults[selectedDate]
        val shiftKey = dateData?.keys?.find { it.contains(selectedShift) && it.contains(selectedGroup) } 
                      ?: dateData?.keys?.find { it.contains(selectedShift) }
                      ?: dateData?.keys?.firstOrNull()
        val currentStates = dateData?.get(shiftKey) ?: result.equipmentStates

        OcrResultDialog(
            result = result.copy(equipmentStates = currentStates),
            imageUri = ocrViewModel.selectedImageUri.collectAsState().value,
            allEquipments = allEquipments,
            onDismiss = { showResultDialog = false },
            onLearnAlias = { raw, id -> ocrViewModel.learnAlias(raw, id) },
            onUpdateState = { id, run -> ocrViewModel.updateManualState(id, run) },
            initialTab = 1, // 가동 판정 탭으로 시작
            templates = templates
        )
    }

    if (showConflictDialog && existingShiftData != null) {
        AlertDialog(
            onDismissRequest = { showConflictDialog = false },
            title = { Text("기존 데이터 발견") },
            text = { Text("$selectedDate ${selectedGroup}조 ${selectedShift}의 저장된 데이터가 이미 존재합니다. 어떤 데이터를 사용하시겠습니까?") },
            confirmButton = {
                Button(onClick = {
                    val result = (ocrUiState as OcrUiState.Success).result
                    autoViewModel.applyOcrResult(result.copy(equipmentStates = existingShiftData!!.equipmentStates))
                    showConflictDialog = false
                    proceedToNextStep(autoViewModel, selectedGroup)
                }) { Text("기존 데이터 사용") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val result = (ocrUiState as OcrUiState.Success).result
                    autoViewModel.saveShiftData(selectedDate, selectedShift, selectedGroup, pendingOcrStates)
                    autoViewModel.applyOcrResult(result.copy(equipmentStates = pendingOcrStates))
                    showConflictDialog = false
                    proceedToNextStep(autoViewModel, selectedGroup)
                }) { Text("새로 인식된 이미지 사용") }
            }
        )
    }
}

@Composable
fun StepContainer(
    number: String,
    title: String,
    summary: String,
    isCompleted: Boolean,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isCompleted) StatusOk else AccentBlue
                ) {
                    Text(text = number, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (isCompleted) MutedForeground else Foreground)
                if (isCompleted) {
                    Text(text = "($summary)", fontSize = 12.sp, color = MutedForeground)
                }
            }
            if (isCompleted) {
                TextButton(onClick = onEdit) {
                    Text("변경하기", fontSize = 12.sp, color = AccentBlue)
                }
            }
        }
        
        AnimatedVisibility(visible = !isCompleted) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
        
        if (isCompleted) {
            HorizontalDivider(color = Border.copy(alpha = 0.3f), thickness = 0.5.dp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceGroup(
    title: String,
    workers: List<Worker>,
    attendance: Map<Long, Boolean>,
    onToggle: (Long) -> Unit,
    onTitleClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Foreground)
                if (onTitleClick != null) {
                    Text(text = "다른 조 선택", fontSize = 11.sp, color = AccentBlue, modifier = Modifier.clickable { onTitleClick() })
                }
            }
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                workers.forEach { worker ->
                    val isPresent = attendance[worker.id] ?: false
                    Surface(
                        onClick = { onToggle(worker.id) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isPresent) StatusOk.copy(alpha = 0.2f) else SurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, if (isPresent) StatusOk else Border
                        )
                    ) {
                        Text(
                            text = worker.name,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            color = if (isPresent) StatusOk else MutedForeground,
                            fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

private fun proceedToNextStep(autoViewModel: AutoAssignViewModel, selectedGroup: String) {
    val defaultOt = when(selectedGroup) {
        "A" -> "C"; "B" -> "D"; "C" -> "A"; "D" -> "B"; else -> "C"
    }
    autoViewModel.initAttendance(selectedGroup, defaultOt)
    autoViewModel.setStep(AssignStep.WORKER_CHECK)
}

@Composable
fun SelectionChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) AccentBlue else SurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (isSelected) AccentBlue else Border.copy(alpha = 0.5f)
        ),
        shadowElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check, 
                    contentDescription = null, 
                    modifier = Modifier.size(14.dp), 
                    tint = Color.White
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else MutedForeground,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
