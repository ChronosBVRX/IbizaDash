package com.chronosbvrx.ibizadash.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Velocímetro premium de 230° con pista degradada turquesa→coral, marcas
 * graduadas, halo neón y la velocidad integrada en el centro del arco.
 *
 * Mantiene la lectura en km/h por defecto y adapta escala/unidad cuando
 * [useMph] está activo, igual que el resto del HUD.
 */
class ProSpeedometerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentSpeed = 0f
    private var maxSpeed = MAX_KMH
    private var isNightMode = true
    private var speedSource = "OBD"
    private var animator: ValueAnimator? = null

    /** Unidades del HUD: false = km/h, true = mph. */
    var useMph: Boolean = false
        set(value) {
            if (field == value) return
            animator?.cancel()
            field = value
            maxSpeed = if (value) MAX_MPH else MAX_KMH
            currentSpeed *= if (value) MPH_FACTOR else KMH_FACTOR
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        // 18dp de desenfoque: se escala con la densidad para que el halo se
        // vea igual en la head unit (160 dpi) y en un teléfono de alta densidad.
        maskFilter = BlurMaskFilter(
            18f * context.resources.displayMetrics.density,
            BlurMaskFilter.Blur.NORMAL
        )
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val speedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
    }
    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.15f
    }

    private val arcRect = RectF()
    private val startAngle = 155f
    private val sweepAngle = 230f

    init {
        // BlurMaskFilter no está soportado por el renderer hardware: la capa
        // software es la que produce el halo suave del arco.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setSpeed(speed: Int, animate: Boolean = true) {
        val converted = if (useMph) speed * MPH_FACTOR else speed.toFloat()
        val target = converted.coerceIn(0f, maxSpeed)
        animator?.cancel()
        if (!animate || abs(target - currentSpeed) < 0.5f) {
            currentSpeed = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(currentSpeed, target).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentSpeed = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun setSourceLabel(source: String) {
        speedSource = source.uppercase()
        invalidate()
    }

    fun setNightMode(night: Boolean) {
        isNightMode = night
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h * 0.58f
        val radius = (minOf(w * 0.80f, h * 0.92f) / 2f)

        val strokeW = radius * 0.11f
        trackPaint.strokeWidth = strokeW
        progressPaint.strokeWidth = strokeW
        glowPaint.strokeWidth = strokeW * 1.6f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        val trackColor = if (isNightMode) Color.parseColor("#1E2631") else Color.parseColor("#E2E8F0")
        val textPrimary = if (isNightMode) Color.WHITE else Color.parseColor("#0F172A")
        val textSecondary = if (isNightMode) Color.parseColor("#8A94A6") else Color.parseColor("#64748B")
        val accentStart = Color.parseColor("#2DD4BF")
        val accentEnd = Color.parseColor("#E87A5D")

        // Pista base
        trackPaint.color = trackColor
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, trackPaint)

        // Progreso con degradado y glow
        val gradient = LinearGradient(
            arcRect.left, cy, arcRect.right, cy,
            intArrayOf(accentStart, accentEnd), null, Shader.TileMode.CLAMP
        )
        progressPaint.shader = gradient
        glowPaint.shader = gradient
        glowPaint.alpha = if (isNightMode) 115 else 45

        val currentSweep = (currentSpeed / maxSpeed) * sweepAngle
        if (currentSweep > 0.5f) {
            canvas.drawArc(arcRect, startAngle, currentSweep, false, glowPaint)
            canvas.drawArc(arcRect, startAngle, currentSweep, false, progressPaint)
        }

        // Marcas y números de escala (km/h: 0..240; mph: 0..150)
        val steps = if (useMph) 10 else 12
        val tickStep = maxSpeed / steps
        val innerTickR = radius - strokeW * 0.85f
        val outerTickR = radius - strokeW * 1.35f
        val labelR = radius - strokeW * 2.35f
        labelPaint.textSize = radius * 0.115f
        tickPaint.style = Paint.Style.STROKE

        for (i in 0..steps) {
            val stepSpeed = (i * tickStep).roundToInt()
            val angleDeg = startAngle + (i.toFloat() / steps) * sweepAngle
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val isPassed = currentSpeed >= stepSpeed

            tickPaint.color = if (isPassed) accentEnd else textSecondary
            tickPaint.strokeWidth = if (i % 2 == 0) 3.5f else 2f

            val sx = cx + (innerTickR * cos(angleRad)).toFloat()
            val sy = cy + (innerTickR * sin(angleRad)).toFloat()
            val ex = cx + (outerTickR * cos(angleRad)).toFloat()
            val ey = cy + (outerTickR * sin(angleRad)).toFloat()
            canvas.drawLine(sx, sy, ex, ey, tickPaint)

            if (i % 2 == 0) {
                labelPaint.color = if (isPassed) textPrimary else textSecondary
                val lx = cx + (labelR * cos(angleRad)).toFloat()
                val ly = cy + (labelR * sin(angleRad)).toFloat() + (labelPaint.textSize / 3f)
                canvas.drawText(stepSpeed.toString(), lx, ly, labelPaint)
            }
        }

        // Indicador circular en la punta: blanco con aro coral para que se
        // lea tanto sobre la tarjeta clara como sobre la oscura.
        val thumbRad = Math.toRadians((startAngle + currentSweep).toDouble())
        val tx = cx + (radius * cos(thumbRad)).toFloat()
        val ty = cy + (radius * sin(thumbRad)).toFloat()
        tickPaint.color = Color.WHITE
        tickPaint.style = Paint.Style.FILL
        canvas.drawCircle(tx, ty, strokeW * 0.50f, tickPaint)
        tickPaint.style = Paint.Style.STROKE
        tickPaint.strokeWidth = 1.5f
        tickPaint.color = accentEnd
        canvas.drawCircle(tx, ty, strokeW * 0.50f, tickPaint)
        tickPaint.style = Paint.Style.FILL

        // Velocidad central gigante ADENTRO del arco
        speedTextPaint.color = textPrimary
        speedTextPaint.textSize = radius * 0.58f
        canvas.drawText(
            currentSpeed.roundToInt().toString(),
            cx,
            cy + (speedTextPaint.textSize * 0.14f),
            speedTextPaint
        )

        // Etiqueta de unidad y fuente debajo del número
        unitTextPaint.color = textSecondary
        unitTextPaint.textSize = radius * 0.115f
        val unit = if (useMph) "MPH" else "KM/H"
        canvas.drawText(
            "$unit · $speedSource",
            cx,
            cy + (speedTextPaint.textSize * 0.44f),
            unitTextPaint
        )
    }

    companion object {
        private const val MAX_KMH = 240f
        private const val MAX_MPH = 150f
        private const val MPH_FACTOR = 0.621371f
        private const val KMH_FACTOR = 1.609344f
    }
}
