package com.radiowave.app

import org.junit.Assert.*
import org.junit.Test

class AppUpdatesTest {
    @Test fun comparesNumericVersionsWithoutLexicographicErrors() {
        assertTrue(AppUpdates.isNewer("v1.0.10", "1.0.2"))
        assertTrue(AppUpdates.isNewer("v2.0.0", "1.99.99"))
        assertFalse(AppUpdates.isNewer("v1.0.1", "1.0.2"))
        assertFalse(AppUpdates.isNewer("v1.0.2", "1.0.2"))
    }
    @Test fun ignoresPrereleaseAndMalformedVersions() {
        assertFalse(AppUpdates.isNewer("v1.1.0-beta", "1.0.2"))
        assertFalse(AppUpdates.isNewer("invalid", "1.0.2"))
        assertFalse(AppUpdates.isNewer("v999999999999999999999.0.0", "1.0.2"))
    }
    private fun release(extra: String = "", assets: String = "[]") = """{
        "tag_name":"v1.0.3", "html_url":"https://github.com/alex-rek/AF-Radio/releases/tag/v1.0.3",
        "assets":$assets $extra
    }"""
    @Test fun selectsApkAndSkipsSourceArchives() {
        val result = AppUpdates.parse(release(assets = """[
            {"name":"source.zip","browser_download_url":"https://example.com/source.zip"},
            {"name":"AF-Radio-1.0.3.apk","browser_download_url":"https://github.com/alex-rek/AF-Radio/releases/download/v1.0.3/AF-Radio-1.0.3.apk"}
        ]"""), "1.0.2")!!
        assertEquals("1.0.3", result.version)
        assertTrue(result.apkUrl!!.endsWith(".apk"))
    }
    @Test fun draftAndPrereleaseDoNotOfferUpdates() {
        assertNull(AppUpdates.parse(release(extra = ",\"draft\":true"), "1.0.2"))
        assertNull(AppUpdates.parse(release(extra = ",\"prerelease\":true"), "1.0.2"))
    }
    @Test fun fallsBackToReleasePageWhenApkIsMissingOrOutsideRepository() {
        val result = AppUpdates.parse(release(assets = """[
            {"name":"update.apk","browser_download_url":"https://example.com/update.apk"}
        ]"""), "1.0.2")!!
        assertNull(result.apkUrl)
        assertTrue(result.releaseUrl.endsWith("/tag/v1.0.3"))
    }
    @Test fun installedOrNewerVersionDoesNotOfferDowngrade() {
        assertNull(AppUpdates.parse(release(), "1.0.3"))
        assertNull(AppUpdates.parse(release(), "2.0.0"))
    }
}
