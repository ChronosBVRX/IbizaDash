package com.chronosbvrx.ibizadash.music

import java.text.Normalizer
import java.util.Locale

/**
 * Normalización compartida con `build_offline_catalog.py`.
 *
 * Si se cambia aquí, hay que cambiar `clean_text()`, `title_key()` y
 * `artist_key()` en el generador y volver a generar `catalog.db`.
 */
object TextNormalizer {

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val WHITESPACE = Regex("\\s+")

    private val BRACKET = Regex("""[\(\[\{]([^\)\]\}]*)[\)\]\}]""")
    private val NOISE = Regex(
        """\b(feat\.?|featuring|ft\.?|with|remaster(?:ed)?|radio\s+edit|""" +
            """single\s+version|album\s+version|live|en\s+vivo|explicit|clean|""" +
            """deluxe|bonus|acoustic|demo|remix|from\s+the|soundtrack|""" +
            """sped\s+up|slowed)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val DASH_NOISE = Regex(
        """\s+-\s+(?:feat\.?|featuring|ft\.?|remaster(?:ed)?|radio\s+edit|""" +
            """single\s+version|album\s+version|live|en\s+vivo|explicit|clean|""" +
            """deluxe|bonus|acoustic|demo|remix|from\s+the|soundtrack|""" +
            """sped\s+up|slowed)\b.*$""",
        RegexOption.IGNORE_CASE,
    )
    private val FEAT = Regex(
        """\b(?:feat\.?|featuring|ft\.?)\b.*$""",
        RegexOption.IGNORE_CASE,
    )

    fun clean(value: String?): String {
        if (value.isNullOrEmpty()) return ""
        var text = value
            .replace("æ", "ae").replace("Æ", "ae")
            .replace("œ", "oe").replace("Œ", "oe")
            .replace("ø", "o").replace("Ø", "o")
            .replace("đ", "d").replace("Đ", "d")
            .replace("ł", "l").replace("Ł", "l")
            .replace("ß", "ss")
        text = Normalizer.normalize(text, Normalizer.Form.NFKD)
        text = MARKS.replace(text, "")
        text = text.lowercase(Locale.ROOT)
        text = text.replace("&", " and ").replace("+", " and ")
        text = text
            .replace('\u2019', '\'')
            .replace('\u2018', '\'')
            .replace('\u201b', '\'')
            .replace('\u2032', '\'')
            .replace('`', '\'')
            .replace('\u00b4', '\'')
            .replace('\u02bc', '\'')
            .replace("'", "")
        text = NON_ALNUM.replace(text, " ")
        return WHITESPACE.replace(text, " ").trim()
    }

    fun title(value: String?): String {
        var text = value.orEmpty()
        text = BRACKET.replace(text) { match ->
            if (NOISE.containsMatchIn(match.groupValues[1])) " " else match.value
        }
        text = DASH_NOISE.replace(text, " ")
        text = FEAT.replace(text, " ")
        return clean(text)
    }

    fun artist(value: String?): String = clean(FEAT.replace(value.orEmpty(), " "))

    /** Sólo para depuración; el hash de portada se genera en Python. */
    fun album(value: String?): String = clean(value)
}
