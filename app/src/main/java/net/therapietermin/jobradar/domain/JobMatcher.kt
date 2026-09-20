package net.therapietermin.jobradar.domain

import net.therapietermin.jobradar.data.Job
import java.util.Locale

object JobMatcher {
    fun score(job: Job): Pair<Int, List<String>> {
        val title = job.title.lowercase(Locale.GERMAN)
        val description = job.description.lowercase(Locale.GERMAN)
        val employer = job.employer.lowercase(Locale.GERMAN)
        val city = job.city.lowercase(Locale.GERMAN)
        val source = job.source.lowercase(Locale.GERMAN)
        val text = "$title $description $employer $city ${job.pay.orEmpty().lowercase(Locale.GERMAN)}"

        if ("lsbb" in employer || "landesstraßenbaubehörde" in employer)
            return 0 to listOf("Arbeitgeber ausgeschlossen")
        if ("lämpe" in employer || "lampe mössner" in employer)
            return 0 to listOf("Arbeitgeber ausgeschlossen")
        if (listOf("sozialpädagog", "sozialarbeiter").any { it in title })
            return 0 to listOf("Sozialpädagogik ausgeschlossen")

        if (("elektrotechnik" in title || "elektroingenieur" in title) &&
            !listOf("maschinenbau", "vergleichbar", "projekt", "bauprojekt", "koordination", "planung").any { it in text })
            return 0 to listOf("Reine Elektrotechnik ausgeschlossen")

        if (listOf("ausbildung ", "duales studium", "werkstudent", "praktikum").any { it in title })
            return 0 to listOf("Keine reguläre Fach-/Projektstelle")

        var score = 22
        val reasons = mutableListOf<String>()
        fun add(points: Int, reason: String) {
            score += points
            reasons += reason
        }

        val nearMagdeburg = listOf(
            "magdeburg", "barleben", "wolmirstedt", "hohenwarsleben",
            "niederndodeleben", "irxleben", "schönebeck", "schoenebeck",
            "burg", "möckern", "moeckern", "genthin", "haldensleben",
            "oschersleben", "calbe", "staßfurt", "stassfurt", "wanzleben",
            "biederitz", "osterweddingen", "sülzetal", "suelzetal"
        )

        if (nearMagdeburg.any { it in text }) add(18, "Magdeburg / ca. 50 km")
        if ("stendal" in text) add(6, "Stendal als Ausnahme")

        if (listOf("projektsteuer", "projektmanagement", "projektleit", "projektkoordin").any { it in text })
            add(18, "Projektsteuerung / Projektmanagement")

        if (listOf(
                "infrastruktur", "vergabe", "ausschreibung", "bauherr",
                "auftraggeber", "koordination", "beschaffung", "schnittstelle",
                "bauprojekt", "planung"
            ).any { it in text })
            add(14, "Passende Infrastruktur-/Schnittstellenaufgaben")

        if (listOf("maschinenbau", "ingenieur", "technik", "technisch", "techniker").any { it in text })
            add(9, "Technischer Hintergrund anschlussfähig")

        if (Regex(
                """\b(?:e|eg|tv-l\s*e|tvöd(?:-vka)?\s*e)\s?1[123]\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(text)
        ) add(15, "Entgeltgruppe E11–E13")

        if ("aveu" in text) add(10, "Tarifliche Vergütung im Energieumfeld")
        if (job.permanent == true || "unbefristet" in text) add(10, "Unbefristet")

        if (listOf(
                "städtische werke magdeburg", "swm magdeburg",
                "netze magdeburg", "autobahn gmbh"
            ).any { it in employer })
            add(12, "Arbeitgeber mit hoher Priorität")

        if ("magdeburger verkehrsbetriebe" in employer || "mvb" in employer)
            add(8, "Regionaler Infrastruktur-Arbeitgeber")

        if ("aib" in employer || "immobilien- und baumanagement" in employer)
            add(1, "AIB nachrangig berücksichtigt")

        if ("karriereportal sachsen-anhalt" in source || "interamt" in source)
            add(2, "Öffentlicher Arbeitgeber")

        return score.coerceIn(0, 100) to reasons.distinct()
    }
}
