package com.radiowave.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class StationStore internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("radio_wave", Context.MODE_PRIVATE))

    fun stations(): List<Station> {
        val remote = readSavedStations("radio_browser_stations", isCustom = false)
        val custom = readSavedStations("custom_stations", isCustom = true)
        val removed = prefs.getStringSet("removed_stations", emptySet()).orEmpty()
        return (StationCatalog.builtIn + remote + custom).filterNot { it.id in removed }
    }

    fun deleteStation(id: String) {
        val order = orderedFavorites().map { it.id }.filterNot { it == id }
        val editor = prefs.edit()
            .putStringSet("favorites", favorites() - id)
            .putString("favorite_order", JSONArray(order).toString())
        if (prefs.getString("last_station", null) == id) editor.remove("last_station")
        if (StationCatalog.builtIn.any { it.id == id }) {
            editor.putStringSet("removed_stations",
                prefs.getStringSet("removed_stations", emptySet()).orEmpty() + id)
        }
        for (key in listOf("custom_stations", "radio_browser_stations")) {
            val source = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
            val remaining = JSONArray()
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                if (item.optString("id") != id) remaining.put(item)
            }
            editor.putString(key, remaining.toString())
        }
        editor.apply()
    }

    private fun readSavedStations(key: String, isCustom: Boolean): List<Station> {
        val result = mutableListOf<Station>()
        val raw = prefs.getString(key, "[]") ?: "[]"
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("id").isBlank() || o.optString("name").isBlank() || o.optString("url").isBlank()) continue
                result += Station(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    url = o.getString("url"),
                    country = o.optString("country", "Custom"),
                    genre = o.optString("genre", "Radio"),
                    isCustom = isCustom,
                    logoUrl = o.optString("logoUrl").takeIf { it.startsWith("http://") || it.startsWith("https://") }
                )
            }
        } catch (_: Exception) { }
        return result
    }

    /**
     * Cache a Radio Browser result locally so favorites remain playable by
     * the background MediaSession when the catalog screen is no longer open.
     */
    fun saveRadioBrowserStation(station: Station) {
        val key = "radio_browser_stations"
        val arr = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()
        var replaced = false
        for (i in 0 until arr.length()) {
            val existing = arr.optJSONObject(i) ?: continue
            if (existing.optString("id") == station.id) {
                updated.put(stationJson(station))
                replaced = true
            } else {
                updated.put(existing)
            }
        }
        if (!replaced) updated.put(stationJson(station))
        prefs.edit().putString(key, updated.toString()).apply()
    }

    private fun stationJson(station: Station) = JSONObject()
        .put("id", station.id)
        .put("name", station.name)
        .put("url", station.url)
        .put("country", station.country)
        .put("genre", station.genre)
        .put("logoUrl", station.logoUrl ?: "")

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()

    fun rememberLastStation(id: String) {
        prefs.edit().putString("last_station", id).apply()
    }

    fun lastStation(): Station? {
        val id = prefs.getString("last_station", null) ?: return null
        return stations().firstOrNull { it.id == id }
    }

    fun clearLastStation() {
        prefs.edit().remove("last_station").apply()
    }

    fun favoriteOrder(): List<String> {
        val favorites = favorites()
        val savedOrder = try { JSONArray(prefs.getString("favorite_order", "[]")) }
            catch (_: Exception) { JSONArray() }
        val orderedIds = (0 until savedOrder.length()).map { savedOrder.optString(it) }
            .filter { it in favorites }.distinct()
        if (orderedIds.containsAll(favorites)) return orderedIds
        return (orderedIds + stations().map { it.id }.filter { it in favorites }).distinct()
    }

    fun orderedFavorites(): List<Station> {
        val stations = stations().associateBy { it.id }
        return favoriteOrder().mapNotNull { stations[it] }
    }

    fun moveFavorite(id: String, targetId: String) {
        val ids = favoriteOrder().toMutableList()
        val from = ids.indexOf(id)
        val to = ids.indexOf(targetId)
        if (from < 0 || to < 0 || from == to) return
        ids.add(to, ids.removeAt(from))
        prefs.edit().putString("favorite_order", JSONArray(ids).toString()).apply()
    }

    fun toggleFavorite(id: String): Set<String> {
        val order = favoriteOrder().toMutableList()
        val next = favorites().toMutableSet().apply { if (!add(id)) remove(id) }
        order.remove(id)
        if (id in next) order.add(id)
        prefs.edit().putStringSet("favorites", next)
            .putString("favorite_order", JSONArray(order).toString()).apply()
        return next
    }

    fun add(name: String, url: String, country: String, genre: String) {
        val arr = try { JSONArray(prefs.getString("custom_stations", "[]")) } catch (_: Exception) { JSONArray() }
        arr.put(
            JSONObject().put("id", UUID.randomUUID().toString())
                .put("name", name).put("url", url)
                .put("country", country.ifBlank { "Custom" })
                .put("genre", genre.ifBlank { "Radio" })
        )
        prefs.edit().putString("custom_stations", arr.toString()).apply()
    }
}
