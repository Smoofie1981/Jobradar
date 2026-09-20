package net.therapietermin.jobradar.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.therapietermin.jobradar.data.Job
import net.therapietermin.jobradar.domain.SalaryParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.*

object PortalJobSources {

    data class SourceResult(
        val source: String,
        val jobs: List<Job>,
        val error: String? = null
    )

    private data class PortalConfig(
        val source: String,
        val listingUrl: String,
        val fixedEmployer: String? = null,
        val fixedCity: String? = null,
        val linkMustContain: List<String> = emptyList(),
        val parseHeadings: Boolean = true
    )

    private data class Candidate(
        val title: String,
        val url: String,
        val context: String
    )

    private val configs = listOf(
        PortalConfig(
            "Karriereportal Sachsen-Anhalt",
            "https://karriere.sachsen-anhalt.de/stellenangebote",
            linkMustContain = listOf("/stellenangebote/")
        ),
        PortalConfig(
            "INTERAMT",
            "https://interamt.de/koop/app/trefferliste?partner=213",
            fixedCity = "Magdeburg",
            linkMustContain = listOf("stellenangebot")
        ),
        PortalConfig(
            "INTERAMT",
            "https://interamt.de/koop/app/trefferliste?partner=5629",
            linkMustContain = listOf("stellenangebot")
        ),
        PortalConfig(
            "SWM Magdeburg",
            "https://www.sw-magdeburg.de/jobs/",
            fixedEmployer = "Städtische Werke Magdeburg / Beteiligungen",
            fixedCity = "Magdeburg",
            linkMustContain = listOf("/jobs/")
        ),
        PortalConfig(
            "MVB Magdeburg",
            "https://www.mvbnet.de/karriere/stellenangebote/",
            fixedEmployer = "Magdeburger Verkehrsbetriebe (MVB)",
            fixedCity = "Magdeburg"
        ),
        PortalConfig(
            "Autobahn GmbH",
            "https://jobs.autobahn.de/jobportal",
            fixedEmployer = "Die Autobahn GmbH des Bundes",
            linkMustContain = listOf("job", "stellen")
        ),

        // Bereits besprochene Erweiterungen
        PortalConfig(
            "MDR",
            "https://mdr.onapply.de/",
            fixedEmployer = "Mitteldeutscher Rundfunk (MDR)",
            linkMustContain = listOf("Vacancies", "vacanc")
        ),
        PortalConfig(
            "MDR Media",
            "https://www.mdrmedia.de/karriere/stellenangebote",
            fixedEmployer = "MDR Media",
            linkMustContain = listOf("/karriere/")
        ),
        PortalConfig(
            "Hochschule Magdeburg-Stendal",
            "https://www.h2.de/hochschule/jobs-und-karriere/stellenangebote.html",
            fixedEmployer = "Hochschule Magdeburg-Stendal",
            linkMustContain = listOf("jobposting", "stellen")
        ),
        PortalConfig(
            "OVGU",
            "https://www.ovgu.de/Karriere_Personal_VerwaltungTechnik.html",
            fixedEmployer = "Otto-von-Guericke-Universität Magdeburg",
            fixedCity = "Magdeburg"
        ),
        PortalConfig(
            "OVGU",
            "https://www.ovgu.de/-p-10243.html",
            fixedEmployer = "Otto-von-Guericke-Universität Magdeburg",
            fixedCity = "Magdeburg"
        ),

        // Neu bestätigte Quellen
        PortalConfig(
            "NASA GmbH",
            "https://www.nasa.de/karriere",
            fixedEmployer = "Nahverkehrsservice Sachsen-Anhalt GmbH (NASA)",
            fixedCity = "Magdeburg",
            linkMustContain = listOf("/karriere/")
        ),
        PortalConfig(
            "JISSA",
            "https://www.jissa.de/stellen/",
            linkMustContain = listOf("stellen")
        ),
        PortalConfig(
            "B·A·D GmbH",
            "https://www.bad-gmbh.de/karriere/stellenangebote/",
            fixedEmployer = "B·A·D GmbH",
            linkMustContain = listOf("job-detail")
        ),
        PortalConfig(
            "hierbleiben-jobs",
            "https://hierbleiben-jobs.de/jobs/",
            linkMustContain = listOf("job")
        ),
        PortalConfig(
            "TÜV NORD",
            "https://www.tuev-nord-group.com/de/karriere/jobs/",
            fixedEmployer = "TÜV NORD GROUP",
            linkMustContain = listOf("job-detail", "/karriere/jobs/")
        ),
        PortalConfig(
            "TÜV SÜD",
            "https://jobs.tuvsud.com/search/?q=&locationsearch=Magdeburg",
            fixedEmployer = "TÜV SÜD",
            linkMustContain = listOf("/job/")
        ),
        PortalConfig(
            "Nachwuchsmarkt",
            "https://www.nachwuchsmarkt.de/suche_angebote.php?doSearch=&jobart=2",
            linkMustContain = listOf("angebot", "job", "stelle")
        )
    )

    suspend fun scanAll(radius: Int): List<SourceResult> = coroutineScope {
        configs.map { config ->
            async(Dispatchers.IO) {
                try {
                    SourceResult(config.source, scan(config, radius))
                } catch (e: Exception) {
                    SourceResult(
                        source = config.source,
                        jobs = emptyList(),
                        error = e.message ?: e.javaClass.simpleName
                    )
                }
            }
        }.awaitAll()
    }

    private fun scan(config: PortalConfig, radius: Int): List<Job> {
        val listing = getText(config.listingUrl)
        val candidates = linkedMapOf<String, Candidate>()

        val anchorRegex = Regex(
            """<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        anchorRegex.findAll(listing).forEach { match ->
            val href = htmlDecode(match.groupValues[1].trim())
            val label = cleanHtml(match.groupValues[2])

            if (label.length !in 7..240) return@forEach
            if (!looksLikeJobTitle(label)) return@forEach

            if (config.linkMustContain.isNotEmpty() &&
                config.linkMustContain.none { href.contains(it, ignoreCase = true) }
            ) return@forEach

            val absolute = absoluteUrl(config.listingUrl, href) ?: return@forEach
            val context = contextAround(listing, match.range.first, match.range.last)

            candidates.putIfAbsent(
                absolute,
                Candidate(label, absolute, context)
            )
        }

        if (config.parseHeadings) {
            val headingRegex = Regex(
                """<h[2-4]\b[^>]*>(.*?)</h[2-4]>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )

            headingRegex.findAll(listing).forEach { match ->
                val title = cleanHtml(match.groupValues[1])
                if (title.length !in 7..240 || !looksLikeJobTitle(title)) return@forEach

                val contextRaw = rawContextAround(listing, match.range.first, match.range.last)
                val href = anchorRegex.find(contextRaw)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.let { absoluteUrl(config.listingUrl, htmlDecode(it.trim())) }
                    ?: config.listingUrl

                val key = "$href#${Integer.toHexString(title.hashCode())}"
                candidates.putIfAbsent(
                    key,
                    Candidate(title, href, cleanHtml(contextRaw))
                )
            }
        }

        return candidates.values
            .asSequence()
            .filter { candidate ->
                isInTargetArea(
                    "${candidate.title} ${candidate.context}",
                    config.fixedCity,
                    radius
                )
            }
            .take(35)
            .mapNotNull { buildJob(config, it, radius) }
            .distinctBy { it.sourceId }
            .toList()
    }

    private fun buildJob(
        config: PortalConfig,
        candidate: Candidate,
        radius: Int
    ): Job? {
        var detailText = ""

        if (candidate.url != config.listingUrl) {
            runCatching {
                detailText = cleanHtml(getText(candidate.url))
            }
        }

        val combined = "${candidate.title} ${candidate.context} $detailText"
        if (!isInTargetArea(combined, config.fixedCity, radius)) return null

        val employer = config.fixedEmployer
            ?: extractEmployer(combined)
            ?: when (config.source) {
                "Karriereportal Sachsen-Anhalt" -> "Land Sachsen-Anhalt"
                "INTERAMT" -> "Öffentlicher Dienst"
                else -> config.source
            }

        val city = config.fixedCity
            ?: extractCity(combined)
            ?: "Magdeburg / Umkreis"

        val permanent = when {
            combined.contains("unbefristet", ignoreCase = true) -> true
            combined.contains("befristet", ignoreCase = true) -> false
            else -> null
        }

        val sourceId =
            "${config.source}:${Integer.toHexString(candidate.url.hashCode())}:${Integer.toHexString(candidate.title.hashCode())}"

        return Job(
            sourceId = sourceId,
            source = config.source,
            title = candidate.title.take(240),
            employer = employer.take(180),
            city = city,
            description = combined.take(14_000),
            pay = SalaryParser.extractDisplay(combined),
            permanent = permanent,
            url = candidate.url
        )
    }

    private fun looksLikeJobTitle(text: String): Boolean {
        val t = text.lowercase(Locale.GERMAN)

        val navigation = listOf(
            "stellenangebote", "karriere", "bewerbung", "job-alarm",
            "mehr erfahren", "weiterlesen", "zurück", "startseite",
            "datenschutz", "impressum", "kontakt", "alle jobs",
            "onlinebewerbung", "jetzt bewerben", "mehr lesen"
        )
        if (navigation.any { t == it || t.startsWith("$it ") }) return false

        val jobWords = listOf(
            // Technik / Projekt / Energie
            "projekt", "ingenieur", "techniker", "referent", "sachbearbeit",
            "bau", "planung", "infrastruktur", "koordination", "manager",
            "management", "vergabe", "ausschreibung", "beschaffung",
            "einkauf", "maschinenbau", "verkehr", "netz", "energie",
            "energiewende", "erneuerbare", "photovoltaik", "solar",
            "wind", "wasserstoff", "klimaschutz", "elektromobil",
            "leiter", "leitung", "fachkraft", "consult", "steuerung",
            // Medien
            "medien", "media", "video", "bewegtbild", "redaktion",
            "redakteur", "content", "öffentlichkeitsarbeit", "presse",
            "social media", "produktion", "kamera", "kommunikationsmanager",
            "unternehmenskommunikation",
            // Sozialpädagogik
            "sozialpädagog", "sozialarbeit", "soziale arbeit", "sozialwesen"
        )
        return jobWords.any { it in t }
    }

    private fun isInTargetArea(
        text: String,
        fixedCity: String?,
        radius: Int
    ): Boolean {
        if (!fixedCity.isNullOrBlank()) {
            if (fixedCity.equals("Stendal", ignoreCase = true)) return true
            return distanceFromMagdeburg(fixedCity)?.let { it <= radius } ?: true
        }

        val city = extractCity(text) ?: return false
        if (city.equals("Stendal", ignoreCase = true)) return true

        val distance = distanceFromMagdeburg(city) ?: return false
        return distance <= radius
    }

    private val cityCoordinates = linkedMapOf(
        "Magdeburg" to Pair(52.1205, 11.6276),
        "Barleben" to Pair(52.2019, 11.6177),
        "Wolmirstedt" to Pair(52.2486, 11.6295),
        "Hohenwarsleben" to Pair(52.1799, 11.4995),
        "Niederndodeleben" to Pair(52.1333, 11.5000),
        "Irxleben" to Pair(52.1664, 11.4788),
        "Biederitz" to Pair(52.1515, 11.7206),
        "Sülzetal" to Pair(52.0167, 11.5333),
        "Osterweddingen" to Pair(52.0414, 11.5780),
        "Schönebeck" to Pair(52.0190, 11.7382),
        "Wanzleben" to Pair(52.0609, 11.4408),
        "Burg" to Pair(52.2715, 11.8549),
        "Haldensleben" to Pair(52.2897, 11.4098),
        "Oschersleben" to Pair(52.0309, 11.2280),
        "Möckern" to Pair(52.1408, 11.9523),
        "Calbe" to Pair(51.9067, 11.7748),
        "Staßfurt" to Pair(51.8519, 11.5851),
        "Genthin" to Pair(52.4062, 12.1598),
        "Zerbst" to Pair(51.9664, 12.0850),
        "Bernburg" to Pair(51.7946, 11.7401),
        "Dessau-Roßlau" to Pair(51.8308, 12.2426),
        "Dessau" to Pair(51.8308, 12.2426),
        "Aschersleben" to Pair(51.7563, 11.4600),
        "Halle" to Pair(51.4825, 11.9705),
        "Halle (Saale)" to Pair(51.4825, 11.9705),
        "Stendal" to Pair(52.6050, 11.8590)
    )

    private fun extractCity(text: String): String? =
        cityCoordinates.keys.firstOrNull {
            text.contains(it, ignoreCase = true)
        }

    private fun distanceFromMagdeburg(city: String): Double? {
        val target = cityCoordinates.entries.firstOrNull {
            it.key.equals(city, ignoreCase = true)
        }?.value ?: return null

        val origin = cityCoordinates.getValue("Magdeburg")
        return haversine(origin.first, origin.second, target.first, target.second)
    }

    private fun haversine(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthKm = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        return 2 * earthKm * asin(sqrt(a))
    }

    private fun extractEmployer(text: String): String? {
        val known = listOf(
            "Amt für Immobilien- und Baumanagement",
            "Landeshauptstadt Magdeburg",
            "Ministerium der Finanzen",
            "Ministerium für Infrastruktur und Digitales",
            "Ministerium für Wirtschaft, Tourismus, Landwirtschaft und Forsten",
            "Ministerium für Arbeit, Soziales, Gesundheit und Gleichstellung",
            "Ministerium für Bildung",
            "Ministerium für Inneres und Sport",
            "Ministerium für Wissenschaft, Energie, Klimaschutz und Umwelt",
            "Ministerium für Justiz und Verbraucherschutz",
            "Staatskanzlei und Ministerium für Kultur",
            "Landesbetrieb Bau- und Liegenschaftsmanagement Sachsen-Anhalt"
        )
        known.firstOrNull { text.contains(it, ignoreCase = true) }?.let { return it }

        val company = Regex(
            """([A-ZÄÖÜ][A-Za-zÄÖÜäöüß0-9&+.,'’()\- ]{2,90}?(?:GmbH(?:\s*&\s*Co\.\s*KG)?|AG|AöR|e\.V\.|KG|SE))"""
        ).find(text)?.groupValues?.getOrNull(1)?.trim()

        return company?.takeIf { it.length in 4..120 }
    }

    private fun rawContextAround(html: String, start: Int, end: Int): String {
        val from = (start - 900).coerceAtLeast(0)
        val to = (end + 1800).coerceAtMost(html.length - 1)
        return html.substring(from, to + 1)
    }

    private fun contextAround(html: String, start: Int, end: Int): String =
        cleanHtml(rawContextAround(html, start, end))

    private fun getText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 14_000
            readTimeout = 22_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 Jobradar/0.5"
            )
            setRequestProperty(
                "Accept",
                "text/html,application/xhtml+xml,application/json;q=0.8,*/*;q=0.5"
            )
            setRequestProperty("Accept-Language", "de-DE,de;q=0.9")
            setRequestProperty("Accept-Encoding", "identity")
        }

        try {
            val code = conn.responseCode
            val stream =
                if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }.orEmpty()

            if (code !in 200..299) {
                throw IOException("HTTP $code bei ${URL(url).host}")
            }
            if (body.isBlank()) {
                throw IOException("Leere Antwort von ${URL(url).host}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun absoluteUrl(base: String, href: String): String? {
        if (href.startsWith("#") ||
            href.startsWith("javascript:", true) ||
            href.startsWith("mailto:", true) ||
            href.startsWith("tel:", true)
        ) return null

        return runCatching {
            URL(URL(base), href).toString()
        }.getOrNull()
    }

    private fun cleanHtml(html: String): String =
        htmlDecode(
            html
                .replace(
                    Regex(
                        """<script\b.*?</script>""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ),
                    " "
                )
                .replace(
                    Regex(
                        """<style\b.*?</style>""",
                        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                    ),
                    " "
                )
                .replace(Regex("""<[^>]+>"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
        )

    private fun htmlDecode(s: String): String =
        s.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&auml;", "ä")
            .replace("&ouml;", "ö")
            .replace("&uuml;", "ü")
            .replace("&Auml;", "Ä")
            .replace("&Ouml;", "Ö")
            .replace("&Uuml;", "Ü")
            .replace("&szlig;", "ß")
}
