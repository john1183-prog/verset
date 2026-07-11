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
        // Re-schedule the periodic sync job every launch (idempotent with KEEP policy)
        // so it survives device reboots. Only runs if actually signed in.
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            com.johndev.verset.sync.SyncWorker.schedule(this)
        }
    }
}
