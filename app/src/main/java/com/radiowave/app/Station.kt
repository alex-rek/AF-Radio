package com.radiowave.app

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val country: String = "Custom",
    val genre: String = "Radio",
    val isCustom: Boolean = false,
    val logoUrl: String? = null
) {
    fun resolvedLogoUrl(): String? {
        if (!logoUrl.isNullOrBlank()) return logoUrl
        return try {
            val host = java.net.URI(url).host?.removePrefix("www.") ?: return null
            "https://www.google.com/s2/favicons?domain=${host}&sz=128"
        } catch (_: Exception) { null }
    }
}

object StationCatalog {
    // Public stream URLs can change or be unavailable in some regions. Users can edit/add their own URLs.
    val builtIn = listOf(
        Station("r1", "Radio Paradise — Main Mix", "https://stream.radioparadise.com/mp3-192", "USA", "Eclectic"),
        Station("r2", "SomaFM — Groove Salad", "https://ice.somafm.com/groovesalad", "USA", "Chillout"),
        Station("r3", "SomaFM — Indie Pop Rocks!", "https://ice.somafm.com/indiepop", "USA", "Indie"),
        Station("r4", "SomaFM — Drone Zone", "https://ice.somafm.com/dronezone", "USA", "Ambient"),
        Station("r5", "SomaFM — Secret Agent", "https://ice.somafm.com/secretagent", "USA", "Lounge"),
        Station("r6", "BBC World Service", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service", "UK", "News"),
        Station("r7", "Radio Record — Russian Mix", "https://radiorecord.hostingradio.ru/rr_main96.aacp", "Russia", "Dance"),
        Station("r8", "Radio Record — Trancemission", "https://radiorecord.hostingradio.ru/trance96.aacp", "Russia", "Trance"),
        Station("r9", "Radio Record — Deep", "https://radiorecord.hostingradio.ru/deep96.aacp", "Russia", "Deep House"),
        Station("r10", "Radio Record — Chill-Out", "https://radiorecord.hostingradio.ru/chilout96.aacp", "Russia", "Chillout"),
        Station("r11", "Radio Record — Russian Gold", "https://radiorecord.hostingradio.ru/gold96.aacp", "Russia", "Russian hits"),
        Station("r12", "Radio Record — Techno", "https://radiorecord.hostingradio.ru/techno96.aacp", "Russia", "Techno")
    )
}
