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
    val btConnected: Boolean = false,
    val btDeviceName: String? = null,
    /** Wi-Fi Direct / TCP conectado. Tiene prioridad sobre Bluetooth. */
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
    val routeOffRoute: Boolean = false
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
