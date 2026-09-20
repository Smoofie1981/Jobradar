package net.therapietermin.jobradar.domain

import net.therapietermin.jobradar.data.Job
import java.util.Locale

object SalaryParser {
    private const val MIN_NET_MONTHLY = 2600.0
    private const val MONEY =
        """(?:\d{1,3}(?:[.\s]\d{3})+(?:,\d{1,2})?|\d{4,6}(?:,\d{1,2})?)"""

    private val tariffPatterns = listOf(
        Regex("""(?i)\bTV-L\s*(?:EG|E)?\s*(?:9[abc]?|1[0-5])\b"""),
        Regex("""(?i)\bTVöD(?:-VKA)?\s*(?:EG|E)?\s*(?:9[abc]?|1[0-5])\b"""),
        Regex("""(?i)\b(?:EG|E)\s*(?:9[abc]?|1[0-5])\b"""),
        Regex("""(?i)\bAVEU\b""")
    )

    private val salaryPatterns = listOf(
        Regex("""(?i)\b$MONEY\s*(?:€|Euro)\s*(?:brutto|netto)(?:\s*(?:pro|/)\s*(?:Monat|Jahr))?"""),
        Regex("""(?i)\b(?:brutto|netto)\s*(?:monatlich|jährlich|pro\s+Monat|pro\s+Jahr|mtl\.?|p\.?\s*a\.?)?\s*[:\-]?\s*$MONEY\s*(?:€|Euro)"""),
        Regex("""(?i)\b$MONEY\s*(?:bis|-)\s*$MONEY\s*(?:€|Euro)\s*(?:brutto|netto)?(?:\s*(?:pro|/)\s*(?:Monat|Jahr))?"""),
        Regex("""(?i)\b$MONEY\s*(?:€|Euro)\s*(?:pro\s+Jahr|jährlich|p\.?\s*a\.?)"""),
        Regex("""(?i)\b$MONEY\s*(?:€|Euro)\s*(?:pro\s+Monat|monatlich|mtl\.?)""")
    )

    fun extractDisplay(text: String): String? {
        val compact = text.replace(Regex("""\s+"""), " ").trim()

        salaryPatterns.asSequence()
            .mapNotNull { it.find(compact)?.value?.trim() }
            .firstOrNull()
            ?.let { return it }

        return tariffPatterns.asSequence()
            .mapNotNull { it.find(compact)?.value?.trim() }
            .firstOrNull()
    }

    fun displayFor(job: Job): String? =
        job.pay?.takeIf { it.isNotBlank() }
            ?: extractDisplay("${job.title} ${job.description}")

    /**
     * Konservative harte Grenze:
     * Nur wenn eine einzelne, ausdrücklich als "netto" bezeichnete Monatsangabe
     * unter 2.600 € erkennbar ist, wird ausgeschlossen.
     * Bei Brutto, Tarif, Spannen oder unklaren Angaben wird NICHT gerechnet.
     */
    fun explicitNetBelowMinimum(job: Job): Boolean {
        val text =
            "${job.title} ${job.description} ${job.pay.orEmpty()}".lowercase(Locale.GERMAN)

        val patterns = listOf(
            Regex("""(?i)\b($MONEY)\s*(?:€|euro)\s*netto\b"""),
            Regex("""(?i)\bnetto\s*(?:monatlich|pro\s+monat|mtl\.?)?\s*[:\-]?\s*($MONEY)\s*(?:€|euro)\b""")
        )

        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                val start = (match.range.first - 35).coerceAtLeast(0)
                val end = (match.range.last + 35).coerceAtMost(text.lastIndex)
                val around = text.substring(start, end + 1)

                // Bei einer Gehaltsspanne lieber nicht automatisch aussortieren.
                if (" bis " in around || Regex("""\d\s*[-–]\s*\d""").containsMatchIn(around)) {
                    continue
                }

                val amount = parseGermanAmount(match.groupValues[1]) ?: continue
                if (amount < MIN_NET_MONTHLY) return true
            }
        }
        return false
    }

    private fun parseGermanAmount(raw: String): Double? {
        var s = raw.trim().replace(" ", "")
        s = if ("," in s) {
            s.replace(".", "").replace(",", ".")
        } else if (s.count { it == '.' } >= 1) {
            s.replace(".", "")
        } else {
            s
        }
        return s.toDoubleOrNull()
    }
}
