# Prueba en carretera — IbizaDash 1.4.0 + IbizaCompanion 1.4.0

Checklist para hacer en el coche, en orden. Marca cada punto y, si algo falla, captura lo
que se indica al final.

## Antes de arrancar (2 minutos)

1. **Actualiza el estéreo** con el enlace corto o por OTA. En **Ajustes rápidos** debe verse
   la versión instalada **1.4.0**.
2. **Actualiza el teléfono** (se actualiza solo en Ajustes → *Buscar ahora*). Versión **1.4.0**.
3. En el teléfono: **Ajustes → TRÁFICO EN TIEMPO REAL → Añadir clave** y pega la clave de
   TomTom. Debe quedar “Clave configurada”.
4. En el teléfono: **Ajustes → Módulos → Instalar** en *Mapas de Morelia y alrededores*
   (13,7 MB). Espera a “Instalado en el estéreo”.
5. En el estéreo: **Ajustes rápidos → Módulos del estéreo** debe decir **2 · 13,7 MB**
   (o 1 si quitamos el de prueba).

## Módulo grande (ya validado en emulador, se repite aquí)

| Medida | Valor de referencia |
|---|---|
| Descarga en el teléfono (13,7 MB) | ~10 s con buena conexión |
| Envío por Wi-Fi Direct | ~3 s (loopback); en el coche, 20–60 s |
| Instalación (2464 archivos) | ~1 s |
| Resultado | mapa visible sin conexión tras reiniciar la app |

**Qué comprobar:** con el teléfono en modo avión y el estéreo sin WiFi, el mapa debe seguir
pintando calles en Morelia a zoom de calle (z16).

## Tráfico real

1. Planifica una ruta por una avenida principal en hora punta.
2. En el estéreo pulsa **MAPA**: debe aparecer la tarjeta con ETA.
3. Si hay congestión, la ruta muestra **tramos en rojo/ámbar** y el chip **TRÁFICO +X MIN**.
4. Si te sales de la ruta, debe decir *“Fuera de ruta · recalculando”* y recalcular en ~30 s.

**Qué comprobar:** que la ETA del estéreo coincida con la del teléfono (±1 min).

## Voz

1. Con ruta activa, circula hacia un giro.
2. Debe sonar **“En cuatrocientos metros”** y luego la maniobra, **bajando la música**.
3. Al llegar: **“Has llegado a tu destino”**.

**Qué comprobar:** que no se repita el mismo aviso y que la música vuelva a su volumen.

## Seguimiento del mapa

1. Circula 300 m: el mapa debe **seguir al coche** y girar con el rumbo por encima de 12 km/h.
2. Arrastra el mapa con el dedo: aparece el botón **Centrar** y deja de seguir.
3. Suelta y espera **10 s**: vuelve a seguir solo.
4. Parado: el mapa debe quedar **norte arriba**.

## Si algo falla, envíame esto

Conecta el estéreo por ADB y ejecuta:

```powershell
$adb = "C:\Users\Axel Rosete\AppData\Local\Android\Sdk\platform-tools\adb.exe"
& $adb connect <IP_ESTEREO>:5555
& $adb -s <IP_ESTEREO>:5555 logcat -d -s NavBridge RouteNav Modules VoicePlayer VoiceGuidance ObdElm327 > estereo.txt
```

Y en el teléfono (por USB):

```powershell
& $adb -s <SERIAL> logcat -d -s Modules TomTomRouting VoiceTTS WifiBridge > telefono.txt
```

Con eso puedo ver exactamente dónde se corta.
