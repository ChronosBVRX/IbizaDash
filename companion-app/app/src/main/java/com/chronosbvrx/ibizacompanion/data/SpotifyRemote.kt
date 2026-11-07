package com.chronosbvrx.ibizacompanion.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

/**
 * Spotify App Remote SDK: controls the official Spotify app through local IPC,
 * without calling the Web API. Allows playing a URI while Spotify stays in the
 * background (no app switch).
 *
 * Requires the Spotify app installed and the user logged in. The first connection
 * may show the Spotify authorization screen once.
 */
object SpotifyRemote {
    private const val CLIENT_ID = "335a2a50aace439d955b3b9d1a0f5221"
    private const val REDIRECT_URI = "spotify-sdk://auth"

    @Volatile
    private var remote: SpotifyAppRemote? = null

    const val AUTH_REQUEST_CODE = 1337

    /**
     * Explicit authorization flow. The App Remote SDK often fails to show its
     * auth view on newer Android versions; launching AuthorizationClient first
     * authorizes the app inside Spotify and makes the App Remote connection work.
     */
    fun authorize(activity: Activity) {
        try {
            val request = AuthorizationRequest.Builder(
                CLIENT_ID,
                AuthorizationResponse.Type.TOKEN,
                REDIRECT_URI
            )
                .setScopes(arrayOf("app-remote-control", "streaming"))
                .build()
            AuthorizationClient.openLoginActivity(activity, AUTH_REQUEST_CODE, request)
        } catch (e: Exception) {
            Log.e("SpotifyRemote", "authorize: ${e.message}", e)
        }
    }

    fun handleAuthResult(resultCode: Int, data: Intent?, context: Context, onResult: (Boolean) -> Unit) {
        try {
            val response = AuthorizationClient.getResponse(resultCode, data)
            Log.i("SpotifyRemote", "auth result: ${response.type} ${response.error ?: ""}")
            if (response.type == AuthorizationResponse.Type.TOKEN) {
                connect(context, onResult)
            } else {
                onResult(false)
            }
        } catch (e: Exception) {
            Log.e("SpotifyRemote", "handleAuthResult: ${e.message}", e)
            onResult(false)
        }
    }

    fun connect(context: Context, onResult: (Boolean) -> Unit) {
        if (remote?.isConnected == true) {
            onResult(true)
            return
        }
        val params = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()
        try {
            SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    Log.i("SpotifyRemote", "Conectado al App Remote")
                    remote = appRemote
                    onResult(true)
                }

                override fun onFailure(error: Throwable) {
                    Log.e("SpotifyRemote", "onFailure: ${error.javaClass.name}: ${error.message}", error)
                    remote = null
                    onResult(false)
                }
            })
        } catch (e: Exception) {
            Log.e("SpotifyRemote", "connect exception: ${e.javaClass.name}: ${e.message}", e)
            remote = null
            onResult(false)
        }
    }

    fun play(uri: String): Boolean {
        return try {
            val r = remote
            if (r == null || !r.isConnected) return false
            r.playerApi.play(uri)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isConnected(): Boolean = remote?.isConnected == true

    fun disconnect() {
        try {
            remote?.let { SpotifyAppRemote.disconnect(it) }
        } catch (_: Exception) {
        }
        remote = null
    }
}