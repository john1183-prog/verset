package com.johndev.verset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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

    var themePickerFor by remember { mutableStateOf<VerseTagEntry?>(null) }

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
                            var confirmDelete by remember { mutableStateOf(false) }
                            var editing by remember { mutableStateOf(false) }
                            ListItem(
                                leadingContent = {
                                    Box(
                                        Modifier
                                            .size(18.dp)
                                            .background(
                                                color = runCatching { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(tag.colorHex)) }
                                                    .getOrDefault(MaterialTheme.colorScheme.secondary),
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                    )
                                },
                                headlineContent = { Text(tag.name) },
                                modifier = Modifier.clickable { selectedTag = tag },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { editing = true }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit tag")
                                        }
                                        IconButton(onClick = { confirmDelete = true }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete tag")
                                        }
                                    }
                                }
                            )
                            HorizontalDivider()
                            if (confirmDelete) {
                                AlertDialog(
                                    onDismissRequest = { confirmDelete = false },
                                    title = { Text("Delete \"${tag.name}\"?") },
                                    text = { Text("This removes the tag and every verse+note saved under it. This can't be undone.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            scope.launch { repository.deleteTag(tag) }
                                            confirmDelete = false
                                        }) { Text("Delete") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                                    }
                                )
                            }
                            if (editing) {
                                EditTagDialog(
                                    tag = tag,
                                    onDismiss = { editing = false },
                                    onSave = { newName, newColor ->
                                        scope.launch { repository.updateTag(tag.copy(name = newName, colorHex = newColor)) }
                                        editing = false
                                    }
                                )
                            }
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
                                onExportImage = { themePickerFor = entry },
                                onDelete = { scope.launch { repository.deleteEntry(entry) } },
                                onSaveNote = { newNote -> scope.launch { repository.updateEntry(entry.copy(note = newNote)) } }
                            )
                            HorizontalDivider()
                        }
                    }

                    themePickerFor?.let { entry ->
                        ThemePickerDialog(
                            onDismiss = { themePickerFor = null },
                            onPick = { theme ->
                                val ok = ImageCardExporter.export(context, entry, tag.name, theme) != null
                                scope.launch {
                                    snackbarHostState.showSnackbar(if (ok) "Saved image to Pictures/Verset" else "Export failed")
                                }
                                themePickerFor = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePickerDialog(onDismiss: () -> Unit, onPick: (com.johndev.verset.export.CardTheme) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a card style") },
        text = {
            Column {
                com.johndev.verset.export.CardTheme.values().forEach { theme ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(theme) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(theme.background)),
                                    androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(theme.accent)),
                                    androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(theme.displayName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTagDialog(tag: Tag, onDismiss: () -> Unit, onSave: (name: String, colorHex: String) -> Unit) {
    var name by remember { mutableStateOf(tag.name) }
    var color by remember { mutableStateOf(tag.colorHex) }
    val swatches = listOf("#4A6FA5", "#C9A24B", "#8B4A62", "#4A8B5C", "#B5533C", "#6B4A8B", "#5C5C5C")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tag") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row {
                    swatches.forEach { hex ->
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(32.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .then(
                                    if (color == hex)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                                    else Modifier
                                )
                                .clickable { color = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), color) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EntryRow(
    entry: VerseTagEntry,
    onExportImage: () -> Unit,
    onDelete: () -> Unit,
    onSaveNote: (String) -> Unit
) {
    var editingNote by remember { mutableStateOf(false) }
    var draftNote by remember { mutableStateOf(entry.note) }

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
        Row {
            TextButton(onClick = { draftNote = entry.note; editingNote = true }) { Text("Edit note") }
            TextButton(onClick = onDelete) { Text("Remove") }
        }
    }

    if (editingNote) {
        AlertDialog(
            onDismissRequest = { editingNote = false },
            title = { Text("Edit note") },
            text = {
                OutlinedTextField(
                    value = draftNote,
                    onValueChange = { draftNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveNote(draftNote.trim())
                    editingNote = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingNote = false }) { Text("Cancel") } }
        )
    }
}
