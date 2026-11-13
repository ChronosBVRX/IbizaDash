package com.chronosbvrx.ibizadash

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import java.io.File
import android.media.AudioManager
import android.os.Build
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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.chronosbvrx.ibizadash.car.CarSignals
import com.chronosbvrx.ibizadash.car.GpsEngine
import com.chronosbvrx.ibizadash.car.GpsFix
import com.chronosbvrx.ibizadash.car.AuxNative
import com.chronosbvrx.ibizadash.car.AvrcpController
import com.chronosbvrx.ibizadash.car.TwCommandClient
import com.chronosbvrx.ibizadash.car.HumanCarAssistant
import com.chronosbvrx.ibizadash.car.HumanInsight
import com.chronosbvrx.ibizadash.car.HumanVehicleStatus
import com.chronosbvrx.ibizadash.car.Severity
import com.chronosbvrx.ibizadash.bluetooth.BluetoothStatus
import com.chronosbvrx.ibizadash.bluetooth.BluetoothStatusClient
import com.chronosbvrx.ibizadash.map.CacheAwareTilesOverlay
import com.chronosbvrx.ibizadash.map.MapHelper
import com.chronosbvrx.ibizadash.map.MapStyle
import com.chronosbvrx.ibizadash.map.MapStyles
import com.chronosbvrx.ibizadash.map.RouteLineOverlay
import com.chronosbvrx.ibizadash.media.MediaKeys
import com.chronosbvrx.ibizadash.media.MediaRepository
import com.chronosbvrx.ibizadash.media.NowPlaying
import com.chronosbvrx.ibizadash.ui.AutomotiveBarDrawable
import com.chronosbvrx.ibizadash.ui.Motion
import com.chronosbvrx.ibizadash.ui.NavPuckOverlay
import com.chronosbvrx.ibizadash.ui.PerspectiveMapView
import com.chronosbvrx.ibizadash.ui.PerspectiveNavOverlayView
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
import com.chronosbvrx.ibizadash.nav.geoDist
import com.chronosbvrx.ibizadash.nav.projectOnSegment
import com.chronosbvrx.ibizadash.obd.DtcDictionary
import com.chronosbvrx.ibizadash.obd.Elm327Client
import com.chronosbvrx.ibizadash.obd.ObdRepository
import com.chronosbvrx.ibizadash.obd.ObdScanner
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

class MainActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var remoteMap: ImageView
    private lateinit var remoteArrow: View
    private lateinit var mapSourceChip: TextView
    private lateinit var clock: TextView
    private lateinit var clockGreeting: TextView
    private lateinit var clockAmPm: TextView
    private lateinit var date: TextView
    private lateinit var weatherTop: TextView
    private lateinit var weatherDesc: TextView
    private lateinit var weatherFeels: TextView
    private lateinit var weatherMaxMin: TextView
    private lateinit var weatherHumidity: TextView
    private lateinit var weatherWind: TextView
    private lateinit var weatherUpdated: TextView
    private lateinit var gearText: TextView
    private lateinit var speedometer: com.chronosbvrx.ibizadash.ui.ProSpeedometerView
    private lateinit var mediaTitle: TextView
    private lateinit var mediaSubtitle: TextView
    private lateinit var mediaSourceIcon: ImageView
    private lateinit var mediaDevice: TextView
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
    private lateinit var mediaCard: View
    private lateinit var weatherCard: View
    private lateinit var obdCockpitTemp: TextView
    private lateinit var obdCockpitVoltage: TextView
    private lateinit var obdCockpitRpm: TextView
    private lateinit var obdCockpitFuel: TextView
    private lateinit var obdCockpitDtc: TextView
    private lateinit var obdCockpitTempBar: ProgressBar
    private lateinit var obdCockpitVoltageBar: ProgressBar
    private lateinit var obdCockpitRpmBar: ProgressBar
    private lateinit var obdCockpitFuelBar: ProgressBar
    private lateinit var obdCockpitDtcBar: ProgressBar
    private lateinit var headlightState: TextView
    private lateinit var headlightGlow: View
    private lateinit var carRender: ImageView
    private var carLightsOn = false
    private lateinit var dock: LinearLayout

    // Continuous canvas
    private lateinit var leftPane: FrameLayout
    private lateinit var centerPane: FrameLayout
    private lateinit var rightPane: FrameLayout
    private lateinit var miniMapContainer: View
    private lateinit var homeRoot: View
    private var nightTheme = true
    private var themeRecreateScheduled = false
    private companion object {
        const val THEME_DOCK_INDEX = 5
        const val BTN_THEME_TOGGLE_ID = 9007
        const val NAV_PITCH = 52f
    }
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
    private lateinit var alertCapsule: View
    private lateinit var alertText: TextView
    private lateinit var nightDim: View
    private lateinit var nowPlayingToast: View
    private lateinit var npToastArt: ImageView
    private lateinit var npToastTitle: TextView
    private lateinit var npToastSubtitle: TextView
    private lateinit var npToastEq: ProgressBar
    private lateinit var npToastStateIcon: ImageView
    private lateinit var npToastTime: TextView
    private var lastMediaKey: String? = null
    private var lastMediaPlaying: Boolean? = null
    private var mediaToastLastShownMs = 0L

    // Vista de navegación
    private lateinit var navOverlay: View

    /** Paneles que participan en el cross-fade; se reutiliza el mismo array. */
    private val transitionPanels: Array<View> by lazy {
        arrayOf(cockpitPanel, centerPane, miniHud, mediaModule, radioModule, vehicleModule, navOverlay)
    }
    private lateinit var navCard: View
    private lateinit var navCardIcon: ImageView
    private lateinit var navCardDist: TextView
    private lateinit var navCardAction: TextView
    private lateinit var navCardThen: TextView
    private lateinit var navCardThenRow: View
    private lateinit var navCardThenIcon: ImageView
    private lateinit var navCardEta: TextView
    private lateinit var navCardRemaining: TextView
    private lateinit var navCardDest: TextView
    private lateinit var navCardTraffic: TextView
    private lateinit var navCardProgress: ProgressBar
    private lateinit var navSummaryPill: View
    private lateinit var navSummaryText: TextView
    private lateinit var btnCompass: ImageButton
    private lateinit var btnMapStyle: ImageButton
    private lateinit var btnVoice: ImageButton
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
    private lateinit var mmBackdrop: ImageView
    private lateinit var mmTitle: TextView
    private lateinit var mmArtist: TextView
    private lateinit var mmAlbum: TextView
    private lateinit var mmPlay: ImageButton
    private lateinit var mmProgress: ProgressBar
    private lateinit var mmTime: TextView
    private lateinit var mmSource: TextView
    private lateinit var mmSourceIcon: ImageView
    private lateinit var mmDevice: TextView
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
    private val metricBars = HashMap<Int, AutomotiveBarDrawable>()
    private lateinit var vHumanFuel: TextView
    private lateinit var vHumanFuelSub: TextView
    private lateinit var vHumanAccel: TextView
    private lateinit var vHumanAccelSub: TextView
    private lateinit var vHumanThermalBar: View
    private lateinit var vHumanElectricalBar: View
    private lateinit var vHumanFuelBar: View
    private lateinit var vHumanAccelBar: View
    private lateinit var vHumanThermalIcon: ImageView
    private lateinit var vHumanElectricalIcon: ImageView
    private lateinit var vHumanFuelIcon: ImageView
    private lateinit var vHumanAccelIcon: ImageView
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
    private lateinit var vFuel: TextView
    private lateinit var vFuelAdv: TextView
    private lateinit var vFuelState: TextView
    private lateinit var vAccel: TextView
    private lateinit var vAccelAdv: TextView
    private lateinit var vAccelState: TextView
    private lateinit var vConsumption: TextView
    private lateinit var vConsumptionAdv: TextView
    private lateinit var vConsumptionState: TextView
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
    private var quickSheetOpen = false
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
    private val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE d MMMM", Locale("es", "MX"))

    private val perspectiveReenable = Runnable {
        if (!perspectiveTouchDown) updatePerspective()
    }

    private var locationOverlay: MyLocationNewOverlay? = null
    private var positionMarker: org.osmdroid.views.overlay.Marker? = null
    private val routeOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private var lastSnapCenterMs = 0L
    private var gpsEngine: GpsEngine? = null
    private var lastGpsFix: GpsFix? = null
    private var lastGpsUiMs = 0L
    private var currentSpeedKmh = 0f

    // Datos que el CAN del coche publica por el MCU (no siempre por OBD).
    private var canFuelRangeKm: Int? = null
    private var canCoolantC: Float? = null
    // Aceleración de respaldo calculada con la velocidad del GPS.
    private var lastGpsSpeedKmh = 0f
    private var lastGpsSpeedAt = 0L
    private var gpsAccelMps2: Float? = null
    private var lastHumanUiMs = 0L
    private var uiVisible = true

    // Geometría completa de la ruta activa. La polilínea visible se recorta
    // conforme el coche avanza; estos arrays se conservan para calcular el
    // punto de corte, el rumbo de la carretera y el progreso.
    private var routePoints: List<GeoPoint> = emptyList()
    private var routeCum = DoubleArray(0)
    private var routeLineOverlay: RouteLineOverlay? = null
    private var puckOverlay: NavPuckOverlay? = null
    private var destIcon: BitmapDrawable? = null
    private var navCar3d: android.graphics.drawable.Drawable? = null
    private var destinationMarker: org.osmdroid.views.overlay.Marker? = null
    private val routeTrafficOverlays = mutableListOf<RouteTrafficOverlay>()
    private var currentMapStyle: MapStyle = MapStyles.ESRI_DARK
    private var navNorthUp = false

    // Perspectiva de conducción (solo NAV follow).
    private lateinit var perspectiveMap: PerspectiveMapView
    private lateinit var perspectiveOverlay: PerspectiveNavOverlayView
    private lateinit var perspectiveBackdrop: View
    private var perspectiveActive = false
    private var perspectiveTouchDown = false
    private var refTilesOverlay: CacheAwareTilesOverlay? = null
    private var routeTrimCursor = 0
    private var lastRouteProgress = -1f
    private var lastRouteTrimMs = 0L
    private var lastNavFollowMs = 0L

    // Cámara de navegación: coche abajo, zoom por velocidad.
    private var navZoomBucket = 1          // 0 urbano · 1 carretera · 2 autopista
    private var navZoomTarget = MapHelper.DEFAULT_ZOOM
    private var navZoomApplied = false
    private var lastZoomStepMs = 0L
    private var lastZoomReadyCheckMs = 0L
    private var lastZoomReady = false
    /** Centro del encuadre que se verá al subir un nivel de zoom. */
    private val ZOOM_PREFETCH_SHRINK = 0.55f
    /** Encuadre exacto al subir un nivel (mitad lineal). */
    private val ZOOM_READY_SHRINK = 0.50f

    // Teselas del viewport (solo cuando el teléfono está conectado).
    private var lastTileReqMs = 0L
    private var lastTileReqKey = ""
    private var tileReqLoop: Runnable? = null

    private var carSignals: CarSignals? = null
    private var twClient: TwCommandClient? = null
    private var avrcpController: AvrcpController? = null
    private var obdClient: Elm327Client? = null

    private val obdListener: (ObdTelemetry) -> Unit = { state ->
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
                        // Incluye la capa de etiquetas (EsriDarkRef).
                        MapHelper.clearTileCaches(map)
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
    private var followAnimator: android.animation.ValueAnimator? = null
    private var followPoint: GeoPoint? = null

    // Estado de seguimiento del mapa
    private var mapMode = MapMode.FOLLOWING
    private var lastUserMapTouchMs = 0L
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchMoved = false
    private var lastFollowCenterMs = 0L
    private var lastGpsStateUiMs = 0L
    private lateinit var gpsStateChip: TextView
    private var currentBearingDeg = 0f
    private var mapSpriteKey = ""
    private var lastDisplayBearing = 0f
    private var bearingAnimator: android.animation.ValueAnimator? = null
    private var lastNavState: NavState? = null
    private var calloutKey = ""

    /** Waze-style maneuver banner + mini strip + tarjeta de navegación. */
    private fun renderNavState(state: NavState?) {
        lastNavState = state
        if (state == null) {
            calloutKey = ""
            try {
                perspectiveOverlay.clearCallout()
            } catch (_: Exception) {
            }
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

        // El rumbo de la carretera es estable: sale de la geometría de la ruta,
        // no del bearing GPS (que salta y obliga a redibujar el mapa). Solo se
        // usa el bearing del teléfono cuando no hay ruta proyectable.
        val routeBearing = routeBearingAt(state.progress)
        val displayBearing = routeBearing ?: state.bearing
        val snapLat = if (state.snappedLat != 0.0 || state.snappedLon != 0.0) {
            state.snappedLat
        } else {
            state.lat
        }
        val snapLon = if (state.snappedLat != 0.0 || state.snappedLon != 0.0) {
            state.snappedLon
        } else {
            state.lon
        }
        val hasFix = snapLat != 0.0 || snapLon != 0.0

        // Vista heading-up: el mapa gira aunque la cámara esté en pausa por
        // una exploración del usuario, para que el coche siempre mire al
        // frente durante la ruta.
        if (focus == Focus.NAV) {
            applyNavigationHeading(displayBearing)
        }

        // Marcador sobre la carretera. En modo phone-first el teléfono manda la
        // posición; en modo local el centrado lo hace onLocationChanged.
        if (!RemoteMapBridge.active && hasFix) {
            try {
                val target = GeoPoint(snapLat, snapLon)
                positionMarker?.position = target
                updateMapMarkerSprite(displayBearing, carLightsOn)
                if (PhoneNavBridge.active) {
                    followNavState(state, displayBearing, target)
                }
                map.invalidate()
            } catch (_: Exception) {
            }
        }
        updateRouteDisplay(state)
        updateStreetCallout(state)
        renderNavOverlay()
        com.chronosbvrx.ibizadash.nav.VoiceGuidance.onState(
            this,
            state,
            NavRouteRepository.current
        )
    }

    /**
     * Globos vectoriales de calle + escudo en 2.5D. Se actualizan solo cuando
     * cambia la maniobra (no en cada fix) y se dibujan en espacio de pantalla.
     */
    private fun updateStreetCallout(state: NavState) {
        if (!::perspectiveOverlay.isInitialized) return
        if (focus != Focus.NAV) {
            if (calloutKey.isNotEmpty()) {
                calloutKey = ""
                perspectiveOverlay.clearCallout()
            }
            return
        }
        val name = state.nextName.ifBlank { state.actionText }
        val lat = state.nextLat
        val lon = state.nextLon
        if (name.isBlank() || (lat == 0.0 && lon == 0.0)) {
            if (calloutKey.isNotEmpty()) {
                calloutKey = ""
                perspectiveOverlay.clearCallout()
            }
            return
        }
        val icon = state.nextIcon ?: state.icon
        val key = "$name|${state.nextRef}|${icon.name}|$lat|$lon"
        if (key == calloutKey) return
        calloutKey = key
        perspectiveOverlay.setCallout(name, state.nextRef, icon, lat, lon)
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
            updatePerspective()
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
            updatePerspective()
        } catch (_: Exception) {
        }
    }

    /** Chip de origen del mapa: TELÉFONO (frame remoto) o LOCAL (osmdroid). */
    private fun updateMapSourceChip() {
        try {
            if (!::mapSourceChip.isInitialized) return
            val remote = RemoteMapBridge.active
            mapSourceChip.text = if (remote) "MAPA DEL TELÉFONO" else "MAPA LOCAL"
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
            if (intent.action != "source_recieve" && intent.action != "Source_recieve") return
            val dump = intent.extras?.keySet()?.joinToString(" | ") { key ->
                "$key=${intent.extras?.get(key)}"
            } ?: ""
            Log.i("VehicleData", "source_recieve extras=[$dump]")
            val value = intent.getIntExtra("SourceValue", -1)
            if (value >= 0) applyMcuSource(value)
        }
    }

    /**
     * Sincroniza la fuente de audio del MCU con el launcher.
     *
     * El servicio contesta `Source_recieve` con `SourceValue`:
     * 1 radio, 3 música local, 7 AUX, 8 Bluetooth, 9 vídeo. Si el MCU sigue
     * en Bluetooth y el usuario quiere AUX, se cambia una sola vez para que
     * el audio del cable no se quede sin ruta.
     */
    private fun applyMcuSource(value: Int) {
        mcuSource = value
        if (value == 8 && !radioSource && !auxAutoSwitchDone) {
            auxAutoSwitchDone = true
            twClient?.openAux()
            uiHandler.postDelayed({ twClient?.requestSource() }, 500L)
        }
        // La fuente del MCU es un detalle interno: la UI siempre muestra la
        // app que suena (Spotify, YouTube Music...) como un reproductor nativo.
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

    private enum class Focus(val dockIndex: Int) {
        COCKPIT(0),
        NAV(1),
        MEDIA(2),
        VEHICLE(3)
    }

    /** Seguimiento del mapa: el coche manda salvo que el usuario lo mueva. */
    private enum class MapMode { FOLLOWING, FREE }

    private var focus = Focus.COCKPIT
    private var splitFraction = 0.55f
    private var radioSource = false
    /** Fuente real del MCU: 1 radio, 3 música local, 7 AUX, 8 Bluetooth, 9 vídeo. */
    private var mcuSource = -1
    private var auxAutoSwitchDone = false
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

    private val mediaToastHide = Runnable {
        nowPlayingToast.animate().alpha(0f).setDuration(220L).withEndAction {
            nowPlayingToast.visibility = View.GONE
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
            setCarLights(value > 0)
            headlightState.text = if (value > 0) "Luces ON" else "Luces off"
            headlightState.setTextColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (value > 0) R.color.accent_sky else R.color.text_muted
                )
            )
            // Atenuador desactivado con tema nativo: se mantiene oculto.
            nightDim.visibility = View.GONE
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            val now = Date()
            val cal = java.util.Calendar.getInstance()
            clock.text = timeFormat.format(now)
            clockAmPm.text = if (cal.get(java.util.Calendar.AM_PM) == java.util.Calendar.PM) "PM" else "AM"
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            clockGreeting.text = when {
                hour < 12 -> "Buenos días"
                hour < 19 -> "Buenas tardes"
                else -> "Buenas noches"
            }
            val raw = dateFormat.format(now)
            date.text = raw.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale("es", "MX")) else it.toString()
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tema nativo antes de setContentView para no recrear en bucle.
        try {
            nightTheme = Prefs.nightMode(this)
            val want = if (nightTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            if (AppCompatDelegate.getDefaultNightMode() != want) {
                AppCompatDelegate.setDefaultNightMode(want)
            }
        } catch (_: Exception) {
        }
        super.onCreate(savedInstanceState)
        // Animaciones ligeras: respeta la escala del sistema y low-RAM.
        Motion.init(this)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_main)
        bindViews()
        goImmersive()
        setupMetricBars()

        setupMap()
        setupDock()
        setupMedia()
        setupQuickSettings()
        setupCar()
        setupObd()
        setupNavBridge()
        try {
            ContextCompat.registerReceiver(
                this,
                lightReceiver,
                IntentFilter("light_recieve"),
                ContextCompat.RECEIVER_EXPORTED
            )
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

        applyPressFeedback(window.decorView)
    }

    private fun applyPressFeedback(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyPressFeedback(view.getChildAt(i))
        }
        if (view.isClickable && (view is Button || view is ImageButton || view is TextView)) {
            view.stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
                this,
                R.animator.press_scale
            )
        }
    }

    private fun bindViews() {
        perspectiveMap = findViewById(R.id.map)
        map = perspectiveMap
        perspectiveOverlay = findViewById(R.id.perspectiveOverlay)
        perspectiveBackdrop = findViewById(R.id.perspectiveBackdrop)
        remoteMap = findViewById(R.id.remoteMap)
        remoteArrow = findViewById(R.id.remoteArrow)
        mapSourceChip = findViewById(R.id.mapSourceChip)
        gpsStateChip = findViewById(R.id.gpsStateChip)
        clock = findViewById(R.id.clock)
        clockGreeting = findViewById(R.id.greeting)
        clockAmPm = findViewById(R.id.amPm)
        date = findViewById(R.id.date)
        weatherTop = findViewById(R.id.weatherTop)
        weatherDesc = findViewById(R.id.weatherDesc)
        weatherFeels = findViewById(R.id.weatherFeels)
        weatherMaxMin = findViewById(R.id.weatherMaxMin)
        weatherHumidity = findViewById(R.id.weatherHumidity)
        weatherWind = findViewById(R.id.weatherWind)
        weatherUpdated = findViewById(R.id.weatherUpdated)
        gearText = findViewById(R.id.gear)
        speedometer = findViewById(R.id.speedometer)
        mediaTitle = findViewById(R.id.mediaTitle)
        mediaSubtitle = findViewById(R.id.mediaSubtitle)
        mediaSourceIcon = findViewById(R.id.mediaSourceIcon)
        mediaDevice = findViewById(R.id.mediaDevice)
        albumArt = findViewById(R.id.albumArt)
        btnPlay = findViewById(R.id.btnPlay)
        btStatus = findViewById(R.id.btStatus)
        btChip = findViewById(R.id.btChip)
        btBatteryGroup = findViewById(R.id.btBatteryGroup)
        btBatteryBar = findViewById(R.id.btBatteryBar)
        btBattery = findViewById(R.id.btBattery)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherCard = findViewById(R.id.weatherCard)
        mediaCard = findViewById(R.id.mediaCard)
        mediaProgress = findViewById(R.id.mediaProgress)
        mediaTime = findViewById(R.id.mediaTime)
        cockpitHealth = findViewById(R.id.cockpitHealth)
        headlightState = findViewById(R.id.headlightState)
        healthRow = findViewById(R.id.healthRow)
        healthRow.visibility = View.GONE // Sin dato, no se muestra (ver refreshHealthRow).
        healthRow.setOnClickListener { applyFocus(Focus.VEHICLE) }
        obdCockpitTemp = findViewById(R.id.obdCockpitTemp)
        obdCockpitVoltage = findViewById(R.id.obdCockpitVoltage)
        obdCockpitRpm = findViewById(R.id.obdCockpitRpm)
        obdCockpitFuel = findViewById(R.id.obdCockpitFuel)
        obdCockpitDtc = findViewById(R.id.obdCockpitDtc)
        obdCockpitTempBar = findViewById(R.id.obdCockpitTempBar)
        obdCockpitVoltageBar = findViewById(R.id.obdCockpitVoltageBar)
        obdCockpitRpmBar = findViewById(R.id.obdCockpitRpmBar)
        obdCockpitFuelBar = findViewById(R.id.obdCockpitFuelBar)
        obdCockpitDtcBar = findViewById(R.id.obdCockpitDtcBar)
        headlightGlow = findViewById(R.id.headlightGlow)
        carRender = findViewById(R.id.carRender)
        callBanner = findViewById(R.id.callBanner)
        callText = findViewById(R.id.callText)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnHangup = findViewById(R.id.btnHangup)
        dock = findViewById(R.id.dock)

        leftPane = findViewById(R.id.leftPane)
        centerPane = findViewById(R.id.centerPane)
        rightPane = findViewById(R.id.rightPane)
        miniMapContainer = findViewById(R.id.miniMapContainer)
        homeRoot = findViewById(R.id.homeRoot)
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
        alertCapsule = findViewById(R.id.alertCapsule)
        alertText = findViewById(R.id.alertText)
        nowPlayingToast = findViewById(R.id.nowPlayingToast)
        npToastArt = findViewById(R.id.npToastArt)
        npToastTitle = findViewById(R.id.npToastTitle)
        npToastSubtitle = findViewById(R.id.npToastSubtitle)
        npToastEq = findViewById(R.id.npToastEq)
        npToastStateIcon = findViewById(R.id.npToastStateIcon)
        npToastTime = findViewById(R.id.npToastTime)
        nightDim = findViewById(R.id.nightDim)

        navOverlay = findViewById(R.id.navOverlay)
        navCard = findViewById(R.id.navCard)
        navCardIcon = findViewById(R.id.navCardIcon)
        navCardDist = findViewById(R.id.navCardDist)
        navCardAction = findViewById(R.id.navCardAction)
        navCardThen = findViewById(R.id.navCardThen)
        navCardThenRow = findViewById(R.id.navCardThenRow)
        navCardThenIcon = findViewById(R.id.navCardThenIcon)
        navCardEta = findViewById(R.id.navCardEta)
        navCardRemaining = findViewById(R.id.navCardRemaining)
        navCardDest = findViewById(R.id.navCardDest)
        navCardTraffic = findViewById(R.id.navCardTraffic)
        navCardProgress = findViewById(R.id.navCardProgress)
        navSummaryPill = findViewById(R.id.navSummaryPill)
        navSummaryText = findViewById(R.id.navSummaryText)
        btnCompass = findViewById(R.id.btnCompass)
        btnMapStyle = findViewById(R.id.btnMapStyle)
        btnVoice = findViewById(R.id.btnVoice)
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
        btnCompass.setOnClickListener {
            navNorthUp = !navNorthUp
            Prefs.setNavNorthUp(this, navNorthUp)
            if (navNorthUp) {
                resetMapBearing()
            } else {
                lastNavState?.let { applyNavigationHeading(lastDisplayBearing) }
            }
            updateCompass()
            Toast.makeText(
                this,
                if (navNorthUp) "Norte arriba" else "Rumbo arriba",
                Toast.LENGTH_SHORT
            ).show()
        }
        btnMapStyle.setOnClickListener { showMapStyleDialog() }
        btnVoice.setOnClickListener {
            val on = !Prefs.voiceOn(this)
            Prefs.setVoiceOn(this, on)
            updateVoiceButton()
            try {
                if (::swVoice.isInitialized) swVoice.isChecked = on
            } catch (_: Exception) {
            }
            Toast.makeText(
                this,
                if (on) "Voz de navegación activada" else "Voz de navegación silenciada",
                Toast.LENGTH_SHORT
            ).show()
        }
        updateVoiceButton()
        updateCompass()
        btnRecenter.setOnClickListener {
            mapMode = MapMode.FOLLOWING
            lastUserMapTouchMs = 0L
            updateRecenterVisibility()
            updatePerspective()
            val target = positionMarker?.position ?: lastGpsFix?.let {
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
        mmBackdrop = findViewById(R.id.mmBackdrop)
        mmTitle = findViewById(R.id.mmTitle)
        mmArtist = findViewById(R.id.mmArtist)
        mmAlbum = findViewById(R.id.mmAlbum)
        mmPlay = findViewById(R.id.mmPlay)
        mmProgress = findViewById(R.id.mmProgress)
        mmTime = findViewById(R.id.mmTime)
        mmSource = findViewById(R.id.mmSource)
        mmSourceIcon = findViewById(R.id.mmSourceIcon)
        mmDevice = findViewById(R.id.mmDevice)
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
        vHumanAccel = findViewById(R.id.vHumanAccel)
        vHumanAccelSub = findViewById(R.id.vHumanAccelSub)
        vHumanThermalBar = findViewById(R.id.vHumanThermalBar)
        vHumanElectricalBar = findViewById(R.id.vHumanElectricalBar)
        vHumanFuelBar = findViewById(R.id.vHumanFuelBar)
        vHumanAccelBar = findViewById(R.id.vHumanAccelBar)
        vHumanThermalIcon = findViewById(R.id.vHumanThermalIcon)
        vHumanElectricalIcon = findViewById(R.id.vHumanElectricalIcon)
        vHumanFuelIcon = findViewById(R.id.vHumanFuelIcon)
        vHumanAccelIcon = findViewById(R.id.vHumanAccelIcon)
        cockpitHealthIcon = findViewById(R.id.cockpitHealthIcon)
        val techContainer = findViewById<android.widget.LinearLayout>(R.id.layoutTechDataContainer)
        val toggleBtn = findViewById<View>(R.id.btnToggleTechData)
        val toggleLabel = findViewById<TextView>(R.id.vToggleLabel)
        val toggleChevron = findViewById<ImageView>(R.id.vToggleChevron)
        var techVisible = false
        toggleBtn.setOnClickListener {
            techVisible = !techVisible
            techContainer.visibility = if (techVisible) View.VISIBLE else View.GONE
            toggleLabel.text =
                if (techVisible) "Ocultar detalles del motor"
                else "Ver detalles del motor"
            toggleChevron.setImageResource(
                if (techVisible) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
            )
            if (techVisible) {
                val scroll = vehicleModule as? ScrollView
                scroll?.post { scroll.smoothScrollTo(0, techContainer.top) }
            }
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
        findViewById<Button>(R.id.btnProbeVin).setOnClickListener {
            Toast.makeText(this, "Leyendo VIN… mira la consola", Toast.LENGTH_SHORT).show()
            obdClient?.probeVin()
            uiHandler.postDelayed({ refreshObdConsole() }, 3000L)
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
        vFuel = findViewById(R.id.vFuel)
        vFuelAdv = findViewById(R.id.vFuelAdv)
        vFuelState = findViewById(R.id.vFuelState)
        vAccel = findViewById(R.id.vAccel)
        vAccelAdv = findViewById(R.id.vAccelAdv)
        vAccelState = findViewById(R.id.vAccelState)
        vConsumption = findViewById(R.id.vConsumption)
        vConsumptionAdv = findViewById(R.id.vConsumptionAdv)
        vConsumptionState = findViewById(R.id.vConsumptionState)
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
        // La música entra por AUX: el control va siempre por Wi-Fi Direct.
        if (!MediaRepository.isDemo() && hasPhoneLink()) {
            sendPhoneMediaCommand(if (playing) "pause" else "play")
            MediaRepository.current?.let {
                MediaRepository.updateBluetooth(it.copy(isPlaying = !playing))
            }
            return
        }
        if (MediaRepository.isDemo()) {
            MediaRepository.playPause()
            return
        }
        Toast.makeText(this, "Abre IbizaCompanion para controlar la música", Toast.LENGTH_SHORT)
            .show()
    }

    private fun setupMap() {
        currentMapStyle = MapStyles.current(this)
        navNorthUp = Prefs.navNorthUp(this)
        refTilesOverlay = MapHelper.configure(this, map, currentMapStyle)
        navCar3d = try {
            ContextCompat.getDrawable(this, R.drawable.nav_car_3d)
                ?: com.chronosbvrx.ibizadash.ui.CarSpriteFactory.create(this)
        } catch (_: Exception) {
            try {
                com.chronosbvrx.ibizadash.ui.CarSpriteFactory.create(this)
            } catch (_: Exception) {
                null
            }
        }
        // Perspectiva de conducción: el puck y el coche se dibujan fuera de
        // la matriz 3D para no aplastarse. Un toque aplana el mapa antes de
        // entregar el evento a osmdroid; al soltar, si seguimos en NAV,
        // vuelve solo a la inclinación.
        perspectiveMap.pitch = 0f
        perspectiveMap.overscan = 1.25f
        perspectiveMap.carScreenFraction = 0.79f
        perspectiveMap.onTouchDown = {
            perspectiveTouchDown = true
            uiHandler.removeCallbacks(perspectiveReenable)
            updatePerspective()
        }
        perspectiveMap.onTouchUp = {
            perspectiveTouchDown = false
            uiHandler.removeCallbacks(perspectiveReenable)
            uiHandler.postDelayed(perspectiveReenable, 220L)
        }
        perspectiveOverlay.bind(
            perspectiveMap,
            { positionMarker?.icon },
            { navCar3d },
            { destIcon },
            { routePoints.lastOrNull() }
        )
        // UA propio y zoom de calle: evita la cuadrícula vacía de osmdroid.
        try {
            org.osmdroid.config.Configuration.getInstance().userAgentValue = packageName
        } catch (_: Exception) {
        }
        map.controller.setCenter(MapHelper.MORELIA)
        map.controller.setZoom(16.0)
        // Solo se deja de seguir si el usuario ARRASTRA de verdad (> 18 px en
        // el mapa) con UN dedo. Un pellizco para zoom no debe abandonar NAV.
        var multiTouch = false
        map.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchMoved = false
                    multiTouch = false
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    multiTouch = true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!multiTouch && event.pointerCount <= 1 && !touchMoved) {
                        val dx = event.x - touchStartX
                        val dy = event.y - touchStartY
                        if (dx * dx + dy * dy > 18f * 18f) {
                            touchMoved = true
                            markMapFree()
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    multiTouch = false
                }
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
        // Ruta 2D (2 pasadas) y puck. La ruta se añade antes que el puck para
        // que este quede encima; bringPuckToFront() lo reafirma al cambiarla.
        try {
            routeLineOverlay = RouteLineOverlay(this).also { map.overlays.add(it) }
            puckOverlay = NavPuckOverlay(this).apply {
                anchor = { positionMarker?.position }
            }.also { map.overlays.add(it) }
        } catch (_: Exception) {
        }
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
        PhoneNavBridge.setUsePhoneGps(Prefs.usePhoneGps(this))
        // Debug route injection.
        try {
            ContextCompat.registerReceiver(
                this,
                debugRouteReceiver,
                IntentFilter("com.chronosbvrx.ibizadash.DEBUG_ROUTE_SET"),
                ContextCompat.RECEIVER_EXPORTED
            )
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
            // El estéreo navega con su propio GPS. Avisar al teléfono para
            // que deje de enviar PHONE_NAV_STATE y no sature el enlace.
            NavLink.broadcastAsync(
                org.json.JSONObject()
                    .put("type", "NAV_MODE")
                    .put("gps", if (Prefs.usePhoneGps(this)) "phone" else "stereo")
            )
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
        Log.i("MainActivity", "drawNavRoute ${route.destinationName}: ${points.size} puntos")
        if (points.isEmpty()) return
        routePoints = points
        routeCum = buildRouteCum(points)
        routeTrimCursor = 0
        lastRouteProgress = -1f
        lastRouteTrimMs = 0L
        followPoint = points.first()

        // Dos pasadas limpias: casing azul petróleo (18 px) + core cian
        // (12 px). Sin glow ni flechas repetidas: el vértice de la próxima
        // maniobra se dibuja en pantalla (PerspectiveNavOverlayView).
        routeLineOverlay?.setPoints(points)

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
                overlay.outlinePaint.strokeWidth = 14f
                overlay.outlinePaint.strokeCap = Paint.Cap.ROUND
                overlay.outlinePaint.strokeJoin = Paint.Join.ROUND
                overlay.outlinePaint.isAntiAlias = true
                overlay.isGeodesic = false
                map.overlays.add(overlay)
                routeOverlays.add(overlay)
                routeTrafficOverlays.add(
                    RouteTrafficOverlay(overlay, span.from, span.to, ArrayList(sub))
                )
            }
        }

        // Pin de destino propio (cobre del sistema visual, no el pin genérico).
        val end = points.last()
        val marker = Marker(map)
        marker.position = end
        marker.title = route.destinationName
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = destinationPin()
        map.overlays.add(marker)
        routeOverlays.add(marker)
        destinationMarker = marker
        // El puck (halo + cono + coche) va siempre por encima de la ruta.
        bringPuckToFront()
        map.controller.animateTo(points.first())
        map.invalidate()
        applyFocus(Focus.NAV)
        NavRepository.publish(route.destinationName, "A ${route.totalDistanceMeters / 1000} km · ${route.totalDurationSeconds / 60} min", null)
        // Si el teléfono mandó su primer estado antes de que llegara la ruta
        // (race ROUTE_SET/PHONE_NAV_STATE), reaplicarlo ahora que la geometría
        // ya existe: así el mapa se centra en el coche y la ruta se recorta.
        lastNavState?.let { st ->
            if (st.destName.isBlank() || st.destName == route.destinationName) {
                renderNavState(st)
            }
        }
    }

    /** Color del tramo según la velocidad efectiva que reporta el proveedor. */
    private fun trafficColor(kmh: Int): Int = when {
        kmh in 1..20 -> Color.parseColor("#EF4444")
        kmh in 21..40 -> Color.parseColor("#F59E0B")
        else -> Color.parseColor("#EAB308")
    }

    private fun clearNavRoute() {
        destinationMarker = null
        if (routeOverlays.isNotEmpty()) {
            routeOverlays.forEach { map.overlays.remove(it) }
            routeOverlays.clear()
        }
        routeTrafficOverlays.clear()
        routeLineOverlay?.clear()
        perspectiveOverlay.clearCallout()
        routePoints = emptyList()
        routeCum = DoubleArray(0)
        routeTrimCursor = 0
        lastRouteProgress = -1f
        lastRouteTrimMs = 0L
        followPoint = null
        map.invalidate()
    }

    /** Pin de destino dibujado en código: cobre con borde carbón. */
    private fun destinationPin(): BitmapDrawable {
        destIcon?.let { return it }
        val width = dp(30)
        val height = dp(40)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = width / 2f
        val radius = dp(10).toFloat()
        val cy = radius + dp(2)
        val copper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E07A5F")
        }
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0E14")
        }
        // Cola del pin.
        val tail = Path().apply {
            moveTo(cx - dp(6).toFloat(), cy + radius * 0.5f)
            lineTo(cx + dp(6).toFloat(), cy + radius * 0.5f)
            lineTo(cx, height - dp(2).toFloat())
            close()
        }
        canvas.drawPath(tail, copper)
        canvas.drawCircle(cx, cy, radius, copper)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
            color = Color.parseColor("#0B0E14")
        }
        canvas.drawCircle(cx, cy, radius, ring)
        canvas.drawCircle(cx, cy, dp(3).toFloat(), dark)
        return BitmapDrawable(resources, bitmap).also { destIcon = it }
    }

    private data class RouteTrafficOverlay(
        val overlay: Polyline,
        val from: Float,
        val to: Float,
        val points: List<GeoPoint>
    )

    private fun buildRouteCum(points: List<GeoPoint>): DoubleArray {
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cum[i] = cum[i - 1] + geoDist(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return cum
    }

    /** Índice del punto de ruta más cercano a [progress] (0..1). */
    private fun cutIndexForProgress(progress: Float): Int {
        if (routeCum.isEmpty()) return 0
        val total = routeCum.last()
        if (total <= 1.0) return 0
        val target = progress.coerceIn(0f, 1f) * total
        var lo = 0
        var hi = routeCum.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (routeCum[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Proyecta la posición actual sobre la ruta activa. Devuelve (progreso,
     * índice de segmento). Primero busca en una ventana alrededor del último
     * corte y solo hace un recorrido completo si el coche se salió del tramo.
     */
    private fun projectOnActiveRoute(lat: Double, lon: Double): Pair<Float, Int>? {
        if (routePoints.size < 2 || routeCum.isEmpty()) return null
        val lastSeg = routePoints.size - 2
        var bestIdx = -1
        var bestT = 0.0
        var bestDist = Double.MAX_VALUE

        fun scan(from: Int, to: Int) {
            for (i in from..to) {
                val (t, _, lateral) = projectOnSegment(
                    lat, lon,
                    routePoints[i].latitude, routePoints[i].longitude,
                    routePoints[i + 1].latitude, routePoints[i + 1].longitude
                )
                if (lateral < bestDist) {
                    bestDist = lateral
                    bestIdx = i
                    bestT = t
                }
            }
        }

        val lo = (routeTrimCursor - 8).coerceAtLeast(0)
        val hi = (routeTrimCursor + 48).coerceAtMost(lastSeg)
        scan(lo, hi)
        if (bestIdx < 0 || bestDist > 90.0) {
            bestIdx = -1
            bestDist = Double.MAX_VALUE
            scan(0, lastSeg)
        }
        if (bestIdx < 0 || bestDist > 120.0) return null
        val segStart = routeCum[bestIdx]
        val segLen = (routeCum[bestIdx + 1] - segStart).coerceAtLeast(0.0)
        val traveled = segStart + bestT * segLen
        val total = routeCum.last().coerceAtLeast(1.0)
        return Pair((traveled / total).toFloat().coerceIn(0f, 1f), bestIdx)
    }

    /**
     * Recorta la polilínea visible al progreso actual: lo recorrido desaparece
     * en lugar de quedarse pintado debajo del coche.
     */
    private fun updateRouteDisplay(state: NavState) {
        if (routePoints.size < 3 || routeCum.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val projected = if (state.snappedLat != 0.0 || state.snappedLon != 0.0) {
            projectOnActiveRoute(state.snappedLat, state.snappedLon)
        } else {
            null
        }
        val progress = (projected?.first ?: state.progress).coerceIn(0f, 0.999f)
        val wentBack = lastRouteProgress >= 0f && progress + 0.03f < lastRouteProgress
        if (wentBack) {
            // Reroute, giro en U o nueva ruta: rearmar desde la posición actual.
            lastRouteProgress = progress
        }
        if (!wentBack && now - lastRouteTrimMs < 800L &&
            Math.abs(progress - lastRouteProgress) < 0.003f
        ) {
            return
        }
        lastRouteTrimMs = now
        lastRouteProgress = progress
        val cut = cutIndexForProgress(progress)
            .coerceIn(0, routePoints.size - 2)
        if (cut != routeTrimCursor) {
            routeTrimCursor = cut
            val remaining = routePoints.subList(cut, routePoints.size)
            if (remaining.size >= 2) {
                // Una sola pasada de datos: casing y core comparten el Path.
                routeLineOverlay?.setPoints(ArrayList(remaining))
            }
        }
        trimTrafficOverlays(progress)
        map.invalidate()
    }

    private fun trimTrafficOverlays(progress: Float) {
        if (routeTrafficOverlays.isEmpty()) return
        for (t in routeTrafficOverlays) {
            val start = maxOf(t.from, progress)
            if (t.to <= start + 0.0005f) {
                t.overlay.isEnabled = false
                continue
            }
            val span = (t.to - t.from).coerceAtLeast(0.0001f)
            val startT = ((start - t.from) / span).coerceIn(0f, 1f)
            val fromIdx = (startT * (t.points.size - 1)).toInt()
                .coerceIn(0, (t.points.size - 2).coerceAtLeast(0))
            val sub = t.points.subList(fromIdx, t.points.size)
            if (sub.size >= 2) {
                t.overlay.isEnabled = true
                t.overlay.setPoints(ArrayList(sub))
            } else {
                t.overlay.isEnabled = false
            }
        }
    }

    /**
     * Rumbo local de la carretera en [progress]. La dirección se promedia con
     * un corto recorrido anterior/posterior para seguir curvas sin saltos:
     * un único segmento es ruidoso y un promedio largo esconde el giro.
     */
    private fun routeBearingAt(progress: Float): Float? {
        if (routePoints.size < 2) return null
        val idx = cutIndexForProgress(progress)
            .coerceIn(0, routePoints.size - 2)
        var x = 0.0
        var y = 0.0
        var count = 0
        val start = (idx - 1).coerceAtLeast(0)
        val end = (idx + 2).coerceAtMost(routePoints.size - 2)
        for (j in start..end) {
            val a = routePoints[j]
            val b = routePoints[j + 1]
            val segLen = geoDist(a.latitude, a.longitude, b.latitude, b.longitude)
            if (segLen < 0.5) continue
            val rad = Math.toRadians(
                bearingBetween(a.latitude, a.longitude, b.latitude, b.longitude).toDouble()
            )
            // El tramo actual manda; los vecinos estabilizan el rumbo.
            val weight = segLen.coerceAtMost(25.0).coerceAtLeast(1.0) *
                if (j == idx) 2.0 else 1.0
            x += Math.cos(rad) * weight
            y += Math.sin(rad) * weight
            count++
        }
        if (count == 0) return null
        var deg = Math.toDegrees(Math.atan2(y, x))
        if (deg < 0) deg += 360.0
        return deg.toFloat()
    }

    private fun bearingBetween(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = Math.sin(dLon) * Math.cos(p2)
        val x = Math.cos(p1) * Math.sin(p2) -
            Math.sin(p1) * Math.cos(p2) * Math.cos(dLon)
        var deg = Math.toDegrees(Math.atan2(y, x))
        if (deg < 0) deg += 360.0
        return deg.toFloat()
    }

    /**
     * En modo phone-first el teléfono manda la posición; el estéreo solo
     * sigue el coche y mantiene el rumbo estable. Se limita a ~1.5 Hz para no
     * encadenar animaciones de cámara ni recalcular el giro del mapa.
     */
    private fun followNavState(state: NavState, bearing: Float, target: GeoPoint) {
        // No pelear con el dedo del usuario: durante un toque/pellizco la
        // cámara se queda quieta y retoma el seguimiento al soltar.
        if (perspectiveTouchDown) return
        if (RemoteMapBridge.active) return
        val now = SystemClock.elapsedRealtime()
        if (mapMode == MapMode.FREE && now - lastUserMapTouchMs > 30_000L) {
            mapMode = MapMode.FOLLOWING
            updateRecenterVisibility()
            updatePerspective()
        }
        if (mapMode != MapMode.FOLLOWING) return
        if (focus == Focus.NAV) {
            if (now - lastNavFollowMs < 800L) return
            lastNavFollowMs = now
            animateMapBearing(bearing)
            updateNavZoomTarget(state.speedKmh)
            animateFollow(target, state.speedKmh)
        } else {
            resetMapBearing()
            if (now - lastFollowCenterMs > 1500L) {
                lastFollowCenterMs = now
                followPoint = target
                try {
                    map.controller.animateTo(target, map.zoomLevelDouble, 600L)
                } catch (_: Exception) {
                }
            }
        }
    }

    private val debugRouteReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            val json = intent.getStringExtra("routeJson")
            Log.i("MainActivity", "DEBUG_ROUTE_SET recibido (${json?.length ?: 0} chars)")
            if (json.isNullOrBlank()) {
                // Ruta de prueba 2.5D alrededor de Morelia (calles, escudo y
                // tráfico) para validar globos, flecha y recorte sin teléfono.
                val test = NavRoute(
                    destinationName = "Centro Histórico, Morelia",
                    totalDistanceMeters = 4300,
                    totalDurationSeconds = 600,
                    encodedPolyline = "oygwBvsshR_SoPsS_S{ToP{T_S{ToP",
                    steps = listOf(
                        com.chronosbvrx.ibizadash.nav.NavStep(
                            lat = 19.7125, lon = -101.1890,
                            instruction = "Gira a la derecha en Cto. Cañadas del Bosque",
                            maneuver = "turn", modifier = "right",
                            distanceMeters = 1200,
                            name = "Cto. Cañadas del Bosque", ref = "48D"
                        ),
                        com.chronosbvrx.ibizadash.nav.NavStep(
                            lat = 19.7195, lon = -101.1830,
                            instruction = "Continúa por Av. Camelinas",
                            maneuver = "continue", modifier = "straight",
                            distanceMeters = 2100,
                            name = "Av. Camelinas", ref = "126"
                        ),
                        com.chronosbvrx.ibizadash.nav.NavStep(
                            lat = 19.7230, lon = -101.1802,
                            instruction = "Llegaste a tu destino",
                            maneuver = "arrive", modifier = "",
                            distanceMeters = 0,
                            name = "Destino", ref = ""
                        )
                    ),
                    trafficDelaySeconds = 180,
                    trafficSections = listOf(
                        com.chronosbvrx.ibizadash.nav.TrafficSpan(
                            from = 0.3f, to = 0.6f, delaySeconds = 180, speedKmh = 15
                        )
                    )
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
                    val steps = ArrayList<com.chronosbvrx.ibizadash.nav.NavStep>()
                    obj.optJSONArray("steps")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val s = arr.optJSONObject(i) ?: continue
                            steps.add(
                                com.chronosbvrx.ibizadash.nav.NavStep(
                                    lat = s.optDouble("lat", 0.0),
                                    lon = s.optDouble("lon", 0.0),
                                    instruction = s.optString("instruction", ""),
                                    maneuver = s.optString("maneuver", "").takeIf { it.isNotBlank() },
                                    modifier = s.optString("modifier", "").takeIf { it.isNotBlank() },
                                    distanceMeters = s.optInt("distanceMeters", 0),
                                    name = s.optString("name", ""),
                                    ref = s.optString("ref", "")
                                )
                            )
                        }
                    }
                    val route = NavRoute(
                        destinationName = obj.optString("destinationName", "Destino"),
                        totalDistanceMeters = obj.optInt("totalDistanceMeters", 0),
                        totalDurationSeconds = obj.optInt("totalDurationSeconds", 0),
                        encodedPolyline = obj.optString("encodedPolyline", ""),
                        steps = steps,
                        trafficDelaySeconds = obj.optInt("trafficDelayS", 0),
                        trafficSections = spans
                    )
                    NavRouteRepository.set(route)
                } catch (e: Exception) {
                    Log.w("MainActivity", "DEBUG_ROUTE_SET inválido: ${e.message}")
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
        when (bundle.getString("action")) {
            // El servicio manda la fuente en un Bundle, no en un broadcast.
            "Source_recieve" -> {
                val value = bundle.getInt("SourceValue", -1)
                if (value >= 0) applyMcuSource(value)
            }
            // El CAN del coche publica estos dos datos por el MCU. Son los
            // que el cuadro usa para la aguja y la autonomía.
            "car_info_recieve" -> {
                bundle.getString("water_temperature")
                    ?.replace(',', '.')
                    ?.toFloatOrNull()
                    ?.takeIf { it > -40f && it < 150f }
                    ?.let { canCoolantC = it }
                bundle.getString("RemainKON")
                    ?.let { raw -> Regex("\\d+").find(raw)?.value?.toIntOrNull() }
                    ?.takeIf { it in 0..2000 }
                    ?.let { canFuelRangeKm = it }
                renderHumanNow()
            }
        }
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
        requestBluetoothPermissions()
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

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val required = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 104)
        }
    }

    private fun setupMetricBars() {
        val d = resources.displayMetrics.density
        val cyan = 0xFF38BDF8.toInt()
        val blue = 0xFF0284C7.toInt()

        attachMetricBar(
            R.id.vRpm,
            AutomotiveBarDrawable(
                0f, 6500f, d,
                segmented = true,
                segmentCount = 13,
                initialLow = cyan,
                initialHigh = blue,
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vLoad,
            AutomotiveBarDrawable(
                0f, 100f, d,
                initialLow = 0xFF4F46E5.toInt(),
                initialHigh = 0xFF818CF8.toInt(),
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vThrottle,
            AutomotiveBarDrawable(
                0f, 100f, d,
                initialLow = 0xFF06B6D4.toInt(),
                initialHigh = 0xFF67E8F9.toInt(),
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vCoolant,
            AutomotiveBarDrawable(
                0f, 130f, d,
                centerBand = 85f to 95f,
                initialLow = cyan,
                initialHigh = 0xFF10B981.toInt(),
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vFuel,
            AutomotiveBarDrawable(
                0f, 100f, d,
                initialLow = cyan,
                initialHigh = 0xFF10B981.toInt(),
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vAccel,
            AutomotiveBarDrawable(
                -5f, 5f, d,
                bipolar = true,
                initialLow = 0xFF64748B.toInt(),
                initialHigh = 0xFFE2E8F0.toInt(),
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vConsumption,
            AutomotiveBarDrawable(
                0f, 20f, d,
                initialLow = 0xFF64748B.toInt(),
                initialHigh = 0xFFE2E8F0.toInt(),
                uiContext = this
            )
        )
        attachMetricBar(
            R.id.vVoltage,
            AutomotiveBarDrawable(
                11f, 15.5f, d,
                barHeightDp = 6f,
                insetDp = 14f,
                bottomDp = 10f,
                markerOnly = true,
                zones = listOf(
                    AutomotiveBarDrawable.Zone(11.0f, 12.1f, 0xFFEF4444.toInt()),
                    AutomotiveBarDrawable.Zone(12.2f, 12.8f, 0xFFF59E0B.toInt()),
                    AutomotiveBarDrawable.Zone(12.9f, 13.4f, 0xFF64748B.toInt()),
                    AutomotiveBarDrawable.Zone(13.5f, 14.6f, 0xFF10B981.toInt()),
                    AutomotiveBarDrawable.Zone(14.7f, 15.5f, 0xFFF59E0B.toInt())
                ),
                uiContext = this
            ),
            levelsUp = 2
        )
    }

    private fun attachMetricBar(
        anchorId: Int,
        bar: AutomotiveBarDrawable,
        levelsUp: Int = 1
    ) {
        var view: View? = findViewById(anchorId)
        repeat(levelsUp) { view = view?.parent as? View }
        val card = view ?: return
        val base = ContextCompat.getDrawable(this, R.drawable.bg_insight_card)?.mutate() ?: return
        card.background = LayerDrawable(arrayOf(base, bar))
        metricBars[anchorId] = bar
    }

    private fun updateMetricBars(s: ObdTelemetry) {
        val cyan = 0xFF38BDF8.toInt()
        val blue = 0xFF0284C7.toInt()
        val amber = 0xFFF59E0B.toInt()
        val red = 0xFFEF4444.toInt()
        val neutralLow = 0xFF64748B.toInt()
        val neutralHigh = 0xFFE2E8F0.toInt()

        metricBars[R.id.vRpm]?.let { bar ->
            val rpm = s.rpm?.toFloat()
            when {
                rpm == null -> bar.setColors(cyan, blue)
                rpm >= 5500f -> bar.setColors(red, red)
                rpm >= 3500f -> bar.setColors(amber, amber)
                else -> bar.setColors(cyan, blue)
            }
            bar.animateTo(rpm)
        }
        metricBars[R.id.vLoad]?.let { bar ->
            val load = s.loadPct
            if (load != null && load > 85f) bar.setColors(0xFF4F46E5.toInt(), amber)
            else bar.setColors(0xFF4F46E5.toInt(), 0xFF818CF8.toInt())
            bar.animateTo(load)
        }
        metricBars[R.id.vThrottle]?.animateTo(s.throttlePct)
        metricBars[R.id.vCoolant]?.let { bar ->
            val temp = s.coolantC
            when {
                temp == null -> bar.setColors(cyan, 0xFF10B981.toInt())
                temp < 75f -> bar.setColors(cyan, cyan)
                temp > 105f -> bar.setColors(red, red)
                else -> bar.setColors(0xFF059669.toInt(), 0xFF10B981.toInt())
            }
            bar.animateTo(temp)
        }
        metricBars[R.id.vFuel]?.let { bar ->
            val fuel = s.fuelLevelPct
            when {
                fuel == null -> bar.setColors(neutralLow, neutralHigh)
                fuel < 12f -> bar.setColors(red, red)
                fuel < 25f -> bar.setColors(amber, amber)
                else -> bar.setColors(cyan, 0xFF10B981.toInt())
            }
            bar.animateTo(fuel)
        }
        metricBars[R.id.vAccel]?.let { bar ->
            val accel = s.accelerationMps2 ?: gpsAccelMps2
            if (accel != null && kotlin.math.abs(accel) > 3f) bar.setColors(amber, amber)
            else bar.setColors(neutralLow, neutralHigh)
            bar.animateTo(accel)
        }
        metricBars[R.id.vConsumption]?.let { bar ->
            val cons = s.instantConsumptionL100
            when {
                cons == null -> bar.setColors(neutralLow, neutralHigh)
                cons > 12f -> bar.setColors(amber, amber)
                cons < 8f -> bar.setColors(0xFF059669.toInt(), 0xFF10B981.toInt())
                else -> bar.setColors(neutralLow, neutralHigh)
            }
            bar.animateTo(cons)
        }
        metricBars[R.id.vVoltage]?.animateTo(s.batteryV)
    }

    private fun setCarLights(on: Boolean) {
        carLightsOn = on
        if (::carRender.isInitialized) {
            carRender.setImageResource(if (on) R.drawable.ibiza_on else R.drawable.ibiza_off)
            // Suaviza el escalado del PNG (bordes dentados en día/noche).
            carRender.drawable?.isFilterBitmap = true
        }
        updateMapMarkerSprite(lastDisplayBearing, on)
    }

    /** El coche del minimapa cambia de ángulo según el rumbo. */
    private fun updateMapMarkerSprite(bearing: Float, lightsOn: Boolean) {
        lastDisplayBearing = bearing
        // El cono de rumbo se actualiza aunque el sprite no cambie de sector.
        puckOverlay?.bearingDeg = bearing
        val marker = positionMarker ?: return
        val sector = if (focus == Focus.NAV) {
            // Heading-up: el mapa gira para que el rumbo quede arriba y el
            // marcador (flat) se dibuja siempre erguido. No se puede mirar
            // mapOrientation aquí: la animación de giro aún puede estar en 0
            // y el sprite quedaría torcido 45°.
            "n"
        } else {
            val normalized = ((bearing % 360f) + 360f) % 360f
            val idx = (((normalized + 22.5f) / 45f).toInt()) % 8
            arrayOf("n", "ne", "e", "se", "s", "sw", "w", "nw")[idx]
        }
        val key = "${sector}_${if (lightsOn) "on" else "off"}"
        if (key == mapSpriteKey) return
        val res = mapCarRes(sector, lightsOn)
        if (res == 0) return
        val bitmap = android.graphics.BitmapFactory.decodeResource(resources, res) ?: return
        // El marcador anterior (26 dp) era casi invisible en el mapa del
        // estéreo. 56 dp mantiene la proporción del coche y se distingue
        // sin tapar la ruta ni la maniobra.
        val width = dp(56)
        val height = (width * bitmap.height / bitmap.width.toFloat())
            .toInt().coerceIn(dp(22), dp(52))
        val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
        marker.icon = android.graphics.drawable.BitmapDrawable(resources, scaled)
        marker.rotation = 0f
        mapSpriteKey = key
        map.invalidate()
    }

    private fun mapCarRes(sector: String, lightsOn: Boolean): Int = when (sector) {
        "n" -> if (lightsOn) R.drawable.map_car_n_on else R.drawable.map_car_n_off
        "ne" -> if (lightsOn) R.drawable.map_car_ne_on else R.drawable.map_car_ne_off
        "e" -> if (lightsOn) R.drawable.map_car_e_on else R.drawable.map_car_e_off
        "se" -> if (lightsOn) R.drawable.map_car_se_on else R.drawable.map_car_se_off
        "s" -> if (lightsOn) R.drawable.map_car_s_on else R.drawable.map_car_s_off
        "sw" -> if (lightsOn) R.drawable.map_car_sw_on else R.drawable.map_car_sw_off
        "w" -> if (lightsOn) R.drawable.map_car_w_on else R.drawable.map_car_w_off
        "nw" -> if (lightsOn) R.drawable.map_car_nw_on else R.drawable.map_car_nw_off
        else -> 0
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

        val s = (if (!state.connected && !state.demo) ObdTelemetry() else state).let {
            it.copy(
                coolantC = it.coolantC ?: canCoolantC,
                fuelRangeKm = it.fuelRangeKm ?: canFuelRangeKm
            )
        }
        if (updateBars) updateMetricBars(s)

        if (updateTexts) {
            // Sin conexión o sin lecturas vivas: guiones honestos en vez de
            // valores viejos.
            vRpm.text = s.rpm?.toString() ?: "--"
            vRpmAdv.text = rpmAdvice(s.rpm)
            vLoad.text = s.loadPct?.let { "${it.roundToInt()} %" } ?: "--"
            vLoadAdv.text = loadAdvice(s.loadPct, s.rpm)
            vThrottle.text = s.throttlePct?.let { "${it.roundToInt()} %" } ?: "--"
            vThrottleAdv.text = throttleAdvice(s.throttlePct)
            val coolant = s.coolantC
            val fuel = s.fuelLevelPct
            val range = s.fuelRangeKm
            val accel = s.accelerationMps2 ?: gpsAccelMps2
            vCoolant.text = coolant?.let { "${it.roundToInt()} °C" } ?: "--"
            vCoolantAdv.text = coolantAdvice(coolant)
            vFuel.text = when {
                fuel != null -> "${fuel.roundToInt()} %"
                range != null -> "~$range km"
                else -> "--"
            }
            vFuelAdv.text = fuelAdvice(fuel, range)
            vAccel.text = accel?.let { "%+.1f m/s²".format(it) } ?: "--"
            vAccelAdv.text = accelAdvice(accel)
            vConsumption.text = s.instantConsumptionL100?.let {
                "%.1f L/100".format(it)
            } ?: "--"
            vConsumptionAdv.text = consumptionAdvice(s.instantConsumptionL100)

            vVoltage.text = s.batteryV?.let { "%.1f V".format(it) } ?: "--"
            vVoltageState.text = batteryAdvice(s.batteryV, s.rpm)
            vVoltageState.setTextColor(
                ContextCompat.getColor(this, batteryStateColor(s.batteryV, s.rpm))
            )

            applyMetricState(vRpmState, rpmState(s.rpm))
            applyMetricState(vLoadState, loadState(s.loadPct))
            applyMetricState(vThrottleState, throttleState(s.throttlePct))
            applyMetricState(vCoolantState, coolantState(coolant))
            applyMetricState(vFuelState, fuelState(fuel, range))
            applyMetricState(vAccelState, accelState(accel))
            applyMetricState(vConsumptionState, consumptionState(s.instantConsumptionL100))

            state.vin?.let { vVin.text = it }
            renderFaults(state)
            renderCockpitObd(s)
        }

        if (updateTexts) {
            val human = HumanCarAssistant.evaluate(this, s, currentSpeedKmh.roundToInt())
            renderHuman(human, s)
            // Proactive alert capsule for critical/warning insights.
            val alertInsight = listOf(
                human.thermalMessage,
                human.electricalMessage,
                human.maintenanceMessage,
                fuelInsight(s)
            ).firstOrNull {
                it.voiceOrBannerAlert && (it.severity == Severity.WARNING || it.severity == Severity.CRITICAL)
            }
            if (alertInsight != null) {
                showHumanAlert(alertInsight)
            }
        }
    }

    /**
     * OBD relevante solo en la pantalla principal (Inicio).
     * Cuatro lecturas reales del adaptador: temperatura, voltaje, RPM y
     * fallas/MIL. Cuando no hay lectura real se muestra "--", nunca un valor
     * inventado. El detalle completo vive en el módulo AUTO.
     */
    private fun obdSeekColor(level: Int): Int = when (level) {
        0 -> R.color.accent_teal
        1 -> R.color.amber
        else -> R.color.danger
    }

    private fun setObdBar(bar: ProgressBar, pct: Int, level: Int) {
        bar.progress = pct.coerceIn(0, 100)
        bar.progressTintList = ContextCompat.getColorStateList(this, obdSeekColor(level))
    }

    private fun renderCockpitObd(s: ObdTelemetry) {
        try {
            if (!::obdCockpitTemp.isInitialized) return
            // TEMP: normal 75-98, aviso 99-105, peligro >105
            val tempLevel = when {
                s.coolantC == null -> 2
                s.coolantC > 105f -> 2
                s.coolantC >= 99f -> 1
                else -> 0
            }
            obdCockpitTemp.text = s.coolantC?.let { it.roundToInt().toString() + "°C" } ?: "--"
            obdCockpitTemp.setTextColor(
                ContextCompat.getColor(this, obdSeekColor(tempLevel))
            )
            setObdBar(obdCockpitTempBar, ((s.coolantC ?: 0f) / 120f * 100f).toInt(), tempLevel)
            // VOLT: normal 13.5-14.6, aviso 12.2-13.4, peligro <12.2 o >14.8
            val voltLevel = when {
                s.batteryV == null -> 2
                s.batteryV < 12.2f || s.batteryV > 14.8f -> 2
                s.batteryV < 13.5f -> 1
                s.batteryV <= 14.6f -> 0
                else -> 1
            }
            obdCockpitVoltage.text = s.batteryV?.let {
                String.format(Locale.getDefault(), "%.1fV", it)
            } ?: "--"
            obdCockpitVoltage.setTextColor(
                ContextCompat.getColor(this, obdSeekColor(voltLevel))
            )
            setObdBar(obdCockpitVoltageBar, (((s.batteryV ?: 0f) - 10f) / 6f * 100f).toInt(), voltLevel)
            // RPM: normal 600-3500, aviso 3501-5000, peligro >5000
            val rpmLevel = when {
                s.rpm == null -> 2
                s.rpm > 5000 -> 2
                s.rpm > 3500 -> 1
                else -> 0
            }
            obdCockpitRpm.text = s.rpm?.toString() ?: "--"
            obdCockpitRpm.setTextColor(
                ContextCompat.getColor(this, obdSeekColor(rpmLevel))
            )
            setObdBar(obdCockpitRpmBar, (((s.rpm ?: 0).toFloat() / 7000f * 100f).toInt()), rpmLevel)
            // NIVEL: se muestra el % si la ECU lo entrega; si solo hay
            // autonomía CAN, se muestra en km para no dejar el hueco en "--".
            val fuelLevel = s.fuelLevelPct
            val fuelRange = s.fuelRangeKm
            val fuelWarning = when {
                fuelLevel != null && fuelLevel < 12f -> 2
                fuelLevel != null && fuelLevel < 25f -> 1
                fuelLevel == null && fuelRange != null && fuelRange < 80 -> 2
                fuelLevel == null && fuelRange != null && fuelRange < 150 -> 1
                fuelLevel == null && fuelRange == null -> -1
                else -> 0
            }
            obdCockpitFuel.text = when {
                fuelLevel != null -> "${fuelLevel.roundToInt()}%"
                fuelRange != null -> "~${fuelRange}km"
                else -> "--"
            }
            obdCockpitFuel.setTextColor(
                ContextCompat.getColor(
                    this,
                    when (fuelWarning) {
                        -1 -> R.color.text_muted
                        2 -> R.color.danger
                        1 -> R.color.amber
                        else -> R.color.accent_teal
                    }
                )
            )
            setObdBar(
                obdCockpitFuelBar,
                fuelLevel?.toInt() ?: 0,
                if (fuelWarning < 0) 0 else fuelWarning
            )
            // FALLAS: 0 OK turquesa, >0 rojo
            val hasDtc = s.dtcs.isNotEmpty() || s.milOn == true
            val dtcLevel = if (hasDtc) 2 else 0
            obdCockpitDtc.text = when {
                s.dtcs.isNotEmpty() -> "${s.dtcs.size} DTC"
                s.milOn == true -> "MIL"
                s.connected || s.demo -> "OK"
                else -> "--"
            }
            obdCockpitDtc.setTextColor(
                ContextCompat.getColor(
                    this,
                    when {
                        hasDtc -> R.color.danger
                        s.connected || s.demo -> R.color.accent_teal
                        else -> R.color.text_muted
                    }
                )
            )
            setObdBar(obdCockpitDtcBar, if (hasDtc) 100 else if (s.connected || s.demo) 0 else 0, dtcLevel)
        } catch (_: Exception) {
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
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg_screen))
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
                        ContextCompat.getColor(this@MainActivity, R.color.bg_card)
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

    private fun fuelState(level: Float?, rangeKm: Int?): MetricState = when {
        level == null && rangeKm == null -> MetricState("Sin dato", R.color.text_muted)
        level != null && level < 12f -> MetricState("Reserva", R.color.danger)
        level != null && level < 25f -> MetricState("Bajo", R.color.amber)
        level != null -> MetricState("Normal", R.color.green)
        else -> MetricState("Autonomía", R.color.accent_sky)
    }

    private fun accelState(a: Float?): MetricState = when {
        a == null -> MetricState("Sin dato", R.color.text_muted)
        a > 1.5f -> MetricState("Acelerando", R.color.accent_sky)
        a > 0.25f -> MetricState("Suave", R.color.green)
        a < -1.5f -> MetricState("Frenando", R.color.amber)
        a < -0.25f -> MetricState("Reteniendo", R.color.accent_sky)
        else -> MetricState("Estable", R.color.green)
    }

    private fun consumptionState(c: Float?): MetricState = when {
        c == null -> MetricState("Sin dato", R.color.text_muted)
        c < 8f -> MetricState("Económico", R.color.green)
        c < 12f -> MetricState("Normal", R.color.accent_sky)
        else -> MetricState("Alto", R.color.amber)
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

    private fun fuelAdvice(level: Float?, rangeKm: Int?): String = when {
        level == null && rangeKm == null ->
            "La ECU no entrega el nivel por OBD. La aguja del cuadro es la referencia."
        level != null && level < 12f ->
            "Reserva. Busca gasolinera ya; no conviene apurar la bomba de gasolina."
        level != null && level < 25f ->
            "Nivel bajo. Llena pronto para no quedarte sin gasolina."
        level != null && rangeKm != null ->
            "Son ${level.roundToInt()}% del tanque; alcanza para ~$rangeKm km."
        level != null ->
            "Porcentaje del tanque. Sirve para planear la próxima carga."
        else ->
            "Autonomía estimada por el CAN: ~$rangeKm km con lo que queda."
    }

    private fun batteryAdvice(v: Float?, rpm: Int?): String = when {
        v == null -> "Sin lectura todavía."
        (rpm ?: 0) < 300 && v < 11.9 -> "Batería baja con el motor apagado: enciende o carga pronto."
        (rpm ?: 0) >= 500 && v < 13.1 -> "No está cargando: revisa la banda del alternador y las terminales."
        v in 13.2f..14.8f -> "El alternador está cargando bien la batería."
        v > 14.8 -> "Voltaje alto: conviene revisar el regulador."
        else -> "Valor en transición; observa si baja de 12 V con el motor apagado."
    }

    private fun consumptionAdvice(c: Float?): String = when {
        c == null ->
            "Se calcula con el aire que entra al motor y la velocidad. Si no hay dato, el motor no publica MAF ni consumo."
        c < 8f -> "Consumo bajo: vas manejando de forma eficiente."
        c < 12f -> "Consumo normal para este motor. Sirve para comparar trayectos."
        else -> "Consumo alto: acelerones, subida o tráfico. Levanta el pie para ahorrar."
    }

    private fun accelAdvice(a: Float?): String = when {
        a == null ->
            "Se calcula con la velocidad OBD o GPS. Sirve para notar tirones o falta de potencia."
        a > 1.5f -> "Aceleración fuerte. El motor responde; suelta un poco para cuidar el consumo."
        a > 0.25f -> "Estás acelerando. Normal al incorporarte o rebasar."
        a < -1.5f -> "Frenado fuerte. Si no fue a propósito, revisa frenos o presión de llantas."
        a < -0.25f -> "Estás frenando o reteniendo con el motor."
        else -> "Velocidad estable. Buen momento para revisar el consumo."
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

    private fun fuelInsight(state: ObdTelemetry): HumanInsight {
        val level = state.fuelLevelPct
        val range = state.fuelRangeKm
        if (level == null && range == null) {
            return HumanInsight(
                "fuel_none",
                "Nivel de gasolina no disponible",
                "Este coche no lo entrega por OBD; usa la aguja del cuadro. La autonomía sí puede llegar por CAN.",
                Severity.INFO
            )
        }
        val title = when {
            level != null && range != null -> "Gasolina ${level.roundToInt()}% · ~$range km"
            level != null -> "Gasolina ${level.roundToInt()}%"
            else -> "Autonomía ~$range km"
        }
        return when {
            level != null && level < 12f -> HumanInsight(
                "fuel_low", title,
                "Estás en reserva. Busca una gasolinera pronto para no quedarte tirado.",
                Severity.WARNING, true
            )
            level != null && level < 25f -> HumanInsight(
                "fuel_warn", title,
                "Nivel bajo. Llena antes de que entre en reserva; así cuidas la bomba.",
                Severity.WARNING
            )
            level != null -> HumanInsight(
                "fuel_ok", title,
                "Nivel leído de la ECU. Sirve para saber cuánto puedes manejar antes de cargar.",
                Severity.GOOD
            )
            else -> HumanInsight(
                "fuel_range", title,
                when {
                    (range ?: 999) < 80 ->
                        "Autonomía baja. Busca una gasolinera pronto; el CAN calcula ~$range km."
                    (range ?: 999) < 150 ->
                        "Te queda poca autonomía. Planea tu próxima carga."
                    else ->
                        "Distancia aproximada que puedes manejar con lo que queda. La calcula el CAN del coche."
                },
                when {
                    (range ?: 999) < 80 -> Severity.WARNING
                    (range ?: 999) < 150 -> Severity.INFO
                    else -> Severity.GOOD
                },
                voiceOrBannerAlert = (range ?: 999) < 80
            )
        }
    }

    private fun accelInsight(state: ObdTelemetry): HumanInsight {
        val a = state.accelerationMps2 ?: gpsAccelMps2
        if (a == null) {
            return HumanInsight(
                "accel_none",
                "Aceleración sin dato",
                "Se calcula con la velocidad OBD o GPS. Sirve para notar tirones o falta de potencia.",
                Severity.INFO
            )
        }
        val title = when {
            a > 1.5f -> "Aceleración fuerte %+.1f m/s²".format(a)
            a > 0.25f -> "Acelerando %+.1f m/s²".format(a)
            a < -1.5f -> "Frenado fuerte %+.1f m/s²".format(a)
            a < -0.25f -> "Frenando %+.1f m/s²".format(a)
            else -> "Velocidad estable %+.1f m/s²".format(a)
        }
        val sub = "Qué tan rápido cambia la velocidad. Sirve para detectar tirones, falta de potencia o frenadas bruscas."
        return HumanInsight(
            "accel",
            title,
            sub,
            if (kotlin.math.abs(a) > 3f) Severity.WARNING else Severity.GOOD
        )
    }

    private fun renderHumanNow() {
        try {
            val state = ObdRepository.current.let {
                it.copy(
                    coolantC = it.coolantC ?: canCoolantC,
                    fuelRangeKm = it.fuelRangeKm ?: canFuelRangeKm
                )
            }
            renderHuman(
                HumanCarAssistant.evaluate(this, state, currentSpeedKmh.roundToInt()),
                state
            )
        } catch (_: Exception) {
        }
    }

    private fun renderHuman(human: HumanVehicleStatus, state: ObdTelemetry) {
        hasObdData = state.connected || state.demo
        refreshHealthRow()
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
            vHumanAccelBar, vHumanAccelIcon, vHumanAccel, vHumanAccelSub,
            accelInsight(state), R.drawable.ic_gauge
        )

        cockpitHealth.text =
            "${human.headlineStatus}\n${human.thermalMessage.title} · ${human.electricalMessage.title}"
    }

    /** Cantidad de sistemas con datos reales. */
    private fun monitoredSystems(state: ObdTelemetry): Int {
        var n = 0
        if (state.coolantC != null || state.intakeC != null) n++
        if (state.batteryV != null) n++
        if (state.fuelLevelPct != null || state.fuelRangeKm != null) n++
        if (state.accelerationMps2 != null || gpsAccelMps2 != null) n++
        return n.coerceAtLeast(1)
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
        val searchIndex = labels.size
        labels.add("Buscar nuevo OBD (Bluetooth clásico + BLE)")
        val autoIndex = labels.size
        labels.add("Buscar automáticamente")
        val demoIndex = labels.size
        labels.add("Modo demo (simulado)")
        val settingsIndex = labels.size
        labels.add("Ajustes Bluetooth")
        val pinIndex = labels.size
        labels.add("PIN de emparejamiento OBD (${Prefs.obdPin(this)})")
        val disconnectIndex = labels.size
        labels.add("Desconectar")

        AlertDialog.Builder(this)
            .setTitle("OBD-II · ELM327")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    in devices.indices -> {
                        val device = devices[which]
                        Prefs.setObdDemo(this, false)
                        Prefs.setObdMac(this, device.address)
                        if (device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                            Thread { ObdScanner.pair(device, Prefs.obdPin(this)) }.start()
                        }
                        obdClient?.connect(device.address, demo = false)
                    }
                    searchIndex -> searchObdDevices()
                    autoIndex -> {
                        Prefs.setObdDemo(this, false)
                        Prefs.setObdMac(this, null)
                        obdClient?.connect(null, demo = false)
                    }
                    demoIndex -> {
                        Prefs.setObdDemo(this, true)
                        obdClient?.connect(null, demo = true)
                    }
                    settingsIndex -> {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    pinIndex -> {
                        showObdPinDialog()
                    }
                    disconnectIndex -> {
                        Prefs.setObdDemo(this, false)
                        obdClient?.stop()
                        ObdRepository.publish(ObdTelemetry())
                    }
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun searchObdDevices() {
        val client = obdClient
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (client == null) {
            Toast.makeText(this, "El cliente OBD todavía no está listo", Toast.LENGTH_SHORT).show()
            return
        }
        val progress = AlertDialog.Builder(this)
            .setTitle("Buscando OBD…")
            .setMessage("Revisando Bluetooth clásico y BLE. Enciende el contacto y el adaptador.")
            .setCancelable(false)
            .create()
        progress.show()
        client.discoverDevices { list ->
            progress.dismiss()
            if (isFinishing || isDestroyed) return@discoverDevices
            if (list.isEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("OBD no encontrado")
                    .setMessage(
                        "No apareció ningún dispositivo. Revisa que el ELM327 tenga " +
                            "corriente, que el contacto esté encendido y que esté cerca del estéreo."
                    )
                    .setPositiveButton("Ajustes Bluetooth") { _, _ ->
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
                return@discoverDevices
            }
            val items = list.map { it.label }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle("Elegir adaptador OBD")
                .setItems(items) { _, index ->
                    val pick = list[index]
                    Prefs.setObdDemo(this, false)
                    Prefs.setObdMac(this, pick.address)
                    try {
                        val device = manager?.adapter?.getRemoteDevice(pick.address)
                        if (device != null && device.type != BluetoothDevice.DEVICE_TYPE_LE) {
                            Thread { ObdScanner.pair(device, Prefs.obdPin(this)) }.start()
                        }
                    } catch (_: Exception) {
                    }
                    obdClient?.connect(pick.address, demo = false)
                    Toast.makeText(
                        this,
                        "Conectando a ${pick.name.ifBlank { pick.address }}…",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }
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
        // Lee el tema guardado ANTES de crear el ítem dinámico del dock.
        try {
            nightTheme = Prefs.nightMode(this)
        } catch (_: Exception) {
            nightTheme = true
        }
        // Dock Home 9 ítems exactos: Apps, Vol -, Música, Streaming,
        // Dock original de 6: INICIO, MAPA, MÚSICA, AUTO, APPS, AJUSTES.
        // El interruptor de tema vive en Ajustes rápidos (switch Modo noche).
        addDockItem(R.drawable.ic_home, "INICIO") {
            applyFocus(Focus.COCKPIT)
        }
        addDockItem(R.drawable.ic_map, "MAPA") {
            applyFocus(Focus.NAV)
        }
        addDockItem(R.drawable.ic_media, "MÚSICA") {
            // Música y radio viven en el mismo módulo. Al abrir MÚSICA se
            // selecciona AUX en el MCU, que es la única entrada que usamos.
            setMediaSource(false)
            applyFocus(Focus.MEDIA)
        }
        addDockItem(R.drawable.ic_car, "AUTO") {
            applyFocus(Focus.VEHICLE)
        }
        addDockItem(R.drawable.ic_apps, "APPS") {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        addDockItem(R.drawable.ic_settings, "AJUSTES") {
            openQuickSettings()
        }
        applyThemeBits()
        applyFocus(Focus.COCKPIT, animate = false)
    }

    /** Accesos rápidos antiguos del dock de 9 (vol/streaming/bt/waze/wifi). */
    private fun quickAction(action: String) {
        when (action) {
            "VOL_NEG" -> adjustVolumeDir(-1)
            "VOL_POS" -> adjustVolumeDir(1)
            "STREAMING" -> openStreaming()
            "BLUETOOTH" -> showBluetoothDetails()
            "WAZE" -> if (!openPackage("com.waze")) Toast.makeText(this, "Waze no está instalado", Toast.LENGTH_SHORT).show()
            "WIFI" -> try {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                openQuickSettings()
            }
        }
    }

    private fun adjustVolumeDir(dir: Int) {
        try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo cambiar el volumen", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openStreaming() {
        val pkg = MediaRepository.current?.packageName
        if (!pkg.isNullOrBlank() && pkg != "demo" && openPackage(pkg)) return
        if (openPackage("com.google.android.apps.youtube.music")) return
        if (openPackage("com.google.android.youtube")) return
        if (!openPackage("com.tw.media")) {
            Toast.makeText(this, "Sin app de streaming", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTheme() {
        applyTheme(!nightTheme)
    }

    /**
     * Tema día/noche NATIVO (AppCompat + values-night/colors.xml).
     * Los colores semánticos bg_screen/bg_card/stroke_card/bg_pill/
     * text_primary/text_secondary/accent_blue/car_glow se resuelven solos
     * por el recurso día/noche; aquí solo se sincroniza el estado.
     * Noche: dock dice "Modo día" + Sol. Día: "Modo noche" + Luna.
     * Al cambiar el modo, la Activity se recrea y se re-ejecuta onCreate.
     */
    private fun applyTheme(isNightMode: Boolean) {
        val changed = nightTheme != isNightMode
        nightTheme = isNightMode
        try {
            Prefs.setNightMode(this, isNightMode)
        } catch (_: Exception) {
        }
        val mode = if (isNightMode) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        applyThemeBits()
        // `uiMode` se maneja en el manifest para que los cambios de día/noche
        // del estéreo no recrea el launcher. Cuando el cambio lo pide el
        // usuario, se recrea UNA sola vez para reinflar los recursos.
        if (changed && !themeRecreateScheduled) {
            themeRecreateScheduled = true
            uiHandler.post {
                themeRecreateScheduled = false
                if (!isFinishing && !isDestroyed) recreate()
            }
        }
    }

    /**
     * El estéreo puede cambiar el uiMode por las luces del coche. Ese cambio
     * NO debe destruir/recrear la Activity (producía el ciclo abre/cierra).
     * El tema lo manda la preferencia del usuario; aquí solo se refrescan los
     * bits que no dependen de recursos.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        try {
            applyThemeBits()
        } catch (_: Exception) {
        }
    }

    /** Bits no cubiertos por recursos: mapa, velocímetro, faros, dock y switch. */
    private fun applyThemeBits() {
        try {
            if (!::homeRoot.isInitialized) return
            // Sin overlay de atenuación: el tema nativo ya oscurece (#050508).
            nightDim.visibility = View.GONE
            // Base Esri: en NAV lleva tinte azul petróleo y más contraste.
            // El filtro se reaplica sin perder el estado de perspectiva.
            applyMapBaseFilter()
            // Marcador, puck y perspectiva según foco.
            updatePerspective()
            speedometer.setNightMode(nightTheme)
            try {
                setCarLights(nightTheme)
            } catch (_: Exception) {
            }
            // Reflectar en Ajustes rápidos si ya existe (modo noche vive ahí).
            try {
                if (::swNight.isInitialized) swNight.isChecked = nightTheme
            } catch (_: Exception) {
            }
            setActiveDock(focus.dockIndex)
        } catch (_: Exception) {
        }
    }

    /** Atajo para el setup inicial: respeta la preferencia guardada. */
    private fun applyTheme() {
        applyThemeBits()
    }

    private fun addDockItem(iconRes: Int, label: String, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
            stateListAnimator = android.animation.AnimatorInflater.loadStateListAnimator(
                this@MainActivity,
                R.animator.press_scale
            )
            setPadding(dp(4), dp(6), dp(4), dp(4))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.text_muted)
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
        }
        val text = TextView(this).apply {
            this.text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
            textSize = 11f
            letterSpacing = 0.06f
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

    private fun setActiveDock(index: Int, animate: Boolean = false) {
        if (dockItems.isEmpty()) return
        activeDockIndex = index
        val accent = R.color.accent_primary
        val muted = R.color.text_secondary
        val canAnimate = animate && Motion.enabled()
        dockItems.forEachIndexed { i, item ->
            val active = index >= 0 && i == activeDockIndex
            item.setBackgroundResource(0)
            item.alpha = if (active) 1f else 0.72f
            val icon = dockIcons[i]
            val indicator = dockIndicators[i]
            icon.imageTintList = ContextCompat.getColorStateList(
                this,
                if (active) accent else muted
            )
            dockLabels[i].setTextColor(
                ContextCompat.getColor(
                    this,
                    if (active) accent else muted
                )
            )
            if (active) {
                indicator.animate().cancel()
                indicator.visibility = View.VISIBLE
                if (canAnimate) {
                    indicator.alpha = 0f
                    indicator.scaleX = 0.35f
                    indicator.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .setDuration(Motion.duration(200L))
                        .setInterpolator(Motion.emphasized)
                        .withEndAction {
                            indicator.alpha = 1f
                            indicator.scaleX = 1f
                        }
                        .start()
                    Motion.pulse(icon, 1.10f, 90L, 160L)
                } else {
                    indicator.alpha = 1f
                    indicator.scaleX = 1f
                    icon.animate().cancel()
                    Motion.resetScale(icon)
                }
            } else {
                val indicatorWasVisible = indicator.visibility == View.VISIBLE
                indicator.animate().cancel()
                if (canAnimate && indicatorWasVisible) {
                    indicator.animate()
                        .alpha(0f)
                        .setDuration(Motion.duration(120L))
                        .setInterpolator(Motion.standard)
                        .withEndAction {
                            indicator.visibility = View.INVISIBLE
                            indicator.alpha = 1f
                            indicator.scaleX = 1f
                        }
                        .start()
                } else {
                    indicator.visibility = View.INVISIBLE
                    indicator.alpha = 1f
                    indicator.scaleX = 1f
                }
                icon.animate().cancel()
                Motion.resetScale(icon)
            }
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

        mediaCard.setOnClickListener {
            applyFocus(Focus.MEDIA)
        }
    }

    private fun mediaPrev() {
        if (!MediaRepository.isDemo() && hasPhoneLink()) {
            sendPhoneMediaCommand("prev")
            return
        }
        if (MediaRepository.isDemo()) {
            MediaRepository.prev()
            return
        }
        Toast.makeText(this, "Abre IbizaCompanion para controlar la música", Toast.LENGTH_SHORT)
            .show()
    }

    private fun mediaNext() {
        if (!MediaRepository.isDemo() && hasPhoneLink()) {
            sendPhoneMediaCommand("next")
            return
        }
        if (MediaRepository.isDemo()) {
            MediaRepository.next()
            return
        }
        Toast.makeText(this, "Abre IbizaCompanion para controlar la música", Toast.LENGTH_SHORT)
            .show()
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
            // El servicio del MCU ignora los comandos AUX por Bundle. La app
            // nativa com.tw.auxin sí deja SourceValue=7; se abre un instante
            // y se vuelve al launcher. El onPause nativo no borra la fuente.
            if (mcuSource == 7) {
                twClient?.requestSource()
            } else {
                AuxNative.selectAux(this) {
                    twClient?.requestSource()
                }
            }
        }
        uiHandler.postDelayed({ twClient?.requestSource() }, 700L)
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

    private data class MediaSourceInfo(val iconRes: Int, val label: String)

    /**
     * Etiqueta e icono de la app que reproduce.
     *
     * Con A2DP el paquete real del teléfono no siempre viaja en el propio
     * metadato; en ese caso el receiver reconoce al menos Spotify por su
     * broadcast público y el resto cae en un Bluetooth honesto.
     */
    private fun mediaSourceInfo(packageName: String?, sourceLabel: String = ""): MediaSourceInfo {
        val pkg = packageName.orEmpty().lowercase().trim()
        val fallback = sourceLabel.ifBlank { "Música" }
        return when {
            pkg == "com.spotify.music" || pkg.startsWith("com.spotify.") ->
                MediaSourceInfo(R.drawable.ic_spotify, "Spotify")
            pkg == "com.google.android.apps.youtube.music" ||
                pkg == "com.google.android.music" ->
                MediaSourceInfo(R.drawable.ic_youtube_music, "YouTube Music")
            pkg == "com.google.android.youtube" ||
                pkg == "com.google.android.youtubemusic" ->
                MediaSourceInfo(R.drawable.ic_youtube, "YouTube")
            pkg == "com.apple.android.music" ->
                MediaSourceInfo(R.drawable.ic_media, "Apple Music")
            pkg == "com.tidal" || pkg.startsWith("com.tidal.") ->
                MediaSourceInfo(R.drawable.ic_media, "Tidal")
            pkg == "deezer.android.app" || pkg.startsWith("com.deezer.") ->
                MediaSourceInfo(R.drawable.ic_media, "Deezer")
            pkg == "com.amazon.mp3" || pkg.startsWith("com.amazon.") ->
                MediaSourceInfo(R.drawable.ic_media, "Amazon Music")
            pkg == "com.soundcloud.android" || pkg.startsWith("com.soundcloud.") ->
                MediaSourceInfo(R.drawable.ic_media, "SoundCloud")
            pkg == "demo" ->
                MediaSourceInfo(R.drawable.ic_media, "Demo")
            else -> MediaSourceInfo(R.drawable.ic_media, fallback)
        }
    }

    private fun applyMediaSourceIcon(iconView: ImageView, info: MediaSourceInfo) {
        iconView.setImageResource(info.iconRes)
        val brandIcon = info.iconRes in intArrayOf(
            R.drawable.ic_spotify,
            R.drawable.ic_youtube_music,
            R.drawable.ic_youtube
        )
        if (brandIcon) {
            iconView.imageTintList = null
        } else {
            iconView.imageTintList = ContextCompat.getColorStateList(
                this,
                R.color.accent_sky
            )
        }
    }

    /**
     * Modal del mapa estilo Android Auto: tapa inferior del lienzo con portada,
     * título, artista y estado. Se muestra al iniciar la reproducción y cada
     * vez que cambia de canción; los avisos genéricos no lo disparan.
     */
    private fun showNowPlayingToast(nowPlaying: NowPlaying?) {
        if (nowPlaying == null || focus != Focus.NAV) return
        val title = nowPlaying.title.ifBlank { getString(R.string.media_title) }
        val key = "${nowPlaying.packageName}|$title|${nowPlaying.album}"
        val changed = key != lastMediaKey
        val stateChanged = lastMediaPlaying != null && lastMediaPlaying != nowPlaying.isPlaying
        if (!changed && !stateChanged) return
        val now = SystemClock.elapsedRealtime()
        if (changed && now - mediaToastLastShownMs < 1200L) {
            lastMediaKey = key
            lastMediaPlaying = nowPlaying.isPlaying
            renderNowPlayingToast(nowPlaying)
            return
        }
        lastMediaKey = key
        mediaToastLastShownMs = now
        lastMediaPlaying = nowPlaying.isPlaying
        renderNowPlayingToast(nowPlaying)
        uiHandler.removeCallbacks(mediaToastHide)
        nowPlayingToast.visibility = View.VISIBLE
        nowPlayingToast.alpha = 0f
        nowPlayingToast.translationY = 40f
        nowPlayingToast.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(240L)
            .start()
        uiHandler.postDelayed(mediaToastHide, 4200L)
    }

    private fun renderNowPlayingToast(nowPlaying: NowPlaying) {
        val title = nowPlaying.title.ifBlank { getString(R.string.media_title) }
        renderArt(npToastArt, nowPlaying.artwork, 10)
        npToastTitle.text = title
        val sourceInfo = mediaSourceInfo(nowPlaying.packageName, nowPlaying.sourceLabel)
        val subtitle = listOf(
            nowPlaying.artist.ifBlank { "Desconocido" },
            sourceInfo.label,
            if (nowPlaying.isPlaying) "Reproduciendo" else "En pausa"
        ).joinToString(" · ")
        npToastSubtitle.text = subtitle
        npToastEq.visibility = if (nowPlaying.isPlaying) View.VISIBLE else View.GONE
        npToastStateIcon.setImageResource(
            if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        if (nowPlaying.durationMs > 0L) {
            npToastTime.visibility = View.VISIBLE
            npToastTime.text = formatDuration(nowPlaying.durationMs)
        } else {
            npToastTime.visibility = View.GONE
        }
    }

    private fun updateMedia(nowPlaying: NowPlaying?) {
        if (nowPlaying == null) {
            mediaTitle.text = getString(R.string.media_title)
            mediaSubtitle.text = "Música · Radio FM"
            renderArt(albumArt, null, 14)
            btnPlay.setImageResource(R.drawable.ic_play)
            mediaProgress.visibility = View.GONE
            mediaTime.visibility = View.GONE

            miniTitle.text = "Sin reproducción"
            renderArt(miniArt, null, 9)
            miniPlay.setImageResource(R.drawable.ic_play)

            mmTitle.text = "Sin reproducción"
            mmArtist.text = "Música · Radio FM"
            mmAlbum.text = ""
            renderArt(mmArt, null, 70)
        mmBackdrop.setImageDrawable(null)
            mmPlay.setImageResource(R.drawable.ic_play)
            mmProgress.visibility = View.GONE
            mmTime.visibility = View.GONE
            mmSource.text = "Música"
            mediaSourceIcon.setImageResource(R.drawable.ic_media)
            mediaDevice.text = "Reproductor"
            mmSourceIcon.setImageResource(R.drawable.ic_media)
            mmDevice.text = mediaDevice.text
            lastMediaKey = null
            lastMediaPlaying = null
            renderNowPlayingToastIdle()
            return
        }

        val title = nowPlaying.title.ifBlank { getString(R.string.media_title) }
        mediaTitle.text = title
        val sourceInfo = mediaSourceInfo(nowPlaying.packageName, nowPlaying.sourceLabel)
        val deviceText = sourceInfo.label
        val subtitle = listOf(nowPlaying.artist, sourceInfo.label)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        mediaSubtitle.text = subtitle
        mediaDevice.text = deviceText
        applyMediaSourceIcon(mediaSourceIcon, sourceInfo)

        renderArt(albumArt, nowPlaying.artwork, 14)
        renderArt(miniArt, nowPlaying.artwork, 9)
        renderArt(mmArt, nowPlaying.artwork, 70)
        val art = nowPlaying.artwork
        if (art != null) {
            mmBackdrop.setImageBitmap(blurBackdrop(art))
            mmBackdrop.alpha = 0.20f
        } else {
            mmBackdrop.setImageDrawable(null)
        }

        miniTitle.text = title
        mmTitle.text = title
        mmArtist.text = nowPlaying.artist.ifBlank { "Reproducción activa" }
        mmAlbum.text = nowPlaying.album
        mmSource.text = "Música"
        mmDevice.text = deviceText
        applyMediaSourceIcon(mmSourceIcon, sourceInfo)

        val playRes = if (nowPlaying.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        btnPlay.setImageResource(playRes)
        miniPlay.setImageResource(playRes)
        mmPlay.setImageResource(playRes)

        renderProgress(nowPlaying.positionMs, nowPlaying.durationMs)
        showNowPlayingToast(nowPlaying)
    }

    private fun renderNowPlayingToastIdle() {
        npToastTitle.text = "Sin reproducción"
        npToastSubtitle.text = "Música · Radio FM"
        npToastEq.visibility = View.GONE
        npToastStateIcon.setImageResource(R.drawable.ic_play)
        npToastTime.visibility = View.GONE
        renderArt(npToastArt, null, 10)
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

    private fun blurBackdrop(src: android.graphics.Bitmap): android.graphics.Bitmap {
        // Desenfoque barato y estable: reducir a 48 px y volver a escalar.
        val width = 48
        val height = (src.height * width / src.width).coerceAtLeast(1)
        val tiny = android.graphics.Bitmap.createScaledBitmap(src, width, height, true)
        val out = android.graphics.Bitmap.createScaledBitmap(tiny, src.width, src.height, true)
        if (tiny !== out) tiny.recycle()
        return out
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

        currentSpeedKmh = signals.speedKmh
        val accelNow = SystemClock.elapsedRealtime()
        if (lastGpsSpeedAt > 0L && accelNow - lastGpsSpeedAt in 250L..3_000L) {
            val dt = (accelNow - lastGpsSpeedAt) / 1000f
            val raw = ((signals.speedKmh - lastGpsSpeedKmh) / 3.6f) / dt
            gpsAccelMps2 = if (gpsAccelMps2 == null) {
                raw.coerceIn(-8f, 8f)
            } else {
                (gpsAccelMps2!! * 0.65f + raw * 0.35f).coerceIn(-8f, 8f)
            }
        } else if (lastGpsSpeedAt == 0L || accelNow - lastGpsSpeedAt > 3_000L) {
            gpsAccelMps2 = null
        }
        lastGpsSpeedKmh = signals.speedKmh
        lastGpsSpeedAt = accelNow
        speedometer.useMph = mph
        speedometer.setSourceLabel(signals.source)
        speedometer.setSpeed(signals.speedKmh.roundToInt())
        gearText.text = when (gear) {
            "D" -> "D · DRIVE"
            "R" -> "R · REVERSA"
            else -> "$gear · ESTACIONADO"
        }

        miniSpeed.text = speedLabel
        miniGear.text = gear
        hudPillSpeed.text = speedLabel
        hudPillGear.text = gear

        vSpeed.text = "$speedLabel $unitLabel"
        vGear.text = gear
        vGearText.text = when (gear) {
            "R" -> "Reversa"
            "D" -> "Avance"
            "N" -> "Neutro"
            else -> "Estacionado"
        }
        vSource.text = signals.source
        vBusChip.text = signals.source
        vBusChip.setTextColor(ContextCompat.getColor(this, R.color.accent_sky))
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
            canStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_sky))
        }

        // Odómetro: integra la velocidad GPS por tiempo real. Se acumula en
        // memoria y se vuelca cada minuto (y al pausar).
        val now = SystemClock.elapsedRealtime()
        if (signals.speedKmh > 2f && lastOdometerMs > 0L) {
            val dtHours = (now - lastOdometerMs).coerceIn(0L, 5_000L) / 3_600_000f
            odometerPendingKm += signals.speedKmh * dtHours
        }
        lastOdometerMs = now
        if (odometerPendingKm >= 0.05f && now - odometerFlushMs >= 60_000L) {
            flushOdometer()
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
        // La aceleración de respaldo (GPS) y la autonomía CAN deben refrescar
        // el panel AUTO aunque el adaptador OBD no entregue velocidad.
        if (now - lastHumanUiMs >= 2_000L) {
            lastHumanUiMs = now
            renderHumanNow()
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
            // Sin A2DP: el teléfono puede estar enlazado por Wi-Fi Direct.
            if (hasPhoneLink()) {
                btStatus.text = "Wi-Fi Direct · conectado"
                btStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.accent_teal)
                )
            } else {
                btStatus.text = "Sin teléfono"
                btStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            }
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
        if (::weatherCard.isInitialized) {
            weatherCard.visibility = visible
        }
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
        try {
            weatherDesc.text = state.text.ifBlank { "Sin datos" }
            weatherFeels.text = state.feelsLikeC?.let {
                "Sensación " + String.format(Locale.getDefault(), "%.0f°", it)
            } ?: ""
            weatherMaxMin.text = when {
                state.maxC != null && state.minC != null ->
                    String.format(Locale.getDefault(), "%.0f° / %.0f°", state.maxC, state.minC)
                state.maxC != null -> String.format(Locale.getDefault(), "Máx %.0f°", state.maxC)
                state.minC != null -> String.format(Locale.getDefault(), "Mín %.0f°", state.minC)
                else -> "-- / --"
            }
            weatherHumidity.text = state.humidityPct?.let { "$it %" } ?: "--"
            weatherWind.text = state.windKmh?.let {
                String.format(Locale.getDefault(), "%.0f km/h", it)
            } ?: "--"
            val updatedText = if (state.updatedAtMs > 0L) {
                val fmt = SimpleDateFormat("h:mm a", Locale("es", "MX"))
                "Actualizado " + fmt.format(Date(state.updatedAtMs)).replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale("es", "MX")) else c.toString()
                }
            } else ""
            weatherUpdated.text = updatedText
            try {
                findViewById<TextView>(R.id.weatherUpdatedGrid).text =
                    if (state.updatedAtMs > 0L) {
                        val fmt2 = SimpleDateFormat("h:mm a", Locale("es", "MX"))
                        fmt2.format(Date(state.updatedAtMs))
                    } else "--"
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
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

        if (locationOverlay == null) {
            locationOverlay = MapHelper.addLocation(this, map)
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
                positionMarker = m
                // En mini-mapa (Inicio) no se muestra el marcador verde:
                // solo queda el badge "Tu ubicación".
                syncMapPuck(focus == Focus.NAV)
                updateMapMarkerSprite(lastDisplayBearing, carLightsOn)
            } catch (_: Exception) {
            }
        }

        // Un solo motor GPS para toda la pantalla: reglas de rechazo,
        // suavizado y extrapolación entre fixes. Se mantiene vivo mientras la
        // Activity exista para que abrir otra app no deje el mapa sin señal.
        val engine = gpsEngine ?: GpsEngine(this).also { newEngine ->
            newEngine.onFix = { fix -> runOnUiThread { onGpsFix(fix) } }
            newEngine.onNoFix = { runOnUiThread { onGpsNoFix() } }
            gpsEngine = newEngine
        }
        engine.start()
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

    private fun onGpsFix(fix: GpsFix) {
        if (isFinishing || isDestroyed) return
        val wasPredicted = lastGpsFix?.let { it.extrapolated || it.lost } == true
        lastGpsFix = fix
        currentSpeedKmh = fix.speedKmh
        carSignals?.setGpsSpeed(fix.speedKmh)
        if (!uiVisible) return
        updateGpsStateChip(fix)

        // Sin señal: se conserva la última posición y la velocidad cae a 0.
        if (fix.lost) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastGpsUiMs < 250L) return
        lastGpsUiMs = now

        // Marcador sobre la carretera + seguimiento del vehículo.
        // Con mapa remoto el MapView está oculto y no hace falta.
        // En modo phone-first el teléfono manda posición/progreso; el GPS
        // local no debe competir con él (causaría saltos y rumbo inestable).
        if (RemoteMapBridge.active || PhoneNavBridge.active) return
        try {
            // Al recuperar señal tras una extrapolación, el salto real puede
            // ser grande: se reinicia el suavizado para no quedarse clavado.
            if (wasPredicted && !fix.extrapolated) {
                com.chronosbvrx.ibizadash.nav.RoadSnapper.reset()
            }
            val (slat, slon, _) = com.chronosbvrx.ibizadash.nav.RoadSnapper.snapLocal(
                fix.latitude, fix.longitude, fix.speedKmh, fix.accuracyM
            )
            positionMarker?.position = GeoPoint(slat, slon)
            // Fuera de NAV el sprite apunta al rumbo GPS. En NAV manda el
            // rumbo de la ruta (el mapa se gira con él).
            if (focus != Focus.NAV && fix.speedKmh > 3f && fix.bearingDeg.isFinite()) {
                updateMapMarkerSprite(fix.bearingDeg, carLightsOn)
            }
            // Si el usuario movió el mapa, vuelve a seguir solo.
            if (mapMode == MapMode.FREE && now - lastUserMapTouchMs > 30_000L) {
                mapMode = MapMode.FOLLOWING
                updateRecenterVisibility()
            }
            if (focus == Focus.NAV) {
                val routeBearing = if (routePoints.size >= 2) {
                    projectOnActiveRoute(fix.latitude, fix.longitude)
                        ?.first?.let { routeBearingAt(it) }
                } else {
                    null
                }
                val heading = routeBearing
                    ?: if (fix.speedKmh > 12f) fix.bearingDeg else null
                heading?.let {
                    updateMapMarkerSprite(it, carLightsOn)
                    applyNavigationHeading(it)
                }
            }
            if (mapMode == MapMode.FOLLOWING) {
                centerOnPosition(
                    slat, slon, fix.speedKmh, fix.bearingDeg, now, fix.extrapolated
                )
            }
            map.invalidate()
        } catch (_: Exception) {
        }
    }

    private fun onGpsNoFix() {
        if (isFinishing || isDestroyed) return
        if (lastGpsFix == null) {
            currentSpeedKmh = 0f
            carSignals?.setGpsSpeed(0f)
        }
        if (!uiVisible || !::gpsStateChip.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGpsStateUiMs < 500L) return
        lastGpsStateUiMs = now
        gpsStateChip.visibility = View.VISIBLE
        gpsStateChip.text = "GPS buscando…"
        gpsStateChip.setTextColor(ContextCompat.getColor(this, R.color.amber))
    }

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
        uiVisible = true
        if (!RemoteMapBridge.active) map.onResume()
        goImmersive()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyPrefs()
        setupClock()
        startViewportTileLoop()
        setActiveDock(focus.dockIndex)
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocation()
        }
        syncMediaMode()
        // Si el MCU cambió de fuente mientras estábamos fuera (mando del
        // volante u otra app), la UI se entera al volver.
        twClient?.requestSource()
    }

    override fun onPause() {
        super.onPause()
        uiVisible = false
        stopViewportTileLoop()
        flushOdometer()
        lastOdometerMs = 0L
        map.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // El motor GPS sigue vivo al pausar: si el usuario abre otra app y
        // vuelve, el mapa no arranca de cero ni pierde la posición.
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
        gpsEngine?.stop()
        gpsEngine = null
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
    // Continuous canvas: one map, one dock, smooth Focus cross-fade.
    // En Cortex-A7 el split mapa/panel se aplica en un solo salto de layout
    // (sin mutación por frame) para no forzar el redraw de osmdroid; la
    // sensación profesional viene del cross-fade + deslizamiento suave de los
    // paneles y de las pastillas, con el mismo easing que el sheet de ajustes.
    // ---------------------------------------------------------------------

    private fun panelFor(target: Focus): View = when (target) {
        Focus.COCKPIT -> cockpitPanel
        Focus.NAV -> navOverlay
        Focus.MEDIA -> if (radioSource) radioModule else mediaModule
        Focus.VEHICLE -> vehicleModule
    }

    private fun applyFocus(target: Focus, animate: Boolean = true) {
        val oldFocus = focus
        val oldShown: View? = try {
            panelFor(oldFocus)
        } catch (_: Exception) {
            null
        }
        val oldWasVisible = oldShown?.visibility == View.VISIBLE
        val shown = panelFor(target)
        val changed = target != focus
        focus = target

        // Cámara: en NAV el coche se ancla abajo (~68 % del viewport) para
        // enseñar camino por delante. Fuera de NAV vuelve al centro exacto.
        applyNavCameraFrame(target == Focus.NAV)

        // Movimiento ligero: la dirección depende del dock (izq/der), el split
        // se aplica de una vez para no redibujar osmdroid por frame, y solo se
        // animan alpha/translation (sin capas, bitmaps ni pesos animados).
        val canAnimate = changed && animate && Motion.enabled()
        val direction = when {
            target.dockIndex > oldFocus.dockIndex -> 1f
            target.dockIndex < oldFocus.dockIndex -> -1f
            else -> 0f
        }
        val sameRightPane = oldFocus != Focus.NAV && target != Focus.NAV
        val enterDx = (if (sameRightPane) dp(12) else dp(22)).toFloat()
        val exitDx = enterDx * 0.65f
        // El cross-fade solo es seguro cuando ambos paneles viven en el mismo
        // pane y su ancho no cambia (Música <-> Auto). En COCKPIT/NAV el split
        // cambia y mantener el panel viejo lo estiraría; se oculta de golpe y
        // el nuevo entra con fade para no deformar la tarjeta.
        val canCrossFade = oldShown != null && oldShown !== shown && oldWasVisible &&
            oldFocus != Focus.NAV && target != Focus.NAV &&
            oldFocus != Focus.COCKPIT && target != Focus.COCKPIT

        // Cancela cualquier animación previa y deja los paneles neutrales.
        for (view in transitionPanels) {
            view.animate().cancel()
            Motion.reset(view)
        }

        cockpitPanel.visibility = if (shown === cockpitPanel) View.VISIBLE else View.GONE
        miniHud.visibility = View.GONE
        mediaModule.visibility = if (shown === mediaModule) View.VISIBLE else View.GONE
        radioModule.visibility = if (shown === radioModule) View.VISIBLE else View.GONE
        vehicleModule.visibility = if (shown === vehicleModule) View.VISIBLE else View.GONE
        navOverlay.visibility = if (target == Focus.NAV) View.VISIBLE else View.GONE

        setActiveDock(target.dockIndex, animate = canAnimate)
        // Pesos del Home 32/36/32 (NAV = mapa 100%, módulos = 25/0/75).
        setSplit(target)

        // Altura del mini-mapa: widget 130dp en Inicio, completo en NAV,
        // expandido en Música/Auto (sin la tarjeta de música duplicada).
        try {
            val params = miniMapContainer.layoutParams as? LinearLayout.LayoutParams
            if (params != null) {
                if (target == Focus.COCKPIT) {
                    params.height = 0
                    params.weight = 1f
                } else {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    params.weight = 0f
                }
                miniMapContainer.layoutParams = params
            }
            centerPane.visibility = if (target == Focus.COCKPIT) View.VISIBLE else View.GONE
            rightPane.visibility = if (target == Focus.NAV) View.GONE else View.VISIBLE
            mediaCard.visibility = if (target == Focus.COCKPIT) View.VISIBLE else View.GONE
        } catch (_: Exception) {
        }

        // Pastillas sobre el mapa: solo se anima lo que realmente cambia de
        // visibilidad; las que siguen visibles no parpadean.
        fun setPill(view: View, show: Boolean) {
            val wasVisible = view.visibility == View.VISIBLE
            // cancel() puede ejecutar el endAction anterior; se hace antes de
            // fijar la visibilidad final para no dejar la pastilla oculta.
            view.animate().cancel()
            if (show) {
                view.visibility = View.VISIBLE
                if (!wasVisible && canAnimate) {
                    Motion.fadeIn(view, 220L)
                } else {
                    view.alpha = 1f
                }
            } else {
                if (wasVisible && canAnimate) {
                    Motion.fadeOut(view, 160L) {
                        view.visibility = View.GONE
                    }
                } else {
                    view.visibility = View.GONE
                    view.alpha = 1f
                }
            }
        }
        // Reloj oculto en NAV porque la tarjeta de maniobra lo ocupa.
        setPill(clockBlock, target != Focus.NAV)
        setPill(navPill, target == Focus.MEDIA || target == Focus.VEHICLE)
        // En NAV el resumen inferior ya lleva velocidad/tiempo; el HUD de
        // velocidad se solaparía con la píldora de resumen.
        setPill(hudPill, target == Focus.MEDIA || target == Focus.VEHICLE)
        // La tarjeta de clima grande es solo de Inicio: en el resto de focos
        // el pane izquierdo se estrecha y la tarjeta desbordaría.
        if (::weatherCard.isInitialized) {
            setPill(weatherCard, target == Focus.COCKPIT && Prefs.showWeather(this))
        }

        if (canAnimate) {
            // En Inicio la tarjeta hero sube y se asienta. No se escala para
            // mantener el texto nítido y no rasterizar la tarjeta.
            if (target == Focus.COCKPIT) {
                centerPane.visibility = View.VISIBLE
                Motion.enter(centerPane, 0f, dp(12).toFloat(), 320L, 40L)
            }
            // Cross-fade solo entre paneles del mismo pane y mismo ancho.
            if (canCrossFade) {
                oldShown?.visibility = View.VISIBLE
                oldShown?.bringToFront()
            }
            shown.visibility = View.VISIBLE
            shown.bringToFront()
            if (direction != 0f) {
                Motion.enter(
                    shown,
                    direction * enterDx,
                    0f,
                    280L,
                    fromAlpha = if (canCrossFade) 0f else 0.22f
                )
            } else {
                Motion.fadeIn(shown, 240L)
            }
            if (canCrossFade) {
                val outgoing = oldShown ?: shown
                if (outgoing !== shown) {
                    Motion.exit(outgoing, -direction * exitDx, 0f, 190L) {
                        outgoing.visibility = View.GONE
                    }
                }
            }
        } else {
            Motion.reset(shown)
            if (target == Focus.COCKPIT) {
                Motion.reset(centerPane)
            }
        }

        if (target == Focus.NAV) {
            // Navegación: se vuelve a seguir al coche y se deja de recibir el
            // vídeo del teléfono (el mapa local se ve mejor y gasta menos).
            mapMode = MapMode.FOLLOWING
            lastUserMapTouchMs = 0L
            if (RemoteMapBridge.active) RemoteMapBridge.stop()
            renderNavOverlay()
            lastGpsFix?.let {
                try {
                    centerOnPosition(
                        it.latitude,
                        it.longitude,
                        it.speedKmh,
                        it.bearingDeg,
                        SystemClock.elapsedRealtime(),
                        it.extrapolated
                    )
                } catch (_: Exception) {
                }
            }
            // Al entrar al mapa, mostrar la reproducción actual de inmediato,
            // como hace Android Auto al abrir la vista de mapa.
            val cur = MediaRepository.current
            if (cur != null) {
                lastMediaKey = null
                lastMediaPlaying = null
                showNowPlayingToast(cur)
            }
            // Ya con el mapa medido, pide las teselas del encuadre actual.
            uiHandler.postDelayed({
                try {
                    maybeRequestViewportTiles(true)
                } catch (_: Exception) {
                }
            }, 900L)
        } else {
            resetMapBearing()
            // Fuera de NAV el minimapa también debe seguir al coche. Antes se
            // quedaba en la última posición o en Morelia hasta tocar el mapa.
            mapMode = MapMode.FOLLOWING
            lastUserMapTouchMs = 0L
            lastGpsFix?.let { fix ->
                try {
                    val target = GeoPoint(fix.latitude, fix.longitude)
                    positionMarker?.position = target
                    map.controller.setCenter(target)
                    map.invalidate()
                } catch (_: Exception) {
                }
            }
        }
        updatePerspective()
        updateRecenterVisibility()
        // El mini-mapa de Inicio/Música/Auto necesita las teselas de su
        // encuadre; se piden al entrar al foco sin esperar al bucle de 3 s.
        uiHandler.postDelayed({
            try {
                maybeRequestViewportTiles(true)
            } catch (_: Exception) {
            }
        }, 500L)
    }

    /**
     * Ajusta el encuadre de navegación. `setMapCenterOffset` desplaza el punto
     * que cae en el centro del MapView y también el pivote de rotación, así
     * que el coche queda fijo abajo mientras el mapa gira (heading-up).
     */
    private fun applyNavCameraFrame(nav: Boolean) {
        if (!::map.isInitialized) return
        try {
            // El offset lo calcula PerspectiveMapView con el 79 % de altura
            // (coche bajo, estilo CarPlay) y lo reaplica al cambiar de tamaño.
            perspectiveMap.navFrameEnabled = nav
            if (!nav && navZoomApplied) {
                navZoomApplied = false
                navZoomBucket = 1
                navZoomTarget = MapHelper.DEFAULT_ZOOM
                map.controller.setZoom(MapHelper.DEFAULT_ZOOM)
            }
            map.invalidate()
        } catch (_: Exception) {
        }
    }

    /** Saca puck y coche al frente tras añadir/quitar capas de ruta. */
    private fun bringPuckToFront() {
        if (!::map.isInitialized) return
        try {
            puckOverlay?.let { puck ->
                if (map.overlays.contains(puck)) map.overlays.remove(puck)
                map.overlays.add(puck)
            }
            positionMarker?.let { pm ->
                if (map.overlays.contains(pm)) map.overlays.remove(pm)
                map.overlays.add(pm)
            }
        } catch (_: Exception) {
        }
    }

    /** Muestra u oculta el puck según el foco y la perspectiva. */
    private fun syncMapPuck(nav: Boolean) {
        if (!::map.isInitialized) return
        try {
            val screenSpace = nav && perspectiveActive
            puckOverlay?.visible = nav && !screenSpace
            puckOverlay?.let { puck ->
                if (nav) {
                    if (!map.overlays.contains(puck)) map.overlays.add(puck)
                } else {
                    map.overlays.remove(puck)
                }
            }
            positionMarker?.let { pm ->
                // El coche debe verse también en el minimapa de Inicio,
                // Música y Auto; antes solo se agregaba en NAV y por eso el
                // vehículo desaparecía fuera de la sección de mapas.
                if (!map.overlays.contains(pm)) map.overlays.add(pm)
                // En perspectiva el coche lo pinta PerspectiveNavOverlayView.
                pm.isEnabled = !screenSpace
            }
            destinationMarker?.isEnabled = !screenSpace
            if (nav) bringPuckToFront()
            map.invalidate()
        } catch (_: Exception) {
        }
    }

    /**
     * Activa/desactiva la perspectiva solo en NAV follow. La base y la ruta
     * se inclinan; coche, pin y horizonte se dibujan en pantalla. En pan libre
     * o con el mapa del teléfono se queda plana para no romper el hit-testing.
     */
    private fun updatePerspective() {
        if (!::map.isInitialized || !::perspectiveMap.isInitialized) return
        val nav = focus == Focus.NAV
        val active = nav && mapMode == MapMode.FOLLOWING &&
            !perspectiveTouchDown && !RemoteMapBridge.active
        perspectiveActive = active
        perspectiveMap.navFrameEnabled = nav
        perspectiveMap.pitch = if (active) NAV_PITCH else 0f
        perspectiveOverlay.visibility = if (active) View.VISIBLE else View.GONE
        perspectiveBackdrop.visibility = if (active) View.VISIBLE else View.GONE
        if (active) perspectiveOverlay.invalidate()
        if (!active) perspectiveOverlay.setCarTilt(0f)
        refTilesOverlay?.isEnabled = !active
        syncMapPuck(nav)
        applyMapBaseFilter()
        updateCompass()
    }

    private fun applyMapBaseFilter() {
        if (!::map.isInitialized) return
        try {
            map.overlayManager.tilesOverlay.setColorFilter(
                MapStyles.colorFilter(currentMapStyle, focus == Focus.NAV)
            )
            map.invalidate()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------
    // Estilos de mapa: selector + caché por estilo
    // ------------------------------------------------------------------

    /**
     * Selector rápido del HUD. Muestra cuántas teselas hay cacheadas de cada
     * estilo; el conteo se hace en IO para no bloquear el mapa.
     */
    private fun showMapStyleDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        val subtitles = HashMap<String, TextView>(MapStyles.ALL.size)
        val radios = HashMap<String, RadioButton>(MapStyles.ALL.size)
        var dialogRef: AlertDialog? = null
        for (style in MapStyles.ALL) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = true
                isFocusable = true
            }
            val radio = RadioButton(this).apply {
                isChecked = style.id == currentMapStyle.id
                buttonTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                text = style.label
                textSize = 16f
                setTextColor(Color.parseColor("#E8EEF7"))
                minWidth = dp(190)
                // El clic lo maneja la fila completa: así el radio no se queda
                // marcado sin cambiar el mapa.
                isClickable = false
                isFocusable = false
            }
            radios[style.id] = radio
            val subtitle = TextView(this).apply {
                text = "…"
                textSize = 12f
                setTextColor(Color.parseColor("#8A94A6"))
            }
            subtitles[style.id] = subtitle
            row.addView(
                radio,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            row.addView(
                subtitle,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            row.setOnClickListener {
                radios.values.forEach { it.isChecked = false }
                radio.isChecked = true
                applyMapStyle(style)
                dialogRef?.dismiss()
            }
            content.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(content) }
        val title = TextView(this).apply {
            text = "Estilo de mapa"
            textSize = 20f
            setTextColor(Color.parseColor("#E8EEF7"))
            setPadding(dp(20), dp(18), dp(20), dp(6))
        }
        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(title)
            .setView(scroll)
            .setNegativeButton("Cerrar", null)
            .create()
        dialogRef = dialog
        dialog.show()
        try {
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_sheet)
            dialog.window?.setDimAmount(0.55f)
        } catch (_: Exception) {
        }
        Thread {
            val stats = MapStyles.ALL.map { it to MapStyles.cacheStats(this, it) }
            runOnUiThread {
                for ((style, st) in stats) {
                    val mb = st.bytes / 1048576.0
                    subtitles[style.id]?.text = when {
                        st.tiles == 0 -> "sin caché offline"
                        st.tiles < 250 -> "${st.tiles} teselas · %.1f MB".format(mb)
                        else -> "${st.tiles} teselas · %.1f MB · listo sin conexión".format(mb)
                    }
                }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun applyMapStyle(style: MapStyle) {
        if (style.id == currentMapStyle.id) return
        currentMapStyle = style
        MapStyles.select(this, style)
        try {
            // 1) vaciar RAM + 2) cambiar TileSource (lee de su carpeta en disco)
            MapHelper.clearTileCaches(map)
            refTilesOverlay = MapHelper.applyStyle(this, map, style)
            refTilesOverlay?.isEnabled = !perspectiveActive
            // 3) filtro de color por estilo
            applyMapBaseFilter()
            navZoomApplied = false
            navZoomBucket = 1
            navZoomTarget = MapHelper.DEFAULT_ZOOM
            lastTileReqKey = ""
            map.invalidate()
            // 4) pedir al teléfono lo que falte del nuevo estilo
            uiHandler.postDelayed({
                try {
                    maybeRequestViewportTiles(true)
                } catch (_: Exception) {
                }
            }, 350L)
            Toast.makeText(
                this,
                "Mapa: ${style.shortLabel}${if (NavLink.wifiActive()) "" else " · solo caché"}",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo cambiar el mapa", Toast.LENGTH_SHORT).show()
            Log.w("MapStyle", "apply: ${e.message}")
        }
    }

    private fun updateVoiceButton() {
        if (!::btnVoice.isInitialized) return
        val on = Prefs.voiceOn(this)
        btnVoice.setImageResource(if (on) R.drawable.ic_volume else R.drawable.ic_volume_off)
        btnVoice.alpha = if (on) 1f else 0.62f
    }

    /** Brújula: gira con el mapa; en norte-arriba se queda recta. */
    private fun updateCompass() {
        if (!::btnCompass.isInitialized) return
        try {
            btnCompass.rotation = if (navNorthUp) 0f else map.mapOrientation
        } catch (_: Exception) {
        }
        btnCompass.alpha = if (navNorthUp) 1f else 0.72f
    }

    // ------------------------------------------------------------------
    // Zoom dinámico por velocidad (17 urbano · 16 carretera · 15 autopista)
    // ------------------------------------------------------------------

    /**
     * Histéresis por bucket para que el zoom no oscile cuando la velocidad
     * baila alrededor del umbral. z17 solo con enlace activo: el pack offline
     * llega hasta z16 y sin teléfono el mapa se vería vacío.
     */
    private fun updateNavZoomTarget(speedKmh: Float) {
        if (focus != Focus.NAV) return
        val link = NavLink.wifiActive()
        navZoomBucket = when (navZoomBucket) {
            0 -> if (speedKmh > 32f) 1 else 0
            2 -> if (speedKmh < 62f) 1 else 2
            else -> when {
                speedKmh < 18f -> if (link) 0 else 1
                speedKmh > 76f -> 2
                else -> 1
            }
        }
        navZoomTarget = when (navZoomBucket) {
            0 -> if (link && currentMapStyle.maxZoom >= 17) 17.0 else 16.0
            1 -> 16.0
            else -> 15.0
        }.coerceAtMost(currentMapStyle.maxZoom.toDouble())
    }

    /** Acerca el zoom poco a poco en cada frame de seguimiento. */
    private fun stepNavZoom() {
        if (focus != Focus.NAV || mapMode != MapMode.FOLLOWING) return
        if (!::map.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastZoomStepMs < 320L) return
        val current = try {
            map.zoomLevelDouble
        } catch (_: Exception) {
            return
        }
        val delta = navZoomTarget - current
        if (Math.abs(delta) < 0.08) return
        // No acercar a z17 si aún no están las teselas: osmdroid no rellena
        // con z16, así que se verían huecos. Se espera a que el teléfono las
        // mande (la petición las pide del centro hacia afuera).
        if (delta > 0 && !navZoomTilesReady(Math.ceil(navZoomTarget).toInt())) return
        lastZoomStepMs = now
        navZoomApplied = true
        try {
            map.controller.setZoom(current + delta * 0.22)
        } catch (_: Exception) {
        }
    }

    /**
     * ¿Hay teselas del zoom pedido para el encuadre? Se comprueba como mucho
     * cada 1.5 s y basta el 85 %: las que faltan en el borde llegan después.
     */
    private fun navZoomTilesReady(zoom: Int): Boolean {
        if (zoom <= 16) return true
        val now = SystemClock.elapsedRealtime()
        if (now - lastZoomReadyCheckMs < 1500L) return lastZoomReady
        lastZoomReadyCheckMs = now
        lastZoomReady = try {
            // Se comprueba el encuadre exacto que se verá al entrar en z17.
            // El prefetch pide un poco más de margen, pero no hace falta
            // esperar a los bordes para acercar.
            val range = viewportTileRange(zoom, ZOOM_READY_SHRINK) ?: return false
            val root = File(
                getExternalFilesDir(null) ?: filesDir,
                "osmdroid/tiles/${currentMapStyle.folder}/$zoom"
            )
            var total = 0
            var have = 0
            for (x in range[0]..range[2]) {
                for (y in range[1]..range[3]) {
                    total++
                    if (File(root, "$x/$y.tile").isFile) have++
                }
            }
            total in 1..80 && have * 100 / total >= 85
        } catch (_: Exception) {
            false
        }
        return lastZoomReady
    }

    /**
     * Rango de teselas del encuadre actual. [shrink] recorta el bbox hacia el
     * centro (1 = todo, 0.62 = lo que se verá al subir un zoom).
     */
    private fun viewportTileRange(zoom: Int, shrink: Float): IntArray? {
        if (!::map.isInitialized || map.width <= 0 || map.height <= 0) return null
        val box = try {
            map.boundingBox
        } catch (_: Exception) {
            return null
        }
        val latC = (box.latNorth + box.latSouth) / 2.0
        val lonC = (box.lonEast + box.lonWest) / 2.0
        // En 2.5D la cámara inclinada enseña más carretera por delante: se
        // pide un encuadre más alto para no dejar huecos en el horizonte.
        val latBoost = if (perspectiveActive) 1.5 else 1.0
        val lonBoost = if (perspectiveActive) 1.15 else 1.0
        val halfLat = (box.latNorth - box.latSouth) / 2.0 * shrink * latBoost
        val halfLon = (box.lonEast - box.lonWest) / 2.0 * shrink * lonBoost
        return intArrayOf(
            MapHelper.lonToTileX(lonC - halfLon, zoom),
            MapHelper.latToTileY(latC + halfLat, zoom),
            MapHelper.lonToTileX(lonC + halfLon, zoom),
            MapHelper.latToTileY(latC - halfLat, zoom)
        )
    }

    // ------------------------------------------------------------------
    // Teselas del viewport: el estéreo pide lo que está viendo
    // ------------------------------------------------------------------

    private fun startViewportTileLoop() {
        if (tileReqLoop != null) return
        val tick = object : Runnable {
            override fun run() {
                try {
                    maybeRequestViewportTiles(false)
                } catch (_: Exception) {
                }
                uiHandler.postDelayed(this, 3000L)
            }
        }
        tileReqLoop = tick
        uiHandler.postDelayed(tick, 1500L)
    }

    private fun stopViewportTileLoop() {
        tileReqLoop?.let { uiHandler.removeCallbacks(it) }
        tileReqLoop = null
    }

    /**
     * Pide al teléfono las teselas visibles (base y etiquetas). Antes solo se
     * empujaban las del corredor de la ruta: explorar libremente dejaba huecos.
     * La firma evita repetir el mismo pedido cada 3 s.
     */
    private fun maybeRequestViewportTiles(force: Boolean) {
        // El mapa es el mismo en Inicio, Música, Auto y NAV: si el mini-mapa
        // cambia de estilo o de encuadre, también hay que pedir sus teselas.
        if (RemoteMapBridge.active) return
        if (!::map.isInitialized || map.width <= 0 || map.height <= 0) return
        if (!NavLink.wifiActive()) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastTileReqMs < 2500L) return
        val shown = try {
            map.zoomLevelDouble.roundToInt()
                .coerceIn(13, currentMapStyle.maxZoom)
        } catch (_: Exception) {
            return
        }
        // Si la cámara va a subir de zoom, se prefetchea ya el centro a ese
        // nivel. Así al acercarse no se ven huecos (osmdroid no rellena con
        // teselas de z16).
        val target = Math.ceil(navZoomTarget).toInt()
            .coerceIn(13, currentMapStyle.maxZoom)
        val zoom = if (focus == Focus.NAV && target > shown) target else shown
        val shrink = if (zoom > shown) ZOOM_PREFETCH_SHRINK else 1f
        val range = viewportTileRange(zoom, shrink) ?: return
        val x0 = range[0]
        val y0 = range[1]
        val x1 = range[2]
        val y1 = range[3]
        if (x1 < x0 || y1 < y0) return
        val count = (x1 - x0 + 1) * (y1 - y0 + 1)
        if (count <= 0 || count > 64) return
        val key = "$zoom:$x0:$y0:$x1:$y1"
        if (!force && key == lastTileReqKey && now - lastTileReqMs < 30_000L) return
        lastTileReqKey = key
        lastTileReqMs = now
        NavLink.sendAsync(
            org.json.JSONObject()
                .put("type", "TILE_REQ")
                .put("layer", "base")
                .put("style", currentMapStyle.id)
                .put("z", zoom)
                .put("x0", x0).put("y0", y0)
                .put("x1", x1).put("y1", y1)
        )
        // Las etiquetas se piden solo para el zoom visible; en el prefetch
        // llegarían antes de tiempo y gastarían enlace. Solo Esri tiene capa
        // `ref`: Carto/Waze traen (o resuelven con globos) sus etiquetas.
        if (currentMapStyle.hasRef && !perspectiveActive &&
            zoom >= 15 && zoom == shown
        ) {
            NavLink.sendAsync(
                org.json.JSONObject()
                    .put("type", "TILE_REQ")
                    .put("layer", "ref")
                    .put("style", currentMapStyle.id)
                    .put("z", zoom)
                    .put("x0", x0).put("y0", y0)
                    .put("x1", x1).put("y1", y1)
            )
        }
    }

    private fun setSplit(target: Focus) {
        // Home: mapa grande 40% (útil de verdad) + centro 30% + derecha 30%.
        // NAV = mapa completo; Música/Auto = mapa 25% + panel 75%.
        val (lw, cw, rw) = when (target) {
            Focus.COCKPIT -> Triple(0.40f, 0.30f, 0.30f)
            Focus.NAV -> Triple(1.00f, 0.0f, 0.0f)
            Focus.MEDIA, Focus.VEHICLE -> Triple(0.25f, 0.0f, 0.75f)
        }
        val lp = leftPane.layoutParams as LinearLayout.LayoutParams
        lp.weight = lw
        leftPane.layoutParams = lp
        val cp = centerPane.layoutParams as LinearLayout.LayoutParams
        cp.weight = cw
        centerPane.layoutParams = cp
        val rp = rightPane.layoutParams as LinearLayout.LayoutParams
        rp.weight = rw
        rightPane.layoutParams = rp
        splitFraction = lw
    }

    // ---------------------------------------------------------------------
    // Navegación: seguimiento del vehículo y tarjeta de maniobra
    // ---------------------------------------------------------------------

    /**
     * El usuario movió el mapa a mano: se deja de seguir hasta que pulse
     * "Centrar" o haya un avance real. Un solo toque (toque rápido) NO debe
     * detener el seguimiento: antes cualquier ACTION_DOWN ponía FREE y el
     * coche parecía quedarse pegado hasta pasados 10 s.
     */
    private fun markMapFree() {
        lastUserMapTouchMs = SystemClock.elapsedRealtime()
        if (mapMode != MapMode.FREE) {
            mapMode = MapMode.FREE
            updateRecenterVisibility()
            updatePerspective()
            // Exploración libre: pide de inmediato las teselas de lo que se
            // está viendo (el corredor de ruta no cubre estas zonas).
            uiHandler.postDelayed({
                try {
                    maybeRequestViewportTiles(true)
                } catch (_: Exception) {
                }
            }, 600L)
        }
    }

    private fun updateRecenterVisibility() {
        if (!::btnRecenter.isInitialized) return
        val show = mapMode == MapMode.FREE && !RemoteMapBridge.active
        btnRecenter.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Chip de diagnóstico GPS: distingue fix vivo, estimado y pérdida real.
     * La edad y los satélites vienen del [GpsEngine], no de un proxy.
     */
    private fun updateGpsStateChip(fix: GpsFix) {
        if (!::gpsStateChip.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastGpsStateUiMs < 250L) return
        lastGpsStateUiMs = now
        val ageS = (fix.ageMs / 1000L).coerceAtLeast(0L)
        val label = when {
            fix.lost -> "GPS sin señal"
            fix.extrapolated -> "GPS estimado ${ageS}s"
            fix.accuracyM > 25f -> "GPS impreciso"
            fix.accuracyM <= 8f -> "GPS en vivo"
            else -> "GPS bueno"
        }
        val sats = if (fix.satellites > 0) "${fix.satellites} sat" else "sin sat"
        // Solo se muestra cuando hay algo que decir (estimado, impreciso o
        // perdido). Un chip permanente tapaba el mapa pequeño en AUTO.
        gpsStateChip.visibility =
            if (fix.lost || fix.extrapolated || fix.accuracyM > 25f) View.VISIBLE else View.GONE
        gpsStateChip.text = "$label · $sats"
        gpsStateChip.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    fix.lost -> R.color.danger
                    fix.extrapolated || fix.accuracyM > 25f -> R.color.amber
                    else -> R.color.accent_sky
                }
            )
        )
    }

    /**
     * Centra el mapa en el coche. En navegación se orienta hacia el rumbo
     * (heading-up); en el resto, norte arriba. Con posición estimada se
     * actualiza directo (sin animación) para que la cámara no vaya un paso
     * atrás entre fixes reales.
     */
    private fun centerOnPosition(
        lat: Double,
        lon: Double,
        speedKmh: Float,
        bearingDeg: Float,
        now: Long,
        predicted: Boolean
    ) {
        if (perspectiveTouchDown) return
        val target = GeoPoint(lat, lon)
        if (focus == Focus.NAV) {
            // Rumbo estable desde la geometría de la ruta; el rumbo GPS solo
            // se usa cuando no hay ruta proyectable.
            val routeBearing = if (routePoints.size >= 2) {
                projectOnActiveRoute(lat, lon)?.first?.let { routeBearingAt(it) }
            } else {
                null
            }
            val bearing = routeBearing
                ?: if (speedKmh > 12f && bearingDeg.isFinite()) bearingDeg else 0f
            animateMapBearing(bearing)
            updateNavZoomTarget(speedKmh)
            if (predicted) {
                followPoint = target
                try {
                    positionMarker?.position = target
                    if (mapMode == MapMode.FOLLOWING) map.controller.setCenter(target)
                    stepNavZoom()
                    map.invalidate()
                } catch (_: Exception) {
                }
            } else {
                animateFollow(target, speedKmh)
            }
        } else {
            resetMapBearing()
            if (predicted) {
                followPoint = target
                try {
                    positionMarker?.position = target
                    if (mapMode == MapMode.FOLLOWING) map.controller.setCenter(target)
                    map.invalidate()
                } catch (_: Exception) {
                }
            } else if (now - lastFollowCenterMs > 600L) {
                lastFollowCenterMs = now
                try {
                    followPoint = target
                    map.controller.animateTo(target, map.zoomLevelDouble, 500L)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun animateFollow(target: GeoPoint, speedKmh: Float) {
        val from = followPoint ?: positionMarker?.position ?: target
        // Primer fix o salto grande: recolocar sin animación.
        val distance = try {
            val out = FloatArray(1)
            android.location.Location.distanceBetween(
                from.latitude, from.longitude,
                target.latitude, target.longitude,
                out
            )
            out[0].toDouble()
        } catch (_: Exception) {
            Double.MAX_VALUE
        }
        if (distance > 300.0) {
            followPoint = target
            try {
                positionMarker?.position = target
                if (mapMode == MapMode.FOLLOWING) map.controller.setCenter(target)
                stepNavZoom()
                map.invalidate()
            } catch (_: Exception) {
            }
            return
        }
        followAnimator?.cancel()
        followAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (speedKmh > 2f) 900L else 350L
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val lat = from.latitude + (target.latitude - from.latitude) * t
                val lon = from.longitude + (target.longitude - from.longitude) * t
                val p = GeoPoint(lat, lon)
                try {
                    positionMarker?.position = p
                    if (mapMode == MapMode.FOLLOWING) map.controller.setCenter(p)
                    stepNavZoom()
                    map.invalidate()
                } catch (_: Exception) {
                }
            }
            start()
        }
        followPoint = target
    }

    /** Mantiene la vista heading-up: el rumbo del coche queda arriba. */
    private fun applyNavigationHeading(bearing: Float) {
        if (focus != Focus.NAV || RemoteMapBridge.active || navNorthUp) return
        val target = ((bearing % 360f) + 360f) % 360f
        animateMapBearing(target)
    }

    /** Gira el mapa suavemente hacia el rumbo, por el camino más corto. */
    private fun animateMapBearing(targetBearing: Float) {
        if (navNorthUp) return
        val from = currentBearingDeg
        var delta = ((targetBearing - from + 540f) % 360f) - 180f
        // Los cambios continuos son los que hacen que el mapa “acompañe” el
        // avance: un umbral de 10° dejaba la cámara quieta en curvas suaves.
        if (Math.abs(delta) < 2f) return
        bearingAnimator?.cancel()
        val to = from + delta
        currentBearingDeg = to
        val duration = (Math.abs(delta) * 12f).toLong().coerceIn(280L, 680L)
        // Micro-rotación del coche: máxima al entrar a la curva y decae con
        // la misma animación del mapa (±12°).
        val carTilt = (delta * 0.30f).coerceIn(-12f, 12f)
        bearingAnimator = android.animation.ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                try {
                    val value = it.animatedValue as Float
                    map.mapOrientation = -value
                    val frac = if (to != from) (value - from) / (to - from) else 1f
                    if (::perspectiveOverlay.isInitialized) {
                        perspectiveOverlay.setCarTilt(carTilt * (1f - frac))
                    }
                } catch (_: Exception) {
                }
                updateCompass()
            }
            start()
        }
    }

    private fun resetMapBearing() {
        if (currentBearingDeg == 0f && map.mapOrientation == 0f) {
            updateCompass()
            return
        }
        bearingAnimator?.cancel()
        bearingAnimator = null
        currentBearingDeg = 0f
        try {
            map.mapOrientation = 0f
        } catch (_: Exception) {
        }
        updateMapMarkerSprite(lastDisplayBearing, carLightsOn)
        updateCompass()
    }

    /** Pinta la tarjeta flotante de navegación con el estado actual. */
    private fun renderNavOverlay() {
        if (!::navCard.isInitialized) return
        val st = lastNavState
        val route = NavRouteRepository.current
        val hasRoute = st != null

        navCardProgress.visibility = if (hasRoute) View.VISIBLE else View.GONE
        navFinish.visibility = if (hasRoute) View.VISIBLE else View.GONE
        navSummaryPill.visibility = if (hasRoute) View.VISIBLE else View.GONE

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
            navCardThen.text = ""
            navCardThenRow.visibility = View.GONE
            navCardEta.visibility = View.GONE
            navCardRemaining.text = ""
            navCardDest.text = ""
            navCardProgress.progress = 0
            navSummaryText.text = "Planifica una ruta desde IbizaCompanion"
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
        navCardThenRow.visibility = if (then == null) View.GONE else View.VISIBLE
        navCardThenIcon.setImageResource(
            (s.thenIcon ?: s.icon).resId
        )
        navCardEta.text = s.etaText
        navCardEta.visibility = if (s.etaText.isNotBlank()) View.VISIBLE else View.GONE
        val remaining = remainingDistanceOnly(s.remainingText)
        navCardRemaining.text = remaining
        // Sin maniobra próxima el número grande ya es el restante: no repetirlo.
        navCardRemaining.visibility =
            if (remaining.isBlank() || remaining == s.distText) View.GONE else View.VISIBLE
        navCardDest.text = s.destName
        navCardProgress.progress = (s.progress.coerceIn(0f, 1f) * 1000f).toInt()
        navSummaryText.text = tripSummary(s, remaining)
    }

    /** "8 min · 3.2 km · llega 18:42" para la píldora inferior. */
    private fun tripSummary(s: NavState, remaining: String): String {
        val parts = ArrayList<String>(3)
        if (s.etaText.isNotBlank()) parts.add(s.etaText)
        if (remaining.isNotBlank()) parts.add(remaining)
        val etaMin = Regex("([0-9]+)").find(s.etaText)?.groupValues?.get(1)?.toIntOrNull()
        if (etaMin != null && etaMin in 0..600) {
            val arrival = System.currentTimeMillis() + etaMin * 60_000L
            parts.add("llega " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(arrival)))
        }
        return parts.joinToString(" · ")
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
                } else {
                    // Teselas por estilo: cada mapa tiene su propia carpeta.
                    var total = 0
                    var currentTiles = 0
                    var currentBytes = 0L
                    for (style in MapStyles.ALL) {
                        val st = MapStyles.cacheStats(this, style)
                        total += st.tiles
                        if (style.id == currentMapStyle.id) {
                            currentTiles = st.tiles
                            currentBytes = st.bytes
                        }
                    }
                    if (currentTiles > 0) {
                        val mb = currentBytes / 1048576.0
                        text = "%s · %d teselas · %.1f MB".format(
                            currentMapStyle.shortLabel, currentTiles, mb
                        )
                        colorRes = R.color.green
                    } else if (total > 0) {
                        text = "otros mapas · $total teselas"
                        colorRes = R.color.amber
                    }
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
                homeStatus.text = "IbizaDash activo"
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
        // Sin overlay de atenuación con tema nativo.
        nightDim.visibility = View.GONE
        swNight.setOnCheckedChangeListener { _, checked ->
            applyTheme(checked)
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
        quickSheetOpen = true
        // Cancelar primero: un endAction de cierre anterior podría ocultar el
        // sheet; después se fuerza visible.
        sheetPanel.animate().cancel()
        sheetScrim.animate().cancel()
        quickSheet.visibility = View.VISIBLE
        // Evita el flash del primer frame: el panel permanece invisible hasta
        // que se conoce su ancho y arranca la entrada desde el borde derecho.
        sheetPanel.alpha = 0f
        sheetPanel.translationX = dp(24).toFloat()
        refreshLinkStatus()
        if (!Motion.enabled()) {
            sheetScrim.alpha = 1f
            sheetPanel.alpha = 1f
            sheetPanel.translationX = 0f
            return
        }
        Motion.fadeIn(sheetScrim, 200L)
        sheetPanel.post {
            if (!quickSheetOpen || quickSheet.visibility != View.VISIBLE) return@post
            Motion.enter(
                sheetPanel,
                sheetPanel.width.toFloat() + dp(24).toFloat(),
                0f,
                260L
            )
        }
    }

    private fun closeQuickSettings() {
        quickSheetOpen = false
        uiHandler.removeCallbacks(linkStatusRunnable)
        sheetPanel.animate().cancel()
        sheetScrim.animate().cancel()
        if (!Motion.enabled()) {
            quickSheet.visibility = View.GONE
            sheetScrim.alpha = 1f
            Motion.reset(sheetPanel)
            return
        }
        Motion.exit(
            sheetPanel,
            sheetPanel.width.toFloat() + dp(24).toFloat(),
            0f,
            200L
        ) {
            quickSheet.visibility = View.GONE
            Motion.reset(sheetPanel)
        }
        Motion.fadeOut(sheetScrim, 160L)
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
