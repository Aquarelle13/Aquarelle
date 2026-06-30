package com.psp.shifthelper.domain

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.EquipmentAlias
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

data class OcrLine(
    val text: String,
    val boundingBox: Rect
)

data class OcrDetail(
    val isRunning: Boolean,
    val partNumber: String? = null,
    val memo: String? = null
)

data class OcrResult(
    val rawText: String,
    val date: String? = null,
    val shiftInfo: String? = null,
    val equipmentStates: Map<Long, Boolean>,
    val details: Map<Long, OcrDetail>,
    val dayStates: Map<Long, Boolean>,
    val nightStates: Map<Long, Boolean>,
    val uncertainEquipments: Set<Long>,
    val matchedLines: Map<Long, String>,
    val boundingBoxes: Map<Long, Rect>,
    val allDetectedLines: List<OcrLine>,
    val imageWidth: Int,
    val imageHeight: Int,
    val accuracy: Float,
    val recognizedDates: List<String> = emptyList(),
    val recognizedShifts: List<String> = emptyList(),
    val multiDateResults: Map<String, Map<String, Map<Long, Boolean>>> = emptyMap()
)

class OcrService(private val context: Context) {

    private val recognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    suspend fun processImage(
        uri: Uri,
        allEquipments: List<Equipment>,
        aliases: List<EquipmentAlias> = emptyList(),
        templates: List<com.psp.shifthelper.data.model.OcrTemplate> = emptyList(),
        targetDate: String? = null,
        targetShift: String = "주간"
    ): OcrResult = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)

        val visionText = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

        val allLines = mutableListOf<OcrLine>()
        visionText.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.boundingBox?.let { allLines.add(OcrLine(line.text, it)) }
            }
        }

        // 1. 단순화된 행(Row) 그룹화: 줄바꿈된 텍스트를 하나의 행으로 병합
        val rows = groupLinesIntoRows(allLines)
        if (rows.isEmpty()) return@withContext createEmptyResult(visionText.text, image.width, image.height)

        // 2. 날짜 행 식별
        var dateRowIndex = -1
        val dateAnchors = mutableListOf<Anchor>()
        for (i in 0 until minOf(rows.size, 5)) {
            val row = rows[i]
            val anchors = extractDateAnchors(row)
            if (anchors.isNotEmpty()) {
                dateRowIndex = i
                dateAnchors.addAll(anchors.sortedBy { it.xCenter })
                break
            }
        }

        if (dateRowIndex == -1) return@withContext createEmptyResult(visionText.text, image.width, image.height)

        // 3. 쉬프트 행 식별 (날짜 행 바로 아래)
        val shiftRowIndex = dateRowIndex + 1
        val shiftRow = if (shiftRowIndex < rows.size) rows[shiftRowIndex] else emptyList()
        val shiftAnchors = extractShiftAnchors(shiftRow).sortedBy { it.xCenter }

        // 컬럼 경계 정의: 중앙 정렬 특성 활용
        val dateColumns = defineColumns(dateAnchors, image.width)
        val shiftColumns = defineColumns(shiftAnchors, image.width)

        val multiResults = mutableMapOf<String, MutableMap<String, MutableMap<Long, Boolean>>>()
        val boundingBoxes = mutableMapOf<Long, Rect>()
        val matchedLines = mutableMapOf<Long, String>()
        val uncertainIds = allEquipments.map { it.id }.toMutableSet()

        // 4. 장비 데이터 추출: 3번째 행(dateRowIndex + 2)부터 순서대로 매칭
        val equipRows = rows.drop(dateRowIndex + 2)
        val activeTemplate = templates.firstOrNull()

        if (activeTemplate != null) {
            activeTemplate.equipmentIds.forEachIndexed { index, equipId ->
                if (index < equipRows.size) {
                    val row = equipRows[index]
                    
                    // 첫 번째 열 (장비명) - 날짜 첫 컬럼의 시작점 이전 텍스트들
                    val firstColXLimit = dateColumns.firstOrNull()?.start ?: (image.width / 4)
                    val nameLines = row.filter { it.boundingBox.centerX() < firstColXLimit }
                    if (nameLines.isNotEmpty()) {
                        uncertainIds.remove(equipId)
                        boundingBoxes[equipId] = nameLines.first().boundingBox
                        matchedLines[equipId] = nameLines.joinToString(" ") { it.text }
                    }

                    // 각 날짜/쉬프트 셀 데이터 추출
                    dateColumns.forEach { dc ->
                        val dateRes = multiResults.getOrPut(dc.text) { mutableMapOf() }
                        
                        // 현재 날짜 범위에 포함되는 쉬프트 컬럼들
                        val scs = shiftColumns.filter { it.xCenter in dc.start..dc.end }
                        
                        if (scs.isNotEmpty()) {
                            scs.forEach { sc ->
                                val cellTxt = row.filter { it.boundingBox.centerX() in sc.start..sc.end }.joinToString("\n") { it.text }
                                dateRes.getOrPut(sc.text) { mutableMapOf() }[equipId] = parseCellStatus(cellTxt).first.isRunning
                            }
                        } else {
                            // 쉬프트 표시가 없으면 날짜 칸 전체를 하나의 데이터로
                            val cellTxt = row.filter { it.boundingBox.centerX() in dc.start..dc.end }.joinToString("\n") { it.text }
                            val (d, n) = parseCellStatus(cellTxt)
                            dateRes.getOrPut("주간") { mutableMapOf() }[equipId] = d.isRunning
                            dateRes.getOrPut("야간") { mutableMapOf() }[equipId] = n.isRunning
                        }
                    }
                }
            }
        }

        val finalDate = targetDate ?: dateAnchors.firstOrNull()?.text
        val dData = multiResults[finalDate]
        val sk = dData?.keys?.find { it.contains(targetShift) } ?: dData?.keys?.firstOrNull()
        val finalStates = dData?.get(sk) ?: emptyMap()

        OcrResult(
            rawText = visionText.text,
            date = finalDate,
            equipmentStates = finalStates,
            details = emptyMap(),
            dayStates = emptyMap(),
            nightStates = emptyMap(),
            uncertainEquipments = uncertainIds,
            matchedLines = matchedLines,
            boundingBoxes = boundingBoxes,
            allDetectedLines = allLines,
            imageWidth = image.width,
            imageHeight = image.height,
            accuracy = 0.95f,
            recognizedDates = dateAnchors.map { it.text },
            recognizedShifts = shiftAnchors.map { it.text }.distinct(),
            multiDateResults = multiResults
        )
    }

    // 텍스트 간격을 분석하여 행을 그룹화 (줄바꿈 병합 포함)
    private fun groupLinesIntoRows(lines: List<OcrLine>): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedBy { it.boundingBox.top }
        val rows = mutableListOf<MutableList<OcrLine>>()
        
        var curRow = mutableListOf(sorted[0])
        rows.add(curRow)
        
        for (i in 1 until sorted.size) {
            val lastLine = curRow.maxBy { it.boundingBox.bottom }
            val curLine = sorted[i]
            
            // 수직 간격이 글자 높이의 70% 이내면 같은 행(줄바꿈)으로 간주
            val gap = curLine.boundingBox.top - lastLine.boundingBox.bottom
            val rowHeight = curRow.map { it.boundingBox.height() }.average()
            
            if (gap < rowHeight * 0.7) {
                curRow.add(curLine)
            } else {
                curRow = mutableListOf(curLine)
                rows.add(curRow)
            }
        }
        return rows.map { it.sortedBy { l -> l.boundingBox.left } }
    }

    // 중앙 좌표를 기준으로 컬럼 영역 정의
    private fun defineColumns(anchors: List<Anchor>, imgWidth: Int): List<ColumnRange> {
        if (anchors.isEmpty()) return emptyList()
        val sorted = anchors.sortedBy { it.xCenter }
        return sorted.mapIndexed { i, a ->
            val start = if (i == 0) a.xCenter - 50 else (sorted[i-1].xCenter + a.xCenter) / 2
            val end = if (i == sorted.size - 1) imgWidth else (a.xCenter + sorted[i+1].xCenter) / 2
            ColumnRange(a.text, start, end, a.xCenter)
        }
    }

    private fun extractDateAnchors(row: List<OcrLine>): List<Anchor> {
        val pattern = Regex("(\\d{1,2})[./\\-월]\\s?(\\d{1,2})")
        return row.flatMap { line ->
            pattern.findAll(line.text).map { m ->
                val charW = line.boundingBox.width().toFloat() / line.text.length.coerceAtLeast(1)
                val centerX = line.boundingBox.left + ((m.range.first + m.range.last) / 2.0 * charW).toInt()
                Anchor("${m.groupValues[1].padStart(2, '0')}.${m.groupValues[2].padStart(2, '0')}", centerX)
            }
        }
    }

    private fun extractShiftAnchors(row: List<OcrLine>): List<Anchor> {
        val pattern = Regex("(주간|야간|주|야)\\s?([A-D])?")
        return row.flatMap { line ->
            pattern.findAll(line.text).map { m ->
                var txt = m.value
                if (txt.startsWith("주") && !txt.startsWith("주간")) txt = txt.replaceFirst("주", "주간")
                else if (txt.startsWith("야") && !txt.startsWith("야간")) txt = txt.replaceFirst("야", "야간")
                
                val charW = line.boundingBox.width().toFloat() / line.text.length.coerceAtLeast(1)
                val centerX = line.boundingBox.left + ((m.range.first + m.range.last) / 2.0 * charW).toInt()
                Anchor(txt, centerX)
            }
        }
    }

    private fun parseCellStatus(text: String): Pair<Status, Status> {
        val pNumRegex = Regex("\\d{5,}-\\d+|\\d{7,}")
        fun getSt(s: String): Status {
            val c = s.replace(" ", "")
            val off = c.contains("X", true) || c.contains("비가동") || c.contains("정지")
            val pn = pNumRegex.find(c)?.value
            return Status(!off && (pn != null || c.length > 1), pn, if (s.length > 10) s else null)
        }
        return if (text.contains("/")) {
            val parts = text.split("/")
            Pair(getSt(parts[0]), getSt(if (parts.size > 1) parts[1] else parts[0]))
        } else {
            val st = getSt(text)
            Pair(st, st)
        }
    }

    private fun createEmptyResult(raw: String, w: Int, h: Int) = OcrResult(raw, equipmentStates = emptyMap(), details = emptyMap(), dayStates = emptyMap(), nightStates = emptyMap(), uncertainEquipments = emptySet(), matchedLines = emptyMap(), boundingBoxes = emptyMap(), allDetectedLines = emptyList(), imageWidth = w, imageHeight = h, accuracy = 0f)
    
    data class Anchor(val text: String, val xCenter: Int)
    data class ColumnRange(val text: String, val start: Int, val end: Int, val xCenter: Int)
    data class Status(val isRunning: Boolean, val partNumber: String?, val memo: String?)
}
