package com.johndev.verset

import android.app.Application
import com.johndev.verset.data.AppDatabase
import com.johndev.verset.data.BibleLoader
import com.johndev.verset.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VersetApp : Application() {

    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
        val prefs = Prefs(this)
        CoroutineScope(Dispatchers.IO).launch {
            BibleLoader.loadIfNeeded(this@VersetApp, db, prefs)
        }
    }
}
