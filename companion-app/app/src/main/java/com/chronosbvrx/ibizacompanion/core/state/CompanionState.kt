package com.chronosbvrx.ibizacompanion.core.state

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: Bitmap? = null,
    val isPlaying: Boolean = false,
    val packageName: String = ""
)

/**
 * Par Wi-Fi Direct visible durante el descubrimiento.
 * [status] es el texto que expone el framework ("disponible", "conectado"...).
 */
data class WifiPeerInfo(
    val name: String,
    val address: String,
    val status: String = ""
)

data class CompanionUiState(
    /** Wi-Fi Direct / TCP conectado; es el único canal de datos. */
    val wifiConnected: Boolean = false,
    val wifiHost: String? = null,
    val wifiDetail: String = "",
    val wifiPeers: List<WifiPeerInfo> = emptyList(),
    val serviceRunning: Boolean = true,
    /** elapsedRealtime of the last message received from the stereo. */
    val lastStereoMs: Long = 0L,
    val nowPlaying: NowPlayingState = NowPlayingState(),
    val routeActive: Boolean = false,
    val routeDestination: String? = null,
    /** Encoded OSRM polyline for the mini route sketch. */
    val routePolyline: String = "",
    /** Total route values as reported when the route was created. */
    val routeDistanceM: Int = 0,
    val routeDurationS: Int = 0,
    /** Live values streamed from the stereo while driving. */
    val routeRemainingM: Int = 0,
    val routeRemainingText: String = "",
    val routeEtaText: String = "",
    val routeNextStep: String = "",
    val routeIcon: String = "",
    val routeProgress: Float = 0f,
    val routeArrived: Boolean = false,
    val routeOffRoute: Boolean = false,
    /** Versión del estéreo conectado (0 = aún no la ha reportado en WELCOME). */
    val stereoVersionCode: Int = 0,
    val stereoVersionName: String = "",
    /** Última versión publicada en GitHub (0 = aún no se ha consultado). */
    val otaLatestVersionCode: Int = 0,
    val otaLatestVersionName: String = "",
    /** Estado de la OTA al estéreo. */
    val otaStatus: String = "",
    val otaProgress: Float = 0f,
    val otaSending: Boolean = false,
    val otaChecking: Boolean = false,
    /** Actualización de la propia app del teléfono (descargada de GitHub). */
    val selfUpdateAvailable: Boolean = false,
    val selfUpdateVersionCode: Int = 0,
    val selfUpdateVersionName: String = "",
    val selfUpdateStatus: String = "",
    val selfUpdating: Boolean = false
)

object CompanionState {
    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()

    fun update(transform: (CompanionUiState) -> CompanionUiState) {
        _state.value = transform(_state.value)
    }

    /** Resets the route block (route cleared, finished or disconnected). */
    fun clearRoute() {
        update {
            it.copy(
                routeActive = false,
                routeDestination = null,
                routePolyline = "",
                routeDistanceM = 0,
                routeDurationS = 0,
                routeRemainingM = 0,
                routeRemainingText = "",
                routeEtaText = "",
                routeNextStep = "",
                routeIcon = "",
                routeProgress = 0f,
                routeArrived = false,
                routeOffRoute = false
            )
        }
    }
}
