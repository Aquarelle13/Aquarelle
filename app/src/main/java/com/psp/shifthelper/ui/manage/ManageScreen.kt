package com.psp.shifthelper.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psp.shifthelper.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("근무조 / 인원 관리", "장비 관리", "OCR 설정")
    val icons = listOf(Icons.Default.People, Icons.Default.Build, Icons.Default.Settings)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // 상단 타이틀
        Text(
            text = "MANAGE",
            modifier = Modifier.padding(16.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Foreground
        )

        // 상단 탭 개편 (Material3 PrimaryTabRow 사용)
        PrimaryTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Surface,
            contentColor = AccentBlue,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                    color = AccentBlue
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(icons[index], contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(title, fontSize = 13.sp, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            when (selectedTabIndex) {
                0 -> WorkerManagementContent()
                1 -> EquipmentManagementContent()
                2 -> OcrSettingsContent()
            }
        }
    }
}

@Composable
fun WorkerManagementContent() {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GroupSection()
        MembersSection()
        PolicySection() // 정책은 인원 관리 탭 하단에 배치
    }
}

@Composable
fun EquipmentManagementContent() {
    // 장비 관리는 맵을 사용하므로 전체 공간 사용
    Box(modifier = Modifier.fillMaxSize()) {
        EquipmentsSection() 
    }
}

@Composable
fun SectionLabel(number: String, title: String) {
    Text(
        text = "$number · $title",
        fontSize = 11.sp,
        color = MutedForeground,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )
}
