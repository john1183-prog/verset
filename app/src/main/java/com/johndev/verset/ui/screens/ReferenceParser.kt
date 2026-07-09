package com.johndev.verset.ui.screens

import com.johndev.verset.data.BookMeta

data class ReferenceMatch(val book: BookMeta, val chapter: Int, val verse: Int?)

/**
 * Parses free-typed search input as a Bible reference so the search bar can jump
 * straight to a book/chapter/verse instead of only full-text matching.
 *
 * Handles: "John 3:16", "John 3", "Genesis", "gen 1" (partial book name prefix),
 * "1 John 4", "Song of Solomon 2:1". Book matching is prefix-based — the shortest
 * book name that starts with the typed prefix wins on ambiguous input (e.g. "jo"
 * could mean Job, John, Joel, Jonah, or Joshua; Job wins since it's shortest).
 * That ambiguity is an accepted trade-off for a simple, fast, local-only parser.
 */
fun parseReference(query: String, books: List<BookMeta>): ReferenceMatch? {
    val trimmed = query.trim()
    if (trimmed.length < 2) return null

    val trailingNumbers = Regex("^(.*?)\\s+(\\d{1,3})(?::(\\d{1,3}))?$").find(trimmed)
    val bookPart: String
    var chapter: Int? = null
    var verse: Int? = null

    if (trailingNumbers != null) {
        bookPart = trailingNumbers.groupValues[1].trim()
        chapter = trailingNumbers.groupValues[2].toIntOrNull()
        verse = trailingNumbers.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
    } else {
        bookPart = trimmed
    }
    if (bookPart.length < 2) return null

    val bookPartLower = bookPart.lowercase()
    val book = books.find { it.name.lowercase() == bookPartLower }
        ?: books.filter { it.name.lowercase().startsWith(bookPartLower) }.minByOrNull { it.name.length }
        ?: return null

    val safeChapter = (chapter ?: 1).coerceIn(1, book.chapterCount)
    return ReferenceMatch(book, safeChapter, verse)
}
