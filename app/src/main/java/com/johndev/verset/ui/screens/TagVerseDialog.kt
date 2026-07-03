package com.johndev.verset.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.Tag
import com.johndev.verset.data.Verse
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagVerseDialog(verse: Verse, repository: BibleRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val tags by repository.tagsFlow().collectAsState(initial = emptyList())
    val existingEntries by repository.entriesForVerse(verse.id).collectAsState(initial = emptyList())

    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var newTagName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${verse.book} ${verse.chapter}:${verse.verse}") },
        text = {
            Column {
                Text(verse.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))

                if (existingEntries.isNotEmpty()) {
                    Text("Already classified under:", style = MaterialTheme.typography.labelMedium)
                    existingEntries.forEach { entry ->
                        val tagName = tags.find { it.id == entry.tagId }?.name ?: "?"
                        Text("• $tagName — ${entry.note.ifBlank { "(no note)" }}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Text("Classify under:", style = MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.heightIn(max = 160.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTagId == tag.id,
                                onClick = { selectedTagId = tag.id; newTagName = "" }
                            )
                            Text(tag.name)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it; if (it.isNotBlank()) selectedTagId = null },
                    label = { Text("Or create a new tag (e.g. \"Promise\")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What's this verse about?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedTagId != null || newTagName.isNotBlank(),
                onClick = {
                    scope.launch {
                        val tag: Tag = if (newTagName.isNotBlank()) {
                            repository.getOrCreateTag(newTagName.trim())
                        } else {
                            tags.first { it.id == selectedTagId }
                        }
                        repository.saveEntry(verse, tag.id, note.trim())
                        onDismiss()
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
