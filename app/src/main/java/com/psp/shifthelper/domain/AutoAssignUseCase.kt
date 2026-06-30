package com.psp.shifthelper.domain

import com.psp.shifthelper.data.model.Assignment
import com.psp.shifthelper.data.model.AssignmentWeight
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.Worker
import kotlin.math.sqrt
import kotlin.math.pow

data class AssignInput(
    val date: String,
    val shift: String,
    val runningEquipments: List<Equipment>,
    val availableWorkers: List<Worker>,
    val recentAssignments: List<Assignment>,
    val weights: List<AssignmentWeight>,
    val fixedAssignments: Map<Long, Long> = emptyMap()
)

data class AssignResult(
    val assignment: Assignment,
    val workerName: String,
    val equipmentCode: String
)

data class AssignOutput(
    val assignments: List<Assignment>,
    val results: List<AssignResult>,
    val warnings: List<String>
)

class AutoAssignUseCase {

    fun execute(input: AssignInput): AssignOutput {
        val warnings = mutableListOf<String>()
        val finalAssignments = mutableListOf<Assignment>()
        
        // 현재 배정 상태 추적
        val workerLoadMap = input.availableWorkers.associate { it.id to mutableListOf<Long>() }.toMutableMap()
        val assignedEquipmentIds = mutableSetOf<Long>()

        // 1순위: 사용자가 직접 수동 배정 (절대 변경 금지)
        input.fixedAssignments.forEach { (equipId, workerId) ->
            val worker = input.availableWorkers.find { it.id == workerId }
            val equip = input.runningEquipments.find { it.id == equipId }
            if (worker != null && equip != null) {
                finalAssignments.add(createAssignment(input, workerId, equipId, "Fixed"))
                workerLoadMap[workerId]?.add(equipId)
                assignedEquipmentIds.add(equipId)
            }
        }

        // 2순위: 세트 장비 로직 (기준 장비가 배정된 경우 연관 장비에 동일 인원 우선 할당)
        input.runningEquipments.filter { it.id !in assignedEquipmentIds && it.referenceEquipId != null }.forEach { equip ->
            val refId = equip.referenceEquipId!!
            val refAssignment = finalAssignments.find { it.equipmentId == refId }
            if (refAssignment != null) {
                finalAssignments.add(createAssignment(input, refAssignment.workerId, equip.id, "Set"))
                workerLoadMap[refAssignment.workerId]?.add(equip.id)
                assignedEquipmentIds.add(equip.id)
            }
        }

        // 3순위: 나머지 가동 장비 배정 (점수 기반 알고리즘)
        val remainingEquipments = input.runningEquipments.filter { it.id !in assignedEquipmentIds }
        
        // 배합의 묘: 장비 수가 조원 수보다 많을 수 있으므로 루프 수행
        remainingEquipments.forEach { equip ->
            val bestWorker = findBestWorkerByScore(
                equipment = equip,
                availableWorkers = input.availableWorkers,
                workerLoadMap = workerLoadMap,
                input = input
            )

            if (bestWorker != null) {
                finalAssignments.add(createAssignment(input, bestWorker.id, equip.id, "Ok"))
                workerLoadMap[bestWorker.id]?.add(equip.id)
                assignedEquipmentIds.add(equip.id)
            } else {
                warnings.add("${equip.code}: 가용 조원 없음")
                finalAssignments.add(createAssignment(input, -1L, equip.id, "Error"))
            }
        }

        // 검증: 모든 출근 직원은 최소 1대 이상 배정되었는가?
        input.availableWorkers.forEach { worker ->
            if (workerLoadMap[worker.id].isNullOrEmpty()) {
                warnings.add("${worker.name}: 배정된 장비가 없습니다.")
            }
        }

        val results = finalAssignments.map { assign ->
            val workerName = if (assign.workerId == -1L) "배정 실패" else input.availableWorkers.find { it.id == assign.workerId }?.name ?: "알 수 없음"
            val equipCode = input.runningEquipments.find { it.id == assign.equipmentId }?.code ?: "알 수 없음"
            AssignResult(assign, workerName, equipCode)
        }

        return AssignOutput(finalAssignments, results, warnings)
    }

    private fun findBestWorkerByScore(
        equipment: Equipment,
        availableWorkers: List<Worker>,
        workerLoadMap: Map<Long, List<Long>>,
        input: AssignInput
    ): Worker? {
        if (availableWorkers.isEmpty()) return null

        return availableWorkers.maxByOrNull { worker ->
            var score = 0f

            // 0. 기초 규칙: 배정된 장비가 없는 조원에게 압도적 가점 (전원 배정 보장)
            val assignedCount = workerLoadMap[worker.id]?.size ?: 0
            if (assignedCount == 0) score += 1000f
            else score -= (assignedCount * 50f) // 이미 많이 배정된 사람은 우선순위 낮춤

            // 1. 학습된 누적 가중치 (지시서 2순위: 사용자 수정 기록)
            val weightObj = input.weights.find { it.workerId == worker.id && it.equipmentId == equipment.id }
            score += (weightObj?.weight ?: 1.0f) * 200f

            // 2. 선호장비 (지시서 3순위)
            if (worker.preferredEquipmentIds.contains(equipment.id)) {
                score += 100f
            }

            // 3. 숙련도 (지시서 4순위: 1:10점, 2:20점, 3:30점)
            score += (worker.skillLevel * 10f)

            // 4. 최근 반복 배정 제한 (지시서 5순위: 3일 내 이력 확인)
            if (equipment.avoidConsecutive) {
                val lastThreeDays = input.recentAssignments.take(input.availableWorkers.size * 3)
                val wasAssignedRecently = lastThreeDays.any { 
                    it.workerId == worker.id && it.equipmentId == equipment.id 
                }
                if (wasAssignedRecently) score -= 500f // 강력한 페널티
            }

            // 5. 거리 기반 우선순위 (한 명이 여러 대 맡을 경우)
            val currentAssignedIds = workerLoadMap[worker.id] ?: emptyList()
            if (currentAssignedIds.isNotEmpty()) {
                val minDist = currentAssignedIds.minOf { refId ->
                    val refEquip = input.runningEquipments.find { it.id == refId }
                    if (refEquip != null) calculateDistance(equipment, refEquip) else 1000f
                }
                // 거리가 가까울수록 점수 가산 (1000은 캔버스 최대 크기 가정)
                score += (1000f - minDist) / 10f
            }

            score
        }
    }

    private fun calculateDistance(e1: Equipment, e2: Equipment): Float {
        return sqrt((e1.posX - e2.posX).pow(2) + (e1.posY - e2.posY).pow(2))
    }

    private fun createAssignment(input: AssignInput, workerId: Long, equipId: Long, status: String): Assignment {
        return Assignment(
            date = input.date,
            shift = input.shift,
            workerId = workerId,
            equipmentId = equipId,
            status = status
        )
    }
}
