package com.johndev.verset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.BookMeta
import com.johndev.verset.data.Prefs
import com.johndev.verset.data.Verse
import com.johndev.verset.repository.BibleRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(repository: BibleRepository, prefs: Prefs) {
    val books by repository.booksFlow().collectAsState(initial = emptyList())
    var bookIndex by remember { mutableStateOf(prefs.lastBookIndex) }
    var chapter by remember { mutableStateOf(prefs.lastChapter) }
    var showPicker by remember { mutableStateOf(false) }
    var verseToTag by remember { mutableStateOf<Verse?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val verses by repository.chapterFlow(bookIndex, chapter).collectAsState(initial = emptyList())
    val taggedIds by repository.taggedVerseIds().collectAsState(initial = emptyList())
    val currentBook = books.find { it.bookIndex == bookIndex }
    val searchResults by (if (searchQuery.trim().length >= 3) repository.search(searchQuery.trim()) else kotlinx.coroutines.flow.flowOf(emptyList()))
        .collectAsState(initial = emptyList())

    val isLoadingBible by com.johndev.verset.data.BibleLoadState.isLoading.collectAsState()

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

        if (showSearch && searchQuery.trim().length >= 3) {
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
                        Text(verse.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (searchResults.isEmpty()) {
                    item { Text("No matches yet…", Modifier.padding(vertical = 16.dp)) }
                }
            }
        } else if (!showSearch) {
            if (isLoadingBible) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Setting up your Bible for the first time…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
            }
        }
    }

    if (showPicker) {
        BookChapterPicker(
            books = books,
            onDismiss = { showPicker = false },
            onSelect = { b, c ->
                bookIndex = b
                chapter = c
                prefs.lastBookIndex = b
                prefs.lastChapter = c
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
