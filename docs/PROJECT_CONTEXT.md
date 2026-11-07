# IbizaDash — Resumen técnico para evaluación externa

> Documento de contexto completo. Objetivo: que un experto que no conoce el proyecto pueda
> evaluar la app y proponer modificaciones sin acceder al APK, al estéreo ni al código.
> Fuentes: `README.md`, `docs/IBIZA_LAUNCHER_HANDOFF.md`, `docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md`,
> `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, código fuente.

---

## 1. Identidad del proyecto

- **Nombre:** IbizaDash
- **Paquete:** `com.chronosbvrx.ibizadash`
- **Versión:** `versionName = "0.3.0"`, `versionCode = 3` (release firmada con llave debug para pruebas).
- **Build:** Gradle Kotlin DSL, `minSdk 23`, `targetSdk 33`, `compileSdk 36`, Java/Kotlin 17, ViewBinding habilitado.
- **Dependencias únicas:** `androidx.core:core-ktx`, `androidx.appcompat`, `org.osmdroid:osmdroid-android:6.1.18`, `kotlinx-coroutines-android`. **Sin Compose, sin librerías nativas, sin Play Services.**
- **APK release:** `dist/IbizaDash.apk` (~1.25–1.27 MB, R8 + shrinkResources).
- **Tipo:** Launcher automotriz (dashboard) para una head unit aftermarket específica.
- **Autor/owner:** ChronosBVRX (mismo autor que el proyecto Vision+, pero son proyectos independientes).

## 2. Hardware objetivo (único soportado de verdad)

| Dato | Valor |
|---|---|
| Modelo de head unit | `sp7731e_1h10_native` |
| SoC | Spreadtrum SC7731E, 4× Cortex-A7, **32-bit** |
| Android | 8.1.0 / API 27 |
| Pantalla | 1280×720 px, densidad 160 → **1280×720 dp** |
| RAM | 2 GB |
| Root | No (`ro.secure=1`); ADB Wi-Fi |
| Panel | dock 84 dp + margen 12 dp; paneles con `marginBottom=104 dp` |
| Coche | SEAT Ibiza 6J 2009–2013; perfil CAN `09-13 IBIZA`, CanBox `03 Simple Soft`, Config `Public` |
| Cámara reversa | `com.tw.reverse`, `SprdCamera.open(0)`, preview 960×240 |
| Teléfono de pruebas | POCO F7 (`88:B9:51:EA:7F:90`), A2DP sink + AVRCP controller |
| Estéreo de pruebas | Wi-Fi ADB `10.114.78.182:5555` (IP dinámica, autorización no persistente) |

**Importante:** en las pruebas el **CAN box no entregó datos** (`persist.can.speed` vacío; solo llegó `light_recieve` del MCU). El dashboard nativo `cn.cardoor.cardashboard` está en **“Modo demo”** y espera un **Superbox OBD**. Por eso los datos de motor requieren una fuente OBD externa (ELM327/Superbox).

## 3. Qué hace la app (funcionalidades verificadas)

### 3.1 Launcher de una sola Activity con lienzo continuo
- `MainActivity` infla `activity_main.xml`: FrameLayout (obsidian) con un `LinearLayout` canvas que contiene `leftPane` (mapa) y `rightPane` (módulos), más `nightDim`, `alertCapsule`, `sheet_quick_settings` y `dock`.
- **El mapa (MapView de osmdroid) es una única instancia que nunca se recrea.** Los módulos (Cockpit, Navegación, Media, Vehículo, Radio) se muestran cambiando la fracción del split y la capa visible. No hay `FragmentTransaction`, no hay pantallas negras, no se recarga el mapa.
- `Focus` enum: `COCKPIT` (55/45), `NAV` (80/20), `MEDIA` (25/75), `VEHICLE` (25/75), y Radio integrado en el Media Center con selector de fuente.
- `applyFocus(target, animate)`: cambia visibilidades, `setActiveDock`, anima pesos. Mapa no se recrea, se re-proyecta.
- Interacción: tocar mapa → NAV; tarjeta de media del Cockpit → MEDIA; píldora mini en NAV → MEDIA; “COCKPIT COMPLETO”/chevron → COCKPIT; Back cierra Quick Settings → Cockpit → sale; dock con 6 destinos.

### 3.2 Cockpit
- Mapa Esri Dark Gray (tiles cacheados), GPS, reloj/fecha/clima (Open-Meteo).
- Render del SEAT Ibiza 2010 verde (`drawable-nodpi/ibiza_render.png`), limpiado de glows azules; plataforma luminosa y sombra.
- HUD: velocidad, km/h–mph, marcha P/R/N/D, fuente GPS/CAN.
- Métricas: PUERTAS/LUCES/TPMS (hoy `—`/ocultas hasta CAN); widget de **Salud térmica/eléctrica** (refrigerante, admisión, batería, carga) + texto de luces.
- Tarjeta de media: título/artista/álbum, portada, progreso, play/pausa, prev/next.
- Clima: temperatura, AUTO, A/C, FAN, REC (**solo UI**, no controla climatizador; reemplazado por salud).
- Chip Bluetooth: nombre del teléfono, batería si la publica, estado de llamada.
- Alerta de velocidad ≥ 120 km/h (máx. 1/min) en cápsula superior.
- Faros encendidos reflejados en el render (`headlightGlow`); modo noche automático por histéresis de `light_recieve`.

### 3.3 Navegación
- Mapa al 80%, panel mini al 20%.
- Velocidad mini + marcha + píldora de reproducción + clima/tiempo.
- **Navegación turn-by-turn propia** (`nav/RouteNavigator.kt`): banner de maniobra, calle, cuenta regresiva, restante/ETA, progreso y detección de salida de ruta. La posición se adhiere a la carretera (`nav/RoadSnapper.kt`). La ruta llega del teléfono (OSRM) o de un intent de depuración.

### 3.4 Media Center
- **Bluetooth real:** título/artista/álbum desde broadcast `com.android.getelementattrrsp`; estado desde `TRACK_EVENT`/`PlaybackState`.
- **Portada online:** iTunes Search API por artista+álbum (fallback título), 600×600, caché LRU 12 MB + `cacheDir/covers`, toggle en Quick Settings.
- **Controles:** siguiente/anterior por AIDL `com.tw.service.xt` tx 25/24; play/pausa por AVRCP passthrough `BluetoothAvrcpController` (68/70, solo press).
- **Caché:** `SharedPreferences("bt_media")`; al arrancar restaura el último track si el A2DP sink sigue conectado.
- “ABRIR APP DE ORIGEN” abre el reproductor nativo.
- Soporta `MediaSessionManager` para apps locales (requiere acceso a notificaciones).
- **Radio FM/AM integrada:** `view_radio_module.xml` con frecuencia 96 sp, banda, RDS, SEEK/SCAN/BAND, 6 presets (pulsación corta sintoniza, larga guarda) y fallback a la app nativa. Selector de fuente BLUETOOTH | RADIO FM en el Media Center. Presets en `SharedPreferences("radio_preset_N")`.
- Carátula: el stack no transfiere arte; si no hay red, placeholder.

### 3.5 Vehículo / Telemetría
- Cabecera: VEHÍCULO, SEAT IBIZA 6J, chip OBD y chip fuente GPS/CAN.
- Resumen: velocidad, marcha, fuente, reversa.
- TPMS 2×2 (placeholders `—`; sin CAN, oculto).
- Motor: RPM, carga, acelerador, refrigerante, batería.
- **Nivel de combustible real por UDS del cuadro** (DID 0x2206, CAN 0x714→0x77E): el motor no responde al PID 2F; sin lectura se muestra “—”, nunca un valor inventado.
- Footer: VIN, DTC/MIL con diccionario específico del **2.0 CEKA**.
- **OBD-II ELM327** implementado (`obd/Elm327Client.kt`): Bluetooth SPP, ATZ/ATE0/ATL0/ATS0/ATH0/ATSP0, PIDs 010C/0104/0111/010D/0105/010F/012F/0142/0101/03/0902. Hilo propio, sin bloquear UI. Auto-detecta dispositivos emparejados con nombre OBD/ELM/Vgate/V-Link/Superbox/327. Diálogo al tocar el chip OBD. **Modo demo** (`--ez obd_demo true` o el diálogo) genera valores plausibles. **No probado con ELM327 físico.**
- **Diccionario DTC del coche** (`obd/DtcDictionary.kt`): 158 códigos SAE/VAG + 19 códigos de taller de 5 dígitos, explicados para el **Ibiza 6J 2010 2.0 MPI 8V CEKA**; explorador táctil en Vehículo → Fallas.
- **CAN real:** `persist.can.speed` y `light_recieve` del MCU; en pruebas no llegaron tramas `type=2560..2563` ni `513`.

### 3.6 Dock, Quick Settings, alertas, ajustes
- Dock isla de 84 dp, 6 destinos (COCKPIT, MAPA, MEDIA, RADIO, VEHÍCULO, APPS), indicador cian, targets ~208×84 dp.
- Quick Settings (400 dp): brillo real de ventana, modo noche (scrim), silencio (AudioManager), WiFi (settings), estado CAN, carátulas online, EQ/DSP (lanza `com.tw.eq`), todos los ajustes.
- Alertas: cápsula superior; velocidad alta y chip de llamada; sin banner modal. **Banner de llamadas** flotante arriba (caller + CONTESTAR verde / COLGAR rojo) conectado a `btAccept`/`btHangUP` (tx 20/21) con fallback a `KEYCODE_CALL`/`KEYCODE_ENDCALL`; receiver `PHONE_STATE`.
- `SettingsActivity`: clima on/off, unidades km/h–mph, abrir Car Choose/ajustes coche, demo media, etc.
- `AppsActivity`: cajón de apps; abre apps externas (context jump inevitable).
- `MapActivity`: mapa a pantalla completa.
- `MediaListenerService`: NotificationListenerService para apps locales.

### 3.7 Cámara de reversa
- Trigger `onReverseStatus` (tx 6 de `com.tw.service.xt`) o `Settings.System.REVERSE_STATUS_FOR_PROTOCOL`.
- Lanza `com.tw.reverse/.ReverseActivity` (fallback `LRReverseActivity`).
- No dibuja cámara propia; usa el pipeline del proveedor.

### 3.8 Catálogo musical offline (módulo listo, no cableado a UI)
- Paquete `music/`: `OfflineMusicCatalog.kt` (abre `catalog.db` solo lectura, lookup y stats), `TextNormalizer.kt`, `CoverCache.kt` (LRU 8 MB, RGB_565, decode en hilo), `CatalogModels.kt`.
- Ruta por defecto `/sdcard/Android/data/com.chronosbvrx.ibizadash/files/music_catalog/`, llena con `MusicCatalog/push_catalog.ps1`.
- `App.onCreate` → `OfflineMusicCatalog.initialize(context)`. `BluetoothMediaReceiver` busca primero en el catálogo y, si no hay coincidencia, cae a iTunes. `CoverCache` decodifica en un hilo con LRU 8 MB.
- Regla `-keep` en `proguard-rules.pro` para que R8 no elimine el módulo. No requiere Internet ni permisos de almacenamiento.

## 4. Qué NO hace / limitaciones

| Función | Estado | Nota |
|---|---|---|
| Radio FM/AM integrada | ✅ UI | Módulo en lienzo 25/75, AIDL 11–18, presets, fallback a app nativa, selector BLUETOOTH/RADIO FM |
| Control de clima real | ❌ | No hay AIDL de climatizador; reemplazado por widget de salud térmica/eléctrica |
| Puertas/luces reales | ⚠️ Luces sí (`light_recieve`); puertas no (CAN) | Luces en el render del Ibiza + modo noche auto |
| TPMS real | ❌ | Requiere CAN; oculto en la UI (sin datos falsos) |
| RPM/temperatura sin OBD | ❌ | Solo con ELM327/Superbox; el MCU no los entrega |
| Turn-by-turn | ✅ | Motor propio en el estéreo + ruta OSRM desde IbizaCompanion; banner, calle, cuenta regresiva y ETA |
| Diccionario DTC | ✅ | 158 códigos SAE + VAG del 2.0 CEKA + códigos de taller; explorador táctil |
| Descolgar/colgar llamadas | ✅ tx 20/21 | Banner flotante + fallback TelecomManager + PHONE_STATE receiver |
| Lectura de batería BT | ⚠️ | Solo si el teléfono la publica; el POCO no lo hace |
| Carátula BT nativa | ⚠️ | No la transfiere el stack; se busca online/catálogo |
| 60 fps en transición | ✅ snap+fade | Single layout pass + fade 180 ms; sin animación de pesos; UI throttling 15 fps/250 ms |
| OBD físico probado | ⚠️ | Cliente implementado + BLE detection; modo demo verificado; hardware pendiente |
| Servicio MCU tras ciclo de contacto | ⚠️ | La app pierde el binder; reiniciar la app lo recupera (no hay rebind automático) |
| Firma release real | ❌ | Release firmada con debug para pruebas |
| HOME por defecto | ❌ | LiteDash sigue siendo HOME; IbizaDash se lanza manualmente |
| Radio/Apps en la misma Activity | ⚠️ | Abren apps externas (context jump) |
| Mapas offline | ✅ | Pack Esri Dark z11–z15 (Michoacán + vecinos) en `files/osmdroid/*.mbtiles`; el teléfono usa PNG en `offline-tiles/` |
| RPM/puertas/luces/TPMS por CAN | ⏳ pendiente | El servicio CAN del fabricante está ofuscado |

## 5. Pantallas nativas y relación con apps del coche

La app es **una sola Activity principal** (`MainActivity`). Las demás Activities son auxiliares:

| Activity | Rol |
|---|---|
| `MainActivity` | Dashboard principal (lienzo continuo, todos los módulos) |
| `MapActivity` | Mapa a pantalla completa |
| `AppsActivity` | Cajón de apps |
| `SettingsActivity` | Ajustes |

Apps/menús nativos que el launcher **no reemplaza** (los abre cuando corresponde):

| App/paquete | Rol | Cómo lo usa IbizaDash |
|---|---|---|
| `com.tw.service.xt` | MCU: teléfono, media, reversa, volumen, radio, luz | Bindeado vía `ITWCommandAidl` (tx 1/2 callbacks, 22–34 media, 52 reverse, 67 extended) |
| `com.tw.bt` | UI Bluetooth: teléfono, contactos, música A2DP | Se abre para llamadas/dialer; fuente de los broadcasts AVRCP |
| `com.tw.radio` | Radio FM/AM nativa | El dock RADIO lo abría; ahora hay módulo propio con fallback a la app nativa |
| `com.tw.reverse` | Cámara de reversa | Se lanza al detectar reversa |
| `com.tw.carchoose` | Perfil CAN (Ibiza/Simple Soft) | Se abre desde Ajustes |
| `cn.cardoor.cardashboard` | DashMate/Superbox (OBD) | En modo demo, espera un Superbox; no es la fuente del launcher |
| `com.dofun.carsetting` | Ajustes del coche (ADAS/voz) | Se abre desde Ajustes |
| `com.android.bluetooth` | Stack A2DP/AVRCP | MediaSession vacía; broadcasts de metadata |
| `com.tw.media` | Reproductor nativo | “Abrir app de origen” |
| `com.irvingguerra.litedash` | Launcher anterior (HOME actual) | No se toca; su listener de notificaciones se conserva |
| `com.tw.eq` | EQ/DSP | Lanzado desde Quick Settings |

## 6. Arquitectura técnica

### 6.1 Una Activity, capas de vista
```
FrameLayout (obsidian)
├─ LinearLayout canvas
│  ├─ FrameLayout leftPane (weight 0.55)   # MapView (única instancia) + scrims + clock/date/weather + navPill + hudPill
│  └─ FrameLayout rightPane (weight 0.45)  # view_cockpit / view_mini_hud / view_media_module / view_vehicle_module / view_radio_module
├─ nightDim
├─ alertCapsule
├─ include sheet_quick_settings
└─ dock
```

### 6.2 Estados de foco y fracciones del split
| Foco | Mapa | Panel | Contenido derecho | Dock |
|---|---|---|---|---|
| COCKPIT | 55 % | 45 % | view_cockpit | COCKPIT |
| NAV | 80 % | 20 % | view_mini_hud | MAPA |
| MEDIA | 25 % | 75 % | view_media_module | MEDIA |
| VEHICLE | 25 % | 75 % | view_vehicle_module | VEHÍCULO |
| (Radio) | 25 % | 75 % | view_radio_module (dentro de MEDIA) | RADIO (app nativa) |

`applyFocus` cambia visibilidades, `setActiveDock`, y anima los pesos. El mapa no se recrea; se re-proyecta al nuevo ancho.

### 6.3 Transiciones
- Split de foco: originalmente 280 ms con `PathInterpolator(0.2,0,0,1)`; tras Fase 2 se hace `setSplit(target.fraction)` en un solo paso (una medida/layout) + fade 180 ms. Eliminado `ValueAnimator` de pesos y `splitAnimator`.
- Fade de capa entrante: 200 ms lineal.
- Quick Settings: 260 ms.
- Alerta entrada/salida: 180 ms ease-out.
- Cambio de pista/arte: inmediato / 150 ms crossfade.
- `ValueAnimator.areAnimatorsEnabled()` false → snap (head units suelen traer animaciones desactivadas; en el estéreo scale global 0.5).
- Medición real del split: 50th 19–25 ms, 90th 31–32 ms → no son 60 fps estables. Tras Fase 2 garantiza 60 fps en el compositor sin re-medir el mapa por frame.
- No hay blur en vivo (API 27 no tiene `RenderEffect`); el frosted glass es color + borde 1 px + gradientes.

### 6.4 Canales de datos
**CAN / MCU:**
- `SystemProperties.get("persist.can.speed")` → velocidad (vacío en pruebas).
- Broadcast `car_info_recieve`: `water_temperature`, `RemainKON`, `source`, `lightValue`, etc. (se registra en `TwCommandClient`, se pide con `car_info_request`).
- Callback `onExtendedInterface(Bundle)` (tx 8): `Bundle { type: Int, data: ByteArray? }`. Tipos vistos: `513` (0x201) 2 bytes; `2560–2563` (0xA00–0xA03) tramas CAN (no llegaron); `type=-1` bundles de `light_recieve` (`LightValue`, `LightMax`) y eventos de sistema.
- `light_recieve`: `LightValue`/`LightMax` (0–10). Se recibe aunque el coche esté apagado; sirve para day/night.
- CAN box: perfil Ibiza/Simple Soft configurado, pero sin tramas; verificar cableado/alimentación.

**AIDL `ITWCommandAidl` (resumen):**
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
| 67 | extendedInterface(Bundle) (reenvía a callbacks) |

**Bluetooth A2DP/AVRCP:**
- Metadata: `com.android.getelementattrrsp` → `trackTitle`, `artist`, `album`.
- Estado: `android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT` → `PlaybackState` (2 pausa, 3 play) y `MediaMetadata` vacío.
- Ruta: `android.bluetooth.a2dpsink.profile.action.PLAYING_STATE_CHANGED` → `STATE=10/11` (no confiable para el icono; usar PlaybackState).
- Controles: AVRCP `sendPassThroughCmd` (68 play, 70 pause, 75 next, 76 prev; solo `state=0` en este estéreo).
- MediaSession: `com.android.bluetooth` existe pero con strings vacíos; `MediaListenerService` sirve para apps locales.
- Caché: `SharedPreferences("bt_media")` + `cacheDir/covers`.

**OBD-II:**
- `obd/Elm327Client.kt`, Bluetooth SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
- PIDs: RPM `010C`, carga `0104`, acelerador `0111`, velocidad `010D`, refrigerante `0105`, admisión `010F`, combustible `012F`, batería `0142`, MIL `0101`, DTC `03`, VIN `0902`.
- Poll: rápido ~350 ms; lento cada 4 ciclos; VIN/DTC al conectar.
- `ObdRepository` publica `ObdTelemetry`; `MainActivity.renderObd` llena la UI.
- Demo: `--ez obd_demo true` o el diálogo OBD.
- No probado con hardware real; el adaptador debe estar emparejado y el contacto puesto. BLE detection: si SPP/RFCOMM 1/2 fallan, comprueba `device.type == DEVICE_TYPE_LE` y publica mensaje de error.

**Turn-by-turn:**
- `NavNotificationListenerService` (NotificationListenerService) inscrito en el manifiesto; extrae `EXTRA_TITLE/TEXT/SUB_TEXT` de notificaciones `CATEGORY_NAVIGATION` (Maps/Waze) y actualiza la píldora de navegación.
- Debug broadcast: `com.chronosbvrx.ibizadash.DEBUG_NAV_NOTIFICATION` con `title`/`text`/`sub`.

**Llamadas:**
- Banner flotante con CONTESTAR/COLGAR conectado a `btAccept`/`btHangUP` (tx 20/21) + fallback a `KEYCODE_CALL`/`KEYCODE_ENDCALL`.
- `PHONE_STATE` receiver para estados reales de llamada.
- Debug broadcast: `com.chronosbvrx.ibizadash.DEBUG_INCOMING_CALL` con `name`/`number`.

### 6.5 Servicios en segundo plano
- `MediaListenerService`: NotificationListenerService para apps locales; requiere acceso a notificaciones.
- `NavNotificationListenerService`: NotificationListenerService para navegación.

## 7. Configuración disponible (qué puede configurar el usuario)

### 7.1 Preferencias (`Prefs.kt`, archivo `ibizadash`)
| Clave | Tipo | Default | Significado |
|---|---|---|---|
| `show_weather` | Boolean | true | Mostrar clima |
| `use_mph` | Boolean | false | Unidades km/h vs mph |
| `demo_media` | Boolean | false | Modo demo de media |
| `map_follow` | Boolean | (no leído en Prefs.kt actual) | — |
| `online_covers` | Boolean | true | Carátulas online (iTunes) |
| `obd_mac` | String | null | MAC del adaptador OBD |
| `obd_demo` | Boolean | false | Modo demo OBD |
| `radio_preset_N` | Int | 0 | Presets de radio (6 slots) |

### 7.2 Quick Settings (400 dp)
- Brillo real de ventana.
- Modo noche (scrim).
- Silencio (AudioManager).
- WiFi (settings).
- Estado CAN.
- Carátulas online (toggle).
- EQ/DSP (lanza `com.tw.eq`).
- Todos los ajustes (abre SettingsActivity).

### 7.3 SettingsActivity
- Clima on/off.
- Unidades km/h–mph.
- Abrir Car Choose / ajustes coche.
- Demo media.
- (Ver `SettingsActivity.kt` para la lista completa.)

### 7.4 Ajustes de instalación / sistema (fuera de la app)
- Acceso a notificaciones (para MediaListenerService y NavNotificationListenerService).
- HOME por defecto (actualmente LiteDash; IbizaDash se lanza manualmente).
- Permisos: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_PHONE_STATE`, `BLUETOOTH`/`BLUETOOTH_CONNECT`.
- Acceso a notificaciones por ADB: `cmd notification allow_listener com.chronosbvrx.ibizadash/...MediaListenerService` y `...NavNotificationListenerService`.
- Firma release real (pendiente).

### 7.5 Parámetros de lanzamiento por intent
- `--ez demo_media true` → modo demo de media.
- `--ez obd_demo true` → modo demo OBD.
- Debug broadcasts: `com.chronosbvrx.ibizadash.DEBUG_INCOMING_CALL` (name/number), `com.chronosbvrx.ibizadash.DEBUG_NAV_NOTIFICATION` (title/text/sub).

### 7.6 Lo que NO se puede configurar hoy
- Perfil de coche / CAN (se abre Car Choose nativo, no se edita dentro).
- Climatizador (no hay AIDL).
- Radio: no hay dial propio completo (ahora sí hay módulo con presets, pero la radio nativa sigue siendo la fuente real).
- Mapas offline: pack Esri Dark z11–z15 en `files/osmdroid/` (estéreo) y `offline-tiles/` (teléfono); se despliega con `scripts/deploy_offline.ps1`.
- Firma/actualización OTA (no existe; se instala por ADB).
- Resolución/orientación (fija landscape 1280×720).

## 8. Rendimiento y memoria

- Medición en emulador (1280×720/160 dpi) tras el rediseño premium: **PSS 79.8 MB**, **388 vistas**,
  APK release **1.36 MB**. El rediseño usa únicamente XML, drawables estáticos y animaciones de
  compositor (sin blur, sin Compose).
- Transición: 50th 19–25 ms, 90th 31–32 ms (no 60 fps estables) en Fase 1; Fase 2 garantiza 60 fps en el compositor con snap+fade.
- UI throttling: barras a 15 fps (66 ms), textos a 250 ms en `renderObd`.
- Optimización ADB: `scripts/optimize_stereo.sh` deshabilita bloatware (com.dofun.market, com.dofun.dofunweather.main, com.tw.bootanimation, com.tw.twfileexplore, com.tw.auxin), restaura escalas de animación a 0.5 y compila AOT `cmd package compile -m speed -f`.
- No hay blur en vivo (API 27); frosted glass simulado con color + borde 1 px + gradientes.

## 9. Archivos clave

```
app/src/main/java/com/chronosbvrx/ibizadash/
├─ MainActivity.kt            canvas, foco, HUD, media, clima, OBD UI, quick settings, radio, llamadas, nav pill
├─ App.kt                     osmdroid cache + registro del receptor BT + OfflineMusicCatalog.initialize
├─ MapActivity.kt             mapa a pantalla completa
├─ AppsActivity.kt            cajón de apps
├─ SettingsActivity.kt        ajustes
├─ Prefs.kt                   preferencias
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
├─ nav/NavRepository.kt
├─ nav/NavNotificationListenerService.kt
├─ music/ (OfflineMusicCatalog, TextNormalizer, CoverCache, CatalogModels)
├─ ui/CarSilhouetteView.kt
└─ weather/WeatherClient.kt

Layouts: activity_main.xml, view_cockpit.xml, view_mini_hud.xml, view_media_module.xml,
         view_vehicle_module.xml, view_radio_module.xml, view_call_banner.xml,
         sheet_quick_settings.xml.

Docs: docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md (especificación visual/técnica), docs/IBIZA_LAUNCHER_HANDOFF.md.
Screenshots: screenshots/ (cockpit, nav, media, vehicle, covers, OBD demo/idle, dashboard nativo, fase2, radio, health, call nav).
Volcados MCU/CAN: ../ibiza-can-dump/ y decompiled/.
```

## 10. Bugs y edge cases conocidos

1. **Binder del MCU tras ciclo de contacto**: `TwCommandClient` no hace rebind automático; si el servicio se reinicia, se pierden callbacks hasta reiniciar la app. Añadir `onBindingDied`/reintento.
2. **NotificationListenerService** puede quedar “dead” tras instalar/forzar parada; togglear `cmd notification disallow/allow_listener` o reiniciar la app.
3. **A2DP PLAYING_STATE** alterna 10/11 durante pausa; usar `PlaybackState` AVRCP para el icono (ya implementado).
4. **Portada “Unavailable”**: el primer paquete puede traer álbum “Unavailable”; se ignora y se reintenta con el siguiente.
5. **OBD real sin probar**: el parsing estándar puede necesitar ajustes según el clon ELM327 (timeouts, `ATH1`, `ATSP0`). El B16 emparejado no respondió por SPP; si es BLE, el cliente actual no sirve. Queda guardado como MAC OBD y reintenta cada 5 s: borrarlo con “Buscar automáticamente” en el diálogo OBD si no es el adaptador correcto.
6. **Auto-connect OBD** con `mac=null` busca dispositivos emparejados cada 4 s; si no hay, mantiene un hilo vivo (barato). Desconectar desde el diálogo.
7. **Clima**: solo UI; no hay AIDL de climatizador mapeado (reemplazado por salud térmica).
8. **Layout Vehículo**: con OBD, el footer (VIN/DTC/autonomía) queda debajo del fold; el ScrollView permite verlo.
9. **Overlay blanco del coche** observado al abrir el diálogo OBD con el dashboard nativo en segundo plano: parece una ventana flotante de DashMate; no se reprodujo tras cerrar el diálogo. Investigar si vuelve.
10. **CAN box** no entregó tramas en pruebas; verificar cableado/alimentación/perfil.

## 11. Build, instalación y pruebas

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\Axel Rosete\Desktop\Proyectos\General\IbizaDash
.\gradlew.bat :app:assembleRelease
adb install -r .\app\build\outputs\apk\release\app-release.apk
```

ADB estéreo: `adb connect 10.114.78.182:5555` (IP dinámica; reautorizar tras reboot).
Permisos:
- Ubicación: `pm grant com.chronosbvrx.ibizadash android.permission.ACCESS_FINE_LOCATION`.
- Acceso a notificaciones: Ajustes → Acceso a notificaciones o `cmd notification allow_listener com.chronosbvrx.ibizadash/com.chronosbvrx.ibizadash.media.MediaListenerService` (y NavNotificationListenerService).
- Bluetooth: manifiesto ya declara BLUETOOTH/BLUETOOTH_ADMIN; en API 27 no hay runtime.

Modo demo OBD:
```powershell
adb shell am start -n com.chronosbvrx.ibizadash/.MainActivity --ez obd_demo true
```

Comandos ADB útiles:
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

Volver a LiteDash:
```powershell
adb shell cmd package set-home-activity com.irvingguerra.litedash/.MainActivity
```

## 12. Siguiente trabajo recomendado (roadmap)

1. Probar el ELM327 físico y ajustar timeouts/PIDs; añadir pantalla de emparejamiento dedicada si hace falta.
2. Módulo de radio nativo con el AIDL ya mapeado (tx 11–18) y presets locales (parcialmente implementado).
3. CAN box: verificar cableado/alimentación; si llegan tramas, mapear puertas/luces/TPMS con el log `VehicleData` (type/data).
4. Turn-by-turn: motor propio implementado en el estéreo; queda probarlo en carretera y afinar la guía con la ruta real.
5. Llamadas: mapear tx de aceptar/colgar y banner con botones (implementado).
6. 60 fps: snap + fade o medir en el estéreo; el split animado no llega (implementado en Fase 2).
7. HOME por defecto y firma release real cuando el usuario lo pida.
8. Mapear el AIDL ofuscado de `com.tw.carinfoservice` (`CarServiceAidl`) para RPM/puertas/luces/TPMS/combustible/autonomía/marcha/clima directamente del CAN.
9. Mapas offline: pack z11–z15 listo; falta copiarlo al estéreo/teléfono cuando estén conectados por ADB.
10. Cablear el catálogo musical offline a la UI de media.
11. Diccionario DTC del 2.0 CEKA implementado; ampliar con los códigos exactos que reporte el coche del usuario.

## 13. Contexto de investigación (temp, reutilizable)

- `decompiled/` — fuentes jadx de apps nativas del coche (`cardashboard_classes3`, etc.).
- `../ibiza-can-dump/` — volcados y README del CAN.
- `General/MusicCatalog/` — generador del catálogo musical offline y `push_catalog.ps1`.
- Screenshots en `screenshots/` para validar visualmente cada módulo.

---

**Fin del documento.** La referencia viva es `MainActivity.kt`; la especificación visual/técnica está en `docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md`; el handoff completo en `docs/IBIZA_LAUNCHER_HANDOFF.md`.