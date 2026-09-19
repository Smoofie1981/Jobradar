package net.therapietermin.jobradar.domain

import net.therapietermin.jobradar.data.Job

object JobMatcher {
    fun score(job: Job): Pair<Int, List<String>> {
        val text = "${job.title} ${job.description} ${job.employer} ${job.city}".lowercase()
        var score = 35
        val reasons = mutableListOf<String>()

        fun add(points: Int, reason: String) { score += points; reasons += reason }

        if ("magdeburg" in text) add(18, "Magdeburg")
        if ("stendal" in text) add(5, "Stendal ist als Ausnahme zugelassen")
        if (listOf("projektsteuer", "projektmanagement", "projektleit").any { it in text })
            add(15, "Projektsteuerung / Projektmanagement")
        if (listOf("infrastruktur", "vergabe", "ausschreibung", "bauherr", "koordination").any { it in text })
            add(12, "Passende Schnittstellen- und Infrastrukturaufgaben")
        if (listOf("maschinenbau", "ingenieur").any { it in text })
            add(8, "Technischer Hintergrund anschlussfähig")
        if (Regex("""\be\s?1[123]\b""").containsMatchIn(text))
            add(12, "Entgeltgruppe E11–E13")
        if (job.permanent == true || "unbefristet" in text) add(10, "Unbefristet")

        if (listOf("swm", "städtische werke magdeburg", "autobahn").any { it in text })
            add(8, "Arbeitgeber mit hoher Priorität")
        if ("aib" in text || "immobilien- und baumanagement" in text)
            add(1, "AIB wird nachrangig berücksichtigt")

        if ("lämpe" in text || "lampe mössner" in text) return 0 to listOf("Arbeitgeber ausgeschlossen")
        if ("lsbb" in text || "landesstraßenbaubehörde" in text) return 0 to listOf("Arbeitgeber ausgeschlossen")
        if (listOf("sozialpädagog", "sozialarbeiter").any { it in text }) return 0 to listOf("Sozialpädagogik ausgeschlossen")
        if (("elektrotechnik" in text || "elektroingenieur" in text) &&
            !listOf("maschinenbau", "vergleichbar").any { it in text })
            return 0 to listOf("Spezifische Elektrotechnik-Anforderung")

        return score.coerceIn(0, 100) to reasons
    }
}
