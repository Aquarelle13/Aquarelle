package com.psp.shifthelper.ui.manage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.OcrTemplate
import com.psp.shifthelper.ui.auto.OcrUiState
import com.psp.shifthelper.ui.auto.OcrViewModel
import com.psp.shifthelper.ui.theme.*

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.psp.shifthelper.data.model.EquipmentAlias
import com.psp.shifthelper.ui.components.OcrImagePicker
import com.psp.shifthelper.ui.home.HomeViewModel

@Composable
fun OcrSettingsContent(
    ocrViewModel: OcrViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val allEquipments by homeViewModel.equipments.collectAsState()
    val templates by ocrViewModel.templates.collectAsState()
    val uiState by ocrViewModel.uiState.collectAsState()
    
    var showRegistrationDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var editingTemplateId by remember { mutableLongStateOf(0L) }
    
    // 등록 중인 아이템 목록
    val registrationItems = remember { mutableStateListOf<RegistrationItem>() }
    var showTemplateNameDialog by remember { mutableStateOf(false) }
    var newTemplateName by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            ocrViewModel.processImage(it)
            registrationItems.clear()
            editingTemplateId = 0L
            newTemplateName = ""
            showRegistrationDialog = true
        }
    }

    // OCR 성공 시 자동 목록 추가 (SideEffect 사용)
    LaunchedEffect(uiState) {
        if (showRegistrationDialog && uiState is OcrUiState.Success && registrationItems.isEmpty()) {
            val result = (uiState as OcrUiState.Success).result
            // y축 순서로 정렬하여 인식된 모든 텍스트 라인을 자동으로 리스트에 추가
            val sortedLines = result.allDetectedLines.sortedBy { it.boundingBox.top }
            sortedLines.forEach { line ->
                registrationItems.add(RegistrationItem(rawText = line.text))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionLabel("03", "장비 매칭 (OCR 학습)")
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("장비명 목록 인식 및 등록", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("스케줄표의 장비명 열(Column)만 포함되도록 크롭하거나, 장비명이 잘 보이게 촬영하여 등록하세요. 인식된 순서대로 장비가 매칭됩니다.", fontSize = 12.sp, color = MutedForeground)
                
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("새 장비 리스트 등록 (이미지 선택)")
                }
            }
        }
        
        Text("등록된 매칭 템플릿", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MutedForeground)
        
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (templates.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("등록된 템플릿이 없습니다.", color = MutedForeground)
                    }
                }
            } else {
                items(templates) { template ->
                    TemplateItem(
                        template = template,
                        onClick = {
                            editingTemplateId = template.id
                            newTemplateName = template.name
                            registrationItems.clear()
                            template.equipmentIds.forEach { id ->
                                val equip = allEquipments.find { it.id == id }
                                registrationItems.add(RegistrationItem(rawText = equip?.code ?: "Unknown", equipmentId = id))
                            }
                            showRegistrationDialog = true
                        },
                        onDelete = { ocrViewModel.deleteTemplate(template) }
                    )
                }
            }
        }
    }

    // 등록 다이얼로그
    if (showRegistrationDialog) {
        val result = (uiState as? OcrUiState.Success)?.result
        Dialog(
            onDismissRequest = { 
                showRegistrationDialog = false
                editingTemplateId = 0L
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Background) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (editingTemplateId == 0L) "매칭 리스트 작성" else "템플릿 수정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = { 
                                showRegistrationDialog = false
                                editingTemplateId = 0L
                            }) { Text("취소") }
                            Button(
                                onClick = { showTemplateNameDialog = true },
                                enabled = registrationItems.isNotEmpty(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                            ) { Text(if (editingTemplateId == 0L) "저장" else "수정 완료") }
                        }
                    }
                    
                    Row(modifier = Modifier.weight(1f)) {
                        // 왼쪽: 이미지 피커
                        Box(modifier = Modifier.weight(1f)) {
                            if (result != null) {
                                OcrImagePicker(
                                    title = "이미지에서 장비명 선택",
                                    imageUri = selectedImageUri,
                                    result = result,
                                    onPicked = { text ->
                                        if (!registrationItems.any { it.rawText == text }) {
                                            registrationItems.add(RegistrationItem(rawText = text))
                                        }
                                    },
                                    onCancel = { showRegistrationDialog = false }
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Default.ImageNotSupported, null, modifier = Modifier.size(48.dp), tint = MutedForeground)
                                        Text("이미지 분석 데이터가 없습니다.", color = MutedForeground)
                                        Text("목록 편집만 가능합니다.", fontSize = 12.sp, color = MutedForeground)
                                    }
                                }
                            }
                        }
                        
                        // 오른쪽: 매칭 리스트
                        Column(modifier = Modifier.width(220.dp).fillMaxHeight().background(Surface).padding(8.dp)) {
                            Text("매칭 목록 (순서대로 인식됨)", fontSize = 12.sp, color = MutedForeground, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            
                            val matchedIds = registrationItems.mapNotNull { it.equipmentId }.toSet()
                            
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(registrationItems) { index, item ->
                                    MatchingItemRow(
                                        index = index,
                                        item = item,
                                        allEquipments = allEquipments,
                                        excludeIds = matchedIds,
                                        onRemove = { registrationItems.removeAt(index) },
                                        onEquipSelected = { id -> 
                                            registrationItems[index] = item.copy(equipmentId = id)
                                            // Alias도 즉시 학습
                                            ocrViewModel.learnAlias(item.rawText, id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTemplateNameDialog) {
        AlertDialog(
            onDismissRequest = { showTemplateNameDialog = false },
            title = { Text("템플릿 저장") },
            text = {
                TextField(
                    value = newTemplateName,
                    onValueChange = { newTemplateName = it },
                    placeholder = { Text("템플릿 이름을 입력하세요") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val ids = registrationItems.mapNotNull { it.equipmentId }
                    if (ids.isNotEmpty()) {
                        ocrViewModel.saveTemplate(newTemplateName, ids, editingTemplateId)
                        showTemplateNameDialog = false
                        showRegistrationDialog = false
                        newTemplateName = ""
                        editingTemplateId = 0L
                    }
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = { showTemplateNameDialog = false }) { Text("취소") }
            }
        )
    }
}

data class RegistrationItem(
    val rawText: String,
    val equipmentId: Long? = null
)

@Composable
fun MatchingItemRow(
    index: Int,
    item: RegistrationItem,
    allEquipments: List<Equipment>,
    excludeIds: Set<Long>,
    onRemove: () -> Unit,
    onEquipSelected: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedEquip = allEquipments.find { it.id == item.equipmentId }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${index + 1}. ${item.rawText}", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp).clickable { onRemove() }, tint = MutedForeground)
            }
            
            Button(
                onClick = { showPicker = true },
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (selectedEquip != null) StatusOk.copy(alpha = 0.1f) else Border, contentColor = if (selectedEquip != null) StatusOk else MutedForeground)
            ) {
                Text(selectedEquip?.code ?: "장비 매칭", fontSize = 11.sp)
            }
        }
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = Surface) {
                Column(modifier = Modifier.padding(16.dp).heightIn(max = 500.dp)) {
                    Text("장비 선택 (알파벳/번호 순)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    
                    val filteredEquipments = remember(allEquipments, excludeIds, item.equipmentId) {
                        allEquipments.filter { it.id !in excludeIds || it.id == item.equipmentId }
                            .sortedBy { it.code }
                    }

                    LazyColumn {
                        items(filteredEquipments) { equip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEquipSelected(equip.id)
                                        showPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(if (equip.isRunning) StatusOk else Border, RoundedCornerShape(2.dp))
                                )
                                Text(
                                    text = equip.code,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Foreground
                                )
                                Text(
                                    text = "(${equip.name})",
                                    fontSize = 12.sp,
                                    color = MutedForeground
                                )
                            }
                            HorizontalDivider(color = Border.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateItem(template: OcrTemplate, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ListAlt, null, tint = AccentBlue)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(template.name, fontWeight = FontWeight.Bold)
                Text("장비 ${template.equipmentIds.size}개 등록됨", fontSize = 12.sp, color = MutedForeground)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, null, tint = StatusError)
            }
        }
    }
}
