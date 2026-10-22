package com.radiowave.app

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class StationStoreTest {
    private val prefs = MemoryPreferences()
    private val store = StationStore(prefs)

    @Test fun legacyFavoritesGetStableOrderAndMovesSurviveReload() {
        prefs.edit().putStringSet("favorites", setOf("r3", "r1", "r2")).apply()
        assertEquals(listOf("r1", "r2", "r3"), store.favoriteOrder())
        store.moveFavorite("r3", "r1")
        assertEquals(listOf("r3", "r1", "r2"), StationStore(prefs).orderedFavorites().map { it.id })
    }

    @Test fun deletingBuiltInStationRemovesFavoriteAndSurvivesReload() {
        store.toggleFavorite("r1")
        store.toggleFavorite("r2")
        store.deleteStation("r1")
        val reloaded = StationStore(prefs)
        assertFalse(reloaded.stations().any { it.id == "r1" })
        assertEquals(setOf("r2"), reloaded.favorites())
        assertEquals(listOf("r2"), reloaded.favoriteOrder())
    }

    @Test fun malformedSavedEntryDoesNotHideFollowingValidStation() {
        prefs.edit().putString("custom_stations", """[
            {"id":"first","name":"First","url":"https://example.com/first"},
            {"name":"Missing id"},
            42,
            {"id":"last","name":"Last","url":"https://example.com/last"}
        ]""").apply()
        assertEquals(listOf("first", "last"), store.stations().filter { it.isCustom }.map { it.id })
    }

    @Test fun remoteStationIsUpdatedWithoutDuplicationAndCanBeDeleted() {
        val station = Station("rb:test", "First", "https://example.com/stream")
        store.saveRadioBrowserStation(station)
        store.saveRadioBrowserStation(station.copy(name = "Updated"))
        store.toggleFavorite(station.id)
        assertEquals("Updated", store.stations().single { it.id == station.id }.name)
        store.deleteStation(station.id)
        assertFalse(StationStore(prefs).stations().any { it.id == station.id })
        assertFalse(station.id in store.favorites())
    }

    @Test fun readdingFavoriteAppendsItWithoutResettingOtherPositions() {
        listOf("r1", "r2", "r3").forEach { store.toggleFavorite(it) }
        store.moveFavorite("r3", "r1")
        store.toggleFavorite("r1")
        store.toggleFavorite("r1")
        assertEquals(listOf("r3", "r2", "r1"), store.favoriteOrder())
    }

    @Test fun movingMissingStationDoesNotChangeOrder() {
        store.toggleFavorite("r1")
        store.toggleFavorite("r2")
        store.moveFavorite("missing", "r1")
        store.moveFavorite("r1", "missing")
        assertEquals(listOf("r1", "r2"), store.favoriteOrder())
    }

    @Test fun corruptedOrderFallsBackWithoutLosingFavorites() {
        prefs.edit().putStringSet("favorites", setOf("r2", "r1"))
            .putString("favorite_order", "invalid json").apply()
        assertEquals(listOf("r1", "r2"), store.favoriteOrder())
    }
}

/** In-memory Android preferences contract; no device, disk or network required. */
private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String?) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val updates = mutableMapOf<String, Any?>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { updates[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { updates[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { updates[key] = value }
        override fun putLong(key: String, value: Long) = apply { updates[key] = value }
        override fun putFloat(key: String, value: Float) = apply { updates[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
        override fun remove(key: String) = apply { updates[key] = null }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            if (clearAll) values.clear()
            for ((key, value) in updates) {
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }
        override fun apply() { commit() }
    }
}
