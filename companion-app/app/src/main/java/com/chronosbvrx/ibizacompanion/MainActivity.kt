package com.chronosbvrx.ibizacompanion

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chronosbvrx.ibizacompanion.core.design.theme.IbizaTheme
import com.chronosbvrx.ibizacompanion.core.navigation.IbizaRoute
import com.chronosbvrx.ibizacompanion.feature.connection.ConnectionScreen
import com.chronosbvrx.ibizacompanion.feature.dashboard.DashboardScreen
import com.chronosbvrx.ibizacompanion.feature.media.MediaScreen
import com.chronosbvrx.ibizacompanion.feature.navigation.NavigationScreen
import com.chronosbvrx.ibizacompanion.feature.settings.SettingsScreen
import com.chronosbvrx.ibizacompanion.service.MediaBridgeService
import com.chronosbvrx.ibizacompanion.service.MediaNotificationListener

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = android.graphics.Color.parseColor("#0B0E14")
        window.navigationBarColor = android.graphics.Color.parseColor("#151A23")
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        requestRuntimePermissions()

        // Start foreground service
        val serviceIntent = Intent(this, MediaBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)

        maybeInstallSelfUpdate(intent)

        setContent {
            IbizaTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF080D14),
                    bottomBar = {
                        NavigationBar(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF0A1220),
                            tonalElevation = 8.dp
                        ) {
                            listOf(
                                IbizaRoute.Dashboard to Icons.Default.Dashboard,
                                IbizaRoute.Navigation to Icons.Default.Navigation,
                                IbizaRoute.Media to Icons.Default.LibraryMusic,
                                IbizaRoute.Connection to Icons.Default.Wifi,
                                IbizaRoute.Settings to Icons.Default.Settings
                            ).forEach { (route, icon) ->
                                NavigationBarItem(
                                    selected = currentRoute == route.route,
                                    onClick = {
                                        navController.navigate(route.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = route.label) },
                                    label = {
                                        Text(
                                            route.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            softWrap = false,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors.Primary,
                                        selectedTextColor = com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors.Primary,
                                        unselectedIconColor = com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors.TextSecondary,
                                        unselectedTextColor = com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors.TextSecondary,
                                        indicatorColor = com.chronosbvrx.ibizacompanion.core.design.theme.IbizaColors.SurfaceElevated
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = IbizaRoute.Dashboard.route,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(IbizaRoute.Dashboard.route) {
                            DashboardScreen(
                                onOpenMedia = {
                                    navController.navigate(IbizaRoute.Media.route) { launchSingleTop = true }
                                },
                                onOpenRoute = {
                                    navController.navigate(IbizaRoute.Navigation.route) { launchSingleTop = true }
                                },
                                onOpenConnection = {
                                    navController.navigate(IbizaRoute.Connection.route) { launchSingleTop = true }
                                }
                            )
                        }
                        composable(IbizaRoute.Navigation.route) { NavigationScreen() }
                        composable(IbizaRoute.Media.route) { MediaScreen() }
                        composable(IbizaRoute.Connection.route) { ConnectionScreen() }
                        composable(IbizaRoute.Settings.route) { SettingsScreen() }
                    }
                }
            }
        }

        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                window.insetsController?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeInstallSelfUpdate(intent)
    }

    /**
     * La notificación de "instalar actualización" abre la app con este extra.
     * Android exige confirmar en el diálogo del sistema; si falta el permiso
     * de orígenes desconocidos, se abre primero el ajuste correspondiente.
     */
    private fun maybeInstallSelfUpdate(intent: Intent?) {
        if (intent?.getBooleanExtra(MediaBridgeService.EXTRA_INSTALL_SELF_UPDATE, false) != true) return
        intent.removeExtra(MediaBridgeService.EXTRA_INSTALL_SELF_UPDATE)
        window.decorView.post {
            if (!com.chronosbvrx.ibizacompanion.update.CompanionUpdater.canInstall(this)) {
                com.chronosbvrx.ibizacompanion.update.CompanionUpdater
                    .openInstallPermissionSettings(this)
            } else {
                com.chronosbvrx.ibizacompanion.update.CompanionUpdater.install(this)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == com.chronosbvrx.ibizacompanion.data.SpotifyRemote.AUTH_REQUEST_CODE) {
            com.chronosbvrx.ibizacompanion.data.SpotifyRemote.handleAuthResult(resultCode, data, this) { ok ->
                if (ok) {
                    com.chronosbvrx.ibizacompanion.data.SpotifyRemote.play("spotify:track:4uLU6hMCjMI75M1A2tKUQC")
                }
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf<String>()
        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
    }
}