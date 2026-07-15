package com.johndev.verset

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.johndev.verset.data.Prefs
import com.johndev.verset.repository.BibleRepository
import com.johndev.verset.repository.SyncRepository
import com.johndev.verset.ui.navigation.VersetNavGraph
import com.johndev.verset.ui.theme.VersetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as VersetApp
        val repository = BibleRepository(app.db)
        val syncRepository = SyncRepository(app.db)
        val prefs = Prefs(this)

        setContent {
            var darkMode by remember { mutableStateOf(if (prefs.followSystemTheme) null else prefs.darkMode) }
            var onboardingDone by remember { mutableStateOf(prefs.onboardingComplete) }
            VersetTheme(darkTheme = darkMode ?: isSystemInDarkTheme()) {
                if (!onboardingDone) {
                    com.johndev.verset.ui.screens.OnboardingScreen(onFinish = {
                        prefs.onboardingComplete = true
                        onboardingDone = true
                    })
                } else {
                    VersetNavGraph(
                        repository = repository,
                        syncRepository = syncRepository,
                        prefs = prefs,
                        onDarkModeChange = { follow, dark ->
                            prefs.followSystemTheme = follow
                            prefs.darkMode = dark
                            darkMode = if (follow) null else dark
                        }
                    )
                }
            }
        }
    }
}
