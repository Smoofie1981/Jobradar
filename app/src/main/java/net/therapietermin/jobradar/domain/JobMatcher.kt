package net.therapietermin.jobradar.domain

import net.therapietermin.jobradar.data.Job
import java.util.Locale

object JobMatcher {

    fun category(job: Job): String? {
        val title = job.title.lowercase(Locale.GERMAN)
        val description = job.description.lowercase(Locale.GERMAN)
        val employer = job.employer.lowercase(Locale.GERMAN)
        val source = job.source.lowercase(Locale.GERMAN)
        val text = "$title $description $employer $source"

        val socialWords = listOf(
            "sozialpädagog", "sozialarbeiter", "soziale arbeit",
            "sozialwesen", "jugendhilfe", "sozialdienst"
        )
        if (socialWords.any { it in text }) return "Sozialpädagogik"

        // Bewusst keine bloße "Kommunikation": das würde fast jede Stelle
        // wegen "Kommunikationsfähigkeit" fälschlich als Medienjob einsortieren.
        val mediaWords = listOf(
            "mediengestalter", "medienproduktion", "medienmanagement",
            "medienwissenschaft", "media manager", "video", "bewegtbild",
            "audiovisu", "redaktion", "redakteur", "content manager",
            "content creator", "content redaktion", "öffentlichkeitsarbeit",
            "pressearbeit", "pressesprecher", "public relations", "social media",
            "kamera", "videoschnitt", "postproduktion", "broadcast",
            "journalis", "e-learning", "elearning", "multimedia",
            "online-redaktion", "unternehmenskommunikation",
            "kommunikationsmanagement", "kommunikationswissenschaft",
            "marketingkommunikation"
        )
        if (mediaWords.any { it in text }) return "Medien"

        val technicalWords = listOf(
            "projekt", "ingenieur", "maschinenbau", "technik", "technisch",
            "infrastruktur", "vergabe", "ausschreibung", "bau", "planung",
            "koordination", "beschaffung", "einkauf", "verkehr", "netz",
            "energie", "energiewende", "erneuerbare", "photovoltaik", "solar",
            "windenergie", "wärmepumpe", "energieeffizienz", "energiemanagement",
            "dekarbonisierung", "klimaschutz", "wasserstoff", "netzausbau",
            "elektromobilität", "alternative antriebe", "facility",
            "immobilien", "steuerung"
        )
        if (technicalWords.any { it in text }) return "Technik"

        return null
    }

    fun shouldInclude(job: Job): Boolean {
        val title = job.title.lowercase(Locale.GERMAN)
        val description = job.description.lowercase(Locale.GERMAN)
        val employer = job.employer.lowercase(Locale.GERMAN)
        val source = job.source.lowercase(Locale.GERMAN)
        val text = "$title $description $employer $source"

        if ("lsbb" in employer || "landesstraßenbaubehörde" in employer) return false
        if ("lämpe" in employer || "lampe mössner" in employer) return false

        if (listOf(
                "ausbildung ", "duales studium", "werkstudent", "praktikum",
                "studentische aushilfe", "studentische hilfskraft"
            ).any { it in title }
        ) return false

        // Juristische Stellen nur dann ausschließen, wenn die juristische
        // Qualifikation eindeutig zwingender Kern der Stelle ist.
        val legalTitleMarkers = listOf(
            "volljurist", "jurist ", "juristin", "rechtsanwalt",
            "rechtsanwält", "syndikus", "legal counsel"
        )
        if (legalTitleMarkers.any { it in title }) return false

        val mandatoryLegalMarkers = listOf(
            "befähigung zum richteramt",
            "zweites juristisches staatsexamen",
            "2. juristisches staatsexamen",
            "erstes und zweites juristisches staatsexamen",
            "erstes und 2. juristisches staatsexamen",
            "rechtswissenschaften mit staatsexamen"
        )
        if (mandatoryLegalMarkers.any { it in text }) return false

        val mandatoryLawStudy = Regex(
            """(?i)(zwingend|voraussetzung|erforderlich|vorausgesetzt).{0,120}(studium|abschluss).{0,80}(rechtswissenschaft|jura)"""
        )
        if (mandatoryLawStudy.containsMatchIn(text)) return false

        // Reine Elektrotechnik bleibt draußen; Projekt-/Schnittstellen-/Energie-
        // Rollen mit breiterem Profil dürfen durchkommen.
        if (("elektrotechnik" in title || "elektroingenieur" in title) &&
            !listOf(
                "maschinenbau", "vergleichbar", "projekt", "bauprojekt",
                "koordination", "planung", "energie", "energiewende",
                "erneuerbare", "netzausbau"
            ).any { it in text }
        ) return false

        val category = category(job) ?: return false

        // Sozialpädagogik ausschließlich Ministerium/Staatskanzlei.
        if (category == "Sozialpädagogik") {
            val ministryMarkers = listOf("ministerium", "ministerial", "staatskanzlei")
            if (ministryMarkers.none { it in text }) return false
        }

        // Nur eine ausdrücklich erkennbare Nettoangabe unter 2.600 € führt
        // zum Ausschluss. Bruttoangaben werden bewusst nicht umgerechnet.
        if (SalaryParser.explicitNetBelowMinimum(job)) return false

        return true
    }
}
