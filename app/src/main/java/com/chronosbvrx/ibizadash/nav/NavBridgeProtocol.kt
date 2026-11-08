package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizadash.media.MediaRepository
import com.chronosbvrx.ibizadash.weather.WeatherRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Protocolo común del puente con el teléfono (IbizaCompanion).
 *
 * El mismo JSON line-delimited viaja por Wi-Fi Direct / TCP, el único canal
 * de datos entre el teléfono y el estéreo.
 *
 * Aquí sólo vive la semántica de los mensajes; el transporte lo aporta
 * [WifiNavBridgeServer].
 */
object NavBridgeProtocol {
    private const val TAG = "NavBridge"

    const val PROTOCOL_VERSION = 2

    /** Puerto TCP del puente (Wi-Fi Direct o LAN/hotspot). */
    const val TCP_PORT = 47800

    /** Puerto UDP donde el estéreo responde al descubrimiento del teléfono. */
    const val DISCOVERY_PORT = 47801
    const val DISCOVER_REQUEST = "IBIZADASH_DISCOVER"
    const val DISCOVER_REPLY = "IBIZADASH"

    /**
     * Token de sesión del canal TCP. Evita que cualquier equipo de la misma red
     * inyecte rutas o carátulas. No es criptografía: la privacidad real la da
     * el grupo Wi-Fi Direct (WPA2) o el hotspot.
     */
    const val NAV_TOKEN = "chronosbvrx-ibiza-6j"

    /** Procesa una línea JSON completa. [reply] escribe en el mismo socket. */
    fun handle(context: Context, line: String, reply: (JSONObject) -> Unit) {
        if (line.isBlank()) return
        val obj = try {
            JSONObject(line)
        } catch (e: Exception) {
            Log.w(TAG, "JSON inválido: ${e.message}")
            return
        }
        handle(context, obj, reply)
    }

    fun handle(context: Context, obj: JSONObject, reply: (JSONObject) -> Unit) {
        when (obj.optString("type")) {
            "HELLO" -> {
                reply(
                    JSONObject()
                        .put("type", "WELCOME")
                        .put("proto", PROTOCOL_VERSION)
                        .put("transport", obj.optString("transport", "?"))
                        .put(
                            "stereoVersionCode",
                            com.chronosbvrx.ibizadash.ota.OtaReceiver
                                .installedVersionCode(context)
                        )
                        .put(
                            "stereoVersionName",
                            com.chronosbvrx.ibizadash.ota.OtaReceiver
                                .installedVersionName(context)
                        )
                )
            }

            // OTA: el teléfono envía el APK nuevo por este mismo canal.
            "APK_META", "APK_CHUNK", "APK_END" -> {
                val answer = com.chronosbvrx.ibizadash.ota.OtaReceiver.handle(context, obj)
                if (answer != null) reply(answer)
            }

            "ROUTE_SET" -> {
                Log.i(
                    TAG,
                    "ROUTE_SET destino=${obj.optString("destinationName")} " +
                        "pasos=${obj.optJSONArray("steps")?.length() ?: 0}"
                )
                val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
                val steps = ArrayList<NavStep>()
                for (i in 0 until stepsArr.length()) {
                    val s = stepsArr.optJSONObject(i) ?: continue
                    steps.add(
                        NavStep(
                            lat = s.optDouble("lat", 0.0),
                            lon = s.optDouble("lon", 0.0),
                            instruction = s.optString("instruction", ""),
                            maneuver = s.optString("maneuver", "").takeIf { it.isNotBlank() },
                            modifier = s.optString("modifier", "").takeIf { it.isNotBlank() },
                            distanceMeters = s.optInt("distanceMeters", 0)
                        )
                    )
                }
                NavRouteRepository.set(
                    NavRoute(
                        destinationName = obj.optString("destinationName", "Destino"),
                        totalDistanceMeters = obj.optInt("totalDistanceMeters", 0),
                        totalDurationSeconds = obj.optInt("totalDurationSeconds", 0),
                        encodedPolyline = obj.optString("encodedPolyline", ""),
                        steps = steps,
                        trafficDelaySeconds = obj.optInt("trafficDelayS", 0),
                        trafficSections = parseTrafficSections(obj)
                    )
                )
            }

            "NAV_STEP_UPDATE" -> {
                NavRepository.publish(
                    obj.optString("nextInstruction", ""),
                    obj.optString("etaText", ""),
                    null
                )
            }

            "ROUTE_CLEAR" -> {
                NavRouteRepository.clear()
                NavRepository.clear()
            }

            "PING" -> {
                reply(
                    JSONObject()
                        .put("type", "PONG")
                        .put("ts", obj.optLong("ts", System.currentTimeMillis()))
                )
            }

            "TILE_BATCH" -> saveTile(context, obj)

            // Módulos descargables (Fase D): llegan por chunks como el OTA.
            "MODULE_META" -> handleModuleMeta(context, obj, reply)
            "MODULE_CHUNK" -> handleModuleChunk(context, obj, reply)
            "MODULE_END" -> handleModuleEnd(context, obj, reply)

            // Clip de voz sintetizado por el teléfono (WAV corto en base64).
            "VOICE_CLIP" -> {
                val key = obj.optString("key", "")
                val b64 = obj.optString("wavBase64", "")
                if (key.isNotBlank() && b64.isNotBlank()) {
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        if (VoiceStore.save(context, key, bytes)) {
                            Log.i(TAG, "VOICE_CLIP ${bytes.size}B key=$key")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "VOICE_CLIP: ${e.message}")
                    }
                }
            }

            "MEDIA_ART" -> {                val b64 = obj.optString("pngBase64", "")
                val title = obj.optString("title", "")
                val artist = obj.optString("artist", "")
                val album = obj.optString("album", "")
                val isPlaying = obj.optBoolean("isPlaying", true)
                var bmp: android.graphics.Bitmap? = null
                if (b64.isNotBlank()) {
                    try {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        Log.i(
                            TAG,
                            "MEDIA_ART ${bytes.size}B bmp=${bmp?.width}x${bmp?.height} " +
                                "track=$title/$artist"
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "MEDIA_ART: ${e.message}")
                    }
                }
                if (bmp != null || title.isNotBlank() || artist.isNotBlank()) {
                    MediaRepository.updateFromPhone(title, artist, album, isPlaying, bmp)
                }
            }

            "PHONE_NAV_STATE" -> {
                val lat = obj.optDouble("lat", 0.0)
                val lon = obj.optDouble("lon", 0.0)
                val icon = try {
                    ManeuverIcon.valueOf(obj.optString("icon", ManeuverIcon.STRAIGHT.name))
                } catch (_: Exception) {
                    ManeuverIcon.STRAIGHT
                }
                val state = NavState(
                    icon = icon,
                    distText = obj.optString("distText", ""),
                    actionText = obj.optString("actionText", ""),
                    thenText = obj.optString("thenText", "").takeIf { it.isNotBlank() },
                    remainingText = obj.optString("remainingText", ""),
                    etaText = obj.optString("etaText", ""),
                    progress = obj.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                    offRoute = obj.optBoolean("offRoute", false),
                    arrived = obj.optBoolean("arrived", false),
                    destName = obj.optString("destName", ""),
                    lat = lat,
                    lon = lon,
                    snappedLat = obj.optDouble("snappedLat", lat),
                    snappedLon = obj.optDouble("snappedLon", lon),
                    bearing = obj.optDouble("bearing", 0.0).toFloat(),
                    speedKmh = obj.optDouble("speedKmh", 0.0).toFloat()
                )
                PhoneNavBridge.onState(context, state)
            }

            // Clima calculado en el teléfono (el estéreo no usa Internet).
            "WEATHER" -> WeatherRepository.update(
                tempC = obj.optDouble("tempC", Double.NaN),
                code = obj.optInt("code", -1),
                text = obj.optString("text", "")
            )

            "MAP_FRAME" -> RemoteMapBridge.onFrame(obj)

            "MAP_FRAME_STOP" -> RemoteMapBridge.stop()

            "NOTIFY" -> PhoneNotifyBridge.onNotify(obj)

            "TILE_STATS" -> {
                Log.i(
                    TAG,
                    "tiles recibidas=${obj.optInt("sent", 0)}/${obj.optInt("total", 0)}"
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Módulos descargables
    // ------------------------------------------------------------------

    private data class PendingModule(
        val id: String,
        val name: String,
        val type: String,
        val version: Int,
        val size: Long,
        val sha: String
    )

    private val pendingModules = java.util.concurrent.ConcurrentHashMap<String, PendingModule>()

    private fun handleModuleMeta(context: Context, obj: JSONObject, reply: (JSONObject) -> Unit) {
        val id = obj.optString("id", "")
        if (id.isBlank()) return
        com.chronosbvrx.ibizadash.modules.ModuleManager.clearPart(context, id)
        try {
            com.chronosbvrx.ibizadash.modules.ModuleManager
                .partFile(context, id).writeBytes(ByteArray(0))
        } catch (e: Exception) {
            reply(JSONObject().put("type", "MODULE_ERROR").put("id", id).put("reason", "sin espacio"))
            return
        }
        pendingModules[id] = PendingModule(
            id = id,
            name = obj.optString("name", id),
            type = obj.optString("moduleType", "raw"),
            version = obj.optInt("version", 1),
            size = obj.optLong("size", 0L),
            sha = obj.optString("sha256", "")
        )
        Log.i(TAG, "MODULE_META $id tipo=${obj.optString("moduleType")} ${obj.optLong("size")}B")
        reply(JSONObject().put("type", "MODULE_META_ACK").put("id", id))
    }

    private fun handleModuleChunk(context: Context, obj: JSONObject, reply: (JSONObject) -> Unit) {
        val id = obj.optString("id", "")
        if (id.isBlank()) return
        val meta = pendingModules[id] ?: return
        val b64 = obj.optString("data", "")
        if (b64.isBlank()) return
        try {
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            java.io.FileOutputStream(
                com.chronosbvrx.ibizadash.modules.ModuleManager.partFile(context, id),
                true
            ).use { it.write(bytes) }
        } catch (e: Exception) {
            Log.w(TAG, "MODULE_CHUNK: ${e.message}")
            if (meta.size > 0) {
                // Sin ACK por chunk: el teléfono continúa y el END validará.
            }
        }
    }

    private fun handleModuleEnd(context: Context, obj: JSONObject, reply: (JSONObject) -> Unit) {
        val id = obj.optString("id", "")
        val meta = pendingModules.remove(id) ?: run {
            reply(JSONObject().put("type", "MODULE_ERROR").put("id", id).put("reason", "meta desconocida"))
            return
        }
        val (ok, reason) = com.chronosbvrx.ibizadash.modules.ModuleManager.install(
            context = context,
            id = meta.id,
            name = meta.name,
            type = meta.type,
            version = meta.version,
            expectedSize = meta.size,
            expectedSha = meta.sha
        )
        if (ok) {
            if (meta.type == "tiles") MapTileBridge.notifyTileSaved()
            ModulesBridge.notifyChanged()
            reply(
                JSONObject().put("type", "MODULE_END_OK")
                    .put("id", id)
                    .put("version", meta.version)
            )
        } else {
            reply(
                JSONObject().put("type", "MODULE_ERROR")
                    .put("id", id)
                    .put("reason", reason)
            )
        }
    }

    /** Tramos lentos que manda el teléfono (fracción 0..1 del recorrido). */    private fun parseTrafficSections(obj: JSONObject): List<TrafficSpan> {
        val arr = obj.optJSONArray("trafficSections") ?: return emptyList()
        val out = ArrayList<TrafficSpan>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val from = s.optDouble("from", -1.0).toFloat()
            val to = s.optDouble("to", -1.0).toFloat()
            if (from < 0f || to <= from) continue
            out.add(
                TrafficSpan(
                    from = from.coerceIn(0f, 1f),
                    to = to.coerceIn(0f, 1f),
                    delaySeconds = s.optInt("delayS", 0),
                    speedKmh = s.optInt("kmh", 0)
                )
            )
        }
        return out
    }

    /**
     * Guarda una tesela en la caché de osmdroid:     * files/osmdroid/tiles/EsriDark/{z}/{x}/{y}.png
     */
    private fun saveTile(context: Context, obj: JSONObject) {
        try {
            val zoom = obj.optInt("zoom", 0)
            val x = obj.optInt("x", 0)
            val y = obj.optInt("y", 0)
            val b64 = obj.optString("pngBase64", "")
            if (b64.isBlank() || zoom <= 0) return
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            if (bytes.size < 40) return

            val base = File(
                context.getExternalFilesDir(null) ?: context.filesDir,
                "osmdroid/tiles/EsriDark"
            )
            val dir = File(base, "$zoom/$x")
            if (!dir.exists() && !dir.mkdirs()) return
            val target = File(dir, "$y.png")

            // Escritura atómica: osmdroid no debe leer un PNG a medias.
            val tmp = File(dir, "$y.png.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.writeBytes(bytes)
                tmp.delete()
            }
            MapTileBridge.notifyTileSaved()
        } catch (e: Exception) {
            Log.w(TAG, "tile save: ${e.message}")
        }
    }
}

/** Avisa a la UI cuando cambia la lista de módulos instalados. */
object ModulesBridge {
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged() {
        listeners.forEach {
            try {
                it()
            } catch (_: Exception) {
            }
        }
    }
}

/** Avisa a la UI cuando llegan teselas nuevas para refrescar el MapView. */object MapTileBridge {
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun notifyTileSaved() {
        listeners.forEach {
            try {
                it()
            } catch (_: Exception) {
            }
        }
    }
}
