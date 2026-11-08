package com.chronosbvrx.ibizacompanion.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors

private data class SettingItem(val title: String, val subtitle: String, val detail: String)

@Composable
fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val companionState by com.chronosbvrx.ibizacompanion.core.state.CompanionState.state
        .collectAsState()
    val appVersion = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    val stereoVersion = companionState.stereoVersionName
        .takeIf { it.isNotBlank() }
        ?: companionState.stereoVersionCode.takeIf { it > 0 }?.let { "v$it" }
        ?: "sin conectar"
    val items = listOf(
        SettingItem(
            "General",
            "Tema, idioma, inicio",
            "Tema oscuro (Automotive Premium) activo. Idioma: Español."
        ),
        SettingItem(
            "Conexión",
            "Wi-Fi Direct únicamente",
            "Rutas, mapas, portadas, clima y OTA viajan por Wi-Fi Direct. " +
                "Bluetooth queda solo para audio A2DP, llamadas y OBD."
        ),
        SettingItem(
            "Multimedia",
            "Portadas y sesiones activas",
            "Portada de 512 px por Wi-Fi Direct. Se detectan todas las sesiones de audio activas."
        ),
        SettingItem(
            "Navegación",
            "OSRM, unidades, ubicación",
            "Proveedor: OSRM público. Unidades: km. La navegación se calcula en el teléfono."
        ),
        SettingItem(
            "Segundo plano",
            "Servicio, batería, notificaciones",
            "Servicio en primer plano activo. Si el teléfono lo suspende, desactiva la " +
                "optimización de batería para IbizaCompanion en los ajustes del sistema."
        ),
        SettingItem(
            "Apariencia",
            "Tipografía, animaciones, contraste",
            "Fuente: sans-serif. Animaciones: activas."
        ),
        SettingItem(
            "Diagnóstico",
            "Versiones y enlace",
            "IbizaCompanion v$appVersion. Estéreo: $stereoVersion. " +
                "Wi-Fi Direct: puerto 47800. OTA desde GitHub."
        ),
        SettingItem(
            "Acerca de",
            "IbizaCompanion, IbizaDash",
            "IbizaCompanion v$appVersion · IbizaDash $stereoVersion · Enlace Wi-Fi Direct."
        )
    )
    var selected by remember { mutableStateOf<SettingItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("AJUSTES", style = MaterialTheme.typography.headlineMedium, color = IbizaColors.TextPrimary)
        Spacer(Modifier.height(16.dp))
        // Spotify App Remote (reproducción en segundo plano, sin cambiar de app).
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = IbizaColors.Surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Spotify en segundo plano",
                    style = MaterialTheme.typography.titleMedium,
                    color = IbizaColors.TextPrimary
                )
                Text(
                    "Prueba la reproducción sin abrir Spotify (requiere app instalada y Premium).",
                    style = MaterialTheme.typography.bodySmall,
                    color = IbizaColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                com.chronosbvrx.ibizacompanion.data.SpotifyRemote.authorize(activity)
                            } else {
                                com.chronosbvrx.ibizacompanion.data.SpotifyRemote.connect(context) { ok ->
                                    if (ok) {
                                        com.chronosbvrx.ibizacompanion.data.SpotifyRemote.play(
                                            "spotify:track:4uLU6hMCjMI75M1A2tKUQC"
                                        )
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IbizaColors.Primary,
                            contentColor = IbizaColors.Background
                        )
                    ) { Text("Probar") }
                    OutlinedButton(
                        onClick = { com.chronosbvrx.ibizacompanion.data.SpotifyRemote.disconnect() }
                    ) {
                        Text("Desconectar", color = IbizaColors.TextSecondary)
                    }
                }
            }
        }

        items.forEach { item ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = IbizaColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { selected = item }
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = IbizaColors.TextPrimary
                        )
                        Text(
                            item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = IbizaColors.TextSecondary
                        )
                    }
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = IbizaColors.TextSecondary
                    )
                }
            }
        }
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.title, color = IbizaColors.TextPrimary) },
            text = { Text(item.detail, color = IbizaColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { selected = null }) {
                    Text("Cerrar", color = IbizaColors.Primary)
                }
            },
            containerColor = IbizaColors.Surface
        )
    }
}
