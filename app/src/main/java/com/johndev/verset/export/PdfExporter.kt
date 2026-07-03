package com.johndev.verset.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import com.johndev.verset.data.Tag
import com.johndev.verset.data.VerseTagEntry

/**
 * Exports a tag's full verse+note list (or "all entries") as a multi-page PDF
 * using Android's built-in PdfDocument — no extra library needed.
 */
object PdfExporter {

    private const val PAGE_WIDTH = 612  // US Letter at 72dpi
    private const val PAGE_HEIGHT = 792
    private const val MARGIN = 56f

    fun export(context: Context, title: String, entries: List<Pair<VerseTagEntry, Tag>>): Uri? {
        val doc = PdfDocument()
        val titlePaint = Paint().apply { textSize = 22f; isFakeBoldText = true }
        val refPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 12f }
        val notePaint = Paint().apply { textSize = 11f; isFakeBoldText = false }
        notePaint.color = 0xFF666666.toInt()

        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        canvas.drawText(title, MARGIN, y, titlePaint)
        y += 36f

        fun newPageIfNeeded(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        for ((entry, tag) in entries) {
            newPageIfNeeded(90f)
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
        val uri = saveToDownloads(context, doc, "verset_${title.replace(" ", "_")}")
        doc.close()
        return uri
    }

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
