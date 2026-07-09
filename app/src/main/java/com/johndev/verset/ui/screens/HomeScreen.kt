package com.johndev.verset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.ReadingHistoryEntry
import com.johndev.verset.data.VerseTagEntry
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.random.Random

/**
 * "Verse of the day" is picked from your own saved/tagged verses — not the
 * whole KJV — since the point is to resurface things you've already marked
 * as meaningful. It's stable for the calendar day (same verse all day, like
 * a normal verse-of-the-day feature), with a manual shuffle if you want a
 * different one without waiting for tomorrow.
 *
 * "Recently Read" is one row per book (most recent chapter you viewed there),
 * not a full timestamped log of every chapter visit — keeps the list short
 * and useful ("continue reading Genesis") instead of a noisy scroll history.
 */
@Composable
fun HomeScreen(repository: BibleRepository, onReadInContext: (bookIndex: Int, chapter: Int) -> Unit) {
    val entries by repository.allEntriesFlow().collectAsState(initial = emptyList())
    val tags by repository.tagsFlow().collectAsState(initial = emptyList())
    val history by repository.historyFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var confirmClearHistory by remember { mutableStateOf(false) }

    var shuffleSeed by remember { mutableStateOf(0) }
    val todayKey = remember { System.currentTimeMillis() / 86_400_000L }

    val picked: VerseTagEntry? = remember(entries, shuffleSeed) {
        if (entries.isEmpty()) null
        else {
            val seed = if (shuffleSeed == 0) todayKey else todayKey * 1000 + shuffleSeed
            entries[Random(seed).nextInt(entries.size)]
        }
    }
    val pickedTagName = picked?.let { p -> tags.find { it.id == p.tagId }?.name }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Verse of the Day", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        if (entries.isEmpty()) {
            Text(
                "Nothing saved yet. Tag a verse in Read and it'll start showing up here.",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (picked != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    if (pickedTagName != null) {
                        Text(
                            pickedTagName.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        picked.verseText,
                        style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "— ${picked.book} ${picked.chapter}:${picked.verse}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (picked.note.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(picked.note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row {
                OutlinedButton(onClick = { onReadInContext((picked.verseId / 1_000_000L).toInt(), picked.chapter) }) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Read in context")
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { shuffleSeed++ }) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recently Read", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { confirmClearHistory = true }) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            Column {
                history.forEach { h ->
                    HistoryRow(h, onClick = { onReadInContext(h.bookIndex, h.chapter) })
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear reading history?") },
            text = { Text("This only clears your \"Recently Read\" list — your tagged verses and notes are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.clearHistory() }
                    confirmClearHistory = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun HistoryRow(entry: ReadingHistoryEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text("${entry.book} ${entry.chapter}", modifier = Modifier.weight(1f))
        Text(
            DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.viewedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
