package com.radiowave.app

import org.json.JSONArray
import okhttp3.Request
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Small Radio Browser API client. Requests run on Dispatchers.IO from the UI.
 * It uses public API mirrors and tries another mirror if one is unavailable.
 */
object RadioBrowserRepository {
    data class FilterOption(val value: String, val code: String)
    private val filterCache = java.util.concurrent.ConcurrentHashMap<String, List<FilterOption>>()

    suspend fun filterOptions(kind: String): List<FilterOption> {
        require(kind in setOf("countries", "languages", "tags"))
        filterCache[kind]?.let { return it }
        val array = JSONArray(request("/json/$kind?hidebroken=true&order=stationcount&reverse=true&limit=100000"))
        val options = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isBlank() || item.optInt("stationcount") <= 0) return@mapNotNull null
            FilterOption(name, item.optString(if (kind == "countries") "iso_3166_1" else "iso_639")
                .takeUnless { it == "null" }.orEmpty())
        }.distinctBy { it.value }
        filterCache[kind] = options
        return options
    }
    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://fr1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info"
    )

    suspend fun search(name: String, country: String, language: String, tag: String): List<Station> {
        val params = linkedMapOf(
            "hidebroken" to "true",
            "limit" to "100",
            "order" to "clickcount",
            "reverse" to "true"
        )
        if (name.isNotBlank()) params["name"] = name.trim()
        if (country.isNotBlank()) { params["country"] = country.trim(); params["countryExact"] = "true" }
        if (language.isNotBlank()) { params["language"] = language.trim(); params["languageExact"] = "true" }
        if (tag.isNotBlank()) { params["tag"] = tag.trim(); params["tagExact"] = "true" }

        val query = params.entries.joinToString("&") { (key, value) ->
            "${enc(key)}=${enc(value)}"
        }
        return parse(request("/json/stations/search?$query"))
    }

    private suspend fun request(path: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (mirror in mirrors) {
            currentCoroutineContext().ensureActive()
            try {
                val request = Request.Builder().url("$mirror$path").header("Accept", "application/json")
                    .header("User-Agent", "AF-Radio").build()
                RadioNetwork.client().newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Radio Browser HTTP ${response.code}" }
                    val body = requireNotNull(response.body).string()
                    currentCoroutineContext().ensureActive()
                    return@withContext body
                }
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Не удалось подключиться к Radio Browser")
    }

    private fun parse(body: String): List<Station> {
        val array = JSONArray(body)
        val result = ArrayList<Station>(array.length())
        val seen = HashSet<String>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uuid = item.optString("stationuuid").trim()
            val name = item.optString("name").trim()
            val resolved = item.optString("url_resolved").trim()
            val streamUrl = if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved
                else item.optString("url").trim()
            if (uuid.isBlank() || name.isBlank() ||
                !(streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) ||
                !seen.add(uuid)
            ) continue
            val country = item.optString("country").ifBlank { "Unknown" }
            val tags = item.optString("tags").split(",")
                .map { it.trim() }.filter { it.isNotBlank() }.take(3).joinToString(", ")
                .ifBlank { "Radio" }
            result += Station(
                id = "rb:$uuid",
                name = name,
                url = streamUrl,
                country = country,
                genre = tags,
                isCustom = false,
                logoUrl = item.optString("favicon").trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
            )
        }
        return result
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
