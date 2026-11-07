package com.chronosbvrx.ibizadash.nav

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.chronosbvrx.ibizadash.media.MediaRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Protocolo común del puente con el teléfono (IbizaCompanion).
 *
 * El mismo JSON line-delimited viaja por dos transportes:
 *  - Wi-Fi Direct / TCP (preferido: portadas y mapas sin cuello de botella).
 *  - Bluetooth RFCOMM (respaldo automático cuando no hay Wi-Fi).
 *
 * Aquí sólo vive la semántica de los mensajes; el transporte lo aporta
 * [WifiNavBridgeServer] o [BtNavBridgeServer].
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
                )
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
                        steps = steps
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

            "MEDIA_ART" -> {
                val b64 = obj.optString("pngBase64", "")
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

    /**
     * Guarda una tesela en la caché de osmdroid:
     * files/osmdroid/tiles/EsriDark/{z}/{x}/{y}.png
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

/** Avisa a la UI cuando llegan teselas nuevas para refrescar el MapView. */
object MapTileBridge {
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
