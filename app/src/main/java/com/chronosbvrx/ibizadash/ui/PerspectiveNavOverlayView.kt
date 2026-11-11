package com.chronosbvrx.ibizadash.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import org.osmdroid.api.IGeoPoint

/**
 * Capa de pantalla del modo conducción.
 *
 * Dibuja fuera de la matriz 3D del [PerspectiveMapView]:
 *  - banda de horizonte/neblina en la parte superior,
 *  - pin de destino sin aplastar,
 *  - coche, cono y halo en espacio de pantalla.
 *
 * Son shaders y drawables ya cargados; no crea bitmaps por frame ni capas
 * hardware. La base y la ruta siguen dentro del MapView inclinado.
 */
class PerspectiveNavOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val horizonFraction = 0.17f
    private var lastHorizonH = -1f
    private val haloRadius = 56f * density
    private val coneHeight = 78f * density
    private val coneHalfWidth = 19f * density
    private val carLift = 6f * density

    private var map: PerspectiveMapView? = null
    private var carProvider: (() -> Drawable?)? = null
    private var destProvider: (() -> Drawable?)? = null
    private var destPointProvider: (() -> IGeoPoint?)? = null

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, -coneHeight,
            intArrayOf(0x5A00E5FF, 0x2E00E5FF, 0x0000E5FF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, haloRadius,
            intArrayOf(0x5500E5FF, 0x2800E5FF, 0x0000E5FF),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val conePath = Path().apply {
        moveTo(0f, 0f)
        lineTo(-coneHalfWidth, 0f)
        lineTo(0f, -coneHeight)
        lineTo(coneHalfWidth, 0f)
        close()
    }
    private val destScr = PointF()

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(
        mapView: PerspectiveMapView,
        car: () -> Drawable?,
        destination: () -> Drawable?,
        destinationPoint: () -> IGeoPoint?
    ) {
        map = mapView
        carProvider = car
        destProvider = destination
        destPointProvider = destinationPoint
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        lastHorizonH = -1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val map = map ?: return
        if (map.pitch <= 0.01f) return
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // El pin va primero: si entra en la banda de neblina, esta lo funde.
        drawDestination(canvas, map)
        drawCar(canvas, map, w, h)

        // Horizonte encima de todo lo lejano; el coche está al 79 %, no se tapa.
        // Si por el aspect ratio el borde del mapa cae dentro de la vista, la
        // niebla lo cubre para que nunca se vea el corte del trapecio.
        val mapTop = map.perspectiveTopY()
        val horizonH = when {
            mapTop <= h * 0.10f -> h * horizonFraction
            mapTop < h * 0.34f -> mapTop + 2f * density
            else -> h * 0.30f
        }.coerceIn(h * 0.12f, h * 0.34f)
        ensureHorizonShader(horizonH)
        canvas.drawRect(0f, 0f, w, horizonH, horizonPaint)
        // Sin línea de horizonte: el degradado termina en alpha 0 sobre el
        // propio mapa, así no se ve un corte ni un borde duro.
    }

    private fun ensureHorizonShader(horizonH: Float) {
        if (horizonPaint.shader != null && kotlin.math.abs(horizonH - lastHorizonH) < 1f) return
        lastHorizonH = horizonH
        horizonPaint.shader = LinearGradient(
            0f, 0f, 0f, horizonH.coerceAtLeast(1f),
            intArrayOf(0xFF0D2538.toInt(), 0xE60F344C.toInt(), 0x000F344C),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawDestination(canvas: Canvas, map: PerspectiveMapView) {
        val point = destPointProvider?.invoke() ?: return
        val drawable = destProvider?.invoke() ?: return
        if (!map.perspectiveScreenPoint(point, destScr)) return
        val dw = drawable.intrinsicWidth.coerceAtLeast(1)
        val dh = drawable.intrinsicHeight.coerceAtLeast(1)
        if (destScr.x < -dw || destScr.x > width + dw ||
            destScr.y < -dh || destScr.y > height + dh
        ) return
        drawable.setBounds(
            (destScr.x - dw / 2f).toInt(),
            (destScr.y - dh).toInt(),
            (destScr.x + dw / 2f).toInt(),
            destScr.y.toInt()
        )
        drawable.draw(canvas)
    }

    private fun drawCar(canvas: Canvas, map: PerspectiveMapView, w: Float, h: Float) {
        val cx = w / 2f
        val cy = map.carScreenY()

        // Cono de avance en el suelo, detrás del sprite.
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawPath(conePath, conePaint)
        canvas.restore()

        // Halo elíptico bajo el chasis.
        canvas.save()
        canvas.translate(cx, cy + carLift)
        canvas.scale(1f, 0.55f)
        canvas.drawCircle(0f, 0f, haloRadius, haloPaint)
        canvas.restore()

        // Sprite isométrico erecto: no entra en la matriz 3D del mapa.
        // Se compensa el overscan del suelo y se agranda un poco para que el
        // coche tenga presencia tipo CarPlay sin tapar la ruta.
        val car = carProvider?.invoke() ?: return
        val scale = (map.overscan * 1.18f).coerceIn(1.25f, 1.8f)
        val cw = (car.intrinsicWidth * scale).toInt().coerceAtLeast(1)
        val ch = (car.intrinsicHeight * scale).toInt().coerceAtLeast(1)
        car.setBounds(
            (cx - cw / 2f).toInt(),
            (cy - ch / 2f).toInt(),
            (cx + cw / 2f).toInt(),
            (cy + ch / 2f).toInt()
        )
        car.draw(canvas)
    }
}
