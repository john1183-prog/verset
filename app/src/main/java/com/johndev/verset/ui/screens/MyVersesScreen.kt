package com.johndev.verset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.Tag
import com.johndev.verset.data.VerseTagEntry
import com.johndev.verset.export.ImageCardExporter
import com.johndev.verset.export.PdfExporter
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVersesScreen(repository: BibleRepository) {
    val context = LocalContext.current
    val tags by repository.tagsFlow().collectAsState(initial = emptyList())
    var selectedTag by remember { mutableStateOf<Tag?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.padding(padding)) {
            if (selectedTag == null) {
                if (tags.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text("No tags yet. Tap a verse in Read to classify it.", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                        items(tags, key = { it.id }) { tag ->
                            ListItem(
                                headlineContent = { Text(tag.name) },
                                modifier = Modifier.clickable { selectedTag = tag }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                val tag = selectedTag!!
                val entries by repository.entriesForTag(tag.id).collectAsState(initial = emptyList())

                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(tag.name) },
                        navigationIcon = {
                            IconButton(onClick = { selectedTag = null }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                val ok = PdfExporter.export(
                                    context, tag.name,
                                    entries.map { it to tag }
                                ) != null
                                scope.launch {
                                    snackbarHostState.showSnackbar(if (ok) "Saved PDF to Downloads/Verset" else "Export failed")
                                }
                            }) { Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF") }
                        }
                    )

                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(entries, key = { it.id }) { entry ->
                            EntryRow(
                                entry = entry,
                                onExportImage = {
                                    val ok = ImageCardExporter.export(context, entry, tag.name) != null
                                    scope.launch {
                                        snackbarHostState.showSnackbar(if (ok) "Saved image to Pictures/Verset" else "Export failed")
                                    }
                                },
                                onDelete = { scope.launch { repository.deleteEntry(entry) } }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: VerseTagEntry, onExportImage: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "${entry.book} ${entry.chapter}:${entry.verse}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onExportImage) {
                Icon(Icons.Filled.Image, contentDescription = "Export as image")
            }
        }
        Text(entry.verseText, style = MaterialTheme.typography.bodyMedium)
        if (entry.note.isNotBlank()) {
            Text(
                entry.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        TextButton(onClick = onDelete) { Text("Remove") }
    }
}
