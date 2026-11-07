# Wi-Fi Direct como transporte principal (IbizaDash ↔ IbizaCompanion)

> Sustituye el RFCOMM de datos por Wi-Fi Direct/TCP para portadas, rutas y
> teselas de mapa. Bluetooth sigue disponible como respaldo automático y para
> lo que no se puede mover a Wi-Fi: audio A2DP, AVRCP, llamadas y ELM327/OBD.

## Por qué

| Dato | Tamaño típico | RFCOMM | Wi-Fi Direct |
|---|---:|---|---|
| Portada de álbum | 15–60 KB | 0.5–3 s, bloquea el polling | < 50 ms |
| Ruta + maniobras | 5–40 KB | 0.2–1 s | < 30 ms |
| Tesela Esri 256 px | 8–25 KB | 1–2 s cada una | < 20 ms |
| Corredor de ruta (z14–z15) | 2–8 MB | inviable | 1–3 s |

Bluetooth RFCOMM en este estéreo se queda en decenas de KB/s y comparte radio
con el audio A2DP. Wi-Fi Direct da ancho de banda suficiente y permite que el
teléfono haga lo pesado sin congelar la reproducción.

## Arquitectura

### Estéreo (`IbizaDash`)

- `nav/WifiNavBridgeServer.kt`
  - Servidor TCP en `0.0.0.0:47800`.
  - Respondedor UDP de descubrimiento en `:47801` (`IBIZADASH_DISCOVER` →
    `IBIZADASH:47800`).
  - Crea el grupo Wi-Fi Direct como **Group Owner** (`createGroup`) y lo
    mantiene; si el chip no lo soporta, siguen funcionando TCP + UDP en LAN o
    hotspot.
- `nav/NavBridgeProtocol.kt`
  - Semántica única de los mensajes (la misma que usaba RFCOMM).
  - Guarda `TILE_BATCH` en `files/osmdroid/tiles/EsriDark/{z}/{x}/{y}.png` con
    escritura atómica y avisa a `MapTileBridge` para refrescar el `MapView`.
- `nav/NavLink.kt`
  - Elige Wi-Fi si hay un cliente conectado; si la escritura falla, cae a
    Bluetooth en el mismo envío.
- `nav/BtNavBridgeServer.kt`
  - Sigue escuchando RFCOMM con el UUID de siempre. Ya no parsea mensajes:
    delega en `NavBridgeProtocol`.

### Teléfono (`IbizaCompanion`)

- `WifiBridgeClient.kt`
  1. IP manual de LAN/hotspot (`StereoPrefs.wifiHost`).
  2. Grupo Wi-Fi Direct ya formado (el GO es el estéreo).
  3. Descubrimiento UDP en la red Wi-Fi actual.
  4. Descubrimiento/conexión Wi-Fi Direct (auto-conecta si hay un solo peer o
     si el nombre contiene "Ibiza"/"Dash"; si hay varios, la pantalla
     **Conexión** permite elegir).
- `BridgeRouter.kt` / `BridgeLink.kt`
  - Wi-Fi primero, Bluetooth después. El servicio mantiene ambos canales.
  - La UI (`NavigationScreen`) usa `BridgeLink` para enviar rutas y teselas.
- `data/TileSender.kt`
  - Toma el corredor de la polilínea, lee teselas del pack offline del teléfono
    o las descarga de Esri y las envía como `TILE_BATCH` (z14 y z15, máx. 420
    teselas / 12 MB por ruta).
  - Se lanza automáticamente al enviar una ruta si el enlace es Wi-Fi; también
    hay un botón manual en la hoja de ruta.

## Protocolo

JSON por línea (`\n`) sobre TCP o RFCOMM.

### Handshake TCP

```json
{"type":"HELLO","proto":2,"token":"chronosbvrx-ibiza-6j","client":"IbizaCompanion","transport":"wifi"}
{"type":"WELCOME","proto":2,"transport":"wifi"}
```

El estéreo rechaza cualquier socket TCP que no envíe `HELLO` con el token
antes del primer mensaje. Después envía un `NAV_STATE` completo (con polilínea)
al cliente nuevo.

### Mensajes (sin cambios de forma)

- `ROUTE_SET` — destino, distancia, duración, polilínea y pasos.
- `NAV_STEP_UPDATE` / `ROUTE_CLEAR`.
- `MEDIA_ART` — portada en base64 (512 px por Wi-Fi, 256 px por Bluetooth).
- `TILE_BATCH` — `zoom`, `x`, `y`, `pngBase64`.
- `PING`/`PONG` — heartbeat cada 15 s.
- `TILE_STATS` — solo diagnóstico (cuántas teselas llegaron).

## Permisos

Estéreo:

```xml
ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, ACCESS_FINE_LOCATION,
NEARBY_WIFI_DEVICES (API 33+), INTERNET
```

Teléfono:

```xml
ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, ACCESS_NETWORK_STATE,
ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES (API 33+), INTERNET
```

## Cómo probar

1. Instalar ambos APK (el estéreo debe tener Wi-Fi encendido; la app lo pide
   una vez si está apagado).
2. Abrir IbizaCompanion → el servicio arranca y busca el estéreo.
3. En **Conexión** debe aparecer `Wi-Fi Direct · 192.168.49.1` (o la IP del
   hotspot/LAN). El estado también se ve en Ajustes rápidos del estéreo, fila
   **WiFi y punto de acceso**.
4. En el estéreo, reproducir música desde el teléfono: la portada debe llegar
   al instante. En el teléfono, enviar una ruta: se dibuja y, si hay Wi-Fi,
   empieza el lote de teselas (progreso en la hoja).
5. Logs:

```powershell
adb logcat -s WifiNavBridge WifiBridge NavBridge TileSender BtNavBridge
```

## Validación en emulador (7 nov 2026)

- AVD Android 36 (1280×720); estéreo con APK **release** y teléfono con APK debug.
- `HELLO`/`WELCOME`, `PING`/`PONG`, `ROUTE_SET` (Palo Alto, 10.7 km, 16 pasos)
  y `TILE_STATS` confirmados en los logs de ambos lados.
- 67 mensajes `TILE_BATCH` → **55 teselas únicas** (~983 KB) escritas en
  `files/osmdroid/tiles/EsriDark/{14,15}/…` del estéreo.
- `MEDIA_ART` de 64×64 decodificada y aplicada al reproductor.
- Capturas: `screenshots/wifi_connection.png`, `screenshots/wifi_quick.png`,
  `screenshots/wifi_stereo_route.png`, `screenshots/wifi_dashboard.png`.
- **No se pudo probar el P2P real en el emulador** (no expone Wi-Fi Direct); el
  emulador sólo valida TCP/UDP, handshake y protocolo. Falta confirmar
  `createGroup`/`connect` en el estéreo sp7731e y el POCO. Si el chip no soporta
  P2P, el modo hotspot/LAN con IP manual es el respaldo.

## Diagnóstico

| Síntoma | Causa probable / solución |
|---|---|
| "Wi-Fi Direct no disponible" en el estéreo | El chip/ROM no expone P2P. Usar hotspot/LAN: conectar ambos a la misma red y poner la IP del estéreo en **Conexión**. |
| "Wi-Fi apagado" | Encender Wi-Fi en el estéreo; la app intenta hacerlo una vez. |
| El teléfono se queda en "buscando estéreo" | El estéreo aún no creó el grupo (esperar 15–30 s) o hay varios peers; elegir el estéreo en la lista. |
| Conecta y se cae al segundo | El estéreo perdió el grupo P2P; revisar que la app siga abierta y Wi-Fi encendido. |
| Audio A2DP entrecortado | Wi-Fi 2.4 GHz y Bluetooth comparten radio. Usar hotspot 5 GHz o desactivar Wi-Fi Direct y quedarse en Bluetooth. |
| No llegan teselas | El enlace es Bluetooth (solo se envían por Wi-Fi) o el teléfono no tiene internet ni pack offline. |
| El estéreo muestra "sin pack" | Es normal: el estado de Ajustes rápidos cuenta `.mbtiles`; las teselas nuevas son PNG sueltos en la caché de osmdroid. |

## Límites conocidos

- **Group Owner**: el diseño espera que el estéreo sea GO. Si el teléfono acaba
  como GO (negociación del ROM), no hay servidor TCP en el teléfono y se usa
  LAN como respaldo. Se puede forzar el orden reinstalando/creando el grupo
  desde el estéreo.
- **Mapa completo regional**: el envío automático cubre el corredor de la ruta
  (z14–z15). Los paquetes `.mbtiles` completos siguen yendo por ADB
  (`scripts/deploy_offline.ps1`); moverlos por Wi-Fi requeriría un protocolo de
  archivo grande aparte.
- **Seguridad**: el token del handshake evita inyecciones accidentales, pero no
  es criptografía de extremo a extremo. La privacidad real la da el grupo
  Wi-Fi Direct (WPA2) o el hotspot.
- **Coexistencia**: en chips 2.4 GHz baratos, P2P + A2DP pueden interferir.
  Es la principal razón para mantener Bluetooth como fallback.
