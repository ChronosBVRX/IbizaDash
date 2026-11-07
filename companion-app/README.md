# IbizaCompanion

App Android del teléfono (POCO F7 y similares) que actúa como cerebro del
estéreo **IbizaDash**: calcula rutas, reproduce/controla media, reenvía avisos
y renderiza el mapa que el estéreo muestra.

- Paquete: `com.chronosbvrx.ibizacompanion`
- minSdk 26 · targetSdk 34 · compileSdk 34
- Kotlin + Jetpack Compose + osmdroid + OkHttp
- Transporte: **Wi-Fi Direct/TCP** (preferido) y **Bluetooth RFCOMM** (respaldo)

## Compilar

Desde la raíz del repo (recomendado, usa el wrapper común):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\Axel Rosete\Desktop\Proyectos\General\IbizaDash
.\gradlew.bat -p companion-app :app:assembleDebug
```

Salida: `companion-app/app/build/outputs/apk/debug/app-debug.apk`.

También puedes abrir `companion-app/` como proyecto independiente en Android
Studio; ya tiene su propio `settings.gradle.kts` y `build.gradle.kts`.

### Requisitos

- JDK 17.
- Android SDK 34 (`sdk.dir` en `local.properties`).
- El AAR de Spotify App Remote está en `app/libs/` (incluido, 13 MB).
- Para compilar por consola crea `companion-app/local.properties`:
  ```properties
  sdk.dir=C\:\\ruta\\a\\Android\\Sdk
  ```

### API key de YouTube (opcional)

La búsqueda exacta de un video usa YouTube Data API v3. La key **no se versiona**.
Sin key, `YouTubeSearch` abre la búsqueda de YouTube Music y el usuario elige el
resultado. Para habilitar el video exacto, añade a `companion-app/local.properties`:

```properties
YOUTUBE_API_KEY=tu_key_de_google
```

También puedes pasarla en el build:

```powershell
.\gradlew.bat -p companion-app :app:assembleDebug -PYOUTUBE_API_KEY=tu_key
```

## Permisos que pide

- **Ubicación** (`ACCESS_FINE_LOCATION`): GPS para la navegación y Wi-Fi Direct.
- **Dispositivos cercanos** (`NEARBY_WIFI_DEVICES`, Android 13+): Wi-Fi Direct.
- **Acceso a notificaciones**: necesario para `MediaSessionManager` y para
  reenviar avisos al estéreo.
- **Bluetooth** (Android 12+): respaldo RFCOMM y control de media.
- **Servicio en primer plano**: mantiene el enlace y el GPS (`mediaPlayback`,
  `dataSync`, `location`).

## Uso

1. Abre la app y concede permisos.
2. **Conexión** → espera `Wi-Fi Direct · …` o escribe la IP del estéreo
   (hotspot/LAN). También puedes elegir un peer detectado.
3. **Rutas** → busca un destino, calcula y pulsa **Enviar al IbizaDash**.
   - La navegación la calcula el teléfono (`PHONE_NAV_STATE`).
   - El corredor de teselas se envía por Wi-Fi.
   - Si dejas la pantalla **Rutas** abierta, el estéreo muestra el mapa
     renderizado por el teléfono (`MAP_FRAME` a 2 fps).
4. **Media** → el estéreo puede mandar `MEDIA_CMD` a la MediaSession del teléfono.

## Estructura

```
app/src/main/java/com/chronosbvrx/ibizacompanion/
├── BluetoothNavClient.kt       # RFCOMM de respaldo
├── WifiBridgeClient.kt         # Wi-Fi Direct + TCP 47800
├── BridgeRouter.kt / BridgeLink.kt
├── map/MapFrameSender.kt       # captura y envía MAP_FRAME
├── nav/PhoneNavigator.kt       # navegación con GPS del teléfono
├── nav/PhoneNavController.kt
├── data/TileSender.kt          # teselas del corredor
├── data/RouteBuilder.kt        # OSRM → ROUTE_SET
├── data/YouTubeSearch.kt       # búsqueda opcional (API key externa)
├── service/MediaBridgeService.kt
└── feature/…                   # Compose: Dashboard, Rutas, Media, Conexión, Ajustes
```

El APK del estéreo y el resto del proyecto están en el directorio padre.
