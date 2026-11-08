package com.chronosbvrx.ibizacompanion.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.chronosbvrx.ibizacompanion.BridgeLink
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Voz de navegación generada en el teléfono.
 *
 * El estéreo no tiene TTS ni Internet. Aquí se sintetiza cada frase a un WAV
 * corto, se guarda en caché por hash y se envía por Wi-Fi Direct. El estéreo
 * solo reproduce el archivo cuando toca, sin gastar CPU ni datos del coche.
 */
object VoiceSynthesizer {
    private const val TAG = "VoiceTTS"
    private const val CACHE_DIR = "voice_cache"

    private var tts: TextToSpeech? = null
    private var ready = false
    private val initLatch = CountDownLatch(1)
    private val busy = AtomicBoolean(false)

    /** Inicializa el motor de voz una sola vez. */
    fun init(context: Context) {
        if (tts != null) return
        val app = context.applicationContext
        tts = TextToSpeech(app) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts
                try {
                    val locale = pickLocale(engine)
                    val result = engine?.setLanguage(locale)
                    ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    engine?.setSpeechRate(1.0f)
                    Log.i(TAG, "TTS listo: ${locale} ok=$ready")
                } catch (e: Exception) {
                    Log.w(TAG, "configurar TTS: ${e.message}")
                }
            } else {
                Log.w(TAG, "TTS no disponible (status=$status)")
            }
            initLatch.countDown()
        }
    }

    private fun pickLocale(engine: TextToSpeech?): Locale {
        val candidates = listOf(Locale("es", "MX"), Locale("es", "ES"), Locale("es"))
        for (locale in candidates) {
            try {
                val available = engine?.isLanguageAvailable(locale) ?: -2
                if (available >= TextToSpeech.LANG_AVAILABLE) return locale
            } catch (_: Exception) {
            }
        }
        return Locale("es", "ES")
    }

    /** Sintetiza y envía los clips pedidos por el estéreo. Hilo de fondo. */
    fun handleRequest(context: Context, lang: String, items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        if (!busy.compareAndSet(false, true)) {
            Log.w(TAG, "ya hay una síntesis en curso")
            return
        }
        try {
            init(context)
            initLatch.await(8, TimeUnit.SECONDS)
            if (!ready) {
                Log.w(TAG, "sin motor TTS: no se envía voz")
                return
            }
            val dir = File(context.cacheDir, CACHE_DIR).apply { if (!exists()) mkdirs() }
            for ((key, text) in items) {
                if (text.isBlank()) continue
                val wav = File(dir, "$key.wav")
                if (!wav.exists() || wav.length() < 100) {
                    if (!synthesize(text, wav)) {
                        Log.w(TAG, "no se pudo sintetizar: $text")
                        continue
                    }
                }
                sendClip(key, wav)
            }
            Log.i(TAG, "voz enviada: ${items.size} clips")
        } catch (e: Exception) {
            Log.w(TAG, "handleRequest: ${e.message}")
        } finally {
            busy.set(false)
        }
    }

    private fun synthesize(text: String, out: File): Boolean {
        val engine = tts ?: return false
        val done = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                ok.set(true)
                done.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                done.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                done.countDown()
            }
        })
        return try {
            val params = Bundle()
            val result = engine.synthesizeToFile(text, params, out, "v-${text.hashCode()}")
            if (result != TextToSpeech.SUCCESS) {
                Log.w(TAG, "synthesizeToFile devolvió $result para: $text")
                return false
            }
            val finished = done.await(15, TimeUnit.SECONDS)
            val good = finished && ok.get() && out.exists() && out.length() > 100
            if (!good) out.delete()
            good
        } catch (e: Exception) {
            Log.w(TAG, "synthesize: ${e.message}")
            false
        }
    }

    private fun sendClip(key: String, file: File) {
        try {
            val bytes = file.readBytes()
            if (bytes.size > 600_000) {
                Log.w(TAG, "clip demasiado grande (${bytes.size}B): ${file.name}")
                return
            }
            val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val sent = BridgeLink.send(
                JSONObject()
                    .put("type", "VOICE_CLIP")
                    .put("key", key)
                    .put("wavBase64", b64)
            )
            Log.i(TAG, "clip ${file.name} ${bytes.size}B enviado=$sent")
        } catch (e: Exception) {
            Log.w(TAG, "sendClip: ${e.message}")
        }
    }

    /** Clave estable; debe coincidir con VoiceStore del estéreo. */
    fun keyFor(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(text.trim().lowercase().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }
}
