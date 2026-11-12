package com.chronosbvrx.ibizadash.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Ruta con dos pasadas limpias (casing + core), sin glow ni flechas repetidas.
 *
 * Todo el estado de dibujo vive en el objeto: el `Path`, los `Paint` y el
 * `Point` se reutilizan en cada frame con `path.rewind()`. El vértice de la
 * próxima maniobra y su flecha se dibujan aparte, en
 * [com.chronosbvrx.ibizadash.ui.PerspectiveNavOverlayView], para que la
 * perspectiva 2.5D no los aplaste.
 */
class RouteLineOverlay(context: Context) : Overlay() {

    private val density = context.resources.displayMetrics.density
    private val casingWidth = 18f * density
    private val coreWidth = 12f * density

    private val casingPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = casingWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#004C70")
    }

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = coreWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#00E5FF")
    }

    private val path = Path()
    private val screen = Point()

    var points: List<GeoPoint> = emptyList()
        private set

    var visible: Boolean = true

    fun setPoints(value: List<GeoPoint>) {
        points = value
    }

    fun clear() {
        points = emptyList()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible || points.size < 2) return
        val projection = mapView.projection ?: return
        if (mapView.width <= 0 || mapView.height <= 0) return

        path.rewind()
        var started = false
        for (i in points.indices) {
            val p = points[i]
            try {
                projection.toPixels(p, screen)
            } catch (_: Exception) {
                started = false
                continue
            }
            val x = screen.x.toFloat()
            val y = screen.y.toFloat()
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
        if (path.isEmpty) return
        canvas.drawPath(path, casingPaint)
        canvas.drawPath(path, corePaint)
    }
}
