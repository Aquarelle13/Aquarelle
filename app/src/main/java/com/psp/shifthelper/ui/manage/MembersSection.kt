package com.psp.shifthelper.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psp.shifthelper.data.model.Worker
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.ui.components.EquipmentMapCanvas
import com.psp.shifthelper.ui.home.HomeViewModel
import com.psp.shifthelper.ui.theme.*

@Composable
fun MembersSection(
    manageViewModel: ManageViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val workers by manageViewModel.workers.collectAsState()
    val selectedGroup by manageViewModel.selectedGroup.collectAsState()
    val allEquipments by homeViewModel.equipments.collectAsState()
    val allWeights by manageViewModel.allWeights.collectAsState()
    
    val filteredWorkers = remember(workers, selectedGroup) {
        workers.filter { it.group == selectedGroup }.sortedBy { it.displayOrder }
    }

    var showWorkerDialog by remember { mutableStateOf(false) }
    var selectedWorker by remember { mutableStateOf<Worker?>(null) }
    
    var showEquipPickDialog by remember { mutableStateOf(false) }
    var pickingForWorker by remember { mutableStateOf<Worker?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("02", "MEMBERS")
                IconButton(onClick = { selectedWorker = null; showWorkerDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue)
                }
            }

            HorizontalDivider(color = Border, thickness = 0.5.dp)

            filteredWorkers.forEachIndexed { index, worker ->
                // 숙련도 통계 추출 (가중치 높은 상위 2개 장비)
                val familiarEquips = allWeights.filter { it.workerId == worker.id }
                    .sortedByDescending { it.weight }
                    .take(2)
                    .mapNotNull { weight -> allEquipments.find { it.id == weight.equipmentId }?.code }

                MemberRow(
                    worker = worker,
                    familiarEquips = familiarEquips,
                    onNameClick = { selectedWorker = worker; showWorkerDialog = true },
                    onSkillChange = { newLevel -> manageViewModel.upsertWorker(worker.copy(skillLevel = newLevel)) },
                    onPickEquip = { pickingForWorker = worker; showEquipPickDialog = true },
                    onDelete = { manageViewModel.deleteWorker(worker) },
                    onMoveUp = if (index > 0) { { manageViewModel.moveWorker(worker, filteredWorkers[index - 1]) } } else null,
                    onMoveDown = if (index < filteredWorkers.size - 1) { { manageViewModel.moveWorker(worker, filteredWorkers[index + 1]) } } else null
                )
                HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp)
            }
            
            if (filteredWorkers.isEmpty()) {
                Text("등록된 조원이 없습니다.", fontSize = 12.sp, color = MutedForeground, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }

    if (showWorkerDialog) {
        WorkerDialog(
            worker = selectedWorker,
            currentGroup = selectedGroup,
            onDismiss = { showWorkerDialog = false },
            onConfirm = { manageViewModel.upsertWorker(it); showWorkerDialog = false }
        )
    }
    
    if (showEquipPickDialog && pickingForWorker != null) {
        EquipmentPickDialog(
            worker = pickingForWorker!!,
            allEquipments = allEquipments,
            onDismiss = { showEquipPickDialog = false },
            onConfirm = { ids -> 
                manageViewModel.upsertWorker(pickingForWorker!!.copy(preferredEquipmentIds = ids))
                showEquipPickDialog = false
            }
        )
    }
}

@Composable
fun MemberRow(
    worker: Worker,
    familiarEquips: List<String>,
    onNameClick: () -> Unit,
    onSkillChange: (Int) -> Unit,
    onPickEquip: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 순서 변경 핸들
        Column(modifier = Modifier.width(24.dp)) {
            if (onMoveUp != null) {
                Icon(
                    Icons.Default.DragHandle, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp).clickable { onMoveUp() }, 
                    tint = Border
                )
            }
            if (onMoveDown != null) {
                Icon(
                    Icons.Default.DragHandle, 
                    contentDescription = null, 
                    modifier = Modifier.size(16.dp).clickable { onMoveDown() }, 
                    tint = Border
                )
            }
        }

        // 이름 및 숙련 장비 요약
        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = worker.name,
                modifier = Modifier.clickable { onNameClick() },
                fontSize = 14.sp,
                color = Foreground,
                fontWeight = FontWeight.Bold
            )
            if (familiarEquips.isNotEmpty()) {
                Text(
                    text = "숙련: ${familiarEquips.joinToString(", ")}",
                    fontSize = 10.sp,
                    color = AccentBlue.copy(alpha = 0.8f)
                )
            }
        }

        // 스킬 매트릭스
        Row(
            modifier = Modifier.weight(2.5f).pointerInput(worker.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val threshold = 20f
                    if (dragAmount.x > threshold) onSkillChange((worker.skillLevel + 1).coerceAtMost(3))
                    else if (dragAmount.x < -threshold) onSkillChange((worker.skillLevel - 1).coerceAtLeast(1))
                }
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(1, 2, 3).forEach { level ->
                val isSelected = worker.skillLevel >= level
                val color = when(level) {
                    1 -> StatusWarn
                    2 -> Color(0xFFFFD600)
                    else -> StatusOk
                }
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 14.dp)
                        .background(
                            if (isSelected) color else Border.copy(alpha = 0.2f),
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { onSkillChange(level) }
                )
            }
        }

        // 선호 장비 (톱니바퀴)
        IconButton(onClick = onPickEquip, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = MutedForeground, modifier = Modifier.size(18.dp))
        }

        // 삭제
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerDialog(
    worker: Worker?,
    currentGroup: String,
    onDismiss: () -> Unit,
    onConfirm: (Worker) -> Unit
) {
    var name by remember { mutableStateOf(worker?.name ?: "") }
    var role by remember { mutableStateOf(worker?.role ?: "사원") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (worker == null) "조원 추가" else "이름 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") })
                OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("직급") })
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(Worker(
                    id = worker?.id ?: 0L,
                    name = name,
                    role = role,
                    group = worker?.group ?: currentGroup,
                    skillLevel = worker?.skillLevel ?: 1,
                    preferredEquipmentIds = worker?.preferredEquipmentIds ?: emptyList(),
                    displayOrder = worker?.displayOrder ?: 0
                ))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
fun EquipmentPickDialog(
    worker: Worker,
    allEquipments: List<com.psp.shifthelper.data.model.Equipment>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<Long>().apply { addAll(worker.preferredEquipmentIds) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${worker.name}의 선호 장비 선택") },
        text = {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                EquipmentMapCanvas(
                    equipments = allEquipments,
                    selectedEquipmentId = null,
                    assignments = selectedIds.associateWith { "선호" },
                    onEquipmentClick = { equip ->
                        if (selectedIds.contains(equip.id)) selectedIds.remove(equip.id)
                        else selectedIds.add(equip.id)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedIds.toList()) }) { Text("확정") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
