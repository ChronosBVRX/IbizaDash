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
    val items = listOf(
        SettingItem("General", "Tema, idioma, inicio", "Tema oscuro (Automotive Premium) activo. Idioma: Español."),
        SettingItem("Conexión", "Wi-Fi Direct, Bluetooth de respaldo", "Wi-Fi Direct (TCP) para portadas y mapas; Bluetooth RFCOMM como respaldo."),
        SettingItem("Multimedia", "Aplicaciones prioritarias, calidad", "Portada: 512 px por Wi-Fi, 256 px por Bluetooth. Apps: todas las sesiones activas."),
        SettingItem("Navegación", "Proveedor, unidades, ubicación", "Proveedor: OSRM público. Unidades: km."),
        SettingItem("Segundo plano", "Servicio, batería, notificaciones", "Servicio foreground activo. Optimización de batería: recomendado desactivar."),
        SettingItem("Apariencia", "Tipografía, animaciones, contraste", "Fuente: sans-serif. Animaciones: activas."),
        SettingItem("Diagnóstico", "Logs, versión, estado", "Versión 0.4.0. Wi-Fi Direct + RFCOMM: puerto 47800. Navegación y mapa en el teléfono."),
        SettingItem("Acerca de", "IbizaCompanion, IbizaDash", "IbizaCompanion v0.1.0 · IbizaDash v0.1.0 · Puente Bluetooth.")
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
        // Spotify App Remote test (background playback, no app switch after auth).
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = IbizaColors.Surface,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Spotify en segundo plano", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                Text("Prueba la reproducción sin abrir Spotify (requiere app instalada y Premium).", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
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
                                        com.chronosbvrx.ibizacompanion.data.SpotifyRemote.play("spotify:track:4uLU6hMCjMI75M1A2tKUQC")
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IbizaColors.Primary, contentColor = IbizaColors.Background)
                    ) { Text("Probar") }
                    OutlinedButton(onClick = { com.chronosbvrx.ibizacompanion.data.SpotifyRemote.disconnect() }) {
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
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                    }
                    Text("›", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextSecondary)
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