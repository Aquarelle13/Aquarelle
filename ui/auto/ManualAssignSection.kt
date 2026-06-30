package com.psp.shifthelper.ui.auto

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psp.shifthelper.ui.components.EquipmentMapCanvas
import com.psp.shifthelper.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualAssignSection(
    state: ManualAssignUiState,
    onWorkerClick: (com.psp.shifthelper.data.model.Worker) -> Unit,
    onEquipmentClick: (com.psp.shifthelper.data.model.Equipment) -> Unit,
    onSave: () -> Unit,
    onSemiAuto: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 상단: 배정 가능한 직원 리스트
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("배정 가능 인원 (이름 선택 후 장비 선택)", fontSize = 12.sp, color = MutedForeground, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableWorkers.forEach { worker ->
                        val isAssigned = state.currentAssignments.values.contains(worker.id)
                        val isSelected = state.selectedWorkerId == worker.id
                        Surface(
                            onClick = { onWorkerClick(worker) },
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isSelected -> AccentBlue
                                isAssigned -> Border.copy(alpha = 0.3f)
                                else -> Surface
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentBlue else Border)
                        ) {
                            Text(
                                text = worker.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else if (isAssigned) MutedForeground else Foreground
                            )
                        }
                    }
                }
            }
        }

        // 하단: 장비 맵
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val assignmentsMapping = state.currentAssignments.mapValues { entry ->
                state.availableWorkers.find { it.id == entry.value }?.name ?: "Unknown"
            }
            EquipmentMapCanvas(
                equipments = state.equipments,
                assignments = assignmentsMapping,
                selectedEquipmentId = state.selectedEquipmentId,
                onEquipmentClick = onEquipmentClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 하단 버튼
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
            Button(
                onClick = onSemiAuto,
                modifier = Modifier.weight(1.2f),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = Foreground)
            ) { Text("나머지 반자동", fontSize = 12.sp) }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("저장", fontWeight = FontWeight.Bold) }
        }
    }
}
