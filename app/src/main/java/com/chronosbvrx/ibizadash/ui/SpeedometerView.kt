package com.chronosbvrx.ibizadash.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * LEGACY: solo lo referencia `view_cockpit.xml`, que ya no se infla.
 * El Home usa [ProSpeedometerView].
 *
 * Velocímetro en arco horizontal estilo mockup (VELOCIDAD 0..260).
 *
 * Mantiene la misma API pública (source/useMph/setSpeed) y paleta
 * (cobre #E07A5F activo, carbón #3A4756 riel) para no romper a los
 * llamantes. Solo cambia el dibujo: arco superior con marcas y número grande.
 */
class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var source: String = "GPS"
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

    /** Tema día/noche del Home: cambia acento y textos (ver applyTheme en MainActivity). */
    var nightMode: Boolean = true
        set(value) {
            field = value
            applyThemeColors()
            invalidate()
        }

    private var displayMax = MAX_KMH
    private var targetSpeed = 0f
    private var displaySpeed = 0f
    private var animator: ValueAnimator? = null
    private var gaugeBounds = RectF()
    private var viewW = 0f
    private var viewH = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3A4756")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E07A5F")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#64748B")
        strokeWidth = 2f
    }

    private val tickActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E07A5F")
        strokeWidth = 2f
    }

    private val tickTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
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
        color = Color.parseColor("#94A3B8")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun applyThemeColors() {
        // Coral propio #E87A5D/#E06D53 (día/noche) + turquesa solo para OK
        // en OBD, no en el arco. El arco activo usa degradado a coral.
        if (nightMode) {
            trackPaint.color = Color.parseColor("#26FFFFFF")
            activePaint.color = Color.parseColor("#E87A5D")
            tickPaint.color = Color.parseColor("#8A94A6")
            tickActivePaint.color = Color.parseColor("#E87A5D")
            tickTextPaint.color = Color.parseColor("#8A94A6")
            labelPaint.color = Color.parseColor("#8A94A6")
            speedPaint.color = Color.parseColor("#FFFFFF")
            unitPaint.color = Color.parseColor("#8A94A6")
            sourcePaint.color = Color.parseColor("#8A94A6")
        } else {
            trackPaint.color = Color.parseColor("#E5E7EB")
            activePaint.color = Color.parseColor("#E06D53")
            tickPaint.color = Color.parseColor("#6B7280")
            tickActivePaint.color = Color.parseColor("#E06D53")
            tickTextPaint.color = Color.parseColor("#6B7280")
            labelPaint.color = Color.parseColor("#6B7280")
            speedPaint.color = Color.parseColor("#111827")
            unitPaint.color = Color.parseColor("#6B7280")
            sourcePaint.color = Color.parseColor("#6B7280")
        }
        // Degradado del progreso: transparente en 0 → coral en la velocidad.
        activePaint.shader = null
    }

    init {
        applyThemeColors()
    }

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
        viewW = w.toFloat()
        viewH = h.toFloat()
        // Domo ancho y bajo estilo mockup (elipse, no semicírculo): el arco
        // ocupa casi todo el ancho y deja sitio debajo para el número.
        val arcW = (viewW - 16f).coerceAtLeast(80f)
        val arcH = arcW * 0.22f
        val left = (viewW - arcW) / 2f
        val top = 18f
        gaugeBounds.set(left, top, left + arcW, top + arcH)
        trackPaint.strokeWidth = 6f
        activePaint.strokeWidth = 6f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gaugeBounds.width() <= 0f || viewW <= 0f) return
        val cx = gaugeBounds.centerX()
        // Elipse del arco: rx = mitad del ancho, ry = mitad del alto.
        val rx = gaugeBounds.width() / 2f
        val ry = gaugeBounds.height() / 2f
        val cy = gaugeBounds.centerY()
        val converted = if (useMph) displaySpeed * MPH_FACTOR else displaySpeed
        val ratio = converted.coerceIn(0f, displayMax) / displayMax

        // Etiqueta y número van en TextView Overlay en el XML (FrameLayout),
        // no en el Canvas: evita el corte y respeta clipChildren=false.
        // El Canvas solo dibuja arco + marcas.

        // Arco: de 180° (izquierda) a 0° (derecha), por arriba.
        // Android mide desde la derecha: el domo superior es 180°..360°,
        // aquí se dibuja 180f+180f que equivale al tramo superior.
        // Progreso con degradado transparente→coral (SweepGradient).
        canvas.drawArc(gaugeBounds, 180f, 180f, false, trackPaint)
        if (ratio > 0f) {
            val coral = if (nightMode) Color.parseColor("#E87A5D") else Color.parseColor("#E06D53")
            val clear = coral and 0x00FFFFFF
            activePaint.shader = SweepGradient(
                cx, cy,
                intArrayOf(Color.TRANSPARENT, coral, coral),
                floatArrayOf(0f, 0.5f, 1f)
            )
            canvas.drawArc(gaugeBounds, 180f, 180f * ratio, false, activePaint)
            activePaint.shader = null
        }

        // Marcas cada 20 km/h con numeración.
        tickTextPaint.textSize = 11f
        val steps = listOf(0, 40, 80, 120, 160, 200, 260)
        for (v in steps) {
            val t = v / displayMax
            val ang = Math.toRadians((180f - 180f * t).toDouble())
            val c = cos(ang).toFloat()
            val s = sin(ang).toFloat()
            val x1 = cx + (rx - 4f) * c
            val y1 = cy - (ry - 2f) * s
            val x2 = cx + (rx - 12f) * c
            val y2 = cy - (ry - 6f) * s
            canvas.drawLine(x1, y1, x2, y2, if (t <= ratio) tickActivePaint else tickPaint)
            val xt = cx + (rx - 26f) * c
            val yt = cy - (ry - 14f) * s
            canvas.drawText(v.toString(), xt, yt + 4f, tickTextPaint)
        }

        // Número y fuente: TextView Overlay en el XML (ver FrameLayout).
        // El Canvas no dibuja textos para no cortarse con el arco.
    }

    companion object {
        private const val MAX_KMH = 260f
        private const val MAX_MPH = 160f
        private const val MPH_FACTOR = 0.621371f
    }
}
