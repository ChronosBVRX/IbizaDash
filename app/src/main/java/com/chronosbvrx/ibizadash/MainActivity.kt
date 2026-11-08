package com.chronosbvrx.ibizadash

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.chronosbvrx.ibizadash.car.CarSignals
import com.chronosbvrx.ibizadash.car.AvrcpController
import com.chronosbvrx.ibizadash.car.TwCommandClient
import com.chronosbvrx.ibizadash.car.HumanCarAssistant
import com.chronosbvrx.ibizadash.car.HumanInsight
import com.chronosbvrx.ibizadash.car.HumanVehicleStatus
import com.chronosbvrx.ibizadash.car.Severity
import com.chronosbvrx.ibizadash.bluetooth.BluetoothStatus
import com.chronosbvrx.ibizadash.bluetooth.BluetoothStatusClient
import com.chronosbvrx.ibizadash.map.MapHelper
import com.chronosbvrx.ibizadash.media.MediaKeys
import com.chronosbvrx.ibizadash.media.MediaRepository
import com.chronosbvrx.ibizadash.media.NowPlaying
import com.chronosbvrx.ibizadash.nav.NavRepository
import com.chronosbvrx.ibizadash.nav.NavRoute
import com.chronosbvrx.ibizadash.nav.NavRouteRepository
import com.chronosbvrx.ibizadash.nav.NavState
import com.chronosbvrx.ibizadash.nav.NavStateRepository
import com.chronosbvrx.ibizadash.nav.RouteNavigator
import com.chronosbvrx.ibizadash.nav.MapTileBridge
import com.chronosbvrx.ibizadash.nav.NavLink
import com.chronosbvrx.ibizadash.nav.NavStateRelay
import com.chronosbvrx.ibizadash.nav.PhoneNavBridge
import com.chronosbvrx.ibizadash.nav.PhoneNotifyBridge
import com.chronosbvrx.ibizadash.nav.RemoteMapBridge
import com.chronosbvrx.ibizadash.nav.WifiNavBridgeServer
import com.chronosbvrx.ibizadash.nav.TurnInstruction
import com.chronosbvrx.ibizadash.obd.DtcDictionary
import com.chronosbvrx.ibizadash.obd.Elm327Client
import com.chronosbvrx.ibizadash.obd.ObdRepository
import com.chronosbvrx.ibizadash.obd.ObdTelemetry
import com.chronosbvrx.ibizadash.weather.WeatherRepository
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var map: MapView
    private lateinit var remoteMap: ImageView
    private lateinit var remoteArrow: View
    private lateinit var mapSourceChip: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weatherTop: TextView
    private lateinit var speedText: TextView
    private lateinit var unitText: TextView
    private lateinit var gearText: TextView
    private lateinit var speedSource: TextView
    private lateinit var mediaTitle: TextView
    private lateinit var mediaSubtitle: TextView
    private lateinit var albumArt: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var btStatus: TextView
    private lateinit var btChip: View
    private lateinit var btBatteryGroup: View
    private lateinit var btBatteryBar: ProgressBar
    private lateinit var btBattery: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var mediaProgress: ProgressBar
    private lateinit var mediaTime: TextView
    private lateinit var cockpitHealth: TextView
    private lateinit var cockpitHealthIcon: ImageView
    private lateinit var healthRow: View
    private lateinit var headlightState: TextView
    private lateinit var headlightGlow: View
    private lateinit var dock: LinearLayout

    // Continuous canvas
    private lateinit var leftPane: FrameLayout
    private lateinit var rightPane: FrameLayout
    private lateinit var cockpitPanel: View
    private lateinit var miniHud: View
    private lateinit var mediaModule: View
    private lateinit var radioModule: View
    private lateinit var radioFreq: TextView
    private lateinit var radioBand: TextView
    private lateinit var radioRds: TextView
    private lateinit var radioDial: ProgressBar
    private lateinit var radioNative: TextView
    private lateinit var radioSrcBt: TextView
    private lateinit var radioSrcRadio: TextView
    private lateinit var vehicleModule: View
    private lateinit var navPill: View
    private lateinit var navPillTitle: TextView
    private lateinit var navPillSub: TextView
    private lateinit var navPillIcon: ImageView
    private lateinit var navPillDist: TextView
    private lateinit var hudPill: View
    private lateinit var hudPillSpeed: TextView
    private lateinit var hudPillGear: TextView
    private lateinit var hudPillFuel: TextView
    private lateinit var cockpitFuel: TextView
    private lateinit var alertCapsule: View
    private lateinit var alertText: TextView
    private lateinit var nightDim: View

    // Vista de navegación
    private lateinit var navOverlay: View
    private lateinit var navCard: View
    private lateinit var navCardIcon: ImageView
    private lateinit var navCardDist: TextView
    private lateinit var navCardAction: TextView
    private lateinit var navCardThen: TextView
    private lateinit var navCardEta: TextView
    private lateinit var navCardRemaining: TextView
    private lateinit var navCardDest: TextView
    private lateinit var navCardTraffic: TextView
    private lateinit var navCardProgress: ProgressBar
    private lateinit var navFinish: View
    private lateinit var navExit: View
    private lateinit var btnRecenter: View
    private lateinit var clockBlock: View
    private lateinit var callBanner: View
    private lateinit var callText: TextView
    private lateinit var btnAnswer: TextView
    private lateinit var btnHangup: TextView

    // Mini HUD
    private lateinit var miniSpeed: TextView
    private lateinit var miniGear: TextView
    private lateinit var miniTitle: TextView
    private lateinit var miniArt: ImageView
    private lateinit var miniPlay: ImageButton
    private lateinit var miniWeather: TextView
    private lateinit var miniWeatherIcon: ImageView
    private lateinit var miniDestTitle: TextView
    private lateinit var miniDestSub: TextView
    private lateinit var miniMavStrip: View
    private lateinit var miniMavIcon: ImageView
    private lateinit var miniMavDist: TextView
    private lateinit var miniMavAction: TextView

    // Media module
    private lateinit var mmArt: ImageView
    private lateinit var mmTitle: TextView
    private lateinit var mmArtist: TextView
    private lateinit var mmAlbum: TextView
    private lateinit var mmPlay: ImageButton
    private lateinit var mmProgress: ProgressBar
    private lateinit var mmTime: TextView
    private lateinit var mmSource: TextView
    private lateinit var mmSrcBt: TextView
    private lateinit var mmSrcRadio: TextView

    // Vehicle module
    private lateinit var vSpeed: TextView
    private lateinit var vHumanHeadline: TextView
    private lateinit var vHumanHeroSub: TextView
    private lateinit var vHumanHeroIcon: ImageView
    private lateinit var vHumanStatusPill: TextView
    private lateinit var vHumanThermal: TextView
    private lateinit var vHumanThermalSub: TextView
    private lateinit var vHumanElectrical: TextView
    private lateinit var vHumanElectricalSub: TextView
    private lateinit var vHumanFuel: TextView
    private lateinit var vHumanFuelSub: TextView
    private lateinit var vHumanMaintenance: TextView
    private lateinit var vHumanMaintenanceSub: TextView
    private lateinit var vHumanThermalBar: View
    private lateinit var vHumanElectricalBar: View
    private lateinit var vHumanFuelBar: View
    private lateinit var vHumanMaintenanceBar: View
    private lateinit var vHumanThermalIcon: ImageView
    private lateinit var vHumanElectricalIcon: ImageView
    private lateinit var vHumanFuelIcon: ImageView
    private lateinit var vHumanMaintenanceIcon: ImageView
    private lateinit var vGear: TextView
    private lateinit var vGearText: TextView
    private lateinit var vSource: TextView
    private lateinit var vReverse: TextView
    private lateinit var vBusChip: TextView
    private lateinit var vReverseIcon: ImageView
    private lateinit var vCoolant: TextView
    private lateinit var vCoolantAdv: TextView
    private lateinit var vCoolantState: TextView
    private lateinit var vObdChip: TextView
    private lateinit var vThrottle: TextView
    private lateinit var vThrottleAdv: TextView
    private lateinit var vThrottleState: TextView
    private lateinit var vRpm: TextView
    private lateinit var vRpmAdv: TextView
    private lateinit var vRpmState: TextView
    private lateinit var vLoad: TextView
    private lateinit var vLoadAdv: TextView
    private lateinit var vLoadState: TextView
    private lateinit var vVoltage: TextView
    private lateinit var vVoltageState: TextView
    private lateinit var vIntake: TextView
    private lateinit var vIntakeAdv: TextView
    private lateinit var vIntakeState: TextView
    private lateinit var vTiming: TextView
    private lateinit var vTimingAdv: TextView
    private lateinit var vTimingState: TextView
    private lateinit var vTrim: TextView
    private lateinit var vTrimAdv: TextView
    private lateinit var vTrimState: TextView
    private lateinit var vSpeedObd: TextView
    private lateinit var vSpeedObdAdv: TextView
    private lateinit var vVin: TextView
    private lateinit var vDtc: TextView
    private lateinit var vDtcMeanings: TextView
    private lateinit var vObdConsole: TextView
    private lateinit var pageMotor: View
    private lateinit var pageElec: View
    private lateinit var pageFaults: View
    private lateinit var pageConsole: View

    // Quick settings
    private lateinit var quickSheet: View
    private lateinit var sheetScrim: View
    private lateinit var sheetPanel: View
    private lateinit var brightnessSeek: SeekBar
    private lateinit var brightnessValue: TextView
    private lateinit var swNight: Switch
    private lateinit var swMute: Switch
    private lateinit var swVoice: Switch
    private lateinit var navVoiceStatus: TextView
    private lateinit var modulesStatus: TextView
    private lateinit var canStatus: TextView
    private lateinit var mapsStatus: TextView
    private lateinit var wifiLinkStatus: TextView
    private lateinit var homeStatus: TextView
    private lateinit var otaStatus: TextView

    /** Sin dato, no se muestra: la fila de salud solo aparece cuando hay
        datos OBD o del sensor de luces del MCU. */
    private var hasObdData = false
    private var hasLightData = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE d MMMM", Locale("es", "MX"))

    private var locationOverlay: MyLocationNewOverlay? = null
    private var positionMarker: org.osmdroid.views.overlay.Marker? = null
    private val routeOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private var lastSnapCenterMs = 0L
    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null

    private var carSignals: CarSignals? = null
    private var twClient: TwCommandClient? = null
    private var avrcpController: AvrcpController? = null
    private var obdClient: Elm327Client? = null

    private val obdListener: (ObdTelemetry) -> Unit = { state ->
        // OBD speed feeds the speedometer with top priority (OBD > CAN > GPS).
        carSignals?.setObdSpeed(
            if (state.connected || state.demo) state.speedKmh?.toFloat() else null
        )
        runOnUiThread { renderObd(state) }
    }

    private val mediaListener: (NowPlaying?) -> Unit = { nowPlaying ->
        runOnUiThread { updateMedia(nowPlaying) }
    }

    private val navListener: (TurnInstruction?) -> Unit = { turn ->
        runOnUiThread { updateNavPill(turn) }
    }

    private val navStateListener: (NavState?) -> Unit = { state ->
        runOnUiThread { renderNavState(state) }
    }

    /**
     * Listener de rutas guardado en un campo para poder retirarlo en
     * onDestroy. Antes era un lambda anónimo: quedaba registrado en el
     * repositorio global y retenía la Activity después de recrearla.
     */
    private val routeRepoListener: (NavRoute?) -> Unit = { route ->
        runOnUiThread { onRouteChanged(route) }
    }

    private val weatherUpdate: (WeatherRepository.State) -> Unit = { state ->
        runOnUiThread { renderWeather(state) }
    }

    /**
     * Las teselas llegan en ráfaga desde el teléfono. No conviene invalidar el
     * mapa 300 veces: se agenda un único refresco cada 1.2 s.
     */
    private var mapTileRefreshPending = false
    private val mapTileListener: () -> Unit = {
        uiHandler.post {
            if (!mapTileRefreshPending) {
                mapTileRefreshPending = true
                uiHandler.postDelayed({
                    mapTileRefreshPending = false
                    if (RemoteMapBridge.active) return@postDelayed
                    try {
                        // Las teselas nuevas están en disco; limpiar la caché en
                        // memoria obliga a osmdroid a leerlas en el próximo draw.
                        map.tileProvider?.clearTileCache()
                    } catch (_: Exception) {
                    }
                    try {
                        map.invalidate()
                    } catch (_: Exception) {
                    }
                }, 1200L)
            }
        }
    }

    private var lastMapBearing = 0f

    // Estado de seguimiento del mapa
    private var mapMode = MapMode.FOLLOWING
    private var lastUserMapTouchMs = 0L
    private var lastFollowCenterMs = 0L
    private var currentBearingDeg = 0f
    private var bearingAnimator: android.animation.ValueAnimator? = null
    private var lastNavState: NavState? = null

    /** Waze-style maneuver banner + mini strip + tarjeta de navegación. */
    private fun renderNavState(state: NavState?) {
        lastNavState = state
        if (state == null) {
            miniMavStrip.visibility = View.GONE
            lastMapBearing = 0f
            resetMapBearing()
            renderNavOverlay()
            return
        }
        navPillIcon.setImageResource(state.icon.resId)
        navPillDist.text = state.distText
        navPillTitle.text = state.actionText
        navPillSub.text = if (state.thenText != null) {
            "${state.thenText} · ${state.remainingText}"
        } else {
            "${state.remainingText} · ${state.destName}"
        }
        miniMavStrip.visibility = View.VISIBLE
        miniMavIcon.setImageResource(state.icon.resId)
        miniMavDist.text = state.distText
        miniMavAction.text = state.actionText
        // Marcador sobre la carretera (el centrado lo hace onLocationChanged).
        if (!RemoteMapBridge.active) {
            try {
                positionMarker?.position = GeoPoint(state.snappedLat, state.snappedLon)
                if (state.bearing > 0) positionMarker?.rotation = state.bearing
                map.invalidate()
            } catch (_: Exception) {
            }
        }
        renderNavOverlay()
        com.chronosbvrx.ibizadash.nav.VoiceGuidance.onState(
            this,
            state,
            NavRouteRepository.current
        )
    }

    /** Muestra un frame del mapa renderizado por el teléfono (Fase 2). */
    private fun showRemoteMap(bmp: android.graphics.Bitmap) {
        try {
            if (remoteMap.visibility != View.VISIBLE) {
                remoteMap.visibility = View.VISIBLE
                remoteArrow.visibility = View.VISIBLE
                map.visibility = View.GONE
                try {
                    map.onPause()
                } catch (_: Exception) {
                }
            }
            remoteMap.setImageBitmap(bmp)
            updateMapSourceChip()
        } catch (_: Exception) {
        }
    }

    /** Vuelve al MapView nativo cuando el teléfono deja de mandar frames. */
    private fun hideRemoteMap() {
        try {
            if (remoteMap.visibility != View.VISIBLE) return
            remoteMap.visibility = View.GONE
            remoteArrow.visibility = View.GONE
            remoteMap.setImageBitmap(null)
            map.visibility = View.VISIBLE
            map.onResume()
            map.invalidate()
            updateMapSourceChip()
        } catch (_: Exception) {
        }
    }

    /** Chip de origen del mapa: TELÉFONO (frame remoto) o LOCAL (osmdroid). */
    private fun updateMapSourceChip() {
        try {
            if (!::mapSourceChip.isInitialized) return
            val remote = RemoteMapBridge.active
            mapSourceChip.text = if (remote) "TELÉFONO" else "LOCAL"
            mapSourceChip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (remote) R.color.accent_cyan else R.color.text_muted
                )
            )
        } catch (_: Exception) {
        }
    }

    /** La fila de salud solo existe cuando hay algo real que mostrar. */
    private fun refreshHealthRow() {
        try {
            if (!::healthRow.isInitialized) return
            healthRow.visibility =
                if (hasObdData || hasLightData) View.VISIBLE else View.GONE
        } catch (_: Exception) {
        }
    }

    private val debugNavReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DEBUG_NAV_ACTION) return
            NavRepository.publish(
                intent.getStringExtra("title") ?: "",
                intent.getStringExtra("text") ?: "",
                intent.getStringExtra("sub")
            )
        }
    }

    private val debugCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DEBUG_CALL_ACTION) return
            val name = intent.getStringExtra("name") ?: "Llamada entrante"
            val number = intent.getStringExtra("number")
            showCallBanner(name, number)
        }
    }

    private val DEBUG_NAV_ACTION = "com.chronosbvrx.ibizadash.DEBUG_NAV_NOTIFICATION"

    /** Ventana de reutilización del estado OTA verificado. */
    private val OTA_STATUS_TTL_MS = 30_000L
    private val DEBUG_CALL_ACTION = "com.chronosbvrx.ibizadash.DEBUG_INCOMING_CALL"
    private var bluetoothClient: BluetoothStatusClient? = null

    private val sourceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != "source_recieve") return
            val dump = intent.extras?.keySet()?.joinToString(" | ") { key ->
                "$key=${intent.extras?.get(key)}"
            } ?: ""
            Log.i("VehicleData", "source_recieve extras=[$dump]")
            intent.getStringExtra("source")?.let { vSource.text = it }
        }
    }

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> showCallBanner("Llamada entrante", number)
                TelephonyManager.EXTRA_STATE_OFFHOOK -> showCallBanner("Llamada activa", number)
                TelephonyManager.EXTRA_STATE_IDLE -> hideCallBanner()
            }
        }
    }
    private var lastBluetooth: BluetoothStatus? = null
    private var activeCallText: String? = null
    private var mcuPhoneText: String? = null

    private var reverseActive = false
    private var reverseCameraLaunched = false
    private var mediaListenerRegistered = false

    private val dockItems = mutableListOf<View>()
    private val dockIcons = mutableListOf<ImageView>()
    private val dockLabels = mutableListOf<TextView>()
    private val dockIndicators = mutableListOf<View>()
    private var activeDockIndex = 0

    private enum class Focus(val fraction: Float, val dockIndex: Int) {
        COCKPIT(0.55f, 0),
        NAV(1.00f, 1),
        MEDIA(0.25f, 2),
        VEHICLE(0.25f, 4)
    }

    /** Seguimiento del mapa: el coche manda salvo que el usuario lo mueva. */
    private enum class MapMode { FOLLOWING, FREE }

    private var focus = Focus.COCKPIT
    private var splitFraction = 0.55f
    private var radioSource = false
    private var radioFreqKhz = 99500
    private var radioBandIndex = 0
    private val radioBands = arrayOf("FM1", "FM2", "AM")
    private val radioPresets = IntArray(6) { 0 }
    private var lastSpeedAlert = 0L
    private var lastBarUpdate = 0L
    private var lastTextUpdate = 0L

    // Odómetro: acumulado en memoria y volcado periódico a prefs.
    private var odometerPendingKm = 0f
    private var lastOdometerMs = 0L
    private var odometerFlushMs = 0L

    private fun flushOdometer() {
        if (odometerPendingKm <= 0f) return
        try {
            Prefs.addOdometerKm(this, odometerPendingKm)
        } catch (_: Exception) {
        }
        odometerPendingKm = 0f
        odometerFlushMs = SystemClock.elapsedRealtime()
    }

    private val alertHide = Runnable {
        alertCapsule.animate().alpha(0f).setDuration(180L).withEndAction {
            alertCapsule.visibility = View.GONE
        }.start()
    }

    private val lightReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != "light_recieve") return
            val max = intent.getIntExtra("LightMax", 0)
            val value = intent.getIntExtra("LightValue", 0)
            val ratio = if (max > 0) value.toFloat() / max else 0.5f
            hasLightData = true
            refreshHealthRow()
            headlightGlow.visibility = if (value > 0) View.VISIBLE else View.GONE
            headlightState.text = if (value > 0) "Luces ON" else "Luces off"
            headlightState.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (value > 0) R.color.accent_sky else R.color.text_muted
                )
            )
            // Auto night mode with hysteresis.
            if (ratio < 0.3f) nightDim.visibility = View.VISIBLE
            else if (ratio > 0.5f) nightDim.visibility = View.GONE
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = timeFormat.format(now)
            val raw = dateFormat.format(now)
            date.text = raw.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString()
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)
        bindViews()
        goImmersive()

        setupMap()
        setupDock()
        setupMedia()
        setupQuickSettings()
        setupCar()
        setupObd()
        setupNavBridge()
        try {
            registerReceiver(lightReceiver, IntentFilter("light_recieve"))
        } catch (_: Exception) {
        }
        try {
            registerReceiver(sourceReceiver, IntentFilter("source_recieve"))
            registerReceiver(phoneStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
        } catch (_: Exception) {
        }
        NavRepository.addListener(navListener)
        NavStateRepository.addListener(navStateListener)
        try {
            registerReceiver(debugNavReceiver, IntentFilter(DEBUG_NAV_ACTION))
            registerReceiver(debugCallReceiver, IntentFilter(DEBUG_CALL_ACTION))
        } catch (_: Exception) {
        }
        setupClock()
        requestLocationPermission()

        // El clima lo calcula el teléfono y llega por Wi-Fi Direct (WEATHER).
        WeatherRepository.onUpdate = weatherUpdate
        renderWeather(WeatherRepository.state)
        applyPrefs()
        syncMediaMode()

        bluetoothClient = BluetoothStatusClient(this) { status ->
            runOnUiThread { renderBluetooth(status) }
        }.also { it.start() }
    }

    private fun bindViews() {
        map = findViewById(R.id.map)
        remoteMap = findViewById(R.id.remoteMap)
        remoteArrow = findViewById(R.id.remoteArrow)
        mapSourceChip = findViewById(R.id.mapSourceChip)
        clock = findViewById(R.id.clock)
        date = findViewById(R.id.date)
        weatherTop = findViewById(R.id.weatherTop)
        speedText = findViewById(R.id.speed)
        unitText = findViewById(R.id.unit)
        gearText = findViewById(R.id.gear)
        speedSource = findViewById(R.id.speedSource)
        mediaTitle = findViewById(R.id.mediaTitle)
        mediaSubtitle = findViewById(R.id.mediaSubtitle)
        albumArt = findViewById(R.id.albumArt)
        btnPlay = findViewById(R.id.btnPlay)
        btStatus = findViewById(R.id.btStatus)
        btChip = findViewById(R.id.btChip)
        btBatteryGroup = findViewById(R.id.btBatteryGroup)
        btBatteryBar = findViewById(R.id.btBatteryBar)
        btBattery = findViewById(R.id.btBattery)
        weatherIcon = findViewById(R.id.weatherIcon)
        mediaProgress = findViewById(R.id.mediaProgress)
        mediaTime = findViewById(R.id.mediaTime)
        cockpitHealth = findViewById(R.id.cockpitHealth)
        headlightState = findViewById(R.id.headlightState)
        healthRow = findViewById(R.id.healthRow)
        healthRow.visibility = View.GONE // Sin dato, no se muestra (ver refreshHealthRow).
        headlightGlow = findViewById(R.id.headlightGlow)
        callBanner = findViewById(R.id.callBanner)
        callText = findViewById(R.id.callText)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnHangup = findViewById(R.id.btnHangup)
        dock = findViewById(R.id.dock)

        leftPane = findViewById(R.id.leftPane)
        rightPane = findViewById(R.id.rightPane)
        cockpitPanel = findViewById(R.id.cockpitPanel)
        miniHud = findViewById(R.id.miniHud)
        mediaModule = findViewById(R.id.mediaModule)
        radioModule = findViewById(R.id.radioModule)
        vehicleModule = findViewById(R.id.vehicleModule)
        navPill = findViewById(R.id.navPill)
        navPillTitle = findViewById(R.id.navPillTitle)
        navPillSub = findViewById(R.id.navPillSub)
        navPillIcon = findViewById(R.id.navPillIcon)
        navPillDist = findViewById(R.id.navPillDist)
        hudPill = findViewById(R.id.hudPill)
        hudPillSpeed = findViewById(R.id.hudPillSpeed)
        hudPillGear = findViewById(R.id.hudPillGear)
        hudPillFuel = findViewById(R.id.hudPillFuel)
        cockpitFuel = findViewById(R.id.cockpitFuel)
        alertCapsule = findViewById(R.id.alertCapsule)
        alertText = findViewById(R.id.alertText)
        nightDim = findViewById(R.id.nightDim)

        navOverlay = findViewById(R.id.navOverlay)
        navCard = findViewById(R.id.navCard)
        navCardIcon = findViewById(R.id.navCardIcon)
        navCardDist = findViewById(R.id.navCardDist)
        navCardAction = findViewById(R.id.navCardAction)
        navCardThen = findViewById(R.id.navCardThen)
        navCardEta = findViewById(R.id.navCardEta)
        navCardRemaining = findViewById(R.id.navCardRemaining)
        navCardDest = findViewById(R.id.navCardDest)
        navCardTraffic = findViewById(R.id.navCardTraffic)
        navCardProgress = findViewById(R.id.navCardProgress)
        navFinish = findViewById(R.id.navFinish)
        navExit = findViewById(R.id.navExit)
        btnRecenter = findViewById(R.id.btnRecenter)
        clockBlock = findViewById(R.id.clockBlock)

        navExit.setOnClickListener { applyFocus(Focus.COCKPIT) }
        navFinish.setOnClickListener {
            // Termina la ruta en el estéreo y avisa al teléfono.
            NavRouteRepository.clear()
            applyFocus(Focus.COCKPIT)
        }
        btnRecenter.setOnClickListener {
            mapMode = MapMode.FOLLOWING
            lastUserMapTouchMs = 0L
            updateRecenterVisibility()
            val target = positionMarker?.position ?: lastLocation?.let {
                GeoPoint(it.latitude, it.longitude)
            }
            if (target != null) {
                try {
                    map.controller.animateTo(target, map.zoomLevelDouble, 450L)
                } catch (_: Exception) {
                }
            }
        }

        miniSpeed = findViewById(R.id.miniSpeed)
        miniGear = findViewById(R.id.miniGear)
        miniTitle = findViewById(R.id.miniTitle)
        miniArt = findViewById(R.id.miniArt)
        miniPlay = findViewById(R.id.miniPlay)
        miniWeather = findViewById(R.id.miniWeather)
        miniWeatherIcon = findViewById(R.id.miniWeatherIcon)
        miniDestTitle = findViewById(R.id.miniDestTitle)
        miniDestSub = findViewById(R.id.miniDestSub)
        miniMavStrip = findViewById(R.id.miniMavStrip)
        miniMavIcon = findViewById(R.id.miniMavIcon)
        miniMavDist = findViewById(R.id.miniMavDist)
        miniMavAction = findViewById(R.id.miniMavAction)

        mmArt = findViewById(R.id.mmArt)
        mmTitle = findViewById(R.id.mmTitle)
        mmArtist = findViewById(R.id.mmArtist)
        mmAlbum = findViewById(R.id.mmAlbum)
        mmPlay = findViewById(R.id.mmPlay)
        mmProgress = findViewById(R.id.mmProgress)
        mmTime = findViewById(R.id.mmTime)
        mmSource = findViewById(R.id.mmSource)
        mmSrcBt = findViewById(R.id.mmSrcBt)
        mmSrcRadio = findViewById(R.id.mmSrcRadio)

        vSpeed = findViewById(R.id.vSpeed)
        vHumanHeadline = findViewById(R.id.vHumanHeadline)
        vHumanHeroSub = findViewById(R.id.vHumanHeroSub)
        vHumanHeroIcon = findViewById(R.id.vHumanHeroIcon)
        vHumanStatusPill = findViewById(R.id.vHumanStatusPill)
        vHumanThermal = findViewById(R.id.vHumanThermal)
        vHumanThermalSub = findViewById(R.id.vHumanThermalSub)
        vHumanElectrical = findViewById(R.id.vHumanElectrical)
        vHumanElectricalSub = findViewById(R.id.vHumanElectricalSub)
        vHumanFuel = findViewById(R.id.vHumanFuel)
        vHumanFuelSub = findViewById(R.id.vHumanFuelSub)
        vHumanMaintenance = findViewById(R.id.vHumanMaintenance)
        vHumanMaintenanceSub = findViewById(R.id.vHumanMaintenanceSub)
        vHumanThermalBar = findViewById(R.id.vHumanThermalBar)
        vHumanElectricalBar = findViewById(R.id.vHumanElectricalBar)
        vHumanFuelBar = findViewById(R.id.vHumanFuelBar)
        vHumanMaintenanceBar = findViewById(R.id.vHumanMaintenanceBar)
        vHumanThermalIcon = findViewById(R.id.vHumanThermalIcon)
        vHumanElectricalIcon = findViewById(R.id.vHumanElectricalIcon)
        cockpitHealthIcon = findViewById(R.id.cockpitHealthIcon)
        vHumanFuelIcon = findViewById(R.id.vHumanFuelIcon)
        vHumanMaintenanceIcon = findViewById(R.id.vHumanMaintenanceIcon)
        val techContainer = findViewById<android.widget.LinearLayout>(R.id.layoutTechDataContainer)
        val toggleBtn = findViewById<View>(R.id.btnToggleTechData)
        val toggleLabel = findViewById<TextView>(R.id.vToggleLabel)
        val toggleChevron = findViewById<ImageView>(R.id.vToggleChevron)
        var techVisible = false
        toggleBtn.setOnClickListener {
            techVisible = !techVisible
            techContainer.visibility = if (techVisible) View.VISIBLE else View.GONE
            toggleLabel.text =
                if (techVisible) "Cerrar panel de diagnóstico"
                else "Abrir panel de diagnóstico"
            toggleChevron.setImageResource(
                if (techVisible) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
            )
        }
        findViewById<Button>(R.id.btnOilChange).setOnClickListener { Prefs.setLastOilChangeKm(this, Prefs.totalOdometerKm(this)); Prefs.setOilLevelCheckedNow(this); renderHumanNow() }
        findViewById<Button>(R.id.btnOilCheck).setOnClickListener { Prefs.setOilLevelCheckedNow(this); renderHumanNow() }
        findViewById<Button>(R.id.tabMotor).setOnClickListener { selectVehiclePage(0) }
        findViewById<Button>(R.id.tabElec).setOnClickListener { selectVehiclePage(1) }
        findViewById<Button>(R.id.tabFaults).setOnClickListener { selectVehiclePage(2) }
        findViewById<Button>(R.id.tabConsole).setOnClickListener { selectVehiclePage(3); refreshObdConsole() }
        findViewById<Button>(R.id.btnConsoleRefresh).setOnClickListener { refreshObdConsole() }
        findViewById<Button>(R.id.btnConsoleClear).setOnClickListener { com.chronosbvrx.ibizadash.obd.ObdConsole.clear(); refreshObdConsole() }
        findViewById<Button>(R.id.btnDtcRefresh).setOnClickListener {
            Toast.makeText(this, "Releyendo fallas…", Toast.LENGTH_SHORT).show()
            obdClient?.refreshDtcs()
            uiHandler.postDelayed({ renderFaults(ObdRepository.current); refreshObdConsole() }, 3000L)
        }
        findViewById<Button>(R.id.btnDtcDictionary).setOnClickListener {
            showDtcDictionary()
        }
        findViewById<Button>(R.id.btnProbeFuel).setOnClickListener {
            Toast.makeText(this, "Probando 012F… mira la consola", Toast.LENGTH_SHORT).show()
            obdClient?.probeFuel()
            uiHandler.postDelayed({ refreshObdConsole() }, 3000L)
        }
        findViewById<Button>(R.id.btnProbeVin).setOnClickListener {
            Toast.makeText(this, "Leyendo VIN… mira la consola", Toast.LENGTH_SHORT).show()
            obdClient?.probeVin()
            uiHandler.postDelayed({ refreshObdConsole() }, 3000L)
        }
        findViewById<Button>(R.id.btnFuelUds).setOnClickListener {
            Toast.makeText(this, "Consultando nivel en el cuadro (UDS)…", Toast.LENGTH_SHORT).show()
            obdClient?.probeFuelUds()
            uiHandler.postDelayed({ refreshObdConsole() }, 5000L)
        }
        findViewById<Button>(R.id.btnFuelCal).setOnClickListener {
            val raw = ObdRepository.current.fuelRaw
            if (raw == null) {
                Toast.makeText(
                    this,
                    "Primero lee el nivel UDS con el tanque lleno",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Prefs.setFuelCalibration(this, raw, Prefs.fuelEmptyRaw(this))
                Toast.makeText(
                    this,
                    "Tanque lleno calibrado con raw=$raw",
                    Toast.LENGTH_LONG
                ).show()
                obdClient?.probeFuelUds()
                renderHumanNow()
            }
        }
        findViewById<Button>(R.id.btnFuelCalEmpty).setOnClickListener {
            val raw = ObdRepository.current.fuelRaw
            if (raw == null) {
                Toast.makeText(
                    this,
                    "Primero lee el nivel UDS con el tanque en reserva",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Prefs.setFuelCalibration(this, Prefs.fuelFullRaw(this), raw)
                Toast.makeText(
                    this,
                    "Reserva calibrada con raw=$raw",
                    Toast.LENGTH_LONG
                ).show()
                obdClient?.probeFuelUds()
                renderHumanNow()
            }
        }
        vGear = findViewById(R.id.vGear)
        vGearText = findViewById(R.id.vGearText)
        vSource = findViewById(R.id.vSource)
        vReverse = findViewById(R.id.vReverse)
        vBusChip = findViewById(R.id.vBusChip)
        vReverseIcon = findViewById(R.id.vReverseIcon)
        vCoolant = findViewById(R.id.vCoolant)
        vCoolantAdv = findViewById(R.id.vCoolantAdv)
        vCoolantState = findViewById(R.id.vCoolantState)
        vObdChip = findViewById(R.id.vObdChip)
        vThrottle = findViewById(R.id.vThrottle)
        vThrottleAdv = findViewById(R.id.vThrottleAdv)
        vThrottleState = findViewById(R.id.vThrottleState)
        vRpm = findViewById(R.id.vRpm)
        vRpmAdv = findViewById(R.id.vRpmAdv)
        vRpmState = findViewById(R.id.vRpmState)
        vLoad = findViewById(R.id.vLoad)
        vLoadAdv = findViewById(R.id.vLoadAdv)
        vLoadState = findViewById(R.id.vLoadState)
        vVoltage = findViewById(R.id.vVoltage)
        vVoltageState = findViewById(R.id.vVoltageState)
        vIntake = findViewById(R.id.vIntake)
        vIntakeAdv = findViewById(R.id.vIntakeAdv)
        vIntakeState = findViewById(R.id.vIntakeState)
        vTiming = findViewById(R.id.vTiming)
        vTimingAdv = findViewById(R.id.vTimingAdv)
        vTimingState = findViewById(R.id.vTimingState)
        vTrim = findViewById(R.id.vTrim)
        vTrimAdv = findViewById(R.id.vTrimAdv)
        vTrimState = findViewById(R.id.vTrimState)
        vSpeedObd = findViewById(R.id.vSpeedObd)
        vSpeedObdAdv = findViewById(R.id.vSpeedObdAdv)
        vVin = findViewById(R.id.vVin)
        vDtc = findViewById(R.id.vDtc)
        vDtcMeanings = findViewById(R.id.vDtcMeanings)
        vObdConsole = findViewById(R.id.vObdConsole)
        pageMotor = findViewById(R.id.pageMotor)
        pageElec = findViewById(R.id.pageElec)
        pageFaults = findViewById(R.id.pageFaults)
        pageConsole = findViewById(R.id.pageConsole)
        selectVehiclePage(0)
        vObdChip.setOnClickListener { showObdDialog() }

        btnAnswer.setOnClickListener { answerCall() }
        btnHangup.setOnClickListener { hangupCall() }

        btStatus.setOnClickListener { showBluetoothDetails() }
        btChip.setOnClickListener { showBluetoothDetails() }
        navPill.setOnClickListener {
            // El lienzo principal conserva la ruta y la guía activas; abrir
            // MapActivity llevaba a un mapa sin la ruta del viaje.
            applyFocus(Focus.NAV)
        }
        findViewById<View>(R.id.miniExpand).setOnClickListener {
            applyFocus(Focus.COCKPIT)
        }
        findViewById<View>(R.id.miniBack).setOnClickListener {
            applyFocus(Focus.COCKPIT)
        }
        findViewById<View>(R.id.miniMediaCard).setOnClickListener {
            applyFocus(Focus.MEDIA)
        }
        miniPlay.setOnClickListener { togglePlayback() }
        miniArt.setOnClickListener { applyFocus(Focus.MEDIA) }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            openQuickSettings()
        }
    }

    private val mediaCmdExecutor =
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "MediaCmdTx").apply { isDaemon = true }
        }

    private fun sendPhoneMediaCommand(cmd: String) {
        mediaCmdExecutor.execute {
            try {
                NavLink.sendToPhone(
                    org.json.JSONObject()
                        .put("type", "MEDIA_CMD")
                        .put("cmd", cmd)
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun hasPhoneLink(): Boolean = NavLink.wifiActive()

    private fun togglePlayback() {
        val playing = MediaRepository.current?.isPlaying == true
        // El móvil manda: su MediaSession conoce play/pausa mejor que AVRCP.
        // La escritura va a un hilo de red (Android prohíbe socket en main).
        if (!MediaRepository.isDemo() &&
            MediaRepository.current?.isBluetooth != false &&
            hasPhoneLink()
        ) {
            sendPhoneMediaCommand(if (playing) "pause" else "play")
            MediaRepository.current?.let {
                MediaRepository.updateBluetooth(it.copy(isPlaying = !playing))
            }
            return
        }
        val address = lastBluetooth?.address
        val avrcpSent = if (playing) {
            avrcpController?.pause(address)
        } else {
            avrcpController?.play(address)
        }
        if (avrcpSent == true) {
            MediaRepository.current?.let {
                MediaRepository.updateBluetooth(it.copy(isPlaying = !playing))
            }
            return
        }
        val viaMcu = if (playing) twClient?.btPause() else twClient?.btPlay()
        if (viaMcu == true) {
            MediaRepository.current?.let {
                MediaRepository.updateBluetooth(it.copy(isPlaying = !playing))
            }
            return
        }
        if (!MediaRepository.playPause()) {
            MediaKeys.send(this, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    private fun setupMap() {
        MapHelper.configure(this, map)
        map.controller.setCenter(MapHelper.MORELIA)
        // Si el usuario arrastra el mapa, se deja de seguir hasta que pulse
        // "Centrar" o pasen 10 s sin tocarlo.
        map.setOnTouchListener { _, event ->
            if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                markMapFree()
            }
            false
        }
        map.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    if (focus != Focus.NAV) {
                        applyFocus(Focus.NAV)
                    }
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            })
        )
    }

    private fun setupNavBridge() {
        NavRouteRepository.addListener(routeRepoListener)
        PhoneNavBridge.start(this)
        RemoteMapBridge.attach(
            onFrame = { bmp -> runOnUiThread { showRemoteMap(bmp) } },
            onStop = { runOnUiThread { hideRemoteMap() } }
        )
        PhoneNotifyBridge.attach { msg -> runOnUiThread { showAlert(msg) } }
        // OTA: cuando el teléfono termina de enviar el APK, avisar en la
        // cápsula (sin modal) para que el usuario lo instale cuando quiera.
        com.chronosbvrx.ibizadash.ota.OtaReceiver.onReady = { pending ->
            runOnUiThread {
                otaStatusCheckedMs = 0L
                otaStatusPending = pending
                showAlert("Actualización v${pending.versionName} lista · Ajustes rápidos")
            }
        }
        // Wi-Fi Direct: portadas y teselas sin el cuello de botella de RFCOMM.
        WifiNavBridgeServer.start(this)
        MapTileBridge.addListener(mapTileListener)
        NavStateRelay.start()
        // Debug route injection.
        try {
            registerReceiver(debugRouteReceiver, IntentFilter("com.chronosbvrx.ibizadash.DEBUG_ROUTE_SET"))
        } catch (_: Exception) {
        }
    }

    private fun onRouteChanged(route: NavRoute?) {
        if (route == null) {
            clearNavRoute()
            RouteNavigator.stop()
            PhoneNavBridge.reset()
            NavStateRepository.clear()
            NavStateRelay.onRouteCleared()
            com.chronosbvrx.ibizadash.nav.VoiceGuidance.onRouteCleared()
            com.chronosbvrx.ibizadash.nav.VoicePlayer.stop(this)
            lastNavState = null
            lastMapBearing = 0f
            resetMapBearing()
            // Sin ruta se permite de nuevo la proyección del teléfono.
            requestMapStream(true)
            RemoteMapBridge.stop()
            renderNavOverlay()
            if (focus == Focus.NAV) applyFocus(Focus.COCKPIT)
        } else {
            drawNavRoute(route)
            NavStateRelay.onRouteSet(route)
            RouteNavigator.start(this)
            // Pide al teléfono las voces de esta ruta (si faltan).
            com.chronosbvrx.ibizadash.nav.VoiceGuidance.onRouteSet(this, route)
            // Con ruta, el estéreo dibuja su propio mapa (60 fps) y el teléfono
            // deja de mandar vídeo: menos red y mejor sensación.
            requestMapStream(false)
            if (RemoteMapBridge.active) RemoteMapBridge.stop()
        }
    }

    private fun drawNavRoute(route: NavRoute) {
        clearNavRoute()
        val points = MapHelper.decodePolyline(route.encodedPolyline)
        if (points.isEmpty()) return
        val polyline = Polyline(map)
        polyline.setPoints(points)
        polyline.outlinePaint.color = android.graphics.Color.parseColor("#00E5FF")
        polyline.outlinePaint.strokeWidth = 10f
        polyline.isGeodesic = false
        map.overlays.add(polyline)
        routeOverlays.add(polyline)

        // Tramos con tráfico: se pintan encima y más gruesos, como en Waze.
        if (route.trafficSections.isNotEmpty() && points.size > 2) {
            for (span in route.trafficSections) {
                val fromIdx = (span.from * (points.size - 1)).toInt()
                    .coerceIn(0, points.size - 2)
                val toIdx = (span.to * (points.size - 1)).toInt()
                    .coerceIn(fromIdx + 1, points.size - 1)
                val sub = points.subList(fromIdx, toIdx + 1)
                if (sub.size < 2) continue
                val overlay = Polyline(map)
                overlay.setPoints(ArrayList(sub))
                overlay.outlinePaint.color = trafficColor(span.speedKmh)
                overlay.outlinePaint.strokeWidth = 13f
                overlay.isGeodesic = false
                map.overlays.add(overlay)
                routeOverlays.add(overlay)
            }
        }
        val end = points.last()
        val marker = Marker(map)
        marker.position = end
        marker.title = route.destinationName
        map.overlays.add(marker)
        routeOverlays.add(marker)
        // Keep the road-snapped position marker on top of the route line.
        positionMarker?.let { pm ->
            if (map.overlays.contains(pm)) {
                map.overlays.remove(pm)
                map.overlays.add(pm)
            }
        }
        map.controller.animateTo(points.first())
        map.invalidate()
        applyFocus(Focus.NAV)
        NavRepository.publish(route.destinationName, "A ${route.totalDistanceMeters / 1000} km · ${route.totalDurationSeconds / 60} min", null)
    }

    /** Color del tramo según la velocidad efectiva que reporta el proveedor. */
    private fun trafficColor(kmh: Int): Int = when {
        kmh in 1..20 -> Color.parseColor("#EF4444")
        kmh in 21..40 -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#EAB308")
    }

    private fun clearNavRoute() {        if (routeOverlays.isNotEmpty()) {
            routeOverlays.forEach { map.overlays.remove(it) }
            routeOverlays.clear()
        }
        map.invalidate()
    }

    private val debugRouteReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val json = intent.getStringExtra("routeJson")
            if (json.isNullOrBlank()) {
                // inject a default test route
                val test = NavRoute(
                    destinationName = "Centro Histórico, Morelia",
                    totalDistanceMeters = 8450,
                    totalDurationSeconds = 1140,
                    encodedPolyline = MapHelper.encodeTestPolyline(),
                    steps = emptyList()
                )
                NavRouteRepository.set(test)
            } else {
                try {
                    val obj = org.json.JSONObject(json)
                    val spans = ArrayList<com.chronosbvrx.ibizadash.nav.TrafficSpan>()
                    obj.optJSONArray("trafficSections")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            spans.add(
                                com.chronosbvrx.ibizadash.nav.TrafficSpan(
                                    from = s.optDouble("from", 0.0).toFloat(),
                                    to = s.optDouble("to", 0.0).toFloat(),
                                    delaySeconds = s.optInt("delayS", 0),
                                    speedKmh = s.optInt("kmh", 0)
                                )
                            )
                        }
                    }
                    val route = NavRoute(
                        destinationName = obj.optString("destinationName", "Destino"),
                        totalDistanceMeters = obj.optInt("totalDistanceMeters", 0),
                        totalDurationSeconds = obj.optInt("totalDurationSeconds", 0),
                        encodedPolyline = obj.optString("encodedPolyline", ""),
                        steps = emptyList(),
                        trafficDelaySeconds = obj.optInt("trafficDelayS", 0),
                        trafficSections = spans
                    )
                    NavRouteRepository.set(route)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun setupCar() {
        avrcpController = AvrcpController(this).also { it.start() }
        carSignals = CarSignals(this) { signals ->
            runOnUiThread { renderSignals(signals) }
        }.also { it.start() }

        twClient = TwCommandClient(
            this,
            onReverse = { reverse -> runOnUiThread { onReverseChanged(reverse) } },
            onPhoneStatus = { status -> runOnUiThread { onPhoneStatus(status) } },
            onCallStatus = { status, number, name ->
                runOnUiThread { onCallStatus(status, number, name) }
            },
            onConnectedStatus = { status -> runOnUiThread { onBtConnected(status) } },
            onVehicleBundle = { bundle -> runOnUiThread { onVehicleBundle(bundle) } },
            onCarInfo = { extras -> runOnUiThread { onCarInfo(extras) } }
        ).also { it.start() }
    }

    private fun onVehicleBundle(bundle: Bundle) {
        // The raw CAN frames are logged by VehicleCallback until the byte map
        // is validated against physical actions (doors, lights, RPM).
    }

    private fun onCarInfo(extras: Bundle) {
        val rawTemp = extras.get("water_temperature")?.toString()
        if (!rawTemp.isNullOrBlank()) {
            val value = rawTemp.replace(',', '.')
                .replace(Regex("[^0-9.\\-]"), "")
                .toFloatOrNull()
            if (value != null && value > -40f && value < 150f) {
                vCoolant.text = "${value.roundToInt()} °C"
            }
        }
    }

    private fun selectVehiclePage(index: Int) {
        pageMotor.visibility = if (index == 0) View.VISIBLE else View.GONE
        pageElec.visibility = if (index == 1) View.VISIBLE else View.GONE
        pageFaults.visibility = if (index == 2) View.VISIBLE else View.GONE
        pageConsole.visibility = if (index == 3) View.VISIBLE else View.GONE
        if (index == 2) renderFaults(ObdRepository.current)
        if (index == 3) refreshObdConsole()
    }

    private fun refreshObdConsole() {
        try {
            vObdConsole.text = com.chronosbvrx.ibizadash.obd.ObdConsole.snapshot()
        } catch (_: Exception) {
        }
    }

    private fun updateNavPill(turn: TurnInstruction?) {
        val title = if (turn == null) "Sin destino activo" else turn.title.ifBlank { turn.text }
        val subtitle = if (turn == null) {
            "Toca para ver el mapa"
        } else {
            turn.text.takeIf { it.isNotBlank() && it != turn.title } ?: (turn.sub ?: "")
        }
        navPillTitle.text = title
        navPillSub.text = subtitle
        miniDestTitle.text = title
        miniDestSub.text = subtitle
    }

    private fun showCallBanner(name: String, number: String?) {
        callText.text = if (number.isNullOrBlank()) name else "$name · $number"
        callBanner.visibility = View.VISIBLE
        callBanner.alpha = 0f
        callBanner.animate().alpha(1f).setDuration(180L).start()
    }

    private fun hideCallBanner() {
        callBanner.animate().alpha(0f).setDuration(160L).withEndAction {
            callBanner.visibility = View.GONE
        }.start()
    }

    private fun answerCall() {
        if (twClient?.btAccept() != true) {
            try { MediaKeys.send(this, KeyEvent.KEYCODE_CALL) } catch (_: Exception) { }
        }
        hideCallBanner()
    }

    private fun hangupCall() {
        if (twClient?.btHangUP() != true) {
            try { MediaKeys.send(this, KeyEvent.KEYCODE_ENDCALL) } catch (_: Exception) { }
        }
        hideCallBanner()
    }

    private fun setupObd() {
        ObdRepository.addListener(obdListener)
        obdClient = Elm327Client(this)
        val demo = intent.getBooleanExtra("obd_demo", false) || Prefs.obdDemo(this)
        val mac = Prefs.obdMac(this)
        if (demo) {
            obdClient?.connect(null, demo = true)
        } else {
            obdClient?.connect(mac, demo = false)
        }
    }

    private fun renderObd(state: ObdTelemetry) {
        val now = SystemClock.elapsedRealtime()
        val updateTexts = now - lastTextUpdate >= 250L
        val updateBars = now - lastBarUpdate >= 66L
        if (updateTexts) lastTextUpdate = now
        if (updateBars) lastBarUpdate = now

        if (updateTexts) {
            val chipColor = when {
                state.error != null -> R.color.amber
                state.demo -> R.color.amber
                state.hasLiveData -> R.color.green
                state.connected -> R.color.amber
                else -> R.color.text_muted
            }
            vObdChip.setTextColor(ContextCompat.getColor(this, chipColor))
            vObdChip.text = when {
                state.error != null -> state.error.take(14)
                state.demo -> "OBD DEMO"
                state.hasLiveData -> "OBD OK"
                // Enlace abierto pero la ECU no contesta: no decir "OK".
                state.connected -> "OBD sin datos"
                else -> "OBD —"
            }
        }

        if (updateTexts) {
            // Sin conexión o sin lecturas vivas: guiones honestos en vez de
            // valores viejos.
            val s = if (!state.connected && !state.demo) ObdTelemetry() else state
            vRpm.text = s.rpm?.toString() ?: "--"
            vRpmAdv.text = rpmAdvice(s.rpm)
            vLoad.text = s.loadPct?.let { "${it.roundToInt()} %" } ?: "--"
            vLoadAdv.text = loadAdvice(s.loadPct, s.rpm)
            vThrottle.text = s.throttlePct?.let { "${it.roundToInt()} %" } ?: "--"
            vThrottleAdv.text = throttleAdvice(s.throttlePct)
            vCoolant.text = s.coolantC?.let { "${it.roundToInt()} °C" } ?: "--"
            vCoolantAdv.text = coolantAdvice(s.coolantC)
            vIntake.text = s.intakeC?.let { "${it.roundToInt()} °C" } ?: "--"
            vIntakeAdv.text = intakeAdvice(s.intakeC)
            vTiming.text = s.timingAdvanceDeg?.let { "%.0f°".format(it) } ?: "--"
            vTimingAdv.text = timingAdvice(s.timingAdvanceDeg)
            vTrim.text = s.fuelTrimPct?.let { "%+.1f %%".format(it) } ?: "--"
            vTrimAdv.text = trimAdvice(s.fuelTrimPct)
            vSpeedObd.text = s.speedKmh?.let { "$it km/h" } ?: "--"
            vSpeedObdAdv.text = if (s.speedKmh != null) "PID 010D de la ECU" else "Sin lectura"

            vVoltage.text = s.batteryV?.let { "%.1f V".format(it) } ?: "--"
            vVoltageState.text = batteryAdvice(s.batteryV, s.rpm)
            vVoltageState.setTextColor(
                ContextCompat.getColor(this, batteryStateColor(s.batteryV, s.rpm))
            )

            applyMetricState(vRpmState, rpmState(s.rpm))
            applyMetricState(vLoadState, loadState(s.loadPct))
            applyMetricState(vThrottleState, throttleState(s.throttlePct))
            applyMetricState(vCoolantState, coolantState(s.coolantC))
            applyMetricState(vIntakeState, intakeState(s.intakeC))
            applyMetricState(vTimingState, timingState(s.timingAdvanceDeg))
            applyMetricState(vTrimState, trimState(s.fuelTrimPct))

            state.vin?.let { vVin.text = it }
            renderFaults(state)
        }

        // Odómetro: se integra por tiempo real entre publicaciones. Antes se
        // sumaba una fracción fija por cada aviso, así que dependía del ritmo
        // del adaptador y escribía prefs cada segundo. Ahora se acumula en
        // memoria y se vuelca cada minuto (y al pausar).
        val spd = state.speedKmh ?: 0
        if (spd > 2 && lastOdometerMs > 0L) {
            val dtHours = (now - lastOdometerMs).coerceIn(0L, 5_000L) / 3_600_000f
            odometerPendingKm += spd * dtHours
        }
        lastOdometerMs = now
        if (odometerPendingKm >= 0.05f && now - odometerFlushMs >= 60_000L) {
            flushOdometer()
        }

        if (updateTexts) {
            val human = HumanCarAssistant.evaluate(this, state, spd)
            renderHuman(human, state)
            // Proactive alert capsule for critical/warning insights (fuel level
            // excluded: this ECU answers NO DATA to PID 012F, so there is no
            // real fuel signal to alert on).
            val alertInsight = listOf(human.thermalMessage, human.electricalMessage, human.maintenanceMessage)
                .firstOrNull { it.voiceOrBannerAlert && (it.severity == Severity.WARNING || it.severity == Severity.CRITICAL) }
            if (alertInsight != null) {
                showHumanAlert(alertInsight)
            }
        }
    }

    private fun renderFaults(state: ObdTelemetry) {
        if (state.connected) {
            val hasCodes = state.dtcs.isNotEmpty()
            vDtc.text = when {
                hasCodes -> state.dtcs.joinToString(", ")
                state.milOn == true -> "MIL encendido · ${state.dtcCount ?: 0} código(s)"
                else -> "Sin códigos"
            }
            vDtc.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (hasCodes || state.milOn == true) R.color.danger else R.color.green
                )
            )
            vDtcMeanings.text = when {
                hasCodes -> "Diccionario: ${DtcDictionary.MODEL_NAME}\n\n" +
                    state.dtcs.joinToString("\n\n") { DtcDictionary.explain(it) }
                state.milOn == true -> "MIL encendido sin códigos leídos. Si el check parpadea, detente: hay fallo de encendido activo."
                else -> "Sin fallas almacenadas. La ECU reporta todo en orden. " +
                    "Puedes consultar el diccionario del 6J 2.0 con el botón de abajo."
            }
        } else if (!state.demo) {
            vDtc.text = "No leído"
            vDtc.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            vDtcMeanings.text = "Sin conexión con el adaptador OBD-II."
        }
    }

    /**
     * Explorador del diccionario del coche. Incluye filtros táctiles (funcionan
     * aunque la ROM no tenga teclado) y búsqueda por código o síntoma.
     */
    private fun showDtcDictionary() {
        val all = DtcDictionary.all().map { "${it.first}  —  ${it.second}" }
        var shown = all
        var group = 0
        var query = ""
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(6), dp(20), 0)
        }
        val search = android.widget.EditText(this).apply {
            hint = "Buscar: P0171, EVAP, bobina, termostato…"
            setSingleLine(true)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
        }
        val list = android.widget.ListView(this).apply {
            dividerHeight = 1
            divider = android.graphics.drawable.ColorDrawable(
                ContextCompat.getColor(this@MainActivity, R.color.line)
            )
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.obsidian_900))
        }
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount(): Int = shown.size
            override fun getItem(position: Int): Any = shown[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(
                position: Int,
                convertView: android.view.View?,
                parent: android.view.ViewGroup?
            ): android.view.View {
                val tv = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
                    setPadding(dp(18), dp(12), dp(18), dp(12))
                    textSize = 12f
                    setLineSpacing(0f, 1.15f)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    setBackgroundColor(
                        ContextCompat.getColor(this@MainActivity, R.color.obsidian_900)
                    )
                }
                tv.text = shown[position]
                return tv
            }
        }
        list.adapter = adapter

        fun applyFilter() {
            shown = all.filter { line ->
                val code = line.substringBefore(" ")
                val okGroup = when (group) {
                    1 -> code.startsWith("P0")
                    2 -> code.startsWith("P1")
                    3 -> code.isNotEmpty() && code[0].isDigit()
                    4 -> code.startsWith("P07")
                    else -> true
                }
                okGroup && (query.isEmpty() || line.contains(query, ignoreCase = true))
            }
            adapter.notifyDataSetChanged()
        }

        val filterRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val labels = listOf("TODO", "P0", "P1/VAG", "5 DÍG.", "CAJA")
        val buttons = ArrayList<Button>(labels.size)
        labels.forEachIndexed { idx, label ->
            val button = Button(this).apply {
                text = label
                textSize = 10f
                isAllCaps = false
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(4), 0, dp(4), 0)
                setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        if (idx == 0) R.color.accent_cyan else R.color.text_dim
                    )
                )
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setOnClickListener {
                    group = idx
                    buttons.forEachIndexed { i, btn ->
                        btn.setTextColor(
                            ContextCompat.getColor(
                                this@MainActivity,
                                if (i == idx) R.color.accent_cyan else R.color.text_dim
                            )
                        )
                    }
                    applyFilter()
                }
            }
            buttons.add(button)
            filterRow.addView(button, LinearLayout.LayoutParams(0, dp(34), 1f))
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })
        container.addView(filterRow)
        container.addView(search)
        val metrics = resources.displayMetrics
        container.addView(
            list,
            android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (metrics.heightPixels * 0.45f).toInt()
            )
        )
        val dialog = AlertDialog.Builder(this)
            .setTitle("Fallas · ${DtcDictionary.MODEL_NAME}")
            .setView(container)
            .setNegativeButton("Cerrar", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                (metrics.widthPixels * 0.82f).toInt(),
                (metrics.heightPixels * 0.88f).toInt()
            )
        }
        dialog.show()
    }

    /** Estado corto y visual de cada métrica: color + palabra cotidiana. */
    private data class MetricState(val label: String, val color: Int)

    private fun applyMetricState(view: TextView, state: MetricState) {
        view.text = state.label
        view.setTextColor(ContextCompat.getColor(this, state.color))
    }

    private fun rpmState(rpm: Int?): MetricState = when {
        rpm == null -> MetricState("Sin dato", R.color.text_muted)
        rpm < 300 -> MetricState("Apagado", R.color.text_muted)
        rpm < 1000 -> MetricState("Ralentí", R.color.accent_sky)
        rpm <= 2500 -> MetricState("Óptimo", R.color.green)
        rpm <= 3400 -> MetricState("Alto", R.color.accent_sky)
        else -> MetricState("Muy alto", R.color.amber)
    }

    private fun loadState(load: Float?): MetricState = when {
        load == null -> MetricState("Sin dato", R.color.text_muted)
        load < 60f -> MetricState("Normal", R.color.green)
        load < 85f -> MetricState("Alto", R.color.accent_sky)
        else -> MetricState("Exigido", R.color.amber)
    }

    private fun throttleState(pct: Float?): MetricState = when {
        pct == null -> MetricState("Sin dato", R.color.text_muted)
        pct < 5f -> MetricState("En reposo", R.color.text_muted)
        pct < 40f -> MetricState("Suave", R.color.green)
        pct < 80f -> MetricState("Acelerando", R.color.accent_sky)
        else -> MetricState("A fondo", R.color.amber)
    }

    private fun coolantState(t: Float?): MetricState = when {
        t == null -> MetricState("Sin dato", R.color.text_muted)
        t < 60f -> MetricState("Frío", R.color.accent_sky)
        t <= 104f -> MetricState("Óptimo", R.color.green)
        t <= 111f -> MetricState("Caliente", R.color.amber)
        else -> MetricState("Peligro", R.color.danger)
    }

    private fun intakeState(t: Float?): MetricState = when {
        t == null -> MetricState("Sin dato", R.color.text_muted)
        t < 50f -> MetricState("Fresco", R.color.green)
        t <= 70f -> MetricState("Tibio", R.color.accent_sky)
        else -> MetricState("Caliente", R.color.amber)
    }

    private fun timingState(t: Float?): MetricState = when {
        t == null -> MetricState("Sin dato", R.color.text_muted)
        t in 5f..20f -> MetricState("Normal", R.color.green)
        t < 0f -> MetricState("Atrasado", R.color.amber)
        else -> MetricState("Avanzado", R.color.accent_sky)
    }

    private fun trimState(t: Float?): MetricState = when {
        t == null -> MetricState("Sin dato", R.color.text_muted)
        t in -10f..10f -> MetricState("Equilibrada", R.color.green)
        t > 10f -> MetricState("Pobre", R.color.amber)
        else -> MetricState("Rica", R.color.amber)
    }

    private fun batteryStateColor(v: Float?, rpm: Int?): Int = when {
        v == null -> R.color.text_muted
        (rpm ?: 0) < 300 && v < 11.9f -> R.color.amber
        (rpm ?: 0) >= 500 && v < 13.1f -> R.color.danger
        v in 13.2f..14.8f -> R.color.green
        v > 14.8f -> R.color.amber
        else -> R.color.accent_sky
    }

    private fun rpmAdvice(rpm: Int?): String = when {
        rpm == null -> "Sin lectura."
        rpm < 300 -> "Motor apagado o apenas arrancando."
        rpm < 1000 -> "En reposo. Si tiembla, conviene revisar bujías."
        rpm <= 2500 -> "Rango cómodo para circular."
        rpm <= 3400 -> "Normal al acelerar o subir una cuesta."
        else -> "Gira muy alto: sube de marcha y no lo sostengas en frío."
    }

    private fun loadAdvice(load: Float?, rpm: Int?): String = when {
        load == null -> "Sin lectura."
        load < 25 && (rpm ?: 0) < 1000 -> "Trabajo ligero en reposo, normal."
        load < 60 -> "Esfuerzo moderado, buen ritmo."
        load < 85 -> "Va trabajando fuerte: aceleración o subida."
        else -> "Trabajo muy alto: si sigue así en plano, revisa frenos o peso."
    }

    private fun throttleAdvice(p: Float?): String = when {
        p == null -> "Sin lectura."
        p < 5 -> "Pie fuera del acelerador."
        p < 40 -> "Aceleración suave."
        p < 80 -> "Acelerando con ganas."
        else -> "A fondo: solo para rebasar, no lo sostengas."
    }

    private fun coolantAdvice(t: Float?): String = when {
        t == null -> "Sin lectura."
        t < 60 -> "Aún frío: maneja suave y evita acelerones."
        t <= 104 -> "En su temperatura ideal."
        t <= 111 -> "Más caliente de lo normal: modera la marcha y vigila."
        else -> "PELIGRO: detente en un lugar seguro y no abras el tapón caliente."
    }

    private fun intakeAdvice(t: Float?): String = when {
        t == null -> "Sin lectura."
        t < 50 -> "Aire fresco, buena combustión."
        t <= 70 -> "Aire tibio; normal en tráfico."
        else -> "Aire muy caliente: pierdes potencia; revisa el filtro."
    }

    private fun batteryAdvice(v: Float?, rpm: Int?): String = when {
        v == null -> "Sin lectura todavía."
        (rpm ?: 0) < 300 && v < 11.9 -> "Batería baja con el motor apagado: enciende o carga pronto."
        (rpm ?: 0) >= 500 && v < 13.1 -> "No está cargando: revisa la banda del alternador y las terminales."
        v in 13.2f..14.8f -> "Cargando bien."
        v > 14.8 -> "Voltaje alto: conviene revisar el regulador."
        else -> "Valor en transición; observa si baja de 12 V con el motor apagado."
    }

    private fun trimAdvice(t: Float?): String = when {
        t == null -> "Sin lectura."
        t in -10f..10f -> "Mezcla bien ajustada."
        t > 10 -> "El motor pide más gasolina: puede entrar aire de más."
        else -> "El motor recibe gasolina de más: conviene revisar inyectores."
    }

    private fun timingAdvice(t: Float?): String = when {
        t == null -> "Sin lectura."
        t in 5f..20f -> "Encendido bien ajustado en marcha."
        t < 0 -> "Encendido atrasado: el motor se está protegiendo."
        else -> "Encendido adelantado: normal con poca carga."
    }

    private fun trimInsight(state: ObdTelemetry): HumanInsight {
        val t = state.fuelTrimPct
        if (t == null) return HumanInsight("trim", "Sin lectura de mezcla", "Esperando PID 0106", Severity.INFO)
        return when {
            t in -10f..10f -> HumanInsight("trim_ok", "Mezcla %+.1f %%".format(t), "La ECU ajusta bien la mezcla", Severity.GOOD)
            t > 10 -> HumanInsight("trim_lean", "Mezcla pobre %+.1f %%".format(t), "Posible fuga de vacío o MAF sucio", Severity.WARNING, true)
            else -> HumanInsight("trim_rich", "Mezcla rica %.1f %%".format(t), "Posible exceso de presión o inyector", Severity.WARNING, true)
        }
    }

    /**
     * Nivel real del cuadro (UDS 0x2206) con reserva honesta: si el cuadro no
     * responde, se muestra "sin lectura", nunca un valor inventado.
     */
    private fun fuelInsight(state: ObdTelemetry): HumanInsight {
        if (!state.connected && !state.demo) {
            return HumanInsight(
                "fuel_na", "Sin lectura de nivel",
                "Conecta el adaptador OBD y enciende el contacto", Severity.INFO
            )
        }
        val pct = state.fuelLevelPct ?: state.fuelPct
        val source = state.fuelSource ?: if (state.fuelPct != null) "OBD" else null
        if (pct == null) {
            return HumanInsight(
                "fuel_na", "Sin lectura de nivel",
                "El cuadro se consulta por UDS 0x2206", Severity.INFO
            )
        }
        val liters = state.fuelLevelLiters
            ?: (pct * Prefs.fuelTankLiters(this) / 100f)
        val title = "Combustible ${pct.roundToInt()} %"
        val subtitle = buildString {
            append("≈%.1f L".format(liters))
            if (!source.isNullOrBlank()) append(" · $source")
        }
        val severity = when {
            pct <= 10f -> Severity.CRITICAL
            pct <= 25f -> Severity.WARNING
            else -> Severity.GOOD
        }
        return HumanInsight("fuel_level", title, subtitle, severity, pct <= 10f)
    }

    private fun renderHumanNow() {
        try {
            val state = ObdRepository.current
            renderHuman(HumanCarAssistant.evaluate(this, state, state.speedKmh ?: 0), state)
        } catch (_: Exception) {
        }
    }

    private fun renderHuman(human: HumanVehicleStatus, state: ObdTelemetry) {
        hasObdData = state.connected || state.demo
        refreshHealthRow()
        val fuelPct = if (state.connected || state.demo) {
            state.fuelLevelPct ?: state.fuelPct
        } else {
            null
        }
        if (fuelPct != null) {
            hudPillFuel.visibility = View.VISIBLE
            cockpitFuel.visibility = View.VISIBLE
            hudPillFuel.text = "F ${fuelPct.roundToInt()}%"
            cockpitFuel.text = "F ${fuelPct.roundToInt()}%"
            val fuelColor = ContextCompat.getColor(
                this,
                if (fuelPct <= 12f) R.color.danger else R.color.accent_cyan
            )
            hudPillFuel.setTextColor(fuelColor)
            cockpitFuel.setTextColor(fuelColor)
        } else {
            // Sin lectura de combustible no se muestra el chip: un "F --"
            // permanente se lee como algo roto.
            hudPillFuel.visibility = View.GONE
            cockpitFuel.visibility = View.GONE
        }

        val heroColor = severityColor(human.headlineSeverity)
        val noLiveData = !state.demo && !state.hasLiveData
        val monitored = monitoredSystems(state)
        val systemsLabel =
            if (monitored == 1) "1 sistema monitoreado" else "$monitored sistemas monitoreados"
        vHumanHeadline.text = human.headlineStatus
        vHumanHeroSub.text = when {
            noLiveData && state.connected ->
                "El adaptador responde pero la ECU no entrega datos"
            noLiveData ->
                "Conecta el adaptador OBD para ver el motor"
            human.headlineSeverity == Severity.CRITICAL ->
                "Atención prioritaria antes de continuar"
            human.headlineSeverity == Severity.WARNING -> "$systemsLabel · revisa los avisos"
            human.headlineSeverity == Severity.INFO -> "$systemsLabel · revisa las sugerencias"
            else -> "$systemsLabel · sin avisos pendientes"
        }
        vHumanHeroIcon.setImageResource(
            when (human.headlineSeverity) {
                Severity.WARNING, Severity.CRITICAL -> R.drawable.ic_warning
                else -> R.drawable.ic_shield_check
            }
        )
        vHumanHeroIcon.setColorFilter(heroColor)
        vHumanStatusPill.text = when {
            noLiveData -> "SIN DATOS"
            human.headlineSeverity == Severity.GOOD -> "ÓPTIMO"
            human.headlineSeverity == Severity.INFO -> "SUGERENCIA"
            human.headlineSeverity == Severity.WARNING -> "ATENCIÓN"
            else -> "REVISAR"
        }
        vHumanStatusPill.setTextColor(heroColor)
        vHumanStatusPill.backgroundTintList =
            ColorStateList.valueOf(withAlpha(heroColor, 0.14f))
        cockpitHealthIcon.setImageResource(
            when (human.headlineSeverity) {
                Severity.WARNING, Severity.CRITICAL -> R.drawable.ic_warning
                else -> R.drawable.ic_shield_check
            }
        )
        cockpitHealthIcon.setColorFilter(heroColor)

        renderHumanInsight(
            vHumanThermalBar, vHumanThermalIcon, vHumanThermal, vHumanThermalSub,
            human.thermalMessage, R.drawable.ic_thermometer
        )
        renderHumanInsight(
            vHumanElectricalBar, vHumanElectricalIcon, vHumanElectrical, vHumanElectricalSub,
            human.electricalMessage, R.drawable.ic_battery
        )
        renderHumanInsight(
            vHumanFuelBar, vHumanFuelIcon, vHumanFuel, vHumanFuelSub,
            fuelInsight(state), R.drawable.ic_fuel
        )
        renderHumanInsight(
            vHumanMaintenanceBar, vHumanMaintenanceIcon, vHumanMaintenance, vHumanMaintenanceSub,
            human.maintenanceMessage, R.drawable.ic_oil
        )

        cockpitHealth.text = "${human.headlineStatus}\n${human.thermalMessage.title} · ${human.electricalMessage.title}"
    }

    /** Cantidad de sistemas con datos reales (mantenimiento siempre cuenta). */
    private fun monitoredSystems(state: ObdTelemetry): Int {
        var n = 1 // aceite/mantenimiento se basa en el odómetro local
        if (state.coolantC != null || state.intakeC != null) n++
        if (state.batteryV != null) n++
        if (state.fuelLevelPct != null || state.fuelPct != null) n++
        return n
    }

    private fun renderHumanInsight(
        bar: View,
        icon: ImageView,
        title: TextView,
        subtitle: TextView,
        insight: HumanInsight,
        iconRes: Int
    ) {
        val color = severityColor(insight.severity)
        bar.backgroundTintList = ColorStateList.valueOf(color)
        icon.setImageResource(iconRes)
        icon.setColorFilter(color)
        title.text = insight.title
        title.setTextColor(
            if (insight.severity == Severity.WARNING || insight.severity == Severity.CRITICAL) color
            else ContextCompat.getColor(this, R.color.text_primary)
        )
        subtitle.text = insight.subtitle
    }

    private fun severityColor(severity: Severity): Int = ContextCompat.getColor(
        this,
        when (severity) {
            Severity.GOOD -> R.color.green
            Severity.INFO -> R.color.accent_sky
            Severity.WARNING -> R.color.amber
            Severity.CRITICAL -> R.color.danger
        }
    )

    private fun withAlpha(color: Int, alpha: Float): Int = Color.argb(
        (alpha * 255f).toInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color)
    )

    private var lastHumanAlertMs = 0L
    private var lastHumanAlertId: String? = null
    private fun showHumanAlert(insight: HumanInsight) {
        val now = SystemClock.elapsedRealtime()
        if (insight.id == lastHumanAlertId && now - lastHumanAlertMs < 180_000L) return
        lastHumanAlertId = insight.id
        lastHumanAlertMs = now
        alertText.text = insight.title
        alertCapsule.visibility = android.view.View.VISIBLE
        alertCapsule.alpha = 0f
        alertCapsule.animate().alpha(1f).setDuration(180L).start()
        uiHandler.removeCallbacks(alertHide)
        uiHandler.postDelayed(alertHide, 6000L)
    }

    private fun showObdDialog() {
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val devices = try {
            manager?.adapter?.bondedDevices
                ?.sortedBy { it.name ?: "" }
                ?.toList()
                .orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        val labels = ArrayList<String>()
        devices.forEach { labels.add("${it.name ?: "Sin nombre"} · ${it.address}") }
        labels.add("Buscar automáticamente")
        labels.add("Modo demo (simulado)")
        labels.add("Ajustes Bluetooth")
        labels.add("PIN de emparejamiento OBD (${Prefs.obdPin(this)})")
        labels.add("Desconectar")

        AlertDialog.Builder(this)
            .setTitle("OBD-II · ELM327")
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < devices.size -> {
                        val device = devices[which]
                        Prefs.setObdDemo(this, false)
                        Prefs.setObdMac(this, device.address)
                        obdClient?.connect(device.address, demo = false)
                    }
                    which == devices.size -> {
                        Prefs.setObdDemo(this, false)
                        Prefs.setObdMac(this, null)
                        obdClient?.connect(null, demo = false)
                    }
                    which == devices.size + 1 -> {
                        Prefs.setObdDemo(this, true)
                        obdClient?.connect(null, demo = true)
                    }
                    which == devices.size + 2 -> {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    which == devices.size + 3 -> {
                        showObdPinDialog()
                    }
                    else -> {
                        Prefs.setObdDemo(this, false)
                        obdClient?.stop()
                        ObdRepository.publish(ObdTelemetry())
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showObdPinDialog() {
        val pins = arrayOf("1234", "0000", "6789", "1111")
        AlertDialog.Builder(this)
            .setTitle("PIN del adaptador OBD")
            .setItems(pins) { _, which ->
                val pin = pins[which]
                Prefs.setObdPin(this, pin)
                try {
                    Runtime.getRuntime()
                        .exec(arrayOf("setprop", "persist.service.bt.pincode", pin))
                        .waitFor()
                } catch (_: Exception) {
                }
                Toast.makeText(this, "PIN Bluetooth: $pin. Vuelve a emparejar el OBDII.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun setupClock() {
        uiHandler.removeCallbacks(clockRunnable)
        uiHandler.post(clockRunnable)
    }

    private fun setupDock() {
        addDockItem(R.drawable.ic_home, "COCKPIT") {
            applyFocus(Focus.COCKPIT)
        }
        addDockItem(R.drawable.ic_map, "MAPA") {
            applyFocus(Focus.NAV)
        }
        addDockItem(R.drawable.ic_media, "MEDIA") {
            applyFocus(Focus.MEDIA)
        }
        addDockItem(R.drawable.ic_radio, "RADIO") {
            // La radio integrada vive en el módulo Media; abrir la app nativa
            // sacaba del launcher y dejaba el dock sin control real.
            radioSource = true
            applyFocus(Focus.MEDIA)
            setupRadio()
            updateSourceToggle()
            setActiveDock(3)
        }
        addDockItem(R.drawable.ic_car, "VEHÍCULO") {
            applyFocus(Focus.VEHICLE)
        }
        addDockItem(R.drawable.ic_apps, "APPS") {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        addDockItem(R.drawable.ic_settings, "AJUSTES") {
            openQuickSettings()
        }
        applyFocus(Focus.COCKPIT, animate = false)
    }

    private fun addDockItem(iconRes: Int, label: String, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.text_muted)
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        }
        val text = TextView(this).apply {
            this.text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
            textSize = 9f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            fontFeatureSettings = "tnum"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) }
        }
        val indicator = View(this).apply {
            background = ContextCompat.getDrawable(
                this@MainActivity,
                R.drawable.bg_dock_indicator
            )
            visibility = View.INVISIBLE
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(2)).apply { topMargin = dp(4) }
            elevation = dp(6).toFloat()
        }
        item.addView(icon)
        item.addView(text)
        item.addView(indicator)
        item.setOnClickListener {
            action()
        }
        dock.addView(item)

        dockItems += item
        dockIcons += icon
        dockLabels += text
        dockIndicators += indicator
    }

    private fun setActiveDock(index: Int) {
        if (dockItems.isEmpty()) return
        activeDockIndex = index.coerceIn(0, dockItems.lastIndex)
        dockItems.forEachIndexed { i, item ->
            val active = i == activeDockIndex
            item.setBackgroundResource(0)
            item.alpha = if (active) 1f else 0.72f
            dockIcons[i].imageTintList = ContextCompat.getColorStateList(
                this,
                if (active) R.color.accent_sky else R.color.text_muted
            )
            dockLabels[i].setTextColor(
                ContextCompat.getColor(
                    this,
                    if (active) R.color.accent_sky else R.color.text_muted
                )
            )
            dockIndicators[i].visibility = if (active) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun setupMedia() {
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { mediaPrev() }
        btnPlay.setOnClickListener { togglePlayback() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { mediaNext() }

        mmSrcBt = findViewById(R.id.mmSrcBt)
        mmSrcRadio = findViewById(R.id.mmSrcRadio)
        mmSrcBt.setOnClickListener { setMediaSource(false) }
        mmSrcRadio.setOnClickListener { setMediaSource(true) }
        updateSourceToggle()

        radioFreq = findViewById(R.id.radioFreq)
        radioBand = findViewById(R.id.radioBand)
        radioRds = findViewById(R.id.radioRds)
        radioDial = findViewById(R.id.radioDial)
        radioNative = findViewById(R.id.radioNative)

        findViewById<ImageButton>(R.id.mmPrev).setOnClickListener { mediaPrev() }
        mmPlay.setOnClickListener { togglePlayback() }
        findViewById<ImageButton>(R.id.mmNext).setOnClickListener { mediaNext() }

        findViewById<View>(R.id.mediaCard).setOnClickListener {
            applyFocus(Focus.MEDIA)
        }
        findViewById<View>(R.id.mmOpenSource).setOnClickListener {
            val packageName = MediaRepository.current?.packageName
            if (!packageName.isNullOrBlank() &&
                packageName != "demo" &&
                openPackage(packageName)
            ) {
                return@setOnClickListener
            }
            openPackage("com.tw.media")
        }
    }

    private fun mediaPrev() {
        if (!MediaRepository.isDemo() &&
            MediaRepository.current?.isBluetooth != false &&
            hasPhoneLink()
        ) {
            sendPhoneMediaCommand("prev")
            return
        }
        if (twClient?.btPrev() == true) return
        if (!MediaRepository.prev()) {
            MediaKeys.send(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    private fun mediaNext() {
        if (!MediaRepository.isDemo() &&
            MediaRepository.current?.isBluetooth != false &&
            hasPhoneLink()
        ) {
            sendPhoneMediaCommand("next")
            return
        }
        if (twClient?.btNext() == true) return
        if (!MediaRepository.next()) {
            MediaKeys.send(this, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    private fun setMediaSource(radio: Boolean) {
        radioSource = radio
        mediaModule.visibility = if (radio) View.GONE else View.VISIBLE
        radioModule.visibility = if (radio) View.VISIBLE else View.GONE
        updateSourceToggle()
        if (radio) {
            setupRadio()
            twClient?.radioOpenChannel("FM")
        } else {
            twClient?.btOpenMusic()
        }
    }

    private fun updateSourceToggle() {
        mmSrcBt.setBackgroundResource(if (!radioSource) R.drawable.bg_dock_active else R.drawable.bg_pill)
        mmSrcBt.setTextColor(
            ContextCompat.getColor(this, if (!radioSource) R.color.accent_sky else R.color.text_secondary)
        )
        mmSrcRadio.setBackgroundResource(if (radioSource) R.drawable.bg_dock_active else R.drawable.bg_pill)
        mmSrcRadio.setTextColor(
            ContextCompat.getColor(this, if (radioSource) R.color.accent_sky else R.color.text_secondary)
        )
        if (::radioSrcBt.isInitialized) {
            radioSrcBt.setBackgroundResource(if (!radioSource) R.drawable.bg_dock_active else R.drawable.bg_pill)
            radioSrcBt.setTextColor(
                ContextCompat.getColor(this, if (!radioSource) R.color.accent_sky else R.color.text_secondary)
            )
            radioSrcRadio.setBackgroundResource(if (radioSource) R.drawable.bg_dock_active else R.drawable.bg_pill)
            radioSrcRadio.setTextColor(
                ContextCompat.getColor(this, if (radioSource) R.color.accent_sky else R.color.text_secondary)
            )
        }
    }

    private fun setupRadio() {
        radioSrcBt = findViewById(R.id.radioSrcBt)
        radioSrcRadio = findViewById(R.id.radioSrcRadio)
        radioSrcBt.setOnClickListener { setMediaSource(false) }
        radioSrcRadio.setOnClickListener { setMediaSource(true) }
        updateSourceToggle()

        for (i in 0 until 6) radioPresets[i] = Prefs.radioPreset(this, i)
        updateRadioUi()

        findViewById<TextView>(R.id.btnRadioPrev).setOnClickListener { tuneRadio(-100) }
        findViewById<TextView>(R.id.btnRadioNext).setOnClickListener { tuneRadio(100) }
        findViewById<TextView>(R.id.btnRadioSearchPrev).setOnClickListener { tuneRadio(-1000) }
        findViewById<TextView>(R.id.btnRadioSearchNext).setOnClickListener { tuneRadio(1000) }
        findViewById<TextView>(R.id.btnRadioBand).setOnClickListener { cycleBand() }
        radioNative.setOnClickListener {
            if (!openPackage("com.tw.radio")) openPackage("com.tw.bt")
        }

        val grid = findViewById<GridLayout>(R.id.radioPresets)
        grid.removeAllViews()
        for (i in 0 until 6) {
            val btn = TextView(this).apply {
                text = "P${i + 1}"
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(dp(4), dp(8), dp(4), dp(8))
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_pill)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(110)
                    height = dp(56)
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
            val slot = i
            btn.setOnClickListener {
                if (radioPresets[slot] > 0) {
                    radioFreqKhz = radioPresets[slot]
                    updateRadioUi()
                    twClient?.radioSetChannel(radioFreqKhz)
                }
            }
            btn.setOnLongClickListener {
                radioPresets[slot] = radioFreqKhz
                Prefs.setRadioPreset(this, slot, radioFreqKhz)
                btn.text = "P${slot + 1}\n${String.format("%.1f", radioFreqKhz / 1000f)}"
                true
            }
            grid.addView(btn)
        }
    }

    private fun tuneRadio(deltaKhz: Int) {
        radioFreqKhz = (radioFreqKhz + deltaKhz).coerceIn(87500, 108000)
        updateRadioUi()
        twClient?.radioSetChannel(radioFreqKhz)
        if (deltaKhz < 0) twClient?.radioPre() else twClient?.radioNext()
    }

    private fun cycleBand() {
        radioBandIndex = (radioBandIndex + 1) % radioBands.size
        updateRadioUi()
        twClient?.radioBand(radioBandIndex)
        twClient?.radioBandChange()
    }

    private fun updateRadioUi() {
        radioFreq.text = String.format("%.1f", radioFreqKhz / 1000f)
        radioBand.text = radioBands[radioBandIndex]
        radioRds.text = "RDS —"
        radioDial.progress = ((radioFreqKhz - 87_500) / 100).coerceIn(0, 205)
        val grid = findViewById<GridLayout>(R.id.radioPresets)
        for (i in 0 until minOf(6, grid.childCount)) {
            val btn = grid.getChildAt(i) as? TextView ?: continue
            val freq = radioPresets[i]
            btn.text = if (freq > 0) "P${i + 1}\n${String.format("%.1f", freq / 1000f)}" else "P${i + 1}"
            btn.setTextColor(
                ContextCompat.getColor(this, if (freq > 0) R.color.accent_sky else R.color.text_secondary)
            )
        }
    }

    private fun syncMediaMode() {
        val demo = Prefs.demoMedia(this) || intent.getBooleanExtra("demo_media", false)
        if (demo) {
            if (mediaListenerRegistered) {
                MediaRepository.removeListener(mediaListener)
                mediaListenerRegistered = false
            }
            if (!MediaRepository.isDemo()) {
                MediaRepository.setDemo(true)
            }
            updateMedia(MediaRepository.current)
        } else {
            if (MediaRepository.isDemo()) {
                MediaRepository.setDemo(false)
            }
            if (!mediaListenerRegistered) {
                MediaRepository.addListener(mediaListener)
                mediaListenerRegistered = true
            }
        }
    }

    private fun updateMedia(nowPlaying: NowPlaying?) {
        if (nowPlaying == null) {
            mediaTitle.text = getString(R.string.media_title)
            mediaSubtitle.text = "Bluetooth / Radio FM"
            renderArt(albumArt, null, 14)
            btnPlay.setImageResource(R.drawable.ic_play)
            mediaProgress.visibility = View.GONE
            mediaTime.visibility = View.GONE

            miniTitle.text = "Sin reproducción"
            renderArt(miniArt, null, 9)
            miniPlay.setImageResource(R.drawable.ic_play)

            mmTitle.text = "Sin reproducción"
            mmArtist.text = "Bluetooth · Radio FM"
            mmAlbum.text = ""
            renderArt(mmArt, null, 70)
            mmPlay.setImageResource(R.drawable.ic_play)
            mmProgress.visibility = View.GONE
            mmTime.visibility = View.GONE
            mmSource.text = "—"
            return
        }

        val title = nowPlaying.title.ifBlank { getString(R.string.media_title) }
        mediaTitle.text = title
        val source = when {
            nowPlaying.isBluetooth -> "Bluetooth"
            nowPlaying.album.isNotBlank() -> nowPlaying.album
            else -> "Media"
        }
        val subtitle = listOf(nowPlaying.artist, source)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        mediaSubtitle.text = subtitle

        renderArt(albumArt, nowPlaying.artwork, 14)
        renderArt(miniArt, nowPlaying.artwork, 9)
        renderArt(mmArt, nowPlaying.artwork, 70)

        miniTitle.text = title
        mmTitle.text = title
        mmArtist.text = nowPlaying.artist.ifBlank { "Reproducción activa" }
        mmAlbum.text = nowPlaying.album
        mmSource.text = if (nowPlaying.isBluetooth) "BT · A2DP" else "MEDIA"

        val playRes = if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnPlay.setImageResource(playRes)
        miniPlay.setImageResource(playRes)
        mmPlay.setImageResource(playRes)

        renderProgress(nowPlaying.positionMs, nowPlaying.durationMs)
    }

    private fun renderArt(view: ImageView, art: android.graphics.Bitmap?, placeholderPaddingDp: Int) {
        if (art != null) {
            view.imageTintList = null
            view.setImageBitmap(art)
            view.setPadding(0, 0, 0, 0)
            view.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            view.imageTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_dim))
            view.setImageResource(R.drawable.ic_media)
            val pad = dp(placeholderPaddingDp)
            view.setPadding(pad, pad, pad, pad)
            view.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun renderProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) {
            mediaProgress.visibility = View.GONE
            mediaTime.visibility = View.GONE
            mmProgress.visibility = View.GONE
            mmTime.visibility = View.GONE
            return
        }
        val progress = ((positionMs * 1000L) / durationMs).toInt().coerceIn(0, 1000)
        val label = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"

        mediaProgress.visibility = View.VISIBLE
        mediaProgress.progress = progress
        mediaTime.visibility = View.VISIBLE
        mediaTime.text = label

        mmProgress.visibility = View.VISIBLE
        mmProgress.progress = progress
        mmTime.visibility = View.VISIBLE
        mmTime.text = label
    }

    private fun formatDuration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        return String.format(
            Locale.getDefault(),
            "%d:%02d",
            total / 60L,
            total % 60L
        )
    }

    private fun renderSignals(signals: CarSignals.Signals) {
        val mph = Prefs.useMph(this)
        val value = if (mph) signals.speedKmh * 0.621371f else signals.speedKmh
        val speedLabel = value.roundToInt().toString()
        val unitLabel = if (mph) "mph" else "km/h"
        val gear = when {
            signals.reverse -> "R"
            value > 0.5f -> "D"
            else -> "P"
        }

        speedText.text = speedLabel
        unitText.text = unitLabel
        speedSource.text = signals.source
        gearText.text = gear

        miniSpeed.text = speedLabel
        miniGear.text = gear
        hudPillSpeed.text = speedLabel
        hudPillGear.text = gear

        vSpeed.text = "$speedLabel $unitLabel"
        vGear.text = gear
        vGearText.text = when (gear) {
            "R" -> "Reversa"
            "D" -> "Drive"
            else -> "Park"
        }
        vSource.text = signals.source
        vBusChip.text = signals.source
        vBusChip.setTextColor(
            ContextCompat.getColor(
                this,
                if (signals.source == "CAN" || signals.source == "OBD") R.color.green else R.color.accent_sky
            )
        )
        vReverse.text = if (signals.reverse) "Sí" else "No"
        vReverse.setTextColor(
            ContextCompat.getColor(
                this,
                if (signals.reverse) R.color.amber else R.color.text_secondary
            )
        )
        vReverseIcon.imageTintList = ContextCompat.getColorStateList(
            this,
            if (signals.reverse) R.color.amber else R.color.text_muted
        )
        if (::canStatus.isInitialized) {
            canStatus.text = signals.source
            canStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (signals.source == "CAN" || signals.source == "OBD") R.color.green else R.color.amber
                )
            )
        }

        if (signals.speedKmh >= 120f) {
            val now = System.currentTimeMillis()
            if (now - lastSpeedAlert > 60_000L) {
                lastSpeedAlert = now
                showAlert("Velocidad alta · $speedLabel $unitLabel")
            }
        }

        if (signals.reverse != reverseActive) {
            onReverseChanged(signals.reverse)
        }
    }

    private fun showAlert(message: String) {
        alertText.text = message
        alertCapsule.visibility = View.VISIBLE
        alertCapsule.alpha = 0f
        alertCapsule.animate().alpha(1f).setDuration(180L).start()
        uiHandler.removeCallbacks(alertHide)
        uiHandler.postDelayed(alertHide, 4200L)
    }

    private fun onReverseChanged(active: Boolean) {
        if (active == reverseActive) return
        reverseActive = active
        if (active) {
            if (!reverseCameraLaunched) {
                reverseCameraLaunched = true
                openReverseCamera()
            }
        } else {
            reverseCameraLaunched = false
        }
    }

    private fun renderBluetooth(status: BluetoothStatus?) {
        lastBluetooth = status
        updateBluetoothText()
    }

    private fun updateBluetoothText() {
        val call = activeCallText ?: mcuPhoneText
        if (!call.isNullOrBlank()) {
            btStatus.text = call
            btStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
            btBatteryGroup.visibility = View.GONE
            return
        }
        val status = lastBluetooth
        if (status == null) {
            btStatus.text = "Sin conectar"
            btStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            btBatteryGroup.visibility = View.GONE
            return
        }
        btStatus.text = status.name
        btStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (status.connected) R.color.text_secondary else R.color.text_muted
            )
        )
        if (status.battery in 0..100) {
            btBatteryGroup.visibility = View.VISIBLE
            btBatteryBar.progress = status.battery
            btBattery.text = "${status.battery}%"
        } else {
            btBatteryGroup.visibility = View.GONE
        }
    }

    private fun onPhoneStatus(status: Int) {
        mcuPhoneText = when (status) {
            0 -> null
            1 -> "Llamada activa"
            2 -> "Teléfono conectado"
            3 -> "Llamada entrante"
            4 -> "Llamada saliente"
            else -> null
        }
        updateBluetoothText()
    }

    private fun onCallStatus(status: Int, number: String?, name: String?) {
        val who = name?.takeIf { it.isNotBlank() }
            ?: number?.takeIf { it.isNotBlank() }
        activeCallText = when {
            who != null -> "Llamada · $who"
            status == 0 -> null
            else -> "Llamada"
        }
        if (status == 3 || status == 1) {
            showCallBanner(who ?: "Llamada entrante", number)
        } else if (status == 0) {
            hideCallBanner()
        }
        updateBluetoothText()
    }

    private fun onBtConnected(status: Int) {
        if (status == 0) {
            activeCallText = null
            mcuPhoneText = null
            updateBluetoothText()
        }
    }

    private fun showBluetoothDetails() {
        val status = lastBluetooth
        val battery = when {
            status == null -> "—"
            status.battery in 0..100 -> "${status.battery}%"
            else -> "No disponible (el teléfono no la publica)"
        }
        val message = buildString {
            appendLine("Nombre: ${status?.name ?: "—"}")
            appendLine("Dirección: ${status?.address ?: "—"}")
            appendLine("Conectado: ${if (status?.connected == true) "Sí" else "No"}")
            appendLine("A2DP (audio): ${if (status?.a2dp == true) "Sí" else "No"}")
            appendLine("HFP (manos libres): ${if (status?.hfp == true) "Sí" else "No"}")
            appendLine("Batería: $battery")
            appendLine("Estado MCU: ${mcuPhoneText ?: "—"}")
            appendLine("Llamada: ${activeCallText ?: "—"}")
            append("La batería solo aparece si el teléfono la publica por Bluetooth.")
        }
        AlertDialog.Builder(this)
            .setTitle("Bluetooth")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun applyPrefs() {
        val visible = if (Prefs.showWeather(this)) View.VISIBLE else View.GONE
        weatherTop.visibility = visible
        weatherIcon.visibility = visible
    }

    private fun renderWeather(state: WeatherRepository.State) {
        val temp = state.tempC
        val tempText = if (temp != null) {
            String.format(Locale.getDefault(), "%.0f\u00B0", temp)
        } else {
            "--\u00B0"
        }
        val iconRes = when (state.code) {
            0 -> R.drawable.ic_weather_sun
            1, 2 -> R.drawable.ic_weather_cloud
            3 -> R.drawable.ic_weather_cloud
            45, 48 -> R.drawable.ic_weather_fog
            in 51..67 -> R.drawable.ic_weather_rain
            in 71..77, 85, 86 -> R.drawable.ic_weather_snow
            in 80..82 -> R.drawable.ic_weather_rain
            in 95..99 -> R.drawable.ic_weather_storm
            else -> R.drawable.ic_weather_cloud
        }
        weatherIcon.setImageResource(iconRes)
        miniWeatherIcon.setImageResource(iconRes)
        weatherTop.text = tempText
        miniWeather.text = tempText
        weatherTop.contentDescription = state.text.ifBlank { "Clima" }
        miniWeather.contentDescription = state.text.ifBlank { "Clima" }
    }

    private fun toggleClimate(view: TextView) {
        view.isSelected = !view.isSelected
        view.setBackgroundResource(
            if (view.isSelected) R.drawable.bg_dock_active else R.drawable.bg_pill
        )
        view.setTextColor(
            ContextCompat.getColor(
                this,
                if (view.isSelected) R.color.accent_sky else R.color.text_secondary
            )
        )
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }

    private fun enableLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (locationOverlay == null) {
            locationOverlay = MapHelper.addLocation(this, map)
        } else {
            locationOverlay?.enableMyLocation()
        }
        // We draw our own road-snapped position marker; the overlay icon would
        // show the raw GPS dot off-road. Disable its display (and its own GPS
        // requests: RouteNavigator + this activity listen themselves).
        try {
            locationOverlay?.disableMyLocation()
            locationOverlay?.disableFollowLocation()
        } catch (_: Exception) {
        }
        if (positionMarker == null) {
            try {
                val m = org.osmdroid.views.overlay.Marker(map)
                m.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                // Flat marker: rotates with the map, so the arrow points up in
                // heading-up navigation and toward travel in north-up mode.
                m.setFlat(true)
                try {
                    m.icon = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav)
                } catch (_: Exception) {
                }
                m.title = ""
                map.overlays.add(m)
                positionMarker = m
            } catch (_: Exception) {
            }
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider != null) {
            locationManager.requestLocationUpdates(provider, 1000L, 0f, this)
            locationManager.getLastKnownLocation(provider)?.let { onLocationChanged(it) }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableLocation()
            } else {
                Toast.makeText(this, "Sin ubicación: mapa centrado en Morelia", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        if (location.hasSpeed()) {
            carSignals?.setGpsSpeed(speedKmh)
        }
        // Marcador sobre la carretera + seguimiento del vehículo.
        // Con mapa remoto el MapView está oculto y no hace falta.
        if (!RemoteMapBridge.active) {
            try {
                val acc = if (location.hasAccuracy()) location.accuracy else 999f
                val (slat, slon, _) = com.chronosbvrx.ibizadash.nav.RoadSnapper.snapLocal(
                    location.latitude, location.longitude, speedKmh, acc
                )
                positionMarker?.position = GeoPoint(slat, slon)
                if (location.hasBearing() && speedKmh > 3f) {
                    positionMarker?.rotation = location.bearing
                }
                val now = android.os.SystemClock.elapsedRealtime()
                // Si el usuario movió el mapa, vuelve a seguir solo.
                if (mapMode == MapMode.FREE && now - lastUserMapTouchMs > 10_000L) {
                    mapMode = MapMode.FOLLOWING
                    updateRecenterVisibility()
                }
                if (mapMode == MapMode.FOLLOWING) {
                    centerOnPosition(slat, slon, speedKmh, location, now)
                }
                map.invalidate()
            } catch (_: Exception) {
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun openPackage(packageName: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            openPackage("com.tw.bt")
        }
    }

    private fun openReverseCamera() {
        val candidates = listOf(
            ComponentName("com.tw.reverse", "com.tw.reverse.ReverseActivity"),
            ComponentName("com.tw.reverse", "com.tw.reverse.LRReverseActivity")
        )
        for (component in candidates) {
            try {
                startActivity(
                    Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (_: Exception) {
            }
        }
        reverseCameraLaunched = false
        Toast.makeText(this, "No se pudo abrir la cámara de reversa", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (!RemoteMapBridge.active) map.onResume()
        goImmersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyPrefs()
        setupClock()
        setActiveDock(focus.dockIndex)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        }
        syncMediaMode()
    }

    override fun onPause() {
        super.onPause()
        flushOdometer()
        lastOdometerMs = 0L
        map.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(this)
        }
        locationOverlay?.disableMyLocation()
        if (mediaListenerRegistered) {
            MediaRepository.removeListener(mediaListener)
            mediaListenerRegistered = false
        }
    }

    override fun onDestroy() {
        flushOdometer()
        uiHandler.removeCallbacks(clockRunnable)
        uiHandler.removeCallbacks(alertHide)
        carSignals?.stop()
        twClient?.stop()
        avrcpController?.stop()
        bluetoothClient?.stop()
        ObdRepository.removeListener(obdListener)
        obdClient?.stop()
        try { unregisterReceiver(lightReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(sourceReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(phoneStateReceiver) } catch (_: Exception) { }
        NavRepository.removeListener(navListener)
        NavStateRepository.removeListener(navStateListener)
        NavRouteRepository.removeListener(routeRepoListener)
        com.chronosbvrx.ibizadash.nav.ModulesBridge.removeListener(modulesListener)
        if (WeatherRepository.onUpdate === weatherUpdate) WeatherRepository.onUpdate = null
        com.chronosbvrx.ibizadash.ota.OtaReceiver.onReady = null
        MapTileBridge.removeListener(mapTileListener)
        RemoteMapBridge.detach()
        PhoneNotifyBridge.detach()
        PhoneNavBridge.reset()
        uiHandler.removeCallbacks(linkStatusRunnable)
        try { unregisterReceiver(debugNavReceiver) } catch (_: Exception) { }
        try { unregisterReceiver(debugCallReceiver) } catch (_: Exception) { }
        if (mediaListenerRegistered) {
            MediaRepository.removeListener(mediaListener)
            mediaListenerRegistered = false
        }
        map.onDetach()
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (::quickSheet.isInitialized && quickSheet.visibility == View.VISIBLE) {
            closeQuickSettings()
            return
        }
        if (focus != Focus.COCKPIT) {
            applyFocus(Focus.COCKPIT)
            return
        }
        super.onBackPressed()
    }

    // ---------------------------------------------------------------------
    // Continuous canvas: one map, one dock, animated split fractions.
    // ---------------------------------------------------------------------

    private fun applyFocus(target: Focus, animate: Boolean = true) {
        val shown = when (target) {
            Focus.COCKPIT -> cockpitPanel
            Focus.NAV -> navOverlay
            Focus.MEDIA -> mediaModule
            Focus.VEHICLE -> vehicleModule
        }
        val changed = target != focus
        focus = target

        cockpitPanel.visibility = if (shown === cockpitPanel) View.VISIBLE else View.GONE
        // En NAV el mapa ocupa todo el ancho: no hay panel lateral.
        miniHud.visibility = View.GONE
        mediaModule.visibility = if (shown === mediaModule && !radioSource) View.VISIBLE else View.GONE
        radioModule.visibility = if (shown === mediaModule && radioSource) View.VISIBLE else View.GONE
        vehicleModule.visibility = if (shown === vehicleModule) View.VISIBLE else View.GONE
        navOverlay.visibility = if (target == Focus.NAV) View.VISIBLE else View.GONE

        // La tarjeta de maniobra ocupa la esquina superior; el reloj se oculta.
        clockBlock.visibility = if (target == Focus.NAV) View.GONE else View.VISIBLE

        navPill.visibility =
            if (target == Focus.MEDIA || target == Focus.VEHICLE) View.VISIBLE else View.GONE
        hudPill.visibility =
            if (target != Focus.COCKPIT) View.VISIBLE else View.GONE

        setActiveDock(target.dockIndex)

        for (view in arrayOf(cockpitPanel, miniHud, mediaModule, vehicleModule, navOverlay)) {
            view.animate().cancel()
            view.alpha = 1f
        }
        // Single measure/layout pass: no per-frame LayoutParams mutation.
        setSplit(target.fraction)

        if (target == Focus.NAV) {
            // Navegación: se vuelve a seguir al coche y se deja de recibir el
            // vídeo del teléfono (el mapa local se ve mejor y gasta menos).
            mapMode = MapMode.FOLLOWING
            lastUserMapTouchMs = 0L
            if (RemoteMapBridge.active) RemoteMapBridge.stop()
            renderNavOverlay()
            lastLocation?.let {
                val spd = if (it.hasSpeed()) it.speed * 3.6f else 0f
                try {
                    centerOnPosition(it.latitude, it.longitude, spd, it, SystemClock.elapsedRealtime())
                } catch (_: Exception) {
                }
            }
        } else {
            resetMapBearing()
        }
        updateRecenterVisibility()

        if (changed && animate && target != Focus.NAV) {
            shown.alpha = 0f
            shown.animate().alpha(1f).setDuration(180L).start()
        } else {
            shown.alpha = 1f
        }
    }

    private fun setSplit(fraction: Float) {
        splitFraction = fraction.coerceIn(0.2f, 1f)
        val leftParams = leftPane.layoutParams as LinearLayout.LayoutParams
        leftParams.weight = splitFraction
        leftPane.layoutParams = leftParams

        val rightParams = rightPane.layoutParams as LinearLayout.LayoutParams
        rightParams.weight = 1f - splitFraction
        rightPane.layoutParams = rightParams
    }

    // ---------------------------------------------------------------------
    // Navegación: seguimiento del vehículo y tarjeta de maniobra
    // ---------------------------------------------------------------------

    /** El usuario tocó el mapa: deja de seguir hasta que recentre o pasen 10 s. */
    private fun markMapFree() {
        lastUserMapTouchMs = SystemClock.elapsedRealtime()
        if (mapMode != MapMode.FREE) {
            mapMode = MapMode.FREE
            updateRecenterVisibility()
        }
    }

    private fun updateRecenterVisibility() {
        if (!::btnRecenter.isInitialized) return
        val show = mapMode == MapMode.FREE && !RemoteMapBridge.active
        btnRecenter.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Centra el mapa en el coche. En navegación se orienta hacia el rumbo
     * (heading-up) con una animación corta; en el resto, norte arriba y a un
     * ritmo más relajado para no gastar CPU.
     */
    private fun centerOnPosition(
        lat: Double,
        lon: Double,
        speedKmh: Float,
        location: Location,
        now: Long
    ) {
        val target = GeoPoint(lat, lon)
        if (focus == Focus.NAV) {
            val bearing = if (speedKmh > 12f && location.hasBearing()) location.bearing else 0f
            animateMapBearing(bearing)
            // Un solo redibujado por fix: animar el paneo en cada GPS (1 Hz)
            // mantiene el mapa redibujándose casi sin pausa y en el Cortex-A7
            // eso se nota. Con setCenter el movimiento sigue al coche igual.
            try {
                map.controller.setCenter(target)
            } catch (_: Exception) {
            }
        } else {
            resetMapBearing()
            if (now - lastFollowCenterMs > 1500L) {
                lastFollowCenterMs = now
                try {
                    map.controller.setCenter(target)
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Gira el mapa suavemente hacia el rumbo, por el camino más corto. */
    private fun animateMapBearing(targetBearing: Float) {
        val from = currentBearingDeg
        var delta = ((targetBearing - from + 540f) % 360f) - 180f
        // Umbral alto: corregir 3° cada segundo obliga a redibujar el mapa
        // entero. Solo se gira cuando el rumbo cambia de verdad.
        if (Math.abs(delta) < 10f) return
        bearingAnimator?.cancel()
        val to = from + delta
        currentBearingDeg = to
        bearingAnimator = android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = 700L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                try {
                    map.mapOrientation = -((it.animatedValue as Float))
                } catch (_: Exception) {
                }
            }
            start()
        }
    }

    private fun resetMapBearing() {
        if (currentBearingDeg == 0f && map.mapOrientation == 0f) return
        bearingAnimator?.cancel()
        bearingAnimator = null
        currentBearingDeg = 0f
        try {
            map.mapOrientation = 0f
        } catch (_: Exception) {
        }
    }

    /** Pinta la tarjeta flotante de navegación con el estado actual. */
    private fun renderNavOverlay() {
        if (!::navCard.isInitialized) return
        val st = lastNavState
        val route = NavRouteRepository.current
        val hasRoute = st != null

        navCardProgress.visibility = if (hasRoute) View.VISIBLE else View.GONE
        navFinish.visibility = if (hasRoute) View.VISIBLE else View.GONE

        // Aviso de tráfico: solo si aporta (más de un minuto de retraso).
        val delayS = route?.trafficDelaySeconds ?: 0
        if (hasRoute && delayS >= 60) {
            navCardTraffic.text = "TRÁFICO +${delayS / 60} MIN"
            navCardTraffic.visibility = View.VISIBLE
        } else {
            navCardTraffic.visibility = View.GONE
        }

        if (!hasRoute) {
            navCardIcon.setImageResource(R.drawable.ic_mav_straight)
            navCardDist.text = "—"
            navCardAction.text = "Sin destino activo"
            navCardThen.text = "Planifica una ruta desde IbizaCompanion"
            navCardEta.visibility = View.GONE
            navCardRemaining.text = ""
            navCardDest.text = ""
            navCardProgress.progress = 0
            return
        }

        val s = st!!
        navCardIcon.setImageResource(s.icon.resId)
        navCardDist.text = s.distText.ifBlank { "—" }
        navCardAction.text = when {
            s.arrived -> "Has llegado a tu destino"
            s.offRoute -> "Fuera de ruta · recalculando"
            s.actionText.isBlank() -> "Continúa en la ruta"
            else -> s.actionText
        }
        val then = s.thenText?.takeIf { it.isNotBlank() }
        navCardThen.text = then.orEmpty()
        navCardThen.visibility = if (then == null) View.GONE else View.VISIBLE
        navCardEta.text = s.etaText
        navCardEta.visibility = if (s.etaText.isNotBlank()) View.VISIBLE else View.GONE
        val remaining = remainingDistanceOnly(s.remainingText)
        navCardRemaining.text = remaining
        // Sin maniobra próxima el número grande ya es el restante: no repetirlo.
        navCardRemaining.visibility =
            if (remaining.isBlank() || remaining == s.distText) View.GONE else View.VISIBLE
        navCardDest.text = s.destName
        navCardProgress.progress = (s.progress.coerceIn(0f, 1f) * 1000f).toInt()
    }

    /** "quedan 1.2 km · 8 min" → "1.2 km" (la ETA ya se muestra aparte). */
    private fun remainingDistanceOnly(text: String): String {
        val clean = text.removePrefix("quedan ").trim()
        val head = clean.substringBefore(" · ").trim()
        return head.ifBlank { clean }
    }

    /** Pide al teléfono que deje o retome la proyección del mapa. */
    private fun requestMapStream(on: Boolean) {
        try {
            NavLink.sendAsync(org.json.JSONObject().put("type", "MAP_STREAM").put("on", on))
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------------
    // Quick settings drawer
    // ---------------------------------------------------------------------

    /** Shows offline map pack presence (Mich + neighbors) in quick settings. */
    private fun refreshMapsStatus() {
        Thread {
            var text = "sin pack"
            var colorRes = R.color.amber
            try {
                val dir = org.osmdroid.config.Configuration.getInstance().osmdroidBasePath
                val files = dir?.listFiles { f ->
                    f.isFile && (f.name.endsWith(".mbtiles") || f.name.endsWith(".sqlite"))
                }.orEmpty()
                if (files.isNotEmpty()) {
                    val mb = files.sumOf { it.length() } / 1048576L
                    text = "${files.size} packs · ${mb}MB"
                    colorRes = R.color.green
                }
            } catch (_: Exception) {
            }
            val t = text
            val c = colorRes
            runOnUiThread {
                try {
                    mapsStatus.text = t
                    mapsStatus.setTextColor(ContextCompat.getColor(this, c))
                } catch (_: Exception) {
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    /** Estado del enlace con el teléfono (Wi-Fi Direct / LAN / BT). */
    private val linkStatusRunnable = object : Runnable {
        override fun run() {
            try {
                if (quickSheet.visibility == View.VISIBLE) {
                    refreshLinkStatus()
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun refreshLinkStatus() {
        if (!::wifiLinkStatus.isInitialized) return
        try {
            // Sufijo corto del origen del mapa: lo interesante es saber si el
            // mapa que se ve viene del teléfono o es el nativo.
            val mapSuffix =
                if (RemoteMapBridge.active) " · mapa TEL" else ""
            wifiLinkStatus.text = WifiNavBridgeServer.statusText() + mapSuffix
            val active = WifiNavBridgeServer.hasClients()
            wifiLinkStatus.setTextColor(
                ContextCompat.getColor(this, if (active) R.color.green else R.color.accent_sky)
            )
        } catch (_: Exception) {
        }
        refreshHomeStatus()
        refreshOtaStatus()
        refreshVoiceStatus()
        uiHandler.removeCallbacks(linkStatusRunnable)
        uiHandler.postDelayed(linkStatusRunnable, 3000L)
    }

    /** ¿Qué app es el launcher por defecto del estéreo? */
    private fun refreshHomeStatus() {
        try {
            if (!::homeStatus.isInitialized) return
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolve = packageManager.resolveActivity(homeIntent, 0)
            val pkg = resolve?.activityInfo?.packageName
            if (pkg == packageName) {
                homeStatus.text = "IbizaDash ✓"
                homeStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
            } else {
                homeStatus.text = "No es IbizaDash"
                homeStatus.setTextColor(ContextCompat.getColor(this, R.color.amber))
            }
        } catch (_: Exception) {
        }
    }

    /** Estado de la actualización OTA recibida desde el teléfono. */
    private var otaStatusCheckedMs = 0L
    private var otaStatusInFlight = false

    @Volatile
    private var otaStatusPending: com.chronosbvrx.ibizadash.ota.OtaReceiver.PendingUpdate? = null

    /**
     * El SHA-256 del APK pendiente se calculaba en el hilo principal cada 3 s
     * mientras el panel estaba abierto. Ahora se verifica en segundo plano y
     * el resultado se reutiliza 30 s.
     */
    private fun refreshOtaStatus() {
        if (!::otaStatus.isInitialized) return
        if (otaStatusInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (otaStatusCheckedMs != 0L && now - otaStatusCheckedMs < OTA_STATUS_TTL_MS) return
        otaStatusInFlight = true
        Thread {
            val pending = try {
                com.chronosbvrx.ibizadash.ota.OtaReceiver.pendingUpdate(this)
            } catch (_: Exception) {
                null
            }
            val current = try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
            } catch (_: Exception) {
                "?"
            }
            otaStatusPending = pending
            otaStatusCheckedMs = SystemClock.elapsedRealtime()
            otaStatusInFlight = false
            runOnUiThread { applyOtaStatus(pending, current) }
        }.apply {
            name = "OtaStatus"
            isDaemon = true
        }.start()
    }

    private fun applyOtaStatus(
        pending: com.chronosbvrx.ibizadash.ota.OtaReceiver.PendingUpdate?,
        current: String
    ) {
        try {
            if (pending != null) {
                otaStatus.text = "v${pending.versionName} lista · toca para instalar"
                otaStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan))
            } else {
                otaStatus.text = "v$current instalada"
                otaStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
        } catch (_: Exception) {
        }
    }

    private fun setupQuickSettings() {
        quickSheet = findViewById(R.id.quickSheet)
        sheetScrim = findViewById(R.id.sheetScrim)
        sheetPanel = findViewById(R.id.sheetPanel)
        brightnessSeek = findViewById(R.id.brightness)
        brightnessValue = findViewById(R.id.brightnessValue)
        swNight = findViewById(R.id.swNight)
        swMute = findViewById(R.id.swMute)
        swVoice = findViewById(R.id.swVoice)
        navVoiceStatus = findViewById(R.id.navVoiceStatus)
        modulesStatus = findViewById(R.id.modulesStatus)
        canStatus = findViewById(R.id.canStatus)
        mapsStatus = findViewById(R.id.mapsStatus)
        wifiLinkStatus = findViewById(R.id.wifiLinkStatus)
        homeStatus = findViewById(R.id.homeStatus)
        otaStatus = findViewById(R.id.otaStatus)
        refreshMapsStatus()

        brightnessSeek.progress = Prefs.brightnessPct(this)
        brightnessValue.text = "${Prefs.brightnessPct(this)}%"
        applyBrightness(brightnessSeek.progress)

        brightnessSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessValue.text = "$progress%"
                if (fromUser) applyBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Se guarda al soltar para conservar el brillo entre arranques.
                seekBar?.let { Prefs.setBrightnessPct(this@MainActivity, it.progress) }
            }
        })

        // Modo noche y silencio reflejan (y conservan) el estado real.
        val night = Prefs.nightMode(this)
        swNight.isChecked = night
        nightDim.visibility = if (night) View.VISIBLE else View.GONE
        swNight.setOnCheckedChangeListener { _, checked ->
            nightDim.visibility = if (checked) View.VISIBLE else View.GONE
            Prefs.setNightMode(this, checked)
        }
        val audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        swMute.isChecked = try {
            audio?.isStreamMute(AudioManager.STREAM_MUSIC) == true
        } catch (_: Exception) {
            false
        }
        swMute.setOnCheckedChangeListener { _, checked -> setMuted(checked) }

        // Voz de navegación
        swVoice.isChecked = Prefs.voiceOn(this)
        swVoice.setOnCheckedChangeListener { _, checked ->
            Prefs.setVoiceOn(this, checked)
            if (!checked) com.chronosbvrx.ibizadash.nav.VoicePlayer.stop(this)
            refreshVoiceStatus()
        }
        findViewById<View>(R.id.navVoiceClear).setOnClickListener {
            val removed = com.chronosbvrx.ibizadash.nav.VoiceStore.clear(this)
            com.chronosbvrx.ibizadash.nav.VoicePlayer.stop(this)
            Toast.makeText(
                this,
                if (removed > 0) "Voces liberadas: $removed clips" else "No había voces guardadas",
                Toast.LENGTH_SHORT
            ).show()
            refreshVoiceStatus()
        }
        refreshVoiceStatus()

        // Módulos descargables (Fase D): el teléfono los envía y aquí se ven.
        findViewById<View>(R.id.rowModules).setOnClickListener { showModulesDialog() }
        com.chronosbvrx.ibizadash.nav.ModulesBridge.addListener(modulesListener)
        refreshModulesStatus()

        sheetScrim.setOnClickListener { closeQuickSettings() }
        findViewById<View>(R.id.sheetClose).setOnClickListener { closeQuickSettings() }
        findViewById<View>(R.id.openSettings).setOnClickListener {
            closeQuickSettings()
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.rowEq).setOnClickListener {
            closeQuickSettings()
            if (!openPackage("com.tw.eq")) {
                Toast.makeText(this, "EQ / DSP no disponible en este estéreo", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.rowWifi).setOnClickListener {
            closeQuickSettings()
            try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "WiFi no disponible", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.rowHome).setOnClickListener {
            // En API 27 no hay RoleManager: se abre el selector de app de
            // inicio del sistema y el usuario elige IbizaDash "Siempre".
            closeQuickSettings()
            try {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "No se pudo abrir el selector de inicio", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.rowOta).setOnClickListener {
            // Se usa el pendiente ya verificado por refreshOtaStatus para no
            // calcular el SHA-256 otra vez en el hilo principal.
            val pending = otaStatusPending
                ?: com.chronosbvrx.ibizadash.ota.OtaReceiver.pendingUpdate(this)
            if (pending == null) {
                Toast.makeText(this, "Sin actualizaciones pendientes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            closeQuickSettings()
            com.chronosbvrx.ibizadash.ota.OtaInstaller.install(this, pending)
        }
    }

    private fun refreshVoiceStatus() {
        try {
            if (!::navVoiceStatus.isInitialized) return
            val count = com.chronosbvrx.ibizadash.nav.VoiceStore.count(this)
            val kb = com.chronosbvrx.ibizadash.nav.VoiceStore.totalBytes(this) / 1024
            navVoiceStatus.text = if (count == 0) {
                "Voces: aún sin descargar"
            } else {
                "Voces: $count clips · ${kb} KB"
            }
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------------
    // Módulos descargables (Fase D)
    // ---------------------------------------------------------------------

    private val modulesListener: () -> Unit = {
        runOnUiThread { refreshModulesStatus() }
    }

    private fun refreshModulesStatus() {
        try {
            if (!::modulesStatus.isInitialized) return
            val list = com.chronosbvrx.ibizadash.modules.ModuleManager.installed(this)
            modulesStatus.text = if (list.isEmpty()) {
                "ninguno"
            } else {
                val bytes = com.chronosbvrx.ibizadash.modules.ModuleManager.totalBytes(this)
                val size = if (bytes < 1024) "$bytes B"
                else if (bytes < 1024 * 1024) "${bytes / 1024} KB"
                else "%.1f MB".format(bytes / 1048576f)
                "${list.size} · $size"
            }
        } catch (_: Exception) {
        }
    }

    /** "976 B", "512 KB", "1.2 MB". */
    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes / 1048576f)
    }

    private fun showModulesDialog() {        val list = com.chronosbvrx.ibizadash.modules.ModuleManager.installed(this)
        if (list.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Módulos del estéreo")
                .setMessage(
                    "No hay módulos instalados.\n\n" +
                        "Se instalan desde IbizaCompanion → Ajustes → Módulos: " +
                        "el teléfono los descarga y los envía por Wi-Fi Direct."
                )
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = list.map { m ->
            "${m.name} · v${m.version} · ${formatBytes(m.sizeBytes)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Módulos instalados")
            .setItems(labels) { _, which ->
                val m = list[which]
                AlertDialog.Builder(this)
                    .setTitle(m.name)
                    .setMessage(
                        "Tipo: ${m.type}\nVersión: ${m.version}\n" +
                            "Tamaño: ${formatBytes(m.sizeBytes)}\n\n" +
                            "Quitar solo borra el registro del módulo: las teselas y " +
                            "voces ya descargadas se conservan."
                    )
                    .setPositiveButton("Quitar registro") { _, _ ->
                        com.chronosbvrx.ibizadash.modules.ModuleManager.remove(this, m.id)
                        refreshModulesStatus()
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun applyBrightness(percent: Int) {
        val params = window.attributes
        params.screenBrightness =
            0.12f + (percent.coerceIn(0, 100) / 100f) * 0.88f
        window.attributes = params
    }

    private fun setMuted(muted: Boolean) {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo cambiar el audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openQuickSettings() {
        quickSheet.visibility = View.VISIBLE
        sheetScrim.alpha = 0f
        sheetScrim.animate().alpha(1f).setDuration(200L).start()
        refreshLinkStatus()
        sheetPanel.post {
            sheetPanel.translationX = sheetPanel.width.toFloat() + dp(24)
            sheetPanel.animate()
                .translationX(0f)
                .setDuration(260L)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f))
                .start()
        }
    }

    private fun closeQuickSettings() {
        uiHandler.removeCallbacks(linkStatusRunnable)
        sheetPanel.animate()
            .translationX(sheetPanel.width.toFloat() + dp(24))
            .setDuration(200L)
            .withEndAction { quickSheet.visibility = View.GONE }
            .start()
        sheetScrim.animate().alpha(0f).setDuration(160L).start()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("DEPRECATION")
    private fun goImmersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()
}
