package com.psp.shifthelper.ui.auto

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.psp.shifthelper.data.model.OcrTemplate
import com.psp.shifthelper.domain.OcrResult
import com.psp.shifthelper.ui.home.HomeViewModel
import com.psp.shifthelper.ui.theme.*

@Composable
fun OcrSection(
    selectedDate: String? = null,
    selectedShift: String,
    onApplyToAssign: (OcrResult) -> Unit,
    ocrViewModel: OcrViewModel = viewModel(),
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by ocrViewModel.uiState.collectAsState()
    val selectedUri by ocrViewModel.selectedImageUri.collectAsState()
    val allEquipments by homeViewModel.equipments.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showResultDialog by remember { mutableStateOf(false) }
    val templates by ocrViewModel.templates.collectAsState()

    // 갤러리 런처 (날짜와 쉬프트 전달)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { ocrViewModel.processImage(it, selectedDate, selectedShift) } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("02", "스케줄 이미지 인식")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, Border, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedUri != null) {
                            AsyncImage(model = selectedUri, contentDescription = null, contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Outlined.DateRange, contentDescription = null, tint = MutedForeground)
                        }
                    }

                    Column {
                        when (val state = uiState) {
                            is OcrUiState.Idle -> Text("스케줄표 이미지를 선택하세요", fontSize = 13.sp, color = MutedForeground)
                            is OcrUiState.Loading -> Text("데이터 분석 중...", fontSize = 13.sp, color = MutedForeground)
                            is OcrUiState.Success -> {
                                Text("분석 완료", fontSize = 13.sp, color = StatusOk, fontWeight = FontWeight.Bold)
                                Text("매칭된 장비: ${state.result.matchedLines.size}개", fontSize = 11.sp, color = MutedForeground)
                            }
                            is OcrUiState.Error -> Text("오류 발생", fontSize = 13.sp, color = StatusError)
                        }
                    }
                }

                if (uiState is OcrUiState.Success) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { showResultDialog = true }
                        ) {
                            Text("상세확인", fontSize = 11.sp, color = AccentBlue)
                        }
                        TextButton(
                            onClick = { galleryLauncher.launch("image/*") }
                        ) {
                            Text("이미지 변경", fontSize = 11.sp, color = AccentBlue)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = uiState is OcrUiState.Success) {
                // 통합된 AutoAssignScreen에서 컨트롤하므로 여기 버튼들은 제거
            }

            if (uiState !is OcrUiState.Success) {
                Button(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = Foreground)
                ) {
                    Icon(Icons.Outlined.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("갤러리에서 스케줄 선택", fontSize = 12.sp)
                }
            }
        }
    }

    if (showResultDialog && uiState is OcrUiState.Success) {
        OcrResultDialog(
            result = (uiState as OcrUiState.Success).result,
            imageUri = selectedUri,
            allEquipments = allEquipments,
            onDismiss = { showResultDialog = false },
            onLearnAlias = { raw, id -> ocrViewModel.learnAlias(raw, id) },
            onUpdateState = { id, run -> ocrViewModel.updateManualState(id, run) },
            initialTab = 0, // 인덱스 매칭 탭으로 시작
            templates = templates
        )
    }
}
