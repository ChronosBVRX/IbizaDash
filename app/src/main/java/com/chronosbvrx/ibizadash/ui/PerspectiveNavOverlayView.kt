package com.chronosbvrx.ibizadash.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizadash.nav.ManeuverIcon
import org.osmdroid.api.IGeoPoint

/**
 * Capa de pantalla del modo conducción.
 *
 * Dibuja fuera de la matriz 3D del [PerspectiveMapView], así que nada de esto
 * se aplasta:
 *  - banda de horizonte/neblina,
 *  - pin de destino,
 *  - globo de la próxima calle + escudo de carretera,
 *  - coche 3D con micro-rotación y su sombra integrada.
 *
 * Todos los `Paint`, `Path` y `RectF` son propiedades: `draw()` no crea
 * objetos. Como máximo se pintan tres etiquetas (badge de maniobra, globo de
 * calle y escudo).
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
    private var car3dProvider: (() -> Drawable?)? = null
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

    // --- Globo de calle y escudo (cero alocaciones en draw) ---
    private val bubbleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7FBFF.toInt()
        style = Paint.Style.FILL
    }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF15212B.toInt()
        textSize = 15f * density
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
    }
    private val bubblePath = Path()
    private val bubbleRect = RectF()
    private val tailPath = Path()

    private val shieldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    private val shieldStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B2330.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val shieldText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF14212B.toInt()
        textSize = 12f * density
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            android.graphics.Typeface.BOLD
        )
        textAlign = Paint.Align.CENTER
    }
    private val shieldPath = Path()
    private val shieldRect = RectF()

    private val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE612151A.toInt()
        style = Paint.Style.FILL
    }
    private val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF00E5FF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val badgeRadius = 20f * density
    private val badgeIcons: Array<Drawable?> = ManeuverIcon.values().map { icon ->
        ContextCompat.getDrawable(context, icon.resId)?.mutate()?.apply {
            setTint(0xFFFFFFFF.toInt())
        }
    }.toTypedArray()

    private var calloutName: String = ""
    private var calloutRef: String = ""
    private var calloutPoint: IGeoPoint? = null
    private var calloutIcon: ManeuverIcon? = null
    private var calloutKey: String = ""
    private var carTiltDeg: Float = 0f

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun bind(
        mapView: PerspectiveMapView,
        car: () -> Drawable?,
        car3d: () -> Drawable?,
        destination: () -> Drawable?,
        destinationPoint: () -> IGeoPoint?
    ) {
        map = mapView
        carProvider = car
        car3dProvider = car3d
        destProvider = destination
        destPointProvider = destinationPoint
        invalidate()
    }

    /** Actualiza el globo/escudo. Solo invalida si cambió la maniobra. */
    fun setCallout(
        name: String?,
        ref: String?,
        icon: ManeuverIcon?,
        lat: Double,
        lon: Double
    ) {
        val safeName = name?.trim().orEmpty().let {
            if (it.length > 26) it.take(25) + "…" else it
        }
        val safeRef = ref?.trim().orEmpty()
        val key = "$safeName|$safeRef|${icon?.name}|$lat|$lon"
        if (key == calloutKey) return
        calloutKey = key
        calloutName = safeName
        calloutRef = safeRef
        calloutIcon = icon
        calloutPoint = if (lat == 0.0 && lon == 0.0) null else
            org.osmdroid.util.GeoPoint(lat, lon)
        invalidate()
    }

    fun clearCallout() {
        if (calloutKey.isEmpty()) return
        calloutKey = ""
        calloutName = ""
        calloutRef = ""
        calloutPoint = null
        calloutIcon = null
        invalidate()
    }

    /** Micro-rotación del coche en curvas (±12°). */
    fun setCarTilt(deg: Float) {
        val clamped = deg.coerceIn(-12f, 12f)
        if (Math.abs(clamped - carTiltDeg) < 0.05f) return
        carTiltDeg = clamped
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
        drawCallout(canvas, map, w, h)
        drawCar(canvas, map, w, h)

        // Horizonte encima de todo lo lejano; el coche está al 79 %, no se tapa.
        val mapTop = map.perspectiveTopY()
        val horizonH = when {
            mapTop <= h * 0.10f -> h * horizonFraction
            mapTop < h * 0.34f -> mapTop + 2f * density
            else -> h * 0.30f
        }.coerceIn(h * 0.12f, h * 0.34f)
        ensureHorizonShader(horizonH)
        canvas.drawRect(0f, 0f, w, horizonH, horizonPaint)
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

    /**
     * Badge de maniobra + globo de la próxima calle + escudo de carretera.
     * Se dibuja pegado al vértice del giro, en espacio de pantalla, para que
     * el `rotateX` no deforme el texto.
     */
    private fun drawCallout(canvas: Canvas, map: PerspectiveMapView, w: Float, h: Float) {
        val point = calloutPoint ?: return
        if (!map.perspectiveScreenPoint(point, destScr)) return
        val x = destScr.x
        val y = destScr.y
        if (x < -160f || x > w + 160f || y < -100f || y > h + 100f) return

        // 1) Badge blanco con el ícono de la maniobra (la "flecha" única).
        canvas.drawCircle(x, y, badgeRadius, badgeFill)
        canvas.drawCircle(x, y, badgeRadius, badgeStroke)
        val icon = calloutIcon?.let { badgeIcons[it.ordinal] }
        if (icon != null) {
            val s = (24f * density).toInt()
            icon.setBounds(
                (x - s / 2f).toInt(),
                (y - s / 2f).toInt(),
                (x + s / 2f).toInt(),
                (y + s / 2f).toInt()
            )
            icon.draw(canvas)
        }

        // 2) Globo de la calle, a un lado del badge para no tapar la ruta.
        if (calloutName.isNotBlank()) {
            val padH = 12f * density
            val padV = 8f * density
            val textW = bubbleText.measureText(calloutName)
            val textH = bubbleText.descent() - bubbleText.ascent()
            val bw = textW + padH * 2f
            val bh = textH + padV * 2f
            val gap = badgeRadius + 8f * density
            var bx = x + gap
            var tailOnRight = false
            if (bx + bw > w - 10f * density) {
                bx = x - gap - bw
                tailOnRight = true
            }
            if (bx < 10f * density) bx = 10f * density
            var by = y - bh / 2f
            by = by.coerceIn(6f * density, (h - bh - 6f * density).coerceAtLeast(6f * density))

            bubbleRect.set(bx, by, bx + bw, by + bh)
            bubblePath.rewind()
            bubblePath.addRoundRect(bubbleRect, 12f * density, 12f * density, Path.Direction.CW)

            // Cola: triángulo que apunta al badge.
            val tipY = y.coerceIn(by + 10f * density, by + bh - 10f * density)
            val baseHalf = 6f * density
            tailPath.rewind()
            if (tailOnRight) {
                tailPath.moveTo(bx + bw, tipY - baseHalf)
                tailPath.lineTo(x - badgeRadius - 3f * density, y)
                tailPath.lineTo(bx + bw, tipY + baseHalf)
            } else {
                tailPath.moveTo(bx, tipY - baseHalf)
                tailPath.lineTo(x + badgeRadius + 3f * density, y)
                tailPath.lineTo(bx, tipY + baseHalf)
            }
            tailPath.close()

            canvas.drawPath(tailPath, bubbleFill)
            canvas.drawPath(bubblePath, bubbleFill)
            canvas.drawPath(bubblePath, bubbleStroke)
            val baseline = by + (bh - textH) / 2f - bubbleText.ascent()
            canvas.drawText(calloutName, bx + padH, baseline, bubbleText)
        }

        // 3) Escudo de carretera (ej. "48D"), pegado al badge.
        if (calloutRef.isNotBlank()) {
            val padH = 8f * density
            val sw = shieldText.measureText(calloutRef) + padH * 2f
            val sh = 20f * density
            val sx = (x - sw / 2f).coerceIn(8f * density, (w - sw - 8f * density).coerceAtLeast(8f * density))
            val sy = y + badgeRadius + 7f * density
            if (sy + sh < h - 8f * density) {
                shieldRect.set(sx, sy, sx + sw, sy + sh)
                shieldPath.rewind()
                shieldPath.addRoundRect(shieldRect, 6f * density, 6f * density, Path.Direction.CW)
                canvas.drawPath(shieldPath, shieldFill)
                canvas.drawPath(shieldPath, shieldStroke)
                val baseline = sy + (sh - (shieldText.descent() - shieldText.ascent())) / 2f -
                    shieldText.ascent()
                canvas.drawText(calloutRef, sx + sw / 2f, baseline, shieldText)
            }
        }
    }

    private fun drawCar(canvas: Canvas, map: PerspectiveMapView, w: Float, h: Float) {
        val cx = w / 2f
        val cy = map.carScreenY()

        val sprite = car3dProvider?.invoke()
        if (sprite != null) {
            // Asset 3D con sombra integrada: sin cono ni halo extra.
            val cw = 112f * density
            val ratio = sprite.intrinsicHeight / sprite.intrinsicWidth.coerceAtLeast(1)
            val ch = cw * ratio
            canvas.save()
            canvas.rotate(carTiltDeg, cx, cy)
            sprite.setBounds(
                (cx - cw / 2f).toInt(),
                (cy - ch * 0.72f).toInt(),
                (cx + cw / 2f).toInt(),
                (cy + ch * 0.28f).toInt()
            )
            sprite.draw(canvas)
            canvas.restore()
            return
        }

        // Respaldo: puck clásico (cono + halo + sprite del marcador).
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawPath(conePath, conePaint)
        canvas.restore()

        canvas.save()
        canvas.translate(cx, cy + carLift)
        canvas.scale(1f, 0.55f)
        canvas.drawCircle(0f, 0f, haloRadius, haloPaint)
        canvas.restore()

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
