# Wi-Fi Direct: único transporte de datos (IbizaDash ↔ IbizaCompanion)

> Estado vigente desde **IbizaDash 0.6.5 / IbizaCompanion 0.5.1** (noviembre 2026).
> El puente Bluetooth RFCOMM se eliminó. Bluetooth queda solo para audio A2DP/AVRCP,
> llamadas HFP y adaptadores ELM327/OBD.

## Por qué

| Dato | Tamaño típico | Wi-Fi Direct |
|---|---:|---|
| Portada de álbum | 15–60 KB | < 50 ms |
| Ruta + maniobras | 5–40 KB | < 30 ms |
| Tesela Esri 256 px | 8–25 KB | < 20 ms |
| Corredor de ruta (z14–z15) | 2–8 MB | 1–3 s |
| APK de actualización | ~1.4 MB | 2–5 s |

El teléfono es la única fuente de Internet y contenido. El estéreo no descarga clima,
carátulas ni teselas; solo pinta lo que recibe o lee de su caché.

## Arquitectura

### Estéreo (`IbizaDash`)

- `nav/WifiNavBridgeServer.kt`
  - Servidor TCP en `0.0.0.0:47800`.
  - Respondedor UDP de descubrimiento en `:47801` (`IBIZADASH_DISCOVER` → `IBIZADASH:47800`).
  - Crea el grupo Wi-Fi Direct como **Group Owner** (`createGroup`) y lo mantiene.
  - Si el chip no soporta Wi-Fi Direct, TCP + UDP siguen funcionando en LAN o hotspot.
- `nav/NavBridgeProtocol.kt`
  - Semántica única de los mensajes.
  - Guarda `TILE_BATCH` en `files/osmdroid/tiles/EsriDark/{z}/{x}/{y}.png` con escritura
    atómica y avisa a `MapTileBridge` para refrescar el `MapView`.
- `nav/NavLink.kt`
  - Salida estéreo → teléfono. Solo Wi-Fi; no hay fallback Bluetooth.
- `weather/WeatherRepository.kt`
  - Recibe `WEATHER` del teléfono. La UI pinta temperatura e icono; no hay cliente HTTP.
- `ota/OtaReceiver.kt` + `ota/OtaInstaller.kt`
  - Reciben el APK por el mismo TCP, verifican tamaño/SHA-256/paquete/firma y abren el
    instalador del sistema.

### Teléfono (`IbizaCompanion`)

- `WifiBridgeClient.kt`
  1. IP manual de LAN/hotspot (`StereoPrefs.wifiHost`).
  2. Grupo Wi-Fi Direct ya formado (el GO es el estéreo).
  3. Descubrimiento UDP en la red Wi-Fi actual.
  4. Descubrimiento/conexión Wi-Fi Direct (auto-conecta si hay un solo peer o si el nombre
     contiene "Ibiza"/"Dash"; si hay varios, la pantalla **Conexión** permite elegir).
- `BridgeRouter.kt` / `BridgeLink.kt`
  - Wi-Fi Direct únicamente.
- `weather/WeatherSender.kt`
  - Consulta Open-Meteo con la ubicación del teléfono y envía `WEATHER` cada ~10 min o
    cuando el coche se mueve más de 2 km.
- `ota/OtaSender.kt`
  - Descarga `IbizaDash.apk` del último release de GitHub
    (`releases/latest/download/IbizaDash.apk`), lee su `versionCode` y lo envía por chunks
    de 32 KB con ACK. Caché local de 6 h con validación condicional.
- `data/TileSender.kt`
  - Toma el corredor de la polilínea, lee teselas del pack offline del teléfono o las
    descarga de Esri y las envía como `TILE_BATCH` (z14 y z15, máx. 420 teselas / 12 MB
    por ruta).

## Protocolo

JSON por línea (`\n`) sobre TCP.

### Handshake

```json
{"type":"HELLO","proto":2,"token":"chronosbvrx-ibiza-6j","client":"IbizaCompanion","transport":"wifi"}
{"type":"WELCOME","proto":2,"transport":"wifi","stereoVersionCode":11,"stereoVersionName":"0.6.5"}
```

El estéreo rechaza cualquier socket TCP que no envíe `HELLO` con el token antes del primer
mensaje. Después envía un `NAV_STATE` completo (con polilínea) al cliente nuevo.

### Mensajes

- `ROUTE_SET`: destino, distancia, duración, polilínea y pasos.
- `NAV_STEP_UPDATE` / `ROUTE_CLEAR`.
- `MEDIA_ART`: portada en base64 (512 px).
- `TILE_BATCH`: `zoom`, `x`, `y`, `pngBase64`.
- `WEATHER`: `tempC`, `code` (WMO), `text`.
- `PHONE_NAV_STATE`: snapshot de navegación calculado en el teléfono.
- `MAP_FRAME` / `MAP_FRAME_STOP`: mapa remoto dibujado en el teléfono.
- `NOTIFY`: avisos del teléfono.
- `PING` / `PONG`: heartbeat cada 15 s.
- `TILE_STATS`: diagnóstico.
- OTA: `APK_META` → `APK_META_ACK`; `APK_CHUNK` → `APK_CHUNK_ACK` / `APK_CHUNK_NACK`;
  `APK_END` → `APK_END_OK` / `OTA_ERROR`. `OTA_ALREADY_NEWER` evita reinstalar.

## Permisos

Estéreo:

```xml
INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, NEARBY_WIFI_DEVICES (API 33+),
BLUETOOTH/BLUETOOTH_ADMIN (≤ API 30) y BLUETOOTH_CONNECT para el estado del teléfono,
REQUEST_INSTALL_PACKAGES para la OTA
```

Teléfono:

```xml
INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE,
ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION,
NEARBY_WIFI_DEVICES (API 33+), POST_NOTIFICATIONS
```

No se piden permisos Bluetooth en la compañera: no usa Bluetooth para datos.

## Cómo probar

1. Instala ambos APK (el estéreo debe tener Wi-Fi encendido; la app lo pide una vez si está
   apagado).
2. Abre IbizaCompanion → el servicio arranca y busca el estéreo.
3. En **Conexión** debe aparecer `Wi-Fi Direct · 192.168.49.1` (o la IP del hotspot/LAN).
   El estado también se ve en Ajustes rápidos del estéreo, fila **WiFi y punto de acceso**.
4. Reproduce música desde el teléfono: la portada debe llegar al instante. Envía una ruta:
   se dibuja y empieza el lote de teselas.
5. OTA: abre **Conexión**, pulsa **Buscar actualización** y luego **Actualizar estéreo**.
   En el estéreo, Ajustes rápidos → Actualización del sistema → toca para instalar.
6. Logs:

```powershell
adb logcat -s WifiNavBridge WifiBridge NavBridge TileSender OtaSender OtaReceiver WeatherSender
```

## Diagnóstico rápido

- **"Wi-Fi Direct conectado" pero sin datos**: revisa que el estéreo tenga la app abierta y
  el puerto 47800 libre; el watchdog reconecta cada 15 s.
- **La OTA dice "firma distinta"**: el APK de GitHub no está firmado con la keystore fija.
  Revisa `local.properties` y vuelve a compilar.
- **La OTA dice "paquete ajeno"**: el release apunta a otro APK; el asset debe llamarse
  `IbizaDash.apk`.
- **Sin clima**: el teléfono necesita permiso de ubicación y conexión a Internet; el estéreo
  solo muestra el último `WEATHER` recibido.
