package com.chronosbvrx.ibizadash.car

import android.content.Context
import com.chronosbvrx.ibizadash.Prefs
import com.chronosbvrx.ibizadash.obd.ObdTelemetry

enum class Severity { GOOD, INFO, WARNING, CRITICAL }

data class HumanInsight(
    val id: String,
    val title: String,
    val subtitle: String,
    val severity: Severity,
    val voiceOrBannerAlert: Boolean = false
)

data class HumanVehicleStatus(
    val headlineStatus: String,
    val headlineSeverity: Severity,
    val thermalMessage: HumanInsight,
    val electricalMessage: HumanInsight,
    val fuelAutonomyMessage: HumanInsight,
    val maintenanceMessage: HumanInsight,
    val drivingCoachMessage: HumanInsight?
)

object HumanCarAssistant {

    private const val OIL_INTERVAL_KM = 10000f
    private const val LOW_BATTERY_V = 11.9f
    private const val MIN_CHARGE_V = 13.1f
    private const val MAX_CHARGE_V = 14.8f

    fun evaluate(context: Context, state: ObdTelemetry, speedKmh: Int): HumanVehicleStatus {
        val thermal = thermalMessage(state)
        val electrical = electricalMessage(state)
        val fuel = fuelMessage(state)
        val maintenance = maintenanceMessage(context, state)
        val coach = drivingCoach(state, speedKmh)

        val headlineSeverity = listOf(thermal, electrical, maintenance)
            .maxByOrNull { it.severity.ordinal }?.severity ?: Severity.GOOD
        val headlineStatus = when (headlineSeverity) {
            Severity.GOOD -> "Todo en orden • Listo para viajar"
            Severity.INFO -> "Todo bien • Revisa las sugerencias"
            Severity.WARNING -> "Atención • Revisa los avisos"
            Severity.CRITICAL -> "¡Atención! Revisa el auto"
        }

        return HumanVehicleStatus(
            headlineStatus = headlineStatus,
            headlineSeverity = headlineSeverity,
            thermalMessage = thermal,
            electricalMessage = electrical,
            fuelAutonomyMessage = fuel,
            maintenanceMessage = maintenance,
            drivingCoachMessage = coach
        )
    }

    private fun thermalMessage(state: ObdTelemetry): HumanInsight {
        val t = state.coolantC
        if (t == null) {
            return HumanInsight("thermal", "Temperatura no leída", "Esperando datos del motor", Severity.INFO)
        }
        return when {
            t < 60f -> HumanInsight(
                "thermal_cold", "Motor frío, maneja suave",
                "Evita acelerar fuerte mientras toma temperatura", Severity.INFO
            )
            t <= 104f -> HumanInsight(
                "thermal_ok", "Temperatura perfecta",
                "El motor trabaja en su rango ideal", Severity.GOOD
            )
            t <= 111f -> HumanInsight(
                "thermal_warm", "El auto está calentándose más de lo normal",
                "Modera la marcha y vigila el tráfico", Severity.WARNING, true
            )
            else -> HumanInsight(
                "thermal_hot", "¡PELIGRO! El auto está muy caliente",
                "Detente en un lugar seguro y no abras el tapón del anticongelante", Severity.CRITICAL, true
            )
        }
    }

    private fun electricalMessage(state: ObdTelemetry): HumanInsight {
        val v = state.batteryV
        val rpm = state.rpm ?: 0
        if (v == null) {
            return HumanInsight("elec", "Batería no leída", "Esperando datos eléctricos", Severity.INFO)
        }
        return if (rpm < 300) {
            if (v < LOW_BATTERY_V) HumanInsight(
                "bat_weak", "Batería débil",
                "Enciende el motor pronto para no quedarte sin carga", Severity.WARNING, true
            ) else HumanInsight(
                "bat_ok_off", "Batería OK",
                "Sistema eléctrico en reposo", Severity.GOOD
            )
        } else {
            when {
                v < MIN_CHARGE_V -> HumanInsight(
                    "alt_fail", "Falla en sistema de carga (Alternador)",
                    "La batería no se está recargando correctamente", Severity.CRITICAL, true
                )
                v <= MAX_CHARGE_V -> HumanInsight(
                    "bat_charge_ok", "Batería y carga al 100%",
                    "Sistema eléctrico funcionando sin problemas", Severity.GOOD
                )
                else -> HumanInsight(
                    "bat_overcharge", "Voltaje alto",
                    "Revisa el regulador del alternador", Severity.WARNING, true
                )
            }
        }
    }

    private fun fuelMessage(state: ObdTelemetry): HumanInsight {
        // No virtual fuel: the 6J does not report level over generic OBD.
        val pct = state.fuelPct
            ?: return HumanInsight(
                "fuel_na", "Sin lectura de combustible",
                "Este coche no expone nivel por OBD genérico", Severity.INFO
            )
        return when {
            pct > 25f -> HumanInsight(
                "fuel_ok", "Combustible al ${pct.toInt()} %",
                "Nivel suficiente para el trayecto", Severity.GOOD
            )
            pct >= 12f -> HumanInsight(
                "fuel_warn", "Combustible al ${pct.toInt()} % • Recomiendo recargar",
                "Busca una gasolinera en tu próximo trayecto", Severity.WARNING, true
            )
            else -> HumanInsight(
                "fuel_crit", "Reserva de combustible: ${pct.toInt()} %",
                "Recarga gasolina lo antes posible", Severity.CRITICAL, true
            )
        }
    }

    private fun maintenanceMessage(context: Context, state: ObdTelemetry): HumanInsight {
        val totalKm = Prefs.totalOdometerKm(context)
        val lastOilKm = Prefs.lastOilChangeKm(context)
        val kmSinceOil = totalKm - lastOilKm
        val kmRestantesAceite = (OIL_INTERVAL_KM - kmSinceOil).toInt()

        val daysSinceCheck = (System.currentTimeMillis() - Prefs.lastOilLevelCheckMs(context)) / (1000L * 60 * 60 * 24)
        if (kmRestantesAceite <= 0) {
            return HumanInsight(
                "oil_due", "Ya toca cambio de aceite y filtro",
                "Has superado el límite recomendado de 10,000 km", Severity.CRITICAL
            )
        }
        if (kmRestantesAceite in 1..1000) {
            return HumanInsight(
                "oil_soon", "Próximo cambio de aceite en $kmRestantesAceite km",
                "Ve programando tu cita en el taller", Severity.WARNING
            )
        }
        if (daysSinceCheck > 30) {
            return HumanInsight(
                "oil_check", "Recomendación: Revisa la bayoneta de aceite",
                "Han pasado más de 30 días desde tu última revisión manual de nivel", Severity.INFO
            )
        }
        val dtcCount = state.dtcCount ?: 0
        val milOn = state.milOn == true
        if (milOn || dtcCount > 0) {
            return HumanInsight(
                "dtc", "Atención: El motor reporta $dtcCount anomalía(s)",
                "Lleva el auto a revisión. Toca aquí para ver detalles.", Severity.WARNING, true
            )
        }
        return HumanInsight(
            "oil_ok", "Aceite y servicio al día",
            "Faltan $kmRestantesAceite km para el próximo cambio", Severity.GOOD
        )
    }

    private fun drivingCoach(state: ObdTelemetry, speedKmh: Int): HumanInsight? {
        val rpm = state.rpm ?: return null
        val coolant = state.coolantC
        val load = state.loadPct
        if (coolant != null && coolant < 65f && rpm > 3000) {
            return HumanInsight(
                "coach_cold", "Cuidado: Estás exigiendo el motor mientras sigue frío",
                "Espera a que tome temperatura", Severity.WARNING
            )
        }
        if (speedKmh > 15 && rpm > 3400) {
            return HumanInsight(
                "coach_shift", "Sugerencia: Sube de marcha para ahorrar gasolina y cuidar el motor",
                "RPM altas a velocidad moderada", Severity.INFO
            )
        }
        if (rpm in 1400..2500 && (load ?: 100f) < 60f) {
            return HumanInsight(
                "coach_smooth", "Conducción suave y ahorradora",
                "Buen rango de eficiencia", Severity.GOOD
            )
        }
        return null
    }
}
