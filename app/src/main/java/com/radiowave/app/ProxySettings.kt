package com.radiowave.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private val extraLabels = mapOf(
    "restore" to listOf("Восстанавливать воспроизведение последней радиостанции", "Відновлювати відтворення останньої радіостанції", "Resume playback of the last station", "Wiedergabe des letzten Senders fortsetzen", "Reanudar la última emisora", "Retomar a reprodução da última estação", "恢复播放上次收听的电台"),
    "restore_hint" to listOf("Если включено — последняя станция заиграет при запуске приложения, в том числе через подключённый Bluetooth. Если выключено — нажмите Play.", "Якщо ввімкнено — остання станція заграє під час запуску застосунку, зокрема через підключений Bluetooth. Інакше натисніть Play.", "When enabled, the last station plays on app launch, including through connected Bluetooth. Otherwise, press Play.", "Wenn aktiviert, startet der letzte Sender beim App-Start, auch über verbundenes Bluetooth. Sonst Play drücken.", "Al activarlo, la última emisora se reproduce al abrir la app, también por Bluetooth conectado. Si no, pulsa Play.", "Quando ativado, a última estação toca ao abrir o app, inclusive pelo Bluetooth conectado. Caso contrário, toque em Play.", "启用后，启动应用时自动播放上次的电台，包括通过已连接的蓝牙。否则请点击播放。"),
    "proxy" to listOf("Настройки прокси", "Налаштування проксі", "Proxy settings", "Proxy-Einstellungen", "Configuración del proxy", "Configurações de proxy", "代理设置"),
    "off" to listOf("Выключен", "Вимкнено", "Off", "Aus", "Desactivado", "Desativado", "关闭"),
    "host" to listOf("Адрес сервера", "Адреса сервера", "Server address", "Serveradresse", "Dirección del servidor", "Endereço do servidor", "服务器地址"),
    "port" to listOf("Порт", "Порт", "Port", "Port", "Puerto", "Porta", "端口"),
    "username" to listOf("Логин (необязательно)", "Логін (необов’язково)", "Username (optional)", "Benutzername (optional)", "Usuario (opcional)", "Usuário (opcional)", "用户名（可选）"),
    "password" to listOf("Пароль", "Пароль", "Password", "Passwort", "Contraseña", "Senha", "密码"),
    "check" to listOf("Проверить соединение", "Перевірити з’єднання", "Test connection", "Verbindung testen", "Probar conexión", "Testar conexão", "测试连接"),
    "checking" to listOf("Проверка…", "Перевірка…", "Testing…", "Prüfung…", "Comprobando…", "Testando…", "测试中…"),
    "ok" to listOf("Соединение работает (HTTP и HTTPS)", "З’єднання працює (HTTP і HTTPS)", "Connection works (HTTP and HTTPS)", "Verbindung funktioniert (HTTP und HTTPS)", "Conexión correcta (HTTP y HTTPS)", "Conexão funcionando (HTTP e HTTPS)", "连接正常（HTTP 和 HTTPS）"),
    "failed" to listOf("Соединение не удалось. Проверьте сервер, порт, логин и пароль.", "Не вдалося з’єднатися. Перевірте сервер, порт, логін і пароль.", "Connection failed. Check the server, port, username and password.", "Verbindung fehlgeschlagen. Server, Port und Zugangsdaten prüfen.", "Error de conexión. Comprueba servidor, puerto, usuario y contraseña.", "Falha na conexão. Verifique servidor, porta, usuário e senha.", "连接失败，请检查服务器、端口、用户名和密码。"),
    "invalid" to listOf("Укажите адрес без http:// и порт 1–65535. Для SOCKS5 заполните оба поля авторизации или оставьте оба пустыми.", "Укажіть адресу без http:// та порт 1–65535. Для SOCKS5 заповніть обидва поля авторизації або залиште обидва порожніми.", "Enter a host without http:// and a port from 1 to 65535. For SOCKS5, fill both credential fields or leave both empty.", "Adresse ohne http:// und Port 1–65535 eingeben. Für SOCKS5 beide Zugangsfelder ausfüllen oder leer lassen.", "Indica dirección sin http:// y puerto 1–65535. Para SOCKS5, completa ambos campos de acceso o deja ambos vacíos.", "Informe endereço sem http:// e porta 1–65535. No SOCKS5, preencha ambos os campos de acesso ou deixe ambos vazios.", "请输入不含 http:// 的地址和 1–65535 的端口。SOCKS5 的用户名和密码须同时填写或同时留空。"),
    "save" to listOf("Сохранить", "Зберегти", "Save", "Speichern", "Guardar", "Salvar", "保存"),
    "cancel" to listOf("Отмена", "Скасувати", "Cancel", "Abbrechen", "Cancelar", "Cancelar", "取消"),
    "hint" to listOf("Прокси применяется к радиопотокам, каталогу, логотипам и проверке обновлений. После сохранения переподключите станцию. Загрузка APK в браузере использует настройки браузера.", "Проксі застосовується до потоків, каталогу, логотипів та перевірки оновлень. Після збереження перепідключіть станцію. Завантаження APK у браузері використовує його налаштування.", "Proxy applies to streams, catalog, logos and update checks. Reconnect the station after saving. APK downloads in your browser use the browser settings.", "Proxy gilt für Streams, Katalog, Logos und Update-Prüfung. Nach dem Speichern Sender neu verbinden. APK-Downloads verwenden die Browser-Einstellungen.", "El proxy se aplica a emisiones, catálogo, logos y búsqueda de actualizaciones. Reconecta la emisora tras guardar. El APK usa la configuración del navegador.", "O proxy vale para transmissões, catálogo, logos e busca de atualizações. Reconecte a estação após salvar. O APK usa as configurações do navegador.", "代理用于电台流、目录、图标和更新检查。保存后请重新连接电台。浏览器下载 APK 时使用浏览器的设置。")
)

internal fun extraLabel(key: String, language: String): String = extraLabels.getValue(key)[
    when (language) { "uk" -> 1; "en" -> 2; "de" -> 3; "es" -> 4; "pt" -> 5; "zh" -> 6; else -> 0 }]

@Composable
internal fun ProxySettings(language: String) {
    var opened by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { opened = true }, modifier = Modifier.fillMaxWidth()) {
        Text(extraLabel("proxy", language))
    }
    if (!opened) return
    var config by remember { mutableStateOf(RadioNetwork.config()) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun change(value: ProxyConfig) { config = value; message = "" }
    AlertDialog(onDismissRequest = { opened = false },
        title = { Text(extraLabel("proxy", language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("off", "http", "socks5").forEach { type ->
                        FilterChip(selected = config.type == type, enabled = !checking,
                            onClick = { change(config.copy(type = type, port = when {
                                type == "socks5" && config.port == "8080" -> "1080"
                                type == "http" && config.port == "1080" -> "8080"
                                else -> config.port
                            })) }, label = { Text(if (type == "off") extraLabel("off", language) else type.uppercase()) })
                    }
                }
                if (config.type != "off") {
                    OutlinedTextField(config.host, { change(config.copy(host = it.trim())) }, label = { Text(extraLabel("host", language)) }, enabled = !checking, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(config.port, { change(config.copy(port = it)) }, label = { Text(extraLabel("port", language)) }, enabled = !checking, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(config.username, { change(config.copy(username = it)) }, label = { Text(extraLabel("username", language)) }, enabled = !checking, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(config.password, { change(config.copy(password = it)) }, label = { Text(extraLabel("password", language)) }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !checking, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!config.isValid()) Text(extraLabel("invalid", language), color = MaterialTheme.colorScheme.error)
                }
                Text(extraLabel("hint", language), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = config.isValid() && !checking, onClick = {
                    checking = true; message = ""
                    val candidate = config
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val client = RadioNetwork.buildClient(candidate)
                                try {
                                    for (url in listOf("http://example.com/", "https://api.github.com/repos/alex-rek/AF-Radio")) {
                                        client.newCall(Request.Builder().url(url).header("User-Agent", "AF-Radio").build()).execute().use {
                                            check(it.isSuccessful)
                                            check(it.body?.byteStream()?.read() != -1)
                                        }
                                    }
                                } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
                            }
                            message = extraLabel("ok", language)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { message = extraLabel("failed", language) }
                        finally { checking = false }
                    }
                }) { Text(extraLabel(if (checking) "checking" else "check", language)) }
                if (message.isNotEmpty()) Text(message)
            }
        },
        confirmButton = { TextButton(enabled = config.isValid() && !checking, onClick = { RadioNetwork.save(config); opened = false }) { Text(extraLabel("save", language)) } },
        dismissButton = { TextButton(onClick = { opened = false }) { Text(extraLabel("cancel", language)) } })
}
