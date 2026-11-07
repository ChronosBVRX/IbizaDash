package com.chronosbvrx.ibizadash.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class CarSilhouetteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bodyPath = Path()
    private val glassPath = Path()

    private var w = 0f
    private var h = 0f

    init {
        glassPaint.color = Color.parseColor("#101820")
        wheelPaint.color = Color.parseColor("#05080C")
        rimPaint.color = Color.parseColor("#2D7DF6")
        rimPaint.style = Paint.Style.STROKE
        rimPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)
        w = width.toFloat()
        h = height.toFloat()
        buildPaths()
    }

    private fun buildPaths() {
        if (w <= 0f || h <= 0f) return

        bodyPath.reset()
        bodyPath.moveTo(w * 0.06f, h * 0.72f)
        bodyPath.cubicTo(w * 0.08f, h * 0.54f, w * 0.17f, h * 0.47f, w * 0.27f, h * 0.45f)
        bodyPath.lineTo(w * 0.35f, h * 0.31f)
        bodyPath.cubicTo(w * 0.40f, h * 0.25f, w * 0.46f, h * 0.23f, w * 0.53f, h * 0.23f)
        bodyPath.lineTo(w * 0.67f, h * 0.23f)
        bodyPath.cubicTo(w * 0.74f, h * 0.23f, w * 0.80f, h * 0.28f, w * 0.84f, h * 0.35f)
        bodyPath.lineTo(w * 0.90f, h * 0.45f)
        bodyPath.cubicTo(w * 0.96f, h * 0.49f, w * 0.97f, h * 0.61f, w * 0.94f, h * 0.72f)
        bodyPath.lineTo(w * 0.90f, h * 0.78f)
        bodyPath.lineTo(w * 0.10f, h * 0.78f)
        bodyPath.close()

        glassPath.reset()
        glassPath.moveTo(w * 0.37f, h * 0.43f)
        glassPath.lineTo(w * 0.43f, h * 0.30f)
        glassPath.cubicTo(w * 0.47f, h * 0.27f, w * 0.50f, h * 0.27f, w * 0.53f, h * 0.27f)
        glassPath.lineTo(w * 0.62f, h * 0.27f)
        glassPath.lineTo(w * 0.67f, h * 0.43f)
        glassPath.close()

        glassPath.moveTo(w * 0.70f, h * 0.43f)
        glassPath.lineTo(w * 0.66f, h * 0.29f)
        glassPath.lineTo(w * 0.72f, h * 0.29f)
        glassPath.cubicTo(w * 0.76f, h * 0.30f, w * 0.79f, h * 0.35f, w * 0.81f, h * 0.39f)
        glassPath.lineTo(w * 0.84f, h * 0.43f)
        glassPath.close()

        bodyPaint.shader = LinearGradient(
            0f, h * 0.18f, 0f, h * 0.82f,
            intArrayOf(Color.parseColor("#4E6078"), Color.parseColor("#161D27")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (w <= 0f || h <= 0f) return

        glowPaint.shader = LinearGradient(
            0f, h * 0.80f, 0f, h * 0.96f,
            intArrayOf(0x002D7DF6, 0x662D7DF6, 0x002D7DF6),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(w * 0.06f, h * 0.78f, w * 0.94f, h * 0.96f, glowPaint)

        canvas.drawPath(bodyPath, bodyPaint)
        canvas.drawPath(glassPath, glassPaint)

        val outline = Paint(Paint.ANTI_ALIAS_FLAG)
        outline.style = Paint.Style.STROKE
        outline.color = Color.parseColor("#33FFFFFF")
        outline.strokeWidth = h * 0.006f
        canvas.drawPath(bodyPath, outline)

        val headlight = Paint(Paint.ANTI_ALIAS_FLAG)
        headlight.color = Color.parseColor("#FFD27A")
        canvas.drawCircle(w * 0.085f, h * 0.56f, h * 0.018f, headlight)

        val taillight = Paint(Paint.ANTI_ALIAS_FLAG)
        taillight.color = Color.parseColor("#FF5A5F")
        canvas.drawCircle(w * 0.93f, h * 0.55f, h * 0.014f, taillight)

        val wheelR = h * 0.105f
        canvas.drawCircle(w * 0.27f, h * 0.78f, wheelR, wheelPaint)
        canvas.drawCircle(w * 0.73f, h * 0.78f, wheelR, wheelPaint)

        rimPaint.strokeWidth = h * 0.018f
        canvas.drawCircle(w * 0.27f, h * 0.78f, wheelR * 0.55f, rimPaint)
        canvas.drawCircle(w * 0.73f, h * 0.78f, wheelR * 0.55f, rimPaint)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG)
        accent.color = Color.parseColor("#2D7DF6")
        accent.strokeWidth = h * 0.012f
        accent.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(w * 0.10f, h * 0.74f, w * 0.20f, h * 0.74f, accent)
    }
}
