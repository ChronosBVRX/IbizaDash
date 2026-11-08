package com.chronosbvrx.ibizacompanion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.chronosbvrx.ibizacompanion.AlbumArtReader
import com.chronosbvrx.ibizacompanion.BridgeLink
import com.chronosbvrx.ibizacompanion.BridgeRouter
import com.chronosbvrx.ibizacompanion.WifiBridgeClient
import com.chronosbvrx.ibizacompanion.nav.PhoneNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class MediaBridgeService : Service() {
    companion object {
        const val CHANNEL_ID = "ibiza_bridge_v2"
        const val NOTIF_ID = 1
        const val ACTION_CONNECT = "com.chronosbvrx.ibizacompanion.CONNECT"
        const val ACTION_CLEAR_ROUTE = "com.chronosbvrx.ibizacompanion.CLEAR_ROUTE"
        const val EXTRA_INSTALL_SELF_UPDATE = "install_self_update"
        private const val TAG = "MediaBridge"

        @Volatile
        private var instance: MediaBridgeService? = null

        /**
         * Sube el servicio a foreground con tipo `location` cuando empieza la
         * navegación del teléfono. Así el GPS sigue con la pantalla apagada o
         * la app en segundo plano.
         */
        fun promoteLocationForeground() {
            instance?.promoteLocation()
        }

        /**
         * La proyección del mapa (Fase 2) vive bajo este servicio en primer
         * plano: wake lock parcial + notificación visible mientras se
         * transmite. La WebView del teléfono solo dibuja con la pantalla
         * encendida; esto evita que Android mate el proceso en marcha.
         */
        fun setMapStreaming(active: Boolean) {
            instance?.setMapStreamingInternal(active)
        }
    }

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var job: Job? = null
    private var weatherJob: Job? = null
    private var selfUpdateJob: Job? = null
    private var wifiClient: WifiBridgeClient? = null
    private var router: BridgeRouter? = null
    @Volatile private var lastTrackKey: String? = null
    @Volatile private var currentController: MediaController? = null
    @Volatile private var lastBringToFrontMs = 0L
    private var lastLinkNotification: String? = null
    @Volatile private var wasWifiConnected = false
    /** Last PONG from the stereo; 0 until a PONG-capable build answers. */
    @Volatile private var lastPongMs = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
            it.copy(serviceRunning = true)
        }
        createChannel()
        val notif = buildNotification("Conectando al Ibiza...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        wifiClient = WifiBridgeClient(this) { line -> handleStereoMessage(line) }.apply {
            onState = { state -> onWifiState(state) }
        }
        router = BridgeRouter(wifiClient!!).also {
            BridgeLink.router = it
        }
        PhoneNavController.init(this)
        wifiClient?.start()
        startConnectionWatchdog()
        job = scope.launch {
            while (isActive) {
                try {
                    pollMedia()
                } catch (e: Exception) {
                    Log.w(TAG, "pollMedia: ${e.message}")
                }
                delay(2000L)
            }
        }
        // Clima: se calcula en el teléfono y se envía por Wi-Fi Direct.
        weatherJob = scope.launch {
            while (isActive) {
                try {
                    com.chronosbvrx.ibizacompanion.weather.WeatherSender.tick(this@MediaBridgeService)
                } catch (e: Exception) {
                    Log.w(TAG, "weather: ${e.message}")
                }
                delay(60_000L)
            }
        }
        // Autoactualización de la app del teléfono: revisa al arrancar y cada 6 h.
        // La instalación siempre la confirma el usuario en el diálogo del sistema.
        com.chronosbvrx.ibizacompanion.update.CompanionUpdater.onReady = { version ->
            updateNotification("Actualización $version lista · toca para instalar")
        }
        selfUpdateJob = scope.launch {
            delay(15_000L)
            while (isActive) {
                try {
                    if (com.chronosbvrx.ibizacompanion.data.CompanionPrefs
                            .autoUpdate(this@MediaBridgeService)
                    ) {
                        com.chronosbvrx.ibizacompanion.update.CompanionUpdater
                            .checkForUpdate(this@MediaBridgeService)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "self-update: ${e.message}")
                }
                delay(6 * 60 * 60 * 1000L)
            }
        }
    }

    private var mapWakeLock: android.os.PowerManager.WakeLock? = null
    @Volatile private var mapStreaming = false

    private fun setMapStreamingInternal(active: Boolean) {
        if (active == mapStreaming) return
        mapStreaming = active
        try {
            if (active) {
                val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                val lock = pm?.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "IbizaCompanion:MapStream"
                )
                lock?.acquire()
                mapWakeLock = lock
                updateNotification("Transmitiendo mapa al estéreo")
            } else {
                try {
                    mapWakeLock?.release()
                } catch (_: Exception) {
                }
                mapWakeLock = null
                updateNotification(lastLinkNotification ?: "IbizaCompanion")
            }
        } catch (e: Exception) {
            Log.w(TAG, "mapStreaming: ${e.message}")
        }
    }

    /** Actualiza el FGS a `location` para navegación con pantalla apagada. */
    private fun promoteLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            startForeground(
                NOTIF_ID,
                buildNotification("Navegación activa"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } catch (e: Exception) {
            Log.w(TAG, "FGS location: ${e.message}")
        }
    }

    /** Publica el estado de Wi-Fi Direct en la UI y en la notificación. */
    private fun onWifiState(state: WifiBridgeClient.State) {
        val justConnected = state.connected && !wasWifiConnected
        wasWifiConnected = state.connected
        if (justConnected) {
            // Al abrir el enlace se reenvía la portada y el último clima.
            lastTrackKey = null
            com.chronosbvrx.ibizacompanion.weather.WeatherSender.onLinkUp(this)
            // PING inmediato: el watchdog necesita un PONG fresco para no
            // confundir un enlace nuevo con un estéreo mudo.
            lastPongMs = 0L
            try {
                router?.sendPing()
            } catch (_: Exception) {
            }
        }
        com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
            it.copy(
                wifiConnected = state.connected,
                wifiHost = state.host,
                wifiDetail = state.detail,
                wifiPeers = state.peers
            )
        }
        val label = if (state.connected) {
            "Wi-Fi Direct · ${state.host ?: "conectado"}"
        } else {
            state.detail.takeIf { it.isNotBlank() } ?: "Wi-Fi Direct"
        }
        if (label != lastLinkNotification) {
            lastLinkNotification = label
            updateNotification(label)
        }
    }

    /** Watchdog: si el socket se cae, reconecta solo. */
    private fun startConnectionWatchdog() {
        scope.launch {
            while (isActive) {
                delay(15000L)
                try {
                    val alive = router?.isConnected() == true
                    if (alive) {
                        val last = lastPongMs
                        val now = SystemClock.elapsedRealtime()
                        // Socket abierto pero sin PONG: la app del estéreo murió.
                        // Solo se aplica cuando esta build ya respondió una vez.
                        if (last > 0L && now - last > 35_000L) {
                            Log.i(TAG, "watchdog: estéreo mudo, reconectando")
                            wifiClient?.close()
                            lastPongMs = 0L
                            com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
                                it.copy(wifiConnected = false)
                            }
                            wifiClient?.reconnectNow()
                        } else {
                            router?.sendPing()
                        }
                    } else {
                        Log.i(TAG, "watchdog: enlace caído, reconectando")
                        wifiClient?.reconnectNow()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "watchdog: ${e.message}")
                }
            }
        }
    }

    private fun handleStereoMessage(line: String) {
        try {
            val obj = org.json.JSONObject(line)
            com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
                it.copy(lastStereoMs = SystemClock.elapsedRealtime())
            }
            when (obj.optString("type")) {
                "WELCOME" -> {
                    val vc = obj.optInt("stereoVersionCode", 0)
                    if (vc > 0) {
                        com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
                            it.copy(
                                stereoVersionCode = vc,
                                stereoVersionName = obj.optString("stereoVersionName", "")
                            )
                        }
                    }
                }
                "APK_META_ACK", "APK_CHUNK_ACK", "APK_CHUNK_NACK",
                "APK_END_OK", "OTA_ERROR", "OTA_ALREADY_NEWER" -> {
                    com.chronosbvrx.ibizacompanion.ota.OtaSender.onReply(obj)
                }
                "PONG" -> lastPongMs = SystemClock.elapsedRealtime()
                "NAV_STATE" -> handleNavState(obj)
                "REROUTE_REQUEST" -> handleReroute(obj)
                "MEDIA_CMD" -> handleMediaCommand(obj.optString("cmd", ""))
                "MAP_STREAM" -> {
                    // El estéreo pide detener o retomar el vídeo del mapa.
                    com.chronosbvrx.ibizacompanion.map.MapFrameSender
                        .setSuppressed(!obj.optBoolean("on", true))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "inbox parse: ${e.message}")
        }
    }

    private fun handleNavState(obj: org.json.JSONObject) {
        val active = obj.optBoolean("active", false)
        if (!active) {
            // El estéreo borró la ruta: el teléfono también debe parar.
            PhoneNavController.clear()
            com.chronosbvrx.ibizacompanion.core.state.CompanionState.clearRoute()
            return
        }
        // Si el teléfono es quien calcula, el NAV_STATE del estéreo es un
        // snapshot viejo (o el eco) y no debe pisar el estado local.
        if (PhoneNavController.active) return
        com.chronosbvrx.ibizacompanion.core.state.CompanionState.update { st ->
            st.copy(
                routeActive = true,
                routeDestination = obj.optString("destName", st.routeDestination ?: "Destino"),
                routePolyline = if (obj.has("polyline")) obj.optString("polyline", st.routePolyline) else st.routePolyline,
                routeDistanceM = obj.optInt("totalM", st.routeDistanceM),
                routeDurationS = obj.optInt("totalS", st.routeDurationS),
                routeRemainingM = obj.optInt("remainingM", st.routeRemainingM),
                routeRemainingText = obj.optString("remainingText", st.routeRemainingText),
                routeEtaText = obj.optString("etaText", st.routeEtaText),
                routeNextStep = obj.optString("actionText", st.routeNextStep),
                routeIcon = obj.optString("icon", st.routeIcon),
                routeProgress = obj.optDouble("progress", st.routeProgress.toDouble())
                    .toFloat()
                    .let { p -> if (p.isFinite()) p.coerceIn(0f, 1f) else st.routeProgress },
                routeArrived = obj.optBoolean("arrived", false),
                routeOffRoute = obj.optBoolean("offRoute", false)
            )
        }
    }

    /**
     * Comandos que llegan del estéreo (botones de play/pausa/siguiente).
     * Usan la MediaSession activa del teléfono, sin depender de AVRCP.
     */
    private fun handleMediaCommand(cmd: String) {
        if (cmd.isBlank()) return
        val controller = currentController ?: findMediaController()
        if (controller == null) {
            Log.w(TAG, "MEDIA_CMD $cmd sin MediaSession")
            return
        }
        currentController = controller
        try {
            when (cmd) {
                "play" -> controller.transportControls.play()
                "pause" -> controller.transportControls.pause()
                "toggle" -> {
                    if (controller.playbackState?.state ==
                        android.media.session.PlaybackState.STATE_PLAYING
                    ) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                }
                "next" -> controller.transportControls.skipToNext()
                "prev" -> controller.transportControls.skipToPrevious()
            }
            Log.i(TAG, "MEDIA_CMD $cmd → ${controller.packageName}")
        } catch (e: Exception) {
            Log.w(TAG, "MEDIA_CMD $cmd: ${e.message}")
        }
    }

    private fun findMediaController(): MediaController? {
        return try {
            val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return null
            val controllers = msm.getActiveSessions(
                android.content.ComponentName(this, MediaNotificationListener::class.java)
            )
            pickMusicSession(controllers)
        } catch (_: Exception) {
            null
        }
    }

    private fun handleReroute(obj: org.json.JSONObject) {
        val fromLat = obj.optDouble("lat", 0.0)
        val fromLon = obj.optDouble("lon", 0.0)
        val destLat = obj.optDouble("destLat", 0.0)
        val destLon = obj.optDouble("destLon", 0.0)
        val destName = obj.optString("destName", "Destino")
        Log.i(TAG, "REROUTE_REQUEST $fromLat,$fromLon → $destLat,$destLon")
        updateNotification("Recalculando ruta…")
        scope.launch {
            // Usar la posición del teléfono (va en el auto) si el punto
            // reportado es viejo; si no hay GPS, usar el reportado.
            val fix = awaitFix(8000L)
            val oLat = fix?.latitude ?: fromLat
            val oLon = fix?.longitude ?: fromLon
            val built = com.chronosbvrx.ibizacompanion.data.RouteBuilder.build(
                oLat, oLon, destLat, destLon, destName
            )
            if (built == null) {
                Log.w(TAG, "reroute: OSRM sin respuesta")
                updateNotification("Sin conexión")
                return@launch
            }
            com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
                it.copy(
                    routeActive = true,
                    routeDestination = built.destinationName,
                    routePolyline = built.encodedPolyline,
                    routeDistanceM = built.distanceMeters,
                    routeDurationS = built.durationSeconds,
                    routeRemainingM = built.distanceMeters,
                    routeRemainingText = "Calculando…",
                    routeNextStep = "Esperando GPS",
                    routeIcon = "STRAIGHT",
                    routeProgress = 0f,
                    routeOffRoute = false
                )
            }
            val ok = router?.sendRouteSet(
                built.destinationName,
                built.distanceMeters,
                built.durationSeconds,
                built.encodedPolyline,
                built.steps
            ) == true
            if (ok) {
                PhoneNavController.setRoute(
                    this@MediaBridgeService,
                    built.destinationName,
                    built.distanceMeters,
                    built.durationSeconds,
                    built.encodedPolyline,
                    built.steps
                )
            }
            Log.i(TAG, "reroute enviada=$ok")
            updateNotification(
                if (ok) "Ruta recalculada" else "Sin vehículo (reintentando)"
            )
        }
    }

    /** Un fix de GPS con timeout; null si no hay posición a tiempo. */
    private fun awaitFix(timeoutMs: Long): android.location.Location? {
        return try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return null
            val providers = lm.getProviders(true)
            val provider = when {
                providers.contains(android.location.LocationManager.GPS_PROVIDER) ->
                    android.location.LocationManager.GPS_PROVIDER
                providers.contains(android.location.LocationManager.NETWORK_PROVIDER) ->
                    android.location.LocationManager.NETWORK_PROVIDER
                else -> return lm.getLastKnownLocation(providers.firstOrNull() ?: return null)
            }
            val last = try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
            if (last != null && SystemClock.elapsedRealtime() - last.elapsedRealtimeNanos / 1_000_000L < 30_000L) {
                return last
            }
            val latch = java.util.concurrent.CountDownLatch(1)
            var fix: android.location.Location? = null
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    fix = location
                    latch.countDown()
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener)
                latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
            } finally {
                try {
                    lm.removeUpdates(listener)
                } catch (_: Exception) {
                }
            }
            fix ?: last
        } catch (_: Exception) {
            null
        }
    }

    private fun pollMedia() {
        // Return to IbizaCompanion after the user launched a track externally.
        // Checked before the metadata early-returns because the external app can
        // transiently expose an empty session while switching.
        if (com.chronosbvrx.ibizacompanion.data.PlaybackReturn.shouldReturnNow()) {
            bringAppToFrontIfAllowed()
        }
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        val controllers = try {
            msm.getActiveSessions(android.content.ComponentName(this, MediaNotificationListener::class.java))
        } catch (e: Exception) {
            emptyList()
        }
        val c = pickMusicSession(controllers) ?: return
        currentController = c
        val md = c.metadata ?: return
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val isPlaying = c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        val bmp = AlbumArtReader.readArtworkBitmap(this, c)
        // Update UI state even if metadata is partially empty.
        com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
            it.copy(
                nowPlaying = com.chronosbvrx.ibizacompanion.core.state.NowPlayingState(
                    title = title,
                    artist = artist,
                    album = album,
                    artwork = bmp,
                    isPlaying = isPlaying,
                    packageName = c.packageName
                )
            )
        }
        if (title.isBlank() && artist.isBlank()) return
        val key = "$title|$artist|$album|$isPlaying"
        if (key == lastTrackKey) return
        lastTrackKey = key
        val b64 = if (bmp != null) {
            AlbumArtReader.bitmapToBase64(bmp, router?.mediaArtMaxSize() ?: 256)
        } else ""
        if (b64.isNotBlank()) {
            router?.sendMediaArt(title, artist, album, isPlaying, b64)
        }
        updateNotification("♪ $title — $artist")
        bringAppToFrontIfAllowed()
    }

    private fun bringAppToFrontIfAllowed() {
        // Background activity starts are blocked on Android 10+ unless the app
        // has the "Display over other apps" permission. If granted, return to
        // IbizaCompanion automatically when a new track starts.
        val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this)
        Log.d("MediaBridge", "return: canOverlay=$canOverlay pending=${com.chronosbvrx.ibizacompanion.data.PlaybackReturn.hasPending()}")
        if (!canOverlay) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        val requested = com.chronosbvrx.ibizacompanion.data.PlaybackReturn.consumeIfRecent()
        if (!requested) {
            if (now - lastBringToFrontMs < 5000L) return
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return
            val alreadyForeground = try {
                am.runningAppProcesses?.any {
                    it.processName == packageName && it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                } == true
            } catch (_: Exception) {
                false
            }
            if (alreadyForeground) return
        }
        lastBringToFrontMs = now
        try {
            val intent = Intent(this, com.chronosbvrx.ibizacompanion.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
            Log.i("MediaBridge", "return: startActivity OK")
        } catch (e: Exception) {
            Log.e("MediaBridge", "return: startActivity failed ${e.message}", e)
        }
    }

    private fun pickMusicSession(list: List<android.media.session.MediaController>): android.media.session.MediaController? {
        // Prefer the session that is actually playing.
        val playing = list.filter {
            it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING
        }
        val pool = playing.ifEmpty { list }
        // Prioritize music apps over video (YouTube).
        val musicPackages = listOf(
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            "com.amazon.mp3",
            "deezer.android.app",
            "com.apple.android.music",
            "com.soundcloud.android"
        )
        for (pkg in musicPackages) {
            pool.firstOrNull { it.packageName == pkg }?.let { return it }
        }
        return pool.firstOrNull { it.packageName != "com.google.android.youtube" }
            ?: pool.firstOrNull()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Ibiza Bridge", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Reproducción detectada y acceso rápido a IbizaCompanion"
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        val openApp = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.chronosbvrx.ibizacompanion.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val ready = com.chronosbvrx.ibizacompanion.core.state.CompanionState.state.value
        val built = builder
            .setContentTitle("IbizaCompanion")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_view, "Volver a IbizaCompanion", openApp)

        // Si hay una actualización de la propia app descargada, se ofrece
        // instalarla desde aquí (un toque + confirmación del sistema).
        if (ready.selfUpdateAvailable) {
            val installIntent = Intent(
                this,
                com.chronosbvrx.ibizacompanion.MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_INSTALL_SELF_UPDATE, true)
            }
            val installPending = android.app.PendingIntent.getActivity(
                this,
                1,
                installIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
            built.addAction(
                android.R.drawable.stat_sys_download_done,
                "Instalar v${ready.selfUpdateVersionName}",
                installPending
            )
        }
        return built.build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (router?.isConnected() == true) {
                    updateNotification("Wi-Fi Direct conectado")
                } else {
                    wifiClient?.reconnectNow()
                }
            }
            ACTION_CLEAR_ROUTE -> {
                scope.launch {
                    try {
                        router?.clearRoute()
                    } catch (_: Exception) {
                    }
                    PhoneNavController.clear()
                    com.chronosbvrx.ibizacompanion.core.state.CompanionState.clearRoute()
                    updateNotification("Ruta cancelada")
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            mapWakeLock?.release()
        } catch (_: Exception) {
        }
        mapWakeLock = null
        mapStreaming = false
        com.chronosbvrx.ibizacompanion.core.state.CompanionState.update {
            it.copy(serviceRunning = false, wifiConnected = false)
        }
        super.onDestroy()
        if (instance === this) instance = null
        job?.cancel()
        weatherJob?.cancel()
        selfUpdateJob?.cancel()
        com.chronosbvrx.ibizacompanion.update.CompanionUpdater.onReady = null
        scope.cancel()
        PhoneNavController.clear()
        BridgeLink.router = null
        wifiClient?.stop()
        wifiClient = null
        router = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
