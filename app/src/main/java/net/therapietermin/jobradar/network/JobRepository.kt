package net.therapietermin.jobradar.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.therapietermin.jobradar.data.Job
import net.therapietermin.jobradar.data.JobDao
import net.therapietermin.jobradar.domain.JobMatcher

class JobRepository(
    private val dao: JobDao,
    private val service: JobsucheService = JobsucheService()
) {
    data class SearchSummary(val scanned: Int, val matched: Int, val newCount: Int)

    suspend fun refresh(): SearchSummary = withContext(Dispatchers.IO) {
        val hits = linkedMapOf<String, JobsucheService.SearchHit>()

        val terms = listOf(
            "Projektmanagement",
            "Projektleitung",
            "Projektsteuerung",
            "Infrastruktur",
            "Vergabe",
            "Ausschreibung",
            "technische Koordination",
            "Maschinenbau"
        )

        terms.forEach { term ->
            runCatching { service.search(what = term, where = "Magdeburg", radius = 50, size = 25) }
                .getOrDefault(emptyList())
                .forEach { hits.putIfAbsent(it.ref, it) }
        }

        listOf("Projektmanagement", "Infrastruktur", "Maschinenbau").forEach { term ->
            runCatching { service.search(what = term, where = "Stendal", radius = 15, size = 20) }
                .getOrDefault(emptyList())
                .forEach { hits.putIfAbsent(it.ref, it) }
        }

        listOf(
            "Städtische Werke Magdeburg",
            "Die Autobahn GmbH des Bundes",
            "Amt für Immobilien- und Baumanagement"
        ).forEach { employer ->
            runCatching { service.search(where = "Magdeburg", radius = 50, employer = employer, size = 25) }
                .getOrDefault(emptyList())
                .forEach { hits.putIfAbsent(it.ref, it) }
        }

        if (hits.isEmpty()) throw IllegalStateException("Die Jobsuche hat keine Daten geliefert. Bitte Internetverbindung prüfen und erneut versuchen.")

        val detailed = hits.values.take(90).mapNotNull { hit ->
            runCatching { service.details(hit) }.getOrNull()
        }

        val matched = detailed.mapNotNull { raw ->
            val (score, reasons) = JobMatcher.score(raw)
            if (score < 50) null
            else raw.copy(score = score, reasons = reasons.joinToString(" • "))
        }

        val ids = matched.map { it.sourceId }
        val existing = if (ids.isEmpty()) emptySet() else dao.existingIds(ids).toSet()
        val newCount = ids.count { it !in existing }
        dao.insertAll(matched)

        SearchSummary(scanned = detailed.size, matched = matched.size, newCount = newCount)
    }
}
