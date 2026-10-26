package com.radiowave.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AppUpdate(val version: String, val releaseUrl: String, val apkUrl: String?)

internal object AppUpdates {
    private const val REPO = "https://github.com/alex-rek/AF-Radio/releases/"

    suspend fun check(currentVersion: String): AppUpdate? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/alex-rek/AF-Radio/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "AF-Radio/$currentVersion")
            if (connection.responseCode == 404) return@withContext null
            require(connection.responseCode == 200) { "GitHub HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            coroutineContext.ensureActive()
            parse(body, currentVersion)
        } finally { connection.disconnect() }
    }

    internal fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(value: String): List<Long>? {
            val groups = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
                .matchEntire(value.trim())?.groupValues?.drop(1) ?: return null
            return groups.map { it.toLongOrNull() ?: return null }
        }
        val newer = parts(candidate) ?: return false
        val current = parts(installed) ?: return false
        for (index in newer.indices) {
            if (newer[index] != current[index]) return newer[index] > current[index]
        }
        return false
    }

    internal fun parse(body: String, installed: String): AppUpdate? {
        val release = JSONObject(body)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val version = release.getString("tag_name")
        if (!isNewer(version, installed)) return null
        val page = release.getString("html_url")
        require(page.startsWith(REPO + "tag/")) { "Unexpected release URL" }
        val assets = release.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0)).mapNotNull { index ->
            val asset = assets?.optJSONObject(index) ?: return@mapNotNull null
            val url = asset.optString("browser_download_url")
            url.takeIf { asset.optString("name").endsWith(".apk", true) && it.startsWith(REPO + "download/") }
        }.firstOrNull()
        return AppUpdate(version.removePrefix("v"), page, apk)
    }
}

private val updateLabels = mapOf(
    "check" to listOf("Проверить обновления", "Перевірити оновлення", "Check for updates", "Nach Updates suchen", "Buscar actualizaciones", "Verificar atualizações", "检查更新"),
    "checking" to listOf("Проверка…", "Перевірка…", "Checking…", "Wird geprüft…", "Comprobando…", "Verificando…", "检查中…"),
    "current" to listOf("Версия", "Версія", "Version", "Version", "Versión", "Versão", "版本"),
    "latest" to listOf("Установлена актуальная версия", "Встановлено актуальну версію", "You are up to date", "Die App ist aktuell", "La aplicación está actualizada", "O aplicativo está atualizado", "已是最新版本"),
    "available" to listOf("Доступна версия", "Доступна версія", "Update available", "Neue Version verfügbar", "Versión disponible", "Versão disponível", "新版本可用"),
    "download" to listOf("Скачать обновление", "Завантажити оновлення", "Download update", "Update herunterladen", "Descargar actualización", "Baixar atualização", "下载更新"),
    "error" to listOf("Не удалось проверить обновления. Проверьте интернет и повторите позже.", "Не вдалося перевірити оновлення. Перевірте інтернет і повторіть пізніше.", "Could not check for updates. Check your connection and try again later.", "Updates konnten nicht geprüft werden. Verbindung prüfen und später erneut versuchen.", "No se pudieron buscar actualizaciones. Comprueba la conexión e inténtalo más tarde.", "Não foi possível verificar atualizações. Verifique a conexão e tente mais tarde.", "无法检查更新，请检查网络并稍后重试。"),
    "hint" to listOf("Обновления с GitHub. APK откроется в браузере; установку нужно подтвердить вручную.", "Оновлення з GitHub. APK відкриється в браузері; установлення потрібно підтвердити вручну.", "Updates from GitHub. The APK opens in your browser; confirm installation manually.", "Updates von GitHub. Die APK wird im Browser geöffnet; Installation manuell bestätigen.", "Actualizaciones de GitHub. El APK se abre en el navegador; confirma la instalación manualmente.", "Atualizações do GitHub. O APK abre no navegador; confirme a instalação manualmente.", "更新来自 GitHub，APK 将在浏览器中打开，请手动确认安装。")
)

@Composable
internal fun AppUpdateSettings(language: String) {
    val context = LocalContext.current
    val installed = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    val index = when (language) { "uk" -> 1; "en" -> 2; "de" -> 3; "es" -> 4; "pt" -> 5; "zh" -> 6; else -> 0 }
    fun label(key: String) = updateLabels.getValue(key)[index]
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AF Radio · ${label("current")} $installed", color = MaterialTheme.colorScheme.onSurface)
        OutlinedButton(enabled = !loading, onClick = {
            scope.launch {
                loading = true
                message = ""
                update = null
                try {
                    update = AppUpdates.check(installed)
                    message = if (update == null) "latest" else "available"
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { message = "error" }
                finally { loading = false }
            }
        }) { Text(label(if (loading) "checking" else "check")) }
        if (message.isNotBlank()) Text(label(message) + (update?.let { " ${it.version}" } ?: ""),
            color = if (message == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        update?.let { release ->
            Button(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl ?: release.releaseUrl))) }
                catch (_: android.content.ActivityNotFoundException) { message = "error" }
            }) { Text(label("download")) }
        }
        Text(label("hint"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
