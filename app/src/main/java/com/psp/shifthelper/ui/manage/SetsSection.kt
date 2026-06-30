package com.psp.shifthelper.ui.manage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psp.shifthelper.data.model.EquipmentSet
import com.psp.shifthelper.ui.home.HomeViewModel
import com.psp.shifthelper.ui.theme.*

@Composable
fun SetsSection(
    manageViewModel: ManageViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val equipmentSets by manageViewModel.equipmentSets.collectAsState()
    val allEquipments by homeViewModel.equipments.collectAsState()
    
    var showDialog by remember { mutableStateOf(false) }
    var selectedSet by remember { mutableStateOf<EquipmentSet?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionLabel("04", "EQUIPMENT SETS")

            if (equipmentSets.isEmpty()) {
                Text(
                    text = "등록된 세트가 없습니다.",
                    fontSize = 12.sp,
                    color = MutedForeground
                )
            } else {
                equipmentSets.forEach { set ->
                    // ID 리스트를 장비 코드 리스트로 변환
                    val equipCodes = set.equipmentIds.map { id ->
                        allEquipments.find { it.id == id }?.code ?: "Unknown"
                    }
                    
                    SetRow(
                        name = set.name,
                        equipments = equipCodes,
                        onEdit = {
                            selectedSet = set
                            showDialog = true
                        },
                        onDelete = { manageViewModel.deleteEquipmentSet(set) }
                    )
                    HorizontalDivider(
                        color = Border.copy(alpha = 0.5f),
                        thickness = 0.5.dp
                    )
                }
            }

            // 세트 추가 버튼
            OutlinedButton(
                onClick = {
                    selectedSet = null
                    showDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = Border
                )
            ) {
                Text(
                    text = "+ 새 세트 추가",
                    color = AccentBlue,
                    fontSize = 12.sp
                )
            }
        }
    }

    if (showDialog) {
        SetDialog(
            equipmentSet = selectedSet,
            allEquipments = allEquipments,
            onDismiss = { showDialog = false },
            onConfirm = { set ->
                manageViewModel.upsertEquipmentSet(set)
                showDialog = false
            }
        )
    }
}

@Composable
fun SetRow(
    name: String,
    equipments: List<String>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                color = Foreground,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                equipments.forEach { equip ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SurfaceElevated
                    ) {
                        Text(
                            text = equip,
                            modifier = Modifier.padding(
                                horizontal = 6.dp,
                                vertical = 2.dp
                            ),
                            fontSize = 11.sp,
                            color = MutedForeground
                        )
                    }
                }
            }
        }
        Row {
            IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "수정", tint = MutedForeground, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "삭제", tint = StatusError.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDialog(
    equipmentSet: EquipmentSet?,
    allEquipments: List<com.psp.shifthelper.data.model.Equipment>,
    onDismiss: () -> Unit,
    onConfirm: (EquipmentSet) -> Unit
) {
    var name by remember { mutableStateOf(equipmentSet?.name ?: "") }
    val selectedIds = remember { mutableStateListOf<Long>().apply { 
        equipmentSet?.equipmentIds?.let { addAll(it) } 
    } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (equipmentSet == null) "세트 추가" else "세트 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("세트 이름") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(text = "포함할 장비 선택", fontSize = 12.sp, color = MutedForeground)
                
                Column(
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .fillMaxWidth()
                ) {
                    allEquipments.forEach { equip ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(equip.id),
                                onCheckedChange = { checked ->
                                    if (checked) selectedIds.add(equip.id)
                                    else selectedIds.remove(equip.id)
                                }
                            )
                            Text(text = equip.code, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    EquipmentSet(
                        id = equipmentSet?.id ?: 0L,
                        name = name,
                        equipmentIds = selectedIds.toList()
                    )
                )
            }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
