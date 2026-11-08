package com.chronosbvrx.ibizacompanion.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import com.chronosbvrx.ibizacompanion.core.state.CompanionUiState

@Composable
fun DashboardScreen(
    onOpenMedia: () -> Unit = {},
    onOpenRoute: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by CompanionState.state.collectAsState()
    val np = state.nowPlaying

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("IBIZA COMPANION", style = MaterialTheme.typography.headlineMedium, color = IbizaColors.TextPrimary)
        Text("Centro de control del vehículo", style = MaterialTheme.typography.bodyMedium, color = IbizaColors.TextSecondary)
        Spacer(Modifier.height(20.dp))

        // ---- Connection hero ----
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IbizaColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .background(Brush.horizontalGradient(listOf(IbizaColors.Surface, IbizaColors.SurfaceElevated)))
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(56.dp)) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("IbizaDash", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            state.wifiConnected ->
                                "Wi-Fi Direct \u00B7 ${state.wifiHost ?: "conectado"}"
                            state.serviceRunning -> "Buscando el est\u00E9reo..."
                            else -> "Servicio detenido"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.wifiConnected) IbizaColors.Success else IbizaColors.TextSecondary
                    )
                }
                StatusChip(
                    text = if (state.wifiConnected) "Wi-Fi" else "Desconectado",
                    active = state.wifiConnected
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Active route ----
        if (state.routeActive) {
            RouteCard(
                state = state,
                onOpen = onOpenRoute,
                onStop = { stopRoute(context) }
            )
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = IbizaColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRoute() }
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(44.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = IbizaColors.TextSecondary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Sin ruta activa", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                        Text("Toca para planificar un destino", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = IbizaColors.TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---- Now playing ----
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = IbizaColors.Surface,
            modifier = Modifier.fillMaxWidth().clickable { onOpenMedia() }
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(64.dp)) {
                    if (np.artwork != null) {
                        Image(
                            bitmap = np.artwork!!.asImageBitmap(),
                            contentDescription = "Portada",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(26.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (np.packageName.isNotBlank()) {
                            val appIcon = remember(np.packageName) {
                                runCatching { context.packageManager.getApplicationIcon(np.packageName) }.getOrNull()
                            }
                            if (appIcon != null) {
                                coil.compose.AsyncImage(
                                    model = appIcon,
                                    contentDescription = "App",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                        }
                        Text("AHORA SUENA", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary)
                    }
                    Text(
                        np.title.ifBlank { "Sin reproducción" },
                        style = MaterialTheme.typography.titleMedium,
                        color = IbizaColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (np.artist.isBlank()) "—" else "${np.artist} · ${np.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = IbizaColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Route + service tiles ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IbizaColors.Surface,
                modifier = Modifier.weight(1f).clickable { onOpenRoute() }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Navigation, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Ruta", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary)
                    Text(
                        if (state.routeActive) "Activa" else "Ninguna",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.routeActive) IbizaColors.Success else IbizaColors.TextPrimary
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IbizaColors.Surface,
                modifier = Modifier.weight(1f).clickable { startService(context) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Servicio", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary)
                    Text(
                        if (state.serviceRunning) "Activo" else "Detenido",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.serviceRunning) IbizaColors.Success else IbizaColors.Warning
                    )
                }
            }
        }

        // Notification access warning
        if (!notificationAccessEnabled(context)) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = IbizaColors.Warning, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Detección de música desactivada", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                        Text("Activa el acceso a notificaciones", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                    }
                    TextButton(onClick = {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text("Activar", color = IbizaColors.Primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Actualización de la propia app: se descarga sola y aquí se instala.
        if (state.selfUpdateAvailable) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = IbizaColors.Surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = IbizaColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Actualización de IbizaCompanion",
                            style = MaterialTheme.typography.titleMedium,
                            color = IbizaColors.TextPrimary
                        )
                        Text(
                            "v${state.selfUpdateVersionName} descargada · toca instalar",
                            style = MaterialTheme.typography.bodySmall,
                            color = IbizaColors.TextSecondary
                        )
                    }
                    TextButton(onClick = {
                        val updater = com.chronosbvrx.ibizacompanion.update.CompanionUpdater
                        if (!updater.canInstall(context)) {
                            updater.openInstallPermissionSettings(context)
                        } else {
                            updater.install(context)
                        }
                    }) {
                        Text("Instalar", color = IbizaColors.Primary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Quick actions
        Text("ACCIONES RÁPIDAS", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction(
                text = if (state.wifiConnected) "Reconectar" else "Conectar",
                modifier = Modifier.weight(1f)
            ) { reconnect(context) }
            QuickAction(text = "Enviar ruta", modifier = Modifier.weight(1f)) { onOpenRoute() }
            QuickAction(
                text = if (android.provider.Settings.canDrawOverlays(context)) "Volver: ON" else "Activar volver",
                modifier = Modifier.weight(1f)
            ) {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusChip(text: String, active: Boolean) {
    val dot = if (active) IbizaColors.Success else IbizaColors.TextSecondary
    Surface(shape = RoundedCornerShape(20.dp), color = IbizaColors.SurfaceElevated) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(dot, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = dot)
        }
    }
}

@Composable
private fun RouteCard(
    state: CompanionUiState,
    onOpen: () -> Unit,
    onStop: () -> Unit
) {
    val dest = state.routeDestination ?: "Destino"
    val accent = when {
        state.routeArrived -> IbizaColors.Success
        state.routeOffRoute -> IbizaColors.Warning
        else -> IbizaColors.Primary
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = IbizaColors.Surface,
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            maneuverIcon(state.routeIcon),
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (state.routeArrived) "HAS LLEGADO" else "RUTA ACTIVA",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                    Text(
                        dest,
                        style = MaterialTheme.typography.titleMedium,
                        color = IbizaColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.routeEtaText.isNotBlank() && !state.routeArrived) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(state.routeEtaText, style = MaterialTheme.typography.titleMedium, color = accent)
                        Text("restante", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.routePolyline.isNotBlank()) {
                RouteSketch(state.routePolyline, accent, state.routeProgress)
                Spacer(Modifier.height(10.dp))
            }

            Text(
                when {
                    state.routeArrived -> "Viaje terminado"
                    state.routeOffRoute -> "Recalculando ruta…"
                    state.routeNextStep.isNotBlank() -> state.routeNextStep
                    else -> "Continúa en la ruta"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.routeOffRoute) IbizaColors.Warning else IbizaColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                remainingLabel(state),
                style = MaterialTheme.typography.bodySmall,
                color = IbizaColors.TextSecondary
            )

            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = state.routeProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = IbizaColors.SurfaceElevated
            )

            if (state.routeDistanceM > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Total ${fmtMeters(state.routeDistanceM)} · ${fmtDuration(state.routeDurationS)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = IbizaColors.TextSecondary
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ver en el mapa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IbizaColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onStop) {
                    Text("Detener", color = IbizaColors.Error)
                }
            }
        }
    }
}

@Composable
private fun RouteSketch(encoded: String, color: Color, progress: Float) {
    val points = remember(encoded) { decodePolyline(encoded) }
    if (points.size < 2) return
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(IbizaColors.Background)
    ) {
        val step = max(1, points.size / 350)
        val sample = ArrayList<Pt>(min(points.size, 351))
        var i = 0
        while (i < points.size) {
            sample.add(points[i])
            i += step
        }
        if (sample.last() != points.last()) sample.add(points.last())

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        sample.forEach { p ->
            if (p.lat < minLat) minLat = p.lat
            if (p.lat > maxLat) maxLat = p.lat
            if (p.lon < minLon) minLon = p.lon
            if (p.lon > maxLon) maxLon = p.lon
        }
        val latSpan = max(1e-6, maxLat - minLat)
        val lonSpan = max(1e-6, maxLon - minLon)
        val pad = 8.dp.toPx()
        val w = (size.width - pad * 2).coerceAtLeast(1f)
        val h = (size.height - pad * 2).coerceAtLeast(1f)
        val scale = min(w / lonSpan.toFloat(), h / latSpan.toFloat())
        val offX = pad + (w - lonSpan.toFloat() * scale) / 2f
        val offY = pad + (h - latSpan.toFloat() * scale) / 2f

        val path = Path()
        var sx = 0f
        var sy = 0f
        var ex = 0f
        var ey = 0f
        sample.forEachIndexed { idx, p ->
            val x = offX + ((p.lon - minLon).toFloat() * scale)
            val y = offY + ((maxLat - p.lat).toFloat() * scale)
            if (idx == 0) {
                path.moveTo(x, y)
                sx = x
                sy = y
            } else {
                path.lineTo(x, y)
            }
            if (idx == sample.lastIndex) {
                ex = x
                ey = y
            }
        }
        drawPath(
            path = path,
            color = IbizaColors.SurfaceElevated,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        // Remaining part of the route in the accent color.
        val split = (progress.coerceIn(0f, 1f) * (sample.size - 1)).toInt()
        val rest = Path()
        for (idx in split until sample.size) {
            val p = sample[idx]
            val x = offX + ((p.lon - minLon).toFloat() * scale)
            val y = offY + ((maxLat - p.lat).toFloat() * scale)
            if (idx == split) rest.moveTo(x, y) else rest.lineTo(x, y)
        }
        drawPath(
            path = rest,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(IbizaColors.Success, radius = 3.dp.toPx(), center = Offset(sx, sy))
        val cp = sample[split]
        drawCircle(
            Color.White,
            radius = 3.dp.toPx(),
            center = Offset(
                offX + ((cp.lon - minLon).toFloat() * scale),
                offY + ((maxLat - cp.lat).toFloat() * scale)
            )
        )
        drawCircle(color, radius = 4.dp.toPx(), center = Offset(ex, ey))
    }
}

private data class Pt(val lat: Double, val lon: Double)

/** Standard Google encoded polyline decoder (same format OSRM returns). */
private fun decodePolyline(encoded: String): List<Pt> {
    if (encoded.isBlank()) return emptyList()
    val out = ArrayList<Pt>()
    var index = 0
    var lat = 0
    var lon = 0
    while (index < encoded.length) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            if (index >= encoded.length) return out
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dLat

        shift = 0
        result = 0
        do {
            if (index >= encoded.length) return out
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val dLon = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lon += dLon
        out.add(Pt(lat / 1e5, lon / 1e5))
    }
    return out
}

@Composable
private fun QuickAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = IbizaColors.SurfaceElevated,
        modifier = modifier.height(44.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().clickable { onClick() }
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = IbizaColors.Primary)
        }
    }
}

private fun maneuverIcon(name: String) = when (name) {
    "LEFT" -> Icons.Filled.TurnLeft
    "RIGHT" -> Icons.Filled.TurnRight
    "UTURN" -> Icons.Filled.UTurnLeft
    "ROUNDABOUT" -> Icons.Filled.RoundaboutRight
    "ARRIVE" -> Icons.Filled.Place
    "DEPART" -> Icons.Filled.Navigation
    else -> Icons.Filled.Straight
}

private fun remainingLabel(state: CompanionUiState): String {
    if (state.routeRemainingText.isNotBlank()) return state.routeRemainingText
    if (state.routeRemainingM > 0) {
        return "Quedan ${fmtMeters(state.routeRemainingM)}" +
            if (state.routeEtaText.isNotBlank()) " · ${state.routeEtaText}" else ""
    }
    return "Esperando datos del vehículo…"
}

private fun fmtMeters(m: Int): String {
    val v = m.coerceAtLeast(0)
    return if (v < 1000) "$v m" else {
        val km = v / 1000.0
        if (km < 10) String.format("%.1f km", km) else "${Math.round(km)} km"
    }
}

private fun fmtDuration(seconds: Int): String {
    if (seconds <= 0) return "—"
    val min = seconds / 60
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}

private fun serviceIntent(context: android.content.Context): android.content.Intent =
    android.content.Intent(context, com.chronosbvrx.ibizacompanion.service.MediaBridgeService::class.java)

private fun startService(context: android.content.Context) {
    try {
        val intent = serviceIntent(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (_: Exception) {
    }
}

private fun reconnect(context: android.content.Context) {
    try {
        val intent = serviceIntent(context).setAction(
            com.chronosbvrx.ibizacompanion.service.MediaBridgeService.ACTION_CONNECT
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (_: Exception) {
    }
}

private fun stopRoute(context: android.content.Context) {
    try {
        val intent = serviceIntent(context).setAction(
            com.chronosbvrx.ibizacompanion.service.MediaBridgeService.ACTION_CLEAR_ROUTE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (_: Exception) {
    }
}

private fun notificationAccessEnabled(context: android.content.Context): Boolean {
    val enabled = android.provider.Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabled.contains(context.packageName)
}
