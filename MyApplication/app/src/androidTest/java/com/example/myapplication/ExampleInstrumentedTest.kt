package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class package com.example.pspshifthelper.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "equipment")
data class Equipment(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val x: Float,  // 상대 좌표
    val y: Float,
    val width: Float,
    val height: Float,
    val status: String,  // "가동", "비가동", "야간", "대기" 등
    val assignedEmployee: String? = null,  // 배정된 직원명
    val isFromSchedule: Boolean = true,  // 스케줄에서 인식된 장비인지 여부
    val createdAt: Long = System.currentTimeMillis()
)ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.myapplication", appContext.packageName)
    }
}