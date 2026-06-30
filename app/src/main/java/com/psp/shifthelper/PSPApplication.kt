package com.psp.shifthelper

import android.app.Application
import com.psp.shifthelper.data.database.AppDatabase
import com.psp.shifthelper.data.local.LocalDataSource

class PSPApplication : Application() {
    val localDataSource by lazy {
        LocalDataSource(this)
    }
    
    val database by lazy {
        AppDatabase.getDatabase(this)
    }
}