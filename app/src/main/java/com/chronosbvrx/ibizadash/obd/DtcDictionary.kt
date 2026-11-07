package com.chronosbvrx.ibizadash.obd

/**
 * Diccionario de fallas ajustado al coche del proyecto:
 * **SEAT Ibiza 6J 2010, 2.0 MPI 8V, 115 hp, código de motor CEKA**
 * (versión para México).
 *
 * Incluye:
 *  - Códigos genéricos SAE P0xxx que esta ECU puede memorizar.
 *  - Códigos específicos VAG P1xxx que aparecen en este motor.
 *  - Conversión de los códigos VAG de 5 dígitos (VCDS) a su P equivalente
 *    cuando la relación es conocida.
 *
 * Las explicaciones priorizan lo que de verdad falla en este motor:
 * bobina/cables/bujías, fugas de admisión y PCV, cuerpo de aceleración,
 * válvula N80 del EVAP, termostato, sensores G40/G28 y correa de distribución.
 */
object DtcDictionary {

    /** Identificación que se muestra en la UI. */
    const val MODEL_NAME = "SEAT Ibiza 6J 2010 · 2.0 MPI 8V (CEKA)"

    private val entries: Map<String, String> = mapOf(
        // -----------------------------------------------------------------
        // Distribución / sincronización
        // -----------------------------------------------------------------
        "P0016" to "Correlación cigüeñal/árbol de levas fuera de punto. " +
            "En el CEKA revisa primero la correa de distribución (saltada o tensor vencido), " +
            "la marca del árbol y los sensores G28 (cigüeñal) y G40 (levas).",

        // -----------------------------------------------------------------
        // Medición de aire
        // -----------------------------------------------------------------
        "P0100" to "Circuito del sensor MAF sin señal o intermitente. Revisa conector, cableado, " +
            "fusible y que el sensor tenga alimentación; si el motor anda bien pero el código sigue, el MAF está sucio o muerto.",
        "P0101" to "MAF fuera de rango: entra aire sin medir o el sensor no responde. " +
            "Revisa filtro de aire, mangueras de admisión, PCV y limpieza del MAF con limpiador específico.",
        "P0102" to "Señal baja del MAF: fuga de aire después del sensor, conector flojo o MAF defectuoso.",
        "P0103" to "Señal alta del MAF: restricción en la admisión, sensor sucio o cableado en corto.",
        "P0105" to "Circuito del sensor de presión de admisión (MAP). Revisa conector y manguera del sensor.",
        "P0106" to "MAP fuera de rango: manguera tapada o desconectada, fuga de vacío o sensor defectuoso.",
        "P0107" to "Señal baja del MAP: fuga de vacío, manguera rota o cableado.",
        "P0108" to "Señal alta del MAP: sensor o cableado; compáralo con la presión barométrica al arrancar.",
        "P0110" to "Circuito del sensor de temperatura de aire (IAT). Revisa conector y cableado; suele venir integrado en el MAF.",
        "P0111" to "IAT fuera de rango: sensor sucio/defectuoso o cableado. Revisa valores en frío y con motor caliente.",
        "P0112" to "IAT indica aire demasiado caliente: sensor o cableado; no cambia piezas sin verificar la lectura real.",
        "P0113" to "IAT indica aire demasiado frío (o circuito abierto): sensor desconectado, cable roto o sensor dañado.",
        "P0115" to "Circuito del sensor de temperatura del refrigerante (ECT). Revisa conector, cableado y masa del motor.",
        "P0116" to "ECT fuera de rango: termostato atascado, nivel bajo de anticongelante o sensor envejecido.",
        "P0117" to "ECT marca sobrecalentamiento: detente, revisa nivel, bomba de agua, termostato y ventiladores.",
        "P0118" to "ECT marca frío extremo: termostato abierto, sensor desconectado o cableado abierto.",
        "P0120" to "Circuito del sensor de posición del acelerador (TPS). Revisa conector del cuerpo de aceleración y cableado.",
        "P0121" to "TPS fuera de rango: cuerpo de aceleración sucio o desgastado. Limpia y haz adaptación con VCDS.",
        "P0122" to "TPS señal baja: conector, cableado o cuerpo de aceleración.",
        "P0123" to "TPS señal alta: conector, cableado o cuerpo de aceleración.",
        "P0125" to "El motor no alcanza la temperatura para entrar en lazo cerrado: termostato atascado abierto o ECT fuera de rango.",
        "P0128" to "Termostato por debajo de la temperatura de regulación. Es una falla muy común en el Ibiza: " +
            "cambia el termostato y verifica el ECT; no es peligroso, pero sube el consumo.",

        // -----------------------------------------------------------------
        // Sondas lambda y ajuste de combustible
        // -----------------------------------------------------------------
        "P0130" to "Circuito de la sonda lambda delantera (banco 1, sensor 1). Revisa conector, cableado y que el escape no tenga fugas antes de la sonda.",
        "P0131" to "Sonda delantera con señal baja (mezcla pobre o sonda desconectada). Revisa fugas de vacío y el cableado de la sonda.",
        "P0132" to "Sonda delantera con señal alta (mezcla rica o calefactor). Revisa inyectores, presión de gasolina y el MAF.",
        "P0133" to "Respuesta lenta de la sonda delantera: sonda envejecida o contaminada. Sustitúyela si tiene más de 100,000 km.",
        "P0134" to "Sonda delantera sin actividad: sonda, calefactor o fusible. No circules mucho así: la ECU pierde el control fino de mezcla.",
        "P0135" to "Calefactor de la sonda delantera. Revisa fusible, relevador y resistencia del calefactor.",
        "P0136" to "Circuito de la sonda trasera (banco 1, sensor 2). Revisa conector y cableado; es la que vigila el catalizador.",
        "P0137" to "Sonda trasera con señal baja: fuga de escape, sonda o cableado.",
        "P0138" to "Sonda trasera con señal alta: sonda o cableado; descarta primero fallos de encendido y consumo de aceite.",
        "P0139" to "Respuesta lenta de la sonda trasera: sonda envejecida o catalizador contaminado.",
        "P0140" to "Sonda trasera sin actividad: sonda, calefactor o fusible.",
        "P0141" to "Calefactor de la sonda trasera. Revisa fusible, relevador y resistencia.",
        "P0170" to "Ajuste de combustible del banco 1 fuera de rango. Revisa fugas de vacío, MAF sucio, presión de gasolina e inyectores.",
        "P0171" to "Mezcla pobre en banco 1: entra más aire del medido o falta gasolina. " +
            "En el CEKA revisa mangueras de admisión, PCV, junta del múltiple, MAF sucio, filtro/bomba de gasolina e inyectores.",
        "P0172" to "Mezcla rica en banco 1: sobra gasolina. Revisa inyector que gotea, regulador de presión, MAF y válvula EVAP atascada abierta.",
        "P0174" to "Mezcla pobre en banco 2. Este motor es de 4 cilindros y solo tiene banco 1: " +
            "si aparece, el escáner está leyendo mal o la ECU no corresponde a este coche.",
        "P0175" to "Mezcla rica en banco 2. No aplica al 2.0 de 4 cilindros (solo banco 1); verifica el escáner.",

        // -----------------------------------------------------------------
        // Inyección y bomba
        // -----------------------------------------------------------------
        "P0201" to "Circuito del inyector del cilindro 1. Revisa conector, cableado y resistencia del inyector.",
        "P0202" to "Circuito del inyector del cilindro 2. Revisa conector, cableado y resistencia del inyector.",
        "P0203" to "Circuito del inyector del cilindro 3. Revisa conector, cableado y resistencia del inyector.",
        "P0204" to "Circuito del inyector del cilindro 4. Revisa conector, cableado y resistencia del inyector.",
        "P0230" to "Circuito primario de la bomba de combustible. Revisa relevador, fusible, cableado y masa de la bomba.",
        "P0261" to "Inyector del cilindro 1 en corto a masa. Revisa cableado y resistencia del inyector.",
        "P0262" to "Inyector del cilindro 1 en corto a positivo. Revisa cableado y resistencia del inyector.",
        "P0263" to "Inyector del cilindro 1 con problema de balance/consumo. Revisa inyector y compresión de ese cilindro.",
        "P0264" to "Inyector del cilindro 2 en corto a masa. Revisa cableado y resistencia del inyector.",

        // -----------------------------------------------------------------
        // Encendido
        // -----------------------------------------------------------------
        "P0300" to "Fallos de encendido aleatorios. En el CEKA revisa primero bujías, bobina de encendido y cables; " +
            "después inyectores, compresión y fugas de admisión. Si el check parpadea, no sigas circulando: daña el catalizador.",
        "P0301" to "Fallo de encendido en cilindro 1. Revisa bujía, cable y bobina; si sigue, inyector y compresión.",
        "P0302" to "Fallo de encendido en cilindro 2. Revisa bujía, cable y bobina; si sigue, inyector y compresión.",
        "P0303" to "Fallo de encendido en cilindro 3. Revisa bujía, cable y bobina; si sigue, inyector y compresión.",
        "P0304" to "Fallo de encendido en cilindro 4. Revisa bujía, cable y bobina; si sigue, inyector y compresión.",
        "P0321" to "Señal del sensor de régimen (cigüeñal, G28) fuera de rango. Revisa conector, rueda fónica y cableado; " +
            "también puede ser correa de distribución o volante flojo.",
        "P0322" to "Sensor del cigüeñal (G28) sin señal: el motor no arranca o se apaga. Revisa conector, cableado y sensor.",
        "P0323" to "Sensor del cigüeñal (G28) intermitente: revisa conector, cable y suciedad en la rueda fónica.",
        "P0325" to "Circuito del sensor de detonación (knock) 1. Revisa conector, cableado y torque del sensor.",
        "P0326" to "Sensor de detonación fuera de rango: revisa su montaje, cableado y que no haya golpes en el bloque.",
        "P0327" to "Señal baja del sensor de detonación: conector, cableado o sensor.",
        "P0328" to "Señal alta del sensor de detonación: cableado o sensor.",
        "P0335" to "Circuito del sensor de posición del cigüeñal (A). Revisa conector, cableado y sensor G28.",
        "P0336" to "Cigüeñal fuera de rango: revisa rueda fónica, separación del sensor y correa de distribución.",
        "P0337" to "Señal baja del sensor del cigüeñal: cableado o sensor.",
        "P0338" to "Señal alta del sensor del cigüeñal: cableado o sensor.",
        "P0340" to "Circuito del sensor de árbol de levas (G40). Revisa conector, cableado y sensor; " +
            "en este motor también se dispara si la correa está fuera de punto.",
        "P0341" to "Árbol de levas fuera de rango: revisa la correa de distribución, el sensor G40 y su rueda.",
        "P0342" to "Señal baja del sensor de árbol de levas: cableado o sensor.",
        "P0343" to "Señal alta del sensor de árbol de levas: cableado o sensor.",
        "P0350" to "Circuito primario/secundario de la bobina de encendido. Revisa conector, cableado y bobina; " +
            "es una falla frecuente en el 2.0 y suele venir con fallos P030x.",
        "P0351" to "Bobina de encendido A (cilindro 1). Revisa bobina, cable y bujía.",
        "P0352" to "Bobina de encendido B (cilindro 2). Revisa bobina, cable y bujía.",
        "P0353" to "Bobina de encendido C (cilindro 3). Revisa bobina, cable y bujía.",
        "P0354" to "Bobina de encendido D (cilindro 4). Revisa bobina, cable y bujía.",

        // -----------------------------------------------------------------
        // Emisiones
        // -----------------------------------------------------------------
        "P0400" to "Flujo de EGR incorrecto. Este motor puede no traer EGR; verifica que tu versión lo tenga antes de cambiar piezas.",
        "P0401" to "Flujo de EGR insuficiente: válvula o conductos con carbonilla. Revisa si tu versión trae EGR.",
        "P0402" to "Flujo de EGR excesivo: válvula atascada abierta; revisa si tu versión trae EGR.",
        "P0403" to "Circuito de la válvula EGR. Revisa conector, cableado y válvula si el coche la tiene.",
        "P0410" to "Sistema de aire secundario. Si tu versión no trae bomba de aire, revisa que el código no venga de otra ECU o de una adaptación incorrecta.",
        "P0411" to "Flujo incorrecto del aire secundario: bomba, válvulas o mangueras (solo versiones con sistema).",
        "P0412" to "Circuito de la válvula de conmutación A del aire secundario (solo versiones con sistema).",
        "P0413" to "Válvula de conmutación A del aire secundario en circuito abierto (solo versiones con sistema).",
        "P0414" to "Válvula de conmutación A del aire secundario en corto (solo versiones con sistema).",
        "P0420" to "Catalizador por debajo del umbral de eficiencia (banco 1). Antes de cambiarlo descarta fallos de encendido, " +
            "mezcla rica/pobre, fugas de escape y consumo de aceite; luego revisa la sonda trasera y el catalizador.",
        "P0430" to "Catalizador del banco 2 ineficiente. No aplica a este motor de 4 cilindros (un solo banco); verifica el escáner.",
        "P0440" to "Sistema EVAP con falla general. Revisa el tapón de gasolina, mangueras del canister y la válvula N80.",
        "P0441" to "Purga EVAP con flujo incorrecto: la válvula N80 suele quedarse abierta o el canister está saturado. " +
            "Revisa N80, mangueras y tapón; es una falla típica del Ibiza.",
        "P0442" to "Fuga pequeña en el EVAP. Empieza por el tapón de gasolina; después mangueras, canister y N80. " +
            "Una prueba de humo lo encuentra rápido.",
        "P0443" to "Circuito de la válvula de purga EVAP (N80). Revisa conector, cableado y resistencia de la válvula.",
        "P0444" to "Válvula de purga EVAP (N80) en circuito abierto. Revisa conector y cableado; la válvula no está recibiendo señal.",
        "P0445" to "Válvula de purga EVAP (N80) en corto. Revisa cableado y válvula.",
        "P0446" to "Circuito de ventilación del canister. Revisa la válvula de venteo, conector y mangueras.",
        "P0447" to "Válvula de ventilación del canister en circuito abierto: conector, cableado o válvula.",
        "P0448" to "Válvula de ventilación del canister en corto: cableado o válvula.",
        "P0450" to "Sensor de presión del tanque de gasolina. Revisa conector, cableado y sellado del tapón.",
        "P0451" to "Sensor de presión del tanque fuera de rango: revisa tapón, mangueras y sensor.",
        "P0452" to "Señal baja del sensor de presión del tanque: cableado, sensor o fuga.",
        "P0453" to "Señal alta del sensor de presión del tanque: cableado o sensor.",
        "P0455" to "Fuga grande en el EVAP: revisa primero el tapón de gasolina, luego mangueras, canister y N80.",
        "P0456" to "Fuga muy pequeña en el EVAP: tapón, mangueras o válvula de venteo; requiere prueba de humo para localizarla.",
        "P0496" to "Flujo alto en el sistema de purga EVAP: válvula N80 atascada abierta; puede causar ralentí irregular.",
        "P0497" to "Flujo bajo en el sistema de purga EVAP: N80 tapada, manguera doblada o canister saturado.",

        // -----------------------------------------------------------------
        // Ralentí, velocidad y eléctrico
        // -----------------------------------------------------------------
        "P0500" to "Sensor de velocidad del vehículo (VSS). Revisa sensor, cableado y que el velocímetro marque.",
        "P0501" to "VSS fuera de rango: revisa sensor, conector y cableado.",
        "P0502" to "Señal baja del VSS: sensor o cableado.",
        "P0503" to "Señal intermitente del VSS: conector flojo o sensor.",
        "P0505" to "Falla del control de ralentí. En este motor revisa cuerpo de aceleración sucio, fugas de vacío y adaptación.",
        "P0506" to "Ralentí más bajo de lo esperado: cuerpo de aceleración sucio, fuga de vacío o PCV.",
        "P0507" to "Ralentí más alto de lo esperado: cuerpo de aceleración sucio, fuga de vacío, PCV o manguera de admisión. " +
            "Limpia el cuerpo y haz adaptación con VCDS.",
        "P0560" to "Voltaje del sistema fuera de rango. Revisa batería, alternador, terminales y masa.",
        "P0562" to "Voltaje del sistema bajo: batería descargada, alternador o cableado. Revisa la carga con motor en marcha (13.2–14.8 V).",
        "P0563" to "Voltaje del sistema alto: regulador del alternador o cableado.",
        "P0571" to "Circuito del interruptor de freno (A) fuera de rango. Es común en VAG: revisa el interruptor del pedal, " +
            "fusible y cableado; puede dejar el control de velocidad inactivo.",
        "P0600" to "Falla del enlace de comunicación serie interno de la ECU.",
        "P0601" to "Error de checksum en la memoria interna de la ECU. Puede requerir reprogramación o sustitución.",
        "P0602" to "ECU sin programación correcta. Revisa si hubo una reprogramación o batería baja durante el flasheo.",
        "P0603" to "Error de memoria de adaptación (KAM). Revisa alimentación permanente y masa de la ECU.",
        "P0604" to "Error de RAM interna de la ECU.",
        "P0605" to "Error de ROM interna de la ECU.",
        "P0606" to "Falla del procesador de la ECU. Revisa alimentación y masas antes de condenar la ECU.",
        "P0607" to "Rendimiento del módulo de control fuera de rango: revisa voltaje, masas y conexiones de la ECU.",
        "P0620" to "Circuito de control del alternador. Revisa alternador, conector y cableado.",
        "P0621" to "Circuito de la lámpara de carga del alternador.",
        "P0622" to "Circuito de campo del alternador: revisa conector y regulador.",
        "P0630" to "VIN no programado en la ECU. Revisa si la ECU fue sustituida o reprogramada.",
        "P0641" to "Voltaje de referencia de sensores A fuera de rango. Revisa el cableado de 5 V y los sensores que comparten esa referencia.",
        "P0642" to "Voltaje de referencia A bajo: corto a masa en el cableado o en un sensor.",
        "P0643" to "Voltaje de referencia A alto: corto a positivo en el cableado o en un sensor.",
        "P0650" to "Circuito de la lámpara MIL (check engine). Revisa el foco/led del tablero y el cableado si el check no enciende.",
        "P0700" to "La ECU de transmisión pide encender el check. Si tu coche es manual, " +
            "revisa que el código no venga de otra ECU; si es automática, lee también los códigos de la caja.",
        "P0705" to "Sensor de rango de la palanca (solo automática). Revisa el selector y su cableado.",
        "P0715" to "Sensor de velocidad de entrada de la transmisión (solo automática).",
        "P0720" to "Sensor de velocidad de salida de la transmisión (solo automática).",
        "P0730" to "Relación de engranaje incorrecta (solo automática): revisa nivel y presión de aceite de la caja.",
        "P0740" to "Circuito del embrague del convertidor (TCC, solo automática).",
        "P0741" to "Embrague del convertidor atascado abierto (solo automática).",
        "P0742" to "Embrague del convertidor atascado cerrado (solo automática).",
        "P0748" to "Solenoide de presión de la transmisión (solo automática).",
        "P0750" to "Solenoide de cambio A (solo automática).",
        "P0755" to "Solenoide de cambio B (solo automática).",
        "P0760" to "Solenoide de cambio C (solo automática).",

        // -----------------------------------------------------------------
        // Específicos VAG del 2.0 MPI CEKA
        // -----------------------------------------------------------------
        "P1127" to "Ajuste multiplicativo de combustible (a carga) demasiado RICO. Revisa MAF, presión de gasolina, " +
            "inyectores que gotean y filtro de aire.",
        "P1128" to "Ajuste multiplicativo de combustible (a carga) demasiado POBRE. Revisa MAF sucio, fugas de admisión, " +
            "presión/filtro de gasolina y PCV.",
        "P1136" to "Ajuste aditivo de combustible (al ralentí) demasiado POBRE. Es típico de fuga de vacío: " +
            "mangueras de admisión, PCV, junta del múltiple y servo de freno. También revisa MAF e inyectores.",
        "P1137" to "Ajuste aditivo de combustible (al ralentí) demasiado RICO. Revisa inyector que gotea, " +
            "válvula EVAP abierta, regulador de presión y MAF.",
        "P1141" to "Verificación cruzada de carga fuera de rango: la ECU compara MAF con presión de admisión y no coinciden. " +
            "Busca fuga de vacío, MAF sucio o sensor MAP.",
        "P1142" to "Verificación cruzada de carga por debajo del límite: fuga de aire no medida o MAF descalibrado.",
        "P1143" to "Verificación cruzada de carga por encima del límite: restricción de admisión o MAF descalibrado.",
        "P1296" to "Falla del sistema de refrigeración. En el Ibiza suele ser termostato atascado abierto, " +
            "sensor ECT o bomba de agua; revisa nivel y temperatura real con el motor en marcha.",
        "P1338" to "Sensor de árbol de levas (G40) abierto o en corto a positivo. Revisa conector, cableado y sensor; " +
            "si la correa está fuera de punto también aparece.",
        "P1340" to "Correlación incorrecta entre árbol de levas (G40) y cigüeñal (G28). " +
            "En el CEKA es señal de correa de distribución saltada, tensor vencido o sensor G40/G28; " +
            "no sigas circulando hasta revisar la sincronización.",
        "P1425" to "Válvula de purga EVAP (N80) en corto a masa. Revisa conector y cableado; la válvula puede estar pegada.",
        "P1426" to "Válvula de purga EVAP (N80) en circuito abierto. Revisa conector y cableado; " +
            "es una de las fallas más comunes de olor a gasolina y ralentí irregular.",
        "P1545" to "Falla del control del cuerpo de aceleración (mariposa electrónica). Limpia el cuerpo, revisa conector y cableado, " +
            "y haz adaptación con VCDS; si no responde, el cuerpo está dañado.",
        "P1570" to "Arranque bloqueado por el inmovilizador. Revisa la llave (otra llave), el lector del clausor, " +
            "la batería y el cableado; no es una falla del motor.",
        "P1602" to "Voltaje bajo en la terminal 30 (alimentación permanente). Revisa batería, fusibles, " +
            "terminales flojas y masa; suele aparecer tras una batería descargada.",
        "P2187" to "Mezcla pobre al ralentí (banco 1). Muy parecido a P0171: busca fugas de vacío, PCV, " +
            "mangueras de admisión y MAF sucio.",
        "P2279" to "Fuga en el sistema de admisión: entra aire no medido después del MAF. " +
            "Revisa mangueras, PCV, junta del múltiple y tubo de vacío del servo; una prueba de humo lo confirma."
    )

    /** Códigos VAG de taller (5 dígitos, otros módulos) que no son Pxxxx. */
    private val workshop: Map<String, String> = mapOf(
        "00513" to "Sensor de régimen del motor (G28). Es el mismo cigüeñal que P0335: revisa conector, cableado y rueda fónica.",
        "00515" to "Sensor de árbol de levas (G40). Equivale a P0340/P1338: revisa conector, cableado y sincronización de la correa.",
        "00522" to "Sensor de temperatura del refrigerante (G62). Equivale a P0115-P0118: revisa sensor, conector y nivel.",
        "00532" to "Voltaje de alimentación (B+) fuera de rango. Revisa batería, alternador, terminales y masas.",
        "00537" to "Regulación de la sonda lambda fuera de rango. Revisa fugas de admisión/escape, sonda delantera y presión de gasolina.",
        "00561" to "Adaptación de mezcla en el límite (aditiva o multiplicativa). Revisa fugas de vacío, MAF, PCV, presión de gasolina e inyectores.",
        "00575" to "Presión del colector de admisión fuera de rango. Revisa sensor MAP y mangueras de vacío.",
        "00588" to "Detonador de la bolsa de aire del conductor. No midas con multímetro: desconecta la batería y revisa conector/anillo del volante.",
        "00589" to "Detonador de la bolsa de aire del acompañante. Revisa conector y cableado con la batería desconectada.",
        "00668" to "Voltaje de la terminal 30 (alimentación permanente) bajo. Revisa batería, fusibles y terminales.",
        "00778" to "Sensor de ángulo de dirección (G85). Requiere calibración con VCDS tras alinear o cambiar la columna.",
        "01176" to "Llave no autorizada por el inmovilizador. Prueba con la segunda llave; revisa lector del clausor y batería.",
        "01177" to "ECU del motor no autorizada por el inmovilizador. Revisa codificación/emparejamiento con VCDS.",
        "01221" to "Sensor de impacto lateral del conductor (airbag). Revisa conector y cableado bajo el asiento.",
        "01222" to "Sensor de impacto lateral del acompañante (airbag). Revisa conector y cableado bajo el asiento.",
        "01314" to "Sin comunicación con la ECU del motor por CAN. Revisa fusibles, masas y cableado del bus.",
        "01316" to "Sin comunicación con la ECU de ABS por CAN. Revisa fusibles, masas y conector del ABS.",
        "01317" to "Sin comunicación con el cuadro de instrumentos por CAN. Revisa fusibles y conector del tablero.",
        "01336" to "Bus de datos de confort sin comunicación. Revisa módulo de confort, fusibles y cableado de puertas."
    )

    /** Códigos VAG de 5 dígitos que no se deducen con el offset habitual. */
    private val vag5: Map<Int, String> = mapOf(
        16514 to "P0130", 16515 to "P0131", 16516 to "P0132", 16517 to "P0133",
        16518 to "P0134", 16519 to "P0135", 16520 to "P0136", 16521 to "P0137",
        16522 to "P0138", 16523 to "P0139", 16524 to "P0140", 16525 to "P0141",
        16555 to "P0171", 16556 to "P0172",
        16684 to "P0300", 16685 to "P0301", 16686 to "P0302", 16687 to "P0303",
        16688 to "P0304",
        16804 to "P0420",
        16825 to "P0441", 16826 to "P0442", 16839 to "P0455",
        16891 to "P0507",
        16955 to "P0571",
        17535 to "P1127", 17536 to "P1128", 17544 to "P1136", 17545 to "P1137",
        17549 to "P1141", 17550 to "P1142", 17551 to "P1143",
        17704 to "P1296", 17746 to "P1338", 17748 to "P1340",
        17833 to "P1425", 17834 to "P1426",
        17953 to "P1545", 17978 to "P1570", 18010 to "P1602",
        18711 to "P2279"
    )

    /**
     * Explica un código crudo. Acepta "P0171", "0171", "17544" o cadenas
     * tipo "17746/P1338/004920" (formato VCDS).
     */
    fun explain(raw: String): String {
        val clean = raw.trim().uppercase()
        if (clean.isEmpty()) return "Sin código."
        val pCode = Regex("P[0-9]{4}").find(clean)?.value
        val five = Regex("(?<![0-9])([0-9]{5})(?![0-9])")
            .find(clean)?.groupValues?.getOrNull(1)
        if (pCode == null && five != null) {
            workshop[five]?.let { return "$five — $it" }
        }
        val fiveCode = five?.let { vagToP(it) }
        val resolved = pCode ?: fiveCode

        if (resolved != null) {
            val body = entries[resolved]
            if (body != null) {
                val label = when {
                    pCode != null && five != null -> "$five · $pCode"
                    pCode != null -> resolved
                    five != null -> "$five ($resolved)"
                    else -> resolved
                }
                return "$label — $body"
            }
            return formatUnknown(resolved)
        }

        // A 4-digit P code without the letter ("0171").
        if (clean.length == 4 && clean.all { it.isDigit() }) {
            return explain("P$clean")
        }

        // Unknown 5-digit VAG code.
        if (five != null) {
            return "$five — Código VAG de 5 dígitos no incluido en el diccionario del 6J. " +
                "Anótalo y búscalo junto con el Pxxxx que muestre VCDS."
        }
        return formatUnknown(clean)
    }

    /** Para el explorador de la UI: pares (código, explicación) ordenados. */
    fun all(): List<Pair<String, String>> =
        (entries.map { it.key to it.value } + workshop.map { it.key to it.value })
            .sortedBy { it.first }

    private fun vagToP(raw: String): String? {
        val n = raw.toIntOrNull() ?: return null
        vag5[n]?.let { return it }
        return when (n) {
            // 16514..17383 corresponden a P0000..P0999.
            in 16384..17383 -> "P%04d".format(n - 16384)
            // 17508..17999 corresponden a P1100..P1591.
            in 17508..17999 -> "P%04d".format(n - 16408)
            else -> null
        }
    }

    private fun formatUnknown(code: String): String {
        if (code.length != 5 || code[0] != 'P') {
            return "$code — No está en el diccionario del Ibiza 6J 2.0 (CEKA). " +
                "Anótalo y consúltalo con el manual o un escáner VCDS."
        }
        val letter = code[0]
        val system = when (letter) {
            'P' -> "motor/transmisión"
            'C' -> "chasis (frenos, dirección, suspensión)"
            'B' -> "carrocería (airbags, climatizador, puertas)"
            'U' -> "red/comunicación entre módulos"
            else -> "sistema desconocido"
        }
        val kind = when (code[1]) {
            '0' -> "genérico"
            '1' -> "específico del fabricante"
            '2' -> "genérico ampliado"
            '3' -> "reservado"
            else -> "de tipo desconocido"
        }
        val area = when (code[0] to code.substring(2, 3).toIntOrNull()) {
            'P' to 1 -> "medición de aire/combustible"
            'P' to 2 -> "medición de aire/combustible (inyectores)"
            'P' to 3 -> "encendido / fallos de combustión"
            'P' to 4 -> "emisiones (catalizador, EGR, EVAP)"
            'P' to 5 -> "ralentí y control de velocidad"
            'P' to 6 -> "ECU y salidas de control"
            'P' to 7, 'P' to 8, 'P' to 9 -> "transmisión"
            else -> "subsistema ${code.substring(2, 3)}"
        }
        return "$code — Falla de $system, código $kind, zona de $area. " +
            "No está en el diccionario local del 6J 2.0 (CEKA): búscalo como \"$code SEAT Ibiza\" antes de cambiar piezas."
    }
}
