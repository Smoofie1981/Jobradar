package net.therapietermin.jobradar.network

import android.util.Base64
import net.therapietermin.jobradar.data.Job
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JobsucheService {
    private val base = "https://rest.arbeitsagentur.de/jobboerse/jobsuche-service"
    private val apiKey = "jobboerse-jobsuche"

    data class SearchHit(
        val ref: String,
        val profession: String,
        val employer: String,
        val city: String,
        val externalUrl: String?
    )

    fun search(
        what: String? = null,
        where: String = "Magdeburg",
        radius: Int = 50,
        employer: String? = null,
        size: Int = 25
    ): List<SearchHit> {
        val params = linkedMapOf(
            "angebotsart" to "1",
            "wo" to where,
            "umkreis" to radius.toString(),
            "veroeffentlichtseit" to "30",
            "zeitarbeit" to "false",
            "page" to "1",
            "size" to size.toString()
        )
        if (!what.isNullOrBlank()) params["was"] = what
        if (!employer.isNullOrBlank()) params["arbeitgeber"] = employer

        val query = params.entries.joinToString("&") {
            "${enc(it.key)}=${enc(it.value)}"
        }
        val json = getJson("$base/pc/v4/app/jobs?$query")
        val arr = json.optJSONArray("stellenangebote") ?: return emptyList()

        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val ref = firstNonBlank(o, "refnr", "referenznummer", "hashId") ?: continue
                val place = o.optJSONObject("arbeitsort")
                add(
                    SearchHit(
                        ref = ref,
                        profession = firstNonBlank(o, "titel", "stellenangebotsTitel", "beruf") ?: "Stellenangebot",
                        employer = o.optString("arbeitgeber", "Unbekannter Arbeitgeber"),
                        city = place?.optString("ort").orEmpty(),
                        externalUrl = o.optString("externeUrl").takeIf { it.startsWith("http") }
                    )
                )
            }
        }
    }

    fun details(hit: SearchHit): Job {
        val encoded = Base64.encodeToString(hit.ref.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val json = runCatching { getJson("$base/pc/v4/jobdetails/${enc(encoded)}") }.getOrNull()

        val title = json?.let { firstNonBlank(it, "stellenangebotsTitel", "titel", "beruf") }
            ?: hit.profession
        val description = json?.let { firstNonBlank(it, "stellenangebotsBeschreibung", "stellenbeschreibung") }.orEmpty()
        val employer = json?.optString("arbeitgeber")?.takeIf { it.isNotBlank() } ?: hit.employer
        val city = json?.optJSONArray("arbeitsorte")?.optJSONObject(0)?.optString("ort")
            ?.takeIf { it.isNotBlank() } ?: hit.city
        val befristung = json?.optString("befristung").orEmpty()
        val permanent = when {
            befristung.contains("UNBEFRISTET", true) -> true
            befristung.contains("BEFRISTET", true) -> false
            description.contains("unbefristet", true) -> true
            else -> null
        }
        val pay = extractPay("$title $description")
        val url = hit.externalUrl ?: "https://www.arbeitsagentur.de/jobsuche/jobdetail/${hit.ref}"

        return Job(
            sourceId = "BA:${hit.ref}",
            source = "Bundesagentur für Arbeit",
            title = title,
            employer = employer,
            city = city,
            description = description,
            pay = pay,
            permanent = permanent,
            url = url
        )
    }

    private fun getJson(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("X-API-Key", apiKey)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Jobradar/0.2 Android")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("Jobsuche HTTP $code")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun firstNonBlank(o: JSONObject, vararg keys: String): String? =
        keys.asSequence().map { o.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }

    private fun extractPay(text: String): String? {
        val patterns = listOf(
            Regex("""(?i)\b(?:TV-L|TVöD(?:-VKA)?)\s*(?:EG|E)?\s*(1[0-5]|9[abc]?)\b"""),
            Regex("""(?i)\b(?:EG|E)\s*(1[0-5]|9[abc]?)\b""")
        )
        return patterns.asSequence().mapNotNull { it.find(text)?.value }.firstOrNull()
    }
}
