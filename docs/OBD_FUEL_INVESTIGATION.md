# OBD en el Ibiza 6J — velocidad y gasolina (histórico)

> **Retirado de la app.** El nivel de gasolina nunca respondió en este coche y
> el sondeo UDS (`ATZ`) desestabilizaba el ELM327. La velocidad OBD tampoco
> responde (`010D`): ahora la velocidad sale solo del GPS. Este documento queda
> como referencia de la investigación.
>
> Investigación aplicada al SEAT Ibiza 6J 2010 (2.0 MPI 8V CEKA) con adaptador
> ELM327/B16. Objetivo: recuperar las lecturas que se perdieron al introducir
> la máscara de PIDs y explicar de dónde sale el nivel de gasolina.

## 0. Adaptador del usuario y compatibilidad

Adaptador declarado: **MINI ELM327 Bluetooth V1.5** (hardware 1.5, software
2.1), Bluetooth clásico SPP. Protocolos declarados: SAE J1850 PWM/VPW,
ISO 9141-2, ISO 14230-4 KWP y **ISO 15765-4 CAN 11/29 bits a 500/250 kbaud**.

El Ibiza 6J 2010 usa **ISO 15765-4 CAN 11 bits / 500 kbaud**, que está en la
lista. La conexión OBD es compatible. La app usa SPP clásico para este modelo
(no BLE); el chip OBD debe mostrar `ATI` = `ELM327 v1.5` y `ATDPN` = `A6`
(auto, CAN 11/500) en la consola.

La lista de live data del producto incluye velocidad del vehículo, pero **no
incluye nivel de combustible**. Eso confirma que el nivel no llega por el PID
estándar del motor en este coche: hay que pedírselo al cuadro por UDS.

## 1. Por qué desaparecieron velocidad y otras lecturas

La regresión llegó con el commit `ecf9f74` ("consultar solo PIDs soportados"):
se leía la máscara `0100/0120/0140` y se omitían los PIDs que no aparecían.
En clones ELM327 y en respuestas multi-ECU esa máscara llega incompleta.

Referencia: `python-OBD` issue #225 documenta que `010D` (Vehicle Speed) puede
figurar como “no soportado” aunque el coche sí entregue velocidad; la solución
fue forzar protocolo y ajustar el timeout de la consulta inicial. En este
proyecto la máscara se parsea con una sola respuesta y no distingue ECUs, por
lo que `010D` y `012F` podían quedar fuera.

**Cambio:** la máscara se conserva solo para el log de diagnóstico. La app
vuelve a consultar todos los PIDs (fail-open), como antes de la regresión.
Además se ampliaron los TTL: rápidos 10 s y lentos 120 s, y los lentos se
consultan cada 2 ciclos.

## 2. De dónde sale la gasolina en este coche

El motor de este Ibiza no entrega de forma fiable el PID estándar `012F`.
El nivel vive en el **cuadro de instrumentos (dirección 17)**, que en el 6J es
UDS VDO:

- ASAM/ODX: `EV_Kombi_UDS_VDD_RM09`
- CAN petición: `0x714`
- CAN respuesta: `0x77E`
- Servicio UDS: `0x22` (ReadDataByIdentifier)
- DID: `0x2206`
- Respuesta: `04 62 22 06 2C AA AA AA` → `0x2C` = **44 litros**

Referencias:
- Ross-Tech Forums, escaneo de un Ibiza 6J 2009: Address 17,
  `KOMBI 017 0018`, ASAM `EV_Kombi_UDS_VDD_RM09 A04020`.
- HackMag, “Turn Your Smartphone into a Car Instrument Cluster Using CAN Bus
  and OBD-II”: secuencia `714 03 22 22 06 55 55 55 55` → `77E 04 62 22 06 2C…`
  = 44 L. También documenta puertas (`220D`) y temperatura de aceite (`202F`).
- Ross-Tech/VCDS: el valor aparece como `IDE00304 Fuel level` en litros.

## 3. Secuencia ELM327 que funciona

La petición auto-formateada no basta en muchos clones. La secuencia que
documenta HackMag y que ahora usa la app es:

```
ATSP6          # ISO 15765-4 CAN 11/500
ATSH714        # cabecera de petición al cuadro
ATCRA77E       # filtro de respuesta del cuadro
ATFCSH714      # flow control hacia 0x714
ATFCSD300000   # flow control: 30 00 00
ATFCSM1        # flow control definido por el usuario
ATAL           # permitir mensajes largos
ATCAF0         # desactivar auto-formato CAN
03 22 22 06 55 55 55 55
```

Después se restaura `ATFCSM0`, `ATCAF1`, `ATCRA7E8`, `ATSH7DF`, `ATSP6` y
`ATH0` para no dejar el adaptador en modo UDS.

## 4. Respaldo en la UI

Si el cuadro aún no responde (contacto apagado, clon sin flow control, etc.):

- la tarjeta de combustible muestra la **autonomía estimada** por los botones
  de tanque/odómetro local, marcada como “estimada”;
- la página Motor añade una tarjeta **COMBUSTIBLE** con porcentaje/litros si
  hay lectura UDS, o autonomía estimada si no;
- la consola OBD conserva las pruebas `012F`, `UDS 0x2206` y el escaneo de DIDs.

Nunca se inventa un porcentaje: si no hay lectura real ni autonomía estimada,
se muestra `--`.
