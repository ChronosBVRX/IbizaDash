package com.chronosbvrx.ibizadash.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Halo + cono de rumbo bajo el sprite del coche (56 dp) en modo NAV.
 *
 * Son dos bitmaps cacheados de 100 px: no se crea ningún shader por frame.
 * El cono se dibuja en el espacio del mapa; el canvas de osmdroid ya está
 * girado por la orientación, así que en heading-up queda apuntando arriba.
 */
class NavPuckOverlay(context: Context) : Overlay() {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val halo: Bitmap by lazy { buildHalo() }
    private val cone: Bitmap by lazy { buildCone() }
    private val coneBaseFromBottom = 5f * density
    private val screen = Point()

    /** Posición del marcador; se resuelve en cada draw. */
    var anchor: () -> GeoPoint? = { null }

    var bearingDeg: Float = 0f
    var visible: Boolean = false

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val point = anchor() ?: return
        val projection = mapView.projection ?: return
        try {
            projection.toPixels(point, screen)
        } catch (_: Exception) {
            return
        }
        val x = screen.x.toFloat()
        val y = screen.y.toFloat()

        // Cono: apunta al rumbo de la marcha. Al girar el canvas por el rumbo
        // y estar el mapa girado -rumbo, en heading-up queda hacia arriba.
        canvas.save()
        canvas.rotate(bearingDeg, x, y)
        canvas.drawBitmap(
            cone,
            x - cone.width / 2f,
            y - (cone.height - coneBaseFromBottom),
            paint
        )
        canvas.restore()

        // Halo suave, sin rotación.
        canvas.drawBitmap(halo, x - halo.width / 2f, y - halo.height / 2f, paint)
    }

    private fun buildHalo(): Bitmap {
        val size = (104f * density).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2f
        val shader = RadialGradient(
            radius,
            radius,
            radius,
            intArrayOf(0x4400E5FF, 0x2200E5FF, 0x0000E5FF),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawCircle(radius, radius, radius, p)
        return bitmap
    }

    private fun buildCone(): Bitmap {
        val width = (62f * density).toInt().coerceAtLeast(2)
        val height = (78f * density).toInt().coerceAtLeast(2)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val baseY = height - coneBaseFromBottom
        val tipY = 3f * density
        val half = 19f * density
        val path = Path().apply {
            moveTo(cx, baseY)
            lineTo(cx - half, baseY)
            lineTo(cx, tipY)
            lineTo(cx + half, baseY)
            close()
        }
        val shader = LinearGradient(
            cx,
            baseY,
            cx,
            tipY,
            intArrayOf(0x5A00E5FF, 0x2E00E5FF, 0x0000E5FF),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawPath(path, p)
        return bitmap
    }
}
