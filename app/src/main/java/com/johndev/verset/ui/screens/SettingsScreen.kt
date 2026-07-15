package com.johndev.verset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    // Sync configuration state — drives which section the user sees
    val isPlaceholderConfig = remember { GoogleAuthManager.isPlaceholderConfig() }
    var webClientId by remember { mutableStateOf(prefs.webClientId) }
    var webClientIdInput by remember { mutableStateOf(prefs.webClientId) }
    var showClientIdField by remember { mutableStateOf(false) }
    var showClientId by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // ── Display ──────────────────────────────────────────────────────────
        Text("Display", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("Reader text size: ${"%.1f".format(fontScale)}x")
        Slider(
            value = fontScale,
            onValueChange = { fontScale = it; prefs.fontScale = it },
            valueRange = 0.8f..1.6f,
            steps = 7
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Follow system theme", Modifier.weight(1f))
            Switch(checked = followSystem, onCheckedChange = {
                followSystem = it; onDarkModeChange(it, darkMode)
            })
        }
        if (!followSystem) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", Modifier.weight(1f))
                Switch(checked = darkMode, onCheckedChange = {
                    darkMode = it; onDarkModeChange(false, it)
                })
            }
        }

        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        // ── Backup & Sync ─────────────────────────────────────────────────────
        Text("Backup & Sync", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        when {
            // ── State 1: google-services.json still placeholder ───────────────
            isPlaceholderConfig -> {
                SyncSetupCard(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    tint = MaterialTheme.colorScheme.errorContainer,
                    title = "Firebase not connected yet",
                    body = "Sync needs a Firebase project to store your backup. This is a one-time developer setup — not something users need to repeat."
                )
                Spacer(Modifier.height(16.dp))
                Text("Steps to connect (for the app developer):", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                SetupStep("1", "Go to firebase.google.com and sign in with a Google account.")
                SetupStep("2", "Create a new project, then add an Android app with package name com.johndev.verset.")
                SetupStep("3", "Download google-services.json and replace the placeholder file at app/google-services.json in the project, then rebuild.")
                SetupStep("4", "In Firebase, enable Authentication → Google, and enable Firestore Database.")
                SetupStep("5", "Copy the Web Client ID (Authentication → Sign-in method → Google → Web SDK configuration) and enter it in the field below.")
                Spacer(Modifier.height(16.dp))
                Text(
                    "⚠ The google-services.json step requires a rebuild — it's a compile-time file. Everything else is runtime.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── State 2: Real config, but Web Client ID not saved yet ─────────
            webClientId.isBlank() -> {
                SyncSetupCard(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                    tint = MaterialTheme.colorScheme.secondaryContainer,
                    title = "One more step to enable sync",
                    body = "Firebase is connected. Enter your Web Client ID below to activate Google Sign-In."
                )
                Spacer(Modifier.height(8.dp))
                SetupStep("1", "Open Firebase console → Authentication → Sign-in method → Google.")
                SetupStep("2", "Expand \"Web SDK configuration\" and copy the Web Client ID.")
                SetupStep("3", "Paste it below and tap Save.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = webClientIdInput,
                    onValueChange = { webClientIdInput = it },
                    label = { Text("Web Client ID") },
                    placeholder = { Text("xxxxxxxx.apps.googleusercontent.com") },
                    visualTransformation = if (showClientId) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showClientId = !showClientId }) {
                            Text(if (showClientId) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = webClientIdInput.contains(".apps.googleusercontent.com"),
                    onClick = {
                        prefs.webClientId = webClientIdInput.trim()
                        webClientId = webClientIdInput.trim()
                        statusMessage = "Web Client ID saved. You can now sign in."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save and activate sync") }
            }

            // ── State 3: Fully configured — just sign in and sync ────────────
            else -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Everything you tag is saved on this phone. Sign in with Google to back it up and use it on another device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        SyncStep(1, "Sign in with Google below.")
                        SyncStep(2, "Tap \"Sync now\" any time to back up new tags and notes.")
                        SyncStep(3, "On another phone, sign in with the same Google account and sync — everything comes back.")
                    }
                }

                Spacer(Modifier.height(16.dp))

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
                        Text("Not backed up — your data is still safe on this device", style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!signedIn) {
                    Button(
                        onClick = {
                            scope.launch {
                                val result = GoogleAuthManager.signIn(context, webClientId)
                                signedIn = result.isSuccess
                                if (result.isSuccess) com.johndev.verset.sync.SyncWorker.schedule(context)
                                statusMessage = if (result.isSuccess) "Signed in" else "Sign-in failed: ${result.exceptionOrNull()?.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign in with Google") }
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
                                    statusMessage = "Synced ✓"
                                } else {
                                    statusMessage = "Sync failed: ${result.exceptionOrNull()?.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (syncing) "Syncing…" else "Sync now") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            GoogleAuthManager.signOut()
                            com.johndev.verset.sync.SyncWorker.cancel(context)
                            signedIn = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sign out") }
                    Spacer(Modifier.height(8.dp))
                    // Developer escape hatch — update the Web Client ID without
                    // needing to clear app data
                    TextButton(onClick = { showClientIdField = !showClientIdField }) {
                        Text("Change Web Client ID", style = MaterialTheme.typography.labelSmall)
                    }
                    if (showClientIdField) {
                        OutlinedTextField(
                            value = webClientIdInput,
                            onValueChange = { webClientIdInput = it },
                            label = { Text("Web Client ID") },
                            visualTransformation = if (showClientId) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showClientId = !showClientId }) {
                                    Text(if (showClientId) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            prefs.webClientId = webClientIdInput.trim()
                            webClientId = webClientIdInput.trim()
                            showClientIdField = false
                            statusMessage = "Web Client ID updated."
                        }) { Text("Save") }
                    }
                }
            }
        }

        statusMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SyncSetupCard(
    icon: @Composable () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SetupStep(number: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SyncStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text("$number.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
