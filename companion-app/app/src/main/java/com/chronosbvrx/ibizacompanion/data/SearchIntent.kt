package com.chronosbvrx.ibizacompanion.data

import java.text.Normalizer
import java.util.Locale

/**
 * Convierte intenciones cotidianas de México en consultas que los geocodificadores
 * entienden mejor. Solo actúa cuando la consulta completa es una intención
 * ("comer", "gasolina", "vulcanizadora"…); si el usuario escribe un nombre o una
 * dirección, se respeta tal cual.
 */
object SearchIntent {
    private val map: Map<String, String> = mapOf(
        // Comida
        "comer" to "restaurante",
        "comida" to "restaurante",
        "cenar" to "restaurante",
        "comida corrida" to "cocina económica",
        "cocina economica" to "cocina económica",
        "fonda" to "cocina económica",
        "antojitos" to "antojitos mexicanos",
        "desayunar" to "cafetería",
        "desayuno" to "cafetería",
        "cafe" to "cafetería",
        "cafeteria" to "cafetería",
        "pan" to "panadería",
        "panaderia" to "panadería",
        "pastel" to "pastelería",
        "pasteleria" to "pastelería",
        "helado" to "heladería",
        "tacos" to "taquería",
        "taqueria" to "taquería",
        "tortas" to "tortería",
        "tortas ahogadas" to "tortas ahogadas",
        "pizza" to "pizzería",
        "hamburguesa" to "hamburguesería",
        "sushi" to "sushi",
        "mariscos" to "marisquería",
        "pescado" to "marisquería",
        "pollo" to "pollería",
        "carne" to "carnicería",
        "carniceria" to "carnicería",
        "tortillas" to "tortillería",
        "tortilleria" to "tortillería",
        "cerveza" to "cervecería",
        "chela" to "cervecería",
        "bar" to "bar",
        "cantina" to "cantina",

        // Compras y servicios
        "mandado" to "supermercado",
        "despensa" to "supermercado",
        "super" to "supermercado",
        "supermercado" to "supermercado",
        "tiendita" to "tienda de abarrotes",
        "abarrotes" to "tienda de abarrotes",
        "tienda" to "tienda",
        "mercado" to "mercado",
        "tianguis" to "tianguis",
        "plaza" to "centro comercial",
        "centro comercial" to "centro comercial",
        "ferreteria" to "ferretería",
        "papeleria" to "papelería",
        "lavanderia" to "lavandería",
        "flores" to "florería",
        "floreria" to "florería",
        "regalo" to "tienda de regalos",

        // Auto
        "gasolina" to "gasolinera",
        "gas" to "gasolinera",
        "combustible" to "gasolinera",
        "gasolinera" to "gasolinera",
        "taller" to "taller mecánico",
        "mecanico" to "taller mecánico",
        "taller mecanico" to "taller mecánico",
        "llantas" to "taller de llantas",
        "llanta" to "taller de llantas",
        "vulcanizadora" to "taller de llantas",
        "ponchada" to "taller de llantas",
        "ponchar" to "taller de llantas",
        "lavado" to "lavado de autos",
        "car wash" to "lavado de autos",
        "estacionamiento" to "estacionamiento",
        "parking" to "estacionamiento",
        "grua" to "grúa",

        // Salud y trámites
        "farmacia" to "farmacia",
        "medicinas" to "farmacia",
        "medicamento" to "farmacia",
        "doctor" to "hospital",
        "hospital" to "hospital",
        "urgencias" to "hospital",
        "clinica" to "clínica",
        "dentista" to "dentista",
        "optica" to "óptica",
        "veterinaria" to "veterinaria",
        "banco" to "banco",
        "cajero" to "cajero automático",
        "dinero" to "cajero automático",
        "correo" to "correos",
        "paqueteria" to "paquetería",
        "policia" to "policía",

        // Ocio y viaje
        "hotel" to "hotel",
        "motel" to "motel",
        "dormir" to "hotel",
        "cine" to "cine",
        "parque" to "parque",
        "playa" to "playa",
        "balneario" to "balneario",
        "gimnasio" to "gimnasio",
        "escuela" to "escuela",
        "colegio" to "colegio",
        "universidad" to "universidad"
    )

    fun expand(query: String): String {
        val raw = query.trim()
        if (raw.isEmpty()) return raw
        return map[normalize(raw)] ?: raw
    }

    fun isCategory(query: String): Boolean = map.containsKey(normalize(query))

    /** Minúsculas, sin acentos y con espacios colapsados. */
    fun normalize(text: String): String {
        val base = Normalizer.normalize(text.lowercase(Locale("es", "MX")), Normalizer.Form.NFD)
        return base.replace(Regex("\\p{M}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
