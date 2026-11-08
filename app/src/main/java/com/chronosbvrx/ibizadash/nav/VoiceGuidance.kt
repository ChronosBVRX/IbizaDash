package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Decide cuándo hablar y qué frases pedir al teléfono.
 *
 * Reglas (estilo navegador comercial):
 *  - Avisa al cruzar 800 / 400 / 200 / 100 / 50 m antes de cada maniobra, de
 *    más lejos a más cerca, una sola vez por umbral y maniobra.
 *  - No repite el mismo aviso en menos de 3,5 s ni encadena más de dos clips.
 *  - Anuncia llegada y salida de ruta.
 *
 * El texto de cada maniobra ya viene del teléfono ("Gira a la derecha en
 * Av. Madero"), así que se sintetiza tal cual. Las distancias son un conjunto
 * fijo de frases para que la caché sea pequeña.
 */
object VoiceGuidance {
    private const val TAG = "VoiceGuidance"

    private const val MIN_GAP_MS = 3_500L
    private const val OFF_ROUTE_GAP_MS = 10_000L
    private const val MAX_REQUESTS = 3

    private val BUCKETS = intArrayOf(800, 400, 200, 100, 50)

    private val FIXED_PHRASES = listOf(
        "En ochocientos metros",
        "En cuatrocientos metros",
        "En doscientos metros",
        "En cien metros",
        "En cincuenta metros",
        "Has llegado a tu destino",
        "Fuera de ruta",
        "Recalculando"
    )

    private var lastStepKey: String? = null
    private var lastBucket = Int.MAX_VALUE
    private var lastSpeakMs = 0L
    private var arrivedAnnounced = false
    private var offRouteAnnouncedMs = 0L
    private var requestsSent = 0
    private var lastRequestMs = 0L

    /** Al fijar ruta: reinicia el estado y pide las voces que falten. */
    fun onRouteSet(context: Context, route: NavRoute) {
        lastStepKey = null
        lastBucket = Int.MAX_VALUE
        arrivedAnnounced = false
        offRouteAnnouncedMs = 0L
        requestsSent = 0
        lastRequestMs = 0L
        requestMissing(context, route)
    }

    fun onRouteCleared() {
        lastStepKey = null
        lastBucket = Int.MAX_VALUE
        arrivedAnnounced = false
        offRouteAnnouncedMs = 0L
        requestsSent = 0
    }

    /** Llamado en el hilo de UI cada vez que cambia el estado de navegación. */
    fun onState(context: Context, state: NavState?, route: NavRoute?) {
        if (state == null || route == null) return
        if (!com.chronosbvrx.ibizadash.Prefs.voiceOn(context)) return

        val now = SystemClock.elapsedRealtime()

        // Si faltan clips (p. ej. la ruta llegó antes que las voces), reintenta.
        if (now - lastRequestMs > 45_000L && requestsSent < MAX_REQUESTS) {
            lastRequestMs = now
            requestMissing(context, route)
        }

        if (state.arrived) {
            if (!arrivedAnnounced && now - lastSpeakMs > 1_500L) {
                arrivedAnnounced = true
                speak(context, "Has llegado a tu destino")
            }
            return
        }

        if (state.offRoute) {
            if (now - offRouteAnnouncedMs > OFF_ROUTE_GAP_MS) {
                offRouteAnnouncedMs = now
                speakSequence(context, listOf("Fuera de ruta", "Recalculando"))
            }
            return
        }

        val stepKey = state.actionText
        if (stepKey.isBlank()) return
        if (stepKey != lastStepKey) {
            lastStepKey = stepKey
            lastBucket = Int.MAX_VALUE
        }

        val meters = parseDistanceMeters(state.distText) ?: return
        val bucket = BUCKETS.firstOrNull { meters <= it } ?: return
        if (bucket >= lastBucket) return
        lastBucket = bucket
        if (now - lastSpeakMs < MIN_GAP_MS) return

        val distancePhrase = when (bucket) {
            800 -> "En ochocientos metros"
            400 -> "En cuatrocientos metros"
            200 -> "En doscientos metros"
            100 -> "En cien metros"
            else -> "En cincuenta metros"
        }
        // En tramo recto `actionText` es el nombre del destino: no se dice
        // ("En 400 metros · Morelia" no aporta). Solo se anuncia la maniobra.
        val speakAction = state.actionText != state.destName &&
            state.icon != ManeuverIcon.STRAIGHT
        if (speakAction) {
            speakSequence(context, listOf(distancePhrase, state.actionText))
        } else {
            speak(context, distancePhrase)
        }
    }

    // ------------------------------------------------------------------
    // Reproducción
    // ------------------------------------------------------------------

    private fun speak(context: Context, text: String) {
        speakSequence(context, listOf(text))
    }

    private fun speakSequence(context: Context, texts: List<String>) {
        val clips = ArrayList<File>(texts.size)
        for (text in texts) {
            val key = VoiceStore.keyFor(text)
            val file = VoiceStore.fileFor(context, key)
            if (file.isFile && file.length() > 100) clips.add(file)
        }
        if (clips.isEmpty()) return
        lastSpeakMs = SystemClock.elapsedRealtime()
        VoicePlayer.speak(context, clips)
    }

    // ------------------------------------------------------------------
    // Pedir voces al teléfono
    // ------------------------------------------------------------------

    /** Pide al teléfono los clips que falten para esta ruta. */
    fun requestMissing(context: Context, route: NavRoute) {
        requestsSent++
        val missing = ArrayList<Pair<String, String>>()
        for (text in FIXED_PHRASES) {
            addIfMissing(context, missing, text)
        }
        route.steps.forEach { step ->
            val text = step.instruction.trim()
            if (text.isNotEmpty()) addIfMissing(context, missing, text)
        }
        if (missing.isEmpty()) {
            Log.i(TAG, "voces completas para esta ruta")
            return
        }
        if (!NavLink.wifiActive()) {
            Log.i(TAG, "sin enlace; se pedirán las voces al conectar")
            return
        }
        Log.i(TAG, "pidiendo ${missing.size} clips de voz")
        val items = JSONArray()
        missing.forEach { (key, text) ->
            items.put(JSONObject().put("key", key).put("text", text))
        }
        try {
            NavLink.sendAsync(
                JSONObject()
                    .put("type", "VOICE_REQ")
                    .put("lang", "es-MX")
                    .put("items", items)
            )
        } catch (_: Exception) {
        }
    }

    private fun addIfMissing(
        context: Context,
        out: ArrayList<Pair<String, String>>,
        text: String
    ) {
        val key = VoiceStore.keyFor(text)
        if (!VoiceStore.has(context, key)) out.add(key to text)
    }

    /** "200 m" → 200; "1.2 km" → 1200; "…" → null. */
    private fun parseDistanceMeters(text: String): Int? {
        val clean = text.trim().lowercase().replace(',', '.')
        val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*(km|m)").find(clean) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        return if (match.groupValues[2] == "km") (value * 1000).toInt() else value.toInt()
    }
}
