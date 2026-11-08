> **Documento histórico (2026-11-05/07).** La arquitectura vigente usa Wi-Fi Direct como
> único canal de datos y OTA desde el teléfono. Ver `README.md` y
> `docs/WIFI_DIRECT_TRANSPORT.md`.

# IbizaCompanion + IbizaDash — Reporte técnico completo (Phone-as-Brain)

> Documento para análisis externo. Cubre la app compañera del teléfono (`IbizaCompanion`) y cómo
> el estéreo (`IbizaDash`) recibe y procesa sus datos. Incluye arquitectura, interfaz, vistas,
> elementos, flujo de datos, protocolo Bluetooth, permisos, limitaciones y propuestas.

---

## 1. Visión general

**IbizaCompanion** es una app Android para el teléfono del usuario (POCO F7) que actúa como
"segundo cerebro" del estéreo IbizaDash (`sp7731e`, Android 8.1). El estéreo tiene CPU limitada
(Cortex-A7, 2 GB RAM) y no conviene que haga cálculos pesados (routing, mapas, portadas). El teléfono
hace todo el trabajo pesado y envía datos ya procesados al estéreo **exclusivamente por Bluetooth
RFCOMM**, de modo que el estéreo solo pinta líneas, muestra maniobras y reproduce portadas.

**Dos canales de datos:**
1. **Navegación ligera** (`ROUTE_SET`, `NAV_STEP_UPDATE`, `ROUTE_CLEAR`, `TILE_BATCH`): el teléfono
   calcula la ruta con OSRM y envía la polilínea + maniobras + teselas del mapa.
2. **Portadas de música** (`MEDIA_ART`): el teléfono lee la portada del track actual (Spotify, YouTube
   Music, etc.) y la envía al estéreo para que la muestre sin depender de iTunes/red.

---

## 2. Hardware y entorno

### Estéreo (IbizaDash)
- SoC Spreadtrum SC7731E, 4× Cortex-A7, 32-bit, Android 8.1 (API 27), 1280×720 @160 dpi, 2 GB RAM.
- Paquete: `com.chronosbvrx.ibizadash`, versionName `0.1.0`.
- Bluetooth: servidor RFCOMM (UUID dedicado `00001101-c0de-4b56-5258-4942495a4101`).

### Teléfono (IbizaCompanion)
- POCO F7 (Android 14+), paquete `com.chronosbvrx.ibizacompanion`, versionName `0.1.0`.
- minSdk 26, targetSdk 34, compileSdk 34.
- Kotlin 1.9.24, AndroidX, Material, osmdroid 6.1.18, OkHttp 4.12.0, kotlinx-coroutines 1.8.1.

---

## 3. Arquitectura de IbizaCompanion

### 3.1 Componentes

| Componente | Archivo | Rol |
|---|---|---|
| `IbizaCompanionApp` | `IbizaCompanionApp.kt` | Application; arranca `MediaBridgeService` en `onCreate()`. |
| `MainActivity` | `MainActivity.kt` | UI principal: conexión BT, permisos, now playing, envío de rutas. |
| `MediaBridgeService` | `service/MediaBridgeService.kt` | Foreground service persistente. Mantiene conexión BT y hace polling de media cada 2 s. |
| `MediaNotificationListener` | `service/MediaNotificationListener.kt` | `NotificationListenerService` (debe activarse manualmente). Requerido para `MediaSessionManager.getActiveSessions()`. |
| `BluetoothNavClient` | `BluetoothNavClient.kt` | Cliente RFCOMM: busca estéreo, conecta, envía JSON line-delimited. |
| `AlbumArtReader` | `AlbumArtReader.kt` | Lee metadata y artwork de `MediaController`. |
| `OsrmClient` | `OsrmClient.kt` | Calcula rutas con OSRM (servidor público). |

### 3.2 Servicio en segundo plano
- `MediaBridgeService` es un `Service` con `startForeground()` (notificación persistente, canal "Ibiza Bridge", importancia LOW).
- `foregroundServiceType = mediaPlayback | dataSync` (Android 14+ requiere declararlo).
- `onStartCommand` devuelve `START_STICKY` → si el sistema mata el proceso, se reinicia.
- Coroutine en `Dispatchers.IO` hace polling cada 2 s (`pollMedia()`).
- Conexión BT con reconexión automática cada 5 s si falla.
- El servicio se arranca desde `IbizaCompanionApp.onCreate()` y desde el botón "Iniciar servicio".

### 3.3 Detección de media (Spotify, YouTube Music, etc.)
- Usa `MediaSessionManager.getActiveSessions(ComponentName)` con el `MediaNotificationListener` habilitado.
- Toma el primer controller activo (`controllers.firstOrNull()`).
- Lee `MediaMetadata`: `METADATA_KEY_TITLE`, `ARTIST`, `ALBUM`.
- Lee artwork: `METADATA_KEY_ALBUM_ART` o `METADATA_KEY_ART` (Bitmap).
- Calcula una clave de track (`"$title|$artist|$album"`); si cambió, envía `MEDIA_ART`.
- El servicio envía solo si hay artwork (base64 no vacío).

### 3.4 Lectura de portadas
- `AlbumArtReader.readArtworkBitmap(controller)` devuelve el Bitmap.
- `bitmapToBase64(bmp, maxSize=256)` escala a 256 px y comprime JPEG calidad 70.
- El resultado es un string base64 sin saltos de línea (`Base64.NO_WRAP`).

### 3.5 Conexión Bluetooth
- `findStereo()`: busca en dispositivos emparejados uno cuyo nombre contenga "Ibiza" o "Dash"; si no, toma el primero emparejado.
- `connect(device)`: `createInsecureRfcommSocketToServiceRecord(NAV_BRIDGE_UUID)` + `connect()`.
- Permisos runtime: `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (Android 12+), `POST_NOTIFICATIONS` (Android 13+).

### 3.6 Cálculo de rutas
- `OsrmClient.route(fromLat, fromLon, toLat, toLon)` llama al servidor público OSRM:
  `https://router.project-osrm.org/route/v1/driving/{lon,lat;...}?overview=full&geometries=polyline&steps=true&language=es`
- Parsea `routes[0].geometry` (polilínea codificada), `distance`, `duration`.
- Envía `ROUTE_SET` con destinationName, totalDistanceMeters, totalDurationSeconds, encodedPolyline, steps (vacío en esta versión).

---

## 4. Interfaz completa de IbizaCompanion

### 4.1 Tema y estilo
- Tema: `Theme.IbizaCompanion` (parent `Theme.AppCompat.NoActionBar`), fondo `#0B0F17` (obsidian).
- Status bar y navigation bar transparentes, modo fullscreen inmersivo (`SYSTEM_UI_FLAG_IMMERSIVE_STICKY`).
- Header con gradiente lineal (135°, `#1A2A4A` → `#0B0F17`).
- Tarjetas: `bg_glass_panel` (fondo `#CC0F1622`, esquinas 16 dp, borde 1 dp `#1FFFFFFF`).
- Botones: `AccentButton` (fondo cyan `#38BDF8`, texto oscuro) y `GhostButton` (fondo card, texto blanco).
- Tipografía: sans-serif-medium para títulos, sans-serif para valores.

### 4.2 Layout (`activity_main.xml`) — secciones

**Header**
- `TextView` "IBIZA COMPANION" (20 sp, medium, letterSpacing 0.05).
- `TextView` statusPill (32 dp alto, fondo card, texto "Desconectado" / "Conectado").
- Subtítulo: "Puente Bluetooth con el estéreo" (12 sp, muted).

**Tarjeta CONEXIÓN**
- `TextView` connStatus: "Sin estéreo" / "Conectado a {nombre}".
- Botón `btnConnect` ("Conectar", AccentButton).
- Botón `btnStartService` ("Iniciar servicio", GhostButton).

**Tarjeta PERMISOS**
- Botón `btnNotifAccess` ("Habilitar acceso a notificaciones", GhostButton).

**Tarjeta NOW PLAYING**
- `ImageView` albumArt (72×72 dp, centerCrop).
- `TextView` npTitle (15 sp, primary, maxLines 1).
- `TextView` npArtist (13 sp, secondary).
- `TextView` npAlbum (12 sp, muted).

**Tarjeta RUTA**
- `EditText` destInput (hint "Destino: lat,lon", fondo card).
- Botón `btnSendRoute` ("Enviar ruta al Ibiza", AccentButton).

**Footer**
- "IbizaCompanion · puente Bluetooth" (11 sp, faint, centrado).

### 4.3 Elementos interactivos
| ID | Tipo | Acción |
|---|---|---|
| `btnConnect` | Button | Llama `connectBt()`: busca y conecta al estéreo. |
| `btnStartService` | Button | Arranca `MediaBridgeService` en foreground. |
| `btnNotifAccess` | Button | Abre `ACTION_NOTIFICATION_LISTENER_SETTINGS`. |
| `btnSendRoute` | Button | Parsea "lat,lon", llama OSRM, envía `ROUTE_SET`. |
| `statusPill` | TextView | Estado de conexión BT. |
| `connStatus` | TextView | Detalle de conexión. |
| `albumArt` | ImageView | Portada del track actual. |
| `npTitle/npArtist/npAlbum` | TextView | Metadata del track actual. |
| `destInput` | EditText | Coordenadas de destino. |

---

## 5. Protocolo Bluetooth (estéreo ↔ teléfono)

**Transporte:** RFCOMM insecure, UUID dedicado `00001101-c0de-4b56-5258-4942495a4101`.
**Formato:** JSON line-delimited (un objeto JSON por línea, terminado en `\n`).
**Dirección:** El estéreo es **servidor** (`listenUsingInsecureRfcommWithServiceRecord`); el teléfono es **cliente**.

### 5.1 Mensajes del teléfono → estéreo

**ROUTE_SET** (ruta completa)
```json
{
  "type": "ROUTE_SET",
  "destinationName": "Destino",
  "totalDistanceMeters": 8450,
  "totalDurationSeconds": 1140,
  "encodedPolyline": "}_ilF~_~uO...",
  "steps": []
}
```

**NAV_STEP_UPDATE** (actualización en tiempo real)
```json
{
  "type": "NAV_STEP_UPDATE",
  "nextInstruction": "Gira a la izquierda en 200 m",
  "maneuver": "TURN_LEFT",
  "distanceToTurnMeters": 200,
  "remainingDistanceMeters": 4200,
  "etaText": "14:35 (8 min)"
}
```

**ROUTE_CLEAR**
```json
{"type": "ROUTE_CLEAR"}
```

**TILE_BATCH** (tesela de mapa del corredor)
```json
{
  "type": "TILE_BATCH",
  "zoom": 15,
  "x": 7182,
  "y": 14520,
  "pngBase64": "iVBORw0KGgoAAAANS..."
}
```

**MEDIA_ART** (portada del track actual)
```json
{
  "type": "MEDIA_ART",
  "title": "Todo cambia",
  "artist": "Saturno",
  "album": "Dedos Rotos y Funerales",
  "pngBase64": "/9j/4AAQSkZJRgABAQ..."
}
```

### 5.2 Procesamiento en el estéreo (`BtNavBridgeServer.kt`)
- Servidor RFCOMM en hilo IO, se reinicia automáticamente al desconectarse.
- Lee líneas con `BufferedReader.readLine()`.
- Parsea con `org.json.JSONObject`.
- `ROUTE_SET`: crea `NavRoute`, publica en `NavRouteRepository`, dibuja polilínea cian en `MapView`, centra mapa, cambia foco a `NAV`, inicia `RouteNavigator`.
- `NAV_STEP_UPDATE`: publica en `NavRepository` (píldora de navegación).
- `ROUTE_CLEAR`: limpia ruta y repositorio.
- `TILE_BATCH`: decodifica base64 y guarda en `/sdcard/Android/data/.../osmdroid/tiles/EsriDark/{z}/{x}/{y}.png`.
- `MEDIA_ART`: decodifica base64 a `Bitmap` y llama `MediaRepository.updateArtwork(bmp)`.

---

## 6. Flujo de datos completo (portada de música)

```
[Spotify/YouTube Music] → MediaSession (Android)
        ↓
[MediaBridgeService.pollMedia()] cada 2s
        ↓ lee MediaMetadata + artwork
[AlbumArtReader.bitmapToBase64()] → base64
        ↓
[BluetoothNavClient.sendMediaArt()] → JSON line
        ↓ RFCOMM
[BtNavBridgeServer.handleClient()] → "MEDIA_ART"
        ↓ Base64.decode + BitmapFactory.decodeByteArray
[MediaRepository.updateArtwork(bmp)]
        ↓ notifyListeners
[MainActivity render] → albumArt ImageView actualizado
```

---

## 7. Flujo de datos completo (navegación)

```
[Usuario ingresa lat,lon en IbizaCompanion]
        ↓
[OsrmClient.route()] → OSRM público
        ↓ geometry (polilínea) + distance + duration
[BluetoothNavClient.sendRouteSet()] → JSON
        ↓ RFCOMM
[BtNavBridgeServer] → NavRouteRepository.set(route)
        ↓
[MainActivity.drawNavRoute()] → Polyline cian en MapView + marcador
        ↓
[RouteNavigator.start()] → GPS local avanza instrucciones si el teléfono se duerme
```

---

## 8. Permisos requeridos

### IbizaCompanion (teléfono)
| Permiso | Motivo |
|---|---|
| `INTERNET` | OSRM + Nominatim |
| `ACCESS_FINE_LOCATION` / `COARSE` | GPS para rutas |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | Conexión clásica |
| `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` | Android 12+ |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC` | Servicio persistente |
| `POST_NOTIFICATIONS` | Android 13+ |
| `WAKE_LOCK` | Mantener CPU activa |
| Acceso a notificaciones (manual) | Requerido para `MediaSessionManager.getActiveSessions()` |

### IbizaDash (estéreo)
- `BLUETOOTH` (maxSdk 30), `BLUETOOTH_CONNECT`.
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- `READ_PHONE_STATE` (llamadas).
- Acceso a notificaciones para `MediaListenerService` y `NavNotificationListenerService`.

---

## 9. Limitaciones conocidas

1. **`findStereo()`** toma el primer dispositivo emparejado si no encuentra "Ibiza"/"Dash" en el nombre. Si hay varios dispositivos emparejados, puede elegir el incorrecto.
2. **OSRM público** puede tener rate limits o latencia; para producción conviene self-hosted.
3. **Pasos (steps)** se envían vacíos en la versión actual; el fallback autónomo del estéreo no tiene maniobras detalladas.
4. **TILE_BATCH** no se envía automáticamente desde la compañera todavía (solo el estéreo soporta recibirlo).
5. **Escalado de portada** fijo a 256 px / JPEG 70; puede perder calidad en pantallas grandes.
6. **MediaSessionManager** requiere que el usuario habilite manualmente el acceso a notificaciones; sin eso no hay detección.
7. **Servicio en segundo plano** puede ser matado por el OEM (POCO/HyperOS agresivo con battery saver); se recomienda desactivar optimización de batería para la app.
8. **No hay reconexión BT automática** si el estéreo se apaga y enciende (el servicio reintenta cada 5 s, pero no re-empareja).
9. **No hay cifrado** en el canal RFCOMM (insecure socket).
10. **No hay autenticación** entre teléfono y estéreo (cualquier dispositivo con el UUID puede conectarse).

---

## 10. Propuestas de mejora (para otra IA)

### A. Robustez de conexión
- Implementar **reconexión automática con backoff exponencial** en `BluetoothNavClient` (igual que el estéreo ya hace con el MCU).
- **Heartbeat** periódico (cada 5 s) para detectar desconexión rápida.
- **Emparejamiento por UUID de servicio** en lugar de por nombre; registrar el servicio SDP con nombre fijo y buscar por UUID.
- **Autenticación simple**: enviar un token compartido (PIN) en el primer mensaje; el estéreo rechaza clientes no autenticados.

### B. Detección de media más robusta
- Implementar `MediaSessionManager.OnActiveSessionsChangedListener` en lugar de polling cada 2 s (menos batería, respuesta inmediata).
- **Filtrar por paquete**: priorizar Spotify, YouTube Music, etc. y ignorar sesiones del sistema (notificaciones, etc.).
- **Enviar metadata completa** (título, artista, álbum, duración, posición) en `MEDIA_ART` para que el estéreo muestre progreso.
- **Caché de portadas** en el estéreo (LRU en memoria + disco) para no reenviar la misma portada.
- **Enviar solo si cambió** (ya implementado con `lastTrackKey`, pero ampliar a hash de la imagen).

### C. Navegación
- **Enviar los `steps` completos** en `ROUTE_SET` (maneuvers, distancias) para que el fallback autónomo del estéreo funcione.
- **Enviar `NAV_STEP_UPDATE` periódicamente** desde el teléfono usando su GPS (más preciso que el GPS del estéreo).
- **Descargar y enviar teselas** (`TILE_BATCH`) del corredor de la ruta automáticamente.
- **Modo offline**: cachear la última ruta en el teléfono y reenviarla si se pierde la conexión.

### D. UI/UX de la compañera
- **Pantalla de mapa** con búsqueda de destino (Nominatim/Photon) y selección en mapa (osmdroid).
- **Historial de destinos** recientes.
- **Indicador de estado** en tiempo real (conectado, reproduciendo, ruta activa).
- **Notificación persistente** con controles (play/pause, siguiente) que controlan la sesión del teléfono.
- **Tema oscuro** ya aplicado; mejorar con animaciones de transición y feedback háptico.

### E. Rendimiento y batería
- **Reducir polling** a 5 s cuando no hay música reproduciéndose.
- **Usar `JobScheduler`/`WorkManager`** para tareas diferibles (envío de teselas).
- **Comprimir imágenes** con WebP en lugar de JPEG para menor tamaño.
- **Diferir envío de portada** si el estéreo está en segundo plano (no visible).

### F. Seguridad
- **Emparejamiento por PIN** compartido.
- **Cifrado** del canal (usar `listenUsingRfcommWithServiceRecord` con SSL si es posible, o cifrar payload con AES).
- **Lista blanca de dispositivos** en el estéreo (solo aceptar conexiones de MACs autorizadas).

### G. Integración más profunda
- **Control bidireccional**: el estéreo envía comandos al teléfono (play/pause/skip) por el mismo canal.
- **Sincronización de volumen** y estado de llamada.
- **Compartir ubicación en tiempo real** (el teléfono envía su GPS al estéreo para que el mapa siga al coche).

---

## 10. Archivos clave (companion app)

```
companion-app/
├── app/build.gradle.kts
├── app/src/main/AndroidManifest.xml
├── app/src/main/java/com/chronosbvrx/ibizacompanion/
│   ├── IbizaCompanionApp.kt          # Arranca el servicio
│   ├── MainActivity.kt              # UI + conexión + rutas + now playing
│   ├── BluetoothNavClient.kt        # Cliente RFCOMM
│   ├── AlbumArtReader.kt            # Lectura de metadata/artwork
│   ├── OsrmClient.kt                # Cálculo de rutas
│   └── service/
│       ├── MediaBridgeService.kt    # Foreground service + polling
│       └── MediaNotificationListener.kt  # NotificationListenerService
├── app/src/main/res/
│   ├── layout/activity_main.xml     # UI automotive
│   ├── values/colors.xml
│   ├── values/styles.xml
│   └── drawable/bg_glass_panel.xml, bg_gradient_top.xml
```

### Archivos clave (estéreo, lado receptor)
```
IbizaDash/app/src/main/java/com/chronosbvrx/ibizadash/
├── nav/BtNavBridgeServer.kt         # Servidor RFCOMM + parseo de mensajes
├── nav/NavRouteRepository.kt        # Estado de la ruta actual
├── nav/RouteNavigator.kt            # Fallback autónomo por GPS
├── media/MediaRepository.kt         # updateArtwork()
└── map/MapHelper.kt                 # decodePolyline()
```

---

## 11. Capturas y pruebas

- `screenshots/ibizacomp_ui.png`: UI de IbizaCompanion en el POCO F7 (tema automotive, fullscreen).
- `screenshots/emu_fase3.png`: IbizaDash en emulador con panel "Asistente del Ibiza".

**Pruebas realizadas:**
- Instalación en POCO F7 vía ADB Wi-Fi (éxito).
- App abre sin crashes.
- Servicio en segundo plano arranca.
- Conexión RFCOMM al estéreo (pendiente de prueba con estéreo real encendido).

---

## 12. Resumen ejecutivo

IbizaCompanion es una app Android persistente (foreground service) que:
1. Detecta lo que reproduce el teléfono (Spotify, YouTube Music, etc.) vía `MediaSessionManager`.
2. Lee la portada del track actual.
3. La envía al estéreo IbizaDash por Bluetooth RFCOMM (`MEDIA_ART`).
4. Calcula rutas con OSRM y las envía (`ROUTE_SET`) para que el estéreo las pinte.

El estéreo recibe los datos en `BtNavBridgeServer`, decodifica base64 a `Bitmap`, actualiza `MediaRepository` y pinta la portada en la UI. Para navegación, decodifica la polilínea y la dibuja en el `MapView` único.

**Principal gap**: los `steps` de la ruta se envían vacíos, el envío de teselas no está automatizado, y la conexión no tiene autenticación ni cifrado. Las propuestas de la sección 10 abordan estos puntos.
