package com.psp.shifthelper.ui.auto

import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.OcrTemplate
import com.psp.shifthelper.domain.OcrResult
import com.psp.shifthelper.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun OcrResultDialog(
    result: OcrResult,
    imageUri: Uri?,
    allEquipments: List<Equipment>,
    onDismiss: () -> Unit,
    onLearnAlias: (String, Long) -> Unit,
    onUpdateState: (Long, Boolean) -> Unit,
    initialTab: Int = 0,
    templates: List<OcrTemplate> = emptyList()
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Background
        ) {
            MainConfirmationContent(
                result = result,
                imageUri = imageUri,
                allEquipments = allEquipments,
                onDismiss = onDismiss,
                onLearnAlias = onLearnAlias,
                onUpdateState = onUpdateState,
                initialTab = initialTab,
                templates = templates
            )
        }
    }
}

@Composable
fun MainConfirmationContent(
    result: OcrResult,
    imageUri: Uri?,
    allEquipments: List<Equipment>,
    onDismiss: () -> Unit,
    onLearnAlias: (String, Long) -> Unit,
    onUpdateState: (Long, Boolean) -> Unit,
    initialTab: Int = 0,
    templates: List<OcrTemplate> = emptyList()
) {
    var selectedTabIndex by remember(initialTab) { mutableIntStateOf(initialTab) }
    val tabs = listOf("인덱스 매칭", "가동 판정", "템플릿 대조")

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("인식 결과 확인 및 수정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (result.shiftInfo != null) Text("이미지 인식 정보: ${result.shiftInfo}", fontSize = 12.sp, color = AccentBlue)
            }
            TextButton(onClick = onDismiss) { Text("닫기", color = AccentBlue) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = AccentBlue,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontSize = 13.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val sortedIds = result.equipmentStates.keys.sortedBy { id -> 
            allEquipments.find { it.id == id }?.displayOrder ?: 0
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (sortedIds.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("인식된 설비 데이터가 없습니다.", color = MutedForeground)
                }
            } else if (selectedTabIndex == 2) {
                // Tab 2: 템플릿 대조
                TemplateComparisonContent(result, templates, allEquipments)
            }

            sortedIds.forEach { id ->
                if (selectedTabIndex == 2) return@forEach // 템플릿 대조 탭에서는 별도 UI 사용

                val equip = allEquipments.find { it.id == id }
                val isRunning = result.equipmentStates[id] ?: true
                val region = result.boundingBoxes[id]
                val detail = result.details[id]
                val rawText = result.matchedLines[id]
                
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceElevated)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedTabIndex == 0) {
                            // Tab 0: 인덱스 매칭 (시각적 확인 위주)
                            if (imageUri != null && region != null) {
                                CropPreview(imageUri = imageUri, region = region, ocrResult = result)
                            }
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = equip?.code ?: "Unknown", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    if (rawText != null) {
                                        Text("인식된 텍스트: $rawText", fontSize = 12.sp, color = MutedForeground)
                                        TextButton(
                                            onClick = { onLearnAlias(rawText, id) },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Icon(Icons.Outlined.TouchApp, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("이 텍스트를 별칭으로 학습", fontSize = 10.sp, color = AccentBlue)
                                        }
                                    }
                                }
                            }
                        } else if (selectedTabIndex == 1) {
                            // Tab 1: 가동 판정 (판정 결과 및 근거 위주)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = equip?.code ?: "Unknown", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    if (detail?.partNumber != null) {
                                        Text("검출된 품번: ${detail.partNumber}", fontSize = 12.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                                    }
                                    if (rawText != null) {
                                        Text("판정 근거: $rawText", fontSize = 11.sp, color = MutedForeground)
                                    }
                                    if (detail?.memo != null) {
                                        Text(detail.memo, fontSize = 10.sp, color = MutedForeground.copy(alpha = 0.7f), lineHeight = 12.sp)
                                    }
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (isRunning) "가동" else "비가동", fontSize = 12.sp, color = if (isRunning) StatusOk else StatusError, fontWeight = FontWeight.Bold)
                                    Switch(
                                        checked = isRunning, 
                                        onCheckedChange = { onUpdateState(id, it) }, 
                                        colors = SwitchDefaults.colors(checkedTrackColor = StatusOk, uncheckedThumbColor = StatusError) 
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Button(
            onClick = onDismiss, 
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp), 
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StatusOk)
        ) { 
            Text("검토 및 수정 완료", fontWeight = FontWeight.Bold) 
        }
    }
}

@Composable
fun CropPreview(imageUri: Uri, region: Rect, ocrResult: OcrResult) {
    // 검은 화면 방지를 위해 ContentScale.Crop과 뷰포트 이동 방식 적용
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
    ) {
        AsyncImage(
            model = imageUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 원본 이미지 크기 대비 크롭 영역의 비율 계산
                    val imgW = ocrResult.imageWidth.toFloat().coerceAtLeast(1f)
                    val imgH = ocrResult.imageHeight.toFloat().coerceAtLeast(1f)
                    
                    // 뷰포트(90dp 높이)에 맞게 확대 배율 결정
                    val zoom = (size.width / region.width().toFloat().coerceAtLeast(1f)) * 0.7f
                    scaleX = zoom.coerceIn(1f, 15f)
                    scaleY = zoom.coerceIn(1f, 15f)
                    
                    // 해당 영역의 중심점을 뷰의 중앙으로 이동
                    translationX = (size.width / 2f) - (region.centerX().toFloat() * (size.width / imgW) * scaleX)
                    translationY = (size.height / 2f) - (region.centerY().toFloat() * (size.height / imgH) * scaleY)
                    
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                },
            contentScale = ContentScale.FillBounds // 전체를 꽉 채운 후 레이어로 조절
        )
        Box(modifier = Modifier.fillMaxSize().border(1.dp, AccentBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp)))
    }
}

@Composable
fun TemplateComparisonContent(
    result: OcrResult,
    templates: List<OcrTemplate>,
    allEquipments: List<Equipment>
) {
    val matchedIds = result.matchedLines.keys.toSet()

    if (templates.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("등록된 OCR 템플릿이 없습니다.\n설정 탭에서 템플릿을 먼저 등록해주세요.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MutedForeground, fontSize = 13.sp)
        }
    } else {
        templates.forEach { template ->
            val templateIds = template.equipmentIds.toSet()
            val intersection = matchedIds.intersect(templateIds)
            val missing = templateIds - matchedIds
            val extra = matchedIds - templateIds
            val matchRate = if (templateIds.isNotEmpty()) (intersection.size.toFloat() / templateIds.size * 100).toInt() else 0

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = if (matchRate == 100) StatusOk.copy(alpha = 0.1f) else SurfaceElevated)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(template.name, fontWeight = FontWeight.Bold, color = Foreground)
                        Text("일치율: $matchRate%", color = if (matchRate == 100) StatusOk else AccentBlue, fontWeight = FontWeight.Bold)
                    }

                    if (missing.isNotEmpty()) {
                        Text("미인식 (${missing.size}):", fontSize = 11.sp, color = StatusError, fontWeight = FontWeight.Bold)
                        Text(
                            text = missing.mapNotNull { id -> allEquipments.find { it.id == id }?.code }.joinToString(", "),
                            fontSize = 10.sp, color = MutedForeground
                        )
                    }

                    if (extra.isNotEmpty()) {
                        Text("템플릿 외 인식 (${extra.size}):", fontSize = 11.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                        Text(
                            text = extra.mapNotNull { id -> allEquipments.find { it.id == id }?.code }.joinToString(", "),
                            fontSize = 10.sp, color = MutedForeground
                        )
                    }

                    if (matchRate == 100 && extra.isEmpty()) {
                        Text("템플릿과 완벽히 일치합니다.", fontSize = 11.sp, color = StatusOk)
                    }
                }
            }
        }
    }
}


