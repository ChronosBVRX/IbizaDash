package com.chronosbvrx.ibizacompanion.feature.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors
import com.chronosbvrx.ibizacompanion.core.state.CompanionState
import com.chronosbvrx.ibizacompanion.service.MediaNotificationListener
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaScreen() {
    val context = LocalContext.current
    val state by CompanionState.state.collectAsState()

    var currentCtrl by remember { mutableStateOf<MediaController?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var showAppChooser by remember { mutableStateOf(false) }
    var favoritePkg by remember { mutableStateOf(com.chronosbvrx.ibizacompanion.data.SearchPrefs.favoritePackage(context)) }
    var showQueue by remember { mutableStateOf(true) }
    var queue by remember { mutableStateOf<List<android.media.session.MediaSession.QueueItem>>(emptyList()) }
    var queueTitle by remember { mutableStateOf<String?>(null) }
    var actions by remember { mutableStateOf(0L) }
    var showNoAppsDialog by remember { mutableStateOf(false) }
    val installedApps = remember { detectMusicApps(context) }
    val runSearch: () -> Unit = {
        val fav = favoritePkg
        if (fav != null && installedApps.any { it.packageName == fav }) {
            openSearch(context, fav, query)
        } else if (installedApps.size > 1) {
            showAppChooser = true
        } else if (installedApps.size == 1) {
            openSearch(context, installedApps.first().packageName, query)
        } else {
            // Antes el botón no hacía nada visible si no había reproductor.
            showNoAppsDialog = true
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val cn = ComponentName(context, MediaNotificationListener::class.java)
            val raw = try { msm?.getActiveSessions(cn) ?: emptyList() } catch (_: Exception) { emptyList() }
            val playing = raw.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            val preferredPlaying = playing.firstOrNull { it.packageName == "com.google.android.apps.youtube.music" }
                ?: playing.firstOrNull { it.packageName == "com.spotify.music" }
                ?: playing.firstOrNull()

            if (preferredPlaying != null) {
                currentCtrl = preferredPlaying
            } else {
                // Keep the last/paused track visible so the play button remains
                // and returning to the app doesn't look like "nothing playing".
                val currentPkg = currentCtrl?.packageName
                val stillThere = raw.firstOrNull { it.packageName == currentPkg && it.metadata != null }
                val pausedWithMeta = raw.firstOrNull {
                    it.metadata != null && it.playbackState?.state == PlaybackState.STATE_PAUSED
                }
                currentCtrl = stillThere ?: pausedWithMeta
            }

            val c = currentCtrl
            isPlaying = c?.playbackState?.state == PlaybackState.STATE_PLAYING
            positionMs = c?.playbackState?.position ?: 0L
            durationMs = c?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            actions = c?.playbackState?.actions ?: 0L
            queue = try { c?.queue ?: emptyList() } catch (_: Exception) { emptyList() }
            queueTitle = try { c?.queueTitle?.toString() } catch (_: Exception) { null }
            delay(1000L)
        }
    }

    val ctrl = currentCtrl
    val md = ctrl?.metadata
    val np = com.chronosbvrx.ibizacompanion.core.state.NowPlayingState(
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: state.nowPlaying.title,
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: state.nowPlaying.artist,
        album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: state.nowPlaying.album,
        artwork = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: state.nowPlaying.artwork,
        isPlaying = isPlaying
    )
    val artUri = md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        ?: md?.getString(MediaMetadata.METADATA_KEY_ART_URI)
    val appLabel = ctrl?.let { appName(context, it.packageName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MULTIMEDIA", style = MaterialTheme.typography.headlineMedium, color = IbizaColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            if (ctrl != null) {
                IconButton(onClick = { showQueue = !showQueue }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (showQueue) Icons.Default.ExpandMore else Icons.Default.QueueMusic,
                        contentDescription = "Cola",
                        tint = IbizaColors.Primary
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            if (appLabel != null) {
                Surface(shape = RoundedCornerShape(20.dp), color = IbizaColors.SurfaceElevated) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(IbizaColors.Success, RoundedCornerShape(4.dp)))
                        Spacer(Modifier.width(6.dp))
                        Text(appLabel, style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary, maxLines = 1)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (ctrl == null) {
            val last = state.nowPlaying
            Surface(shape = RoundedCornerShape(20.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (last.title.isNotBlank()) {
                        // Show the last known track so returning doesn't look empty.
                        Surface(shape = RoundedCornerShape(14.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(150.dp)) {
                            if (last.artwork != null) {
                                Image(
                                    bitmap = last.artwork!!.asImageBitmap(),
                                    contentDescription = "Portada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = IbizaColors.TextSecondary, modifier = Modifier.size(56.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(last.title, style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(last.artist.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = IbizaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Pausado o sesión cerrada", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        if (last.packageName.isNotBlank()) {
                            Button(
                                onClick = {
                                    val i = context.packageManager.getLaunchIntentForPackage(last.packageName)
                                    if (i != null) context.startActivity(i)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IbizaColors.Primary, contentColor = IbizaColors.Background),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Abrir app para reanudar")
                            }
                        }
                    } else {
                        Icon(Icons.Default.MusicOff, contentDescription = null, tint = IbizaColors.TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Sin reproducción activa", style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary)
                        Text("Abre Spotify o YouTube Music", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                    }
                }
            }
        } else {
            // Player shrinks and queue slides in below with a premium animation.
            val playerWeight by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showQueue) 0.40f else 1f,
                animationSpec = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "playerWeight"
            )
            val queueWeight by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showQueue) 0.60f else 0f,
                animationSpec = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "queueWeight"
            )
            val artSize by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (showQueue) 92.dp else 150.dp,
                animationSpec = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "artSize"
            )

            Surface(shape = RoundedCornerShape(22.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxWidth().weight(playerWeight)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(if (showQueue) 10.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(shape = RoundedCornerShape(16.dp), color = IbizaColors.SurfaceElevated, modifier = Modifier.size(artSize)) {
                        if (np.artwork != null) {
                            Image(
                                bitmap = np.artwork!!.asImageBitmap(),
                                contentDescription = "Portada",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (!artUri.isNullOrBlank()) {
                            AsyncImage(
                                model = artUri,
                                contentDescription = "Portada",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = IbizaColors.TextSecondary, modifier = Modifier.size(56.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(if (showQueue) 6.dp else 12.dp))
                    Text(np.title.ifBlank { "Sin título" }, style = MaterialTheme.typography.titleMedium, color = IbizaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np.artist.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = IbizaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!showQueue) {
                        Text(np.album.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val trackNo = md?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER) ?: 0L
                        val totalTracks = md?.getLong(MediaMetadata.METADATA_KEY_NUM_TRACKS) ?: 0L
                        if (trackNo > 0 && totalTracks > 0) {
                            Text("Pista $trackNo de $totalTracks", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = IbizaColors.Primary,
                        trackColor = IbizaColors.SurfaceElevated
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(fmt(positionMs), style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                        Text(fmt(durationMs), style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary)
                    }
                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canPrev = (actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
                        val canNext = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
                        val canPause = (actions and PlaybackState.ACTION_PAUSE) != 0L
                        val canPlay = (actions and PlaybackState.ACTION_PLAY) != 0L
                        IconButton(
                            onClick = { if (canPrev) ctrl.transportControls.skipToPrevious() },
                            enabled = canPrev,
                            modifier = Modifier.size(if (showQueue) 38.dp else 48.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = if (canPrev) IbizaColors.TextPrimary else IbizaColors.TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(if (showQueue) 22.dp else 26.dp))
                        }
                        IconButton(
                            onClick = {
                                val playing = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
                                if (playing && canPause) ctrl.transportControls.pause()
                                else if (!playing && canPlay) ctrl.transportControls.play()
                            },
                            enabled = if (isPlaying) canPause else canPlay,
                            modifier = Modifier.size(if (showQueue) 48.dp else 60.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pausa" else "Play",
                                tint = IbizaColors.Primary,
                                modifier = Modifier.size(if (showQueue) 30.dp else 38.dp)
                            )
                        }
                        IconButton(
                            onClick = { if (canNext) ctrl.transportControls.skipToNext() },
                            enabled = canNext,
                            modifier = Modifier.size(if (showQueue) 38.dp else 48.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Siguiente", tint = if (canNext) IbizaColors.TextPrimary else IbizaColors.TextSecondary.copy(alpha = 0.4f), modifier = Modifier.size(if (showQueue) 22.dp else 26.dp))
                        }
                    }
                    if (!showQueue) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = {
                            openTrack(
                                context = context,
                                pkg = ctrl.packageName,
                                title = np.title,
                                artist = np.artist,
                                mediaId = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                                mediaUri = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)
                            )
                        }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reproducir en la app", color = IbizaColors.Primary)
                        }
                    }
                }
            }

            // Inline queue: slides/fades in below the shrunken player.
            Box(Modifier.fillMaxWidth().weight(queueWeight.coerceAtLeast(0.001f))) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showQueue,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(260)
                    ) + androidx.compose.animation.slideInVertically(
                        animationSpec = androidx.compose.animation.core.tween(380),
                        initialOffsetY = { it / 3 }
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(180)
                    ) + androidx.compose.animation.slideOutVertically(
                        animationSpec = androidx.compose.animation.core.tween(240),
                        targetOffsetY = { it / 3 }
                    )
                ) {
                    Surface(shape = RoundedCornerShape(18.dp), color = IbizaColors.Surface, modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(queueTitle ?: "COLA DE REPRODUCCIÓN", style = MaterialTheme.typography.labelSmall, color = IbizaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(6.dp))
                            if (queue.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "La app no comparte su cola",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IbizaColors.TextSecondary
                                    )
                                }
                            } else {
                            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                                items(queue.size) { idx ->
                                    val item = queue[idx]
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Tapping the title/row opens the track in the source app.
                                                openTrack(
                                                    context = context,
                                                    pkg = ctrl.packageName,
                                                    title = item.description?.title?.toString().orEmpty(),
                                                    artist = item.description?.subtitle?.toString().orEmpty(),
                                                    mediaId = item.description?.mediaId,
                                                    mediaUri = item.description?.mediaUri?.toString()
                                                )
                                            }
                                            .padding(vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${idx + 1}", style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary, modifier = Modifier.width(26.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                item.description?.title?.toString() ?: "—",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = IbizaColors.TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            item.description?.subtitle?.toString()?.takeIf { it.isNotBlank() }?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall, color = IbizaColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }

                                    }
                                }
                            }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Compact search row
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar música…", color = IbizaColors.TextSecondary, maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = IbizaColors.Primary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IbizaColors.Primary,
                    unfocusedBorderColor = IbizaColors.Border,
                    focusedTextColor = IbizaColors.TextPrimary,
                    unfocusedTextColor = IbizaColors.TextPrimary,
                    cursorColor = IbizaColors.Primary
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(IbizaColors.Primary, RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { runSearch() },
                        onLongClick = { if (installedApps.size > 1) showAppChooser = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = IbizaColors.Background)
            }
        }
    }

    if (showNoAppsDialog) {
        AlertDialog(
            onDismissRequest = { showNoAppsDialog = false },
            title = { Text("Sin app de música", color = IbizaColors.TextPrimary) },
            text = {
                Text(
                    "Para buscar y reproducir desde aquí instala Spotify o YouTube Music. " +
                        "La app puede seguir mostrando lo que suene en otras sesiones.",
                    color = IbizaColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNoAppsDialog = false
                    try {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/search?q=spotify%20o%20youtube%20music&c=apps")
                            )
                        )
                    } catch (_: Exception) {
                    }
                }) { Text("Buscar en Play Store", color = IbizaColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showNoAppsDialog = false }) {
                    Text("Cerrar", color = IbizaColors.TextSecondary)
                }
            },
            containerColor = IbizaColors.Surface
        )
    }

    if (showAppChooser) {
        AlertDialog(
            onDismissRequest = { showAppChooser = false },
            title = { Text("¿En qué app buscar?", color = IbizaColors.TextPrimary) },
            text = {
                Column {
                    Text("Elige la app para buscar \"$query\".", color = IbizaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    installedApps.forEach { (pkg, label) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = IbizaColors.SurfaceElevated,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(
                                Modifier.clickable {
                                    showAppChooser = false
                                    openSearch(context, pkg, query)
                                }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = IbizaColors.Primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = IbizaColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    if (favoritePkg == pkg) {
                                        Text("Búsqueda directa", color = IbizaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val newFav = if (favoritePkg == pkg) null else pkg
                                        favoritePkg = newFav
                                        com.chronosbvrx.ibizacompanion.data.SearchPrefs.setFavoritePackage(context, newFav)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (favoritePkg == pkg) Icons.Default.Star else Icons.Default.StarBorder,
                                        contentDescription = "Marcar como favorita",
                                        tint = if (favoritePkg == pkg) IbizaColors.Primary else IbizaColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppChooser = false }) {
                    Text("Cancelar", color = IbizaColors.TextSecondary)
                }
            },
            containerColor = IbizaColors.Surface
        )
    }

}

private fun openTrack(
    context: Context,
    pkg: String,
    title: String,
    artist: String,
    mediaId: String?,
    mediaUri: String?
) {
    android.util.Log.d("MediaOpen", "pkg=$pkg title=$title artist=$artist mediaId=$mediaId mediaUri=$mediaUri")
    // 1) Direct media URI if the app exposes it.
    if (!mediaUri.isNullOrBlank()) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mediaUri)))
            return
        } catch (_: Exception) {
        }
    }
    // 2) Spotify track deep link. Spotify's mediaId is often the full
    //    "spotify:track:<id>" URI, so don't prefix it twice.
    if (pkg == "com.spotify.music" && !mediaId.isNullOrBlank()) {
        val uri = if (mediaId.startsWith("spotify:")) mediaId else "spotify:track:$mediaId"
        try {
            com.chronosbvrx.ibizacompanion.data.PlaybackReturn.markRequested()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(pkg))
            return
        } catch (_: Exception) {
        }
    }
    // 3) YouTube Music: use a videoId if available; otherwise resolve it with
    //    the YouTube Data API (title + artist) and open the exact video.
    if (pkg == "com.google.android.apps.youtube.music") {
        val videoId = mediaId?.takeIf { it.isNotBlank() }
            ?: mediaUri?.let { uri ->
                runCatching { Uri.parse(uri).getQueryParameter("v") }.getOrNull()
            }
        if (!videoId.isNullOrBlank()) {
            try {
                com.chronosbvrx.ibizacompanion.data.PlaybackReturn.markRequested()
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=$videoId"))
                        .setPackage(pkg)
                )
                return
            } catch (_: Exception) {
            }
        }
        // No ID in the MediaSession: resolve via YouTube Data API.
        com.chronosbvrx.ibizacompanion.data.PlaybackReturn.markRequested()
        com.chronosbvrx.ibizacompanion.data.YouTubeSearch.play(context, title, artist)
        return
    }
    // 4) Fallback: search by title + artist in the source app.
    val q = Uri.encode("$title $artist".trim())
    when (pkg) {
        "com.spotify.music" -> {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q")).setPackage(pkg)) } catch (_: Exception) {}
        }
        "com.google.android.apps.youtube.music" -> {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$q")).setPackage(pkg)) } catch (_: Exception) {}
        }
        else -> {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q"))) } catch (_: Exception) {}
        }
    }
}

private data class MusicApp(val packageName: String, val label: String)

private fun detectMusicApps(context: Context): List<MusicApp> {
    val candidates = listOf(
        "com.spotify.music" to "Spotify",
        "com.google.android.apps.youtube.music" to "YouTube Music"
    )
    return candidates.mapNotNull { (pkg, fallback) ->
        try {
            context.packageManager.getApplicationInfo(pkg, 0)
            MusicApp(pkg, fallback)
        } catch (_: Exception) {
            null
        }
    }
}

private fun openSearch(context: Context, pkg: String, query: String) {
    val q = Uri.encode(query)
    when (pkg) {
        "com.spotify.music" -> openSpotifySearch(context, query)
        "com.google.android.apps.youtube.music" -> openYouTubeMusicSearch(context, query)
        else -> {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$q")).setPackage(pkg))
            } catch (_: Exception) {
            }
        }
    }
}

private fun appName(context: Context, pkg: String): String {
    return try {
        val ai = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        when (pkg) {
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            "com.spotify.music" -> "Spotify"
            else -> pkg.substringAfterLast('.')
        }
    }
}

private fun fmt(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun openSpotifySearch(context: Context, query: String) {
    val q = Uri.encode(query)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q")).setPackage("com.spotify.music"))
    } catch (_: Exception) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$q"))) } catch (_: Exception) {}
    }
}

private fun openYouTubeMusicSearch(context: Context, query: String) {
    val q = Uri.encode(query)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$q")).setPackage("com.google.android.apps.youtube.music"))
    } catch (_: Exception) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$q"))) } catch (_: Exception) {}
    }
}