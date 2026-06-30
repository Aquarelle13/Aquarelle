package com.psp.shifthelper.data.repository

import com.psp.shifthelper.data.dao.AssignmentDao
import com.psp.shifthelper.data.dao.EquipmentDao
import com.psp.shifthelper.data.dao.WorkerDao
import com.psp.shifthelper.data.model.Assignment
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.Worker
import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val equipmentDao: EquipmentDao,
    private val workerDao: WorkerDao,
    private val assignmentDao: AssignmentDao
) {
    // 장비
    fun getAllEquipments(): Flow<List<Equipment>> =
        equipmentDao.getAll()

    suspend fun upsertEquipment(equipment: Equipment) =
        equipmentDao.upsert(equipment)

    suspend fun deleteEquipment(equipment: Equipment) =
        equipmentDao.delete(equipment)

    // 조원
    fun getAllWorkers(): Flow<List<Worker>> =
        workerDao.getAll()

    fun getWorkersByGroup(group: String): Flow<List<Worker>> =
        workerDao.getByGroup(group)

    suspend fun upsertWorker(worker: Worker) =
        workerDao.upsert(worker)

    suspend fun deleteWorker(worker: Worker) =
        workerDao.delete(worker)

    // 배정
    fun getAllAssignments(): Flow<List<Assignment>> =
        assignmentDao.getAll()

    fun getAssignmentsByDateAndShift(
        date: String,
        shift: String
    ): Flow<List<Assignment>> =
        assignmentDao.getByDateAndShift(date, shift)

    fun getRecentAssignmentsByWorker(
        workerId: Long
    ): Flow<List<Assignment>> =
        assignmentDao.getRecentByWorker(workerId)

    suspend fun upsertAssignment(assignment: Assignment) =
        assignmentDao.upsert(assignment)

    suspend fun deleteAssignment(assignment: Assignment) =
        assignmentDao.delete(assignment)
}