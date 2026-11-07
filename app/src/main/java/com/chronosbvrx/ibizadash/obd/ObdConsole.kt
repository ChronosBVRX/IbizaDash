package com.chronosbvrx.ibizadash.obd

/**
 * Ring buffer Tx/Rx del bus OBD-II para la consola del panel de diagnóstico.
 * Guarda las últimas líneas enviadas al ELM327 (Tx) y recibidas (Rx).
 */
object ObdConsole {
    private const val MAX = 60
    private val lock = Any()
    private val lines = ArrayDeque<String>()

    fun tx(command: String) = add("> $command")

    fun rx(response: String) {
        val clean = response
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(110)
        if (clean.isNotEmpty()) add("< $clean")
    }

    fun snapshot(): String = synchronized(lock) {
        if (lines.isEmpty()) "(sin tráfico todavía — abre el panel con el motor en marcha)"
        else lines.joinToString("\n")
    }

    /** Línea informativa (conclusiones en lenguaje normal, prefijo =). */
    fun info(line: String) = add(line)

    fun clear() = synchronized(lock) { lines.clear() }

    private fun add(line: String) = synchronized(lock) {
        lines.addLast(line)
        while (lines.size > MAX) lines.removeFirst()
    }
}
