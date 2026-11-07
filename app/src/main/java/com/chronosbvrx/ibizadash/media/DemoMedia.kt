package com.chronosbvrx.ibizadash.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface

object DemoMedia {
    fun nowPlaying(): NowPlaying = NowPlaying(
        title = "Midnight Drive",
        artist = "The Midnight",
        album = "Endless Summer",
        artwork = cover(512),
        isPlaying = true,
        packageName = "demo",
        isBluetooth = true,
        positionMs = 83_000L,
        durationMs = 225_000L
    )

    private fun cover(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, 0f, size.toFloat(),
            intArrayOf(
                Color.parseColor("#05070B"),
                Color.parseColor("#101A2E"),
                Color.parseColor("#1B2A4A")
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        paint.shader = null
        paint.color = Color.parseColor("#FFB020")
        canvas.drawCircle(size * 0.50f, size * 0.40f, size * 0.22f, paint)

        paint.color = Color.parseColor("#101A2E")
        for (i in 0..5) {
            val y = size * 0.36f + i * size * 0.045f
            canvas.drawRect(
                size * 0.25f, y,
                size * 0.75f, y + size * 0.014f,
                paint
            )
        }

        paint.color = Color.parseColor("#332D7DF6")
        paint.strokeWidth = size * 0.004f
        for (i in 0..10) {
            val x = size * i / 10f
            canvas.drawLine(
                x, size * 0.62f,
                size * 0.5f + (x - size * 0.5f) * 0.45f, size.toFloat(),
                paint
            )
        }
        for (i in 0..6) {
            val t = i / 6f
            val y = size * 0.62f + size * 0.38f * t * t
            canvas.drawLine(0f, y, size.toFloat(), y, paint)
        }

        paint.style = Paint.Style.FILL
        paint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        paint.textSize = size * 0.13f
        canvas.drawText("IBIZA", size / 2f, size * 0.82f, paint)

        paint.color = Color.parseColor("#8A94A6")
        paint.textSize = size * 0.045f
        canvas.drawText("DEMO • BLUETOOTH", size / 2f, size * 0.89f, paint)

        return bitmap
    }
}
