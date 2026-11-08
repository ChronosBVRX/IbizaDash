package com.chronosbvrx.ibizacompanion.feature.connection

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.ibizacompanion.BridgeLink
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import com.chronosbvrx.ibizacompanion.core.state.CompanionUiState
import com.chronosbvrx.ibizacompanion.data.StereoPrefs
import com.chronosbvrx.ibizacompanion.ota.OtaSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ConnectionScreen() {
    val context = LocalContext.current
    val state by CompanionState.state.collectAsState()
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var wifiEnabled by remember { mutableStateOf(StereoPrefs.wifiEnabled(context)) }
    var wifiHost by remember { mutableStateOf(StereoPrefs.wifiHost(context) ?: "") }
    var savedHost by remember { mutableStateOf(StereoPrefs.wifiHost(context)) }

    // Consulta GitHub en segundo plano al abrir la pantalla.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { OtaSender.refresh(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("CONEXIÓN", style = MaterialTheme.typography.headlineMedium, color = IbizaColors.TextPrimary)
        Text(
            "Wi-Fi Direct es el único canal de datos: rutas, mapas, portadas, clima y actualizaciones viajan por aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = IbizaColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))

        // ---- Wi-Fi Direct ----
        Surface(shape = RoundedCornerShape(16.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (state.wifiConnected) IbizaColors.Success else IbizaColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "WI-FI DIRECTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = IbizaColors.TextSecondary
                        )
                        Text(
                            when {
                                state.wifiConnected -> "Conectado · ${state.wifiHost ?: "TCP"}"
                                state.serviceRunning -> "Buscando estéreo…"
                                else -> "Servicio detenido"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (state.wifiConnected) IbizaColors.Success else IbizaColors.TextPrimary
                        )
                    }
                    Switch(
                        checked = wifiEnabled,
                        onCheckedChange = { on ->
                            wifiEnabled = on
                            BridgeLink.setWifiEnabled(context, on)
                            statusMsg = if (on) {
                                "Wi-Fi Direct activado"
                            } else {
                                "Wi-Fi Direct desactivado: sin enlace con el estéreo"
                            }
                        }
                    )
                }

                if (state.wifiDetail.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.wifiDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = IbizaColors.TextSecondary
                    )
                }

                if (state.wifiPeers.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "ESTÉREOS DETECTADOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = IbizaColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    state.wifiPeers.forEach { peer ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IbizaColors.SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable {
                                    BridgeLink.connectPeer(peer.address)
                                    statusMsg = "Conectando a ${peer.name}…"
                                }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = IbizaColors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        peer.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = IbizaColors.TextPrimary
                                    )
                                    Text(
                                        "${peer.address} · ${peer.status}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IbizaColors.TextSecondary
                                    )
                                }
                                Text("CONECTAR", color = IbizaColors.Primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            startBridgeService(context)
                            BridgeLink.scanPeers()
                            statusMsg = "Buscando estéreos por Wi-Fi Direct…"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IbizaColors.Primary,
                            contentColor = IbizaColors.Background
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Buscar estéreo")
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            } catch (_: Exception) {
                                statusMsg = "Wi-Fi no disponible"
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Wi-Fi", color = IbizaColors.TextSecondary)
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = wifiHost,
                    onValueChange = { wifiHost = it },
                    label = { Text("IP del estéreo (LAN/hotspot, opcional)") },
                    singleLine = true,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        savedHost?.takeIf { it.isNotBlank() }?.let { "IP guardada: $it" } ?: "Sin IP manual",
                        style = MaterialTheme.typography.bodySmall,
                        color = IbizaColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            StereoPrefs.setWifiHost(context, wifiHost)
                            savedHost = StereoPrefs.wifiHost(context)
                            BridgeLink.reconnectWifi()
                            statusMsg = if (wifiHost.isBlank()) "IP manual borrada" else "IP guardada: $wifiHost"
                        }
                    ) {
                        Text(if (wifiHost.isBlank()) "Borrar" else "Guardar", color = IbizaColors.Primary)
                    }
                }
            }
        }

        if (statusMsg != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                statusMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.wifiConnected) IbizaColors.Success else IbizaColors.Warning
            )
        }

        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("ESTADO", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        state.wifiConnected -> "Enlace Wi-Fi Direct activo"
                        state.serviceRunning -> "Buscando el estéreo…"
                        else -> "Servicio detenido"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = IbizaColors.TextPrimary
                )
                Text(
                    "El teléfono es la única fuente de Internet y contenido del estéreo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IbizaColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OtaSection(state)
    }
}

/**
 * Actualización OTA del estéreo: la app consulta el último release de GitHub,
 * descarga `IbizaDash.apk` y lo envía por Wi-Fi Direct. El estéreo verifica
 * tamaño, SHA-256 y firma antes de instalar. Sin ADB y sin Internet en el coche.
 */
@Composable
private fun OtaSection(state: CompanionUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stereoVc = state.stereoVersionCode
    val stereoName = state.stereoVersionName.takeIf { it.isNotBlank() } ?: "v$stereoVc"
    val latestVc = state.otaLatestVersionCode
    val latestName = state.otaLatestVersionName.takeIf { it.isNotBlank() } ?: "v$latestVc"
    val hasLatest = latestVc > 0
    val updateAvailable = hasLatest && (stereoVc == 0 || latestVc > stereoVc)

    Surface(shape = RoundedCornerShape(16.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "ACTUALIZACIÓN DEL ESTÉREO",
                style = MaterialTheme.typography.labelSmall,
                color = IbizaColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    state.otaChecking -> "Buscando la última versión en GitHub…"
                    !hasLatest -> "No se pudo consultar GitHub"
                    stereoVc == 0 -> "Última versión: $latestName · estéreo sin conectar"
                    updateAvailable -> "Estéreo $stereoName · disponible $latestName"
                    else -> "Estéreo al día ($stereoName)"
                },
                style = MaterialTheme.typography.titleMedium,
                color = IbizaColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "La actualización viaja por Wi-Fi Direct y se instala desde el estéreo. Sin ADB.",
                style = MaterialTheme.typography.bodySmall,
                color = IbizaColors.TextSecondary
            )
            if (state.otaStatus.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    state.otaStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.otaProgress >= 1f) IbizaColors.Success else IbizaColors.TextSecondary
                )
            }
            if (state.otaSending) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.otaProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = IbizaColors.Primary,
                    trackColor = IbizaColors.SurfaceElevated
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) { OtaSender.sendUpdate(context) }
                },
                enabled = updateAvailable && !state.otaSending && state.wifiConnected,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IbizaColors.Primary,
                    contentColor = IbizaColors.Background
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.otaSending) "Enviando…" else "Actualizar estéreo")
            }
            if (!state.wifiConnected) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Conecta por Wi-Fi Direct para poder enviar la actualización.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IbizaColors.Warning
                )
            }
            TextButton(
                onClick = {
                    scope.launch(Dispatchers.IO) { OtaSender.refresh(context, force = true) }
                },
                enabled = !state.otaChecking && !state.otaSending
            ) {
                Text("Buscar actualización", color = IbizaColors.Primary)
            }
        }
    }
}

private fun startBridgeService(context: Context) {
    try {
        val svc = Intent(
            context,
            com.chronosbvrx.ibizacompanion.service.MediaBridgeService::class.java
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    } catch (_: Exception) {
    }
}
