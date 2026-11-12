package com.chronosbvrx.ibizacompanion.feature.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.chronosbvrx.ibizacompanion.BridgeLink
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import com.chronosbvrx.ibizacompanion.data.CompanionPrefs
import com.chronosbvrx.ibizacompanion.data.PlaceResult
import com.chronosbvrx.ibizacompanion.data.PlaceSearch
import com.chronosbvrx.ibizacompanion.data.TileSender
import com.chronosbvrx.ibizacompanion.map.MapFrameSender
import com.chronosbvrx.ibizacompanion.map.MapStyleCatalog
import com.chronosbvrx.ibizacompanion.nav.PhoneNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class RoutePreview(
    val destination: PlaceResult,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val encodedPolyline: String,
    val steps: List<JSONObject>,
    val trafficDelaySeconds: Int = 0,
    val trafficSections: List<com.chronosbvrx.ibizacompanion.data.TomTomRouting.TrafficSection> = emptyList()
)

private const val MAP_HTML = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<link rel="stylesheet" href="leaflet.css"/>
<style>
  html,body { height:100%; width:100%; margin:0; background:#080D14; }
  #map { position:absolute; top:0; right:0; bottom:0; left:0; }
  .leaflet-control-attribution { font-size:9px; background:rgba(8,13,20,.7); color:#91A2B5; }
  .leaflet-control-attribution a { color:#42C8E8; }
  .car-icon { background:transparent; border:none; }
</style>
</head>
<body>
<div id="map"></div>
<script src="leaflet.js"></script>
<script>
  var map = L.map('map', { zoomControl:false, attributionControl:true }).setView([19.7060,-101.1950], 15);
  var STYLES = {
    esri_dark: {
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      attr: 'Esri, HERE, Garmin, © OpenStreetMap',
      max: 16
    },
    carto_dark: {
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      attr: 'Esri, HERE, Garmin, © OpenStreetMap',
      filter: 'saturate(1.15) hue-rotate(-18deg) brightness(1.08) contrast(1.1)',
      max: 16
    },
    waze_night: {
      url: 'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
      attr: 'Esri, HERE, Garmin, © OpenStreetMap',
      filter: 'saturate(1.15) hue-rotate(-8deg) brightness(1.02)',
      max: 16
    }
  };
  var tiles = null;
  // Cambia el estilo base sin recrear el mapa (la misma paleta que el estéreo).
  function setStyle(id) {
    var s = STYLES[id] || STYLES.esri_dark;
    if (tiles) map.removeLayer(tiles);
    tiles = L.tileLayer(s.url, {
      maxZoom: 19,
      maxNativeZoom: s.max || 19,
      attribution: s.attr
    });
    tiles.on('tileload', function(e){ console.log('tileload ' + e.tile.src); });
    tiles.on('tileerror', function(e){ console.log('tileerror ' + e.tile.src); });
    tiles.addTo(map);
    if (tiles.getContainer()) {
      tiles.getContainer().style.filter = s.filter || 'none';
    }
    if (offlineBase && id === 'esri_dark') {
      if (!map.hasLayer(offline)) { offline.addTo(map); }
      offline.bringToFront();
    } else if (offlineBase && map.hasLayer(offline)) {
      map.removeLayer(offline);
    }
    if (routeLine) routeLine.bringToFront();
    if (me) me.bringToFront();
    console.log('style ' + id);
  }
  // Offline-first layer: file:// PNG tree pushed by scripts/build_offline_pack.py.
  // If local tiles miss repeatedly, it removes itself and the online layer shows.
  var offlineBase = null, offlineMisses = 0, offlineHits = 0;
  var offline = L.tileLayer('', { maxZoom: 19, maxNativeZoom: 15, attribution: '' });
  offline.on('tileerror', function(e){
    offlineMisses++;
    if (offlineMisses > 14 && map.hasLayer(offline)) {
      map.removeLayer(offline);
      console.log('offline_fallback');
    }
  });
  offline.on('tileload', function(e){ offlineMisses = 0; offlineHits++; });
  function setOfflineBase(base) {
    offlineBase = base;
    offlineMisses = 0; offlineHits = 0;
    offline.setUrl(base + '/{z}/{x}/{y}.png');
    if (!map.hasLayer(offline)) { offline.addTo(map); offline.bringToFront(); }
    if (routeLine) routeLine.bringToFront();
    if (me) me.bringToFront();
    console.log('offline_on ' + base);
  }
  setTimeout(function(){ console.log('mapsize ' + map.getSize().x + 'x' + map.getSize().y + ' zoom ' + map.getZoom()); }, 1000);
  // La WebView puede medirse antes de que el Scaffold termine de asentar el
  // teclado/insets. Recalcular el tamaño evita que el mapa quede a media altura.
  window.addEventListener('resize', function(){ setTimeout(function(){ map.invalidateSize(); }, 120); });
  setTimeout(function(){ map.invalidateSize(); }, 700);
  setTimeout(function(){ map.invalidateSize(); }, 1600);
  var me = null, routeLine = null, carBearing = 0;
  // Coche visto desde arriba con morro blanco: la rotación del grupo indica
  // el rumbo real (0 = norte). Es un SVG para no depender de imágenes extra.
  var CAR_SVG = '<svg width="56" height="56" viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">'
    + '<circle cx="32" cy="32" r="30" fill="#0B0F17" fill-opacity="0.92" stroke="#42C8E8" stroke-width="2"/>'
    + '<g id="carRot">'
    + '<rect x="24" y="12" width="16" height="40" rx="8" fill="#42C8E8"/>'
    + '<rect x="26.5" y="17" width="11" height="9" rx="3" fill="#0B0F17"/>'
    + '<rect x="26.5" y="39" width="11" height="7" rx="2.5" fill="#0B0F17"/>'
    + '<path d="M32 5 L37.5 13 L26.5 13 Z" fill="#F8FAFC"/>'
    + '</g></svg>';
  function setCurrent(lat, lon, bearing, center) {
    if (bearing !== undefined && bearing !== null && isFinite(bearing)) {
      carBearing = bearing;
    }
    if (!me) {
      me = L.marker([lat, lon], {
        icon: L.divIcon({
          className: 'car-icon',
          html: CAR_SVG,
          iconSize: [56, 56],
          iconAnchor: [28, 28]
        }),
        interactive: false,
        keyboard: false
      }).addTo(map);
    } else {
      me.setLatLng([lat, lon]);
    }
    var rot = document.getElementById('carRot');
    if (rot) rot.setAttribute('transform', 'rotate(' + carBearing + ' 32 32)');
    if (center) map.setView([lat, lon], Math.max(map.getZoom(), 16));
  }
  function setRoute(points) {
    if (routeLine) map.removeLayer(routeLine);
    routeLine = L.polyline(points, { color:'#42C8E8', weight:7, opacity:0.95 }).addTo(map);
    if (me) me.bringToFront();
    map.invalidateSize();
    map.fitBounds(routeLine.getBounds(), { padding:[60,60] });
  }
  function clearRoute() {
    if (routeLine) { map.removeLayer(routeLine); routeLine = null; }
  }
  function center(lat, lon) { map.setView([lat, lon], 16); map.invalidateSize(); }
  setStyle('esri_dark');
</script>
</body>
</html>
"""

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val appState by CompanionState.state.collectAsState()
    val followState = rememberUpdatedState(appState.routeActive)

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var route by remember { mutableStateOf<RoutePreview?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var sendingTiles by remember { mutableStateOf(false) }
    var tileProgress by remember { mutableStateOf("") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var currentLat by remember { mutableStateOf(19.7060) }
    var currentLon by remember { mutableStateOf(-101.1950) }
    var currentBearing by remember { mutableStateOf(0f) }
    var centeredOnce by remember { mutableStateOf(false) }
    var hasFix by remember { mutableStateOf(false) }
    var mapStyleId by remember { mutableStateOf(CompanionPrefs.mapStyle(context)) }

    val hasLocationPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Location updates -> Leaflet marker.
    DisposableEffect(hasLocationPermission) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val prevLat = currentLat
                val prevLon = currentLon
                currentLat = location.latitude
                currentLon = location.longitude
                // Rumbo: el que publica el GPS; si no lo trae, se deduce del
                // desplazamiento entre fixes. Se mantiene el último cuando el
                // coche está parado y se suaviza para que no vibre.
                val moved = if (hasFix) {
                    val out = FloatArray(1)
                    Location.distanceBetween(
                        prevLat, prevLon, location.latitude, location.longitude, out
                    )
                    out[0].toDouble()
                } else {
                    0.0
                }
                val rawBearing = when {
                    location.hasBearing() && location.speed > 1f -> location.bearing
                    moved > 3.0 -> bearingBetween(
                        prevLat, prevLon, location.latitude, location.longitude
                    )
                    else -> currentBearing
                }
                currentBearing = smoothBearing(currentBearing, rawBearing)
                hasFix = true
                val center = !centeredOnce || followState.value
                centeredOnce = true
                webView?.post {
                    webView?.evaluateJavascript(
                        "setCurrent(${location.latitude},${location.longitude},${currentBearing},$center)",
                        null
                    )
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        if (hasLocationPermission && lm != null) {
            try {
                val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (last != null) {
                    currentLat = last.latitude
                    currentLon = last.longitude
                }
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener)
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 20f, listener)
            } catch (_: Exception) {
            }
        }
        onDispose {
            try {
                lm?.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
    }

    fun centerOnMe() {
        val view = webView
        if (view == null) {
            status = "El mapa todavía se está cargando"
            return
        }
        if (!hasLocationPermission) {
            status = "Activa el permiso de ubicación para centrar el mapa"
            return
        }
        view.evaluateJavascript("center($currentLat,$currentLon)", null)
    }

    fun drawRouteFromPolyline(encoded: String) {
        val points = decodePolyline(encoded)
        if (points.isEmpty()) return
        val js = points.joinToString(",") { "[${it.first},${it.second}]" }
        webView?.evaluateJavascript("setRoute([$js])", null)
    }

    fun calculateRoute(dest: PlaceResult) {
        val fromLat = currentLat
        val fromLon = currentLon
        Log.d("Nav", "calculateRoute ${dest.displayName} from=$fromLat,$fromLon to=${dest.lat},${dest.lon}")
        scope.launch {
            status = "Calculando ruta…"
            val built = withContext(Dispatchers.IO) {
                com.chronosbvrx.ibizacompanion.data.RouteBuilder.build(
                    context, fromLat, fromLon, dest.lat, dest.lon, dest.displayName
                )
            }
            if (built == null) {
                status = "No se pudo calcular la ruta"
                return@launch
            }
            val preview = RoutePreview(
                dest, built.distanceMeters, built.durationSeconds,
                built.encodedPolyline, built.steps,
                built.trafficDelaySeconds, built.trafficSections
            )
            route = preview
            status = null
            showSheet = true
            drawRouteFromPolyline(built.encodedPolyline)
        }
    }

    fun clearRouteOnStereo() {
        CompanionState.clearRoute()
        PhoneNavController.clear()
        MapFrameSender.stop()
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    BridgeLink.clearRoute()
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Envía las teselas del corredor cuando el enlace es Wi-Fi. */
    fun sendRouteTiles(preview: RoutePreview) {
        if (sendingTiles) return
        if (!BridgeLink.isWifiActive()) {
            status = "Conecta por Wi-Fi Direct para enviar el mapa"
            return
        }
        sendingTiles = true
        tileProgress = "0/0"
        status = "Preparando mapa del recorrido…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                TileSender.sendRouteTiles(
                    context,
                    preview.encodedPolyline,
                    mapStyleId
                ) { sent, total ->
                    if (sent == total || sent % 8 == 0) {
                        mainHandler.post {
                            tileProgress = "$sent/$total"
                            status = "Enviando mapa $sent/$total…"
                        }
                    }
                }
            }
            sendingTiles = false
            status = when {
                result.total == 0 -> "Sin Wi-Fi activo: no se envió el mapa"
                result.sent == 0 -> "No se pudo descargar el mapa del recorrido"
                else -> "Mapa ${MapStyleCatalog.byId(mapStyleId).label}: " +
                    "${result.sent}/${result.total} teselas"
            }
        }
    }

    /**
     * Descarga z15–z17 del estilo elegido: corredor de la ruta activa si hay
     * una, o un radio de 2.5 km alrededor de la posición actual.
     */
    fun downloadZone() {
        if (sendingTiles) return
        if (!BridgeLink.isWifiActive()) {
            status = "Conecta por Wi-Fi Direct para descargar el mapa"
            return
        }
        sendingTiles = true
        tileProgress = "0/0"
        status = "Descargando zona z15–z17…"
        val polyline = appState.routePolyline
        val fromRoute = appState.routeActive && polyline.isNotBlank()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val progress: (Int, Int) -> Unit = { sent, total ->
                    if (sent == total || sent % 8 == 0) {
                        mainHandler.post {
                            tileProgress = "$sent/$total"
                            status = "Descargando $sent/$total…"
                        }
                    }
                }
                if (fromRoute) {
                    TileSender.sendRouteTiles(
                        context,
                        polyline,
                        mapStyleId,
                        intArrayOf(15, 16, 17),
                        progress
                    )
                } else {
                    TileSender.sendAreaTiles(
                        context,
                        currentLat,
                        currentLon,
                        mapStyleId,
                        2.5,
                        intArrayOf(15, 16, 17),
                        progress
                    )
                }
            }
            sendingTiles = false
            status = when {
                result.total == 0 -> "Sin Wi-Fi activo: no se descargó nada"
                result.sent == 0 -> "No se pudo descargar la zona"
                else -> "Zona lista: ${result.sent}/${result.total} teselas " +
                    "(${MapStyleCatalog.byId(mapStyleId).label})"
            }
        }
    }

    fun sendRoute() {
        val preview = route ?: return
        scope.launch {
            status = "Enviando al IbizaDash…"
            val ok = withContext(Dispatchers.IO) {
                BridgeLink.sendRouteSet(
                    preview.destination.displayName,
                    preview.distanceMeters,
                    preview.durationSeconds,
                    preview.encodedPolyline,
                    preview.steps,
                    preview.trafficDelaySeconds,
                    preview.trafficSections
                )
            }
            if (ok) {
                status = "Ruta enviada al IbizaDash"
                CompanionState.update {
                    it.copy(
                        routeActive = true,
                        routeDestination = preview.destination.displayName,
                        routePolyline = preview.encodedPolyline,
                        routeDistanceM = preview.distanceMeters,
                        routeDurationS = preview.durationSeconds,
                        routeRemainingM = preview.distanceMeters,
                        routeRemainingText = "Calculando…",
                        routeEtaText = "",
                        routeNextStep = "Esperando GPS",
                        routeIcon = "STRAIGHT",
                        routeProgress = 0f,
                        routeArrived = false,
                        routeOffRoute = false
                    )
                }
                // A partir de aquí la navegación la calcula el teléfono con su
                // GPS; el estéreo solo pinta PHONE_NAV_STATE.
                PhoneNavController.setRoute(
                    context,
                    preview.destination.displayName,
                    preview.distanceMeters,
                    preview.durationSeconds,
                    preview.encodedPolyline,
                    preview.steps
                )
                // El mapa del corredor sólo viaja por Wi-Fi (son MB, no KB).
                if (BridgeLink.isWifiActive()) {
                    sendRouteTiles(preview)
                }
            } else {
                status = "No se pudo enviar: revisa Wi-Fi Direct"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(IbizaColors.Background)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.setSupportZoom(false)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d("MapWeb", "onPageFinished $url")
                            mapReady = true
                            view?.evaluateJavascript("console.log('L='+(typeof L));") { r ->
                                Log.d("MapWeb", "L check: $r")
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            Log.e("MapWeb", "error ${request?.url} ${error?.description}")
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            Log.d("MapWeb", "console: ${msg.message()} @${msg.sourceId()}:${msg.lineNumber()}")
                            return true
                        }
                    }
                    setBackgroundColor(android.graphics.Color.parseColor("#080D14"))
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    @Suppress("DEPRECATION")
                    settings.allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadDataWithBaseURL("file:///android_asset/leaflet/", MAP_HTML, "text/html", "UTF-8", null)
                    webView = this
                }
            },
            update = { view ->
                webView = view
            }
        )

        LaunchedEffect(webView) {
            delay(1200L)
            webView?.evaluateJavascript(
                "setCurrent($currentLat,$currentLon,$currentBearing,true)", null
            )
            // Offline tile pack (scripts/build_offline_pack.py) if present.
            // External app storage first so adb can push the PNG tree without root.
            try {
                val pack = context.getExternalFilesDir(null)?.let {
                    java.io.File(it, "offline-tiles")
                } ?: java.io.File(context.filesDir, "offline-tiles")
                if (pack.isDirectory && (pack.list()?.isNotEmpty() == true)) {
                    webView?.evaluateJavascript(
                        "setOfflineBase('file://${pack.absolutePath}')", null
                    )
                    Log.d("MapWeb", "offline pack activo: ${pack.absolutePath}")
                }
            } catch (_: Exception) {
            }
        }

        LaunchedEffect(webView, mapReady, appState.routePolyline) {
            if (mapReady && appState.routePolyline.isNotBlank() && route == null) {
                delay(500L)
                drawRouteFromPolyline(appState.routePolyline)
            }
        }

        LaunchedEffect(webView, mapReady, mapStyleId) {
            if (mapReady) {
                webView?.evaluateJavascript("setStyle('$mapStyleId')", null)
            }
        }

        LaunchedEffect(webView, mapReady, appState.wifiConnected, appState.routeActive) {
            val view = webView
            if (view != null && mapReady && appState.wifiConnected && appState.routeActive) {
                MapFrameSender.start(view)
            } else {
                MapFrameSender.stop()
            }
        }

        // La Fase 2 captura la WebView visible: si la pantalla se apaga, la
        // WebView deja de dibujar y el estéreo cae al mapa nativo. Mientras
        // haya ruta activa y enlace Wi-Fi, la pantalla no se apaga.
        DisposableEffect(appState.routeActive, appState.wifiConnected) {
            val window = (context as? Activity)?.window
            if (appState.routeActive && appState.wifiConnected) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        DisposableEffect(Unit) {
            onDispose { MapFrameSender.stop() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .background(IbizaColors.Surface.copy(alpha = 0.96f), RoundedCornerShape(18.dp))
                .padding(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    searchJob?.cancel()
                    if (q.length >= 3) {
                        searchJob = scope.launch {
                            // Debounce: no consultar en cada tecla.
                            delay(700L)
                            searching = true
                            val res = withContext(Dispatchers.IO) {
                                PlaceSearch.search(context, q, currentLat, currentLon)
                            }
                            results = res
                            searching = false
                            if (res.isNotEmpty()) {
                                status = null
                                showSheet = true
                            } else {
                                status = "Sin resultados para \"$q\""
                            }
                        }
                    } else {
                        results = emptyList()
                        searching = false
                    }
                },
                placeholder = { Text("¿A dónde vamos? Negocios, calles o direcciones", color = IbizaColors.TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IbizaColors.Primary) },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = IbizaColors.Primary,
                            strokeWidth = 2.dp
                        )
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; results = emptyList() }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = IbizaColors.TextSecondary)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (results.isNotEmpty()) calculateRoute(results.first())
                }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IbizaColors.Primary,
                    unfocusedBorderColor = IbizaColors.Border,
                    focusedTextColor = IbizaColors.TextPrimary,
                    unfocusedTextColor = IbizaColors.TextPrimary,
                    cursorColor = IbizaColors.Primary
                )
            )
            Spacer(Modifier.height(8.dp))
            // Estilo de mapa: el mismo id viaja al estéreo en TILE_REQ.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapStyleCatalog.ALL.forEach { style ->
                    FilterChip(
                        selected = style.id == mapStyleId,
                        onClick = {
                            mapStyleId = style.id
                            MapStyleCatalog.select(context, style)
                            webView?.evaluateJavascript("setStyle('${style.id}')", null)
                            status = "Mapa: ${style.label}"
                        },
                        label = { Text(style.label, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IbizaColors.Primary,
                            selectedLabelColor = IbizaColors.Background,
                            labelColor = IbizaColors.TextSecondary,
                            containerColor = IbizaColors.SurfaceElevated
                        )
                    )
                }
                AssistChip(
                    onClick = { downloadZone() },
                    enabled = !sendingTiles,
                    label = {
                        Text(
                            if (sendingTiles) "Descargando $tileProgress" else "Descargar zona z15–17",
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = IbizaColors.Primary
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = IbizaColors.Primary,
                        containerColor = IbizaColors.SurfaceElevated
                    )
                )
            }
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showSheet = true }) {
                    Icon(Icons.Default.List, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${results.size} sugerencias", color = IbizaColors.Primary)
                }
            }
        }

        // Recenter FAB
        FloatingActionButton(
            onClick = { centerOnMe() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = IbizaColors.SurfaceElevated,
            contentColor = IbizaColors.Primary
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Centrar en mi ubicación")
        }

        // Status
        status?.let { msg ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = IbizaColors.SurfaceElevated,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
            ) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = IbizaColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Suggestions / route sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = IbizaColors.Surface,
            contentColor = IbizaColors.TextPrimary,
            dragHandle = { BottomSheetDefaults.DragHandle(color = IbizaColors.TextSecondary) }
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                val preview = route
                if (preview == null) {
                    Text("Sugerencias para \"$query\"", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                    Text("Negocios, calles y direcciones cerca de ti", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(results) { place ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(10.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(36.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(place.displayName, style = MaterialTheme.typography.bodyMedium, color = IbizaColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    place.distanceKm?.let { km ->
                                        Text(
                                            "a %.1f km".format(km),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = IbizaColors.Primary
                                        )
                                    }
                                }
                                TextButton(onClick = {
                                    query = place.displayName.take(48)
                                    calculateRoute(place)
                                }) {
                                    Text("Ir", color = IbizaColors.Primary)
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Route, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(preview.destination.displayName, style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${"%.1f".format(preview.distanceMeters / 1000.0)} km · ${preview.durationSeconds / 60} min",
                                style = MaterialTheme.typography.bodySmall,
                                color = IbizaColors.TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                route = null
                                query = ""
                                showSheet = false
                                webView?.evaluateJavascript("clearRoute()", null)
                                clearRouteOnStereo()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar", color = IbizaColors.TextSecondary)
                        }
                        Button(
                            onClick = { sendRoute() },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = IbizaColors.Primary, contentColor = IbizaColors.Background),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Enviar al IbizaDash")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { sendRouteTiles(preview) },
                        enabled = !sendingTiles,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = IbizaColors.TextSecondary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (sendingTiles) "Enviando mapa $tileProgress"
                            else "Enviar mapa del recorrido",
                            color = IbizaColors.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { downloadZone() },
                        enabled = !sendingTiles,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = IbizaColors.Primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (sendingTiles) "Descargando $tileProgress"
                            else "Descargar zona z15–17 (${MapStyleCatalog.byId(mapStyleId).label})",
                            color = IbizaColors.Primary
                        )
                    }
                    Text(
                        "Teselas z14–z16 en el corredor · z15–z17 con \"Descargar zona\".",
                        style = MaterialTheme.typography.labelSmall,
                        color = IbizaColors.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Suaviza el rumbo por el camino más corto (evita saltos 359°→0°). */
private fun smoothBearing(from: Float, to: Float): Float {
    val delta = ((to - from + 540f) % 360f) - 180f
    return (from + delta * 0.45f + 360f) % 360f
}

/** Rumbo geográfico (0 = norte, 90 = este) entre dos coordenadas. */
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

private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
    val poly = ArrayList<Pair<Double, Double>>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0
    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
        lng += dlng
        poly.add(lat / 1e5 to lng / 1e5)
    }
    return poly
}