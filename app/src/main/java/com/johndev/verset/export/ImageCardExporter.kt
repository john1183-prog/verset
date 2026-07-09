package com.johndev.verset.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.MediaStore

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
 * Renders a verse card as a shareable 1080x1350 image (Instagram-portrait
 * friendly) and saves it to the Pictures/Verset gallery folder.
 *
 * Content (reference/verse text/note) is passed in as plain strings rather
 * than a VerseTagEntry directly, so the caller can let the user edit the
 * text before export without needing a fake entry object.
 *
 * Font size auto-fits to the available space per section (verse text and
 * note each get their own fit pass) instead of a fixed size — short verses
 * use a larger font instead of leaving empty space, long verses shrink
 * instead of silently running past the bottom of the card.
 */
object ImageCardExporter {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 80f

    fun export(
        context: Context,
        reference: String,
        verseText: String,
        note: String,
        tagLabel: String,
        theme: CardTheme = CardTheme.NAVY_GOLD
    ): Uri? {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = Color.parseColor(theme.background)
        val accentColor = Color.parseColor(theme.accent)
        val bodyColor = Color.parseColor(theme.bodyText)
        val noteColor = Color.parseColor(theme.noteText)

        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply { color = bgColor })
        canvas.drawRect(0f, 0f, 24f, HEIGHT.toFloat(), Paint().apply { color = accentColor })

        var y = 140f
        if (tagLabel.isNotBlank()) {
            val tagPaint = Paint().apply { color = accentColor; textSize = 42f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText(tagLabel.uppercase(), MARGIN, y, tagPaint)
        }

        val hasNote = note.isNotBlank()
        val refReservedHeight = 90f
        val noteReservedHeight = if (hasNote) 220f else 0f
        val verseTop = y + 60f
        val verseBottom = HEIGHT - MARGIN - refReservedHeight - noteReservedHeight
        val verseWidth = WIDTH - 2 * MARGIN

        val verseSize = fitFontSize(verseText, verseWidth, verseBottom - verseTop, maxSize = 64f, minSize = 30f)
        val versePaint = Paint().apply { color = bodyColor; textSize = verseSize; isAntiAlias = true }
        drawWrappedText(canvas, verseText, versePaint, RectF(MARGIN, verseTop, WIDTH - MARGIN, verseBottom), lineSpacing = verseSize * 1.3f)

        val refY = HEIGHT - MARGIN - noteReservedHeight - 20f
        val refPaint = Paint().apply { color = accentColor; textSize = 40f; isFakeBoldText = true; isAntiAlias = true }
        canvas.drawText(reference, MARGIN, refY, refPaint)

        if (hasNote) {
            val noteTop = refY + 40f
            val noteBottom = HEIGHT - MARGIN
            val noteSize = fitFontSize(note, verseWidth, noteBottom - noteTop, maxSize = 34f, minSize = 22f)
            val notePaint = Paint().apply { color = noteColor; textSize = noteSize; isAntiAlias = true }
            drawWrappedText(canvas, note, notePaint, RectF(MARGIN, noteTop, WIDTH - MARGIN, noteBottom), lineSpacing = noteSize * 1.35f)
        }

        val uri = saveToGallery(context, bmp, "verset_${System.currentTimeMillis()}")
        bmp.recycle()
        return uri
    }

    /**
     * Finds the largest font size (stepping down from [maxSize] to [minSize]) at which
     * [text], word-wrapped to [maxWidth], fits within [maxHeight]. Prevents both the
     * "lots of empty space" look for short text and silent truncation for long text.
     */
    private fun fitFontSize(text: String, maxWidth: Float, maxHeight: Float, maxSize: Float, minSize: Float): Float {
        if (text.isBlank() || maxHeight <= 0f) return minSize
        var size = maxSize
        while (size > minSize) {
            val paint = Paint().apply { textSize = size; isAntiAlias = true }
            val lineHeight = size * 1.3f
            val lines = wrappedLineCount(text, paint, maxWidth)
            if (lines * lineHeight <= maxHeight) return size
            size -= 2f
        }
        return minSize
    }

    private fun wrappedLineCount(text: String, paint: Paint, maxWidth: Float): Int {
        val words = text.split(" ")
        var lines = 1
        var lineWidth = 0f
        for (word in words) {
            val wordWidth = paint.measureText(if (lineWidth == 0f) word else " $word")
            if (lineWidth + wordWidth > maxWidth) {
                lines++
                lineWidth = paint.measureText(word)
            } else {
                lineWidth += wordWidth
            }
        }
        return lines
    }

    private fun drawWrappedText(canvas: Canvas, text: String, paint: Paint, bounds: RectF, lineSpacing: Float) {
        val words = text.split(" ")
        var line = StringBuilder()
        var y = bounds.top + lineSpacing * 0.8f
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > bounds.width()) {
                canvas.drawText(line.toString(), bounds.left, y, paint)
                line = StringBuilder(word)
                y += lineSpacing
                if (y > bounds.bottom + lineSpacing) return
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) {
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
        resolver.openOutputStream(uri)?.use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return uri
    }
}

typealias Uri = android.net.Uri
