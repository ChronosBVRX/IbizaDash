package com.chronosbvrx.ibizadash.ui

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.roundToInt
import org.osmdroid.api.IGeoPoint
import org.osmdroid.views.MapView

/**
 * MapView con perspectiva de conducción 2.5D.
 *
 * La base y las capas de ruta se dibujan con una matriz de cámara (rotateX)
 * alrededor del punto del coche; el sprite del coche, su halo, el pin de
 * destino y el horizonte se dibujan fuera de esta vista, en
 * [PerspectiveNavOverlayView], para que no se aplasten.
 *
 * Solo trabaja sobre el canvas en `dispatchDraw`: no crea bitmaps ni capas.
 * En modo plano (pitch = 0) el coste es el de un MapView normal.
 */
class PerspectiveMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MapView(context, attrs) {

    /** Grados de inclinación hacia el frente. 0 = vista cenital normal. */
    var pitch: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 65f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** Escala extra para que el trapecio inclinado no deje esquinas vacías. */
    var overscan: Float = 1.35f
        set(value) {
            val clamped = value.coerceIn(1f, 1.8f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /**
     * Distancia de cámara en el eje Z. El default -8 de Android es demasiado
     * cercano para una vista de 1280 px y comprime el horizonte en exceso;
     * -24 da una perspectiva de parabrisas sin deformar el centro.
     */
    var perspectiveCameraZ: Float = -24f
        set(value) {
            val clamped = value.coerceIn(-40f, -10f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /** Altura del coche en el viewport (0.79 = 79 % desde arriba). */
    var carScreenFraction: Float = 0.79f
        set(value) {
            val clamped = value.coerceIn(0.5f, 0.9f)
            if (field != clamped) {
                field = clamped
                applyNavOffset()
                invalidate()
            }
        }

    /**
     * En NAV el centro geográfico se desplaza para que el coche quede bajo.
     * El offset se recalcula solo cuando cambia el tamaño del mapa.
     */
    var navFrameEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyNavOffset()
            }
        }

    var onTouchDown: (() -> Unit)? = null
    var onTouchUp: (() -> Unit)? = null

    private val camera = Camera()
    private val drawMatrix = Matrix()
    private val pointMatrix = Matrix()
    private val screenPoint = Point()
    private val mapped = FloatArray(2)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyNavOffset()
    }

    private fun applyNavOffset() {
        if (height <= 0) return
        val target = if (navFrameEnabled) {
            ((carScreenFraction - 0.5f) * height).roundToInt()
        } else {
            0
        }
        if (target != mapCenterOffsetY) {
            setMapCenterOffset(0, target)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (pitch <= 0.01f) {
            super.dispatchDraw(canvas)
            return
        }
        val pivotX = width / 2f
        val pivotY = height * carScreenFraction
        buildPerspectiveMatrix(drawMatrix, pivotX, pivotY)
        canvas.save()
        canvas.concat(drawMatrix)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> onTouchDown?.invoke()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = super.dispatchTouchEvent(ev)
                onTouchUp?.invoke()
                return handled
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Proyecta un punto geográfico al espacio de pantalla **después** de la
     * perspectiva. Lo usa la capa de coche/pin para dibujar sin aplastar.
     */
    fun perspectiveScreenPoint(geo: IGeoPoint, out: PointF): Boolean {
        val proj = try {
            projection
        } catch (_: Exception) {
            null
        } ?: return false
        try {
            proj.toPixels(geo, screenPoint)
        } catch (_: Exception) {
            return false
        }
        var x = screenPoint.x.toFloat()
        var y = screenPoint.y.toFloat()
        if (pitch > 0.01f) {
            // osmdroid dibuja el mapa dentro de su matriz de rotación/zoom
            // (heading-up). El overlay de pantalla está FUERA de esa matriz,
            // así que hay que aplicarla antes de la perspectiva; si no, el pin
            // y el globo quedan desalineados de la ruta.
            try {
                mapped[0] = x
                mapped[1] = y
                proj.scaleRotateCanvasMatrix.mapPoints(mapped)
                x = mapped[0]
                y = mapped[1]
            } catch (_: Exception) {
            }
            buildPerspectiveMatrix(pointMatrix, width / 2f, height * carScreenFraction)
            mapped[0] = x
            mapped[1] = y
            pointMatrix.mapPoints(mapped)
            x = mapped[0]
            y = mapped[1]
        }
        out.set(x, y)
        return true
    }

    fun carScreenY(): Float = height * carScreenFraction

    /**
     * Y del borde superior del mapa ya inclinado. Si es negativo, el mapa
     * sigue por encima del viewport y el horizonte puede ser fijo; si es
     * positivo, la niebla debe cubrir ese borde para que no se vea el corte.
     */
    fun perspectiveTopY(): Float {
        if (pitch <= 0.01f || width <= 0 || height <= 0) return 0f
        buildPerspectiveMatrix(pointMatrix, width / 2f, height * carScreenFraction)
        mapped[0] = 0f
        mapped[1] = 0f
        pointMatrix.mapPoints(mapped)
        val leftY = mapped[1]
        mapped[0] = width.toFloat()
        mapped[1] = 0f
        pointMatrix.mapPoints(mapped)
        return maxOf(leftY, mapped[1])
    }

    private fun buildPerspectiveMatrix(out: Matrix, pivotX: Float, pivotY: Float) {
        out.reset()
        camera.save()
        camera.setLocation(0f, 0f, perspectiveCameraZ)
        camera.rotateX(pitch)
        camera.getMatrix(out)
        camera.restore()
        out.preTranslate(-pivotX, -pivotY)
        out.postTranslate(pivotX, pivotY)
        out.postScale(overscan, overscan, pivotX, pivotY)
    }
}
