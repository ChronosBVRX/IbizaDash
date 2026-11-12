package com.chronosbvrx.ibizadash.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Sprite del coche para el modo NAV 2.5D.
 *
 * Se dibuja UNA sola vez en un bitmap de 128×128 ARGB_8888 (64 KB exactos,
 * dentro del presupuesto). Reproduce el SEAT Ibiza verde de la referencia:
 * vista trasera, luneta con reposacabezas, logo SEAT, calaveras rojas, placa,
 * difusor y sombra de contacto integrada. Sin assets externos y sin crear
 * nada por frame.
 */
object CarSpriteFactory {

    private const val SIZE = 128

    fun create(context: Context): Drawable {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val d = SIZE / 128f

        // --- Sombra de contacto ---
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                64f * d, 114f * d, 46f * d,
                intArrayOf(0x77000000, 0x33000000, 0x00000000),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.scale(1f, 0.32f, 64f * d, 116f * d)
        canvas.drawCircle(64f * d, 116f * d, 46f * d, shadow)
        canvas.restore()

        // --- Antena de techo ---
        val antenna = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF0A0F14.toInt()
            strokeWidth = 1.6f * d
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(64f * d, 8f * d, 64f * d, 20f * d, antenna)
        canvas.drawCircle(64f * d, 20f * d, 2.4f * d, antenna)

        // --- Carrocería verde (vista trasera) ---
        val body = Path().apply {
            moveTo(30f * d, 100f * d)
            cubicTo(29f * d, 78f * d, 33f * d, 56f * d, 40f * d, 45f * d)
            cubicTo(45f * d, 38f * d, 53f * d, 34f * d, 64f * d, 34f * d)
            cubicTo(75f * d, 34f * d, 83f * d, 38f * d, 88f * d, 45f * d)
            cubicTo(95f * d, 56f * d, 99f * d, 78f * d, 98f * d, 100f * d)
            cubicTo(98f * d, 106f * d, 95f * d, 109f * d, 88f * d, 109f * d)
            lineTo(40f * d, 109f * d)
            cubicTo(33f * d, 109f * d, 30f * d, 106f * d, 30f * d, 100f * d)
            close()
        }
        canvas.drawPath(
            body,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    64f * d, 34f * d, 64f * d, 109f * d,
                    intArrayOf(0xFF4FCB70.toInt(), 0xFF279B4B.toInt(), 0xFF0E5A27.toInt()),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )

        // --- Techo y montantes ---
        canvas.drawPath(
            Path().apply {
                moveTo(42f * d, 44f * d)
                cubicTo(48f * d, 38f * d, 80f * d, 38f * d, 86f * d, 44f * d)
                lineTo(84f * d, 50f * d)
                lineTo(44f * d, 50f * d)
                close()
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2AA24E.toInt() }
        )

        // --- Luneta trasera ---
        val glass = Path().apply {
            moveTo(44f * d, 50f * d)
            lineTo(84f * d, 50f * d)
            lineTo(80f * d, 70f * d)
            lineTo(48f * d, 70f * d)
            close()
        }
        canvas.drawPath(
            glass,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    64f * d, 50f * d, 64f * d, 70f * d,
                    intArrayOf(0xFF44566B.toInt(), 0xFF1B2735.toInt(), 0xFF080D13.toInt()),
                    floatArrayOf(0f, 0.42f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
        // Reposacabezas.
        val headrest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF091018.toInt() }
        canvas.drawRoundRect(RectF(51f * d, 52f * d, 60f * d, 65f * d), 3f * d, 3f * d, headrest)
        canvas.drawRoundRect(RectF(68f * d, 52f * d, 77f * d, 65f * d), 3f * d, 3f * d, headrest)
        // Limpiaparabrisas trasero.
        canvas.drawLine(
            55f * d, 66f * d, 73f * d, 64.5f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A0F14.toInt(); strokeWidth = 1.2f * d
            }
        )

        // --- Tapa del maletero (brillo) ---
        canvas.drawPath(
            Path().apply {
                moveTo(40f * d, 71f * d)
                lineTo(88f * d, 71f * d)
                lineTo(91f * d, 86f * d)
                lineTo(37f * d, 86f * d)
                close()
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    64f * d, 71f * d, 64f * d, 86f * d,
                    intArrayOf(0x33FFFFFF, 0x00000000),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )

        // --- Calaveras ---
        val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, 10f * d,
                intArrayOf(0xFFFF6A5A.toInt(), 0xFFC02018.toInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(30f * d, 72f * d, 51f * d, 82f * d), 4f * d, 4f * d, tail)
        canvas.drawRoundRect(RectF(77f * d, 72f * d, 98f * d, 82f * d), 4f * d, 4f * d, tail)
        val led = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFD9D2.toInt() }
        // Tiras LED en forma de "C" simplificada.
        canvas.drawRoundRect(RectF(33f * d, 74f * d, 44f * d, 76f * d), 1f * d, 1f * d, led)
        canvas.drawRoundRect(RectF(33f * d, 74f * d, 35f * d, 80f * d), 1f * d, 1f * d, led)
        canvas.drawRoundRect(RectF(84f * d, 74f * d, 95f * d, 76f * d), 1f * d, 1f * d, led)
        canvas.drawRoundRect(RectF(93f * d, 74f * d, 95f * d, 80f * d), 1f * d, 1f * d, led)

        // --- Logo SEAT (cromo) ---
        val chrome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFD8DEE6.toInt()
            textSize = 9f * d
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("S", 64f * d, 84f * d, chrome)

        // --- Placa ---
        canvas.drawRoundRect(
            RectF(52f * d, 88f * d, 76f * d, 97f * d),
            1.5f * d, 1.5f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9CDD2.toInt() }
        )
        canvas.drawRect(
            52f * d, 88f * d, 55.5f * d, 97f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C4F9C.toInt() }
        )

        // --- Parachoques, difusor y ruedas ---
        canvas.drawRoundRect(
            RectF(29f * d, 97f * d, 99f * d, 110f * d),
            5f * d, 5f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B1016.toInt() }
        )
        canvas.drawRoundRect(
            RectF(43f * d, 103f * d, 85f * d, 109f * d),
            2.5f * d, 2.5f * d,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8F98A3.toInt() }
        )
        val tire = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF05080C.toInt() }
        canvas.drawRoundRect(RectF(20f * d, 92f * d, 31f * d, 118f * d), 4f * d, 4f * d, tire)
        canvas.drawRoundRect(RectF(97f * d, 92f * d, 108f * d, 118f * d), 4f * d, 4f * d, tire)

        // --- Rim light cian (para separarlo del mapa oscuro) ---
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * d
            strokeCap = Paint.Cap.ROUND
            color = 0x9900E5FF.toInt()
        }
        canvas.drawPath(
            Path().apply {
                moveTo(31f * d, 99f * d)
                cubicTo(30f * d, 76f * d, 34f * d, 56f * d, 41f * d, 45f * d)
                moveTo(97f * d, 99f * d)
                cubicTo(98f * d, 76f * d, 94f * d, 56f * d, 87f * d, 45f * d)
            },
            rim
        )

        return BitmapDrawable(context.resources, bitmap)
    }
}
