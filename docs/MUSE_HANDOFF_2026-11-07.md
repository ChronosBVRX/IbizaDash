# Handoff — IbizaDash v0.6.0 / IbizaCompanion v0.5.0 (Muse, 2026-11-07)

Documento de traspaso para el agente de la laptop y el equipo. Todo el trabajo
de esta sesión está en esta rama; `main` sigue en v0.5.0 (commit `081bfe8`).

## Qué se hizo

- **Revisión UX del launcher** (2026-11-07) y todas sus mejoras implementadas:
  estéreo **v0.6.0 (versionCode 6)**, compañera **v0.5.0 (versionCode 5)**.
  Cambios: chip TELÉFONO/LOCAL sobre el mapa, botones de llamada 56 dp,
  botones de ajustes/mini-HUD 48 dp, chips ocultos cuando no hay dato
  (combustible, fila de salud), microcopy "Bluetooth · Radio FM", fps
  adaptativo del mapa (2 fps parado / 3 fps > 30 km/h), envío de frames bajo
  el servicio en primer plano de la compañera, selector de launcher por
  defecto en Ajustes rápidos, cápsula no modal de aviso OTA en el estéreo.
- **OTA v1** (nuevo): la compañera lleva el APK del estéreo en
  `assets/ibizadash.apk` y lo envía por el puente Wi-Fi Direct existente
  (`APK_META` / `APK_CHUNK` 32 KB base64 con ACK por chunk / `APK_END` con
  sha256). El estéreo lo verifica y lo instala con PackageInstaller
  (`ota/OtaReceiver.kt`, `ota/OtaInstaller.kt`, `res/xml/file_paths.xml`;
  compañera: `ota/OtaSender.kt`, UI en la pantalla de Conexión
  "Actualizar estéreo"). La primera instalación de v0.6.0 todavía requiere
  ADB (el estéreo viejo no tiene receptor OTA); después, las actualizaciones
  van teléfono → estéreo sin ADB.

## Estado actual (2026-11-07 noche) — LEER ANTES DE TOCAR NADA

- **Estéreo: EN LOOP (posible bootloop del launcher).** Tras desinstalar el
  IbizaDash viejo (v0.5.0 debug) el estéreo entró en un loop. Causa probable:
  el IbizaDash viejo era el launcher HOME por defecto y al desinstalarlo el
  sistema se quedó sin home. Muse NUNCA ejecutó ningún comando en el estéreo
  (el ADB nunca autorizó, ver abajo), así que el loop no vino de un comando
  de Muse; el desencadenante fue la desinstalación indicada por Muse.
  Recuperación (desde la laptop, donde el diálogo RSA sí aparece):
  `adb connect 192.168.8.118:5555`, aceptar el diálogo en el estéreo,
  reinstalar un launcher (`adb install` del APK v0.6.0 o de LiteDash) y
  fijarlo como home:
  `adb shell cmd package set-home-activity com.chronosbvrx.ibizadash/.MainActivity`.
  Si ni así arranca, último recurso: factory reset desde recovery.
- **Estéreo v0.6.0: NUNCA instalado.** El APK está construido y verificado
  (ver "APKs construidos"). IP del estéreo: **192.168.8.118**, ADB puerto 5555.
- **Compañera v0.5.0: NUNCA instalada en el POCO.** La v0.4.0 oficial fue
  desinstalada (era debug-signed; la v0.5.0 es release-signed y Android no
  deja actualizar entre firmas distintas). El APK nuevo está listo y
  verificado; falta instalarlo por ADB (ver "Teléfono").
- **ADB al estéreo desde el teléfono (Termux) NO autoriza.** El estéreo
  responde en 192.168.8.118:5555 pero nunca muestra el diálogo
  "Permitir depuración USB", ni con llave nueva ni tras revocar
  autorizaciones (24 reintentos). Desde una PC/laptop el diálogo SÍ aparece.
  Por eso el trabajo pasa al agente de la laptop.
- **Teléfono (POCO F7):** su Depuración inalámbrica cambió de puerto al
  cambiar de Wi-Fi (el puerto viejo 42789 ya no responde). Para instalar la
  compañera hay que leer el puerto nuevo en el teléfono
  (Depuración inalámbrica → "Dirección IP y puerto") y reconectar por ADB.
  Además hace falta "Instalar vía USB" activado en Opciones de desarrollador
  (sin eso `pm install` da `INSTALL_FAILED_USER_RESTRICTED`). Y `pm install`
  no puede leer de `/sdcard/Download` (SELinux): hay que copiar el APK a
  `/data/local/tmp/` e instalar desde ahí.

## APKs construidos (firmados con llave release, ver "Firma")

- Estéreo `IbizaDash-manual-v0.6.0.apk` — 4.114.542 bytes,
  sha256 `e4f3407f95d052c2e85ead8f00b2c988135e4da8fdb2240381769192f88be699`
- Compañera `IbizaCompanion-manual-v0.5.0.apk` — 26.800.078 bytes,
  sha256 `7731b5c1c39e294af600ee6f0e13596f910b5576182bfd903473b0e6fef644a0`
  (su `assets/ibizadash.apk` es byte-idéntico al estéreo v0.6.0)
- Descargas (enlaces de Muse, caducan 2026-11-10 01:00 UTC):
  estéreo: https://muse.ai/files/1149228654947775/1496846269228097/mbiw3qeumd66i3ttsp5uu615/IbizaDash-manual-v0.6.0.apk
  compañera: https://muse.ai/files/1149228654947775/931102379939075/kq03wmjn899091l7rwl8ivl4/IbizaCompanion-manual-v0.5.0.apk
- Los APK **no** van en Git (regla del README: van en Releases). Esta rama
  solo lleva el código. Si se quiere publicar, crear el Release v0.6.0 con
  `gh release create v0.6.0 --title "IbizaDash v0.6.0" <apk>`.

## Firma

Ambas APKs están firmadas con una llave release generada por Muse
(cert SHA-256 `e2bc66ea6c506543b5fd6d8f6ed59baa21c2b067d9b8bd34aebd89b56e0eb217`).
**El keystore NO está en este repo** (nunca se suben llaves): vive en la VM
de Muse y no se puede recuperar desde aquí. Consecuencias:
- Para instalar v0.6.0 sobre el estéreo no hay problema (el viejo ya está
  desinstalado).
- Para futuras actualizaciones OTA o por ADB sobre ESTA v0.6.0, hará falta
  la misma llave o desinstalar y reinstalar. Se recomienda generar un
  keystore propio del equipo y re-firmar los próximos builds con él.

## Bugs encontrados y arreglados (en el tooling de build manual de Muse, NO en este código)

El código del repo estaba bien; los fallos estaban en el merger de manifests
de Muse (`merge_manifest.py`, fuera del repo). Se documentan para no
repetirlos si alguien vuelve a compilar sin Gradle:
1. Placeholders `${applicationId}`, `${redirectSchemeName}`,
   `${redirectHostName}` sin sustituir → APK con autoridades/permisos
   literales inválidos → "error durante el análisis del paquete".
2. `<meta-data android:name="androidx.emoji2.text.EmojiCompatInitializer"/>`
   sin `android:value` ni `android:resource` (viene de
   `emoji2-bundled` con `tools:node="remove"` que el merger ignoraba) →
   `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED` (línea 65). El error exacto
   solo se ve con `adb install` / `pm install`, no en el diálogo del
   instalador. Ambos APKs actuales ya pasan estas verificaciones
   (0 placeholders, 0 meta-data sin value/resource, apksigner OK).

## Compilación

En una máquina normal, compilar con Gradle como siempre
(`gradlew :app:assembleRelease`, compañera con el wrapper de la raíz).
El entorno de Muse tenía Gradle bloqueado (el sandbox niega el loopback TCP
del daemon) y por eso se compiló a mano con kotlinc + aapt2 + d8; esos
scripts NO están en este repo y no hacen falta en la laptop.

## Archivos de esta rama

- `app/...` — mejoras UX del estéreo + OTA (`ota/`, `res/xml/file_paths.xml`)
- `companion-app/...` — mejoras UX de la compañera + OTA (`ota/OtaSender.kt`,
  envío de frames bajo foreground service, UI de actualización)
- `docs/MUSE_HANDOFF_2026-11-07.md` — este documento
