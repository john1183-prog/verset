package com.johndev.verset.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.johndev.verset.data.BookMeta
import com.johndev.verset.data.Prefs
import com.johndev.verset.data.Verse
import com.johndev.verset.data.verseId
import com.johndev.verset.export.CardTheme
import com.johndev.verset.export.ImageAlign
import com.johndev.verset.export.ImageCardExporter
import com.johndev.verset.export.ImageFont
import com.johndev.verset.export.VerseCardItem
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @param jumpTarget when non-null, the reader jumps to this (bookIndex, chapter)
 *   once and then calls [onJumpConsumed] — used when Home's "Read in context"
 *   button sends the user here to a specific verse of the day.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    repository: BibleRepository,
    prefs: Prefs,
    jumpTarget: Pair<Int, Int>? = null,
    onJumpConsumed: () -> Unit = {}
) {
    val books by repository.booksFlow().collectAsState(initial = emptyList())
    var bookIndex by rememberSaveable { mutableStateOf(prefs.lastBookIndex) }
    var chapter by rememberSaveable { mutableStateOf(prefs.lastChapter) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var verseToTag by remember { mutableStateOf<Verse?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchThisBookOnly by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Multi-verse selection: long-press a verse and choose "Select multiple…" to start,
    // then tap other verses to add them to the same share/image. Sharing several verses
    // at once (a passage) reuses the exact same text-share and image-export pipeline as
    // a single verse — it's just given more than one VerseCardItem.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedVerseIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showMultiThemePicker by remember { mutableStateOf(false) }
    var multiExportDraft by remember { mutableStateOf<ExportDraft?>(null) }

    fun toggleVerseSelection(id: Long) {
        selectedVerseIds = if (selectedVerseIds.contains(id)) selectedVerseIds - id else selectedVerseIds + id
    }
    fun exitSelection() {
        selectionMode = false
        selectedVerseIds = emptySet()
    }
    suspend fun autoTagSelected(selected: List<Verse>) {
        if (!prefs.autoTagOnShare) return
        val sharedTag = repository.getOrCreateTag("Shared", "#6B4A8B")
        selected.forEach { repository.saveEntry(it, sharedTag.id, "") }
    }
    fun shareSelectedAsText(selected: List<Verse>) {
        if (selected.isEmpty()) return
        val textToShare = selected.joinToString("\n\n") { v -> "\"${v.text}\"\n— ${v.book} ${v.chapter}:${v.verse}" }
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share verses"))
        scope.launch { autoTagSelected(selected) }
        exitSelection()
    }

    // ── Immersive/full-screen reading mode ──────────────────────────────────
    // Tap anywhere on the verse list toggles the top bar, prev/next bar, and
    // system status/nav bars — long-press a verse to open its action menu.
    var immersiveMode by remember { mutableStateOf(false) }
    val view = LocalView.current
    LaunchedEffect(immersiveMode) {
        val activity = view.context as? android.app.Activity ?: return@LaunchedEffect
        val window = activity.window
        // Allow content to draw behind system bars so the cream reader background
        // fills the status bar area instead of leaving a solid black rectangle.
        WindowCompat.setDecorFitsSystemWindows(window, !immersiveMode)
        val controller = WindowCompat.getInsetsController(window, view)
        if (immersiveMode) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            val activity = view.context as? android.app.Activity
            if (activity != null) {
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(jumpTarget) {
        jumpTarget?.let { (b, c) ->
            bookIndex = b
            chapter = c
            prefs.lastBookIndex = b
            prefs.lastChapter = c
            onJumpConsumed()
        }
    }

    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    val verses by repository.chapterFlow(bookIndex, chapter).collectAsState(initial = emptyList())
    val taggedIdsList by repository.taggedVerseIds().collectAsState(initial = emptyList())
    val taggedIds = remember(taggedIdsList) { taggedIdsList.toSet() }
    val currentBook = books.find { it.bookIndex == bookIndex }

    var searchResults by remember { mutableStateOf<List<Verse>>(emptyList()) }
    var searchTotalCount by remember { mutableStateOf(0) }
    var searchCorrections by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var searchLoading by remember { mutableStateOf(false) }

    LaunchedEffect(debouncedQuery, searchThisBookOnly, bookIndex) {
        val trimmed = debouncedQuery.trim()
        if (trimmed.length < 3) {
            searchResults = emptyList(); searchTotalCount = 0; searchCorrections = emptyMap()
            return@LaunchedEffect
        }
        searchLoading = true
        val result = repository.searchVerses(trimmed, if (searchThisBookOnly) bookIndex else null)
        searchResults = result.verses
        searchTotalCount = result.totalCount
        searchCorrections = result.correctedWords
        searchLoading = false
    }

    val referenceMatch = remember(searchQuery, books) { parseReference(searchQuery, books) }
    val isLoadingBible by com.johndev.verset.data.BibleLoadState.isLoading.collectAsState()

    // Scroll-to + temporary highlight when arriving via search/reference jump
    var scrollToVerse by remember { mutableStateOf<Int?>(null) }
    var highlightedVerseId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToVerse, verses) {
        val target = scrollToVerse ?: return@LaunchedEffect
        if (verses.isEmpty()) return@LaunchedEffect
        val idx = verses.indexOfFirst { it.verse == target }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            highlightedVerseId = verseId(bookIndex, chapter, target)
            scrollToVerse = null
        }
    }
    LaunchedEffect(highlightedVerseId) {
        if (highlightedVerseId != null) {
            delay(2500)
            highlightedVerseId = null
        }
    }

    LaunchedEffect(bookIndex, chapter, currentBook) {
        currentBook?.let { book -> repository.recordChapterView(bookIndex, book.name, chapter) }
    }

    fun goToChapter(newBookIndex: Int, newChapter: Int) {
        bookIndex = newBookIndex
        chapter = newChapter
        prefs.lastBookIndex = newBookIndex
        prefs.lastChapter = newChapter
    }

    fun goNext() {
        val book = currentBook ?: return
        if (chapter < book.chapterCount) goToChapter(bookIndex, chapter + 1)
        else (books.find { it.bookIndex == bookIndex + 1 } ?: books.firstOrNull())?.let { goToChapter(it.bookIndex, 1) }
    }

    fun goPrevious() {
        if (chapter > 1) goToChapter(bookIndex, chapter - 1)
        else (books.find { it.bookIndex == bookIndex - 1 } ?: books.lastOrNull())?.let { goToChapter(it.bookIndex, it.chapterCount) }
    }

    Column(Modifier.fillMaxSize()) {
        if (selectionMode) {
            TopAppBar(
                title = { Text("${selectedVerseIds.size} selected") },
                navigationIcon = {
                    IconButton(onClick = { exitSelection() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                    }
                },
                actions = {
                    IconButton(
                        enabled = selectedVerseIds.isNotEmpty(),
                        onClick = { shareSelectedAsText(verses.filter { selectedVerseIds.contains(it.id) }) }
                    ) { Icon(Icons.Filled.Share, contentDescription = "Share selected as text") }
                    IconButton(
                        enabled = selectedVerseIds.isNotEmpty(),
                        onClick = { showMultiThemePicker = true }
                    ) { Icon(Icons.Filled.Image, contentDescription = "Share selected as image") }
                }
            )
        } else if (showSearch) {
            val focusRequester = remember { FocusRequester() }
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search KJV text…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                actions = {
                    IconButton(onClick = { showSearch = false; searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                }
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }

            if (currentBook != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = searchThisBookOnly,
                        onClick = { searchThisBookOnly = !searchThisBookOnly },
                        label = { Text("Search in ${currentBook.name} only") }
                    )
                }
            }
        } else if (!immersiveMode) {
            TopAppBar(
                title = {
                    TextButton(onClick = { showPicker = true }) {
                        Text("${currentBook?.name ?: "…"} $chapter")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            )
        }

        if (showSearch) {
            SearchPanel(
                query = searchQuery,
                books = books,
                searchResults = searchResults,
                searchTotalCount = searchTotalCount,
                searchCorrections = searchCorrections,
                searchLoading = searchLoading,
                referenceMatch = referenceMatch,
                onNavigate = { bIdx, ch, verseNum ->
                    goToChapter(bIdx, ch)
                    if (verseNum != null) scrollToVerse = verseNum
                    showSearch = false
                    searchQuery = ""
                },
                loadVerses = { bIdx, ch -> repository.chapterOnce(bIdx, ch) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            if (isLoadingBible) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Setting up your Bible for the first time…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                ) {
                    itemsIndexed(verses, key = { _, v -> v.id }) { _, verse ->
                        val isTagged = taggedIds.contains(verse.id)
                        val isSelected = selectedVerseIds.contains(verse.id)
                        var showVerseMenu by remember { mutableStateOf(false) }
                        val isHighlighted = verse.id == highlightedVerseId
                        val bgColor by animateColorAsState(
                            targetValue = when {
                                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                                isHighlighted -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 300),
                            label = "verseHighlight"
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(bgColor)
                                .combinedClickable(
                                    onClick = {
                                        if (selectionMode) toggleVerseSelection(verse.id) else immersiveMode = !immersiveMode
                                    },
                                    onLongClick = {
                                        if (selectionMode) toggleVerseSelection(verse.id) else showVerseMenu = true
                                    }
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left-border accent for tagged verses — more scannable than the
                            // small bookmark icon alone, especially when reading quickly.
                            if (isTagged && !selectionMode) {
                                Box(
                                    Modifier
                                        .width(3.dp)
                                        .height(with(androidx.compose.ui.platform.LocalDensity.current) {
                                            // Approximate height of one verse row — grows with font scale
                                            (prefs.fontScale * 56).dp
                                        })
                                        .background(MaterialTheme.colorScheme.secondary)
                                        .padding(end = 4.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (selectionMode) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { toggleVerseSelection(verse.id) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            Text(
                                "${verse.verse}",
                                modifier = Modifier.width(28.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                verse.text,
                                style = com.johndev.verset.ui.theme.readerTextStyle(prefs.fontScale),
                                modifier = Modifier.weight(1f)
                            )
                            if (isTagged && !selectionMode) {
                                Icon(
                                    Icons.Filled.Bookmark,
                                    contentDescription = "Tagged",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(start = 6.dp).size(18.dp)
                                )
                            }
                        }
                        if (showVerseMenu) {
                            VerseActionMenu(
                                verse = verse,
                                repository = repository,
                                prefs = prefs,
                                onDismiss = { showVerseMenu = false },
                                onTag = { verseToTag = verse; showVerseMenu = false },
                                onStartSelection = {
                                    selectionMode = true
                                    selectedVerseIds = setOf(verse.id)
                                    showVerseMenu = false
                                }
                            )
                        }
                    }
                }

                if (!immersiveMode && !selectionMode) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(onClick = { goPrevious() }) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null)
                            Text("Previous")
                        }
                        OutlinedButton(onClick = { goNext() }) {
                            Text("Next")
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        BookChapterPicker(
            books = books,
            onDismiss = { showPicker = false },
            onSelect = { b, c -> goToChapter(b, c); showPicker = false }
        )
    }

    verseToTag?.let { verse ->
        TagVerseDialog(verse = verse, repository = repository, onDismiss = { verseToTag = null })
    }

    if (showMultiThemePicker) {
        ThemePickerDialog(
            onDismiss = { showMultiThemePicker = false },
            onPick = { theme ->
                val selected = verses.filter { selectedVerseIds.contains(it.id) }
                multiExportDraft = ExportDraft(
                    items = selected.map { VerseCardItem("— ${it.book} ${it.chapter}:${it.verse}", it.text) },
                    note = "",
                    tagLabel = "",
                    theme = theme
                )
                showMultiThemePicker = false
            }
        )
    }

    multiExportDraft?.let { draft ->
        EditableExportDialog(
            draft = draft,
            onDismiss = { multiExportDraft = null },
            onExport = { edited ->
                val selected = verses.filter { selectedVerseIds.contains(it.id) }
                multiExportDraft = null
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        ImageCardExporter.export(
                            context, edited.items, edited.note, edited.tagLabel, edited.theme, edited.font, edited.align
                        )
                    }
                    if (uri != null) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share verse image"))
                    }
                    autoTagSelected(selected)
                    exitSelection()
                }
            }
        )
    }
}

/**
 * Bottom-sheet-style action menu shown on long-press of a verse.
 * "Share as text" and "Share as image" both share immediately without
 * requiring the verse to be tagged first — if [Prefs.autoTagOnShare] is on
 * (default), the verse also gets filed under a "Shared" tag afterward so it's
 * easy to find again; that behavior is a Settings toggle, not forced.
 */
@Composable
private fun VerseActionMenu(
    verse: Verse,
    repository: BibleRepository,
    prefs: Prefs,
    onDismiss: () -> Unit,
    onTag: () -> Unit,
    onStartSelection: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showThemePicker by remember { mutableStateOf(false) }
    var exportDraft by remember { mutableStateOf<ExportDraft?>(null) }

    suspend fun autoTagIfEnabled() {
        if (!prefs.autoTagOnShare) return
        val sharedTag = repository.getOrCreateTag("Shared", "#6B4A8B")
        repository.saveEntry(verse, sharedTag.id, "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${verse.book} ${verse.chapter}:${verse.verse}") },
        text = {
            Column {
                Text(
                    verse.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                TextButton(
                    onClick = {
                        val textToShare = "\"${verse.text}\"\n— ${verse.book} ${verse.chapter}:${verse.verse}"
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share verse"))
                        scope.launch { autoTagIfEnabled() }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share as text")
                }
                TextButton(
                    onClick = { showThemePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share as image")
                }
                TextButton(
                    onClick = { onStartSelection() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select multiple…")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TextButton(onClick = { onTag() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tag / classify…")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showThemePicker) {
        ThemePickerDialog(
            onDismiss = { showThemePicker = false; onDismiss() },
            onPick = { theme ->
                exportDraft = ExportDraft(
                    items = listOf(VerseCardItem("— ${verse.book} ${verse.chapter}:${verse.verse}", verse.text)),
                    note = "",
                    tagLabel = "",
                    theme = theme
                )
                showThemePicker = false
            }
        )
    }

    exportDraft?.let { draft ->
        EditableExportDialog(
            draft = draft,
            onDismiss = { exportDraft = null; onDismiss() },
            onExport = { edited ->
                exportDraft = null
                onDismiss()
                scope.launch {
                    val uri = withContext(Dispatchers.IO) {
                        ImageCardExporter.export(
                            context, edited.items, edited.note, edited.tagLabel, edited.theme, edited.font, edited.align
                        )
                    }
                    if (uri != null) {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share verse image"))
                    }
                    autoTagIfEnabled()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookChapterPicker(
    books: List<BookMeta>,
    onDismiss: () -> Unit,
    onSelect: (bookIndex: Int, chapter: Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.92f)) {
        var pickerQuery by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = pickerQuery,
                onValueChange = { pickerQuery = it },
                placeholder = { Text("Filter books…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SearchPanel(
                query = pickerQuery,
                books = books,
                searchResults = emptyList(),
                referenceMatch = null,
                onNavigate = { bIdx, ch, _ -> onSelect(bIdx, ch); onDismiss() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
