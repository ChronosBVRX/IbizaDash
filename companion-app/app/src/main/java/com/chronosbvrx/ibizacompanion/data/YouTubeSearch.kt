package com.chronosbvrx.ibizacompanion.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.chronosbvrx.ibizacompanion.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves a YouTube Music track from its title + artist using the YouTube Data
 * API v3, then opens the exact video with a deep link (which starts playback).
 */
object YouTubeSearch {
    private val API_KEY: String get() = BuildConfig.YOUTUBE_API_KEY
    private const val PACKAGE = "com.google.android.apps.youtube.music"

    fun play(context: Context, title: String, artist: String) {
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (query.isBlank()) return
        val key = API_KEY
        if (key.isBlank()) {
            // Sin key configurada, abrir la búsqueda de YouTube Music.
            openSearch(context, query)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val videoId = searchVideoId(query, key)
            withContext(Dispatchers.Main) {
                if (!videoId.isNullOrBlank()) {
                    openVideo(context, videoId)
                } else {
                    openSearch(context, query)
                }
            }
        }
    }

    private fun searchVideoId(query: String, key: String): String? {
        return try {
            val q = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&maxResults=1&q=$q&key=$key"
            val conn = URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val items = JSONObject(text).optJSONArray("items") ?: return null
            val first = items.optJSONObject(0) ?: return null
            first.optJSONObject("id")?.optString("videoId")
        } catch (e: Exception) {
            null
        }
    }

    private fun openVideo(context: Context, videoId: String) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://music.youtube.com/watch?v=$videoId")
            ).setPackage(PACKAGE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            openSearch(context, videoId)
        }
    }

    private fun openSearch(context: Context, query: String) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query))
            ).setPackage(PACKAGE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}