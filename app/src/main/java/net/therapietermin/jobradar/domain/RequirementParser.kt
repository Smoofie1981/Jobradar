package net.therapietermin.jobradar.domain

import net.therapietermin.jobradar.data.Job
import java.util.Locale

object RequirementParser {

    /**
     * Zeigt nur Anforderungen, die im Text als Voraussetzung/Kernanforderung
     * erkennbar formuliert sind. Keine KI-Erfindungen und keine Ableitung
     * aus dem Jobtitel.
     */
    fun mandatory(job: Job, maxItems: Int = 4): List<String> {
        val raw = job.description
            .replace("•", "\n")
            .replace("▪", "\n")
            .replace("►", "\n")
            .replace("·", "\n")
            .replace("–", "-")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (raw.isBlank()) return emptyList()

        val candidates = linkedSetOf<String>()

        val sentenceChunks = raw
            .split(
                Regex("""(?<=[.!?;:])\s+|\s+-\s+""")
            )
            .map { clean(it) }
            .filter { it.length in 18..380 }

        for (chunk in sentenceChunks) {
            if (isMandatoryChunk(chunk)) {
                candidates += shorten(chunk)
                if (candidates.size >= maxItems) break
            }
        }

        // Falls die Anzeige ohne brauchbare Satzzeichen geliefert wurde:
        // Fenster um eindeutige Muss-Begriffe bilden.
        if (candidates.size < maxItems) {
            val lower = raw.lowercase(Locale.GERMAN)
            val markers = listOf(
                "zwingend",
                "voraussetzung",
                "vorausgesetzt",
                "erforderlich",
                "befähigung zum richteramt",
                "abgeschlossenes studium",
                "abgeschlossene ausbildung",
                "erfolgreich abgeschlossenes studium",
                "führerschein klasse",
                "fahrerlaubnis klasse"
            )

            for (marker in markers) {
                var fromIndex = 0
                while (candidates.size < maxItems) {
                    val index = lower.indexOf(marker, fromIndex)
                    if (index < 0) break

                    val start = (index - 90).coerceAtLeast(0)
                    val end = (index + 230).coerceAtMost(raw.length)
                    val window = clean(raw.substring(start, end))
                    if (window.length >= 18) candidates += shorten(window)

                    fromIndex = index + marker.length
                }
                if (candidates.size >= maxItems) break
            }
        }

        return candidates
            .filter { it.isNotBlank() }
            .distinct()
            .take(maxItems)
    }

    private fun isMandatoryChunk(text: String): Boolean {
        val t = text.lowercase(Locale.GERMAN)

        val directMustMarkers = listOf(
            "zwingend",
            "voraussetzung",
            "vorausgesetzt",
            "erforderlich",
            "müssen sie",
            "musst du",
            "wir erwarten",
            "wir setzen voraus",
            "sie verfügen über",
            "du verfügst über",
            "befähigung zum richteramt"
        )
        if (directMustMarkers.any { it in t }) return true

        val qualificationMarkers = listOf(
            "abgeschlossenes studium",
            "abgeschlossene hochschulausbildung",
            "erfolgreich abgeschlossenes studium",
            "abgeschlossene berufsausbildung",
            "abgeschlossene ausbildung",
            "hochschulabschluss",
            "bachelorabschluss",
            "masterabschluss",
            "diplomabschluss",
            "staatsexamen",
            "führerschein klasse",
            "fahrerlaubnis klasse",
            "mehrjährige berufserfahrung"
        )
        if (qualificationMarkers.any { it in t }) return true

        return false
    }

    private fun clean(text: String): String =
        text
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', ';', ',')

    private fun shorten(text: String): String {
        val cleaned = clean(text)
        if (cleaned.length <= 230) return cleaned
        return cleaned.take(227).trimEnd() + "…"
    }
}
