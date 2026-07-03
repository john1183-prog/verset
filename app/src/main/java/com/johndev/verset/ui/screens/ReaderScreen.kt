package com.johndev.verset.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val verses by repository.chapterFlow(bookIndex, chapter).collectAsState(initial = emptyList())
    val taggedIds by repository.taggedVerseIds().collectAsState(initial = emptyList())
    val currentBook = books.find { it.bookIndex == bookIndex }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                TextButton(onClick = { showPicker = true }) {
                    Text("${currentBook?.name ?: "…"} $chapter")
                }
            }
        )

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
