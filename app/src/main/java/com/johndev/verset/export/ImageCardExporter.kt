package com.johndev.verset.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
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

/** A named typeface choice for exported verse cards. */
enum class ImageFont(val displayName: String, val typeface: Typeface) {
    SANS("Sans Serif", Typeface.DEFAULT),
    SERIF("Serif", Typeface.SERIF),
    ELEGANT("Elegant Serif", Typeface.create(Typeface.SERIF, Typeface.ITALIC)),
    MONOSPACE("Monospace", Typeface.MONOSPACE)
}

/** Horizontal text alignment for exported verse cards. */
enum class ImageAlign(val displayName: String, val paintAlign: Paint.Align) {
    LEFT("Left", Paint.Align.LEFT),
    CENTER("Center", Paint.Align.CENTER),
    RIGHT("Right", Paint.Align.RIGHT)
}

/** One verse's reference + text, as rendered on a card. A card can hold one or many. */
data class VerseCardItem(val reference: String, val verseText: String)

/**
 * Renders one or more verses as a shareable 1080x1350 image (Instagram-portrait
 * friendly) and saves it to the Pictures/Verset gallery folder.
 *
 * Content is passed in as plain strings rather than DB entities directly, so the
 * caller can let the user edit the text/font/alignment before export without
 * needing a fake entity object.
 *
 * Font size auto-fits to the available space (all items combined, plus the note)
 * instead of a fixed size — a single short verse uses a larger font instead of
 * leaving empty space; several verses, or long ones, shrink together instead of
 * spilling past the bottom of the card.
 */
object ImageCardExporter {

    private const val WIDTH = 1080
    private const val HEIGHT = 1350
    private const val MARGIN = 80f

    // Reference lines render smaller than verse text, at this fraction of the verse font size.
    private const val REF_SIZE_RATIO = 0.55f
    private const val MIN_REF_SIZE = 20f

    fun export(
        context: Context,
        items: List<VerseCardItem>,
        note: String,
        tagLabel: String,
        theme: CardTheme = CardTheme.NAVY_GOLD,
        font: ImageFont = ImageFont.SANS,
        align: ImageAlign = ImageAlign.LEFT
    ): Uri? {
        if (items.isEmpty()) return null

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
            val tagPaint = Paint().apply {
                color = accentColor; textSize = 42f; isFakeBoldText = true; isAntiAlias = true; typeface = font.typeface
            }
            canvas.drawText(tagLabel.uppercase(), MARGIN, y, tagPaint)
        }

        val hasNote = note.isNotBlank()
        val contentWidth = WIDTH - 2 * MARGIN
        val noteReservedHeight = if (hasNote) 220f else 0f
        val contentTop = y + 60f
        val contentBottom = HEIGHT - MARGIN - noteReservedHeight

        val fitSize = fitCardFontSize(
            items, font.typeface, contentWidth, contentBottom - contentTop, maxSize = 64f, minSize = 22f
        )
        val refSize = (fitSize * REF_SIZE_RATIO).coerceAtLeast(MIN_REF_SIZE)

        val versePaint = Paint().apply { color = bodyColor; textSize = fitSize; typeface = font.typeface; isAntiAlias = true }
        val refPaint = Paint().apply { color = accentColor; textSize = refSize; isFakeBoldText = true; typeface = font.typeface; isAntiAlias = true }

        var cursorY = contentTop + fitSize * 0.9f
        for ((index, item) in items.withIndex()) {
            cursorY = drawWrappedText(canvas, item.verseText, versePaint, MARGIN, WIDTH - MARGIN, cursorY, fitSize * 1.3f, align)
            cursorY += refSize * 0.3f
            cursorY = drawWrappedText(canvas, item.reference, refPaint, MARGIN, WIDTH - MARGIN, cursorY, refSize * 1.3f, align)
            if (index != items.lastIndex) cursorY += fitSize * 0.6f
        }

        if (hasNote) {
            val noteTop = contentBottom + 20f
            val noteBottom = HEIGHT - MARGIN
            val noteSize = fitFontSize(note, contentWidth, noteBottom - noteTop, maxSize = 34f, minSize = 20f, typeface = font.typeface)
            val notePaint = Paint().apply { color = noteColor; textSize = noteSize; isAntiAlias = true; typeface = font.typeface }
            drawWrappedText(canvas, note, notePaint, MARGIN, WIDTH - MARGIN, noteTop + noteSize * 0.9f, noteSize * 1.35f, align)
        }

        val uri = saveToGallery(context, bmp, "verset_${System.currentTimeMillis()}")
        bmp.recycle()
        return uri
    }

    /**
     * Finds the largest font size (stepping down from [maxSize] to [minSize]) at which every
     * item — its verse text plus its own reference line, stacked for however many [items] there
     * are — fits within [maxHeight]. This is what lets a single short verse render large while
     * several long ones shrink together instead of overflowing the card.
     */
    private fun fitCardFontSize(
        items: List<VerseCardItem>, typeface: Typeface, maxWidth: Float, maxHeight: Float, maxSize: Float, minSize: Float
    ): Float {
        if (items.isEmpty() || maxHeight <= 0f) return minSize
        var size = maxSize
        while (size > minSize) {
            val versePaint = Paint().apply { textSize = size; this.typeface = typeface; isAntiAlias = true }
            val refSize = (size * REF_SIZE_RATIO).coerceAtLeast(MIN_REF_SIZE)
            val refPaint = Paint().apply { textSize = refSize; this.typeface = typeface; isAntiAlias = true }
            var totalHeight = 0f
            for ((index, item) in items.withIndex()) {
                totalHeight += wrappedLineCount(item.verseText, versePaint, maxWidth) * (size * 1.3f)
                totalHeight += refSize * 0.3f
                totalHeight += wrappedLineCount(item.reference, refPaint, maxWidth) * (refSize * 1.3f)
                if (index != items.lastIndex) totalHeight += size * 0.6f
            }
            if (totalHeight <= maxHeight) return size
            size -= 2f
        }
        return minSize
    }

    private fun fitFontSize(
        text: String, maxWidth: Float, maxHeight: Float, maxSize: Float, minSize: Float, typeface: Typeface = Typeface.DEFAULT
    ): Float {
        if (text.isBlank() || maxHeight <= 0f) return minSize
        var size = maxSize
        while (size > minSize) {
            val paint = Paint().apply { textSize = size; this.typeface = typeface; isAntiAlias = true }
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

    /**
     * Word-wraps [text] within [left]..[right] and draws it starting at baseline
     * [startBaselineY], honoring [align] (left/center/right — Paint handles the per-line
     * x-offset once textAlign is set, so wrapping logic itself doesn't need to change per
     * alignment). Returns the next available baseline y, so callers can stack multiple
     * text blocks (verse, then its reference, then the next verse...) in sequence.
     */
    private fun drawWrappedText(
        canvas: Canvas, text: String, paint: Paint,
        left: Float, right: Float, startBaselineY: Float, lineSpacing: Float, align: ImageAlign
    ): Float {
        val maxWidth = right - left
        val originalAlign = paint.textAlign
        paint.textAlign = align.paintAlign
        val xPos = when (align) {
            ImageAlign.LEFT -> left
            ImageAlign.CENTER -> (left + right) / 2f
            ImageAlign.RIGHT -> right
        }

        var y = startBaselineY
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            val test = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(test) > maxWidth) {
                canvas.drawText(line.toString(), xPos, y, paint)
                line = StringBuilder(word)
                y += lineSpacing
            } else {
                line = StringBuilder(test)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), xPos, y, paint)
            y += lineSpacing
        }

        paint.textAlign = originalAlign
        return y
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
