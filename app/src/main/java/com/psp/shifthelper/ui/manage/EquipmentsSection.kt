package com.psp.shifthelper.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.ui.components.EquipmentMapCanvas
import com.psp.shifthelper.ui.home.HomeViewModel
import com.psp.shifthelper.ui.theme.*

@Composable
fun EquipmentsSection(
    viewModel: HomeViewModel = viewModel()
) {
    val equipments by viewModel.equipments.collectAsState()
    var selectedEquipment by remember { mutableStateOf<Equipment?>(null) }
    
    // 세트 장비 선택 모드 상태
    var isPickingSlaves by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("03", if (isPickingSlaves) "연관 장비 선택 중..." else "장비 설정 (맵에서 선택)")

        // 장비 맵
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Surface, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            val assignmentsMapping = remember(equipments, selectedEquipment, isPickingSlaves) {
                if (isPickingSlaves && selectedEquipment != null) {
                    equipments.filter { it.referenceEquipId == selectedEquipment!!.id }
                        .associate { it.id to "연관" }
                } else emptyMap()
            }

            EquipmentMapCanvas(
                equipments = equipments,
                selectedEquipmentId = selectedEquipment?.id,
                assignments = assignmentsMapping,
                onEquipmentClick = { equip ->
                    if (isPickingSlaves && selectedEquipment != null) {
                        // 세트 장비 선택 로직
                        if (equip.id == selectedEquipment!!.id) return@EquipmentMapCanvas
                        
                        val isAlreadySlave = equip.referenceEquipId == selectedEquipment!!.id
                        val newRefId = if (isAlreadySlave) null else selectedEquipment!!.id
                        viewModel.upsertEquipment(equip.copy(referenceEquipId = newRefId))
                    } else {
                        selectedEquipment = equip
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 선택된 장비 설정 창 (하단 고정)
        if (selectedEquipment != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                EquipmentSettingsContent(
                    equipment = equipments.find { it.id == selectedEquipment!!.id } ?: selectedEquipment!!,
                    isPickingSlaves = isPickingSlaves,
                    onUpdate = { viewModel.upsertEquipment(it) },
                    onTogglePicking = { isPickingSlaves = !isPickingSlaves },
                    onClose = { 
                        selectedEquipment = null
                        isPickingSlaves = false
                    }
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                color = Surface,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("설정할 장비를 맵에서 선택해주세요.", fontSize = 13.sp, color = MutedForeground)
                }
            }
        }
    }
}

@Composable
fun EquipmentSettingsContent(
    equipment: Equipment,
    isPickingSlaves: Boolean,
    onUpdate: (Equipment) -> Unit,
    onTogglePicking: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()), 
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(8.dp).background(if (equipment.isRunning) StatusOk else Border, RoundedCornerShape(2.dp)))
                Text(text = "${equipment.code} 설정", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Foreground)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Text("닫기", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // 1. 스케줄 별도 확인 장비
        SettingRow(
            title = "스케줄 별도 확인",
            description = "자동인식 불가 시 수동 확인 탭에 노출",
            checked = equipment.isManualCheck,
            onCheckedChange = { onUpdate(equipment.copy(isManualCheck = it)) }
        )

        // 2. 연속배정 제한 장비
        SettingRow(
            title = "동일 직원 연속배정 피하기",
            description = "최근 3일 내 동일 직원 배정 시 큰 감점 부여",
            checked = equipment.avoidConsecutive,
            onCheckedChange = { onUpdate(equipment.copy(avoidConsecutive = it)) }
        )

        // 3. 세트 장비 설정
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("세트 기준 장비로 설정", fontSize = 14.sp, color = Foreground, fontWeight = FontWeight.SemiBold)
                    Text("이 장비 배정 시 연관 장비도 동일인에게 배정", fontSize = 11.sp, color = MutedForeground)
                }
                Switch(
                    checked = equipment.isReference,
                    onCheckedChange = { onUpdate(equipment.copy(isReference = it)) },
                    modifier = Modifier.scale(0.8f)
                )
            }
            
            if (equipment.isReference) {
                Button(
                    onClick = onTogglePicking,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPickingSlaves) StatusOk else AccentBlue.copy(alpha = 0.1f),
                        contentColor = if (isPickingSlaves) Color.White else AccentBlue
                    ),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(if (isPickingSlaves) "선택 완료" else "+ 연관 장비 지정하기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = Foreground, fontWeight = FontWeight.SemiBold)
            Text(description, fontSize = 11.sp, color = MutedForeground)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = StatusOk),
            modifier = Modifier.scale(0.8f)
        )
    }
}
