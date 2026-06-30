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
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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
    // 다중 날짜/쉬프트 대응
    val recognizedDates: List<String> = emptyList(),
    val recognizedShifts: List<String> = emptyList(),
    val multiDateResults: Map<String, Map<String, Map<Long, Boolean>>> = emptyMap() // Date -> Shift -> States
)

class OcrService(private val context: Context) {

    private val recognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    private var tfliteInterpreter: Interpreter? = null

    init {
        try {
            val options = Interpreter.Options()
            // GPU delegate는 런타임에 라이브러리가 없을 수 있으므로 Throwable로 안전하게 감싼다
            try {
                options.addDelegate(GpuDelegate())
            } catch (t: Throwable) {
                // GPU delegate 클래스가 없거나 초기화 실패 시 CPU fallback
                // 필요하면 로그 남기기:
                t.printStackTrace()
            }

            // 모델 파일이 assets에 있다고 가정 (예: table_detector.tflite)
            // loadModelFile("table_detector.tflite")?.let {
            //     tfliteInterpreter = Interpreter(it, options)
            // }
        } catch (t: Throwable) {
            // 기타 초기화 실패 방지
            t.printStackTrace()
        }
    }

    private fun loadModelFile(modelPath: String): ByteBuffer? {
        return try {
            val fileDescriptor = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            null
        }
    }

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

        // 1. 앵커 기반 행(Row) 그룹화
        val rows = groupLinesIntoRows(allLines)
        if (rows.size < 3) return@withContext createEmptyResult(visionText.text, image.width, image.height)

        // 2. 날짜 앵커 추출 (1st Row)
        val dateAnchors = extractDateAnchors(rows[0])
        val recognizedDates = dateAnchors.map { it.text }
        
        // 3. 쉬프트/팀 앵커 추출 (2nd Row) - 날짜 앵커와 X축 매칭
        val shiftAnchors = extractShiftAnchors(rows[1])
        val dateShiftMap = mutableMapOf<String, List<String>>()
        
        dateAnchors.forEach { dateAnchor ->
            val matchingShifts = shiftAnchors.filter { it.xRange.intersect(dateAnchor.xRange).isNotEmpty() || 
                (it.xRange.first in dateAnchor.xRange) || (it.xRange.last in dateAnchor.xRange) }
            dateShiftMap[dateAnchor.text] = matchingShifts.map { it.text }
        }

        val multiDateResults = mutableMapOf<String, MutableMap<String, MutableMap<Long, Boolean>>>()
        val boundingBoxes = mutableMapOf<Long, Rect>()
        val matchedLines = mutableMapOf<Long, String>()
        val uncertainEquipments = allEquipments.map { it.id }.toMutableSet()

        // 4. 장비 매칭 및 데이터 추출 (3rd Row부터)
        val equipRows = rows.drop(2)
        val activeTemplate = templates.firstOrNull() 
        
        equipRows.forEachIndexed { rowIndex, row ->
            val equipLine = row.minByOrNull { it.boundingBox.left } ?: return@forEachIndexed
            
            val matchedEquipId = if (activeTemplate != null && rowIndex < activeTemplate.equipmentIds.size) {
                activeTemplate.equipmentIds[rowIndex]
            } else {
                allEquipments.find { equip ->
                    val hasAlias = aliases.filter { it.equipmentId == equip.id }.any { equipLine.text.contains(it.rawText, ignoreCase = true) }
                    hasAlias || equipLine.text.contains(equip.code, ignoreCase = true)
                }?.id
            }

            if (matchedEquipId != null) {
                uncertainEquipments.remove(matchedEquipId)
                boundingBoxes[matchedEquipId] = equipLine.boundingBox
                
                dateAnchors.forEach { dateAnchor ->
                    val dateResult = multiDateResults.getOrPut(dateAnchor.text) { mutableMapOf() }
                    val shifts = dateShiftMap[dateAnchor.text]?.ifEmpty { listOf("주간", "야간") } ?: listOf("주간", "야간")
                    
                    val cellLine = row.find { it.boundingBox.centerX() in dateAnchor.xRange }
                    val cellText = cellLine?.text ?: ""
                    
                    val (dayStatus, nightStatus) = parseCellStatus(cellText)
                    
                    shifts.forEach { shiftInfo ->
                        val isDay = shiftInfo.contains("주간")
                        val shiftResult = dateResult.getOrPut(shiftInfo) { mutableMapOf() }
                        shiftResult[matchedEquipId] = if (isDay) dayStatus.isRunning else nightStatus.isRunning
                    }
                }
            }
        }

        val finalTargetDate = targetDate ?: recognizedDates.firstOrNull()
        val finalTargetShift = targetShift
        
        val currentStates = if (finalTargetDate != null) {
            val dateData = multiDateResults[finalTargetDate]
            val shiftKey = dateData?.keys?.find { it.contains(finalTargetShift) } ?: dateData?.keys?.firstOrNull()
            dateData?.get(shiftKey) ?: emptyMap()
        } else emptyMap()

        OcrResult(
            rawText = visionText.text,
            date = finalTargetDate,
            shiftInfo = null,
            equipmentStates = currentStates,
            details = emptyMap(),
            dayStates = emptyMap(),
            nightStates = emptyMap(),
            uncertainEquipments = uncertainEquipments,
            matchedLines = matchedLines,
            boundingBoxes = boundingBoxes,
            allDetectedLines = allLines,
            imageWidth = image.width,
            imageHeight = image.height,
            accuracy = 0.9f,
            recognizedDates = recognizedDates,
            recognizedShifts = dateShiftMap.values.flatten().distinct(),
            multiDateResults = multiDateResults
        )
    }

    private fun createEmptyResult(rawText: String, w: Int, h: Int) = OcrResult(
        rawText = rawText,
        equipmentStates = emptyMap(),
        details = emptyMap(),
        dayStates = emptyMap(),
        nightStates = emptyMap(),
        uncertainEquipments = emptySet(),
        matchedLines = emptyMap(),
        boundingBoxes = emptyMap(),
        allDetectedLines = emptyList(),
        imageWidth = w,
        imageHeight = h,
        accuracy = 0f
    )

    data class Anchor(val text: String, val xRange: IntRange)

    private fun extractDateAnchors(row: List<OcrLine>): List<Anchor> {
        val datePattern = Regex("(\\d{1,2})[./월]\\s?(\\d{1,2})")
        return row.mapNotNull { line ->
            datePattern.find(line.text)?.let {
                val month = it.groupValues[1].padStart(2, '0')
                val day = it.groupValues[2].padStart(2, '0')
                Anchor("$month.$day", line.boundingBox.left..line.boundingBox.right)
            }
        }
    }

    private fun extractShiftAnchors(row: List<OcrLine>): List<Anchor> {
        val shiftPattern = Regex("(주간|야간)\\s?([A-D])?")
        return row.mapNotNull { line ->
            shiftPattern.find(line.text)?.let { 
                Anchor(it.value, line.boundingBox.left..line.boundingBox.right)
            }
        }
    }

    private fun groupLinesIntoRows(lines: List<OcrLine>): List<List<OcrLine>> {
        if (lines.isEmpty()) return emptyList()
        val sortedLines = lines.sortedBy { it.boundingBox.top }
        val rows = mutableListOf<MutableList<OcrLine>>()
        
        var currentY = sortedLines[0].boundingBox.centerY()
        var currentRow = mutableListOf(sortedLines[0])
        rows.add(currentRow)
        
        for (i in 1 until sortedLines.size) {
            val line = sortedLines[i]
            // 행 높이의 70% 이내면 같은 행으로 간주
            if (abs(line.boundingBox.centerY() - currentY) < line.boundingBox.height() * 0.7) {
                currentRow.add(line)
            } else {
                currentRow = mutableListOf(line)
                rows.add(currentRow)
                currentY = line.boundingBox.centerY()
            }
        }
        return rows.map { it.sortedBy { line -> line.boundingBox.left } }
    }


    private fun parseCellStatus(text: String): Pair<Status, Status> {
        val partNumPattern = Regex("\\d{5,}-\\d+|\\d{7,}")
        
        fun getStatus(segment: String): Status {
            val clean = segment.replace(" ", "")
            val isOff = clean.contains("X", ignoreCase = true) || clean.contains("비가동") || clean.contains("정지")
            val partNumber = partNumPattern.find(clean)?.value
            val isRunning = !isOff && (partNumber != null || clean.length > 2)
            return Status(isRunning, partNumber, if (segment.length > 10) segment else null)
        }

        return if (text.contains("/")) {
            val parts = text.split("/")
            val day = getStatus(parts[0])
            val night = if (parts.size > 1) getStatus(parts[1]) else day
            Pair(day, night)
        } else {
            val status = getStatus(text)
            Pair(status, status)
        }
    }

    data class Status(val isRunning: Boolean, val partNumber: String?, val memo: String?)

    private fun calculateAccuracy(blockCount: Int, matchedCount: Int): Float {
        if (blockCount == 0) return 0f
        return minOf(0.98f, (matchedCount.toFloat() / maxOf(1, blockCount)) + 0.3f)
    }
}
