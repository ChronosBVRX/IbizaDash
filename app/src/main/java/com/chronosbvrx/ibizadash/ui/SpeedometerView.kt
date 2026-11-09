package com.chronosbvrx.ibizadash.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * Velocidad integrada a la interfaz.
 *
 * Sin tacómetro ni números secundarios: solo km/h, un anillo muy fino y la
 * fuente de datos. OBD se muestra en verde; GPS/CAN confirman un respaldo
 * con azul discreto. El dibujo se limita a arcs y texto preasignados.
 */
class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var source: String = "OBD"
        set(value) {
            field = value
            invalidate()
        }

    var useMph: Boolean = false
        set(value) {
            field = value
            displayMax = if (value) MAX_MPH else MAX_KMH
            invalidate()
        }

    private var displayMax = MAX_KMH
    private var targetSpeed = 0f
    private var displaySpeed = 0f
    private var animator: ValueAnimator? = null
    private var gaugeBounds = RectF()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4756")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E07A5F")
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    private val sourceDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setSpeed(kmh: Float) {
        val nextTarget = kmh.coerceIn(0f, MAX_KMH)
        if (targetSpeed == nextTarget && animator != null) return
        targetSpeed = nextTarget
        showSpeedAnimated(displaySpeed, nextTarget)
    }

    private fun showSpeedAnimated(from: Float, to: Float) {
        animator?.cancel()
        if (from == to) {
            displaySpeed = to
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = 170L
            interpolator = DecelerateInterpolator()
            addUpdateListener { value ->
                displaySpeed = value.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val diameter = minOf(w, h).toFloat() - 20f
        val cx = w / 2f
        val cy = h / 2f
        gaugeBounds.set(
            cx - diameter / 2f,
            cy - diameter / 2f,
            cx + diameter / 2f,
            cy + diameter / 2f
        )
        trackPaint.strokeWidth = diameter * 0.026f
        activePaint.strokeWidth = diameter * 0.026f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gaugeBounds.width() <= 0f) return

        val cx = gaugeBounds.centerX()
        val cy = gaugeBounds.centerY()
        val radius = gaugeBounds.width() / 2f
        val converted = if (useMph) displaySpeed * MPH_FACTOR else displaySpeed
        val ratio = converted.coerceIn(0f, displayMax) / displayMax

        // Arco integrado: solo progreso visual, sin tacómetro ni marcas.
        canvas.drawArc(gaugeBounds, 135f, 270f, false, trackPaint)
        if (ratio > 0f) {
            canvas.drawArc(gaugeBounds, 135f, 270f * ratio, false, activePaint)
        }

        // Texto centrado como bloque (número + unidad + fuente) sobre cy.
        // Antes el bloque quedaba ~0.2R abajo, pegado al borde inferior.
        speedPaint.textSize = radius * 0.52f
        unitPaint.textSize = radius * 0.115f
        sourcePaint.textSize = radius * 0.09f
        val numText = converted.roundToInt().toString()
        val numBounds = android.graphics.Rect()
        speedPaint.getTextBounds(numText, 0, numText.length, numBounds)
        val numBaseline = cy - radius * 0.02f - (numBounds.top + numBounds.bottom) / 2f
        canvas.drawText(numText, cx, numBaseline, speedPaint)
        canvas.drawText(
            if (useMph) "mph" else "km/h",
            cx,
            numBaseline + radius * 0.21f,
            unitPaint
        )

        drawSource(canvas, cx, numBaseline + radius * 0.37f, radius)
    }

    private fun drawSource(canvas: Canvas, cx: Float, y: Float, radius: Float) {
        val isObd = source.equals("OBD", ignoreCase = true)
        val color = if (isObd) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")
        sourceDotPaint.color = color
        sourcePaint.color = color
        sourcePaint.textSize = radius * 0.09f
        val label = source.uppercase()
        val textWidth = sourcePaint.measureText(label)
        val dotRadius = radius * 0.022f
        val totalWidth = dotRadius * 2f + textWidth + radius * 0.045f
        canvas.drawCircle(cx - totalWidth / 2f, y - radius * 0.032f, dotRadius, sourceDotPaint)
        canvas.drawText(label, cx + totalWidth / 2f - textWidth / 2f, y, sourcePaint)
    }

    companion object {
        private const val MAX_KMH = 240f
        private const val MAX_MPH = 160f
        private const val MPH_FACTOR = 0.621371f
    }
}
