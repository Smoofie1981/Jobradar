package net.therapietermin.jobradar.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.therapietermin.jobradar.data.Job
import net.therapietermin.jobradar.data.JobDao
import net.therapietermin.jobradar.domain.JobMatcher

class JobRepository(
    private val dao: JobDao,
    private val baService: JobsucheService = JobsucheService()
) {
    data class SearchSummary(
        val scanned: Int,
        val accepted: Int,
        val newCount: Int,
        val sourceSummary: String
    )

    suspend fun refresh(): SearchSummary = withContext(Dispatchers.IO) {
        val rawJobs = linkedMapOf<String, Job>()
        val sourceCounts = linkedMapOf<String, Int>()
        val errors = mutableListOf<String>()

        val baHits = linkedMapOf<String, JobsucheService.SearchHit>()

        suspend fun collectBa(
            what: String? = null,
            where: String = "Magdeburg",
            radius: Int = 50,
            employer: String? = null,
            size: Int = 50
        ) {
            try {
                baService.search(
                    what = what,
                    where = where,
                    radius = radius,
                    employer = employer,
                    size = size
                ).forEach { baHits.putIfAbsent(it.ref, it) }
            } catch (e: Exception) {
                errors += "BA: ${e.message ?: e.javaClass.simpleName}"
            }
            delay(300)
        }

        // Technik / Projekt / Infrastruktur
        collectBa(what = "Projekt", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Ingenieur", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Infrastruktur", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Maschinenbau", where = "Magdeburg", radius = 50, size = 75)

        // Medien / Kommunikation
        collectBa(what = "Medien", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Kommunikation", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Öffentlichkeitsarbeit", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Redaktion", where = "Magdeburg", radius = 50, size = 75)

        // Sozialpädagogik – später hart auf Ministerien begrenzt.
        collectBa(what = "Sozialpädagogik", where = "Magdeburg", radius = 50, size = 75)
        collectBa(what = "Soziale Arbeit", where = "Magdeburg", radius = 50, size = 75)

        // Stendal bleibt die vereinbarte Ausnahme.
        collectBa(where = "Stendal", radius = 15, size = 60)

        val baJobs = baHits.values.take(180).map { hit ->
            runCatching { baService.details(hit) }
                .getOrElse { baService.asBasicJob(hit) }
        }
        baJobs.forEach { rawJobs.putIfAbsent(it.sourceId, it) }
        sourceCounts["BA"] = baJobs.size

        PortalJobSources.scanAll().forEach { result ->
            if (result.error != null) {
                errors += "${result.source}: ${result.error}"
            }

            result.jobs.forEach { raw ->
                val dedupKey = raw.url.lowercase()
                val already = rawJobs.values.any { it.url.lowercase() == dedupKey }
                if (!already) rawJobs[raw.sourceId] = raw
            }

            val key = when (result.source) {
                "Karriereportal Sachsen-Anhalt" -> "Land SA"
                "SWM Magdeburg" -> "SWM"
                "MVB Magdeburg" -> "MVB"
                "Autobahn GmbH" -> "Autobahn"
                else -> result.source
            }
            sourceCounts[key] = (sourceCounts[key] ?: 0) + result.jobs.size
        }

        if (rawJobs.isEmpty()) {
            val details = errors.distinct().take(4).joinToString(" | ")
            throw IllegalStateException(
                if (details.isBlank()) "Keine der Stellenquellen lieferte aktuell Daten."
                else "Keine Quelle lieferte Daten: $details"
            )
        }

        // Keine Prozentbewertung mehr. Nur noch die vereinbarten harten Ausschlüsse
        // und die Zuordnung zu Technik / Medien / Sozialpädagogik.
        val acceptedJobs = rawJobs.values
            .filter { JobMatcher.shouldInclude(it) }
            .map { it.copy(score = 0, reasons = "") }

        val ids = acceptedJobs.map { it.sourceId }
        val existing = if (ids.isEmpty()) emptySet()
        else dao.existingIds(ids).toSet()

        val newCount = ids.count { it !in existing }
        dao.insertAll(acceptedJobs)

        val summary = sourceCounts.entries
            .filter { it.value > 0 }
            .joinToString(" · ") { "${it.key} ${it.value}" }
            .ifBlank {
                if (errors.isEmpty()) "keine Quellendetails"
                else "Teilfehler bei Quellen"
            }

        SearchSummary(
            scanned = rawJobs.size,
            accepted = acceptedJobs.size,
            newCount = newCount,
            sourceSummary = summary
        )
    }
}
