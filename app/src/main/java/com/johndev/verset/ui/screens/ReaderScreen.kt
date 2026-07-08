package com.johndev.verset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.BookMeta
import com.johndev.verset.data.Prefs
import com.johndev.verset.data.Verse
import com.johndev.verset.repository.BibleRepository
import kotlinx.coroutines.delay

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

    val isLoadingBible by com.johndev.verset.data.BibleLoadState.isLoading.collectAsState()

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

        if (showSearch && debouncedQuery.trim().length >= 3) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(searchResults, key = { it.id }) { verse ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                bookIndex = verse.bookIndex
                                chapter = verse.chapter
                                prefs.lastBookIndex = verse.bookIndex
                                prefs.lastChapter = verse.chapter
                                showSearch = false
                                searchQuery = ""
                                verseToTag = verse
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            "${verse.book} ${verse.chapter}:${verse.verse}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            highlightMatches(verse.text, debouncedQuery, MaterialTheme.colorScheme.secondary),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (searchResults.isEmpty()) {
                    item { Text("No matches yet…", Modifier.padding(vertical = 16.dp)) }
                }
            }
        } else if (!showSearch) {
            if (isLoadingBible) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Setting up your Bible for the first time…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp)) {
                    items(verses, key = { it.id }) { verse ->
                        val isTagged = taggedIds.contains(verse.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    verseToTag = verse
                                    prefs.lastBookIndex = bookIndex
                                    prefs.lastChapter = chapter
                                }
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
 * Builds an AnnotatedString with each word of [query] bolded and colored where it
 * occurs in [text] (case-insensitive). Matches individual words rather than the
 * whole phrase, so a search for "love one another" highlights each of those three
 * words wherever they appear in the verse, not just an exact phrase match.
 */
private fun highlightMatches(text: String, query: String, highlightColor: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)

    // Collect all match ranges (case-insensitive), then merge overlapping ones.
    val ranges = mutableListOf<IntRange>()
    val lowerText = text.lowercase()
    for (word in words) {
        val lowerWord = word.lowercase()
        if (lowerWord.isEmpty()) continue
        var start = 0
        while (true) {
            val idx = lowerText.indexOf(lowerWord, start)
            if (idx == -1) break
            ranges.add(idx until idx + lowerWord.length)
            start = idx + lowerWord.length
        }
    }
    val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
        val last = acc.lastOrNull()
        if (last != null && r.first <= last.last + 1) {
            acc[acc.size - 1] = last.first..maxOf(last.last, r.last)
        } else {
            acc.add(r)
        }
        acc
    }

    return androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        for (range in merged) {
            if (range.first > cursor) append(text.substring(cursor, range.first))
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = highlightColor,
                    background = highlightColor.copy(alpha = 0.15f)
                )
            ) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

@Composable
private fun BookChapterPicker(
    books: List<BookMeta>,
    onDismiss: () -> Unit,
    onSelect: (bookIndex: Int, chapter: Int) -> Unit
) {
    var selectedBook by remember { mutableStateOf<BookMeta?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(selectedBook?.name ?: "Choose a book") },
        text = {
            if (selectedBook == null) {
                LazyColumn(Modifier.height(400.dp)) {
                    items(books, key = { it.bookIndex }) { book ->
                        Text(
                            book.name,
                            Modifier.fillMaxWidth().clickable { selectedBook = book }.padding(vertical = 10.dp)
                        )
                    }
                }
            } else {
                val book = selectedBook!!
                LazyColumn(Modifier.height(400.dp)) {
                    items((1..book.chapterCount).toList()) { chapterNum ->
                        Text(
                            "Chapter $chapterNum",
                            Modifier.fillMaxWidth()
                                .clickable { onSelect(book.bookIndex, chapterNum) }
                                .padding(vertical = 10.dp),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    )
}
