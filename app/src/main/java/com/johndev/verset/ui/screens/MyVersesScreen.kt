package com.johndev.verset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.Tag
import com.johndev.verset.data.VerseTagEntry
import com.johndev.verset.export.ImageAlign
import com.johndev.verset.export.ImageCardExporter
import com.johndev.verset.export.ImageFont
import com.johndev.verset.export.PdfExporter
import com.johndev.verset.export.VerseCardItem
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVersesScreen(repository: BibleRepository, onReadInContext: (bookIndex: Int, chapter: Int) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val tags by repository.tagsFlow().collectAsState(initial = emptyList())
    val allEntries by repository.allEntriesFlow().collectAsState(initial = emptyList())
    var selectedTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedTag = tags.find { it.id == selectedTagId }
    var showAllVerses by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var themePickerFor by remember { mutableStateOf<VerseTagEntry?>(null) }
    var exportDraft by remember { mutableStateOf<ExportDraft?>(null) }
    var pdfExportRequest by remember { mutableStateOf<PdfExportRequest?>(null) }
    var multiExportRequest by remember { mutableStateOf<MultiExportRequest?>(null) }

    // Selection mode lets you pick several saved verses and export them together as one
    // image (e.g. a passage spanning a few verses), instead of one image per verse.
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedEntryIds by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    fun toggleEntrySelection(id: Long) {
        selectedEntryIds = if (selectedEntryIds.contains(id)) selectedEntryIds - id else selectedEntryIds + id
    }
    fun exitSelection() {
        selectionMode = false
        selectedEntryIds = emptySet()
    }

    fun runPdfExport(request: PdfExportRequest, theme: com.johndev.verset.export.PdfTheme) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                PdfExporter.export(context, request.title, request.entries, theme) != null
            }
            snackbarHostState.showSnackbar(if (ok) "Saved PDF to Downloads/Verset" else "Export failed")
        }
    }

    fun beginExport(entry: VerseTagEntry, tagLabel: String, theme: com.johndev.verset.export.CardTheme) {
        exportDraft = ExportDraft(
            items = listOf(VerseCardItem("— ${entry.book} ${entry.chapter}:${entry.verse}", entry.verseText)),
            note = entry.note,
            tagLabel = tagLabel,
            theme = theme
        )
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.padding(padding)) {
            if (selectedTag == null) {
                Column(Modifier.fillMaxSize()) {
                    // Tags / All Verses toggle, plus "export everything" — only useful once
                    // there's actually something saved.
                    if (tags.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            SingleChoiceSegment(
                                options = listOf("By Tag", "All Verses"),
                                selectedIndex = if (showAllVerses) 1 else 0,
                                onSelect = { showAllVerses = it == 1; if (!showAllVerses) exitSelection() }
                            )
                            Row {
                                if (showAllVerses) {
                                    IconButton(onClick = { if (selectionMode) exitSelection() else selectionMode = true }) {
                                        Icon(
                                            if (selectionMode) Icons.Filled.Close else Icons.Filled.DoneAll,
                                            contentDescription = if (selectionMode) "Cancel selection" else "Select multiple verses"
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    val pairs = allEntries.mapNotNull { e -> tags.find { it.id == e.tagId }?.let { e to it } }
                                    pdfExportRequest = PdfExportRequest("All Saved Verses", pairs)
                                }) { Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export everything as PDF") }
                            }
                        }
                        if (selectionMode && showAllVerses) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text("${selectedEntryIds.size} selected", style = MaterialTheme.typography.labelLarge)
                                TextButton(
                                    enabled = selectedEntryIds.isNotEmpty(),
                                    onClick = {
                                        val selected = allEntries.filter { selectedEntryIds.contains(it.id) }
                                        val distinctTagIds = selected.map { it.tagId }.distinct()
                                        val label = if (distinctTagIds.size == 1) tags.find { it.id == distinctTagIds.first() }?.name ?: "" else ""
                                        multiExportRequest = MultiExportRequest(selected, label)
                                    }
                                ) { Text("Export as image") }
                            }
                        }
                    }

                    if (tags.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("No tags yet. Tap a verse in Read to classify it.", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else if (showAllVerses) {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(allEntries, key = { it.id }) { entry ->
                                val tagName = tags.find { it.id == entry.tagId }?.name ?: "?"
                                EntryRow(
                                    entry = entry,
                                    tagNameLabel = tagName,
                                    selectionMode = selectionMode,
                                    selected = selectedEntryIds.contains(entry.id),
                                    onToggleSelect = { toggleEntrySelection(entry.id) },
                                    onExportImage = { themePickerFor = entry },
                                    onDelete = { scope.launch { repository.deleteEntry(entry) } },
                                    onSaveNote = { newNote -> scope.launch { repository.updateEntry(entry.copy(note = newNote)) } },
                                    onReadInContext = { onReadInContext((entry.verseId / 1_000_000L).toInt(), entry.chapter) },
                                    onShareText = {
                                        val text = "\"${entry.verseText}\"\n— ${entry.book} ${entry.chapter}:${entry.verse}"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share verse"))
                                    }
                                )
                                HorizontalDivider()
                            }
                            if (allEntries.isEmpty()) {
                                item { Text("Nothing saved yet.", Modifier.padding(vertical = 16.dp)) }
                            }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
                                    modifier = Modifier.clickable { selectedTagId = tag.id },
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
                                            val nameTaken = tags.any { it.id != tag.id && it.name.equals(newName, ignoreCase = true) }
                                            if (nameTaken) {
                                                scope.launch { snackbarHostState.showSnackbar("A tag named \"$newName\" already exists") }
                                            } else {
                                                scope.launch { repository.updateTag(tag.copy(name = newName, colorHex = newColor)) }
                                                editing = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val tag = selectedTag!!
                val entries by repository.entriesForTag(tag.id).collectAsState(initial = emptyList())

                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text(if (selectionMode) "${selectedEntryIds.size} selected" else tag.name) },
                        navigationIcon = {
                            IconButton(onClick = { if (selectionMode) exitSelection() else selectedTagId = null }) {
                                Icon(
                                    if (selectionMode) Icons.Filled.Close else Icons.Filled.ArrowBack,
                                    contentDescription = if (selectionMode) "Cancel selection" else "Back"
                                )
                            }
                        },
                        actions = {
                            if (selectionMode) {
                                TextButton(
                                    enabled = selectedEntryIds.isNotEmpty(),
                                    onClick = {
                                        val selected = entries.filter { selectedEntryIds.contains(it.id) }
                                        multiExportRequest = MultiExportRequest(selected, tag.name)
                                    }
                                ) { Text("Export as image") }
                            } else {
                                IconButton(onClick = { selectionMode = true }) {
                                    Icon(Icons.Filled.DoneAll, contentDescription = "Select multiple verses")
                                }
                                IconButton(onClick = {
                                    pdfExportRequest = PdfExportRequest(tag.name, entries.map { it to tag })
                                }) { Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export PDF") }
                            }
                        }
                    )

                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        items(entries, key = { it.id }) { entry ->
                            EntryRow(
                                entry = entry,
                                tagNameLabel = null,
                                selectionMode = selectionMode,
                                selected = selectedEntryIds.contains(entry.id),
                                onToggleSelect = { toggleEntrySelection(entry.id) },
                                onExportImage = { themePickerFor = entry },
                                onDelete = { scope.launch { repository.deleteEntry(entry) } },
                                onSaveNote = { newNote -> scope.launch { repository.updateEntry(entry.copy(note = newNote)) } },
                                onReadInContext = { onReadInContext((entry.verseId / 1_000_000L).toInt(), entry.chapter) },
                                onShareText = {
                                    val text = "\"${entry.verseText}\"\n— ${entry.book} ${entry.chapter}:${entry.verse}"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share verse"))
                                }
                            )
                            HorizontalDivider()
                        }
                    }

                    themePickerFor?.let { entry ->
                        ThemePickerDialog(
                            onDismiss = { themePickerFor = null },
                            onPick = { theme ->
                                beginExport(entry, tag.name, theme)
                                themePickerFor = null
                            }
                        )
                    }
                }
            }

            // Theme picker also needs to work when triggered from the "All Verses" flat
            // list, where there's no single `tag` in scope — look the tag up by id.
            if (selectedTag == null) {
                themePickerFor?.let { entry ->
                    val entryTagName = tags.find { it.id == entry.tagId }?.name ?: "Verset"
                    ThemePickerDialog(
                        onDismiss = { themePickerFor = null },
                        onPick = { theme ->
                            beginExport(entry, entryTagName, theme)
                            themePickerFor = null
                        }
                    )
                }
            }

            multiExportRequest?.let { request ->
                ThemePickerDialog(
                    onDismiss = { multiExportRequest = null },
                    onPick = { theme ->
                        exportDraft = ExportDraft(
                            items = request.entries.map { VerseCardItem("— ${it.book} ${it.chapter}:${it.verse}", it.verseText) },
                            note = "",
                            tagLabel = request.tagLabel,
                            theme = theme
                        )
                        multiExportRequest = null
                    }
                )
            }

            exportDraft?.let { draft ->
                EditableExportDialog(
                    draft = draft,
                    onDismiss = { exportDraft = null },
                    onExport = { edited ->
                        exportDraft = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                ImageCardExporter.export(
                                    context, edited.items, edited.note, edited.tagLabel, edited.theme, edited.font, edited.align
                                ) != null
                            }
                            snackbarHostState.showSnackbar(if (ok) "Saved image to Pictures/Verset" else "Export failed")
                            exitSelection()
                        }
                    }
                )
            }

            pdfExportRequest?.let { request ->
                PdfThemePickerDialog(
                    onDismiss = { pdfExportRequest = null },
                    onPick = { theme ->
                        runPdfExport(request, theme)
                        pdfExportRequest = null
                    }
                )
            }
        }
    }
}

internal data class PdfExportRequest(
    val title: String,
    val entries: List<Pair<VerseTagEntry, Tag>>
)

internal data class MultiExportRequest(
    val entries: List<VerseTagEntry>,
    val tagLabel: String
)

@Composable
private fun SingleChoiceSegment(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier
                    .clickable { onSelect(i) }
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal data class ExportDraft(
    val items: List<VerseCardItem>,
    val note: String,
    val tagLabel: String,
    val theme: com.johndev.verset.export.CardTheme,
    val font: ImageFont = ImageFont.SANS,
    val align: ImageAlign = ImageAlign.LEFT
)

/**
 * Shown after picking a card style — lets you tweak the content, font, and
 * alignment before the image is actually generated. The exporter auto-fits font
 * size to whatever ends up here (all verses plus the note), so edits, added/removed
 * verses, and font/alignment changes all still render well.
 *
 * With a single verse, its reference and text are directly editable. With several
 * (from multi-select), editing each one's wording here would be a lot of UI for
 * little benefit, so instead each is listed with a way to drop it, and the shared
 * note stays editable either way.
 */
@Composable
internal fun EditableExportDialog(draft: ExportDraft, onDismiss: () -> Unit, onExport: (ExportDraft) -> Unit) {
    val isSingle = draft.items.size == 1
    var reference by remember { mutableStateOf(draft.items.firstOrNull()?.reference ?: "") }
    var verseText by remember { mutableStateOf(draft.items.firstOrNull()?.verseText ?: "") }
    var pendingItems by remember { mutableStateOf(draft.items) }
    var note by remember { mutableStateOf(draft.note) }
    var font by remember { mutableStateOf(draft.font) }
    var align by remember { mutableStateOf(draft.align) }
    var fontMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit before exporting") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (isSingle) {
                    OutlinedTextField(
                        value = reference,
                        onValueChange = { reference = it },
                        label = { Text("Reference") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = verseText,
                        onValueChange = { verseText = it },
                        label = { Text("Verse text") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("${pendingItems.size} verses selected", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    pendingItems.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.reference, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Text(
                                    item.verseText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { pendingItems = pendingItems - item },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove ${item.reference}", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("Font", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(onClick = { fontMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(font.displayName)
                    }
                    DropdownMenu(expanded = fontMenuExpanded, onDismissRequest = { fontMenuExpanded = false }) {
                        ImageFont.values().forEach { f ->
                            DropdownMenuItem(text = { Text(f.displayName) }, onClick = { font = f; fontMenuExpanded = false })
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Alignment", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegment(
                    options = ImageAlign.values().map { it.displayName },
                    selectedIndex = ImageAlign.values().indexOf(align),
                    onSelect = { align = ImageAlign.values()[it] }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = if (isSingle) verseText.isNotBlank() else pendingItems.isNotEmpty(),
                onClick = {
                    val finalItems = if (isSingle) {
                        listOf(VerseCardItem(reference.trim(), verseText.trim()))
                    } else {
                        pendingItems
                    }
                    onExport(draft.copy(items = finalItems, note = note.trim(), font = font, align = align))
                }
            ) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun ThemePickerDialog(onDismiss: () -> Unit, onPick: (com.johndev.verset.export.CardTheme) -> Unit) {
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

@Composable
internal fun PdfThemePickerDialog(onDismiss: () -> Unit, onPick: (com.johndev.verset.export.PdfTheme) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a PDF style") },
        text = {
            Column {
                com.johndev.verset.export.PdfTheme.values().forEach { theme ->
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
    tagNameLabel: String?,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onExportImage: () -> Unit,
    onDelete: () -> Unit,
    onSaveNote: (String) -> Unit,
    onReadInContext: () -> Unit = {},
    onShareText: () -> Unit = {}
) {
    var editingNote by remember { mutableStateOf(false) }
    var draftNote by remember { mutableStateOf(entry.note) }
    var confirmDelete by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .then(if (selectionMode) Modifier.clickable { onToggleSelect() } else Modifier),
        verticalAlignment = androidx.compose.ui.Alignment.Top
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.padding(top = 2.dp, end = 4.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            if (tagNameLabel != null) {
                Text(
                    tagNameLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    "${entry.book} ${entry.chapter}:${entry.verse}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (!selectionMode) {
                    IconButton(onClick = onExportImage) {
                        Icon(Icons.Filled.Image, contentDescription = "Export as image")
                    }
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
            if (!selectionMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    TextButton(onClick = { draftNote = entry.note; editingNote = true }) { Text("Edit note") }
                    TextButton(onClick = onReadInContext) { Text("Read") }
                    TextButton(onClick = onShareText) { Text("Share") }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove verse?") },
            text = { Text("\"${entry.verseText.take(80)}…\"\n\nThis removes it from your saved verses. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
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
