# Fase 1 + Fase 2 — el teléfono como cerebro

> Objetivo: el teléfono calcula navegación, medios y notificaciones, y renderiza
> el mapa; el estéreo solo pinta y captura órdenes. La Fase 3 (proyección H.264
> tipo Android Auto) queda fuera de este alcance.

## Fase 1 — navegación y media en el teléfono

### Navegación

- `nav/PhoneNavigator.kt` (compañera): mismo algoritmo que tenía el estéreo
  (proyección sobre la polilínea, próximo giro, restante/ETA, llegada y salida
  de ruta), alimentado por el GPS del teléfono a 1 Hz.
- `nav/PhoneNavController.kt`: mantiene el navegador, publica
  `PHONE_NAV_STATE` y recalcula con OSRM si el auto se sale de la ruta.
- El estado se reenvía cada ~2.5 s aunque no haya fixes nuevos, para que un
  socket reconectado o un GPS quieto no congelen el HUD.
- El servicio se promueve a **foreground type `location`** al iniciar una ruta:
  el GPS sigue funcionando con la pantalla apagada o la app en segundo plano.
- Estéreo (`nav/PhoneNavBridge.kt`):
  - Recibe `PHONE_NAV_STATE`, publica `NavState` y `NavRepository`.
  - Detiene el `RouteNavigator` local mientras el teléfono manda.
  - Si pasan **10 s sin estados**, vuelve a la guía local con el GPS del estéreo.
  - `NavStateRelay` no devuelve el estado al teléfono (evita eco).

### Media

- Los botones del estéreo envían `MEDIA_CMD` (`play`, `pause`, `next`, `prev`)
  por Wi-Fi (o Bluetooth de respaldo) en un hilo de red.
- `MediaBridgeService` ejecuta el comando sobre la `MediaSession` activa del
  teléfono; funciona con Spotify, YouTube Music, etc.
- `MEDIA_ART` ahora incluye `title`, `artist`, `album` e `isPlaying`, y el
  estéreo actualiza metadatos aunque AVRCP llegue vacío.

### Notificaciones

- `MediaNotificationListener` reenvía avisos como `NOTIFY` (app, título, texto).
- Filtra notificaciones propias, de media, del sistema, ongoing y group summary;
  limita a una cada 2.5 s.
- El estéreo los muestra en la cápsula de avisos (`Shell · Prueba · Mensaje`).

## Fase 2 — mapa renderizado en el teléfono

- `map/MapFrameSender.kt` (compañera):
  - Captura la WebView Leaflet visible, recorta una ventana **1024×720**
    centrada (proporción del panel del estéreo) y la comprime en **JPEG 80**.
  - Envía `MAP_FRAME` a **2 fps** solo si el enlace Wi-Fi está activo.
  - `MAP_FRAME_STOP` al salir de la pantalla.
- `nav/RemoteMapBridge.kt` (estéreo):
  - Decodifica el JPEG en un hilo propio (`RGB_565`) y lo pinta en `remoteMap`.
  - Oculta y pausa el `MapView` de osmdroid mientras hay frames.
  - Si pasan **5 s sin frames**, vuelve al mapa nativo automáticamente.
- El HUD, el banner de maniobra, la velocidad y el dock siguen siendo nativos.

### Requisitos y límites de la Fase 2

- La pantalla **Rutas** de IbizaCompanion debe estar abierta y visible: la
  captura es de su WebView (las capas Compose de búsqueda no se incluyen).
- Con la pantalla del teléfono apagada o la app en segundo plano, la WebView
  deja de refrescar; el estéreo vuelve al mapa nativo. La navegación y los
  comandos siguen por Fase 1.
- El frame es una imagen: no hay toque/pan desde el estéreo en esta versión.
  El mapa sigue al coche con el GPS del teléfono.
- Consumo: ~30–45 KB por frame, ~0.5–0.7 Mbps a 2 fps; el estéreo solo decodifica
  un JPEG y osmdroid deja de consumir CPU.

## Validación en emulador (7 nov 2026)

- AVD Android 36, 1280×720; estéreo debug/release y compañera debug.
- Navegación del teléfono: estado inicial `DEPART … 5.3 km` y, tras mover el GPS,
  `RIGHT … 5.2 km`; el estéreo recibió ambos `PHONE_NAV_STATE` y mostró el banner.
- FGS `location`: los estados siguieron llegando con la app del teléfono en
  segundo plano (estéreo en primer plano).
- Mapa remoto: frames de **32.756 B**, `RemoteMap: primer frame del teléfono`,
  mapa Leaflet y marcador visibles en el panel izquierdo; osmdroid oculto.
- Media: el estéreo envió `MEDIA_CMD` por Wi-Fi y la compañera lo recibió
  (`MEDIA_CMD play`).
- Notificaciones: `Shell · Prueba · Mensaje` visible en la cápsula del estéreo.
- Teselas del corredor: 54/54 (`TILE_STATS`).

## Notas de prueba

- En el emulador Android 36 hay que desactivar el **cached app freezer** para
  que el proceso del estéreo siga leyendo el socket mientras la compañera está
  en primer plano:
  ```powershell
  adb shell settings put global cached_apps_freezer disabled
  ```
  En el estéreo real (Android 8.1) no existe ese freezer.
- La captura de la WebView puede variar según el ROM; si un teléfono devuelve
  frames negros, habría que pasar a `PixelCopy` o a una WebView de captura
  dedicada con `LAYER_TYPE_SOFTWARE`.
- El audio sigue por A2DP/HFP; no se envía audio por Wi-Fi.
