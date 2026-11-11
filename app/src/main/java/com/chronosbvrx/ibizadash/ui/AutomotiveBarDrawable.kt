package com.chronosbvrx.ibizadash.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Barra de telemetría estilo clúster OEM: riel hundido, marcas de escala,
 * relleno con gradiente, punta luminosa y animación corta entre muestras.
 *
 * Se usa como capa del fondo de una tarjeta, así que no ocupa espacio de
 * layout ni mueve ningún elemento.
 */
class AutomotiveBarDrawable(
    private val minValue: Float,
    private val maxValue: Float,
    private val density: Float,
    private val barHeightDp: Float = 4f,
    private val insetDp: Float = 14f,
    private val bottomDp: Float = 6f,
    private val segmented: Boolean = false,
    private val segmentCount: Int = 20,
    private val bipolar: Boolean = false,
    private val initialLow: Int = 0xFF38BDF8.toInt(),
    private val initialHigh: Int = 0xFF22D3EE.toInt(),
    private val centerBand: Pair<Float, Float>? = null,
    private val zones: List<Zone> = emptyList(),
    private val markerOnly: Boolean = false,
    /** Contexto para resolver el riel semántico día/noche. */
    private val uiContext: android.content.Context? = null
) : Drawable() {

    data class Zone(val from: Float, val to: Float, val color: Int)

    private var lowColor = initialLow
    private var highColor = initialHigh

    fun setColors(low: Int, high: Int) {
        if (low == lowColor && high == highColor) return
        lowColor = low
        highColor = high
        invalidateSelf()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val track = RectF()
    private val fill = RectF()

    private var shown = minValue
    private var target = minValue
    private var hasValue = true
    private var animator: ValueAnimator? = null

    init {
        tickPaint.color = Color.WHITE
        tickPaint.alpha = 28
    }

    fun animateTo(value: Float?) {
        hasValue = value != null
        val next = value?.takeIf { it.isFinite() }?.coerceIn(minValue, maxValue) ?: minValue
        if (next == target) {
            invalidateSelf()
            return
        }
        target = next
        animator?.cancel()
        animator = ValueAnimator.ofFloat(shown, next).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    fun setImmediate(value: Float?) {
        animator?.cancel()
        hasValue = value != null
        shown = value?.takeIf { it.isFinite() }?.coerceIn(minValue, maxValue) ?: minValue
        target = shown
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val inset = insetDp * density
        val barH = barHeightDp * density
        val bottom = bounds.bottom - bottomDp * density
        val top = bottom - barH
        val left = bounds.left + inset
        val right = bounds.right - inset
        if (right <= left) return

        track.set(left, top, right, bottom)
        val radius = barH / 2f

        // Riel hundido (semántico: claro en día, oscuro en noche)
        val ctx = uiContext
        trackPaint.color = if (ctx != null) {
            ContextCompat.getColor(ctx, com.chronosbvrx.ibizadash.R.color.bg_pill)
        } else {
            0xFF080B10.toInt()
        }
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = 1f * density
        trackPaint.color = if (ctx != null) {
            ContextCompat.getColor(ctx, com.chronosbvrx.ibizadash.R.color.stroke_card)
        } else {
            0x0DFFFFFF
        }
        canvas.drawRoundRect(track, radius, radius, trackPaint)
        trackPaint.style = Paint.Style.FILL

        // Marcas pasivas de escala
        val marks = 4
        for (i in 0..marks) {
            val x = left + (right - left) * i / marks
            canvas.drawRect(x, top + 1f * density, x + 1f * density, bottom - 1f * density, tickPaint)
        }
        centerBand?.let { band ->
            for (v in floatArrayOf(band.first, band.second)) {
                val x = left + (right - left) * fraction(v)
                tickPaint.alpha = 60
                canvas.drawRect(x, top - 1f * density, x + 1f * density, bottom + 1f * density, tickPaint)
                tickPaint.alpha = 20
            }
        }

        if (zones.isNotEmpty()) {
            drawZones(canvas, left, right, top, bottom, radius)
            if (hasValue) drawMarker(canvas, left, right, top, bottom, radius)
            return
        }

        if (!hasValue) return

        if (bipolar) {
            drawBipolar(canvas, left, right, top, bottom, radius)
        } else if (segmented) {
            drawSegmented(canvas, left, right, top, bottom, radius)
        } else {
            drawContinuous(canvas, left, right, top, bottom, radius)
        }
    }

    private fun drawContinuous(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, radius: Float) {
        val f = fraction(shown)
        if (f <= 0f) return
        val end = left + (right - left) * f
        fill.set(left, top, end, bottom)
        paint.shader = LinearGradient(
            left, top, right, top,
            lowColor, highColor, Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipRect(fill)
        canvas.drawRoundRect(track, radius, radius, paint)
        canvas.restore()
        paint.shader = null
        drawLeadingEdge(canvas, end, top, bottom, radius)
    }

    private fun drawSegmented(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, radius: Float) {
        val n = segmentCount.coerceAtLeast(1)
        val gap = 2f * density
        val block = ((right - left) - gap * (n - 1)) / n
        val lit = (fraction(shown) * n).toInt()
        for (i in 0 until n) {
            val x = left + i * (block + gap)
            val local = i.toFloat() / (n - 1).coerceAtLeast(1)
            paint.color = ColorUtils.blendARGB(lowColor, highColor, local)
            if (i >= lit) paint.alpha = 70 else paint.alpha = 255
            canvas.drawRoundRect(x, top, x + block, bottom, radius * 0.5f, radius * 0.5f, paint)
        }
        paint.alpha = 255
        val end = left + (right - left) * fraction(shown)
        drawLeadingEdge(canvas, end, top, bottom, radius)
    }

    private fun drawBipolar(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, radius: Float) {
        val zero = fraction(0f)
        val center = left + (right - left) * zero
        val value = left + (right - left) * fraction(shown)
        val from = minOf(center, value)
        val to = maxOf(center, value)
        fill.set(from, top, to, bottom)
        paint.shader = LinearGradient(
            left, top, right, top,
            lowColor, highColor, Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipRect(fill)
        canvas.drawRoundRect(track, radius, radius, paint)
        canvas.restore()
        paint.shader = null
        // Línea central fija de referencia
        glowPaint.color = 0xCCE2E8F0.toInt()
        canvas.drawRect(center - 1f * density, top - 2f * density, center + 1f * density, bottom + 2f * density, glowPaint)
        drawLeadingEdge(canvas, value, top, bottom, radius)
    }

    private fun drawZones(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, radius: Float) {
        val gap = 3f * density
        zones.forEach { zone ->
            val a = left + (right - left) * fraction(zone.from)
            val b = left + (right - left) * fraction(zone.to)
            paint.color = ColorUtils.setAlphaComponent(zone.color, 70)
            canvas.drawRoundRect(a, top, b - gap, bottom, radius, radius, paint)
        }
        if (!markerOnly) {
            val zone = zones.firstOrNull { shown in it.from..it.to } ?: zones.lastOrNull()
            if (zone != null) {
                val a = left + (right - left) * fraction(zone.from)
                val b = left + (right - left) * fraction(zone.to)
                paint.color = ColorUtils.setAlphaComponent(zone.color, 150)
                canvas.drawRoundRect(a, top, b - gap, bottom, radius, radius, paint)
            }
        }
    }

    private fun drawMarker(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, radius: Float) {
        val x = left + (right - left) * fraction(shown)
        val active = zones.firstOrNull { shown in it.from..it.to } ?: zones.lastOrNull()
        val color = active?.color ?: highColor
        // halo
        glowPaint.color = ColorUtils.setAlphaComponent(color, 70)
        canvas.drawCircle(x, (top + bottom) / 2f, 6f * density, glowPaint)
        // aguja vertical que sobresale del riel
        paint.color = color
        canvas.drawRoundRect(
            x - 2f * density, top - 2f * density,
            x + 2f * density, bottom + 2f * density,
            radius, radius, paint
        )
        glowPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(x - 0.5f * density, top - 2f * density, x + 0.5f * density, bottom + 2f * density, glowPaint)
    }

    private fun drawLeadingEdge(canvas: Canvas, x: Float, top: Float, bottom: Float, radius: Float) {
        if (x <= 0f) return
        glowPaint.color = ColorUtils.setAlphaComponent(highColor, 70)
        canvas.drawCircle(x, (top + bottom) / 2f, 5f * density, glowPaint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(
            x - 1.5f * density, top - 1f * density,
            x + 1.5f * density, bottom + 1f * density,
            radius, radius, paint
        )
    }

    private fun fraction(value: Float): Float =
        ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
