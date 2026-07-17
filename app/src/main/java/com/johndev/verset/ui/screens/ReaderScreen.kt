package com.johndev.verset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.BookMeta
import com.johndev.verset.data.Prefs
import com.johndev.verset.data.Verse
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * @param jumpTarget when non-null, the reader jumps to this (bookIndex, chapter)
 *   once and then calls [onJumpConsumed] — used when Home's "Read in context"
 *   button sends the user here to a specific verse of the day.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var verseToTag by remember { mutableStateOf<Verse?>(null) } // Verse isn't Parcelable; lost on process death, acceptable for a transient dialog
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(jumpTarget) {
        jumpTarget?.let { (b, c) ->
            bookIndex = b
            chapter = c
            prefs.lastBookIndex = b
            prefs.lastChapter = c
            onJumpConsumed()
        }
    }

    // Debounce: only re-query the DB 300ms after the user stops typing, instead of on every keystroke.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchQuery) {
        delay(300)
        debouncedQuery = searchQuery
    }

    val verses by repository.chapterFlow(bookIndex, chapter).collectAsState(initial = emptyList())
    val taggedIdsList by repository.taggedVerseIds().collectAsState(initial = emptyList())
    val taggedIds = remember(taggedIdsList) { taggedIdsList.toSet() }
    val currentBook = books.find { it.bookIndex == bookIndex }
    val searchResults by (if (debouncedQuery.trim().length >= 3) repository.search(debouncedQuery.trim()) else kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())
    // Computed instantly (no debounce) since it's cheap local matching, not a DB query —
    // lets "Go to John 3:16" appear the moment it's typeable, not after a delay.
    val referenceMatch = remember(searchQuery, books) { parseReference(searchQuery, books) }

    val isLoadingBible by com.johndev.verset.data.BibleLoadState.isLoading.collectAsState()

    // When a search result or reference jump lands on a specific verse, we scroll the
    // chapter list to that verse instead of just loading the chapter at the top.
    var scrollToVerse by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToVerse, verses) {
        val target = scrollToVerse ?: return@LaunchedEffect
        if (verses.isEmpty()) return@LaunchedEffect
        val idx = verses.indexOfFirst { it.verse == target }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            scrollToVerse = null
        }
    }

    LaunchedEffect(bookIndex, chapter, currentBook) {
        currentBook?.let { book ->
            repository.recordChapterView(bookIndex, book.name, chapter)
        }
    }

    fun goToChapter(newBookIndex: Int, newChapter: Int) {
        bookIndex = newBookIndex
        chapter = newChapter
        prefs.lastBookIndex = newBookIndex
        prefs.lastChapter = newChapter
    }

    fun goNext() {
        val book = currentBook ?: return
        if (chapter < book.chapterCount) {
            goToChapter(bookIndex, chapter + 1)
        } else {
            val nextBook = books.find { it.bookIndex == bookIndex + 1 } ?: books.firstOrNull()
            nextBook?.let { goToChapter(it.bookIndex, 1) }
        }
    }

    fun goPrevious() {
        if (chapter > 1) {
            goToChapter(bookIndex, chapter - 1)
        } else {
            val prevBook = books.find { it.bookIndex == bookIndex - 1 } ?: books.lastOrNull()
            prevBook?.let { goToChapter(it.bookIndex, it.chapterCount) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (showSearch) {
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
        } else {
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
                        val context = LocalContext.current
                        var showVerseMenu by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showVerseMenu = true }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
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
                            if (isTagged) {
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
                                onDismiss = { showVerseMenu = false },
                                onTag = { verseToTag = verse; showVerseMenu = false }
                            )
                        }
                    }
                }

                // Previous/Next chapter bar — wraps across book boundaries (e.g. end of
                // Genesis 50 -> Exodus 1, start of Genesis 1 -> end of Revelation 22).
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

    if (showPicker) {
        BookChapterPicker(
            books = books,
            onDismiss = { showPicker = false },
            onSelect = { b, c ->
                goToChapter(b, c)
                showPicker = false
            }
        )
    }

    verseToTag?.let { verse ->
        TagVerseDialog(
            verse = verse,
            repository = repository,
            onDismiss = { verseToTag = null }
        )
    }
}

/**
 * Bottom-sheet-style action menu shown when a user taps a verse.
 * "Share as text" shares immediately and auto-tags the verse under "Shared".
 * "Tag / classify…" opens the full tagging dialog.
 */
@Composable
private fun VerseActionMenu(
    verse: Verse,
    repository: BibleRepository,
    onDismiss: () -> Unit,
    onTag: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    suspend fun autoTag() {
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
                        scope.launch { autoTag() }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share as text")
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookChapterPicker(
    books: List<BookMeta>,
    onDismiss: () -> Unit,
    onSelect: (bookIndex: Int, chapter: Int) -> Unit
) {
    // Full-screen modal using SearchPanel so the picker and the search bar
    // share the same Book→Chapter→Verse navigation with back arrows.
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
                searchResults = emptyList(),     // no text search inside the picker
                referenceMatch = null,
                onNavigate = { bIdx, ch, _ -> onSelect(bIdx, ch); onDismiss() },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
