package net.therapietermin.jobradar.network

import android.util.Base64
import net.therapietermin.jobradar.data.Job
import net.therapietermin.jobradar.domain.SalaryParser
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JobsucheService {
    private val base = "https://rest.arbeitsagentur.de/jobboerse/jobsuche-service"
    private val apiKey = "jobboerse-jobsuche"

    private val jobsucheUserAgent =
        "Jobsuche/2.9.2 (de.arbeitsagentur.jobboerse; build:1077; iOS 15.1.0) Alamofire/5.4.4"

    data class SearchHit(
        val ref: String,
        val profession: String,
        val employer: String,
        val city: String,
        val externalUrl: String?
    )

    class HttpStatusException(val code: Int, message: String) : IOException(message)

    fun search(
        what: String? = null,
        where: String = "Magdeburg",
        radius: Int = 50,
        employer: String? = null,
        size: Int = 50
    ): List<SearchHit> {
        val params = linkedMapOf(
            "angebotsart" to "1",
            "wo" to where,
            "umkreis" to radius.toString(),
            "page" to "1",
            "size" to size.toString(),
            "pav" to "false"
        )
        if (!what.isNullOrBlank()) params["was"] = what
        if (!employer.isNullOrBlank()) params["arbeitgeber"] = employer

        val query = params.entries.joinToString("&") {
            "${enc(it.key)}=${enc(it.value)}"
        }

        val endpoints = listOf(
            "$base/pc/v6/jobs?$query",
            "$base/pc/v4/app/jobs?$query",
            "$base/pc/v4/jobs?$query"
        )

        var lastError: Exception? = null
        for (url in endpoints) {
            try {
                val json = getJson(url)
                val arr = json.optJSONArray("stellenangebote") ?: continue

                return buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val ref = firstNonBlank(o, "refnr", "referenznummer", "hashId") ?: continue
                        val place = o.optJSONObject("arbeitsort")

                        add(
                            SearchHit(
                                ref = ref,
                                profession = firstNonBlank(
                                    o, "titel", "stellenangebotsTitel", "beruf"
                                ) ?: "Stellenangebot",
                                employer = o.optString(
                                    "arbeitgeber",
                                    "Unbekannter Arbeitgeber"
                                ),
                                city = place?.optString("ort").orEmpty(),
                                externalUrl = o.optString("externeUrl")
                                    .takeIf { it.startsWith("http") }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(300)
            }
        }

        if (lastError != null) throw lastError
        return emptyList()
    }

    fun asBasicJob(hit: SearchHit): Job {
        val url = hit.externalUrl
            ?: "https://www.arbeitsagentur.de/jobsuche/jobdetail/${hit.ref}"

        return Job(
            sourceId = "BA:${hit.ref}",
            source = "Bundesagentur für Arbeit",
            title = hit.profession,
            employer = hit.employer,
            city = hit.city,
            url = url
        )
    }

    fun details(hit: SearchHit): Job {
        val encoded = Base64.encodeToString(
            hit.ref.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP
        )

        val json = getJson("$base/pc/v4/jobdetails/${enc(encoded)}")

        val title = firstNonBlank(json, "stellenangebotsTitel", "titel", "beruf")
            ?: hit.profession
        val description = firstNonBlank(
            json,
            "stellenangebotsBeschreibung",
            "stellenbeschreibung"
        ).orEmpty()

        val employer = json.optString("arbeitgeber")
            .takeIf { it.isNotBlank() }
            ?: hit.employer

        val city = json.optJSONArray("arbeitsorte")
            ?.optJSONObject(0)
            ?.optString("ort")
            ?.takeIf { it.isNotBlank() }
            ?: hit.city

        val befristung = json.optString("befristung").orEmpty()
        val permanent = when {
            befristung.contains("UNBEFRISTET", true) -> true
            befristung.contains("BEFRISTET", true) -> false
            description.contains("unbefristet", true) -> true
            else -> null
        }

        val salaryText =
            "$title $description ${json.optString("verguetung")} ${json.optString("eintrittsdatum")}"
        val pay = SalaryParser.extractDisplay(salaryText)

        val url = hit.externalUrl
            ?: "https://www.arbeitsagentur.de/jobsuche/jobdetail/${hit.ref}"

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
        var lastError: Exception? = null

        for (attempt in 0..1) {
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("X-API-Key", apiKey)
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Language", "de-DE,de;q=0.9")
                    setRequestProperty("User-Agent", jobsucheUserAgent)
                    setRequestProperty("Connection", "keep-alive")
                }

                try {
                    val code = conn.responseCode
                    val stream =
                        if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                    if (code !in 200..299) {
                        val shortBody = body.replace("\n", " ").take(180)
                        throw HttpStatusException(
                            code,
                            "BA HTTP $code${if (shortBody.isNotBlank()) ": $shortBody" else ""}"
                        )
                    }

                    return JSONObject(body)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                lastError = e
                val retryable = e is HttpStatusException &&
                    (e.code == 403 || e.code == 429 || e.code >= 500)

                if (attempt == 0 && retryable) {
                    Thread.sleep(1200)
                    continue
                }
                throw e
            }
        }

        throw lastError ?: IOException("Keine Antwort")
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun firstNonBlank(o: JSONObject, vararg keys: String): String? =
        keys.asSequence()
            .map { o.optString(it) }
            .firstOrNull { it.isNotBlank() && it != "null" }
}
