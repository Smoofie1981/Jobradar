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

    suspend fun refresh(radius: Int = 50): SearchSummary = withContext(Dispatchers.IO) {
        val effectiveRadius =
            radius.takeIf { it in listOf(25, 50, 75, 100) } ?: 50

        val rawJobs = linkedMapOf<String, Job>()
        val sourceCounts = linkedMapOf<String, Int>()
        val errors = mutableListOf<String>()
        val baHits = linkedMapOf<String, JobsucheService.SearchHit>()

        suspend fun collectBa(
            what: String? = null,
            where: String = "Magdeburg",
            searchRadius: Int = effectiveRadius,
            employer: String? = null,
            size: Int = 80
        ) {
            try {
                baService.search(
                    what = what,
                    where = where,
                    radius = searchRadius,
                    employer = employer,
                    size = size
                ).forEach { baHits.putIfAbsent(it.ref, it) }
            } catch (e: Exception) {
                errors += "BA: ${e.message ?: e.javaClass.simpleName}"
            }
            delay(250)
        }

        // Breite Suchprofile statt Prozentbewertung.
        collectBa(what = "Projekt")
        collectBa(what = "Ingenieur")
        collectBa(what = "Energie")
        collectBa(what = "Energiewende")
        collectBa(what = "Medien")
        collectBa(what = "Öffentlichkeitsarbeit")
        collectBa(what = "Kommunikation")
        collectBa(what = "Sozialpädagogik")

        // Stendal bleibt unabhängig vom gewählten Radius die Ausnahme.
        collectBa(where = "Stendal", searchRadius = 15, size = 60)

        val baJobs = baHits.values.take(160).map { hit ->
            runCatching { baService.details(hit) }
                .getOrElse { baService.asBasicJob(hit) }
        }

        baJobs.forEach { rawJobs.putIfAbsent(it.sourceId, it) }
        sourceCounts["BA"] = baJobs.size

        PortalJobSources.scanAll(effectiveRadius).forEach { result ->
            if (result.error != null) {
                errors += "${result.source}: ${result.error}"
            }

            result.jobs.forEach { raw ->
                val sameUrl = rawJobs.values.any {
                    it.url.equals(raw.url, ignoreCase = true)
                }
                if (!sameUrl) rawJobs[raw.sourceId] = raw
            }

            val shortName = when (result.source) {
                "Karriereportal Sachsen-Anhalt" -> "Land SA"
                "SWM Magdeburg" -> "SWM"
                "MVB Magdeburg" -> "MVB"
                "Autobahn GmbH" -> "Autobahn"
                "Hochschule Magdeburg-Stendal" -> "H2"
                "hierbleiben-jobs" -> "hierbleiben"
                "Nachwuchsmarkt" -> "Nachwuchsmarkt"
                else -> result.source
            }
            sourceCounts[shortName] =
                (sourceCounts[shortName] ?: 0) + result.jobs.size
        }

        if (rawJobs.isEmpty()) {
            val details = errors.distinct().take(5).joinToString(" | ")
            throw IllegalStateException(
                if (details.isBlank()) {
                    "Keine der Stellenquellen lieferte aktuell Daten."
                } else {
                    "Keine Quelle lieferte Daten: $details"
                }
            )
        }

        val acceptedJobs = rawJobs.values
            .filter { JobMatcher.shouldInclude(it) }
            .map { it.copy(score = 0, reasons = "") }

        val ids = acceptedJobs.map { it.sourceId }
        val existing =
            if (ids.isEmpty()) emptySet() else dao.existingIds(ids).toSet()

        val newCount = ids.count { it !in existing }
        dao.insertAll(acceptedJobs)

        val summary = sourceCounts.entries
            .filter { it.value > 0 }
            .joinToString(" · ") { "${it.key} ${it.value}" }
            .ifBlank {
                if (errors.isEmpty()) "keine Quellendetails"
                else "Teilfehler bei einzelnen Quellen"
            }

        SearchSummary(
            scanned = rawJobs.size,
            accepted = acceptedJobs.size,
            newCount = newCount,
            sourceSummary = summary
        )
    }
}
