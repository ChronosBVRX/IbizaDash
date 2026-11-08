> **Documento histórico (2026-11-05/07).** La arquitectura vigente usa Wi-Fi Direct como
> único canal de datos y OTA desde el teléfono. Ver `README.md` y
> `docs/WIFI_DIRECT_TRANSPORT.md`.

# IbizaCompanion — Especificación de la app compañera del teléfono

> App ligera para el teléfono del usuario (POCO F7) que calcula rutas pesadas y envía datos
> ya procesados al estéreo IbizaDash **por Wi-Fi Direct (TCP)**, con Bluetooth RFCOMM como
> respaldo automático, de modo que el teléfono haga todo el cálculo y el estéreo (`sp7731e`)
> solo pinte la línea y muestre maniobras con consumo mínimo.

## 1. Conexión
- UUID dedicado: `00001101-c0de-4b56-5258-4942495a4101` (no choca con el UUID estándar del ELM327).
- Nombre del servicio SDP: `IbizaDashNavBridge`.
- El estéreo actúa como **servidor** RFCOMM (`listenUsingInsecureRfcommWithServiceRecord`).
- El teléfono se conecta como cliente y envía mensajes line-delimited JSON (un JSON por línea, terminado en `\n`).

## 2. Protocolo de mensajes

### ROUTE_SET (ruta completa al elegir destino)
```json
{
  "type": "ROUTE_SET",
  "destinationName": "Centro Histórico, Morelia",
  "totalDistanceMeters": 8450,
  "totalDurationSeconds": 1140,
  "encodedPolyline": "}_ilF~_~uO...",
  "steps": [
    {"lat": 19.7024, "lon": -101.1921, "instruction": "Gira a la derecha en Av. Madero", "maneuver": "TURN_RIGHT", "distanceMeters": 350}
  ]
}
```

### NAV_STEP_UPDATE (actualización en tiempo real, ~150 bytes)
```json
{
  "type": "NAV_STEP_UPDATE",
  "nextInstruction": "Gira a la izquierda en 200 m hacia Acueducto",
  "maneuver": "TURN_LEFT",
  "distanceToTurnMeters": 200,
  "remainingDistanceMeters": 4200,
  "etaText": "14:35 (8 min)"
}
```

### ROUTE_CLEAR
```json
{"type": "ROUTE_CLEAR"}
```

### MEDIA_ART (portada del track actual, ~10–30 KB)
```json
{
  "type": "MEDIA_ART",
  "title": "Todo cambia",
  "artist": "Saturno",
  "album": "Dedos Rotos y Funerales",
  "pngBase64": "/9j/4AAQSkZJRgABAQ..."
}
```
El estéreo decodifica el base64 a `Bitmap`, lo cachea y lo muestra en el Media Center y en la tarjeta de media del Cockpit, evitando depender de iTunes/red. El teléfono puede enviarlo cada vez que cambia el track (leyendo `MediaMetadata.METADATA_KEY_ALBUM_ART` de la sesión activa o el icono grande de la notificación de media).

### TILE_BATCH (teselas del corredor de la ruta)
```json
{
  "type": "TILE_BATCH",
  "zoom": 15,
  "x": 7182,
  "y": 14520,
  "pngBase64": "iVBORw0KGgoAAAANS..."
}
```

## 3. Funciones de la app del celular
1. Buscador de destino y mapa interactivo: usa internet del teléfono para buscar direcciones (Nominatim / Photon API gratuita) y calcula la ruta óptima usando **OSRM (Open Source Routing Machine API)**, que devuelve el `encodedPolyline` y los `steps` en español.
2. Botón "Enviar Ruta al Ibiza (Bluetooth)": se conecta por RFCOMM al estéreo usando `NAV_BRIDGE_UUID`, envía el JSON `ROUTE_SET` en ~50 ms, y opcionalmente descarga las ~50 teselas Dark Gray por donde pasa la línea de la ruta y las transmite por `TILE_BATCH` en segundo plano.
3. Exportador de Mapa Regional Completo: permite descargar el mapa completo de la ciudad/estado en el teléfono y enviarlo al estéreo (por Bluetooth para zonas pequeñas o por Wi-Fi Direct / ADB / Hotspot para estados completos).

## 4. Comportamiento en el estéreo
- Al recibir `ROUTE_SET`: decodifica la polilínea (Kotlin puro, sin librerías), dibuja un `Polyline` cian (`#00E5FF`, ancho 10f, `isGeodesic=false`) en el `MapView` único, coloca un marcador en el destino, centra el mapa y cambia el foco a `Focus.NAV`.
- Actualiza `NavRepository` con distancia/duración.
- Fallback autónomo: si el teléfono se duerme, el estéreo usa su GPS local y la lista `steps` recibida para avanzar instrucciones por distancia euclidiana.
- `TILE_BATCH`: guarda los bytes en `/sdcard/Android/data/com.chronosbvrx.ibizadash/files/osmdroid/tiles/EsriDark/{z}/{x}/{y}.png`.

## 5. Estado de implementación
- Estéreo: `nav/BtNavBridgeServer.kt`, `nav/NavRouteRepository.kt`, `map/MapHelper.decodePolyline`, integración en `MainActivity.setupNavBridge()` y broadcast de prueba `DEBUG_ROUTE_SET` ya implementados.
- App compañera: pendiente de crear (carpeta `companion-app/` o proyecto separado).
