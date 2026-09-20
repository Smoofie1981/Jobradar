package net.therapietermin.jobradar.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.therapietermin.jobradar.data.Job
import net.therapietermin.jobradar.data.JobDao
import net.therapietermin.jobradar.domain.JobMatcher

class JobRepository(
    private val dao: JobDao,
    private val service: JobsucheService = JobsucheService()
) {
    data class SearchSummary(
        val scanned: Int,
        val matched: Int,
        val newCount: Int
    )

    suspend fun refresh(): SearchSummary = withContext(Dispatchers.IO) {
        val hits = linkedMapOf<String, JobsucheService.SearchHit>()
        val errors = mutableListOf<String>()

        suspend fun collect(
            what: String? = null,
            where: String = "Magdeburg",
            radius: Int = 50,
            employer: String? = null,
            size: Int = 50
        ) {
            try {
                service.search(
                    what = what,
                    where = where,
                    radius = radius,
                    employer = employer,
                    size = size
                ).forEach { hits.putIfAbsent(it.ref, it) }
            } catch (e: Exception) {
                errors += (e.message ?: e.javaClass.simpleName)
            }
            delay(350)
        }

        // Weniger, breitere Anfragen als in 0.2.0.
        // Dadurch wird die BA-Schnittstelle nicht mit vielen Requests auf einmal belastet.
        collect(what = "Projekt", where = "Magdeburg", radius = 50, size = 75)
        collect(what = "Ingenieur", where = "Magdeburg", radius = 50, size = 75)
        collect(what = "Infrastruktur", where = "Magdeburg", radius = 50, size = 75)
        collect(what = "Maschinenbau", where = "Magdeburg", radius = 50, size = 75)

        // Stendal bleibt die vereinbarte Ausnahme.
        collect(where = "Stendal", radius = 15, size = 60)

        // Zwei priorisierte Arbeitgeber zusätzlich direkt suchen.
        collect(
            where = "Magdeburg",
            radius = 50,
            employer = "Städtische Werke Magdeburg",
            size = 40
        )
        collect(
            where = "Magdeburg",
            radius = 50,
            employer = "Die Autobahn GmbH des Bundes",
            size = 40
        )

        if (hits.isEmpty()) {
            val detail = errors.distinct().take(3).joinToString(" | ")
            throw IllegalStateException(
                if (detail.isBlank()) {
                    "Die BA-Jobsuche lieferte aktuell keine Daten."
                } else {
                    "BA-Jobsuche nicht erreichbar: $detail"
                }
            )
        }

        // Zuerst mit den Daten aus der Trefferliste bewerten.
        // So bleiben Treffer sichtbar, selbst wenn der Detail-Endpunkt zeitweise blockiert.
        val prelim = hits.values.map { hit ->
            val raw = service.asBasicJob(hit)
            val (score, reasons) = JobMatcher.score(raw)
            Triple(hit, score, reasons)
        }.filter { (_, score, _) -> score >= 50 }
            .sortedByDescending { (_, score, _) -> score }

        val matchedJobs = mutableListOf<Job>()

        // Nur für die bestbewerteten Kandidaten Detaildaten abrufen.
        // Wenn das scheitert, wird der Treffer trotzdem gespeichert.
        for ((index, item) in prelim.withIndex()) {
            val (hit, _, _) = item

            val raw: Job = if (index < 20) {
                try {
                    service.details(hit)
                } catch (_: Exception) {
                    service.asBasicJob(hit)
                }
            } else {
                service.asBasicJob(hit)
            }

            val (score, reasons) = JobMatcher.score(raw)
            if (score >= 50) {
                matchedJobs += raw.copy(
                    score = score,
                    reasons = reasons.joinToString(" • ")
                )
            }

            if (index < 20) delay(160)
        }

        val ids = matchedJobs.map { it.sourceId }
        val existing = if (ids.isEmpty()) emptySet()
        else dao.existingIds(ids).toSet()

        val newCount = ids.count { it !in existing }
        dao.insertAll(matchedJobs)

        SearchSummary(
            scanned = hits.size,
            matched = matchedJobs.size,
            newCount = newCount
        )
    }
}
