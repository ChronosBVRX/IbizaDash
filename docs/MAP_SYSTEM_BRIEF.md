# IbizaDash — Briefing técnico del sistema de mapas y navegación

> Documento escrito para que **otra IA** analice la arquitectura y proponga mejoras.
> Describe el estado real del código (no un plan). Los nombres de archivo son rutas
> relativas a la raíz del repo.
>
> Versiones de referencia: **IbizaDash 1.11.3 (40)** en el estéreo y
> **IbizaCompanion 1.10.0 (29)** en el teléfono.

---

## Nota de versión 1.11.0 — 2.5D, multi-estilo y RAM

Esta sección **tiene prioridad** sobre los detalles de las secciones 5, 8 y 11,
que describen el estado 1.10.2. Lo que cambió:

### Estilos de mapa

- Nuevo catálogo `map/MapStyles.kt` en el estéreo y `map/MapStyleCatalog.kt` en
  el teléfono. Ids del protocolo: `esri_dark`, `carto_dark`, `waze_night`.
- **Los tres usan la fuente gratuita de Esri** (Dark Gray Canvas). Carto dejó de
  servir teselas sin API key: en la prueba real devolvía la marca de agua
  “API KEY REQUIRED”, así que no se puede usar en producción.
  - `esri_dark`: base oscura + capa `ref` de etiquetas. Es el estilo por defecto.
  - `carto_dark` → **Slate Dark**: misma base, etiquetas `ref` y una paleta azul
    pizarra (matriz de color propia, más clara que Esri).
  - `waze_night` → **Waze Night**: misma base, etiquetas `ref` y paleta teal.
- **Esri no tiene z17 en muchas zonas de México** (devuelve “Map data not yet
  available”). Todos los estilos limitan a **z16** (`MapStyle.maxZoom`), que es
  el máximo fiable. El teléfono puede descargar z15–z17, pero el estéreo no
  subirá de z16.
- Cada estilo tiene su carpeta de caché (`EsriDark`, `SlateDark`, `WazeNight`) y
  su carpeta de etiquetas (`*Ref`). El selector del HUD muestra teselas y MB
  cacheados por estilo.
- El estéreo aplica `ColorMatrixColorFilter` por estilo (matriz base fuera de
  NAV y matriz de navegación en NAV). El teléfono replica el look con un filtro
  CSS en la capa Leaflet.

### Protocolo

- `TILE_REQ` y `TILE_BATCH` llevan `"style": "<styleId>"`. Si falta, el
  teléfono usa su estilo activo y el estéreo asume `esri_dark` (compatibilidad).
- `ROUTE_SET` acepta el alias `ROUTE_DATA`.
- Cada `step` incluye `name` (calle) y `ref` (escudo). OSRM los entrega
  directamente; TomTom se mapean desde `street` y `routeNumbers`.

### Render 2.5D

- `map/RouteLineOverlay.kt`: **dos pasadas** (casing `#004C70` 18 dp + core
  `#00E5FF` 12 dp) con `Path`/`Paint`/`Point` reutilizados. Se eliminaron el
  glow y las flechas cada 120 m (`RouteChevronOverlay` se borró).
- `ui/PerspectiveNavOverlayView.kt` dibuja en espacio de pantalla:
  - **globos de calle** blancos con borde cian y texto oscuro (máximo 3
    etiquetas: badge de maniobra + globo + escudo),
  - **escudos viales** (ej. `48D`) tomados de `step.ref`,
  - **coche 3D** de 128×128 dibujado por `ui/CarSpriteFactory.kt` (verde, vista
    trasera, calaveras, placa y sombra integrada; 64 KB de RAM),
  - micro-rotación del coche ±12° atada a la animación del rumbo.
- El HUD usa la tarjeta verde petróleo `#0D5C56` (24 dp) con sub-píldora
  `#08423E`, botones circulares `#CC12151A` (brújula, mapa, voz) y píldoras
  inferiores de resumen y “Centrar”. La píldora de velocidad se oculta en NAV
  para no solaparse con el resumen.

### RAM y caché

- Teselas base decodificadas en **RGB_565** (128 KB por tesela en vez de 256 KB)
  mediante `StyledTileSource.getDrawable`; la capa `ref` conserva ARGB_8888.
- `cacheMapTileCount = 16`, `cacheMapTileOvershoot = 2`; caché de disco con tope
  de 320 MB (trim a 280 MB).
- En 2.5D se desactiva la capa `ref` y **no se piden** teselas de etiquetas.
- El encuadre de `TILE_REQ` se expande 1.5× en latitud y 1.15× en longitud
  cuando la perspectiva está activa, para no dejar huecos en el horizonte.
- El `MapView` no se agranda: la cobertura de esquinas se hace con la matriz de
  cámara (`overscan = 1.25`, `Camera.rotateX(52°)`), sin disparar peticiones
  extra.

### Correcciones 1.11.1

- **Sprite real**: `res/drawable-nodpi/nav_car_3d.png` (128×128, ~21 KB) recortado
  del `Auto GPS.png` del usuario, con fondo transparente y sombra suave. Se usa
  en 2.5D; `CarSpriteFactory` queda como respaldo.
- **Mini-mapa**: `TILE_REQ` ya no se limita a NAV. Inicio/Música/Auto piden las
  teselas de su encuadre al entrar al foco y en el bucle de 3 s, así el
  mini-mapa cambia de estilo y de zona igual que el mapa grande.
- **Zoom sin salir de NAV**: un pellizco (más de un dedo) ya no activa el modo
  libre ni aplana la perspectiva. La cámara además se pausa mientras hay un dedo
  sobre el mapa y retoma el seguimiento al soltar.

### Correcciones 1.11.2

- **Sprite adaptativo al zoom**: el coche 3D escala entre ~0.70× y 1.15× según
  `map.zoomLevelDouble` (z15 autopista más pequeño, z16 calle a tamaño normal).
  Un `MapListener` invalida el overlay en cada scroll/zoom para que el globo y
  el escudo sigan anclados al vértice real.
- **Ruta real**: la línea recta que se veía era la polilínea sintética del
  broadcast `DEBUG_ROUTE_SET` (6 puntos), no el motor de rutas. Con un
  `ROUTE_SET` real de OSRM la ruta sigue calles y glorietas, y los `name`/`ref`
  de cada maniobra alimentan los globos y escudos.
- **Pin y globo alineados con la ruta**: `PerspectiveMapView.perspectiveScreenPoint`
  ahora aplica la matriz de rotación/zoom de osmdroid
  (`getScaleRotateCanvasMatrix`) antes de la matriz de perspectiva. Antes el pin
  de destino y el globo se proyectaban sin la rotación heading-up y no
  coincidían con el final de la línea.

### Corrección 1.11.3

- **Proporción del coche**: el sprite baja de 104 dp a **72 dp en z16** (~60 dp
  en z15 y ~79 dp en z17), mucho más cerca de la escala de la calle y de
  Google Maps/Waze. El globo de calle se separa 36 dp del badge para no taparlo.
- La escala sigue el zoom en vivo; el cambio se nota de inmediato al acercar o
  alejar.

---

## 1. Resumen en 30 segundos

Hay **dos apps** y el reparto de responsabilidades es deliberado:

| | Estéreo (coche) | Teléfono |
|---|---|---|
| Paquete | `com.chronosbvrx.ibizadash` | `com.chronosbvrx.ibizacompanion` |
| UI | Android Views (XML), sin Compose | Jetpack Compose + WebView Leaflet |
| Internet | **No** (por diseño) | Sí (única fuente de red) |
| Mapa | **osmdroid** con teselas Esri Dark Gray en caché local | WebView Leaflet (solo para planificar) |
| GPS | Motor propio `GpsEngine` + `RouteNavigator` (por defecto) | `PhoneNavigator` (solo si se activa el respaldo) |
| Trabajo | Pinta, anima, guía, habla | Busca lugares, calcula ruta, trae teselas, clima, OTA |

El canal de datos es **Wi-Fi Direct / TCP** (`JSON` por línea, puerto 47800). Bluetooth
solo se usa para A2DP/AVRCP, HFP y ELM327; **no hay canal de datos por Bluetooth**.

Flujo típico:

```
[Teléfono]                        [Estéreo]
Busca lugar (Photon/Geocoder/Nominatim)
Calcula ruta (TomTom con tráfico, o OSRM)
  -- ROUTE_SET (polyline + steps + tráfico) -->  guarda NavRoute
  -- TILE_BATCH / TILE_REQ <-> TILE_BATCH ----->  escribe caché osmdroid
                                                dibuja ruta + guía con GPS del coche
                                                publica NAV_STATE (para la UI del teléfono)
  <-- NAV_STATE (snapshot/estado) --------------
  -- VOICE_REQ --> TTS local --> VOICE_CLIP --->  reproduce WAV cacheado
```

---

## 2. Inventario de pantallas

### 2.1 Estéreo — una sola `MainActivity` con 4 "focos"

No hay actividades separadas por pantalla: hay **un lienzo continuo** con un `MapView`
persistente y 4 estados (`Focus` en `MainActivity.kt:699`):

| Foco | Dock | Reparto de columnas | Qué se ve |
|---|---|---|---|
| `COCKPIT` | INICIO | 40 % / 30 % / 30 % | Reloj, **mini-mapa grande** en la columna izquierda, tarjeta de música, tarjeta hero con coche + velocímetro, clima, OBD |
| `NAV` | MAPA | **100 % mapa** | Mapa a pantalla completa, ruta, coche, tarjeta de maniobra, progreso, botones salir/terminar |
| `MEDIA` | MÚSICA | 25 % / 0 / 75 % | Mapa a la izquierda (sin puck), módulo de música o radio |
| `VEHICLE` | AUTO | 25 % / 0 / 75 % | Mapa a la izquierda, módulo de vehículo/OBD |

- El cambio de foco se hace en `applyFocus()` (`MainActivity.kt:4160`): cross-fade +
  desplazamiento, y el split se aplica en **un solo salto** de layout para no forzar
  redraws de osmdroid en el Cortex-A7.
- El dock tiene 6 ítems: INICIO, MAPA, MÚSICA, AUTO, APPS, AJUSTES (`setupDock`, línea 2828).

#### Inicio (`COCKPIT`)

- El mismo `MapView` que usa NAV se "encoge" dentro de `miniMapContainer`
  (`activity_main.xml:116`): peso 1 en Inicio, `MATCH_PARENT` en el resto.
- **No se dibuja el coche**: al salir de NAV, `syncMapPuck(nav = false)` quita el
  `positionMarker` y el `NavPuckOverlay`. Se queda solo la ruta + pin de destino.
  Hay una píldora "Tu ubicación" como sustituto textual.
- Zoom fijo 16 y norte arriba (`applyNavCameraFrame(false)` reinicia el zoom si NAV lo
  había cambiado).
- El mini-mapa usa el mismo `PerspectiveMapView`, pero con `pitch = 0`.

#### Mapa / navegación (`NAV`)

- `view_nav_overlay.xml`: tarjeta de maniobra arriba-izquierda (600 dp) con icono,
  distancia grande, acción, "luego …", ETA, destino, aviso `TRÁFICO +X MIN`,
  barra de progreso, botones "Salir" y "Terminar".
- En navegación **se fuerza el foco** al llegar una ruta (`drawNavRoute` →
  `applyFocus(Focus.NAV)`), y al borrarla vuelve a `COCKPIT`.
- Toque simple en el mapa en otro foco → abre NAV (`MapEventsOverlay` en `setupMap`).
- En NAV con seguimiento activo se activa el modo **2.5D** (ver §5.6).
- Un **arrastre > 18 px** pone el mapa en `FREE` (no un toque): aparece "Centrar".
  A los 30 s sin tocar, vuelve solo a `FOLLOWING` (`markMapFree`, línea 4670).

#### Música / Auto (`MEDIA`, `VEHICLE`)

- Mapa al 25 % de ancho, plano (sin perspectiva), con la ruta dibujada pero sin puck.
- En estas pantallas se muestra además la **píldora turn-by-turn** (`navPill`) y, en
  `MEDIA`, el mini HUD con la franja de maniobra (`miniMavStrip`).

#### `MapActivity` (legado)

- `MapActivity.kt` es un mapa a pantalla completa antiguo con `MyLocationNewOverlay`.
- **Ya no se usa**: el clic de la píldora abre `Focus.NAV` (`MainActivity.kt:1123`).
  Sigue en el manifest. Es candidato a eliminar.

### 2.2 Teléfono

#### Inicio (dashboard)

- `feature/dashboard/DashboardScreen.kt`: si hay ruta activa, `RouteCard` con icono de
  maniobra, destino, ETA, barra de progreso y **`RouteSketch`**.
- `RouteSketch` **no es un mapa**: decodifica la polilínea y la dibuja en un `Canvas`
  normalizada, con la parte recorrida atenuada. Es un esquema, no geografía real.
- Botones: ir a Rutas, terminar ruta, atajos.

#### Rutas (`NavigationScreen`)

- **WebView con Leaflet** cargado desde `assets/leaflet/` (`MAP_HTML` embebido en el
  propio Kotlin). Teselas Esri Dark; soporte de `setOfflineBase(...)` si existe
  `offline-tiles/`.
- Buscador con debounce 700 ms (mín. 3 caracteres) → `PlaceSearch`.
- Marcador de posición propio con rumbo suavizado (`setCurrent(lat, lon, bearing, follow)`).
- Al elegir un resultado: `RouteBuilder.build(...)` → se pinta la ruta en el WebView
  (`setRoute`) y se abre un `ModalBottomSheet` con km/min y dos acciones:
  **"Enviar al IbizaDash"** y **"Enviar mapa del recorrido"** (teselas).
- `sendRoute()` envía `ROUTE_SET` y arranca `PhoneNavController` (navegador local del
  teléfono) + `sendRouteTiles()` si el enlace es Wi-Fi.
- `MapFrameSender` (vídeo del mapa) está **apagado** (`ENABLE_REMOTE_MAP = false`); queda
  como código heredado de la "Fase 2".

#### Ajustes

- Tráfico (clave TomTom), GPS del teléfono como respaldo, voz, módulos/teselas offline,
  Wi-Fi Direct en 5 GHz, etc.

---

## 3. Arquitectura de datos del estéreo

### 3.1 Repositorios globales (singletons con listeners)

| Repo | Archivo | Contenido |
|---|---|---|
| `NavRouteRepository` | `nav/NavRouteRepository.kt` | ruta activa: destino, distancias, **polilínea codificada**, `steps`, tráfico |
| `NavStateRepository` | `nav/NavGuidance.kt` | estado de guía: icono, distancia, acción, restante, ETA, progreso, off-route, llegada |
| `NavRepository` | `nav/NavRepository.kt` | píldora "legacy" título/texto/sub (compatibilidad) |
| `PhoneNavBridge` | `nav/PhoneNavBridge.kt` | modo "el teléfono manda" (solo con preferencia activada) |
| `NavStateRelay` | `nav/NavStateRelay.kt` | reenvía el estado al teléfono con throttle |
| `MapTileBridge` | `nav/NavBridgeProtocol.kt` | avisa que llegaron teselas nuevas |
| `RemoteMapBridge` | `nav/RemoteMapBridge.kt` | receptor de `MAP_FRAME` (desactivado) |

### 3.2 Fuente de verdad de la posición

Dos modos excluyentes:

1. **GPS del estéreo (por defecto).**
   `GpsEngine` → `onGpsFix()` → `RoadSnapper` → `positionMarker` + `RouteNavigator`
   (que publica `NavState`).
2. **GPS del teléfono (opt-in, `Prefs.usePhoneGps`).**
   Llegan `PHONE_NAV_STATE` → `PhoneNavBridge` publica `NavState` y **detiene**
   `RouteNavigator`. Un watchdog de 10 s sin estados devuelve el control al estéreo.

En ambos modos `MainActivity` consume `NavState` y hace el dibujo, la cámara y la voz.

---

## 4. Protocolo estéreo ↔ teléfono

Archivo: `nav/NavBridgeProtocol.kt` (semántica) y `nav/WifiNavBridgeServer.kt`
(transporte). Es **JSON por línea** sobre TCP.

### 4.1 Transporte

- **TCP 47800**, servidor en el estéreo, múltiples clientes (`liveOuts`).
- **UDP 47801** para descubrimiento: `IBIZADASH_DISCOVER` → `IBIZADASH:47800`.
- Autenticación: el primer mensaje debe ser `HELLO` con `token =
  "chronosbvrx-ibiza-6j"` (constante en código). Si no, `ERROR auth` y se cierra.
- `WifiNavBridgeServer` crea el grupo Wi-Fi Direct como Group Owner
  (`DIRECT-IbizaDash`), intenta 5 GHz en API 29+ y cae a 2,4 GHz si el chip no puede.
- Keepalive: `PING` cada 15 s desde el teléfono; el estéreo considera muerto un socket
  con más de 60 s de silencio (`hasClients`).
- Al conectar, el estéreo envía: `WELCOME`, snapshot `NAV_STATE` y `NAV_MODE`
  (`gps: "stereo" | "phone"`).

### 4.2 Mensajes relevantes para mapas

| Tipo | Dirección | Contenido |
|---|---|---|
| `ROUTE_SET` | 📱 → 🚗 | destino, `totalDistanceMeters`, `totalDurationSeconds`, `encodedPolyline`, `steps[]` (lat/lon/instruction/maneuver/modifier/distance), `trafficDelayS`, `trafficSections[]` (from/to 0..1, delayS, kmh) |
| `ROUTE_CLEAR` | 📱 → 🚗 | borra ruta y guía |
| `REROUTE_REQUEST` | 🚗 → 📱 | cuando el coche se sale de ruta: lat/lon actual + destLat/destLon + nombre |
| `PHONE_NAV_STATE` | 📱 → 🚗 | estado completo + posición + snapped + rumbo + velocidad (solo en modo respaldo) |
| `NAV_STATE` | 🚗 → 📱 | estado de guía + `polyline` y totales en snapshot |
| `NAV_MODE` | 🚗 → 📱 | qué GPS manda |
| `TILE_REQ` | 🚗 → 📱 | `layer` (`base`/`ref`), `z`, `x0,y0,x1,y1` |
| `TILE_BATCH` | 📱 → 🚗 | una tesela PNG en base64 + `layer`/`zoom`/`x`/`y` |
| `TILE_STATS` | 📱 → 🚗 | resumen de envío |
| `VOICE_REQ` | 🚗 → 📱 | lista de frases a sintetizar |
| `VOICE_CLIP` | 📱 → 🚗 | WAV en base64 |
| `MAP_FRAME` | 📱 → 🚗 | JPEG/WebP del mapa del teléfono (**ignorado** por bandera) |
| `WEATHER`, `MEDIA_ART`, `APK_*`, `MODULE_*`, `NOTIFY` | varios | otros subsistemas |

> El mismo TCP transporta OTA, módulos, clima, carátulas y voz.

---

## 5. Render del mapa en el estéreo

### 5.1 Configuración base (`map/MapHelper.kt`)

- Fuente: **Esri World Dark Gray Canvas** (`World_Dark_Gray_Base`), 256 px, z0–19,
  sin API key. URL `/tile/{z}/{y}/{x}`.
- Capa de etiquetas: `World_Dark_Gray_Reference` en PNG transparente, como segunda
  `TilesOverlay`.
- `MapTileProviderBasic(context, source, TileWriter())` **forzado**: en Android 10+
  osmdroid usaría caché SQL y el módulo offline / el teléfono escriben archivos.
- `setUseDataConnection(false)` en base y etiquetas: el estéreo **nunca descarga**.
- Sin cuadrícula de carga (`setLoadingBackgroundColor/LINE = TRANSPARENT`).
- Centro por defecto: Morelia (19.7060, −101.1950), zoom 16.
- `CopyrightOverlay` abajo-izquierda con "Esri, HERE, Garmin, © OpenStreetMap contributors".

Rutas de caché (escritas por `saveTile`):

```
<externalFilesDir>/osmdroid/tiles/EsriDark/{z}/{x}/{y}.tile
<externalFilesDir>/osmdroid/tiles/EsriDarkRef/{z}/{x}/{y}.tile
```

La escritura es atómica (`*.tile.tmp` + rename) para que osmdroid no lea PNG a medias.

### 5.2 Orden de capas (de abajo a arriba)

1. Tiles base (con `ColorMatrixColorFilter` azul petróleo en NAV).
2. Etiquetas (`CacheAwareTilesOverlay`) — **se ocultan en 2.5D**.
3. `CopyrightOverlay`.
4. `MapEventsOverlay` (toque → NAV).
5. Ruta: **glow** 26 px (`#3300E5FF`), **casing** 18 px (`#0A1018`), **core** 12 px
   (`#00E5FF`), todo `ROUND`/`ROUND`, no geodésico.
6. Tramos de tráfico: 14 px, encima del core.
7. Flechas de dirección (`RouteChevronOverlay`).
8. Pin de destino (cobre `#E07A5F`, 30×40 dp, dibujado por código).
9. `NavPuckOverlay` (halo + cono) — solo en NAV plano.
10. `positionMarker` (sprite del coche) — solo en NAV plano.

`bringPuckToFront()` reordena puck y marker después de añadir/quitar ruta.

### 5.3 La ruta: construcción y recorte

- `decodePolyline()` (decodificador Google estándar) → `List<GeoPoint>`.
- `routeCum` = distancias acumuladas (haversine).
- Se crean 3 `Polyline` + N tramos de tráfico + flechas + pin.
- `updateRouteDisplay(state)` recorta **las 3 pasadas** y los tramos de tráfico al
  progreso (`subList(cut, size)`), con:
  - corte por búsqueda binaria en `routeCum`,
  - throttle 800 ms y cambio mínimo 0.3 %,
  - rearme si el progreso cae > 3 pp (reroute / giro en U).
- `RouteChevronOverlay`: flechas cada **120 m** (máx. 2000), dibuja máximo **80 por
  frame**, solo si `zoom >= 14.5`, salta las que ya se recorrieron con búsqueda binaria
  por índice de segmento.

### 5.4 Colores de tráfico

```kotlin
kmh in 1..20  -> #EF4444 (rojo)
kmh in 21..40 -> #F59E0B (ámbar)
else          -> #EAB308 (amarillo)
```

`TRÁFICO +X MIN` solo si `trafficDelaySeconds >= 60`.

### 5.5 Puck y coche

- `NavPuckOverlay`: halo radial 104 dp (`0x4400E5FF → transparente`) + cono 62×78 dp
  con degradado. Se dibujan como bitmaps cacheados, no se crean shaders por frame.
- El sprite del coche es un `Marker` **flat** de osmdroid, 56 dp
  (`updateMapMarkerSprite`), con 8 variantes por sector de rumbo y luces on/off.
- En NAV plano: puck + marker dentro del `MapView`.
- En NAV 2.5D: ambos se ocultan y `PerspectiveNavOverlayView` pinta coche, cono, halo
  y pin **en espacio de pantalla** (sin aplastarse).

### 5.6 Modo conducción 2.5D (`ui/PerspectiveMapView.kt`)

- `MapView` propio que aplica en `dispatchDraw` una matriz de `Camera`:
  - `rotateX(52°)`, cámara Z `-24`, pivote `(w/2, 0.79 * h)`.
  - `overscan = 1.28` (escala posterior alrededor del pivote para tapar esquinas).
- El **centro geográfico** se desplaza con `setMapCenterOffset(0,
  (0.79 − 0.5) * h)` para que el coche quede abajo; el pivote de rotación usa el mismo
  offset, así no se mueve al girar.
- `PerspectiveNavOverlayView` dibuja fuera de la matriz:
  - banda de horizonte/niebla `#0D2538 → #0F344C → transparente`, altura adaptativa
    (17 % del alto, o ajustada a `perspectiveTopY()`),
  - pin de destino (proyectado a pantalla),
  - cono + halo + sprite del coche (escala `overscan * 1.18`, 1.25–1.8).
- Se activa solo si: foco NAV **y** `FOLLOWING` **y** sin dedo en pantalla **y** sin
  mapa remoto. Un `ACTION_DOWN` aplana el mapa (para hit-testing exacto) y al soltar
  vuelve tras 220 ms.
- Consecuencia conocida: **en 2.5D se desactivan las etiquetas de calles**
  (`refTilesOverlay.isEnabled = !active`).

### 5.7 Cámara, rumbo y zoom

- **Heading-up**: `map.mapOrientation = -rumbo`, animado por `ValueAnimator`
  (`DecelerateInterpolator`, 12 ms/grado, 280–680 ms), umbral 2°.
- El rumbo sale de la **geometría de la ruta** (`routeBearingAt`), no del GPS: media
  circular ponderada de 4 segmentos con doble peso al actual. GPS solo si no hay ruta.
- **Seguimiento**: `animateFollow` interpola entre fixes (900 ms si > 2 km/h, 350 ms
  parado); si el salto es > 300 m, recoloca sin animación.
- **Zoom por velocidad** con histéresis por bucket:

| Bucket | Velocidad | Zoom |
|---|---|---|
| 0 urbano | < 18 km/h (entra si baja de 32) | **17** solo con enlace Wi-Fi, si no 16 |
| 1 carretera | 18–76 km/h | 16 |
| 2 autopista | > 76 km/h (sale si baja de 62) | 15 |

- El cambio de zoom se hace en pasos del 22 % cada 320 ms.
- Antes de subir a z17 espera a que **≥ 85 %** de las teselas del encuadre estén en
  disco (`navZoomTilesReady`, recorte 0.5); si no, osmdroid mostraría huecos.

### 5.8 Teselas bajo demanda

- Bucle cada 3 s, solo en NAV y con enlace activo (`maybeRequestViewportTiles`).
- Pide el **encuadre visible** (`boundingBox`), máx. **64 teselas** por pedido.
- Si el zoom objetivo es mayor que el visible, prefetchea el centro recortado al 55 %.
- `ref` solo si `z >= 15` y es el zoom visible.
- Dedupe por clave `z:x0:y0:x1:y1` durante 30 s.
- `MapTileBridge` invalida la caché en memoria 1.2 s después de una ráfaga, para que
  osmdroid relea del disco.

### 5.9 Color en NAV

`navRoadColorFilter` aplica una `ColorMatrix` (contraste ×1.24, tinte azul) a las
teselas base solo en NAV. En Inicio/Música/Auto el mapa va con su color natural.

---

## 6. GPS y map-matching

### 6.1 `GpsEngine` (`car/GpsEngine.kt`)

- Escucha GPS (500 ms), red (1000 ms) y pasivo; pide XTRA y hora al arrancar.
- Tick de 250 ms emitiendo `GpsFix`.
- Reglas:
  - rechaza saltos > 220 km/h implícitos (con precisión > 20 m),
  - rechaza fixes con precisión > 80 m si hay uno reciente (< 15 s),
  - ignora red si el GPS dio fix en los últimos 10 s,
  - detecta GPS "pegado" (velocidad > 2.2 m/s y posición inmóvil 3 muestras),
  - suaviza velocidad y rumbo (alpha 0.45–0.75),
  - **extrapola** con velocidad constante que decae: 20 s; a los 25 s declara
    `lost` y velocidad 0; a los 60 s reintenta registrar GPS.
- Expone satélites usados (`GnssStatus`) y origen (`gps`/`network`/`predicted`).

### 6.2 `RoadSnapper`

1. Si hay ruta activa → proyección sobre la polilínea (`RouteNavigator.projectOnRoute`,
   solo si la distancia lateral ≤ 60 m).
2. Si precisión > 30 m → fix crudo (no suavizar).
3. Rechazo de saltos > 90 m a velocidad > 8 km/h.
4. Suavizado exponencial con alpha 0.25 / 0.45 / 0.65 según velocidad.
5. `reset()` al recuperar señal tras extrapolación.

### 6.3 `RouteNavigator` (guía local)

- Construye arrays de segmentos y distancias acumuladas al fijar ruta.
- En cada fix (throttle 500 ms):
  - busca en ventana `cursor ± (−4/+12)`; si nada ≤ 60 m, escaneo completo,
  - **off-route** tras 3 fixes consecutivos fuera: publica "Recalculando" y manda
    `REROUTE_REQUEST` al teléfono (cooldown 30 s),
  - **llegada** a < 45 m del final o del último paso,
  - calcula `traveled`, `remaining`, `progress`,
  - elige la próxima maniobra con un **perfil de pasos** (distancia acumulada de cada
    `step` proyectada sobre la geometría), no por cercanía euclidiana,
  - ETA: si velocidad > 12 km/h usa la real; si no, asume 45 km/h.
- Publica `NavState` con posición **snapada a la carretera**, no el punto GPS crudo.

### 6.4 `PhoneNavigator` (respaldo)

- Es **el mismo algoritmo duplicado** en el teléfono: segmentos, perfil de pasos,
  off-route, llegada, ETA, iconos.
- `PhoneNavController` publica `PHONE_NAV_STATE`, actualiza la UI local y, al salir de
  ruta, recalcula y reenvía `ROUTE_SET`.
- Solo se usa si el usuario activa "GPS del teléfono" en Ajustes.

---

## 7. Cálculo de rutas y tráfico (teléfono)

### 7.1 `RouteBuilder`

1. Si hay clave TomTom → `TomTomRouting.route(...)`.
2. Si falla o no hay clave → **OSRM público** (`router.project-osrm.org`).
3. Devuelve `BuiltRoute` con polilínea, pasos en español y tráfico.

### 7.2 TomTom

- `https://api.tomtom.com/routing/1/calculateRoute/...?traffic=true&travelMode=car&routeType=fastest&sectionType=traffic&computeTravelTimeFor=all`
- Extrae `summary.trafficDelayInSeconds`, `noTrafficTravelTimeInSeconds` y las
  `sections` de tipo `TRAFFIC` → `TrafficSection(from, to, delayS, speedKmh)` con
  `from`/`to` como fracción 0..1 del recorrido.
- Convierte maniobras TomTom al vocabulario OSRM (`turn`, `roundabout`, etc.) y
  re-encoda la polilínea a formato Google.

### 7.3 OSRM

- `overview=full&geometries=polyline&steps=true`.
- Instrucciones en español con `RouteBuilder.humanInstruction(type, modifier, street)`.
- Sin datos de tráfico.

### 7.4 Búsqueda de lugares

`PlaceSearch` combina, en orden y con caché LRU de 5 min:

1. `AndroidGeocoderClient` (geocodificador del sistema) o `Photon` según categoría.
2. `PhotonClient` (bbox 0.35° ≈ 39 km; luego 0.9° ≈ 100 km).
3. `NominatimClient` (viewbox + `countrycodes=mx`; luego nacional).

Se deduplica por nombre + coordenada y se ordena por distancia.

---

## 8. Teselas y offline

### 8.1 Origen de las teselas en el teléfono (`data/TileSender.kt`)

Por cada tesela, en orden:
1. Pack offline del teléfono (`offline-tiles/{z}/{x}/{y}.png`).
2. Caché osmdroid del teléfono (`EsriDark` / `EsriDarkRef`).
3. Descarga online de Esri (solo si **no** hay música reproduciéndose).

### 8.2 Dos modos de envío

- **Corredor de ruta** (`sendRouteTiles`): z14 (visión general), z15 (centros + anillo
  3×3) y z16 (calle). Máx. **420 teselas / 8 MB**.
- **Viewport** (`TILE_REQ` → `sendViewportTiles`): z13–18, máx. 36 base / 20 ref,
  tope 2 MB / 1.2 MB, ordenadas del centro hacia afuera.
- Ritmo adaptativo (`AudioCoexistence`): **250 KB/s** sin música, **60 KB/s** con
  música; con música no se descarga online, solo se sirve lo local.
- Todo va en **base64** (≈ +33 % de overhead).

### 8.3 Módulos offline

- `modules.json` en el release de GitHub describe packs `tiles` y `voice` con SHA-256.
- El teléfono descarga y transmite el ZIP por chunks; el estéreo verifica, descomprime
  y registra en `files/modules.json`.
- El módulo `tiles-morelia` v1 (13.7 MB, z13–16) instala PNG en la caché de osmdroid.
- Ajustes rápidos muestra "packs · MB" o "Morelia · X MB" contando archivos.

---

## 9. Interfaz de navegación en el estéreo

### 9.1 Tarjeta de maniobra (`NAV`)

`renderNavOverlay()` (`MainActivity.kt:4879`):

- Icono (`ic_mav_*`), distancia grande, acción ("Gira a la derecha en …"), "luego …",
  ETA, destino, restante, barra de progreso.
- `TRÁFICO +X MIN` si aplica.
- Sin ruta: "Sin destino activo / Planifica una ruta desde IbizaCompanion".

### 9.2 Píldora y mini franja

- `navPill` (Inicio oculto; Música/Auto visible): icono + distancia + acción +
  "luego"/restante.
- `miniMavStrip` dentro del mini HUD: icono, distancia y acción compactos.

### 9.3 Voz

`VoiceGuidance`:

- Umbrales fijos **800 / 400 / 200 / 100 / 50 m** por maniobra, una vez por umbral.
- No repite en < 3.5 s; no encadena más de 2 clips.
- Anuncia llegada y "Fuera de ruta / Recalculando" (cada 10 s).
- Pide al teléfono solo las frases que faltan (`VOICE_REQ`); el teléfono sintetiza con
  TTS es-MX y devuelve WAV (`VOICE_CLIP`); el estéreo cachea en `files/voice/`.
- Se puede desactivar en Ajustes rápidos; muestra peso de caché y permite liberarla.

---

## 10. Decisiones de rendimiento (contexto hardware)

El estéreo es un **SPRD sp7731e**, Android 8.1, 1280×720, 2 GB RAM, Cortex-A7. Todas
las decisiones raras del código vienen de ahí:

- osmdroid a 60 fps en vez de vídeo por Wi-Fi (por eso `MAP_FRAME` está apagado).
- Un solo `MapView` reutilizado en todas las pantallas.
- Cross-fade solo con `alpha`/`translation`; nada de bitmaps ni capas.
- Split de columnas en un solo salto de layout.
- Flechas limitadas a 80 por frame y búsqueda binaria por segmento.
- Bitmaps de halo/cono cacheados.
- Los `NavState` se publican a 2 Hz y al teléfono a 1.5 s mínimo.
- Teselas con tope de cantidad, bytes y ritmo; ref solo al zoom visible.
- `z17` solo con Wi-Fi y teselas ya en disco.

---

## 11. Puntos frágiles / deuda técnica (observados en el código)

Estos son hechos, no opiniones; sirven como lista de preguntas para el análisis.

### Arquitectura y duplicación

1. **Lógica de navegación duplicada**: `RouteNavigator` (estéreo) y `PhoneNavigator`
   (teléfono) implementan el mismo algoritmo por separado. Cualquier corrección hay
   que hacerla dos veces.
2. **Decodificadores de polilínea repetidos** en al menos 6 sitios: `MapHelper`,
   `RouteNavigator` (usa el de MapHelper), `PhoneNavigator`, `PhoneNavController`,
   `TileSender`, `NavigationScreen`, `DashboardScreen`.
3. **Dos modelos de estado** (`NavState` + `NavRepository` legacy) con campos
   redundantes; la píldora usa el "legacy" y la tarjeta usa `NavState`.
4. **`NavStateRelay` reconstruye metros parseando un string formateado**
   (`"quedan 1.2 km · 8 min"`) con regex. Rompe con otro idioma/formato.
5. **`MapActivity` y `RemoteMapBridge`/`MapFrameSender` son código muerto** con
   banderas `ENABLE_REMOTE_MAP = false`.

### Red y seguridad

6. **Token compartido hardcodeado** (`chronosbvrx-ibiza-6j`) y sin TLS. El modelo de
   seguridad depende de que el grupo Wi-Fi Direct sea privado.
7. **base64 en JSON** para teselas, APK, módulos y voz: overhead de ~33 % y picos de
   memoria en un equipo de 2 GB.
8. **Sin ACK por chunk** en módulos/OTA (solo validación al final).
9. Un solo hilo lector por socket; si el teléfono manda `TILE_BATCH` grandes, el
   `readLine()` puede bloquear el resto de mensajes del mismo socket.

### Motor de mapas

10. **Esri Dark Gray raster** con licencia/atribución: el copyright está presente, pero
    en 2.5D se ocultan las etiquetas (no la atribución). Revisar términos de uso para
    uso embebido.
11. **Map matching heurístico**: proyección a la polilínea + ventana de cursor +
    umbral 60 m. No hay HMM ni corrección por dirección; en calles paralelas, bucles o
    glorietas puede enganchar el segmento equivocado.
12. **Perfil de pasos heurístico** (ventana 150 m, corte a 150 segmentos): si un paso
    cae lejos de la geometría, el banner puede anunciar el giro equivocado.
13. **Rumbo de ruta** = media ponderada de 4 segmentos. En curvas cerradas puede
    adelantarse o retrasarse al volante.
14. **Tráfico**: los tramos se interpolan por **índice de punto** (`from * (n-1)`), no
    por distancia acumulada. En polilíneas con puntos irregularmente espaciados el
    color puede desplazarse.
15. **Color de tráfico**: `else → amarillo` incluye `kmh = 0` (sin dato) y velocidades
    > 40, así que un tramo desconocido se pinta como "lento".
16. **En 2.5D no hay nombres de calles** (se apaga la capa `ref`), justo cuando más se
    necesitan.
17. **`z17` depende de que el teléfono tenga la tesela**: sin enlace nunca se acerca,
    aunque el pack offline llegue a z16.
18. **Caché de teselas sin política de expiración ni límite**: crece indefinidamente;
    quitar un módulo no borra sus teselas.
19. El chequeo de "packs offline" en Ajustes solo mira presencia de `.mbtiles`/
    `.sqlite`, pero osmdroid **no está configurado para leer MBTiles**: es cosmético.

### Cámara y UX

20. **Inicio no muestra el coche** en el mini-mapa (se quita el marker fuera de NAV).
    El usuario ve la ruta y el pin, pero no dónde está.
21. **Un toque en el mapa en Inicio abre NAV** (`MapEventsOverlay`), lo que puede
    sorprender si el usuario solo quería mirar el mapa.
22. **Perspectiva y hit-testing**: aplanar en `ACTION_DOWN` evita toques erróneos, pero
    produce un "pop" visual al tocar.
23. El seguimiento se reactiva **solo** a los 30 s o al pulsar Centrar; no hay gesto
    de "volver al coche" inmediato salvo el botón.
24. `animateFollow` cancela y reinicia animaciones cada fix (900 ms vs fixes de
    500 ms): a alta velocidad puede quedar un movimiento perpetuo a medio camino.

### Navegación funcional

25. **Sin alternativas de ruta**, sin waypoints, sin evitar peajes/autopistas.
26. **Sin búsqueda ni planificación en el estéreo**: todo destino depende del teléfono
    conectado. Si el teléfono se cae, solo hay ruta activa y guía local.
27. **Sin reroute local**: el `REROUTE_REQUEST` necesita teléfono. Sin enlace, "Fuera de
    ruta" se queda fijo hasta volver a la polilínea.
28. OSRM público (`router.project-osrm.org`) **no es un servicio para producción**:
    límites de uso y sin SLA. Si TomTom falla, la ruta puede no llegar.
29. La **búsqueda** usa geocoders comunitarios (Photon/Nominatim) con límites de uso y
    cobertura variable en México; el `countrycodes=mx` limita resultados fuera del país.
30. **Sin estado de "tráfico en vivo" en el estéreo**: solo se pinta lo que llegó en
    `ROUTE_SET`; no se refresca mientras se conduce.

### Calidad

31. No se ven **tests automatizados** del motor de navegación (proyección, off-route,
    perfil de pasos, recorte de ruta).
32. Los logs de `MainActivity` son extensos; no hay niveles ni un flag de depuración.
33. `MainActivity.kt` tiene ~228 KB y ~5 400 líneas: concentra mapa, cámara, OBD, media,
    radio, OTA, ajustes y navegación.

---

## 12. Preguntas que el análisis externo debería responder

1. ¿Conviene migrar de osmdroid raster a un motor vectorial con pitch/bearing nativo
   (MapLibre Native, Mapbox, HERE SDK) en un Cortex-A7 con 2 GB? ¿Coste real?
2. ¿Merece la pena un motor de ruta offline (Valhalla/GraphHopper) en el estéreo para
   eliminar la dependencia del teléfono, o es mejor mantener el reparto actual?
3. ¿Cómo implementar map matching robusto (HMM) con presupuesto de CPU de un A7?
4. ¿Cuál es la mejor forma de tener nombres de calles en 2.5D sin rasterizar texto
   (billboard labels, capa vectorial, o dibujar etiquetas en `PerspectiveNavOverlayView`)?
5. ¿Cómo modelar el tráfico en vivo sin saturar el enlace (deltas, solo tramo próximo,
   refresco cada N minutos)?
6. ¿Qué alternativa a Esri Dark Gray ofrece mejor licencia/atribución y buen contraste
   nocturno sin coste?
7. ¿Cómo reducir el peso del protocolo (binario, chunked, compresión, WebSocket) sin
   romper compatibilidad con los APK ya instalados?
8. ¿Qué estrategia de caché de teselas (LRU, límite por MB, prioridad por corredor)
   evita llenar el almacenamiento sin dejar huecos?
9. ¿Cómo unificar la lógica de navegación en un módulo compartido entre las dos apps?
10. ¿Qué métricas de calidad de navegación conviene instrumentar (desvío medio, giros
    anunciados tarde, reroutes por viaje) para iterar con datos?

---

## 13. Índice de archivos clave

### Estéreo — mapa y navegación

```
app/src/main/java/com/chronosbvrx/ibizadash/
├─ MainActivity.kt                  # foco, cámara, ruta, overlays, seguimiento, zoom, teselas
├─ MapActivity.kt                   # mapa legacy (sin uso)
├─ map/MapHelper.kt                 # Esri Dark + ref, caché, teselas, polyline
├─ map/RouteChevronOverlay.kt       # flechas cada 120 m
├─ ui/PerspectiveMapView.kt         # MapView con rotateX(52°)
├─ ui/PerspectiveNavOverlayView.kt  # coche/pin/horizonte en espacio de pantalla
├─ ui/NavPuckOverlay.kt             # halo + cono en NAV plano
├─ car/GpsEngine.kt                 # motor GPS con reglas estrictas
├─ nav/RouteNavigator.kt            # guía local (proyección, pasos, off-route)
├─ nav/NavGuidance.kt               # NavState, ManeuverIcon, proyección
├─ nav/NavRouteRepository.kt        # ruta activa
├─ nav/NavStateRelay.kt             # estado hacia el teléfono
├─ nav/NavBridgeProtocol.kt         # mensajes y guardado de teselas
├─ nav/WifiNavBridgeServer.kt       # TCP/UDP + Wi-Fi Direct
├─ nav/RoadSnapper.kt               # snap local y suavizado
├─ nav/PhoneNavBridge.kt            # modo GPS del teléfono
├─ nav/RemoteMapBridge.kt           # vídeo del mapa (apagado)
├─ nav/VoiceGuidance.kt             # cuándo hablar
├─ nav/VoicePlayer.kt / VoiceStore.kt
└─ res/layout/activity_main.xml, view_nav_overlay.xml, view_mini_hud.xml
```

### Teléfono — mapa y navegación

```
companion-app/app/src/main/java/com/chronosbvrx/ibizacompanion/
├─ feature/navigation/NavigationScreen.kt  # WebView Leaflet, búsqueda, sheet, envío
├─ feature/dashboard/DashboardScreen.kt    # RouteCard + RouteSketch
├─ data/RouteBuilder.kt                    # TomTom → OSRM
├─ data/TomTomRouting.kt                   # tráfico real
├─ OsrmClient.kt                           # OSRM público
├─ data/PlaceSearch.kt                     # Photon + Geocoder + Nominatim
├─ data/TileSender.kt                      # corredor y viewport
├─ nav/PhoneNavigator.kt                   # guía con GPS del teléfono
├─ nav/PhoneNavController.kt               # publica PHONE_NAV_STATE, reroute
├─ map/MapFrameSender.kt                   # vídeo (apagado)
└─ data/CompanionPrefs.kt, StereoPrefs.kt
```

### Documentación existente

```
docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md    # especificación general del suite
docs/IBIZA_COMPANION_SPEC.md           # especificación del teléfono
docs/WIFI_DIRECT_TRANSPORT.md          # transporte
docs/PROJECT_CONTEXT.md                # contexto del proyecto
docs/ROAD_TEST_1.4.0.md                # prueba en carretera
README.md                              # visión general y operación
```
