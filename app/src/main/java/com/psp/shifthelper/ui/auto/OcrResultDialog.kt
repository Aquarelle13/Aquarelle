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
import com.psp.shifthelper.domain.OcrResult
import com.psp.shifthelper.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun OcrResultDialog(
    result: OcrResult,
    imageUri: Uri?,
    allEquipments: List<Equipment>,
    onDismiss: () -> Unit,
    onUpdateState: (Long, Boolean) -> Unit
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
                onUpdateState = onUpdateState
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
    onUpdateState: (Long, Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("인식 결과 확인 및 수정", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (result.shiftInfo != null) Text("이미지 인식 정보: ${result.shiftInfo}", fontSize = 12.sp, color = AccentBlue)
            }
            TextButton(onClick = onDismiss) { Text("닫기", color = AccentBlue) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("가동 판정 및 근거 확인", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
            result.equipmentStates.keys.sorted().forEach { id ->
                val equip = allEquipments.find { it.id == id }
                val isRunning = result.equipmentStates[id] ?: true
                val region = result.boundingBoxes[id]
                val detail = result.details[id]
                
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SurfaceElevated)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (imageUri != null && region != null) {
                            CropPreview(imageUri = imageUri, region = region, ocrResult = result)
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = equip?.code ?: "Unknown", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                if (detail?.partNumber != null) Text("품번: ${detail.partNumber}", fontSize = 12.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                                if (detail?.memo != null) Text(detail.memo, fontSize = 10.sp, color = MutedForeground, lineHeight = 12.sp)
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { onUpdateState(id, false) }) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = if (!isRunning) StatusError else MutedForeground.copy(alpha = 0.3f))
                                }
                                Switch(checked = isRunning, onCheckedChange = { onUpdateState(id, it) }, colors = SwitchDefaults.colors(checkedTrackColor = StatusOk))
                            }
                        }
                    }
                }
            }
        }
        
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), shape = RoundedCornerShape(8.dp)) { Text("수정 완료 및 저장") }
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
            .background(Color.Black)
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


