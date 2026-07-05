package com.psp.shifthelper.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.psp.shifthelper.data.converters.RoomConverters
import com.psp.shifthelper.data.dao.AssignmentDao
import com.psp.shifthelper.data.dao.AssignmentWeightDao
import com.psp.shifthelper.data.dao.EquipmentAliasDao
import com.psp.shifthelper.data.dao.EquipmentDao
import com.psp.shifthelper.data.dao.OcrCacheDao
import com.psp.shifthelper.data.dao.OcrTemplateDao
import com.psp.shifthelper.data.dao.ShiftDataDao
import com.psp.shifthelper.data.dao.WorkerDao
import com.psp.shifthelper.data.model.Assignment
import com.psp.shifthelper.data.model.AssignmentWeight
import com.psp.shifthelper.data.model.Equipment
import com.psp.shifthelper.data.model.EquipmentAlias
import com.psp.shifthelper.data.model.OcrCache
import com.psp.shifthelper.data.model.OcrTemplate
import com.psp.shifthelper.data.model.ShiftData
import com.psp.shifthelper.data.model.Worker

@Database(
    entities = [
        Worker::class,
        Equipment::class,
        Assignment::class,
        AssignmentWeight::class,
        EquipmentAlias::class,
        OcrTemplate::class,
        ShiftData::class,
        OcrCache::class
    ],
    version = 6, // OcrCache 추가
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workerDao(): WorkerDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun assignmentWeightDao(): AssignmentWeightDao
    abstract fun equipmentAliasDao(): EquipmentAliasDao
    abstract fun ocrTemplateDao(): OcrTemplateDao
    abstract fun shiftDataDao(): ShiftDataDao
    abstract fun ocrCacheDao(): OcrCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "psp_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
