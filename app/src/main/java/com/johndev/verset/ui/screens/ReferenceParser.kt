package com.johndev.verset.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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

/**
 * Builds an AnnotatedString with each word of [query] bolded and colored where it
 * occurs in [text] (case-insensitive). Defined here so both ReaderScreen and
 * SearchPanel can use it without duplication.
 */
internal fun highlightMatches(text: String, query: String, highlightColor: Color): AnnotatedString {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return AnnotatedString(text)

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
        } else acc.add(r)
        acc
    }

    return buildAnnotatedString {
        var cursor = 0
        for (range in merged) {
            if (range.first > cursor) append(text.substring(cursor, range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor, background = highlightColor.copy(alpha = 0.15f))) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}
