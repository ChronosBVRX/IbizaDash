package com.chronosbvrx.ibizacompanion.obd

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
        if (lines.isEmpty()) "(sin tráfico todavía)"
        else lines.joinToString("\n")
    }

    fun info(line: String) = add(line)

    fun clear() = synchronized(lock) { lines.clear() }

    private fun add(line: String) = synchronized(lock) {
        lines.addLast(line)
        while (lines.size > MAX) lines.removeFirst()
    }
}
