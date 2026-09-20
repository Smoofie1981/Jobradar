package net.therapietermin.jobradar.domain

import net.therapietermin.jobradar.data.Job

object JobMatcher {
    fun score(job: Job): Pair<Int, List<String>> {
        val title = job.title.lowercase()
        val description = job.description.lowercase()
        val employer = job.employer.lowercase()
        val city = job.city.lowercase()
        val text = "$title $description $employer $city ${job.pay.orEmpty().lowercase()}"

        if ("lsbb" in employer || "landesstraßenbaubehörde" in employer)
            return 0 to listOf("Arbeitgeber ausgeschlossen")
        if ("lämpe" in employer || "lampe mössner" in employer)
            return 0 to listOf("Arbeitgeber ausgeschlossen")
        if (listOf("sozialpädagog", "sozialarbeiter").any { it in title })
            return 0 to listOf("Sozialpädagogik ausgeschlossen")
        if (("elektrotechnik" in title || "elektroingenieur" in title) &&
            !listOf("maschinenbau", "vergleichbar", "projekt").any { it in text })
            return 0 to listOf("Spezifische Elektrotechnik-Anforderung")

        var score = 25
        val reasons = mutableListOf<String>()
        fun add(points: Int, reason: String) { score += points; reasons += reason }

        if ("magdeburg" in city || "magdeburg" in text) add(18, "Magdeburg / Umkreis")
        if ("stendal" in city || "stendal" in text) add(6, "Stendal als Ausnahme")
        if (listOf("projektsteuer", "projektmanagement", "projektleit").any { it in text })
            add(17, "Projektsteuerung / Projektmanagement")
        if (listOf("infrastruktur", "vergabe", "ausschreibung", "bauherr", "koordination", "beschaffung").any { it in text })
            add(13, "Passende Infrastruktur-/Koordinationsaufgaben")
        if (listOf("maschinenbau", "ingenieur", "technik", "technisch").any { it in text })
            add(8, "Technischer Hintergrund anschlussfähig")
        if (Regex("""\b(?:e|eg|tv-l\s*e|tvöd(?:-vka)?\s*e)\s?1[123]\b""", RegexOption.IGNORE_CASE).containsMatchIn(text))
            add(14, "Entgeltgruppe E11–E13")
        if (job.permanent == true || "unbefristet" in text) add(10, "Unbefristet")

        if (listOf("städtische werke magdeburg", "swm magdeburg", "autobahn gmbh").any { it in employer })
            add(10, "Arbeitgeber mit hoher Priorität")
        if ("aib" in employer || "immobilien- und baumanagement" in employer)
            add(1, "AIB nachrangig berücksichtigt")

        return score.coerceIn(0, 100) to reasons.distinct()
    }
}
