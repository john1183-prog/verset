package com.johndev.verset.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johndev.verset.auth.GoogleAuthManager
import com.johndev.verset.data.Prefs
import com.johndev.verset.repository.SyncRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    prefs: Prefs,
    syncRepository: SyncRepository,
    onDarkModeChange: (follow: Boolean, dark: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fontScale by remember { mutableStateOf(prefs.fontScale) }
    var followSystem by remember { mutableStateOf(prefs.followSystemTheme) }
    var darkMode by remember { mutableStateOf(prefs.darkMode) }
    var signedIn by remember { mutableStateOf(GoogleAuthManager.isSignedIn()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Display", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Text("Reader text size: ${"%.1f".format(fontScale)}x")
        Slider(
            value = fontScale,
            onValueChange = {
                fontScale = it
                prefs.fontScale = it
            },
            valueRange = 0.8f..1.6f,
            steps = 7
        )

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Follow system theme", Modifier.weight(1f))
            Switch(checked = followSystem, onCheckedChange = {
                followSystem = it
                onDarkModeChange(it, darkMode)
            })
        }
        if (!followSystem) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Dark mode", Modifier.weight(1f))
                Switch(checked = darkMode, onCheckedChange = {
                    darkMode = it
                    onDarkModeChange(false, it)
                })
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Text("Backup & Sync", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        if (!signedIn) {
            Button(onClick = {
                scope.launch {
                    val result = GoogleAuthManager.signIn(context)
                    signedIn = result.isSuccess
                    statusMessage = if (result.isSuccess) "Signed in" else "Sign-in failed: ${result.exceptionOrNull()?.message}"
                }
            }) { Text("Sign in with Google") }
        } else {
            Text("Signed in ✓")
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    statusMessage = "Syncing…"
                    val result = syncRepository.syncNow()
                    statusMessage = if (result.isSuccess) "Synced" else "Sync failed: ${result.exceptionOrNull()?.message}"
                }
            }) { Text("Sync now") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                GoogleAuthManager.signOut()
                signedIn = false
            }) { Text("Sign out") }
        }

        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
