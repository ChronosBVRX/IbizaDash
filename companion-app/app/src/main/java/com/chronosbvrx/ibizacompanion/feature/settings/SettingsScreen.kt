package com.chronosbvrx.ibizacompanion.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import com.chronosbvrx.ibizacompanion.data.CompanionPrefs
import com.chronosbvrx.ibizacompanion.update.CompanionUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val companionState by CompanionState.state.collectAsState()
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val stereoVersion = companionState.stereoVersionName
        .takeIf { it.isNotBlank() }
        ?: companionState.stereoVersionCode.takeIf { it > 0 }?.let { "v$it" }
        ?: "sin conectar"

    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var autoUpdate by remember { mutableStateOf(CompanionPrefs.autoUpdate(context)) }

    // Se releen en cada recomposición: son consultas baratas y así reflejan
    // el cambio al volver de los ajustes del sistema.
    val canInstall = CompanionUpdater.canInstall(context)
    val notificationsOn = notificationAccessEnabled(context)
    val overlayOn = Settings.canDrawOverlays(context)
    val spotifyInstalled = remember { isInstalled(context, "com.spotify.music") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("AJUSTES", style = MaterialTheme.typography.headlineMedium, color = IbizaColors.TextPrimary)
        Spacer(Modifier.height(16.dp))

        SelfUpdateCard()
        Spacer(Modifier.height(8.dp))
        if (spotifyInstalled) {
            SpotifyCard()
            Spacer(Modifier.height(8.dp))
        }

        SectionTitle("SISTEMA")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = IbizaColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Interruptor real: controla la revisión automática del servicio.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Actualización automática", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                        Text(
                            if (autoUpdate) "Revisa al abrir y cada 6 horas"
                            else "Desactivada · usa Buscar ahora",
                            style = MaterialTheme.typography.bodySmall,
                            color = IbizaColors.TextSecondary
                        )
                    }
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            CompanionPrefs.setAutoUpdate(context, it)
                        }
                    )
                }

                Divider()
                SettingRow(
                    title = "Permiso para instalar actualizaciones",
                    subtitle = if (canInstall) "Permitido" else "Bloqueado · toca para permitir",
                    active = canInstall
                ) { CompanionUpdater.openInstallPermissionSettings(context) }

                Divider()
                SettingRow(
                    title = "Acceso a notificaciones",
                    subtitle = if (notificationsOn) "Activo · detecta Spotify y YouTube Music"
                    else "Inactivo · la música no se puede detectar",
                    active = notificationsOn
                ) { openNotificationSettings(context) }

                Divider()
                SettingRow(
                    title = "Volver a la app al cambiar de canción",
                    subtitle = if (overlayOn) "Activo" else "Requiere permiso de superposición",
                    active = overlayOn
                ) {
                    openSafely(
                        context,
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }

                Divider()
                SettingRow(
                    title = "Optimización de batería",
                    subtitle = "Si el enlace se corta en segundo plano, desactívala aquí",
                    active = true
                ) {
                    openSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }

        SectionTitle("INFORMACIÓN")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = IbizaColors.Surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SettingRow(
                    title = "Diagnóstico",
                    subtitle = "Versiones y estado del enlace",
                    active = true
                ) {
                    dialog = "Diagnóstico" to buildString {
                        appendLine("IbizaCompanion v$appVersion")
                        appendLine("Estéreo: $stereoVersion")
                        appendLine("Wi-Fi Direct: puerto 47800")
                        appendLine("Canal de datos: ${if (companionState.wifiConnected) "conectado" else "desconectado"}")
                        appendLine("Servicio: ${if (companionState.serviceRunning) "activo" else "detenido"}")
                        append("Última versión publicada: ")
                        append(
                            companionState.otaLatestVersionName
                                .takeIf { it.isNotBlank() } ?: "sin consultar"
                        )
                    }
                }
                Divider()
                SettingRow(
                    title = "Acerca de",
                    subtitle = "IbizaCompanion · IbizaDash",
                    active = true
                ) {
                    dialog = "Acerca de" to
                        "IbizaCompanion v$appVersion, la app del teléfono. " +
                        "IbizaDash $stereoVersion corre en el estéreo del coche. " +
                        "El teléfono es la única fuente de Internet y contenido; " +
                        "todo viaja por Wi-Fi Direct."
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    dialog?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title, color = IbizaColors.TextPrimary) },
            text = { Text(text, color = IbizaColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { dialog = null }) {
                    Text("Cerrar", color = IbizaColors.Primary)
                }
            },
            containerColor = IbizaColors.Surface
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = IbizaColors.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
    )
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 16.dp)
            .height(1.dp)
            .background(IbizaColors.SurfaceElevated)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (active) IbizaColors.TextSecondary else IbizaColors.Warning
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = IbizaColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Actualización de la propia app del teléfono. Se descarga sola de GitHub y
 * solo hace falta confirmar la instalación en el diálogo del sistema.
 */
@Composable
private fun SelfUpdateCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by CompanionState.state.collectAsState()
    val scope = rememberCoroutineScope()
    val installed = remember { CompanionUpdater.installedVersionName(context) }
    val version = state.selfUpdateVersionName.ifBlank { "nueva" }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = IbizaColors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "ACTUALIZACIÓN DE IBIZACOMPANION",
                style = MaterialTheme.typography.labelSmall,
                color = IbizaColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.selfUpdateAvailable) "Versión $version lista para instalar"
                else "Instalada v$installed",
                style = MaterialTheme.typography.titleMedium,
                color = IbizaColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                state.selfUpdateStatus.ifBlank {
                    "Android pide confirmar la instalación; no se instala en silencio."
                },
                style = MaterialTheme.typography.bodySmall,
                color = IbizaColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!CompanionUpdater.canInstall(context)) {
                            CompanionUpdater.openInstallPermissionSettings(context)
                        } else {
                            CompanionUpdater.install(context)
                        }
                    },
                    enabled = state.selfUpdateAvailable && !state.selfUpdating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IbizaColors.Primary,
                        contentColor = IbizaColors.Background
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (state.selfUpdating) "Instalando…" else "Instalar actualización")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            CompanionUpdater.checkForUpdate(context, force = true)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Buscar ahora", color = IbizaColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * Spotify App Remote solo sirve si la app de Spotify está instalada. Se oculta
 * el bloque cuando no lo está para no dejar un botón que no puede funcionar.
 */
@Composable
private fun SpotifyCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = IbizaColors.Surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Spotify en segundo plano", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
            Text(
                "Control remoto local de Spotify, sin cambiar de app. Requiere Premium.",
                style = MaterialTheme.typography.bodySmall,
                color = IbizaColors.TextSecondary
            )
            status?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        status = "Conectando con Spotify…"
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            com.chronosbvrx.ibizacompanion.data.SpotifyRemote.authorize(activity)
                        } else {
                            com.chronosbvrx.ibizacompanion.data.SpotifyRemote.connect(context) { ok ->
                                status = if (ok) "Spotify conectado" else "Spotify no conectado"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IbizaColors.Primary,
                        contentColor = IbizaColors.Background
                    )
                ) { Text("Probar conexión") }
                OutlinedButton(
                    onClick = {
                        com.chronosbvrx.ibizacompanion.data.SpotifyRemote.disconnect()
                        status = "Spotify desconectado"
                    }
                ) {
                    Text("Desconectar", color = IbizaColors.TextSecondary)
                }
            }
        }
    }
}

private fun isInstalled(context: Context, pkg: String): Boolean = try {
    context.packageManager.getApplicationInfo(pkg, 0)
    true
} catch (_: Exception) {
    false
}

private fun notificationAccessEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return enabled.contains(context.packageName)
}

private fun openNotificationSettings(context: Context) {
    openSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

private fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}
