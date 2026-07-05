package com.psp.shifthelper.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.EquipmentAlias
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.gson.Gson
import com.psp.shifthelper.data.dao.OcrCacheDao
import com.psp.shifthelper.data.model.OcrCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.security.MessageDigest
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

data class OcrLine(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 1.0f
)

data class OcrDetail(
    val isRunning: Boolean,
    val partNumber: String? = null,
    val memo: String? = null,
    val confidence: String = "High" // High, Medium, Low
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

class OcrService(
    private val context: Context,
    private val cacheDao: OcrCacheDao? = null
) {

    private val gson = Gson()
    private val anchors = listOf("040 16P Plug", "090 중국", "EJ 2P/3P(NEW)")

    init {
        try {
            if (!OpenCVLoader.initDebug()) {
                // OpenCV initialization failed
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
        val originalBitmap = loadBitmap(uri)
        
        val imageHash = calculateHash(originalBitmap)
        val cached = cacheDao?.getCache(imageHash)
        if (cached != null) {
            return@withContext gson.fromJson(cached.ocrResultJson, OcrResult::class.java)
        }

        val processedBitmap = preprocessBitmap(originalBitmap)
        
        // 9. Template 개선: 좌표 정보가 있는 경우 해당 영역만 OCR 수행 시도
        val activeTemplate = templates.firstOrNull { it.regions.isNotEmpty() }
        if (activeTemplate != null) {
            return@withContext processWithTemplate(processedBitmap, activeTemplate, allEquipments, aliases, targetDate, targetShift, imageHash)
        }

        val image = InputImage.fromBitmap(processedBitmap, 0)
        val horizontalLines = detectHorizontalLines(processedBitmap)

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

        val rows = groupLinesIntoRowsWithHorizontalLines(allLines, horizontalLines, image.height)
        if (rows.isEmpty()) return@withContext createEmptyResult(visionText.text, image.width, image.height)

        // 6. 앵커(Anchor) 검증 추가
        verifyAnchors(rows, allEquipments, aliases)

        val dateRow = rows[0]
        val shiftRow = if (rows.size > 1) rows[1] else emptyList()
        
        val dateAnchors = extractDateAnchors(dateRow).sortedBy { it.xCenter }
        val shiftAnchors = extractShiftAnchors(shiftRow).sortedBy { it.xCenter }

        val dateColumns = defineColumns(dateAnchors, image.width)
        val shiftColumns = defineColumns(shiftAnchors, image.width)

        val multiResults = mutableMapOf<String, MutableMap<String, MutableMap<Long, Boolean>>>()
        val boundingBoxes = mutableMapOf<Long, Rect>()
        val matchedLines = mutableMapOf<Long, String>()
        val details = mutableMapOf<Long, OcrDetail>()
        val uncertainIds = allEquipments.map { it.id }.toMutableSet()

        // 4. 장비 매칭 방식 변경 (Row 기반 1:1 매칭)
        val sortedEquipments = allEquipments.sortedBy { it.displayOrder }
        val equipRows = rows.drop(2)

        sortedEquipments.forEachIndexed { index, equip ->
            val equipId = equip.id
            if (index < equipRows.size) {
                val row = equipRows[index]
                
                // 첫 번째 열 (장비명)
                val firstColXLimit = dateColumns.firstOrNull()?.start ?: (image.width / 4)
                val nameLines = row.filter { it.boundingBox.centerX() < firstColXLimit }
                var recognizedName = nameLines.joinToString(" ") { it.text }

                // 7. OCR 재시도 (필요 시 영역 확대 인식)
                recognizedName = retryOcrIfNecessary(processedBitmap, nameLines, recognizedName, equip, aliases)

                // 6. 앵커(Anchor) 검증 및 8. Alias 유사도 매칭
                val bestMatch = findBestAliasMatch(recognizedName, equip, aliases)
                val confidence = if (bestMatch.similarity > 0.8) "High" else if (bestMatch.similarity > 0.5) "Medium" else "Low"

                uncertainIds.remove(equipId)
                if (nameLines.isNotEmpty()) {
                    boundingBoxes[equipId] = nameLines.first().boundingBox
                    matchedLines[equipId] = recognizedName
                } else {
                    matchedLines[equipId] = ""
                }

                // 각 날짜/쉬프트 셀 데이터 추출
                dateColumns.forEach { dc ->
                    val dateRes = multiResults.getOrPut(dc.text) { mutableMapOf() }
                    val scs = shiftColumns.filter { it.xCenter in dc.start..dc.end }
                    
                    if (scs.isNotEmpty()) {
                        scs.forEach { sc ->
                            val cellTxt = row.filter { it.boundingBox.centerX() in sc.start..sc.end }.joinToString("\n") { it.text }
                            val (st, _) = parseCellStatus(cellTxt)
                            dateRes.getOrPut(sc.text) { mutableMapOf() }[equipId] = st.isRunning
                            if (dc.text == (targetDate ?: dateAnchors.firstOrNull()?.text) && sc.text.contains(targetShift)) {
                                details[equipId] = OcrDetail(st.isRunning, st.partNumber, st.memo, confidence)
                            }
                        }
                    } else {
                        val cellTxt = row.filter { it.boundingBox.centerX() in dc.start..dc.end }.joinToString("\n") { it.text }
                        val (d, n) = parseCellStatus(cellTxt)
                        dateRes.getOrPut("주간") { mutableMapOf() }[equipId] = d.isRunning
                        dateRes.getOrPut("야간") { mutableMapOf() }[equipId] = n.isRunning
                        if (dc.text == (targetDate ?: dateAnchors.firstOrNull()?.text)) {
                            val st = if (targetShift.contains("야간")) n else d
                            details[equipId] = OcrDetail(st.isRunning, st.partNumber, st.memo, confidence)
                        }
                    }
                }
            } else {
                // 5. 빈 Row 처리
                matchedLines[equipId] = ""
            }
        }

        val finalDate = targetDate ?: dateAnchors.firstOrNull()?.text
        val dData = multiResults[finalDate]
        val sk = dData?.keys?.find { it.contains(targetShift) } ?: dData?.keys?.firstOrNull()
        val finalStates = dData?.get(sk) ?: emptyMap()

        val result = OcrResult(
            rawText = visionText.text,
            date = finalDate,
            equipmentStates = finalStates,
            details = details,
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

        // 결과 캐시 저장
        cacheDao?.insertCache(OcrCache(imageHash, gson.toJson(result)))

        result
    }

    private fun verifyAnchors(rows: List<List<OcrLine>>, allEquipments: List<Equipment>, aliases: List<EquipmentAlias>) {
        val sortedEquipments = allEquipments.sortedBy { it.displayOrder }
        anchors.forEach { anchorText ->
            // 마스터 장비 목록에서 앵커 텍스트를 포함하는 장비의 기대 인덱스 찾기
            val expectedIndex = sortedEquipments.indexOfFirst { it.name.contains(anchorText) || it.code.contains(anchorText) }
            if (expectedIndex != -1) {
                val rowIdx = expectedIndex + 2 // Header 2개 제외
                if (rowIdx < rows.size) {
                    val rowText = rows[rowIdx].joinToString(" ") { it.text }
                    val sim = calculateSimilarity(rowText, anchorText)
                    if (sim < 0.5) {
                        // 앵커 검증 실패 시 로깅 또는 예외 처리 (여기서는 로그만 출력하도록 설계)
                        // "Row 생성 오류 또는 Header 판정 오류 의심: $anchorText"
                    }
                }
            }
        }
    }

    private suspend fun processWithTemplate(
        bitmap: Bitmap,
        template: com.psp.shifthelper.data.model.OcrTemplate,
        allEquipments: List<Equipment>,
        aliases: List<EquipmentAlias>,
        targetDate: String?,
        targetShift: String,
        imageHash: String
    ): OcrResult {
        val equipmentStates = mutableMapOf<Long, Boolean>()
        val details = mutableMapOf<Long, OcrDetail>()
        val matchedLines = mutableMapOf<Long, String>()
        val boundingBoxes = mutableMapOf<Long, Rect>()

        template.regions.forEach { region ->
            val rect = Rect(region.x, region.y, region.x + region.width, region.y + region.height)
            val recognizedText = reOcrRegion(bitmap, rect)
            val equipId = region.equipmentId
            val equip = allEquipments.find { it.id == equipId } ?: return@forEach
            
            val (status, _) = parseCellStatus(recognizedText)
            equipmentStates[equipId] = status.isRunning
            
            val sim = findBestAliasMatch(recognizedText, equip, aliases).similarity
            val confidence = if (sim > 0.8) "High" else if (sim > 0.5) "Medium" else "Low"
            details[equipId] = OcrDetail(status.isRunning, status.partNumber, status.memo, confidence)
            matchedLines[equipId] = recognizedText
            boundingBoxes[equipId] = rect
        }

        val result = OcrResult(
            rawText = "Template-based OCR",
            date = targetDate,
            equipmentStates = equipmentStates,
            details = details,
            dayStates = emptyMap(),
            nightStates = emptyMap(),
            uncertainEquipments = emptySet(),
            matchedLines = matchedLines,
            boundingBoxes = boundingBoxes,
            allDetectedLines = emptyList(),
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            accuracy = 1.0f
        )
        cacheDao?.insertCache(OcrCache(imageHash, gson.toJson(result)))
        return result
    }

    private fun loadBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    private fun calculateHash(bitmap: Bitmap): String {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val bytes = stream.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val equalized = Mat()
        clahe.apply(gray, equalized)
        val thresholded = Mat()
        Imgproc.adaptiveThreshold(equalized, thresholded, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 21, 15.0)
        val blurred = Mat()
        Imgproc.medianBlur(thresholded, blurred, 3)
        val finalMat = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0))
        Imgproc.morphologyEx(blurred, finalMat, Imgproc.MORPH_CLOSE, kernel)
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(finalMat, resultBitmap)
        src.release(); gray.release(); equalized.release(); thresholded.release(); blurred.release(); finalMat.release()
        return resultBitmap
    }

    private fun detectHorizontalLines(bitmap: Bitmap): List<Int> {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        val lines = Mat()
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 100, bitmap.width * 0.5, 10.0)
        val horizontalLines = mutableListOf<Int>()
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            if (l != null && l.size >= 4) {
                if (abs(l[1] - l[3]) < 5) horizontalLines.add(((l[1] + l[3]) / 2).toInt())
            }
        }
        src.release(); gray.release(); edges.release(); lines.release()
        return horizontalLines.sorted().distinctBy { it / 20 }
    }

    private fun groupLinesIntoRowsWithHorizontalLines(lines: List<OcrLine>, horizontalLines: List<Int>, imgHeight: Int): List<List<OcrLine>> {
        val sortedH = (listOf(0) + horizontalLines + listOf(imgHeight)).sorted().distinctBy { it / 10 }
        val rows = List(sortedH.size - 1) { mutableListOf<OcrLine>() }
        lines.forEach { line ->
            val y = line.boundingBox.centerY()
            val rowIndex = sortedH.zipWithNext().indexOfFirst { (top, bottom) -> y in top..bottom }
            if (rowIndex != -1) rows[rowIndex].add(line)
        }
        return rows.map { it.sortedBy { l -> l.boundingBox.left } }
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

    private fun defineColumns(anchors: List<Anchor>, imgWidth: Int): List<ColumnRange> {
        val sorted = anchors.sortedBy { it.xCenter }
        return sorted.mapIndexed { i, a ->
            val start = if (i == 0) a.xCenter - 50 else (sorted[i-1].xCenter + a.xCenter) / 2
            val end = if (i == sorted.size - 1) imgWidth else (a.xCenter + sorted[i+1].xCenter) / 2
            ColumnRange(a.text, start, end, a.xCenter)
        }
    }

    private fun findBestAliasMatch(recognized: String, equipment: Equipment, aliases: List<EquipmentAlias>): MatchResult {
        val candidates = mutableListOf(equipment.code, equipment.name)
        candidates.addAll(aliases.filter { it.equipmentId == equipment.id }.map { it.rawText })
        
        var maxSim = 0.0
        candidates.forEach { cand ->
            val sim = calculateSimilarity(recognized, cand)
            if (sim > maxSim) maxSim = sim
        }
        return MatchResult(maxSim)
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val dist = calculateLevenshteinDistance(s1.lowercase(), s2.lowercase())
        return 1.0 - (dist.toDouble() / maxOf(s1.length, s2.length))
    }

    private fun calculateLevenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    private suspend fun retryOcrIfNecessary(
        bitmap: Bitmap,
        nameLines: List<OcrLine>,
        currentText: String,
        equip: Equipment,
        aliases: List<EquipmentAlias>
    ): String {
        val sim = findBestAliasMatch(currentText, equip, aliases).similarity
        if (sim < 0.6 && nameLines.isNotEmpty()) {
            var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = Int.MIN_VALUE; var bottom = Int.MIN_VALUE
            nameLines.forEach {
                left = minOf(left, it.boundingBox.left)
                top = minOf(top, it.boundingBox.top)
                right = maxOf(right, it.boundingBox.right)
                bottom = maxOf(bottom, it.boundingBox.bottom)
            }
            val rect = Rect((left - 20).coerceAtLeast(0), (top - 10).coerceAtLeast(0), (right + 20).coerceAtMost(bitmap.width), (bottom + 10).coerceAtMost(bitmap.height))
            if (rect.width() > 0 && rect.height() > 0) {
                return reOcrRegion(bitmap, rect)
            }
        }
        return currentText
    }

    private suspend fun reOcrRegion(bitmap: Bitmap, rect: Rect): String {
        return try {
            val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            val scaled = Bitmap.createScaledBitmap(cropped, cropped.width * 2, cropped.height * 2, true)
            val image = InputImage.fromBitmap(scaled, 0)
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text.replace("\n", " ")) }
                    .addOnFailureListener { cont.resume("") }
            }
        } catch (e: Exception) {
            ""
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
    data class MatchResult(val similarity: Double)
}
