package com.chronosbvrx.ibizacompanion.core.navigation

sealed class IbizaRoute(val route: String, val label: String) {
    data object Dashboard : IbizaRoute("dashboard", "Inicio")
    data object Navigation : IbizaRoute("navigation", "Rutas")
    data object Media : IbizaRoute("media", "Media")
    data object Connection : IbizaRoute("connection", "Conexión")
    data object Settings : IbizaRoute("settings", "Ajustes")
}