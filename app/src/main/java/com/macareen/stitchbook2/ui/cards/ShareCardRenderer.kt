package com.macareen.stitchbook2.ui.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.macareen.stitchbook2.domain.cards.CardContent

/**
 * Renders a card to a 1080×1350 (4:5 portrait) bitmap -- the documented
 * export size, chosen because it displays uncropped in most feeds and
 * messaging apps. Rendering is fully local and deterministic for the same
 * content and photos.
 *
 * The export palette is fixed (it does not follow the app's dark mode) so a
 * shared card always looks the same. It reuses the light theme's brand
 * values from DESIGN_SYSTEM.md: warm ivory, warm charcoal, dusty rose.
 * Photos are drawn as pixels only, so no EXIF/location metadata from the
 * originals can reach the PNG.
 */
object ShareCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 72f
    private val BACKGROUND = Color.rgb(0xFB, 0xF7, 0xF2)
    private val INK = Color.rgb(0x2E, 0x2A, 0x27)
    private val MUTED = Color.rgb(0x6F, 0x66, 0x5F)
    private val ACCENT = Color.rgb(0x9C, 0x4A, 0x5E)
    private val TILE = Color.rgb(0xF2, 0xEA, 0xE2)

    fun render(content: CardContent, photos: List<Bitmap>, text: CardText): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        var y = MARGIN
        val contentWidth = WIDTH - 2 * MARGIN

        if (photos.isNotEmpty()) {
            val photoHeight = if (photos.size == 1) 600f else 520f
            val gap = 24f
            val slotWidth = (contentWidth - gap * (photos.size - 1)) / photos.size
            photos.forEachIndexed { index, photo ->
                val left = MARGIN + index * (slotWidth + gap)
                drawCropped(canvas, photo, RectF(left, y, left + slotWidth, y + photoHeight), radius = 36f)
            }
            y += photoHeight + 48f
        }

        y = drawText(canvas, text.templateLabel(content.template).uppercase(), y, contentWidth, paint(28f, ACCENT, bold = true).apply { letterSpacing = 0.12f }, maxLines = 1) + 12f
        val titleSize = if (photos.isEmpty()) 84f else 64f
        y = drawText(canvas, text.title(content), y, contentWidth, paint(titleSize, INK, serif = true, bold = true), maxLines = 3) + 8f
        text.subtitle(content)?.let {
            y = drawText(canvas, it, y, contentWidth, paint(32f, MUTED), maxLines = 1) + 8f
        }
        y += 32f

        val footerTop = HEIGHT - MARGIN - 32f
        val tiles = content.facts.filterNot { text.isLongText(it) }
        val longTexts = content.facts.filter { text.isLongText(it) }
        val tileWidth = (contentWidth - 24f) / 2f
        val tileHeight = 140f
        tiles.chunked(2).forEach { row ->
            if (y + tileHeight > footerTop - 16f) return@forEach
            row.forEachIndexed { column, fact ->
                val left = MARGIN + column * (tileWidth + 24f)
                val rect = RectF(left, y, left + tileWidth, y + tileHeight)
                canvas.drawRoundRect(rect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TILE })
                drawText(canvas, text.label(fact), y + 24f, tileWidth - 56f, paint(26f, MUTED), maxLines = 1, left = left + 28f)
                drawText(canvas, text.value(fact), y + 64f, tileWidth - 56f, paint(40f, INK, bold = true), maxLines = 1, left = left + 28f)
            }
            y += tileHeight + 20f
        }
        longTexts.forEach { fact ->
            if (y + 80f > footerTop - 16f) return@forEach
            y = drawText(canvas, text.label(fact), y + 8f, contentWidth, paint(26f, MUTED), maxLines = 1) + 4f
            val remainingLines = ((footerTop - 16f - y) / 44f).toInt().coerceAtLeast(1)
            y = drawText(canvas, text.value(fact), y, contentWidth, paint(32f, INK), maxLines = remainingLines.coerceAtMost(4)) + 12f
        }

        drawText(canvas, "Stitchbook", footerTop, contentWidth, paint(26f, MUTED, serif = true).apply { letterSpacing = 0.08f }, maxLines = 1)
        return bitmap
    }

    private fun paint(size: Float, color: Int, serif: Boolean = false, bold: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create(if (serif) Typeface.SERIF else Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    /** Draws wrapped, ellipsized text at [top]; returns the bottom edge. */
    private fun drawText(
        canvas: Canvas,
        value: String,
        top: Float,
        width: Float,
        paint: TextPaint,
        maxLines: Int,
        left: Float = MARGIN
    ): Float {
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    /** Center-crops [photo] into [target] with rounded corners. */
    private fun drawCropped(canvas: Canvas, photo: Bitmap, target: RectF, radius: Float) {
        val targetRatio = target.width() / target.height()
        val photoRatio = photo.width.toFloat() / photo.height
        val source = if (photoRatio > targetRatio) {
            val cropWidth = (photo.height * targetRatio).toInt()
            val x = (photo.width - cropWidth) / 2
            Rect(x, 0, x + cropWidth, photo.height)
        } else {
            val cropHeight = (photo.width / targetRatio).toInt()
            val yOffset = (photo.height - cropHeight) / 2
            Rect(0, yOffset, photo.width, yOffset + cropHeight)
        }
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) })
        canvas.drawBitmap(photo, source, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }
}
