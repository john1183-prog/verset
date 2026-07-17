package com.johndev.verset.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johndev.verset.data.BookMeta
import com.johndev.verset.data.Verse

/**
 * Hierarchical search panel — replaces the previous flat search flow with a
 * three-level navigator (Book → Chapter → Verse) and live text-search, all
 * in one surface.
 *
 * Navigation flow:
 *   [Book suggestions / search results]
 *     → tap a book → [Chapter grid with back arrow]
 *       → tap a chapter → [Verse grid with back arrow]
 *         → tap a verse → fires [onNavigate] and closes
 *
 * The user never has to type more than a partial book name — they can tap at
 * any step and the remaining navigation is done with grids.
 */

sealed class SearchLevel {
    object Books : SearchLevel()
    data class Chapters(val book: BookMeta) : SearchLevel()
    data class Verses(val book: BookMeta, val chapter: Int, val verses: List<Verse>) : SearchLevel()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchPanel(
    query: String,
    books: List<BookMeta>,
    searchResults: List<Verse>,
    referenceMatch: ReferenceMatch?,
    onNavigate: (bookIndex: Int, chapter: Int, verse: Int?) -> Unit,
    loadVerses: (suspend (bookIndex: Int, chapter: Int) -> List<Verse>)? = null,
    modifier: Modifier = Modifier
) {
    var level by remember { mutableStateOf<SearchLevel>(SearchLevel.Books) }
    val filteredBooks = remember(query, books) {
        if (query.isBlank()) books
        else books.filter { it.name.lowercase().startsWith(query.trim().lowercase()) }
            .ifEmpty { books.filter { query.trim().lowercase() in it.name.lowercase() } }
    }

    // If query changes, reset to books level — lets typing "psa" jump back to books
    LaunchedEffect(query) {
        if (query.isNotBlank() && level !is SearchLevel.Books) {
            level = SearchLevel.Books
        }
    }

    Column(modifier.fillMaxSize()) {
        // ── Breadcrumb + back navigation ───────────────────────────────────────
        when (val l = level) {
            is SearchLevel.Chapters -> BreadcrumbBar(
                label = l.book.name,
                onBack = { level = SearchLevel.Books }
            )
            is SearchLevel.Verses -> BreadcrumbBar(
                label = "${l.book.name} ${l.chapter}",
                onBack = { level = SearchLevel.Chapters(l.book) }
            )
            else -> Unit
        }

        when (val l = level) {

            // ── Level 1: Books ─────────────────────────────────────────────────
            SearchLevel.Books -> {
                if (referenceMatch != null && query.isNotBlank()) {
                    // Reference match chip — highest priority tap target
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(referenceMatch.book.bookIndex, referenceMatch.chapter, referenceMatch.verse) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Go to ${referenceMatch.book.name} ${referenceMatch.chapter}${referenceMatch.verse?.let { ":$it" } ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider()
                }

                val showBookSuggestions = query.isBlank() || filteredBooks.isNotEmpty()
                val showTextResults = query.trim().length >= 3 && searchResults.isNotEmpty()

                if (showBookSuggestions) {
                    if (query.isNotBlank() && filteredBooks.size <= 5) {
                        // Compact chip row for a small number of matches
                        Text(
                            "Books",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            filteredBooks.forEach { book ->
                                SuggestionChip(
                                    onClick = { level = SearchLevel.Chapters(book) },
                                    label = { Text(book.name, maxLines = 1) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                    } else {
                        // Full scrollable list when showing all 66 books or many matches
                        val sectionLabel = if (query.isBlank()) "Browse by book" else "Books matching \"$query\""
                        Text(
                            sectionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        // OT / NT grouping only when showing full list
                        if (query.isBlank()) {
                            val ot = books.filter { it.testament == "OT" }
                            val nt = books.filter { it.testament == "NT" }
                            LazyColumn(Modifier.weight(if (showTextResults) 0.4f else 1f)) {
                                item {
                                    TestamentLabel("Old Testament")
                                }
                                lazyItems(ot, key = { it.bookIndex }) { book ->
                                    BookRow(book) { level = SearchLevel.Chapters(book) }
                                }
                                item { TestamentLabel("New Testament") }
                                lazyItems(nt, key = { it.bookIndex }) { book ->
                                    BookRow(book) { level = SearchLevel.Chapters(book) }
                                }
                            }
                        } else {
                            LazyColumn(Modifier.weight(if (showTextResults) 0.4f else 1f)) {
                                lazyItems(filteredBooks, key = { it.bookIndex }) { book ->
                                    BookRow(book) { level = SearchLevel.Chapters(book) }
                                }
                            }
                        }
                    }
                }

                if (showTextResults) {
                    HorizontalDivider()
                    Text(
                        "Verses containing \"${query.trim()}\"",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyColumn(Modifier.weight(if (showBookSuggestions) 0.6f else 1f)) {
                        lazyItems(searchResults.take(100), key = { it.id }) { verse ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate(verse.bookIndex, verse.chapter, verse.verse) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "${verse.book} ${verse.chapter}:${verse.verse}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    highlightMatches(verse.text, query, MaterialTheme.colorScheme.secondary),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }

                if (!showBookSuggestions && !showTextResults && query.isNotBlank()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No matches for \"$query\"", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ── Level 2: Chapter grid ─────────────────────────────────────────
            is SearchLevel.Chapters -> {
                var loadingChapter by remember { mutableStateOf<Int?>(null) }
                val scope = rememberCoroutineScope()

                Text(
                    "Select a chapter",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lazyGridItems(
                        items = (1..l.book.chapterCount).toList()
                    ) { ch ->
                        val isLoading = loadingChapter == ch
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isLoading) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable(enabled = loadingChapter == null) {
                                    if (loadVerses != null) {
                                        // Load verses then transition to verse grid
                                        loadingChapter = ch
                                        scope.launch {
                                            val verses = loadVerses(l.book.bookIndex, ch)
                                            if (verses.isNotEmpty()) {
                                                level = SearchLevel.Verses(l.book, ch, verses)
                                            } else {
                                                // Empty chapter (shouldn't happen) — navigate directly
                                                onNavigate(l.book.bookIndex, ch, null)
                                            }
                                            loadingChapter = null
                                        }
                                    } else {
                                        // Picker mode (no verse selection needed) — navigate directly
                                        onNavigate(l.book.bookIndex, ch, null)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "$ch",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
            }

            // ── Level 3: Verse grid ────────────────────────────────────────────
            is SearchLevel.Verses -> {
                Text(
                    "Select a verse",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lazyGridItems(l.verses, key = { it.id }) { v ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { onNavigate(l.book.bookIndex, l.chapter, v.verse) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${v.verse}",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(label: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TestamentLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun BookRow(book: BookMeta, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(book.name, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${book.chapterCount} ch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider()
}
