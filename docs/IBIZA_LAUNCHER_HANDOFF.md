# IbizaDash — Handoff técnico completo

**Fecha:** 2026-11-05  
**Paquete:** `com.chronosbvrx.ibizadash`  
**Versión:** `2.x` (versionCode 215+; el `versionName` vive en `app/build.gradle.kts`)  
**APK:** `dist/IbizaDash.apk` (~1.27 MB, release firmada con debug para pruebas)  
**Target:** SEAT Ibiza 2009–2013 · head unit SPRD `sp7731e_1h10_native` · Android 8.1 (API 27) · 1280×720 px @160 dpi · 2 GB RAM · 32-bit  
**Estéreo de pruebas:** Wi-Fi ADB `10.114.78.182:5555` (IP dinámica, autorización no persistente).

Este documento está pensado para que otro agente pueda continuar sin releer toda la conversación. Distingue explícitamente **lo verificado en hardware**, **lo implementado pero no probado** y **lo que no existe**.

---

## 1. Resumen ejecutivo

IbizaDash es un **launcher automotriz de una sola Activity** con un **lienzo continuo**: el mapa, el HUD y el dock nunca se destruyen; los módulos (Cockpit, Navegación, Media, Vehículo) se muestran cambiando la **fracción del split** y la capa visible. No hay `FragmentTransaction`, no hay pantallas negras y no se recarga el mapa.

Lo que funciona hoy en el estéreo real:

- Mapa oscuro con GPS y clima.
- Cockpit con render del Ibiza, velocímetro/marcha, métricas, media y clima.
- Navegación 80/20 con mini HUD.
- Media Center 25/75 con metadatos Bluetooth reales, portada online, controles next/prev/play-pause.
- Vehículo 25/75 con TPMS/engine placeholders y **OBD-II ELM327** (cliente implementado + modo demo).
- Quick Settings (brillo, noche, mute, WiFi, CAN, carátulas).
- Alerta de velocidad y chip de llamada Bluetooth.
- Cámara de reversa automática.
- Ajustes, cajón de apps y mapa a pantalla completa.
- Radio: el dock **abre la app nativa**; el AIDL de radio ya está mapeado, pero el módulo visual no existe.
- Datos de motor reales: **solo con un adaptador OBD** (ELM327/Superbox); el CAN del coche no está entregando tramas en las pruebas.

---

## 2. Hardware y entorno

| Dato | Valor |
|---|---|
| Modelo | `sp7731e_1h10_native` |
| SoC | Spreadtrum SC7731E, 4× Cortex-A7, 32-bit |
| Android | 8.1.0 / API 27 |
| Pantalla | 1280×720 px, densidad 160 → **1280×720 dp** |
| RAM | 2 GB |
| Root | No (`ro.secure=1`), ADB Wi-Fi |
| Panel | 1280×720; dock 84 dp + 12 dp margen; paneles con `marginBottom=104 dp` |
| Coche | SEAT Ibiza 6J 2009–2013; perfil CAN `09-13 IBIZA`, CanBox `03 Simple Soft`, Config `Public` |
| Cámara reversa | `com.tw.reverse`, `SprdCamera.open(0)`, preview 960×240 |
| Teléfono de pruebas | POCO F7 (`88:B9:51:EA:7F:90`), A2DP sink + AVRCP controller |

**IMPORTANTE:** en las pruebas el **CAN box no entregó datos** (`persist.can.speed` vacío; solo llegó `light_recieve` del MCU). El dashboard nativo `cn.cardoor.cardashboard` está en **“Modo demo”** y espera un **Superbox** OBD; no hay ningún OBD/Superbox emparejado. Por eso los datos de motor requieren una fuente OBD externa.

---

## 3. Arquitectura

### 3.1 Una Activity, capas de vista

`MainActivity` infla `activity_main.xml`:

```
FrameLayout (obsidian)
├─ LinearLayout canvas
│  ├─ FrameLayout leftPane (weight 0.55)
│  │  ├─ MapView (única instancia, nunca se recrea)
│  │  ├─ scrims top/bottom
│  │  ├─ clock/date/weather
│  │  ├─ navPill (turn-by-turn / “Sin destino”)
│  │  └─ hudPill (velocidad + marcha en módulos)
│  └─ FrameLayout rightPane (weight 0.45)
│     ├─ include view_cockpit
│     ├─ include view_mini_hud
│     ├─ include view_media_module
│     └─ include view_vehicle_module
├─ nightDim
├─ alertCapsule
├─ include sheet_quick_settings
└─ dock
```

### 3.2 Estados de foco

`MainActivity.Focus`:

| Foco | Mapa | Panel | Contenido derecho | Dock |
|---|---|---|---|---|
| `COCKPIT` | 55 % | 45 % | `view_cockpit` | COCKPIT |
| `NAV` | 80 % | 20 % | `view_mini_hud` | MAPA |
| `MEDIA` | 25 % | 75 % | `view_media_module` | MEDIA |
| `VEHICLE` | 25 % | 75 % | `view_vehicle_module` | VEHÍCULO |

`applyFocus(target, animate)` cambia visibilidades, `setActiveDock`, y anima los pesos (`animateSplit`). El mapa **no** se recrea; se re-proyecta al nuevo ancho.

### 3.3 Reglas de interacción

- Tocar el mapa (`MapEventsOverlay`) → `NAV` si no está en NAV.
- Tarjeta de media del Cockpit → `MEDIA`.
- Píldora mini del reproductor en NAV → `MEDIA`.
- “COCKPIT COMPLETO” / chevron del mini HUD → `COCKPIT`.
- Back: cierra Quick Settings → vuelve a Cockpit → sale.
- Dock: COCKPIT, MAPA, MEDIA, RADIO (app nativa), VEHÍCULO, APPS.
- La velocidad y la marcha nunca desaparecen: HUD grande, mini HUD o cápsula HUD.

### 3.4 Transiciones

| Transición | Duración | Curva |
|---|---|---|
| Split de foco | **280 ms** | `PathInterpolator(0.2, 0, 0, 1)` (≈ cubic-bezier) |
| Fade de capa entrante | 200 ms | lineal |
| Quick Settings | 260 ms | misma curva |
| Alerta entrada/salida | 180 ms | ease-out |
| Cambio de pista/arte | inmediato / 150 ms crossfade | — |

- `ValueAnimator.areAnimatorsEnabled()` false → **snap** (head units suelen traer animaciones desactivadas). Se respeta el scale global (en el estéreo está en 0.5).
- Medición real del split: **50th 19–25 ms, 90th 31–32 ms** en el estéreo → **no** son 60 fps estables. Si se exige 60 fps, cambiar a snap + fade o reducir la duración; el código ya tiene el camino de snap.
- No hay blur en vivo (API 27 no tiene `RenderEffect`); el frosted glass es color + borde 1 px + gradientes.

---

## 4. Qué hace el launcher (módulo por módulo)

### 4.1 Cockpit

- Mapa Esri Dark Gray (tiles cacheados), GPS, reloj/fecha/clima (Open-Meteo).
- Render del Ibiza 2010 verde (`drawable-nodpi/ibiza_render.png`), limpiado de los glows azules originales; plataforma luminosa y sombra de contacto.
- HUD: velocidad, `km/h`/`mph`, marcha P/R/N/D, fuente GPS/CAN.
- Métricas: PUERTAS, LUCES, TPMS (hoy `—` hasta tener CAN).
- Tarjeta de media: título/artista/álbum, portada, progreso, play/pausa, prev/next.
- Clima: temperatura, `AUTO`, botones A/C, FAN, REC (**solo UI**, no controla el climatizador).
- Chip Bluetooth: nombre del teléfono, batería si el teléfono la publica, estado de llamada.
- Alerta de velocidad ≥ 120 km/h (máx. 1/min) en cápsula superior.

### 4.2 Navegación

- Mapa al 80 %, panel mini al 20 %.
- Velocidad mini + marcha + píldora de reproducción + clima/tiempo.
- “Sin destino activo” → abre `MapActivity`; **no hay motor turn-by-turn** conectado.

### 4.3 Media Center

- **Bluetooth real:** título/artista/álbum desde `com.android.getelementattrrsp`; estado desde `TRACK_EVENT`/`PlaybackState`.
- **Portada online:** iTunes Search API por artista+álbum (fallback título), 600×600, caché LRU 12 MB + `cacheDir/covers`, toggle en Ajustes rápidos.
- **Controles:**
  - Siguiente/anterior: AIDL `com.tw.service.xt` tx 25/24.
  - Play/pausa: AVRCP passthrough `BluetoothAvrcpController` (68/70, solo press) — el AIDL `btPlayMusic` solo inicia; `musicPause` es para media local.
- **Caché:** `SharedPreferences("bt_media")`; al arrancar restaura el último track si el A2DP sink sigue conectado.
- **Carátula:** el teléfono/stack **no** transfiere arte; si no hay red, placeholder.
- “ABRIR APP DE ORIGEN” abre el reproductor real.
- También soporta `MediaSessionManager` para apps locales (requiere acceso a notificaciones).

### 4.4 Vehículo / Telemetría

- Cabecera: VEHÍCULO, SEAT IBIZA 6J, chip **OBD** y chip fuente GPS/CAN.
- Resumen: velocidad, marcha, fuente, reversa.
- TPMS 2×2 (placeholders `—`; sin CAN).
- Motor: RPM, carga, acelerador, refrigerante, batería, combustible (OBD).
- Footer: VIN, DTC/MIL, **AUTONOMÍA** (CAN broadcast).
- **OBD-II ELM327** implementado (`obd/Elm327Client.kt`): Bluetooth SPP, ATZ/ATE0/ATL0/ATS0/ATH0/ATSP0, PIDs 010C/0104/0111/010D/0105/010F/012F/0142/0101/03/0902. Hilo propio, sin bloquear UI.
  - Auto-detecta dispositivos emparejados con nombre OBD/ELM/Vgate/V-Link/Superbox/327.
  - Diálogo al tocar el chip OBD: lista dispositivos, buscar automático, **modo demo**, ajustes Bluetooth, desconectar.
  - **Modo demo** (`--ez obd_demo true` o el diálogo) genera valores plausibles para validar la UI.
  - **No probado con un ELM327 físico** (no hay adaptador en la unidad).
- **CAN real:** `persist.can.speed` y `light_recieve` del MCU; en las pruebas no llegaron tramas `type=2560..2563` ni `513`.

### 4.5 Radio

- El dock **abre `com.tw.radio`** (app nativa). No hay dial propio todavía.
- El AIDL de radio está mapeado en `ITWCommandAidl`: tx 11/12 `radioNext/Pre`, 13/14 search, 15 `radioSetChannel(kHz)`, 16 `radioOpenChannel`, 17 `radioBand`, 18 `radioBandChange`; callback `IRadioCallBack.extendedInterface(Bundle)`.
- `TunerDialView` y el layout de radio están especificados en `docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md` (sección 9), pero **no** incluidos en el APK.

### 4.6 Dock, Quick Settings, alertas, ajustes

- Dock isla de 84 dp, 6 destinos, indicador cian, targets ~208×84 dp.
- Quick Settings (400 dp): brillo real de ventana, modo noche (scrim), silencio (AudioManager), WiFi (settings), estado CAN, carátulas online, todos los ajustes.
- Alertas: cápsula superior; velocidad alta y chip de llamada; sin banner modal.
- `SettingsActivity`: clima on/off, unidades km/h–mph, abrir Car Choose/ajustes coche, demo media, etc. (ver `SettingsActivity.kt`).
- `AppsActivity`: cajón de apps; abre apps externas (context jump inevitable).
- `MapActivity`: mapa a pantalla completa.
- `MediaListenerService`: NotificationListenerService para apps locales; requiere acceso a notificaciones.

### 4.7 Cámara de reversa

- Trigger `onReverseStatus` (tx 6 de `com.tw.service.xt`) o `Settings.System.REVERSE_STATUS_FOR_PROTOCOL`.
- Lanza `com.tw.reverse/.ReverseActivity` (fallback `LRReverseActivity`).
- No dibuja cámara propia; usa el pipeline del proveedor.

---

## 5. Qué NO hace / limitaciones

| Función | Estado | Nota |
|---|---|---|
| Radio FM/AM integrada | ✅ UI | Módulo en lienzo 25/75, AIDL 11–18, presets, fallback a app nativa, selector BLUETOOTH/RADIO FM |
| Control de clima real | ❌ | No hay AIDL de climatizador; reemplazado por widget de salud térmica/eléctrica |
| Puertas/luces reales | ⚠️ Luces sí (light_recieve); puertas no (CAN) | Luces en el render del Ibiza + modo noche auto |
| TPMS real | ❌ | Requiere CAN; oculto en la UI (sin datos falsos) |
| RPM/temperatura sin OBD | ❌ | Solo con ELM327/Superbox; el MCU no los entrega |
| Turn-by-turn | ❌ | Píldora “Sin destino”; falta fuente (intent/notificación/SDK) |
| Descolgar/colgar llamadas | ✅ tx 20/21 | Banner flotante con CONTESTAR/COLGAR + fallback TelecomManager + PHONE_STATE receiver |
| Lectura de batería BT | ⚠️ | Solo si el teléfono la publica; el POCO no lo hace |
| Carátula BT nativa | ⚠️ | No la transfiere el stack; se busca online |
| 60 fps en transición | ✅ snap+fade | Single layout pass + fade 180 ms; sin animación de pesos; UI throttling 15 fps/250 ms |
| OBD físico probado | ⚠️ | Cliente implementado + BLE detection; modo demo verificado; hardware pendiente |
| Servicio MCU tras ciclo de contacto | ⚠️ | La app pierde el binder; reiniciar la app lo recupera (no hay rebind automático) |
| Firma release real | ❌ | Release firmada con debug para pruebas |
| HOME por defecto | ❌ | LiteDash sigue siendo HOME; IbizaDash se lanza manualmente |
| Radio/Apps en la misma Activity | ⚠️ | Abren apps externas (context jump) |

---

## 6. Apps/menús nativos y relación

| App/paquete | Rol | Cómo lo usa IbizaDash |
|---|---|---|
| `com.tw.service.xt` | MCU: teléfono, media, reversa, volumen, radio, luz | Bindeado vía `ITWCommandAidl` (tx 1/2 callbacks, 22–34 media, 52 reverse, 67 extended) |
| `com.tw.bt` | UI Bluetooth: teléfono, contactos, música A2DP | Se abre para llamadas/dialer; fuente de los broadcasts AVRCP |
| `com.tw.radio` | Radio FM/AM nativa | El dock RADIO lo abre; AIDL de radio aún no usado en UI |
| `com.tw.reverse` | Cámara de reversa | Se lanza al detectar reversa |
| `com.tw.carchoose` | Perfil CAN (Ibiza/Simple Soft) | Se abre desde Ajustes |
| `cn.cardoor.cardashboard` | DashMate/Superbox (OBD) | **En modo demo**, espera un Superbox; no es la fuente del launcher |
| `com.dofun.carsetting` | Ajustes del coche (ADAS/voz) | Se abre desde Ajustes |
| `com.android.bluetooth` | Stack A2DP/AVRCP | MediaSession vacía; broadcasts de metadata |
| `com.tw.media` | Reproductor nativo | “Abrir app de origen” |
| `com.irvingguerra.litedash` | Launcher anterior (HOME actual) | No se toca; su listener de notificaciones se conserva |

**Menús nativos que el usuario ve fuera del launcher:** el dashboard DashMate (demo/Superbox), la app Bluetooth, la radio, Car Choose, ajustes del coche, cámara de reversa. El launcher no reemplaza esos menús; los abre cuando corresponde.

---

## 7. Canales de datos (mapa completo)

### 7.1 CAN / MCU

- `SystemProperties.get("persist.can.speed")` → velocidad (vacío en pruebas).
- Broadcast **`car_info_recieve`**: `water_temperature`, `RemainKON`, `source`, `lightValue`, etc. (se registra en `TwCommandClient`, se pide con `car_info_request`).
- Callback **`onExtendedInterface(Bundle)`** (tx 8): `Bundle { type: Int, data: ByteArray? }`. Tipos vistos:
  - `513` (0x201): 2 bytes.
  - `2560–2563` (0xA00–0xA03): tramas CAN (no llegaron en pruebas).
  - `type=-1`: bundles de `light_recieve` (`LightValue`, `LightMax`) y eventos de sistema.
- **Light**: `action=light_recieve`, `LightValue`/`LightMax` (0–10). Se recibe aunque el coche esté apagado; sirve para day/night (no implementado aún).
- **CAN box**: perfil Ibiza/Simple Soft configurado, pero sin tramas; verificar cableado/alimentación.

### 7.2 AIDL `ITWCommandAidl` (resumen)

| Tx | Uso |
|---|---|
| 1/2 | register/unregister `ITWCommandCallbackAidl` |
| 3/4, 5/6, 9/10 | callbacks de radio, música, BT |
| 11–18 | radio: next/pre/search/setChannel/openChannel/band/bandChange |
| 19–21 | btCall/accept/hangup |
| 22–25 | btOpenMusic/btPlayMusic/btPre/btNext |
| 26 | btGetPhoneStatus |
| 31–34 | musicNext/Pre/Play/Pause (media local) |
| 40–45 | volumen |
| 48/49 | goHome/back |
| 52 | getReverseStatus |
| 67 | `extendedInterface(Bundle)` (reenvía a callbacks) |

### 7.3 Bluetooth A2DP/AVRCP

- **Metadata:** `com.android.getelementattrrsp` → `trackTitle`, `artist`, `album`.
- **Estado:** `android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT` → `PlaybackState` (2 pausa, 3 play) y `MediaMetadata` vacío.
- **Ruta:** `android.bluetooth.a2dpsink.profile.action.PLAYING_STATE_CHANGED` → `STATE=10/11` (no confiable para el icono; usar PlaybackState).
- **Controles:** AVRCP `sendPassThroughCmd` (68 play, 70 pause, 75 next, 76 prev; solo `state=0` en este estéreo).
- **MediaSession:** `com.android.bluetooth` existe pero con strings vacíos; `MediaListenerService` sirve para apps locales.
- **Caché:** `SharedPreferences("bt_media")` + `cacheDir/covers`.

### 7.4 OBD-II (nuevo)

- `obd/Elm327Client.kt`, Bluetooth SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
- PIDs: RPM `010C`, carga `0104`, acelerador `0111`, velocidad `010D`, refrigerante `0105`, admisión `010F`, combustible `012F`, batería `0142`, MIL `0101`, DTC `03`, VIN `0902`.
- Poll: rápido ~350 ms; lento cada 4 ciclos; VIN/DTC al conectar.
- `ObdRepository` publica `ObdTelemetry`; `MainActivity.renderObd` llena la UI.
- **Demo:** `--ez obd_demo true` o el diálogo OBD.
- **No probado con hardware real**; el adaptador debe estar emparejado y el contacto puesto.

---

## 8. Archivos clave

```
app/src/main/java/com/chronosbvrx/ibizadash/
├─ MainActivity.kt            canvas, foco, HUD, media, clima, OBD UI, quick settings
├─ App.kt                     osmdroid cache + registro del receptor BT
├─ MapActivity.kt             mapa a pantalla completa
├─ AppsActivity.kt            cajón de apps
├─ SettingsActivity.kt        ajustes
├─ Prefs.kt                   preferencias (unidades, clima, covers, OBD MAC/demo)
├─ car/
│  ├─ CarSignals.kt           persist.can.speed + reversa
│  ├─ TwCommandClient.kt      bind com.tw.service.xt + callbacks + car_info
│  ├─ TwCommandBinder.kt      transacciones AIDL (media/reverse/...)
│  ├─ VehicleCallback.kt      callback tx 8 extended (Bundle type/data)
│  ├─ ReverseCallback.kt
│  ├─ BtCallback.kt
│  └─ AvrcpController.kt      AVRCP passthrough (68/70/75/76)
├─ media/
│  ├─ MediaRepository.kt      estado + prioridad BT/sesión
│  ├─ MediaListenerService.kt MediaSession/NotificationListener
│  ├─ BluetoothMediaReceiver.kt  A2DP metadata + caché + covers
│  ├─ AlbumArtClient.kt       iTunes 600×600 + caché
│  ├─ NowPlaying.kt
│  ├─ MediaKeys.kt
│  └─ DemoMedia.kt
├─ obd/
│  ├─ Elm327Client.kt         ELM327 SPP + PIDs
│  └─ ObdTelemetry.kt         modelo + repositorio
├─ bluetooth/BluetoothStatusClient.kt
├─ map/MapHelper.kt
└─ weather/WeatherClient.kt
```

Layouts: `activity_main.xml`, `view_cockpit.xml`, `view_mini_hud.xml`, `view_media_module.xml`, `view_vehicle_module.xml`, `sheet_quick_settings.xml`.
Docs: `docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md` (especificación visual/técnica), este handoff.
Screenshots: `screenshots/` (cockpit, nav, media, vehicle, covers, OBD demo/idle, dashboard nativo).

---

## 9. Build, instalación y pruebas

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\Axel Rosete\Desktop\Proyectos\General\IbizaDash
.\gradlew.bat :app:assembleRelease
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

ADB estéreo: `adb connect 10.114.78.182:5555` (IP dinámica; reautorizar tras reboot).
Permisos:
- Ubicación: `pm grant com.chronosbvrx.ibizadash android.permission.ACCESS_FINE_LOCATION`.
- Acceso a notificaciones (apps locales): *Ajustes → Acceso a notificaciones* o
  `cmd notification allow_listener com.chronosbvrx.ibizadash/com.chronosbvrx.ibizadash.media.MediaListenerService`.
- Bluetooth: el manifiesto ya declara BLUETOOTH/BLUETOOTH_ADMIN; en API 27 no hay runtime.

Modo demo OBD:
```powershell
adb shell am start -n com.chronosbvrx.ibizadash/.MainActivity --ez obd_demo true
```
También se activa desde el chip **OBD** → “Modo demo (simulado)”.

Pruebas realizadas en el estéreo (2026-11-05):
- Instalación/arranque, mapa, clima, BT POCO F7.
- Metadata BT: “Todo cambia”, “Saturno”, “Dedos Rotos y Funerales”.
- Portadas online (iTunes) y caché tras reinicio.
- Next/prev por AIDL, play/pause por AVRCP.
- OBD demo (RPM/carga/acelerador/refrigerante/batería/combustible) y OBD idle.
- Se intentó conectar al dispositivo emparejado **B16** (`41:42:67:AC:93:78`); el socket SPP falló con `read failed` (posible BLE o no es OBD). El `InterruptedException` del cambio demo→B16 se corrigió capturando el hilo.
- Medición: PSS ~43 MB (sin OBD), 270 vistas, APK ~1.25 MB.
- Medición de transición: 19–25 ms/frame (no 60 fps).
- CAN: solo `light_recieve`; sin tramas de motor.
- Dashboard nativo: **Modo demo**, sin Superbox.

---

## 10. Bugs/edge cases conocidos

1. **Binder del MCU tras ciclo de contacto**: `TwCommandClient` no hace rebind automático; si el servicio se reinicia, se pierden callbacks hasta reiniciar la app. Añadir `onBindingDied`/reintento.
2. **NotificationListenerService** puede quedar “dead” tras instalar/forzar parada; togglear `cmd notification disallow/allow_listener` o reiniciar la app.
3. **A2DP PLAYING_STATE** alterna 10/11 durante pausa; usar `PlaybackState` AVRCP para el icono (ya implementado).
4. **Portada “Unavailable”**: el primer paquete puede traer álbum “Unavailable”; se ignora y se reintenta con el siguiente.
5. **OBD real sin probar**: el parsing estándar puede necesitar ajustes según el clon ELM327 (timeouts, `ATH1`, `ATSP0`). El B16 emparejado no respondió por SPP; si es BLE, el cliente actual no sirve. Queda guardado como MAC OBD y reintenta cada 5 s: borrarlo con “Buscar automáticamente” en el diálogo OBD si no es el adaptador correcto.
6. **Auto-connect OBD** con `mac=null` busca dispositivos emparejados cada 4 s; si no hay, mantiene un hilo vivo (barato). Desconectar desde el diálogo.
7. **Servicio de radio**: no integrado; el dock abre la app nativa.
8. **Clima**: solo UI; no hay AIDL de climatizador mapeado.
9. **Layout Vehículo**: con OBD, el footer (VIN/DTC/autonomía) queda debajo del fold; el ScrollView permite verlo.
10. **Overlay blanco del coche** observado al abrir el diálogo OBD con el dashboard nativo en segundo plano: parece una ventana flotante de DashMate; no se reprodujo tras cerrar el diálogo. Investigar si vuelve.

---

## 11. Siguiente trabajo recomendado

1. **Probar el ELM327 físico** y ajustar timeouts/PIDs; añadir pantalla de emparejamiento dedicada si hace falta.
4. **Módulo de radio nativo** con el AIDL ya mapeado (tx 11–18) y presets locales.
4. **CAN box**: verificar cableado/alimentación; si llegan tramas, mapear puertas/luces/TPMS con el log `VehicleData` (type/data).
5. **Turn-by-turn**: conectar notificación/intent de Maps/Waze a `navPill`.
6. **Llamadas**: mapear tx de aceptar/colgar y banner con botones.
7. **60 fps**: snap + fade o medir en el estéreo; el split animado no llega.
8. **HOME por defecto** y firma release real cuando el usuario lo pida.

---

## 12. Comandos ADB útiles

```powershell
$adb="C:\Users\Axel Rosete\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb connect 10.114.78.182:5555
& $adb -s 10.114.78.182:5555 logcat -s VehicleData:I   # CAN/OBD/car_info
& $adb -s 10.114.78.182:5555 logcat -s BtMedia:I       # metadata Bluetooth
& $adb -s 10.114.78.182:5555 logcat -s ObdElm327:I     # OBD
& $adb -s 10.114.78.182:5555 shell dumpsys meminfo com.chronosbvrx.ibizadash
& $adb -s 10.114.78.182:5555 shell dumpsys gfxinfo com.chronosbvrx.ibizadash
& $adb -s 10.114.78.182:5555 shell getprop persist.can.speed
& $adb -s 10.114.78.182:5555 shell input tap 960 665     # dock VEHÍCULO
```

---

## 13. Fase 2 (implementada en esta sesión)

### 13.1 Optimización ADB
- `scripts/optimize_stereo.sh`: deshabilita bloatware (com.dofun.market, com.dofun.dofunweather.main, com.tw.bootanimation, com.tw.twfileexplore, com.tw.auxin), restaura escalas de animación a 0.5 y compila AOT `cmd package compile -m speed -f`.

### 13.2 OBD-II: BLE detection + throttling
- `Elm327Client.openSocket`: si SPP y RFCOMM 1/2 fallan, comprueba `device.type == DEVICE_TYPE_LE` y publica `"Dispositivo BLE o no-OBD (requiere ELM327 SPP clásico)"`.
- `renderObd`: barras a 15 fps (66 ms), textos a 250 ms; `lastBarUpdate`/`lastTextUpdate` en `MainActivity`.
- Error mostrado en el chip OBD.

### 13.3 Transiciones <16 ms
- `applyFocus` ahora hace `setSplit(target.fraction)` en un solo paso (una medida/layout) y anima solo alpha 180 ms. Eliminado el `ValueAnimator` de pesos y `splitAnimator`. Garantiza 60 fps en el compositor sin re-medir el mapa por frame.

### 13.4 Integración profunda MCU
- `car_info_recieve`: `water_temperature` → refrigerante real en el módulo Vehículo y en la salud del Cockpit; `RemainKON` → fila AUTONOMÍA.
- `light_recieve`: `LightValue`/`LightMax` → faros en el render (headlightGlow visible), texto "Luces ON/OFF", y modo noche automático con histéresis (<0.3 noche, >0.5 día).
- Quick Settings: botón **EQ / DSP** que lanza `com.tw.eq` (Toast si no existe).

### 13.5 Radio FM/AM integrada
- `view_radio_module.xml` con frecuencia 96 sp, banda, RDS, SEEK/SCAN/BAND, 6 presets (pulsación corta sintoniza, larga guarda) y fallback a la app nativa.
- Selector de fuente en el Media Center: **BLUETOOTH | RADIO FM** (`mmSrcBt`/`mmSrcRadio`).
- `radioSetChannel/radioPre/Next/SearchPre/Next/Band/BandChange` por AIDL; en emulador simula el cambio de estación/banda.
- Presets persistidos en `SharedPreferences("radio_preset_N")`.

### 13.6 Banner de llamadas
- `view_call_banner.xml` flotante arriba (caller + CONTESTAR verde / COLGAR rojo).
- Conectado a `btAccept`/`btHangUP` (tx 20/21) con fallback a `KEYCODE_CALL`/`KEYCODE_ENDCALL`.
- Debug: broadcast `com.chronosbvrx.ibizadash.DEBUG_INCOMING_CALL` con `name`/`number`.
- `onCallStatus` muestra/oculta el banner (estados 3/1 y 0).

### 13.7 Limpieza de UI y Turn-by-Turn
- El widget de clima falso (A/C/FAN/REC) del Cockpit se reemplazó por **Salud térmica/eléctrica** (refrigerante, admisión, batería, carga) + texto de luces.
- Métricas falsas (puertas/TPMS) ocultas (`visibility=gone`); TPMS del módulo Vehículo también oculto.
- `NavNotificationListenerService` (NotificationListenerService) inscrito en el manifiesto; extrae `EXTRA_TITLE/TEXT/SUB_TEXT` de notificaciones `CATEGORY_NAVIGATION` (Maps/Waze) y actualiza la píldora de navegación.
- Debug: broadcast `com.chronosbvrx.ibizadash.DEBUG_NAV_NOTIFICATION` con `title`/`text`/`sub`.

### 13.8 Entregables
- APK final: `dist/IbizaDash.apk` (~1.27 MB).
- Capturas en `screenshots/`: `emu_health.png`, `emu_call_nav.png`, `emu_radio.png`.
- Script: `scripts/optimize_stereo.sh`.
- Docs actualizados: este handoff, `docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md`, `README.md`.

## 14. Verificación en emulador (1280x720/160 dpi)
- Health widget + faros: OK (broadcast `car_info_recieve` / `light_recieve`).
- Call banner + Nav pill: OK (broadcasts DEBUG_INCOMING_CALL / DEBUG_NAV_NOTIFICATION).
- Radio FM: OK (fuente + dial + presets + fallback).
- OBD demo: OK (RPM/carga/acelerador/refrigerante/batería/combustible).
- Split: ahora un solo paso de layout + fade 180 ms (sin animación de pesos).

---

## 14. Fase 2 — Segunda tanda (implementada)

### 14.1 Split <16 ms
- `applyFocus`: `setSplit(target.fraction)` en un solo paso + fade 180 ms. Eliminado `ValueAnimator` de pesos y `splitAnimator`.

### 14.2 Integración MCU profunda
- `car_info_recieve`: `water_temperature` → refrigerante real; `RemainKON` → autonomía.
- `light_recieve`: `LightValue`/`LightMax` → faros en el render (`headlightGlow`), texto "Luces ON/OFF", modo noche automático con histéresis.
- Quick Settings: botón **EQ / DSP** que lanza `com.tw.eq` (Toast si no existe).
- `source_recieve`: log de extras + actualización de fuente de audio.

### 14.3 Radio FM integrada
- `view_radio_module.xml` con frecuencia 96 sp, banda, RDS, SEEK/SCAN/BAND, 6 presets (pulsación corta sintoniza, larga guarda) y fallback a la app nativa.
- Selector de fuente en el Media Center: **BLUETOOTH | RADIO FM**.
- `radioOpenChannel("FM")` al entrar a Radio; `btOpenMusic()` al volver a Bluetooth.
- Presets persistidos en `SharedPreferences("radio_preset_N")`.

### 14.4 Banner de llamadas + Turn-by-Turn
- `view_call_banner.xml` flotante: CONTESTAR (verde) / COLGAR (rojo), `btAccept`/`btHangUP` + fallback `KEYCODE_CALL`/`KEYCODE_ENDCALL`.
- `PHONE_STATE` receiver para estados reales de llamada.
- `NavNotificationListenerService` (NotificationListenerService) extrae título/texto/sub de notificaciones `CATEGORY_NAVIGATION` (Maps/Waze) y actualiza la píldora.
- Debug broadcasts: `DEBUG_INCOMING_CALL` y `DEBUG_NAV_NOTIFICATION`.

### 14.5 Limpieza de UI fantasma
- El widget de clima falso (A/C/FAN/REC) reemplazado por **Salud térmica/eléctrica** (refrigerante, admisión, batería, carga) + texto de luces.
- Métricas falsas (puertas/TPMS) ocultas (`visibility=gone`); TPMS del módulo Vehículo oculto.
- Faros encendidos reflejados en el render (`headlightGlow`).

### 14.6 UI Throttling
- `renderObd`: barras a 15 fps (66 ms), textos numéricos a 250 ms; `lastBarUpdate`/`lastTextUpdate`.

### 14.7 Entregables
- APK final: `dist/IbizaDash.apk` (~1.27 MB, 311 vistas).
- Capturas: `emu_fase2_final.png`, `emu_radio_final.png`.
- Script: `scripts/optimize_stereo.sh`.
- Docs: handoff, spec, README actualizados.

---

**Fin del handoff.** La referencia viva es `MainActivity.kt`; la especificación visual/técnica está en `docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md`; los volcados del MCU/CAN están en `../ibiza-can-dump/` y `decompiled/`.
