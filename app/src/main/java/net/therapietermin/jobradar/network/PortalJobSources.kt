package net.therapietermin.jobradar.network

import net.therapietermin.jobradar.data.Job
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

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
        val parseHeadings: Boolean = false
    )

    private val configs = listOf(
        PortalConfig(
            source = "Karriereportal Sachsen-Anhalt",
            listingUrl = "https://karriere.sachsen-anhalt.de/stellenangebote",
            linkMustContain = listOf("/stellenangebote/")
        ),
        PortalConfig(
            source = "INTERAMT",
            listingUrl = "https://interamt.de/koop/app/trefferliste?partner=213",
            fixedCity = "Magdeburg",
            linkMustContain = listOf("stellenangebot")
        ),
        PortalConfig(
            source = "INTERAMT",
            listingUrl = "https://interamt.de/koop/app/trefferliste?partner=5629",
            linkMustContain = listOf("stellenangebot")
        ),
        PortalConfig(
            source = "SWM Magdeburg",
            listingUrl = "https://www.sw-magdeburg.de/jobs/",
            fixedEmployer = "Städtische Werke Magdeburg / Beteiligungen",
            fixedCity = "Magdeburg",
            linkMustContain = listOf("/jobs/")
        ),
        PortalConfig(
            source = "MVB Magdeburg",
            listingUrl = "https://www.mvbnet.de/karriere/stellenangebote/",
            fixedEmployer = "Magdeburger Verkehrsbetriebe (MVB)",
            fixedCity = "Magdeburg",
            parseHeadings = true
        ),
        PortalConfig(
            source = "Autobahn GmbH",
            listingUrl = "https://jobs.autobahn.de/jobportal",
            fixedEmployer = "Die Autobahn GmbH des Bundes",
            linkMustContain = listOf("job", "stellen")
        )
    )

    fun scanAll(): List<SourceResult> = configs.map { config ->
        try {
            SourceResult(config.source, scan(config))
        } catch (e: Exception) {
            SourceResult(
                source = config.source,
                jobs = emptyList(),
                error = e.message ?: e.javaClass.simpleName
            )
        }
    }

    private fun scan(config: PortalConfig): List<Job> {
        val listing = getText(config.listingUrl)
        val candidates = linkedMapOf<String, Pair<String, String>>()

        val anchorRegex = Regex(
            """<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        anchorRegex.findAll(listing).forEach { match ->
            val href = htmlDecode(match.groupValues[1].trim())
            val label = cleanHtml(match.groupValues[2])

            if (label.length !in 7..220) return@forEach
            if (!looksLikeJobTitle(label)) return@forEach

            if (config.linkMustContain.isNotEmpty() &&
                config.linkMustContain.none { href.contains(it, ignoreCase = true) }
            ) return@forEach

            val absolute = absoluteUrl(config.listingUrl, href) ?: return@forEach
            candidates.putIfAbsent(absolute, label to absolute)
        }

        if (config.parseHeadings) {
            val headingRegex = Regex(
                """<h[2-4]\b[^>]*>(.*?)</h[2-4]>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            headingRegex.findAll(listing).forEach { m ->
                val title = cleanHtml(m.groupValues[1])
                if (title.length in 7..220 && looksLikeJobTitle(title)) {
                    val key = "${config.listingUrl}#${Integer.toHexString(title.hashCode())}"
                    candidates.putIfAbsent(key, title to config.listingUrl)
                }
            }
        }

        return candidates.values
            .take(80)
            .mapNotNull { (titleFromList, detailUrl) ->
                buildJob(config, titleFromList, detailUrl)
            }
            .distinctBy { it.sourceId }
    }

    private fun buildJob(
        config: PortalConfig,
        titleFromList: String,
        detailUrl: String
    ): Job? {
        var detailText = ""
        var detailHtml = ""

        if (detailUrl != config.listingUrl) {
            runCatching {
                detailHtml = getText(detailUrl)
                detailText = cleanHtml(detailHtml)
            }
        }

        val combined = "$titleFromList $detailText"
        if (!isInTargetArea(combined, config.fixedCity)) return null

        val title = extractTitle(detailHtml).takeIf { !it.isNullOrBlank() }
            ?: titleFromList

        val employer = config.fixedEmployer
            ?: extractEmployer(combined)
            ?: when (config.source) {
                "Karriereportal Sachsen-Anhalt" -> "Land Sachsen-Anhalt"
                "INTERAMT" -> "Öffentlicher Dienst"
                else -> config.source
            }

        val city = config.fixedCity ?: extractCity(combined) ?: "Magdeburg / Umkreis"
        val pay = extractPay(combined)
        val permanent = when {
            combined.contains("unbefristet", ignoreCase = true) -> true
            combined.contains("befristet", ignoreCase = true) -> false
            else -> null
        }

        val sourceId =
            "${config.source}:${Integer.toHexString(detailUrl.hashCode())}:${Integer.toHexString(title.hashCode())}"

        return Job(
            sourceId = sourceId,
            source = config.source,
            title = title.take(220),
            employer = employer.take(180),
            city = city,
            description = detailText.take(12_000),
            pay = pay,
            permanent = permanent,
            url = detailUrl
        )
    }

    private fun looksLikeJobTitle(text: String): Boolean {
        val t = text.lowercase(Locale.GERMAN)

        val navigation = listOf(
            "stellenangebote", "karriere", "bewerbung", "job-alarm",
            "mehr erfahren", "weiterlesen", "zurück", "startseite",
            "datenschutz", "impressum", "kontakt", "alle jobs",
            "onlinebewerbung", "jetzt bewerben"
        )
        if (navigation.any { t == it || t.startsWith("$it ") }) return false

        val jobWords = listOf(
            // Technik / Projekt
            "projekt", "ingenieur", "techniker", "referent", "sachbearbeit",
            "bau", "planung", "infrastruktur", "koordination", "manager",
            "management", "vergabe", "ausschreibung", "beschaffung",
            "einkauf", "maschinenbau", "verkehr", "netz", "energie",
            "leiter", "leitung", "fachkraft", "consult", "steuerung",
            // Medien / Kommunikation
            "medien", "media", "video", "bewegtbild", "redaktion",
            "redakteur", "content", "kommunikation", "öffentlichkeitsarbeit",
            "presse", "social media", "produktion", "kamera",
            // Sozialpädagogik
            "sozialpädagog", "sozialarbeit", "soziale arbeit", "sozialwesen"
        )
        return jobWords.any { it in t }
    }

    private fun isInTargetArea(text: String, fixedCity: String?): Boolean {
        if (!fixedCity.isNullOrBlank()) return true
        val t = text.lowercase(Locale.GERMAN)

        val allowedPlaces = listOf(
            "magdeburg", "stendal", "barleben", "wolmirstedt",
            "hohenwarsleben", "niederndodeleben", "irxleben",
            "schönebeck", "schoenebeck", "burg", "möckern", "moeckern",
            "genthin", "haldensleben", "oschersleben", "calbe",
            "staßfurt", "stassfurt", "wanzleben", "biederitz",
            "osterweddingen", "sülzetal", "suelzetal"
        )
        return allowedPlaces.any { it in t }
    }

    private fun extractCity(text: String): String? {
        val options = listOf(
            "Magdeburg", "Stendal", "Barleben", "Wolmirstedt",
            "Hohenwarsleben", "Niederndodeleben", "Irxleben",
            "Schönebeck", "Burg", "Möckern", "Genthin",
            "Haldensleben", "Oschersleben", "Calbe", "Staßfurt",
            "Wanzleben", "Biederitz", "Osterweddingen", "Sülzetal"
        )
        return options.firstOrNull { text.contains(it, ignoreCase = true) }
    }

    private fun extractPay(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)\bTV-L\s*(?:EG|E)?\s*(?:9[abc]?|1[0-5])\b"""),
            Regex("""(?i)\bTVöD(?:-VKA)?\s*(?:EG|E)?\s*(?:9[abc]?|1[0-5])\b"""),
            Regex("""(?i)\b(?:EG|E)\s*(?:9[abc]?|1[0-5])\b"""),
            Regex("""(?i)\bAVEU\b""")
        )
        return patterns.asSequence()
            .mapNotNull { it.find(text)?.value }
            .firstOrNull()
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
        return known.firstOrNull { text.contains(it, ignoreCase = true) }
    }

    private fun extractTitle(html: String): String? {
        if (html.isBlank()) return null
        val h1 = Regex(
            """<h1\b[^>]*>(.*?)</h1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)?.groupValues?.getOrNull(1)?.let(::cleanHtml)

        return h1?.takeIf { it.length in 7..220 && looksLikeJobTitle(it) }
    }

    private fun getText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 18_000
            readTimeout = 28_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 Jobradar/0.4"
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
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code bei ${URL(url).host}")
            if (body.isBlank()) throw IOException("Leere Antwort von ${URL(url).host}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun absoluteUrl(base: String, href: String): String? {
        if (href.startsWith("#") || href.startsWith("javascript:", true) ||
            href.startsWith("mailto:", true) || href.startsWith("tel:", true)
        ) return null

        return runCatching { URL(URL(base), href).toString() }.getOrNull()
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
