package com.johndev.verset.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import com.johndev.verset.data.Tag
import com.johndev.verset.data.VerseTagEntry

/** A named color scheme for exported verse PDFs — mirrors [CardTheme] so the two export
 * flows feel like the same product, just different output formats. */
enum class PdfTheme(
    val displayName: String,
    val background: String,
    val accent: String,
    val bodyText: String,
    val noteText: String
) {
    NAVY_GOLD("Navy & Gold", "#1B2A4A", "#C9A24B", "#FFFFFF", "#D8D8D8"),
    PARCHMENT("Parchment", "#F4E9CD", "#8B4A2C", "#3A2E1E", "#6B5B45"),
    CHARCOAL_ROSE("Charcoal & Rose", "#1E1E24", "#C97C7C", "#F2F2F2", "#B8B0B0"),
    FOREST("Forest", "#1E3A2E", "#8FBF7F", "#F4F4F0", "#C4D4C0")
}

/**
 * Exports a tag's full verse+note list (or "all entries") as a multi-page PDF
 * using Android's built-in PdfDocument — no extra library needed.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 612  // US Letter at 72dpi
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 56f
    private const val ACCENT_BAR_WIDTH = 10f

    fun export(
        context: Context,
        title: String,
        entries: List<Pair<VerseTagEntry, Tag>>,
        theme: PdfTheme = PdfTheme.NAVY_GOLD
    ): Uri? {
        val bgColor = Color.parseColor(theme.background)
        val accentColor = Color.parseColor(theme.accent)
        val bodyColor = Color.parseColor(theme.bodyText)
        val noteColor = Color.parseColor(theme.noteText)

        val bgPaint = Paint().apply { color = bgColor }
        val accentBarPaint = Paint().apply { color = accentColor }
        val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true; color = bodyColor; isAntiAlias = true }
        val refPaint = Paint().apply { textSize = 13f; isFakeBoldText = true; color = accentColor; isAntiAlias = true }
        val bodyPaint = Paint().apply { textSize = 12f; color = bodyColor; isAntiAlias = true }
        val notePaint = Paint().apply { textSize = 11f; isFakeBoldText = false; color = noteColor; isAntiAlias = true }

        val doc = PdfDocument()
        var pageNumber = 1

        fun startPage(): PdfDocument.Page {
            val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            // Full-page background fill + a left accent bar, matching the image-card look.
            p.canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat(), bgPaint)
            p.canvas.drawRect(0f, 0f, ACCENT_BAR_WIDTH, PAGE_HEIGHT.toFloat(), accentBarPaint)
            return p
        }

        var page = startPage()
        var canvas = page.canvas
        var y = MARGIN

        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 36f

        // Fixed: previously this only checked space for the reference line itself,
        // so a long verse+note pair could still spill past the bottom margin mid-entry.
        // Now every drawWrapped call re-checks space per *line*, via onBeforeLine,
        // so a page break can happen mid-entry without losing text off the bottom edge.
        fun newPageIfNeeded(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNumber++
                page = startPage()
                canvas = page.canvas
                y = MARGIN
            }
        }

        for ((entry, tag) in entries) {
            newPageIfNeeded(30f) // enough for just the reference line; body wraps handle their own breaks
            canvas.drawText("${entry.book} ${entry.chapter}:${entry.verse}  [${tag.name}]", MARGIN, y, refPaint)
            y += 18f
            y = drawWrapped(canvas, entry.verseText, bodyPaint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, 16f) { newPageIfNeeded(16f) }
            if (entry.note.isNotBlank()) {
                y += 4f
                y = drawWrapped(canvas, "Note: ${entry.note}", notePaint, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, 14f) { newPageIfNeeded(14f) }
            }
            y += 20f
        }

        doc.finishPage(page)
        val uri = saveToDownloads(context, doc, "verset_${sanitizeFileName(title)}")
        doc.close()
        return uri
    }

    /**
     * Draws word-wrapped text, checking [onBeforeLine] before every single line
     * (not just once per entry) so page breaks land cleanly between lines instead
     * of clipping text against the bottom margin.
     */
    private fun drawWrapped(
        canvas: android.graphics.Canvas, text: String, paint: Paint,
        x: Float, startY: Float, maxWidth: Float, lineHeight: Float,
        onBeforeLine: () -> Unit
    ): Float {
        var y = startY
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                onBeforeLine()
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
                line = StringBuilder(word)
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) {
            onBeforeLine()
            canvas.drawText(line.toString(), x, y, paint)
            y += lineHeight
        }
        return y
    }

    /**
     * Tag names are free-text user input and end up directly in a filename. On API 29+
     * MediaStore sandboxes this, but the API 24-28 fallback below builds a raw
     * java.io.File from it — so strip anything that isn't safe there (path separators,
     * ".." traversal, leading dots) rather than trusting the title as-is.
     */
    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-z0-9 _-]"), "").replace(" ", "_").trim('_', '.', ' ')
        return cleaned.ifBlank { "export" }.take(80)
    }

    private fun saveToDownloads(context: Context, doc: PdfDocument, displayName: String): Uri? {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$displayName.pdf")
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Verset")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> doc.writeTo(out) }
            return uri
        } else {
            // API 24-28: write directly to the public Downloads dir (requires
            // WRITE_EXTERNAL_STORAGE, declared in the manifest with maxSdkVersion=28).
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(dir, "$displayName.pdf")
            file.outputStream().use { out -> doc.writeTo(out) }
            return Uri.fromFile(file)
        }
    }
}
