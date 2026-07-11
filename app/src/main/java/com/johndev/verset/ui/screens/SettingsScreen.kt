package com.johndev.verset.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johndev.verset.auth.GoogleAuthManager
import com.johndev.verset.data.Prefs
import com.johndev.verset.repository.SyncRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
    var lastSync by remember { mutableStateOf(prefs.lastSyncTimeMillis) }
    var syncing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Follow system theme", Modifier.weight(1f))
            Switch(checked = followSystem, onCheckedChange = {
                followSystem = it
                onDarkModeChange(it, darkMode)
            })
        }
        if (!followSystem) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(Modifier.height(8.dp))

        // Always-visible plain-language explainer, so someone who's never used
        // cloud sync before understands what signing in actually does before
        // they tap anything.
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Everything you tag is already saved on this phone — sync is only needed if you want a backup or want to use Verset on a second device.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                SyncStep(1, "Sign in with Google below.")
                SyncStep(2, "Tap \"Sync now\" any time you want to back up new tags and notes.")
                SyncStep(3, "On another phone, sign in with the same Google account and sync — everything comes back.")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Status indicator — reassures a new user that sync actually did something.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (signedIn && lastSync > 0) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4A8B5C))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Backed up • last synced ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lastSync))}",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (signedIn) {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Signed in, not backed up yet — tap Sync now", style = MaterialTheme.typography.bodySmall)
            } else {
                Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Not backed up — everything is still safe on this device", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!signedIn) {
            Button(onClick = {
                scope.launch {
                    val result = GoogleAuthManager.signIn(context)
                    signedIn = result.isSuccess
                    if (result.isSuccess) com.johndev.verset.sync.SyncWorker.schedule(context)
                    statusMessage = if (result.isSuccess) "Signed in" else "Sign-in failed: ${result.exceptionOrNull()?.message}"
                }
            }) { Text("Sign in with Google") }
        } else {
            Button(
                enabled = !syncing,
                onClick = {
                    scope.launch {
                        syncing = true
                        statusMessage = "Syncing…"
                        val result = syncRepository.syncNow()
                        syncing = false
                        if (result.isSuccess) {
                            prefs.lastSyncTimeMillis = System.currentTimeMillis()
                            lastSync = prefs.lastSyncTimeMillis
                            statusMessage = "Synced"
                        } else {
                            statusMessage = "Sync failed: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
            ) { Text(if (syncing) "Syncing…" else "Sync now") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                GoogleAuthManager.signOut()
                com.johndev.verset.sync.SyncWorker.cancel(context)
                signedIn = false
            }) { Text("Sign out") }
        }

        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SyncStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("$number.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
