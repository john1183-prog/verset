package com.johndev.verset.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore
import com.johndev.verset.data.VerseTagEntry

/** A named color scheme for exported verse cards. */
enum class CardTheme(
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
 * Renders a single tagged verse (+ note) as a shareable 1080x1350 image card
 * (Instagram-portrait friendly) and saves it to the Pictures/Verset gallery folder.
 */
object ImageCardExporter {

    fun export(context: Context, entry: VerseTagEntry, tagName: String, theme: CardTheme = CardTheme.NAVY_GOLD): Uri? {
        val width = 1080
        val height = 1350
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = Color.parseColor(theme.background)
        val accentColor = Color.parseColor(theme.accent)
        val bodyColor = Color.parseColor(theme.bodyText)
        val noteColor = Color.parseColor(theme.noteText)

        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = bgColor })

        // Accent bar
        canvas.drawRect(0f, 0f, 24f, height.toFloat(), Paint().apply { color = accentColor })

        // Tag label
        val tagPaint = Paint().apply {
            color = accentColor
            textSize = 42f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(tagName.uppercase(), 80f, 140f, tagPaint)

        // Verse text (wrapped)
        val versePaint = Paint().apply {
            color = bodyColor
            textSize = 52f
            isAntiAlias = true
        }
        val verseBounds = RectF(80f, 220f, width - 80f, height - 320f)
        drawWrappedText(canvas, entry.verseText, versePaint, verseBounds, lineSpacing = 68f)

        // Reference
        val refPaint = Paint().apply {
            color = accentColor
            textSize = 40f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("— ${entry.book} ${entry.chapter}:${entry.verse}", 80f, height - 220f, refPaint)

        // Note
        if (entry.note.isNotBlank()) {
            val notePaint = Paint().apply {
                color = noteColor
                textSize = 32f
                isAntiAlias = true
            }
            val noteBounds = RectF(80f, height - 170f, width - 80f, height - 60f)
            drawWrappedText(canvas, entry.note, notePaint, noteBounds, lineSpacing = 42f)
        }

        val uri = saveToGallery(context, bmp, "verset_${entry.book}_${entry.chapter}_${entry.verse}")
        bmp.recycle()
        return uri
    }

    private fun drawWrappedText(canvas: Canvas, text: String, paint: Paint, bounds: RectF, lineSpacing: Float) {
        val words = text.split(" ")
        var line = StringBuilder()
        var y = bounds.top
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > bounds.width()) {
                canvas.drawText(line.toString(), bounds.left, y, paint)
                line = StringBuilder(word)
                y += lineSpacing
                if (y > bounds.bottom) return
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty() && y <= bounds.bottom) {
            canvas.drawText(line.toString(), bounds.left, y, paint)
        }
    }

    private fun saveToGallery(context: Context, bmp: Bitmap, displayName: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Verset")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return uri
    }
}

typealias Uri = android.net.Uri
