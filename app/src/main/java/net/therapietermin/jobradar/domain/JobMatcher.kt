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

        val mediaWords = listOf(
            "medien", "media", "video", "bewegtbild", "audiovisu",
            "redaktion", "redakteur", "content", "kommunikation",
            "öffentlichkeitsarbeit", "presse", "pr ", "social media",
            "kamera", "schnitt", "produktion", "broadcast", "journalis",
            "e-learning", "elearning", "multimedia", "online-redaktion"
        )
        if (mediaWords.any { it in text }) return "Medien"

        val technicalWords = listOf(
            "projekt", "ingenieur", "maschinenbau", "technik", "technisch",
            "infrastruktur", "vergabe", "ausschreibung", "bau", "planung",
            "koordination", "beschaffung", "einkauf", "verkehr", "netz",
            "energie", "facility", "immobilien", "steuerung"
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

        // Weiterhin ausdrücklich ausgeschlossen.
        if ("lsbb" in employer || "landesstraßenbaubehörde" in employer) return false
        if ("lämpe" in employer || "lampe mössner" in employer) return false

        if (listOf("ausbildung ", "duales studium", "werkstudent", "praktikum").any { it in title }) {
            return false
        }

        // Reine Elektrotechnik-Stellen bleiben draußen, Schnittstellen-/Projektrollen nicht.
        if (("elektrotechnik" in title || "elektroingenieur" in title) &&
            !listOf("maschinenbau", "vergleichbar", "projekt", "bauprojekt", "koordination", "planung").any { it in text }
        ) {
            return false
        }

        val category = category(job) ?: return false

        // Sozialpädagogik nur im Ministerium. Jugendamt, Träger, e.V., gGmbH usw.
        // fallen damit automatisch heraus.
        if (category == "Sozialpädagogik") {
            val ministryMarkers = listOf(
                "ministerium", "ministerial", "staatskanzlei"
            )
            if (ministryMarkers.none { it in text }) return false
        }

        return true
    }
}
