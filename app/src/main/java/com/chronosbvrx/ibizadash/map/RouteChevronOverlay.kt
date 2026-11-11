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
 * Flechas de dirección sobre la ruta (estilo Waze/CarPlay).
 *
 * No crea una vista por flecha: guarda posiciones geográficas ya calculadas y
 * las proyecta en cada draw. En el A7 solo se dibujan las que caen dentro de
 * la pantalla y como máximo 80 por frame.
 */
class RouteChevronOverlay(context: Context) : Overlay() {

    data class Arrow(
        val point: GeoPoint,
        val bearing: Float,
        /** Índice del punto de ruta donde empieza la flecha. */
        val segment: Int
    )

    private val density = context.resources.displayMetrics.density
    private val arrowSize = 5.5f * density
    private val marginPx = 28f * density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f * density
        color = Color.parseColor("#EAF9FF")
        alpha = 200
    }
    private val path = Path()
    private val screen = Point()

    var arrows: List<Arrow> = emptyList()
    /** Los segmentos anteriores ya se recorrieron: no se pintan. */
    var fromIndex: Int = 0
    var visible: Boolean = false

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible || arrows.isEmpty()) return
        if (mapView.zoomLevelDouble < 14.5) return
        if (mapView.width <= 0 || mapView.height <= 0) return
        val projection = mapView.projection ?: return
        val maxX = mapView.width + marginPx
        val maxY = mapView.height + marginPx
        // Las flechas están ordenadas por segmento: búsqueda binaria para no
        // recorrer una ruta de 200 km entera en cada frame.
        var lo = 0
        var hi = arrows.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (arrows[mid].segment < fromIndex) lo = mid + 1 else hi = mid
        }
        var drawn = 0
        for (i in lo until arrows.size) {
            val arrow = arrows[i]
            try {
                projection.toPixels(arrow.point, screen)
            } catch (_: Exception) {
                continue
            }
            val x = screen.x.toFloat()
            val y = screen.y.toFloat()
            if (x < -marginPx || y < -marginPx || x > maxX || y > maxY) continue
            canvas.save()
            canvas.rotate(arrow.bearing, x, y)
            path.reset()
            path.moveTo(x - arrowSize, y + arrowSize * 0.6f)
            path.lineTo(x, y - arrowSize * 0.6f)
            path.lineTo(x + arrowSize, y + arrowSize * 0.6f)
            canvas.drawPath(path, paint)
            canvas.restore()
            drawn++
            if (drawn >= 80) break
        }
    }
}
