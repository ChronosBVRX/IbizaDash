> **Documento histórico (2026-11-05/07).** La arquitectura vigente usa Wi-Fi Direct como
> único canal de datos y OTA desde el teléfono. Ver `README.md` y
> `docs/WIFI_DIRECT_TRANSPORT.md`.

# Ibiza Automotive Suite Pro — Especificación de arquitectura y UI

**Versión:** 1.0 · 2026-11-05  
**Target:** SEAT Ibiza 2009–2013 · head unit SPRD `sp7731e` · Android 8.1 (API 27) · 1280×720 px @160 dpi · 2 GB RAM · 32-bit  
**Paquete:** `com.chronosbvrx.ibizadash`  
**Estado:** núcleo de lienzo continuo, Cockpit, Navegación, Media Center, Vehículo, Quick Settings y alertas **implementados y verificados en emulador**. Radio nativa, TPMS/RPM reales y turn-by-turn quedan como fases con contrato definido.

---

## 0. Resumen ejecutivo

IbizaDash no es una pantalla de inicio que abre apps: es una **suite cerrada de una sola Activity** con un **lienzo continuo**. El mapa, el HUD y el dock nunca se destruyen; los módulos aparecen cambiando únicamente la **fracción del split** y la capa visible. No hay `FragmentTransaction`, no hay pantallas negras y no se recarga el mapa.

Reglas no negociables:

1. **Una sola Activity** (`MainActivity`) y un solo `MapView` vivo.
2. **Un solo estado de foco** (`Focus`) que muta la UI; ninguna pantalla se apila.
3. **La velocidad y la marcha nunca desaparecen**: HUD grande en Cockpit, velocidad mini en Navegación, cápsula HUD en módulos.
4. **Cero blur en tiempo real** (API 27 no tiene `RenderEffect`): el frosted glass se simula con capas de color, borde de 1 px y gradientes; el glow ambiental es un drawable precalculado.
5. **Views nativas + XML**, sin Compose, para el Cortex-A7.
6. Presupuesto: **< 100 MB PSS**, **60 fps** en transiciones, arranque percibido < 2.5 s en el estéreo.

---

## 1. Hardware y restricciones reales

| Dato | Valor | Implicación de diseño |
|---|---|---|
| SoC | Spreadtrum SC7731E, 4× Cortex-A7 @1.3 GHz, 32-bit | Nada de Compose, blurs ni animaciones de sombra costosas |
| RAM | 2 GB (~2.1 M kB) + zram | PSS objetivo < 100 MB; evitar bitmaps grandes |
| Pantalla | 1280×720 px, 160 dpi → **1280×720 dp** | Mucho ancho, poca altura útil (dock 84 dp + márgenes) |
| Android | 8.1.0, API 27 | Sin `RenderEffect`, sin `SharedElement` moderno, sin blur nativo |
| GPU | Mali-400 MP2 (software-ish en emulador) | Animaciones de compositor (alpha/translation), no de layout continuo |
| Almacenamiento | ~20 GB libres | Caché de tiles y arte de álbum sin problema |
| Red | WiFi + Bluetooth del teléfono | El clima y los tiles dependen de red; el CAN no |

**Zona útil:** 720 − 84 (dock) − 12 (margen) = **624 dp** de alto para el canvas; los paneles usan `marginBottom = 104 dp` y `marginTop = 16 dp` → **600 dp** de panel.

---

## 2. Design system (tokens)

### 2.1 Color

| Token | HEX / RGBA | Uso |
|---|---|---|
| `obsidian_900` | `#0B0F17` | Fondo raíz |
| `obsidian_800` | `#0F1520` | Paneles base |
| `obsidian_700` | `#121824` | Tarjetas, chips |
| `obsidian_600` | `#182130` | Elevación de tarjeta |
| `glass_surface` | `#CC0F1622` (80 %) | Panel de cristal (legibilidad primero) |
| `glass_surface_soft` | `#99121A26` (60 %) | Tarjeta secundaria |
| `glass_border` | `#1FFFFFFF` (12 %) | Borde interior de 1 px |
| `line` | `#14FFFFFF` (8 %) | Separadores 1 px |
| `accent` / `accent_sky` | `#38BDF8` | Interactivo primario, iconos activos |
| `accent_deep` | `#0284C7` | Fin de gradiente del botón play |
| `accent_electric` | `#00D2FF` | Datos en vivo, glow, progreso |
| `accent_blue` | `#0099FF` | Enlaces y estados de foco |
| `accent_cyan` | `#22D3EE` | Cabezal de progreso / gauges |
| `amber` | `#FF9500` | Advertencia, llamada, Sport |
| `copper` | `#D97706` | Acento deportivo secundario |
| `danger` / `red` | `#FF3B30` | Puerta abierta, TPMS bajo, MIL |
| `green` | `#10B981` | Batería BT, estado OK |
| `text_primary` | `#F8FAFC` | HUD, títulos |
| `text_secondary` | `#CBD5E1` | Valores |
| `text_muted` | `#94A3B8` | Labels, iconos inactivos |
| `text_faint` | `#64748B` | Timestamps, ayudas |

> El “15–20 % de opacidad” del glassmorphism se aplica a la **capa blanca de brillo** (`#14FFFFFF`–`#33FFFFFF`), no al panel completo: un panel 15 % opaco sobre el mapa haría ilegible el HUD a pleno sol. El panel es 80 % obsidiana + 8–12 % de borde blanco, que es el estándar de PCM/MMI.

### 2.2 Tipografía

| Rol | Tamaño | Peso | Tracking | Notas |
|---|---|---|---|---|
| HUD velocidad | 96 sp | `sans-serif-light` | −0.04 | `fontFeatureSettings="tnum"` |
| Velocidad mini | 64 sp | light | −0.04 | Misma métrica que el HUD |
| Hora | 34 sp | medium | +0.01 | Tabular |
| Título de módulo | 20–24 sp | medium | 0 | “VEHÍCULO”, “MEDIA CENTER” |
| Título de pista | 14–24 sp | regular/medium | 0 | 1 línea + ellipsis |
| Valor | 12–15 sp | regular | 0 | Tabular en cifras |
| Label | 9 sp | medium | +0.12 | MAYÚSCULAS |
| Microcopy | 9–11 sp | regular | 0 | “Toca para abrir…” |

Regla: **todo dígito que cambie en vivo usa `tnum`** (velocidad, tiempo, presión, temperatura, frecuencia).

### 2.3 Radios y elevación

| Token | Valor | Uso |
|---|---|---|
| `radius_panel` | 24 dp | Panel de cristal |
| `radius_card` | 20 dp | Tarjeta media / clima |
| `radius_chip` | 26 dp | Chip Bluetooth, pills |
| `radius_dock` | 28 dp | Isla del dock |
| `radius_art` | 8 dp | Carátula |
| `radius_metric` | 14 dp | Mini-widgets |
| `elev_panel` | 12 dp | Sombra del panel |
| `elev_card` | 10 dp | Tarjeta media |
| `elev_dock` | 18 dp | Dock |

Sombras: usar `elevation` (compositor) y **nunca** `layer-list` con blur; en API 27 una sombra de 12–18 dp es barata.

### 2.4 Ergonomía

- Targets primarios: **56–84 dp** (dock, transporte del Media Center, fila de clima del módulo).
- Targets secundarios del cockpit: 48 dp (mínimo Material) y **56 dp** donde el layout lo permite.
- El dock mide **84 dp de alto** y cada ítem ~1/6 del ancho (≈ 208 dp): imposible fallar el toque.
- Navegación máxima: **2 niveles** (dock → módulo). Ajustes avanzados viven en `SettingsActivity` (fuera del canvas, uso estacionario).

### 2.5 Movimiento

| Transición | Duración | Curva |
|---|---|---|
| Split de foco (55→80→25) | **280 ms** | `cubic-bezier(0.2, 0, 0, 1)` (`PathInterpolator`) |
| Fade de la capa entrante | 200 ms | lineal |
| Quick Settings | 260 ms | misma curva |
| Alerta (entrada/salida) | 180 ms | ease-out |
| Cambio de pista / arte | 150 ms crossfade | — |

**Reduced motion:** si `ValueAnimator.areAnimatorsEnabled()` es `false` (head units suelen traer animaciones desactivadas), el split se aplica en un solo frame. Nunca bloquear la UI esperando la animación.

**Regla de oro:** no animar `width/height` de 270 vistas a 60 fps en este SoC. El split actual interpola pesos durante **280 ms** y es aceptable; si el equipo real muestra jank, se cambia a snap + crossfade de 160 ms con una constante (`SPLIT_ANIM_MS = 0`). La percepción premium la da el crossfade, no el costo del layout.

---

## 3. Lienzo continuo (core implementado)

### 3.1 Concepto

```
┌───────────────────────────────────────────────┬──────────────────────────────┐
│ MAPA (siempre vivo, nunca se recrea)          │ CAPA DE FOCO                 │
│ · reloj / fecha / clima                       │ · Cockpit 45 %               │
│ · turn-by-turn pill (si aplica)               │ · Mini HUD 20 %              │
│ · HUD capsule (en módulos)                    │ · Módulo 75 %                │
│                                               │                              │
└───────────────────────────────────────────────┴──────────────────────────────┘
                        DOCK ISLA (84 dp, 6 destinos, indicador cian)
```

El `MapView` es **el mismo objeto** durante toda la sesión. Cambiar de foco solo cambia la fracción del `LinearLayout` (0.55 / 0.80 / 0.25 para el mapa) y la visibilidad de las capas del panel derecho. No se crea ni se destruye ninguna vista de mapa, no se pierde el zoom ni la posición.

### 3.2 Estados y matriz

| Foco | Mapa | Panel derecho | Contenido derecho | Dock activo |
|---|---|---|---|---|
| **COCKPIT** | 55 % | 45 % | Render + HUD + métricas + media + clima | COCKPIT |
| **NAV** | 80 % | 20 % | Velocidad mini + player pill + clima/tiempo | MAPA |
| **MEDIA** | 25 % | 75 % | Media Center (arte 196 dp + transporte 64/84) | MEDIA |
| **VEHÍCULO** | 25 % | 75 % | TPMS + motor + VIN/DTC | VEHÍCULO |
| **RADIO** (fase 2) | 25 % | 75 % | Dial + presets + RDS | RADIO |
| **AJUSTES** | — | overlay | Drawer 400 dp (brillo, noche, mute, WiFi, CAN) | — |

Fracciones implementadas:

```kotlin
private enum class Focus(val fraction: Float, val dockIndex: Int) {
    COCKPIT(0.55f, 0),   // mapa 55 / panel 45
    NAV(0.80f, 1),       // mapa 80 / panel 20
    MEDIA(0.25f, 2),     // mapa 25 / panel 75
    VEHICLE(0.25f, 4)    // mapa 25 / panel 75
}
```

### 3.3 Árbol de capas (`activity_main.xml`)

```
FrameLayout (obsidian_900)
├─ LinearLayout canvas (horizontal)
│  ├─ FrameLayout leftPane (weight 0.55)
│  │  ├─ MapView map                 ← única instancia, cache de tiles
│  │  ├─ View scrim_top / scrim_bottom
│  │  ├─ LinearLayout clock cluster  ← hora, fecha, clima
│  │  ├─ LinearLayout navPill        ← turn-by-turn / “sin destino”
│  │  └─ LinearLayout hudPill        ← velocidad + marcha en módulos
│  └─ FrameLayout rightPane (weight 0.45)
│     ├─ include view_cockpit        ← visible en COCKPIT
│     ├─ include view_mini_hud       ← visible en NAV (GONE por defecto)
│     ├─ include view_media_module   ← visible en MEDIA (GONE)
│     └─ include view_vehicle_module ← visible en VEHÍCULO (GONE)
├─ View nightDim (scrim de modo noche)
├─ LinearLayout alertCapsule         ← alertas CAN (dinamic island)
├─ include sheet_quick_settings      ← drawer lateral
└─ LinearLayout dock                 ← isla flotante
```

### 3.4 State machine (código real)

```kotlin
private fun applyFocus(target: Focus, animate: Boolean = true) {
    val shown = when (target) {
        Focus.COCKPIT -> cockpitPanel
        Focus.NAV     -> miniHud
        Focus.MEDIA   -> mediaModule
        Focus.VEHICLE -> vehicleModule
    }
    val changed = target != focus
    focus = target

    cockpitPanel.visibility   = if (shown === cockpitPanel) View.VISIBLE else View.GONE
    miniHud.visibility        = if (shown === miniHud) View.VISIBLE else View.GONE
    mediaModule.visibility    = if (shown === mediaModule) View.VISIBLE else View.GONE
    vehicleModule.visibility  = if (shown === vehicleModule) View.VISIBLE else View.GONE

    navPill.visibility = if (target == Focus.COCKPIT) View.GONE else View.VISIBLE
    hudPill.visibility =
        if (target == Focus.MEDIA || target == Focus.VEHICLE) View.VISIBLE else View.GONE

    setActiveDock(target.dockIndex)

    // Sólo alpha: costo de compositor, no de layout.
    if (changed && animate) {
        shown.alpha = 0f
        shown.animate().alpha(1f).setDuration(200L).start()
        animateSplit(target.fraction)
    } else {
        setSplit(target.fraction)
    }
}

private fun animateSplit(target: Float) {
    splitAnimator?.cancel()
    val start = splitFraction
    if (kotlin.math.abs(target - start) < 0.0005f) { setSplit(target); return }
    if (!ValueAnimator.areAnimatorsEnabled()) { setSplit(target); return } // head unit sin animaciones
    splitAnimator = ValueAnimator.ofFloat(start, target).apply {
        duration = 280L
        interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
        addUpdateListener { setSplit(it.animatedValue as Float) }
        start()
    }
}

private fun setSplit(fraction: Float) {
    splitFraction = fraction.coerceIn(0.2f, 0.85f)
    val left = leftPane.layoutParams as LinearLayout.LayoutParams
    left.weight = splitFraction
    leftPane.layoutParams = left
    val right = rightPane.layoutParams as LinearLayout.LayoutParams
    right.weight = 1f - splitFraction
    rightPane.layoutParams = right
}
```

### 3.5 Reglas exactas de interacción

1. **Tocar el mapa** (single tap, `MapEventsOverlay`) → `applyFocus(NAV)` si no está en NAV. El mapa no se recrea; solo se re-proyecta al nuevo ancho.
2. **Tocar la tarjeta de media** del Cockpit → `applyFocus(MEDIA)`.
3. **Tocar la píldora mini** del reproductor en NAV → `applyFocus(MEDIA)`.
4. **Tocar el chevron / “COCKPIT COMPLETO”** del mini HUD → `applyFocus(COCKPIT)`.
5. **Dock:** COCKPIT, MAPA, MEDIA, RADIO (abre la app nativa por ahora), VEHÍCULO, APPS.
6. **Back físico:** cierra Quick Settings → si hay foco distinto de COCKPIT, vuelve a COCKPIT → si no, sale.
7. **HUD persistente:** en MEDIA/VEHÍCULO la cápsula de velocidad queda abajo a la izquierda del mapa; en NAV la velocidad es el elemento principal del panel derecho.
8. **Zapping/reverse:** el estado de reversa sigue viniendo de `onReverseStatus` y lanza la cámara; no depende del foco.

### 3.6 Turn-by-turn pill (contrato listo, proveedor pendiente)

La píldora ya existe en el canvas y se muestra en NAV/MEDIA/VEHÍCULO. Hoy dice **“Sin destino activo · Toca para abrir la navegación”**; no simula una ruta. El contrato para conectar un motor de navegación es:

```kotlin
data class TurnInstruction(
    val maneuver: Maneuver,        // LEFT, RIGHT, STRAIGHT, ROUNDABOUT, ARRIVE
    val distanceM: Int,
    val street: String,
    val etaEpochMs: Long?
)

enum class Maneuver { LEFT, RIGHT, STRAIGHT, ROUNDABOUT, ARRIVE }
```

Fuentes posibles, en orden de esfuerzo:

1. **Intent explícito** de un navegador instalado (extra propio) → broadcast receiver.
2. **`NotificationListenerService`** parseando la notificación de Google Maps/Waze (frágil, pero sin SDK).
3. **SDK de mapas** (Mapbox/Google) embebido en el módulo Navegación — requiere API key y suma ~10–20 MB; solo si el usuario lo pide.

La píldora nunca debe tapar el mapa: va anclada arriba-izquierda, debajo del reloj, con ancho máximo 320 dp.

### 3.7 Alertas (dynamic island automotriz)

`alertCapsule` es una cápsula ámbar/roja centrada arriba, con icono + texto y auto-hide a 4.2 s. Reglas implementadas:

- **Velocidad ≥ 120 km/h** → “Velocidad alta · N km/h”, máximo 1 vez por minuto.
- **Llamada entrante/activa** → el chip Bluetooth cambia a ámbar con el nombre/número (no modal).
- **Puerta abierta > 15 km/h / TPMS < 28 psi / reserva** → pendiente de mapeo CAN; se dispara con el mismo `showAlert()` cuando `CanBridge` emita el evento.

```kotlin
private fun showAlert(message: String) {
    alertText.text = message
    alertCapsule.visibility = View.VISIBLE
    alertCapsule.alpha = 0f
    alertCapsule.animate().alpha(1f).setDuration(180L).start()
    uiHandler.removeCallbacks(alertHide)
    uiHandler.postDelayed(alertHide, 4200L)
}
```

### 3.8 Quick Settings drawer (implementado)

Gesto: engranaje del panel (o swipe desde el borde derecho en una iteración futura). Contenido:

- **Brillo** (`SeekBar` 0–100 → `WindowManager.LayoutParams.screenBrightness`, 0.12–1.0).
- **Modo noche** (scrim `#66000000`; en la fase 2 baja el brillo al 25 % y oculta paneles no esenciales).
- **Silenciar audio** (`AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_MUTE)`).
- **WiFi / hotspot** → `Settings.ACTION_WIFI_SETTINGS`.
- **Estado CAN** (GPS/CAN, color semántico).
- **Todos los ajustes** → `SettingsActivity`.

El drawer mide 400 dp, entra con `translationX` (compositor) y respeta el dock (margen inferior 100 dp).

---

## 4. Navegación y estructura de pantallas

### 4.1 Decisión: una Activity, capas de vista (no Fragments)

| Opción | Veredicto | Motivo |
|---|---|---|
| Activities separadas | ❌ | Context switch, recarga de mapa, parpadeo negro |
| `FragmentTransaction` por pestaña | ❌ | Re-inflado, pérdida de estado del mapa, transiciones negras |
| **Una Activity + capas XML + foco** | ✅ | Cero recarga, estado compartido, 60 fps |
| Compose `AnimatedContent` | ❌ | +8–15 MB, jank en A7, tema no nativo |

Si en el futuro se migra a Fragments (por ejemplo para un módulo de radio con ciclo de vida propio), deben ser **child fragments dentro de `rightPane`**, con `setCustomAnimations(0, 0, 0, 0)`, `setReorderingAllowed(true)` y el mapa en la Activity (nunca en un fragment).

### 4.2 Flujo

```
                         ┌──────────────┐
                         │   COCKPIT    │◄──────────────┐
                         │ mapa 55/45   │               │
                         └──┬───┬───┬───┘               │
             toca mapa      │   │   │  toca media card  │
                            ▼   │   ▼                   │
                     ┌────────┐ │ ┌────────┐            │
                     │  NAV   │ │ │ MEDIA  │            │
                     │ 80/20  │ │ │ 25/75  │            │
                     └───┬────┘ │ └───┬────┘            │
                         │      │     │                 │
          dock VEHÍCULO  │      │     │  dock COCKPIT   │
                         ▼      │     ▼                 │
                     ┌────────┐ │ ┌────────┐            │
                     │VEHÍCULO│ │ │ RADIO  │ (fase 2)   │
                     │ 25/75  │ │ │ 25/75  │            │
                     └───┬────┘ │ └───┬────┘            │
                         └──────┴─────┴─────────────────┘
```

### 4.3 Módulos y clases

| Módulo | Layout | Orquestación | Estado |
|---|---|---|---|
| Cockpit | `view_cockpit.xml` | `MainActivity.boundViews` | ✅ |
| Nav mini | `view_mini_hud.xml` | `applyFocus(NAV)` | ✅ |
| Media Center | `view_media_module.xml` | `MediaRepository` + `updateMedia()` | ✅ |
| Vehículo | `view_vehicle_module.xml` | `renderSignals()` + placeholders CAN | ✅ (datos CAN pendientes) |
| Radio | `view_radio_module.xml` (propuesto) | `RadioMcuClient` | ⏳ fase 2 |
| Quick Settings | `sheet_quick_settings.xml` | `MainActivity` | ✅ |
| Ajustes completos | `SettingsInfoActivity` | Activity aparte | ✅ |

---

## 5. Contratos de datos

> Convención: **nullable = aún no mapeado o no disponible**. `-1` del MCU nunca se propaga como valor; se convierte a `null`. Esto evita que la UI muestre “0 psi” o “0 rpm” cuando el dato no existe.

```kotlin
package com.chronosbvrx.ibizadash.core.model

import android.os.SystemClock

enum class SignalSource { CAN, GPS, UNAVAILABLE }

enum class Gear { P, R, N, D, S }

data class DoorState(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false
) {
    val anyOpen: Boolean get() = frontLeft || frontRight || rearLeft || rearRight || trunk || hood
}

data class LightState(
    val lowBeam: Boolean = false,
    val highBeam: Boolean = false,
    val position: Boolean = false,
    val fogFront: Boolean = false,
    val fogRear: Boolean = false,
    val leftTurn: Boolean = false,
    val rightTurn: Boolean = false,
    val hazard: Boolean = false
)

data class TirePressure(
    val position: TirePosition,
    val bar: Float? = null,
    val celsius: Float? = null,
    val status: TireStatus = TireStatus.UNKNOWN
)

enum class TirePosition { FL, FR, RL, RR }
enum class TireStatus { OK, LOW, HIGH, UNKNOWN }

data class CanTelemetry(
    val speedKmh: Float = 0f,
    val rpm: Int? = null,
    val gear: Gear = Gear.P,
    val engineLoadPct: Float? = null,
    val throttlePct: Float? = null,
    val coolantC: Float? = null,
    val fuelC: Float? = null,
    val batteryV: Float? = null,
    val doors: DoorState = DoorState(),
    val lights: LightState = LightState(),
    val tires: List<TirePressure> = emptyList(),
    val odometerKm: Long? = null,
    val tripKm: Float? = null,
    val vin: String? = null,
    val dtc: List<String> = emptyList(),
    val milOn: Boolean? = null,
    val source: SignalSource = SignalSource.GPS,
    val updatedAtMs: Long = SystemClock.elapsedRealtime()
)

sealed interface CanEvent {
    data class DoorsChanged(val doors: DoorState) : CanEvent
    data class LightsChanged(val lights: LightState) : CanEvent
    data class TireAlert(val tire: TirePressure) : CanEvent
    data class ReverseChanged(val active: Boolean) : CanEvent
    data class PhoneCall(val state: CallState, val name: String?, val number: String?) : CanEvent
}

enum class CallState { IDLE, RINGING, ACTIVE, DIALLING }
```

### 5.1 Estado de UI (una sola fuente de verdad)

```kotlin
data class DashboardUiState(
    val focus: Focus = Focus.COCKPIT,
    val telemetry: CanTelemetry = CanTelemetry(),
    val media: MediaState = MediaState(),
    val nav: TurnInstruction? = null,
    val climate: ClimateState = ClimateState(),
    val connection: ConnectionState = ConnectionState()
)

data class ConnectionState(
    val bluetooth: BluetoothState? = null,
    val can: SignalSource = SignalSource.UNAVAILABLE,
    val gps: Boolean = false
)

data class ClimateState(
    val targetC: Int = 22,
    val auto: Boolean = true,
    val ac: Boolean = false,
    val fan: Int = 2,
    val recirculating: Boolean = false
)

data class MediaState(
    val track: TrackInfo? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val source: AudioSource = AudioSource.BLUETOOTH
)

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: android.graphics.Bitmap?,
    val packageName: String
)

enum class AudioSource { BLUETOOTH, USB, STREAMING, RADIO }
```

### 5.2 Radio (fase 2)

```kotlin
data class RadioPreset(
    val id: Int,
    val band: Band,
    val frequencyKhz: Int,
    val name: String,
    val favorite: Boolean = false
)

enum class Band { FM, AM }

data class RadioState(
    val power: Boolean = false,
    val band: Band = Band.FM,
    val frequencyKhz: Int = 98_700,
    val stereo: Boolean = false,
    val rdsName: String? = null,
    val rdsText: String? = null,
    val signalStrength: Int? = null,
    val scanning: Boolean = false,
    val presets: List<RadioPreset> = emptyList(),
    val source: SignalSource = SignalSource.UNAVAILABLE
)
```

### 5.3 Ajustes

```kotlin
data class LauncherSettings(
    val units: Units = Units.METRIC,
    val dayNightMode: DayNightMode = DayNightMode.AUTO,
    val accent: Accent = Accent.CYAN,
    val showWeather: Boolean = true,
    val keepScreenOn: Boolean = true,
    val startOnBoot: Boolean = true
)

data class VehicleSettings(
    val comingHomeSeconds: Int = 30,
    val leavingHome: Boolean = true,
    val autoLockSpeedKmh: Int = 15,
    val selectiveUnlock: Boolean = true,
    val parkSensorVolume: Int = 60,
    val ambientLight: Int = 0
)
```

---

## 6. Capa CAN centralizada (`CanBridge`)

### 6.1 Arquitectura

```
┌──────────────────────────────┐        ┌──────────────────────────────┐
│ com.tw.carinfoservice        │        │ com.tw.service.xt            │
│ CarServiceAidl               │        │ ITWCommandAidl               │
│ · CAN (vel, RPM, puertas…)   │        │ · reversa, teléfono, media    │
└──────────────┬───────────────┘        └──────────────┬───────────────┘
               │ Binder (permiso normal)               │ Binder
               ▼                                       ▼
        ┌────────────────────────────────────────────────────┐
        │                 CanBridge (singleton)              │
        │  · binder pool + reconexión exponencial            │
        │  · normalización (-1 → null)                       │
        │  · dedupe + coalescing (16 ms)                     │
        │  · StateFlow<CanTelemetry> / SharedFlow<CanEvent>  │
        └───────────────┬────────────────────┬───────────────┘
                        │                    │
             ┌──────────▼──────┐   ┌─────────▼──────────┐
             │ DashboardUiState│   │ Módulos / Alertas  │
             │ (collectAsState)│   │ (Telemetry, HUD)   │
             └─────────────────┘   └────────────────────┘
```

Reglas:

- `CanBridge` **no** conoce Activities ni Views.
- Los callbacks AIDL llegan en un binder thread; se normalizan y se publican en `Dispatchers.Main.immediate` para que la UI no toque estado desde hilos.
- Un solo binder por servicio; `CanBridge` sobrevive a la Activity (Application scope) y se cierra en `onTrimMemory`/proceso muerto.
- Los valores desconocidos **no** se inventan: `null` + `SignalSource.UNAVAILABLE`.

### 6.2 Esqueleto

```kotlin
object CanBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _telemetry = MutableStateFlow(CanTelemetry())
    val telemetry: StateFlow<CanTelemetry> = _telemetry.asStateFlow()

    private val _events = MutableSharedFlow<CanEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CanEvent> = _events.asSharedFlow()

    private var carService: CarServiceAidl? = null
    private var twService: ITWCommandAidl? = null

    fun start(context: Context) { /* bind CarServiceAidl + TwCommandAidl */ }

    @Synchronized
    fun onCarFrame(frame: CarFrame) {
        val current = _telemetry.value
        _telemetry.value = current.copy(
            speedKmh = frame.speedKmh ?: current.speedKmh,
            rpm = frame.rpm,
            gear = frame.gear ?: current.gear,
            doors = frame.doors ?: current.doors,
            lights = frame.lights ?: current.lights,
            tires = frame.tires ?: current.tires,
            source = frame.source,
            updatedAtMs = SystemClock.elapsedRealtime()
        )
        frame.alert?.let { scope.launch { _events.emit(it) } }
    }
}
```

### 6.3 AIDL — `com.tw.service.xt` (`ITWCommandAidl`)

Descriptor: `com.tw.service.xt.aidl.ITWCommandAidl`. Verificado contra el APK del estéreo (`decompiled/service_xt/.../ITWCommandAidl.java`).

| Tx | Método | Uso | Estado |
|---|---|---|---|
| 1 / 2 | `register/unRegisterTWCommandCallback` | Callback general | ✅ usado |
| 3 / 4 | `register/unRegisterRadioCallBack` | Callback de radio | ✅ mapeado |
| 5 / 6 | `register/unRegisterMusicCallBack` | Callback de media local | ✅ mapeado |
| 9 / 10 | `register/unRegisterBTCallBack` | Callback BT | ✅ mapeado |
| 11 / 12 | `radioNext` / `radioPre` | Emisora siguiente/anterior | ✅ listo |
| 13 / 14 | `radioSearchNext` / `radioSearchPre` | Búsqueda | ✅ listo |
| 15 | `radioSetChannel(int)` | Frecuencia (kHz) | ✅ listo |
| 16 | `radioOpenChannel(String)` | Abrir emisora | ✅ listo |
| 17 | `radioBand(int)` | FM/AM | ✅ listo |
| 18 | `radioBandChange` | Cambio de banda | ✅ listo |
| 19 | `btCall(String)` | Llamar | ✅ |
| 20 / 21 | `btAccept` / `btHangUP` | Descolgar/colgar | ✅ |
| 22 | `btOpenMusic` | Abrir música BT | ✅ |
| 23 | `btPlayMusic` | **Play BT** (no pausa) | ✅ usado |
| 24 / 25 | `btPre` / `btNext` | Anterior/siguiente BT | ✅ usados |
| 26 | `btGetPhoneStatus` | Estado del teléfono | ✅ usado |
| 31 / 32 | `musicNext` / `musicPre` | Media local | ✅ mapeado |
| 33 / 34 | `musicPlay` / `musicPause` | Media local (no A2DP) | ✅ mapeado |
| 40–45 | `volumeUp/Down/Mute/Max/Min/Value` | Volumen MCU | ✅ mapeado |
| 48 / 49 | `goHome` / `back` | Navegación de sistema | ✅ mapeado |

**Pausa de Bluetooth:** el AIDL no tiene `btPause`. La pausa real se hace con **AVRCP passthrough** sobre `android.bluetooth.BluetoothAvrcpController` (API oculta, sin bloqueo en Android 8.1):
`sendPassThroughCmd(device, key, state)` con `PLAY=68`, `PAUSE=70`, `NEXT=75`, `PREV=76` y **solo `state=0`** en este estéreo (el release reanudaba la reproducción). El objeto se obtiene con `BluetoothAdapter.getProfileProxy(context, listener, 12)`.

Callbacks del comando (`ITWCommandCallbackAidl`):

| Tx | Callback | Payload |
|---|---|---|
| 3 | `onBtPhoneStatus` | int estado |
| 4 | `onBtCallStatus` | int estado, number, name |
| 5 | `onBtConnectedStatus` | int estado |
| 6 | `onReverseStatus` | int 0/1 |

Los callbacks de música/BT/radio reciben además `extendedInterface(Bundle)` (tx 6 del callback) con `project`, `action`, `datatype` y datos; es la vía para telemetría de radio y metadatos si se prefiere no usar broadcasts.

Reversa: además de `onReverseStatus`, el sistema expone `REVERSE_STATUS_FOR_PROTOCOL` en `Settings.System` (fallback). La app **no** usa root.

### 6.4 AIDL — `com.tw.carinfoservice` (`CarServiceAidl`)

- Acción: `com.tw.carinfoservice.CarService.Bind`
- Permiso: `com.tw.carinfoservice.permission.USE_AIDL` (protección **normal**, sin root).
- Callback: `com.tw.carinfoservice.CarServiceCallBack`.

Transaction codes confirmados (extraídos de `p057j5/a.java` + logs de `p015c5/b.java`):

| Tx | Método ofuscado | Dato | Evidencia |
|---|---|---|---|
| 1 | `p2(cb)` | Registro de callback | `bindService` |
| 2 | `F2(cb)` | Baja de callback | `onServiceDisconnected` |
| 7 | `n()` → `int[]` | **Odómetro total** | log `reqFlag:total_Mileage` |
| 48 | `h()` → `int[]` | **Distancia recorrida** | log `reqFlag:drivenDistance` |
| 15 | `U1()` → `int` | **Intermitente izquierdo** | log `leftTurnSignal` |
| 16 | `m4()` → `int` | **Intermitente derecho** | log `rightTurnSignal` |
| 17 | `n2()` → `int` | **Hazard** | log `double_flash` |
| 27 | `i3()` → `int` | **Reversa** | log `carReverse` |
| 61 | `f2()` → `float` | Sensor float (¿combustible?) | por confirmar |

Sentinel: todos los getters devuelven **−1** cuando el dato no está disponible. La app debe tratar `-1` como `null`.

El resto de transactions (3–64) están en `IbizaDash/decompiled/cardashboard_classes2/sources/p057j5/a.java`; la tabla completa se genera con:

```kotlin
// Herramienta de descubrimiento (debug only)
val method = CarServiceAidl.Stub::class.java.declaredMethods
    .first { it.name == "U1" }
println("${method.name} -> tx=${method.getAnnotation(...)}")
```

En la práctica el AIDL es ofuscado; el mapeo se hace por **correlación** con el dashboard del fabricante (ver 6.6).

### 6.5 Callback CAN (posibles datos)

El callback `CarServiceCallBack` tiene 64 transacciones. Las relevantes para la suite:

| Tx callback | Payload detectado | Hipótesis |
|---|---|---|
| 59 | **16 ints** | TPMS: 4 ruedas × (presión, temperatura, estado, flags) |
| 49 | int + string | VIN / CALID / texto de diagnóstico |
| 47 | string | DTC / texto |
| 54–57, 62, 64 | int + string | Valor + etiqueta de sensor |
| 21, 29–33, 37–46 | float + string | Sensores analógicos etiquetados |
| 28, 63 | sin payload | Eventos de estado / refresh |

> Estas hipótesis **no** están validadas en el coche. La UI de Vehículo muestra `—` hasta que se confirmen; es preferible un guion honesto a un dato falso.

### 6.6 Procedimiento de mapeo (cuando el estéreo vuelva a estar online)

1. Activar el dashboard del fabricante y el nuestro en paralelo.
2. Loguear **cada** getter del AIDL a 2 Hz con timestamp (`value`, `tx`, `método`).
3. Ejecutar una secuencia física: puerta delantera izq., der., traseras, maletero; luces bajas/altas/posición/niebla; intermitentes; hazard; freno de mano; reversa; presión de una rueda (bajar 5 psi).
4. Correlacionar el cambio de valor con la acción y anotar `tx → campo` en `McuMap`.
5. Validar el rango (p. ej. RPM 0–8000, coolant −40–130 °C) y el sentinel −1.
6. Añadir un test unitario de normalización por campo.

### 6.7 Radio nativa (AIDL mapeado, UI pendiente)

El **mismo** `ITWCommandAidl` de `com.tw.service.xt` ya expone el sintonizador; no hace falta un servicio aparte:

| Tx | Método | Uso |
|---|---|---|
| 3 / 4 | `register/unRegisterRadioCallBack` | Callback de radio (`IRadioCallBack.extendedInterface(Bundle)`) |
| 11 / 12 | `radioNext` / `radioPre` | Emisora siguiente/anterior |
| 13 / 14 | `radioSearchNext` / `radioSearchPre` | Búsqueda automática |
| 15 | `radioSetChannel(int)` | Frecuencia en kHz (p. ej. `98700`) |
| 16 | `radioOpenChannel(String)` | Abrir emisora por identificador |
| 17 | `radioBand(int)` | FM/AM |
| 18 | `radioBandChange` | Alternar banda |

Lo que falta para el módulo visual:

1. Confirmar en el `Bundle` del callback de radio (`project=radio`) la frecuencia/banda/RDS actuales.
2. Mapear `power` y `scan` (no aparecen como métodos sueltos; pueden ir por `extendedInterface` o por la app `com.tw.radio`).
3. Persistir presets en la app (9–18 favoritos) mientras el MCU no exponga memorias.
4. Implementar `view_radio_module.xml` con `TunerDialView` y conectar los botones a los tx 11–18.

Mientras eso se valida, el dock **abre `com.tw.radio`** para no simular un dial que no sintoniza. **No** se inventan transacciones.

### 6.8 Cámara de reversa

- Trigger: `onReverseStatus` (tx 6) o `Settings.System.REVERSE_STATUS_FOR_PROTOCOL`.
- Lanzamiento: `com.tw.reverse/.ReverseActivity` (fallback `LRReverseActivity`).
- Preview del proveedor: `SprdCamera.open(0)`, 960×240, `persist.camera.mode0=0`, `ic=1`.
- La app no dibuja la cámara: usa la Activity del proveedor para no duplicar el pipeline de cámara en un SoC limitado.

---

## 7. UI — Módulo de Telemetría / Vehículo

**Archivo implementado:** `app/src/main/res/layout/view_vehicle_module.xml`  
**Orquestación:** `MainActivity.renderSignals()`.

### 7.1 Estructura

```
ScrollView (bg_panel, 25/75)
└─ LinearLayout
   ├─ Header: VEHÍCULO / SEAT IBIZA 6J / chip CAN-GPS
   ├─ Fila de 4 stat tiles: velocidad, marcha, fuente, reversa
   ├─ TPMS: 2×2 tarjetas (DEL IZQ, DEL DER, TRAS IZQ, TRAS DER)
   ├─ MOTOR: 4 filas (RPM, carga, refrigerante, batería) con gauge
   └─ Footer: VIN, DTC/MIL
```

### 7.2 XML (extracto real)

```xml
<LinearLayout
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    android:background="@drawable/bg_metric_widget"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:padding="9dp">

    <ImageView
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:src="@drawable/ic_tire"
        android:tint="@color/text_muted" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView style="@style/Label"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="DEL IZQ" />

        <TextView
            android:id="@+id/vTireFL"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:fontFeatureSettings="tnum"
            android:text="-- bar"
            android:textColor="@color/text_secondary"
            android:textSize="15sp" />

        <TextView
            android:id="@+id/vTireFLT"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="-- °C"
            android:textColor="@color/text_faint"
            android:textSize="9sp" />
    </LinearLayout>
</LinearLayout>
```

Gauge (drawable `bg_gauge_cyan.xml`, sin blur):

```xml
<layer-list>
    <item android:id="@android:id/background">
        <shape><solid android:color="#14FFFFFF"/>
            <corners android:radius="3dp"/></shape>
    </item>
    <item android:id="@android:id/progress">
        <scale android:scaleWidth="100%">
            <shape><gradient android:angle="0"
                android:startColor="#00D2FF" android:endColor="#22D3EE"/>
                <corners android:radius="3dp"/></shape>
        </scale>
    </item>
</layer-list>
```

### 7.3 Binding

```kotlin
fun renderTelemetry(t: CanTelemetry) {
    vSpeed.text = "${t.speedKmh.roundToInt()} km/h"
    vGear.text = t.gear.name
    vBusChip.text = if (t.source == SignalSource.CAN) "CAN" else "GPS"

    t.tires.firstOrNull { it.position == TirePosition.FL }?.let { tire ->
        vTireFL.text = tire.bar?.let { "%.1f bar".format(it) } ?: "-- bar"
        vTireFLT.text = tire.celsius?.let { "%.0f °C".format(it) } ?: "-- °C"
        vTireFL.setTextColor(colorForTire(tire.status))
    }

    vRpm.text = t.rpm?.toString() ?: "--"
    vRpmBar.progress = t.rpm ?: 0
    vLoad.text = t.engineLoadPct?.let { "${it.roundToInt()} %" } ?: "--"
    vCoolant.text = t.coolantC?.let { "${it.roundToInt()} °C" } ?: "--"
    vVoltage.text = t.batteryV?.let { "%.1f V".format(it) } ?: "--"
}
```

### 7.4 Reglas de estado

| Condición | Color | UI |
|---|---|---|
| TPMS 29–36 psi | verde `#10B981` | valor normal |
| TPMS 26–28 psi | ámbar `#FF9500` | valor + icono ámbar |
| TPMS < 26 psi | rojo `#FF3B30` | alerta + cápsula |
| Coolant > 105 °C | ámbar/rojo | gauge ámbar + alerta |
| MIL / DTC | rojo | chip “MIL” en header |
| Datos no disponibles | `text_muted` | `—` (nunca 0) |

### 7.5 OBD-II ELM327 (implementado, hardware pendiente)

`obd/Elm327Client.kt` abre Bluetooth SPP (`00001101-0000-1000-8000-00805F9B34FB`), inicializa el ELM327 (`ATZ/ATE0/ATL0/ATS0/ATH0/ATSP0`) y consulta:

| PID | Dato |
|---|---|
| `010C` | RPM |
| `0104` | Carga de motor |
| `0111` | Acelerador |
| `010D` | Velocidad OBD |
| `0105` | Refrigerante |
| `010F` | Temperatura de admisión |
| `012F` | Nivel de combustible |
| `0142` | Voltaje de batería |
| `0101` | MIL + número de DTC |
| `03` | Códigos DTC |
| `0902` | VIN |

- Hilo propio; `ObdRepository` publica `ObdTelemetry`; `MainActivity.renderObd` llena el módulo.
- Auto-detección de dispositivos emparejados con OBD/ELM/Vgate/V-Link/Superbox/327.
- Diálogo al tocar el chip **OBD**: dispositivo, búsqueda automática, **modo demo**, ajustes Bluetooth, desconectar.
- **No probado con hardware real**; el modo demo permite validar la UI sin adaptador.
- Puertas/luces/TPMS **no** se obtienen por OBD genérico; requieren CAN.

---

## 8. UI — Media Center (implementado)

**Archivo:** `app/src/main/res/layout/view_media_module.xml`  
**Estado:** `MediaRepository` + `MediaListenerService` (MediaSessionManager) + `BluetoothMediaReceiver` (A2DP/AVRCP).

### 8.1 Bluetooth real de este estéreo (verificado)

En este head unit el audio del teléfono llega por **A2DP sink** y el metadato por **AVRCP controller**. La `MediaSession` de `com.android.bluetooth` existe pero llega con las 7 claves vacías (`TITLE`, `ARTIST`, `ALBUM`…), así que **no sirve** como fuente principal.

| Fuente | Qué entrega | Uso |
|---|---|---|
| `com.android.getelementattrrsp` | `trackTitle`, `artist`, `album` | **Fuente de título/artista/álbum** |
| `android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT` | `MediaMetadata` vacío + `PlaybackState` (2=pausa, 3=play) | Estado de reproducción |
| `android.bluetooth.a2dpsink.profile.action.PLAYING_STATE_CHANGED` | `STATE=10/11` | Ruta activa; **no** confiable para el icono |
| `MediaSessionManager` | apps locales (radio/media del estéreo) | Fuente local |
| iTunes Search API | portada 600×600 | `AlbumArtClient` (solo si el stack no trae arte) |

Implementación (`BluetoothMediaReceiver.kt`):

- Registra los 17 broadcasts al arrancar (`App.onCreate`).
- Cachea el último track en `SharedPreferences("bt_media")` y lo restaura al iniciar si el A2DP sink sigue conectado.
- `isPlaying` se resuelve por `PlaybackState` AVRCP (2/3); el 10/11 de A2DP solo es fallback.
- Pausa/reanudar: AVRCP passthrough (`68`/`70`, solo `state=0`).
- Anterior/siguiente: AIDL `btPre` (24) / `btNext` (25).
- Carátula: el teléfono **no** transfiere arte por este stack; `AlbumArtClient` busca la portada en la **iTunes Search API** por artista+álbum (fallback al título), descarga **600×600** y la cachea en memoria (LRU 12 MB) y disco (`cacheDir/covers`). Es asíncrono, de un solo hilo, y se puede desactivar en **Ajustes rápidos → Carátulas online** (solo se envía artista/álbum/título a Apple). Si el álbum llega como “Unavailable” se ignora y se reintenta con el paquete siguiente.

> La app nativa `com.tw.bt` usa el mismo `BluetoothAvrcpController`; su pantalla de música confirma que `getelementattrrsp` es el canal correcto. El evento `com.tw.bt.playa2dpsinkcmd` solo enruta audio, no pausa.

Elementos:

- Arte **196 dp**, radio 8 dp, `clipToOutline`, `CENTER_CROP`; placeholder con icono y padding 70 dp.
- Glow ambiental fijo (`bg_media_glow.xml`, radial `#5500D2FF` → transparente); no se extrae color por carátula para no gastar CPU. Fase 2: `Palette` una vez por pista y `GradientDrawable` cacheado.
- Título 24 sp, artista 14 sp, álbum 11 sp.
- Progreso 3 dp con gradiente cian y tiempos tabulares.
- Transporte: **64 / 84 / 64 dp**; play con gradiente `#38BDF8 → #0284C7`, borde blanco 30 % y elevación 10 dp.
- “ABRIR APP DE ORIGEN” (56 dp de alto) para saltar al reproductor real.
- Carátula del Cockpit: 52 dp, **48 dp** de target en los botones laterales y play 48 dp; el módulo usa los targets grandes.

Integración con llamadas (HFP): el chip Bluetooth muestra “Llamada · Nombre” en ámbar; el banner de llamada entrante con botones descolgar/colgar requiere mapear los tx de respuesta del MCU (pendiente). No se muestran botones que no funcionen.

**Asset del coche:** el PNG original traía tres glows azules planos bajo la defensa y las ruedas (resabio del render comercial). Se eliminaron con una máscara de dominancia azul + desvanecido de alfa y se añadió una sombra de contacto suave; el original se conserva en `docs/assets/ibiza_render_original.png` y el recurso de la app es `drawable-nodpi/ibiza_render.png`.

---

## 9. UI — Radio FM/AM (AIDL listo, UI pendiente)

**Propuesta de archivo:** `view_radio_module.xml` (no incluido en el APK actual).

```
FrameLayout (bg_panel)
├─ Header: RADIO · FM / chip señal
├─ Dial: TunerDialView (48 dp de alto, aguja cian, glow en la frecuencia activa)
├─ Frecuencia: 64 sp tabular + “MHz” + estero/RDS
├─ Fila: SEEK − / SCAN / SEEK +
├─ Grid de presets 3×2 (56 dp de alto, edición long-press)
└─ Botón “ABRIR RADIO DEL VEHÍCULO” (fallback)
```

### 9.1 Layout (extracto)

```xml
<com.chronosbvrx.ibizadash.radio.TunerDialView
    android:id="@+id/tunerDial"
    android:layout_width="match_parent"
    android:layout_height="72dp"
    android:layout_marginTop="8dp" />

<TextView
    android:id="@+id/frequency"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:fontFamily="sans-serif-light"
    android:fontFeatureSettings="tnum"
    android:text="98.7"
    android:textColor="@color/text_primary"
    android:textSize="64sp" />

<TextView
    android:id="@+id/band"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="FM · MHz"
    android:textColor="@color/text_muted"
    android:textSize="12sp" />

<GridLayout
    android:id="@+id/presetGrid"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:columnCount="3"
    android:rowCount="2" />
```

### 9.2 `TunerDialView` (núcleo)

```kotlin
class TunerDialView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var minKhz = 87_500
    var maxKhz = 108_000
    var frequencyKhz = 98_700
        set(value) { field = value.coerceIn(minKhz, maxKhz); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        val pad = 24f
        val span = (maxKhz - minKhz).toFloat()

        paint.color = 0x22FFFFFF
        paint.strokeWidth = 2f
        for (i in 0..20) {
            val x = pad + (width - 2 * pad) * i / 20f
            val major = i % 5 == 0
            canvas.drawLine(x, cy - if (major) 16f else 8f, x, cy + if (major) 16f else 8f, paint)
        }

        val x = pad + (width - 2 * pad) * (frequencyKhz - minKhz) / span
        glow.color = 0x5500D2FF
        glow.strokeWidth = 10f
        canvas.drawLine(x, cy - 30f, x, cy + 30f, glow)

        paint.color = 0xFF00D2FF.toInt()
        paint.strokeWidth = 3f
        canvas.drawLine(x, cy - 30f, x, cy + 30f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val pad = 24f
            val t = ((event.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
            frequencyKhz = (minKhz + t * (maxKhz - minKhz)).toInt() / 100 * 100
            performClick()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
```

### 9.3 `RadioMcuClient` (contrato)

```kotlin
interface RadioMcuClient {
    val state: StateFlow<RadioState>
    fun power(on: Boolean)
    fun setBand(band: Band)
    fun tune(khz: Int)
    fun seek(direction: Int)
    fun scan()
    fun savePreset(slot: Int)
    fun loadPreset(slot: Int)
    fun close()
}
```

El AIDL de radio ya está mapeado (tx 11–18 de `ITWCommandAidl`, ver 6.7): `radioSetChannel(kHz)`, `radioNext/Pre`, `radioBand`. Falta construir el módulo visual y confirmar el estado actual/RDS mediante `IRadioCallBack.extendedInterface`. Mientras tanto, el dock RADIO abre la app nativa y el módulo no se muestra (no se simula sintonía).

---

## 10. Ajustes y Quick Settings

### 10.1 Drawer rápido (implementado)

400 dp, fondo `bg_sheet` (obsidiana 95 % + borde izquierdo cian degradado), 56 dp por fila.

| Control | Implementación |
|---|---|
| Brillo | `SeekBar` 0–100 → `screenBrightness` 0.12–1.0 |
| Modo noche | `nightDim` `#66000000` (fase 2: bajar brillo y ocultar paneles) |
| Silencio | `AudioManager.STREAM_MUSIC` mute/unmute |
| WiFi | `Settings.ACTION_WIFI_SETTINGS` |
| Bus CAN | estado `CAN`/`GPS` en vivo |
| Todos los ajustes | `SettingsActivity` |

### 10.2 Ajustes completos (2 pestañas)

**Launcher & sistema:** unidades (km/h–mph, bar–psi, °C–°F), modo día/noche (auto/manual), acento (cian/cobre), clima on/off, keep-screen-on, arranque automático, selección de render del coche, calibración de pantalla táctil (si el MCU la expone).

**Vehículo (BCM):** Coming Home / Leaving Home (0/15/30/60 s), autobloqueo por velocidad (off/15/20 km/h), desbloqueo selectivo, volumen de sensores de reversa, luz ambiental, y **monitor CAN** (tabla viva de `tx`, método, valor crudo, valor normalizado, Hz) para depuración sin herramientas externas.

El monitor CAN es de solo lectura y se activa con long-press en “Versión” para no exponerlo al usuario final.

---

## 11. Rendimiento

### 11.1 Medición actual (emulador Android 36, 1280×720/160 dpi)

| Métrica | Valor | Objetivo en estéreo |
|---|---|---|
| APK release | **1,327,040 B ≈ 1.27 MiB** | < 3 MB |
| PSS total | **60,274 KB ≈ 58.9 MB** | < 100 MB |
| Java heap | 13,000 KB | < 32 MB |
| Native heap | 28,400 KB | < 48 MB |
| Graphics | 8.7 MB | < 24 MB |
| Views | **311** | < 400 |
| ViewRootImpl | 1 | 1 |
| Arranque percibido | ~6 s en emulador con Play Store activo | < 2.5 s en estéreo |

> El emulador usa GPU software y estaba instalando apps en paralelo; sus `gfxinfo` (50th percentile 48–65 ms) **no** representan al sp7731e. La medición válida se hace en el coche con `adb shell dumpsys gfxinfo com.chronosbvrx.ibizadash` y `dumpsys meminfo`.

### 11.2 Presupuesto de frame (16.6 ms)

| Trabajo | Presupuesto |
|---|---|
| Medida/layout del panel durante split | ≤ 4 ms |
| Dibujo de vistas (sin mapa) | ≤ 4 ms |
| Mapa (tiles cache) | ≤ 5 ms |
| Compositor / VSync | ≤ 3 ms |

Si el split supera 8 ms de layout en el equipo real, cambiar a `SPLIT_ANIM_MS = 0` (snap) + fade 160 ms. El código ya respeta `areAnimatorsEnabled()`.

### 11.3 Decisiones que protegen los 60 fps

1. **Sin Compose** (menos RAM, menos jank, APK 1.2 MB).
2. **Sin blur en vivo**; gradientes y capas alfa.
3. **Un solo MapView**; jamás se recrea al cambiar de módulo.
4. **Animaciones de compositor** (alpha/translationX) para drawer, alertas y fades.
5. **Datos a 2 Hz** para la UI (aunque el CAN llegue a 10–50 Hz); coalescing y `distinctUntilChanged`.
6. **Cero asignaciones en `onDraw`** de vistas custom; paints reutilizados.
7. **Listas recicladas** (ListView/RecyclerView) si se añade el selector de canales/EPG.
8. **Caché de tiles** en almacenamiento externo de la app (`App.kt`), no en memoria.
9. **Arte de álbum** decodificado una vez y cacheado (Bitmap 512 px máx.).
10. **ViewStub** para los módulos ocultos (siguiente optimización): reduce el inflado inicial y el árbol de 270 vistas.

### 11.4 Cómo medir en el estéreo

```powershell
adb shell dumpsys meminfo com.chronosbvrx.ibizadash
adb shell dumpsys gfxinfo com.chronosbvrx.ibizadash reset
# tocar MAPA, MEDIA, VEHÍCULO
adb shell dumpsys gfxinfo com.chronosbvrx.ibizadash
adb shell top -m 10 -o PID,CPU,RES,ARGS | findstr ibizadash
```

Criterio de aceptación: PSS < 100 MB, 90th percentile < 16.6 ms en estado estable, sin frames > 50 ms durante la transición.

---

## 12. Roadmap

| # | Tarea | Prioridad | Nota |
|---|---|---|---|
| 1 | ViewStub para Media/Vehículo | Alta | Arranque y memoria |
| 2 | Mapeo CAN completo (puertas, luces, RPM, temp, TPMS) | Alta | Requiere coche online |
| 3 | Validar TPMS callback tx 59 | Alta | 16 ints |
| 4 | Radio nativa (AIDL `com.tw.service`) | Media | Sin simular |
| 5 | Turn-by-turn (notification listener o SDK) | Media | Contrato listo |
| 6 | Llamada entrante con descolgar/colgar | Media | Mapear tx de respuesta |
| 7 | Modo Sport (acento cobre + gauges) | Baja | Cosmético |
| 8 | Ajustes BCM (Coming Home, autolock) | Baja | Depende de AIDL |
| 9 | Render alternativo / paleta | Baja | Assets |
| 10 | Firma release + instalación como HOME | Media | Distribución |

---

## Apéndice A — Mapa de archivos

```
IbizaDash/
├─ app/src/main/java/com/chronosbvrx/ibizadash/
│  ├─ MainActivity.kt              # canvas, foco, HUD, media, quick settings
│  ├─ App.kt                       # osmdroid cache
│  ├─ MapActivity.kt               # mapa a pantalla completa
│  ├─ AppsActivity.kt              # cajón de apps
│  ├─ SettingsActivity.kt          # ajustes
│  ├─ Prefs.kt
│  ├─ car/
│  │  ├─ CarSignals.kt             # persist.can.speed + REVERSE_STATUS
│  │  ├─ TwCommandClient.kt        # bind com.tw.service.xt
│  │  ├─ TwCommandBinder.kt
│  │  ├─ ReverseCallback.kt
│  │  └─ BtCallback.kt
│  ├─ media/
│  │  ├─ MediaRepository.kt        # MediaSessionManager
│  │  ├─ MediaListenerService.kt   # NotificationListenerService
│  │  ├─ NowPlaying.kt
│  │  ├─ MediaKeys.kt
│  │  └─ DemoMedia.kt
│  ├─ bluetooth/BluetoothStatusClient.kt
│  ├─ map/MapHelper.kt
│  └─ weather/WeatherClient.kt
├─ app/src/main/res/layout/
│  ├─ activity_main.xml            # canvas + dock + alertas + drawer
│  ├─ view_cockpit.xml             # 45 %
│  ├─ view_mini_hud.xml            # 20 %
│  ├─ view_media_module.xml        # 75 %
│  ├─ view_vehicle_module.xml      # 75 %
│  └─ sheet_quick_settings.xml
├─ app/src/main/res/drawable/      # glass, gauges, glow, iconos
├─ docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md
├─ screenshots/                    # cockpit, nav, media, vehicle, sheet
└─ dist/IbizaDash.apk
```

## Apéndice B — Checklist de QA visual

- [ ] HUD legible a pleno sol (contraste ≥ 7:1 en texto principal).
- [ ] Ningún target < 48 dp; primarios ≥ 56 dp.
- [ ] Ninguna pantalla negra entre focos.
- [ ] Velocidad visible en todos los estados.
- [ ] El mapa conserva zoom/centro al volver del módulo.
- [ ] Back devuelve a Cockpit antes de salir.
- [ ] Llamada entrante no tapa el mapa (banner + chip).
- [ ] Alerta de velocidad se auto-oculta y no spamea.
- [ ] Modo noche no oculta el HUD.
- [ ] Reduced motion: el split no anima si el sistema lo desactiva.
- [ ] PSS < 100 MB tras 10 min de uso.
- [ ] Sin allocations en `onDraw`.

---

**Fin de la especificación.**  
Para dudas de implementación: `MainActivity.kt` es la referencia viva del canvas; `view_vehicle_module.xml` y `view_media_module.xml` son la referencia de módulos; `IbizaDash/decompiled/` + `ibiza-can-dump/` son la referencia del MCU.
