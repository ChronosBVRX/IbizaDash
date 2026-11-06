# IbizaDash

Launcher/dashboard estilo Tesla para el **SEAT Ibiza 2009-2013** con la head unit
**SPRD sp7731e** (Android 8.1, 1280Ã—720, 2 GB RAM).

![IbizaDash](screenshots/stereo_paused_final.png)

- **APK release:** `dist/IbizaDash.apk` (**~1.26 MB**, sin Play Store, sin librerÃ­as nativas).
- **Paquete:** `com.chronosbvrx.ibizadash`
- **minSdk 23 / targetSdk 33 / compileSdk 36**
- Hecho con **Views** (sin Compose) para que sea liviano en el Cortex-A7.

---

## QuÃ© incluye

| FunciÃ³n | Estado |
|---|---|
| Launcher HOME landscape | âœ… |
| Mapa oscuro con GPS (Esri Dark Gray Canvas) | âœ… |
| **Render SEAT Ibiza 2010 verde (3/4 frontal)** | âœ… |
| Velocidad CAN (`persist.can.speed`) con fallback GPS | âœ… |
| Unidades km/h / mph | âœ… |
| Reloj, fecha y clima (Open-Meteo) | âœ… |
| Reversa automÃ¡tica (AIDL de `com.tw.service.xt`) | âœ… |
| Lanzar app de cÃ¡mara `com.tw.reverse` | âœ… |
| Controles de media (play/pausa, anterior, siguiente) | âœ… |
| **Media Bluetooth: tÃ­tulo, artista, carÃ¡tula** | âœ… (con acceso a notificaciones) |
| **Estado Bluetooth: nombre, A2DP/HFP, baterÃ­a, llamada** | âœ… (baterÃ­a si el telÃ©fono la publica) |
| Dock: Mapa, Media, Radio, TelÃ©fono, CÃ¡mara, Apps | âœ… |
| CajÃ³n de aplicaciones | âœ… |
| Ajustes (clima, unidades, abrir Car Choose/ajustes coche) | âœ… |
| RPM, puertas, luces, TPMS por CAN | â³ pendiente |
| Mapas offline | â³ pendiente |

---

## Estructura

```
IbizaDash/
â”œâ”€ app/src/main/java/com/chronosbvrx/ibizadash/
â”‚  â”œâ”€ MainActivity.kt          # dashboard principal
â”‚  â”œâ”€ MapActivity.kt           # mapa a pantalla completa
â”‚  â”œâ”€ AppsActivity.kt          # cajÃ³n de apps
â”‚  â”œâ”€ SettingsActivity.kt      # ajustes
â”‚  â”œâ”€ App.kt                   # configuraciÃ³n de osmdroid
â”‚  â”œâ”€ Prefs.kt
â”‚  â”œâ”€ car/
â”‚  â”‚  â”œâ”€ CarSignals.kt         # persist.can.speed + reversa (Settings.System)
â”‚  â”‚  â”œâ”€ TwCommandBinder.kt    # transacciones AIDL del MCU
â”‚  â”‚  â”œâ”€ ReverseCallback.kt    # onReverseStatus (transacciÃ³n 6)
â”‚  â”‚  â””â”€ TwCommandClient.kt    # bind a com.tw.service.xt
â”‚  â”œâ”€ map/MapHelper.kt         # Esri Dark Gray Canvas
â”‚  â”œâ”€ media/MediaKeys.kt       # teclas de media
â”‚  â”œâ”€ weather/WeatherClient.kt # Open-Meteo
â”‚  â””â”€ ui/CarSilhouetteView.kt  # coche dibujado con Canvas
â”œâ”€ dist/IbizaDash.apk
â””â”€ screenshots/ibizadash.png
```

---

## Compilar

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\Axel Rosete\Desktop\Proyectos\General\IbizaDash
.\gradlew.bat :app:assembleRelease
```

Salida:
`app/build/outputs/apk/release/app-release.apk`

El release se firma con la llave debug para pruebas. Para distribuciÃ³n, crear un
`keystore.properties` y una `signingConfig` release real.

---

## Instalar en el estÃ©reo

```powershell
adb connect <IP_DEL_ESTEREO>:5555
adb install -r IbizaDash.apk
adb shell pm grant com.chronosbvrx.ibizadash android.permission.ACCESS_FINE_LOCATION
adb shell cmd package set-home-activity com.chronosbvrx.ibizadash/.MainActivity
```

La primera vez hay que aceptar el diÃ¡logo de ubicaciÃ³n en pantalla (o usar el `pm grant`).
DespuÃ©s de reiniciar el estÃ©reo, la autorizaciÃ³n ADB se pierde en esta ROM; hay que
volver a aceptar el diÃ¡logo RSA al arrancar.

### Volver a LiteDash

```powershell
adb shell cmd package set-home-activity com.irvingguerra.litedash/.MainActivity
```

---

## EstÃ©tica

- Render **3/4 frontal** del **SEAT Ibiza 2010 verde** del usuario.
- El fondo del render se separÃ³ con **GrabCut + mÃ¡scara suave** y se guardÃ³ como
  `app/src/main/res/drawable-nodpi/ibiza_render.png`.
- En el panel ocupa **190 dp** de alto, centrado, con el azul del underglow como acento.
- La captura `screenshots/ibizadash.png` es del **emulador real** (AVD `IbizaDash`,
  Android 36.1, 1280×720/160 dpi). El BT muestra “Sin conectar” porque el emulador
  no tiene Bluetooth; en el estéreo mostrará el teléfono real.

---

## Bluetooth del telÃ©fono

La cabecera del panel muestra una pÃ­ldora con el telÃ©fono conectado:

```
BT Â· POCO F7 Â· 87%
```

- **Nombre** y direcciÃ³n del dispositivo (bonded/connected).
- **A2DP** (audio) y **HFP** (manos libres) por `BluetoothProfile`.
- **BaterÃ­a**: se intenta `BluetoothDevice.getBatteryLevel()` por reflexiÃ³n
  (mÃ©todo oculto del SDK). Muchos telÃ©fonos devuelven `-1`; en ese caso se muestra
  â€œNo disponibleâ€. El `com.tw.bt` del estÃ©reo no expone baterÃ­a en su APK decompilado.
- **Llamadas**: el MCU avisa por `com.tw.service.xt`:
  - `onBtPhoneStatus` (3): estado de llamada.
  - `onBtCallStatus` (4): estado + nÃºmero + nombre.
  - `onBtConnectedStatus` (5).
  La pÃ­ldora cambia a â€œLlamada entrante / activa / salienteâ€ y el detalle muestra
  nÃºmero/nombre.
- Toca la pÃ­ldora para abrir el detalle (nombre, direcciÃ³n, A2DP/HFP, baterÃ­a,
  estado del MCU y llamada).

> SeÃ±al/carrier, agenda y registro de llamadas los maneja `com.tw.bt` (PBAP/HFP).
> No estÃ¡n expuestos a apps de terceros sin privilegios del sistema; se pueden
> investigar mÃ¡s adelante si hace falta.

---

## Media Bluetooth (tÃ­tulo, artista y carÃ¡tula)

La app escucha `MediaSessionManager` y las notificaciones de media:

- Muestra **tÃ­tulo, artista, Ã¡lbum y carÃ¡tula**.
- Play/pausa, anterior y siguiente controlan la sesiÃ³n activa.
- Marca la fuente como **Bluetooth** si la sesiÃ³n es A2DP o del paquete `com.tw.bt`.
- Si el Bluetooth del coche no crea una `MediaSession`, se usa el **token/extras de la
  notificaciÃ³n de media** (tÃ­tulo, texto e icono grande) como respaldo.

### Requisito: acceso a notificaciones

En el estÃ©reo:

**Ajustes â†’ Notificaciones â†’ Acceso a notificaciones â†’ IbizaDash**

O por ADB (Android 8.1):

```powershell
adb shell settings put secure enabled_notification_listeners com.chronosbvrx.ibizadash/com.chronosbvrx.ibizadash.media.MediaListenerService
```

Si ya hay otros listeners, hay que conservarlos y agregar el nuestro separado por `:`.
DespuÃ©s de activarlo, reiniciar la app:

```powershell
adb shell am force-stop com.chronosbvrx.ibizadash
adb shell am start -n com.chronosbvrx.ibizadash/.MainActivity
```

### Modo demo (para ver la interfaz sin Bluetooth)

En **Ajustes â†’ â€œModo demo de mediaâ€**, o lanzando:

```powershell
adb shell am start -n com.chronosbvrx.ibizadash/.MainActivity --ez demo_media true
```

Muestra una portada de prueba **â€œIBIZA / DEMO â€¢ BLUETOOTHâ€** con el tema
â€œMidnight Drive / The Midnightâ€ y el icono de pausa.

> La carÃ¡tula real depende de que la pila Bluetooth del coche la exponga en
> `METADATA_KEY_ALBUM_ART`/`ART`, su URI, o en el icono grande de la notificaciÃ³n.
> Si no la expone, se muestra el icono de mÃºsica.

---

## IntegraciÃ³n con el coche

### Reversa (funciona por AIDL)
El MCU avisa por `com.tw.service.xt`. Se usan las transacciones extraÃ­das del APK:

```
Servicio:   com.tw.service.xt.CommandService.Bind
Descriptor: com.tw.service.xt.aidl.ITWCommandAidl
  registerTWCommandCallback   = 1
  unRegisterTWCommandCallback = 2
  getReverseStatus            = 52

Callback:   com.tw.service.xt.aidl.ITWCommandCallbackAidl
  onReverseStatus             = 6
```

Cuando `onReverseStatus(1)` llega, se lanza:

```
com.tw.reverse/.ReverseActivity
```

Si falla, se intenta `com.tw.reverse/.LRReverseActivity`.
TambiÃ©n se lee `Settings.System` â†’ `REVERSE_STATUS_FOR_PROTOCOL` como respaldo.

### Velocidad
- Primero intenta `persist.can.speed` (SystemProperties por reflexiÃ³n).
- Si no existe o es 0, usa GPS (`LocationManager`, 1 Hz).
- La fuente se muestra como `CAN` o `GPS` debajo del velocÃ­metro.

### Mandos del volante
El MCU los maneja. El launcher recibe HOME/BACK/MENU y las teclas de media se envÃ­an
con `AudioManager.dispatchMediaKeyEvent`.

### Pendiente: RPM/puertas/luces/TPMS
El servicio CAN del fabricante (`com.tw.carinfoservice`) expone `CarServiceAidl`, pero
estÃ¡ **ofuscado** en el APK. El APK original y las fuentes decompiladas estÃ¡n en
`General/ibiza-can-dump/`. Cuando se mapee la interfaz, se puede aÃ±adir un `CanClient`
y llenar los campos `PUERTAS / LUCES / TPMS`.

### Datos CAN identificados (para la siguiente iteraciÃ³n)

Del decompilado de `TWCarInfoServiceImpl` y del AIDL `CarServiceAidl`:

- **Estado del coche**: millaje total, distancia recorrida, intermitente izquierdo,
  intermitente derecho, doble intermitente (hazard), marcha atrÃ¡s.
- **OBD / motor** (`AnnualResult`): RPM, carga del motor, acelerador, temperatura
  del refrigerante, temperatura del combustible, flujo de aire, presiÃ³n, VIN,
  CALID, CVN, distancia MIL, cÃ³digos de falla y readiness.
- **AIDL**: ~60 mÃ©todos `int`/`float`/`float[]`/`int[]`/`Bundle`; el dashboard
  del fabricante pinta con ellos velocidad, RPM, temperatura, puertas, luces,
  TPMS, combustible, autonomÃ­a, marcha y clima.
- El proxy `p057j5.a` tiene los **cÃ³digos de transacciÃ³n** (p. ej. `U1()` = 15,
  intermitente izquierdo; `m4()` = derecho; `n2()` = hazard; `i3()` = reversa;
  `n()`/`h()` = millaje/distancia). El siguiente paso es cablear esos cÃ³digos
  en un `CanClient` y probar en el coche con el CAN vivo.

---

## Mapa y atribuciÃ³n

- Tiles: **Esri World Dark Gray Canvas** (`server.arcgisonline.com`), sin API key.
- AtribuciÃ³n mostrada: *Esri, HERE, Garmin, Â© OpenStreetMap contributors*.
- Requiere internet. Si el coche no tiene datos, el mapa queda vacÃ­o; se puede aÃ±adir
  cachÃ©/offline mÃ¡s adelante.
- No se usa CARTO (ahora exige API key) ni Google Maps (mÃ¡s pesado y con GMS).

---

## Pruebas realizadas

- Emulador **Android 14** a **1280Ã—720 / 160 dpi** (misma resoluciÃ³n que el estÃ©reo).
- Launcher, mapa Esri, clima, reloj, dock, cajÃ³n de apps y ajustes funcionando.
- **Modo demo de media** con portada generada, tÃ­tulo/artista y botÃ³n de pausa.
- **Render del Ibiza 2010 verde** integrado en el panel (vista previa).
- APK release R8 de **1.18 MB** sin crashes.
- La AVD del emulador se perdiÃ³ al final, asÃ­ que la captura con el render es una
  composiciÃ³n sobre una captura real del panel; el APK compila con el mismo layout.
- En el estÃ©reo real queda probar:
  1. `set-home-activity` y arranque tras reinicio.
  2. Reversa automÃ¡tica al meter marcha atrÃ¡s.
  3. `persist.can.speed` vs GPS.
  4. CÃ¡mara `com.tw.reverse` y guÃ­as.
  5. Mandos del volante y media.
  6. **Bluetooth real**: activar acceso a notificaciones y comprobar tÃ­tulo/carÃ¡tula.

---

## Notas

- El estÃ©reo es **Android 8.1 (API 27)**; la app usa `targetSdk 33` para evitar avisos
  de â€œapp antiguaâ€ en dispositivos modernos, pero sigue funcionando en API 27.
- No se requiere Play Store; se instala por ADB.
- Para desarrollo del CAN, revisar `General/ibiza-can-dump/README.md` y los APKs
  decompilados (`com.tw.carinfoservice`, `cn.cardoor.cardashboard`, `com.tw.service.xt`).

---

## Lienzo continuo (v2)

La app ya no abre pantallas: **una sola Activity** mantiene vivo el mapa y cambia el foco entre
Cockpit (mapa 55 % / panel 45 %), Navegación (80/20) y módulos (25/75). El dock, el HUD y el
estado de foco se comparten entre todas las capas.

| Foco | Mapa | Panel | Captura |
|---|---|---|---|
| Cockpit | 55 % | 45 % | screenshots/final_cockpit.png |
| Navegación | 80 % | 20 % | screenshots/final_nav.png |
| Media Center | 25 % | 75 % | screenshots/final_media.png |
| Vehículo | 25 % | 75 % | screenshots/final_vehicle.png |

Especificación completa (tokens, estados, contratos de datos, mapeo AIDL, UI de telemetría/radio,
presupuesto de rendimiento): **[docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md](docs/IBIZA_AUTOMOTIVE_SUITE_SPEC.md)**.

Handoff completo para continuar el desarrollo (qué hace/no hace, menús nativos,
transiciones, canales de datos y estado real de pruebas):
**[docs/IBIZA_LAUNCHER_HANDOFF.md](docs/IBIZA_LAUNCHER_HANDOFF.md)**.

> Medición en emulador (1280×720/160 dpi): **PSS 60.3 MB**, **270 vistas**, APK **1.23 MB**.
> La validación de 60 fps debe hacerse en el estéreo con dumpsys gfxinfo.

---

## Cat?logo musical offline (m?dulo listo, sin conectar a la UI)

Se agreg? el paquete `com.chronosbvrx.ibizadash.music` con el lector del cat?logo
generado por `General/MusicCatalog/build_offline_catalog.py`:

```
app/src/main/java/com/chronosbvrx/ibizadash/music/
??? OfflineMusicCatalog.kt   # abre catalog.db en s?lo lectura, lookup y stats
??? TextNormalizer.kt       # normalizaci?n espejo del generador
??? CoverCache.kt           # LRU de 8 MB, RGB_565, decode en un hilo
??? CatalogModels.kt
```

Uso futuro desde el launcher:

```kotlin
OfflineMusicCatalog.initialize(context)
val match = OfflineMusicCatalog.lookup(artist, title)
OfflineMusicCatalog.loadCover(match) { bitmap -> imageView.setImageBitmap(bitmap) }
```

La ruta por defecto es
`/sdcard/Android/data/com.chronosbvrx.ibizadash/files/music_catalog/` y se llena con
`MusicCatalog/push_catalog.ps1`. No requiere Internet ni permisos de almacenamiento.
El m?dulo no est? cableado a `MainActivity` ni al panel de media todav?a; queda listo
para que el launcher lo consuma cuando se decida.

---

## Media Bluetooth en el estéreo real

El audio del teléfono llega por **A2DP sink** y el metadato por **AVRCP controller**.
La `MediaSession` de `com.android.bluetooth` existe pero llega vacía en esta unidad; la fuente real es el
broadcast **`com.android.getelementattrrsp`** con `trackTitle`, `artist` y `album`.

| Acción | Vía |
|---|---|
| Título / artista / álbum | `com.android.getelementattrrsp` (broadcast) |
| Estado play/pausa | `TRACK_EVENT` → `PlaybackState` (2 = pausa, 3 = play) |
| Siguiente / anterior | AIDL `com.tw.service.xt` tx **25 / 24** |
| Play / pausa | AVRCP passthrough **68 / 70** (`BluetoothAvrcpController`) |
| Arranque sin evento | caché en `SharedPreferences("bt_media")` si el A2DP sigue conectado |

La carátula **no** la transfiere este stack; primero se busca en el **catálogo offline**
(5000 álbumes, 37560 canciones, portadas WebP 300×300 en `music_catalog/`) y, si no hay
coincidencia, `AlbumArtClient` la busca en la iTunes Search API por
artista+álbum (fallback al título), descarga 600×600 y la cachea en memoria (LRU 12 MB) y disco
(`cacheDir/covers`). Se puede desactivar en **Ajustes rápidos → Carátulas online**.

Archivos: `media/BluetoothMediaReceiver.kt`, `car/AvrcpController.kt`, `car/TwCommandBinder.kt`.

---

## OBD-II (ELM327)

El módulo Vehículo incluye un cliente OBD-II por Bluetooth SPP. El adaptador debe estar
emparejado en Ajustes → Bluetooth y el contacto del coche puesto. Al tocar el chip **OBD**
se abre el selector de dispositivo, la búsqueda automática, el modo demo y el acceso a los
ajustes Bluetooth.

- PIDs: RPM, carga, acelerador, refrigerante, admisión, velocidad, combustible, batería,
  MIL/DTC y VIN.
- Para probar sin adaptador: `adb shell am start -n com.chronosbvrx.ibizadash/.MainActivity --ez obd_demo true`
  o el chip OBD → “Modo demo (simulado)”.
- El CAN box del coche no entregó datos de motor en las pruebas; el dashboard nativo
  (DashMate) está en modo demo esperando un Superbox. Ver el handoff para el detalle.

---

## Catálogo offline de música

El otro agente generó un catálogo de 5000 álbumes con portadas WebP 300×300 (5.38 MB de base +
54.5 MB de portadas). El módulo receptor (`music/`) ya está cableado en el launcher:

- `App.onCreate` → `OfflineMusicCatalog.initialize(context)` (abre `music_catalog/catalog.db` en sólo lectura).
- `BluetoothMediaReceiver` busca primero en el catálogo y, si no hay coincidencia, cae a iTunes.
- `CoverCache` decodifica en un hilo con LRU de 8 MB (RGB_565).
- Regla `-keep` en `proguard-rules.pro` para que R8 no elimine el módulo.

Para subirlo al estéreo (cuando esté conectado por ADB):
```powershell
cd "C:\Users\Axel Rosete\Desktop\Proyectos\General\MusicCatalog"
.\push_catalog.ps1 -Serial 10.114.78.182:5555
```
Ruta: `/sdcard/Android/data/com.chronosbvrx.ibizadash/files/music_catalog/`.
