package com.radiowave.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val DarkBg = Color(0xFF101018)
private val DarkPanel = Color(0xFF1B1B28)
private val AccentPalette = linkedMapOf(
    "purple" to (Color(0xFFAA8BFA) to Color(0xFF6942B5)),
    "blue" to (Color(0xFF82B5FF) to Color(0xFF225CB5)),
    "teal" to (Color(0xFF65D9D0) to Color(0xFF006B65)),
    "green" to (Color(0xFF93D89C) to Color(0xFF286B36)),
    "orange" to (Color(0xFFFFB777) to Color(0xFF984700)),
    "pink" to (Color(0xFFFF9BBF) to Color(0xFFA12C5C))
)
private val Muted = Color(0xFF9A9AAF)
private val LightBg = Color(0xFFF4F5FA)
private val LightPanel = Color(0xFFFFFFFF)

private fun localText(text: String, language: String): String = if (language != "zh") text else when (text) {
    "Не удалось открыть поток. Проверьте URL или попробуйте позже." -> "无法打开音频流。请检查链接或稍后重试。"
    "Станции не найдены. Измените запрос или фильтры." -> "未找到电台。请修改搜索内容或筛选条件。"
    "Не удалось загрузить Radio Browser. Проверьте интернет и попробуйте ещё раз." -> "无法加载 Radio Browser。请检查网络后重试。"
    "Плеер запускается. Попробуйте ещё раз через секунду." -> "播放器正在启动，请稍后重试。"
    "Нет результатов. Попробуйте изменить название или фильтры." -> "没有搜索结果，请修改名称或筛选条件。"
    "Ничего не найдено. Добавьте станцию по URL через кнопку +." -> "未找到电台。点击 +，通过音频流链接添加电台。"
    "Подключение…" -> "连接中…"
    "СЕЙЧАС ИГРАЕТ" -> "正在播放"
    "ВЫБРАННАЯ СТАНЦИЯ" -> "已选电台"
    "Выберите станцию в списке" -> "请从列表中选择电台"
    "Выберите радиостанцию" -> "请选择电台"
    "Пауза" -> "暂停"
    "Воспроизвести" -> "播放"
    "Избранное" -> "收藏"
    "Остановить" -> "停止"
    "Добавить радиостанцию" -> "添加电台"
    "Название" -> "名称"
    "URL аудиопотока" -> "音频流链接"
    "Страна (необязательно)" -> "国家（可选）"
    "Жанр (необязательно)" -> "类型（可选）"
    "Нужен прямой URL аудиопотока, а не обычная страница сайта." -> "请输入音频流的直接链接，而不是网站页面地址。"
    "Введите название станции" -> "请输入电台名称"
    "URL должен начинаться с http:// или https://" -> "链接必须以 http:// 或 https:// 开头"
    "Добавить" -> "添加"
    "Отмена" -> "取消"
    else -> text
}

class MainActivity : ComponentActivity() {
    private var controllerState by mutableStateOf<MediaController?>(null)
    private lateinit var controllerFuture: ListenableFuture<MediaController>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            try { controllerState = controllerFuture.get() } catch (_: Exception) { }
        }, androidx.core.content.ContextCompat.getMainExecutor(this))
        setContent { RadioWaveApp(controllerState) }
    }

    override fun onDestroy() {
        MediaController.releaseFuture(controllerFuture)
        super.onDestroy()
    }
}

@Composable
private fun RadioWaveApp(controller: MediaController?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { StationStore(context) }
    val prefs = remember { context.getSharedPreferences("af_radio_settings", android.content.Context.MODE_PRIVATE) }
    val systemLocales = androidx.compose.ui.platform.LocalConfiguration.current.locales
    val supportedLanguages = setOf("ru", "uk", "en", "de", "es", "pt", "zh")
    val systemLanguage = (0 until systemLocales.size())
        .map { systemLocales[it].language }
        .firstOrNull { it in supportedLanguages } ?: "en"
    var languageOverride by remember { mutableStateOf(prefs.getString("language", null)) }
    val appLanguage = languageOverride?.takeIf { it in supportedLanguages } ?: systemLanguage
    var startSection by remember { mutableStateOf(prefs.getString("start_section", "my_stations") ?: "my_stations") }
    var continueAfterBt by remember { mutableStateOf(prefs.getBoolean("continue_after_bt", false)) }
    var rememberLastStation by remember { mutableStateOf(prefs.getBoolean("remember_last_station", true)) }
    var themeMode by remember {
        mutableStateOf(prefs.getString("theme_mode", null) ?: if (prefs.contains("light_theme")) {
            if (prefs.getBoolean("light_theme", false)) "light" else "dark"
        } else "auto")
    }
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val lightTheme = when (themeMode) {
        "light" -> true
        "dark" -> false
        else -> !systemDarkTheme
    }
    var accentChoice by remember { mutableStateOf(prefs.getString("accent_color", "purple") ?: "purple") }
    var bufferSeconds by remember { mutableFloatStateOf(prefs.getInt("buffer_seconds", 2).toFloat()) }
    var lowGain by remember { mutableFloatStateOf(prefs.getInt("eq_low", 0).toFloat()) }
    var midGain by remember { mutableFloatStateOf(prefs.getInt("eq_mid", 0).toFloat()) }
    var highGain by remember { mutableFloatStateOf(prefs.getInt("eq_high", 0).toFloat()) }
    var showSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var stations by remember { mutableStateOf(store.stations()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var favoriteOrder by remember { mutableStateOf(store.favoriteOrder()) }
    var query by rememberSaveable { mutableStateOf("") }
    var activeSection by rememberSaveable { mutableStateOf(startSection) }
    val favoritesOnly = activeSection == "favorites"
    var showAdd by remember { mutableStateOf(false) }
    var stationToDelete by remember { mutableStateOf<Station?>(null) }
    val catalogTab = activeSection == "catalog"
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var countryFilter by rememberSaveable { mutableStateOf("") }
    var languageFilter by rememberSaveable { mutableStateOf("") }
    var genreFilter by rememberSaveable { mutableStateOf("") }
    var catalogStations by remember { mutableStateOf<List<Station>>(emptyList()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf("") }
    var currentId by remember { mutableStateOf(controller?.currentMediaItem?.mediaId ?: "") }
    var isPlaying by remember { mutableStateOf(controller?.isPlaying == true) }
    var isBuffering by remember { mutableStateOf(controller?.playbackState == Player.STATE_BUFFERING) }
    var errorMessage by remember { mutableStateOf("") }

    DisposableEffect(controller, appLanguage) {
        // A controller can connect to an already-playing background session.
        currentId = controller?.currentMediaItem?.mediaId.orEmpty()
        isPlaying = controller?.isPlaying == true
        isBuffering = controller?.playbackState == Player.STATE_BUFFERING
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                currentId = mediaItem?.mediaId.orEmpty()
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) errorMessage = ""
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                errorMessage = localText("Не удалось открыть поток. Проверьте URL или попробуйте позже.", appLanguage)
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    // Radio Browser calls are debounced and run off the main thread.
    LaunchedEffect(catalogTab, catalogQuery, countryFilter, languageFilter, genreFilter) {
        if (catalogTab) {
            delay(450)
            catalogLoading = true
            catalogError = ""
            try {
                catalogStations = withContext(Dispatchers.IO) {
                    RadioBrowserRepository.search(
                        name = catalogQuery,
                        country = countryFilter,
                        language = languageFilter,
                        tag = genreFilter
                    )
                }
                if (catalogStations.isEmpty()) catalogError = localText("Станции не найдены. Измените запрос или фильтры.", appLanguage)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                catalogStations = emptyList()
                catalogError = localText("Не удалось загрузить Radio Browser. Проверьте интернет и попробуйте ещё раз.", appLanguage)
            } finally {
                catalogLoading = false
            }
        }
    }

    val current = stations.firstOrNull { it.id == currentId }
    val filtered = remember(stations, favorites, activeSection, query) {
        stations.filter { station ->
            (activeSection != "my_stations" || station.isCustom || station.id.startsWith("rb:")) &&
                (!favoritesOnly || favorites.contains(station.id)) &&
                (query.isBlank() || station.name.contains(query, true) ||
                    station.country.contains(query, true) || station.genre.contains(query, true))
        }
    }
    val visibleStations = remember(catalogTab, catalogStations, favoritesOnly, filtered, favoriteOrder) {
        if (catalogTab) catalogStations else if (favoritesOnly) {
            val order = favoriteOrder.withIndex().associate { it.value to it.index }
            filtered.sortedBy { order[it.id] ?: Int.MAX_VALUE }
        } else filtered
    }
    LaunchedEffect(favorites) {
        favoriteOrder = store.favoriteOrder()
    }
    val reorderState = rememberFavoriteReorderState()
    val visibleStationIds = remember(visibleStations) { visibleStations.map { it.id } }
    LaunchedEffect(currentId, visibleStationIds) {
        val index = visibleStationIds.indexOf(currentId)
        if (index < 0 || reorderState.draggedId != null) return@LaunchedEffect
        withFrameNanos { }
        val layout = reorderState.listState.layoutInfo
        val currentItem = layout.visibleItemsInfo.firstOrNull { it.key == currentId }
        val fullyVisible = currentItem != null && currentItem.offset >= layout.viewportStartOffset &&
            currentItem.offset + currentItem.size <= layout.viewportEndOffset
        if (!fullyVisible && reorderState.draggedId == null) {
            reorderState.listState.animateScrollToItem(index)
        }
    }
    val reorderModifier = favoriteReorderModifier(reorderState, !catalogTab && favoritesOnly,
        visibleStations.map { it.id }) { id, targetId ->
        store.moveFavorite(id, targetId)
        favoriteOrder = store.favoriteOrder()
    }

    val bg = if (lightTheme) LightBg else DarkBg
    val panel = if (lightTheme) LightPanel else DarkPanel
    val foreground = if (lightTheme) Color(0xFF171724) else Color.White
    val accentPair = AccentPalette[accentChoice] ?: AccentPalette.getValue("purple")
    val accent = if (lightTheme) accentPair.second else accentPair.first
    val muted = if (lightTheme) Color(0xFF55566C) else Color(0xFFB8B8C9)
    MaterialTheme(colorScheme = if (lightTheme) lightColorScheme(background = bg, surface = panel, primary = accent, onPrimary = Color.White, primaryContainer = accent.copy(alpha = 0.15f).compositeOver(panel), onPrimaryContainer = foreground, secondary = accent, tertiary = accent, onBackground = foreground, onSurface = foreground, onSurfaceVariant = muted) else darkColorScheme(background = bg, surface = panel, primary = accent, onPrimary = DarkBg, primaryContainer = accent.copy(alpha = 0.22f).compositeOver(panel), onPrimaryContainer = foreground, secondary = accent, tertiary = accent, onBackground = foreground, onSurface = foreground, onSurfaceVariant = muted)) {
        Box(Modifier.fillMaxSize().background(bg)) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (lightTheme) R.drawable.af_tundra_day_background else R.drawable.af_tundra_aurora_background),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.matchParentSize()
            )
            // Darken the sky for readability while keeping the tundra visible below.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                0f to bg.copy(alpha = if (lightTheme) 0.18f else 0.28f),
                0.52f to bg.copy(alpha = if (lightTheme) 0.25f else 0.65f),
                0.66f to bg.copy(alpha = if (lightTheme) 0.12f else 0.25f),
                1f to bg.copy(alpha = if (lightTheme) 0.05f else 0.12f)
            )))
        Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = Color.Transparent,
            topBar = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.radiowave.app.R.drawable.af_fox),
                            contentDescription = "Arctic Fox",
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("AF Radio", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = foreground)
                            Text(tr("slogan", appLanguage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, contentDescription = tr("search", appLanguage), tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = tr("settings", appLanguage), tint = MaterialTheme.colorScheme.primary) }
                        IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, contentDescription = tr("add_station", appLanguage), tint = MaterialTheme.colorScheme.primary) }
                    }
                    Spacer(Modifier.height(8.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val expanded = maxWidth >= 600.dp
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StationNavigationButton(tr("all_stations", appLanguage), Icons.AutoMirrored.Filled.QueueMusic,
                                activeSection == "all_stations", expanded, Modifier.weight(1f)) {
                                activeSection = "all_stations"
                            }
                            StationNavigationButton(tr("my_stations", appLanguage), Icons.Default.LibraryMusic,
                                activeSection == "my_stations", expanded, Modifier.weight(1f)) { activeSection = "my_stations" }
                            StationNavigationButton(tr("catalog_short", appLanguage), Icons.Default.Public,
                                activeSection == "catalog", expanded, Modifier.weight(1f)) { activeSection = "catalog" }
                            StationNavigationButton(tr("favorites", appLanguage), Icons.Default.Favorite,
                                activeSection == "favorites", expanded, Modifier.weight(1f)) {
                                activeSection = "favorites"
                            }
                        }
                    }
                    if (catalogTab) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CatalogFilterDropdown("countries", tr("country", appLanguage), countryFilter,
                                appLanguage, Modifier.weight(1f)) { countryFilter = it }
                            CatalogFilterDropdown("languages", tr("language", appLanguage), languageFilter,
                                appLanguage, Modifier.weight(1f)) { languageFilter = it }
                            CatalogFilterDropdown("tags", tr("genre", appLanguage), genreFilter,
                                appLanguage, Modifier.weight(1f)) { genreFilter = it }
                        }
                        Text("${catalogStations.size} ${tr("stations_found", appLanguage)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (favoritesOnly && filtered.size > 1) {
                                Text(tr("reorder_hint", appLanguage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                                    modifier = Modifier.weight(1f))
                            } else Spacer(Modifier.weight(1f))
                            Text("${filtered.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            },
            bottomBar = {
                if (current != null) {
                    NowPlayingBar(current, isPlaying, isBuffering, language = appLanguage, onPlayPause = {
                        if (controller != null) {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                    }, onStop = { controller?.stop() })
                }
            }
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val tabletLayout = maxWidth >= 600.dp
                val wideTabletLayout = maxWidth >= 720.dp
                val compactPlayer = maxHeight < 400.dp
                val shortPlayer = maxHeight < 240.dp
                val selectedStation = (visibleStations.firstOrNull { it.id == currentId }
                    ?: visibleStations.firstOrNull())
                Column(Modifier.fillMaxSize().padding(horizontal = if (tabletLayout) 20.dp else 0.dp)) {
                    if (errorMessage.isNotBlank()) {
                        Text(errorMessage, color = Color(0xFFFF8A8A), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    if (catalogTab && catalogError.isNotBlank()) {
                        Text(catalogError, color = if ((catalogError.startsWith("Не удалось") || catalogError.startsWith("无法"))) Color(0xFFFF8A8A) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
                    }
                    if (wideTabletLayout) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            LazyColumn(
                                state = reorderState.listState,
                                modifier = Modifier.weight(1.15f).then(reorderModifier),
                                contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                items(visibleStations, key = { it.id }) { station ->
                                    StationRow(station, station.id == currentId, favorites.contains(station.id),
                                        language = appLanguage,
                                        onDelete = if (!catalogTab && !favoritesOnly) ({ stationToDelete = station }) else null,
                                        deleteLabel = tr("delete_station", appLanguage),
                                        modifier = reorderState.itemModifier(station.id), onClick = {
                                        if (controller != null) {
                                            if (station.id.startsWith("rb:")) store.saveRadioBrowserStation(station)
                                            stations = store.stations(); currentId = station.id
                                            controller.setMediaItem(RadioPlaybackService.item(station)); controller.prepare(); controller.play(); errorMessage = ""
                                        } else errorMessage = localText("Плеер запускается. Попробуйте ещё раз через секунду.", appLanguage)
                                    }, onFavorite = {
                                        if (station.id.startsWith("rb:")) { store.saveRadioBrowserStation(station); stations = store.stations() }
                                        favorites = store.toggleFavorite(station.id)
                                    })
                                }
                                if (visibleStations.isEmpty() && !catalogLoading) item { Text(if (catalogTab) localText("Нет результатов. Попробуйте изменить название или фильтры.", appLanguage) else localText("Ничего не найдено. Добавьте станцию по URL через кнопку +.", appLanguage), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
                            }
                            Column(Modifier.weight(0.85f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(panel)
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(if (compactPlayer) 12.dp else 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = if (compactPlayer) Arrangement.Top else Arrangement.Center) {
                                if (selectedStation != null) {
                                    if (!shortPlayer) {
                                        StationArtwork(selectedStation, Modifier.size(if (compactPlayer) 72.dp else 150.dp))
                                        Spacer(Modifier.height(if (compactPlayer) 8.dp else 22.dp))
                                    }
                                    Text(selectedStation.name, color = MaterialTheme.colorScheme.onSurface, fontSize = if (compactPlayer) 16.sp else 22.sp, fontWeight = FontWeight.Bold, maxLines = if (compactPlayer) 2 else 3, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(Modifier.height(if (compactPlayer) 4.dp else 8.dp))
                                    Text("${selectedStation.country} · ${selectedStation.genre}", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    Spacer(Modifier.height(if (compactPlayer) 6.dp else 24.dp))
                                    Text(if (isBuffering) localText("Подключение…", appLanguage) else if (isPlaying && currentId == selectedStation.id) localText("СЕЙЧАС ИГРАЕТ", appLanguage) else if (currentId == selectedStation.id) localText("ВЫБРАННАЯ СТАНЦИЯ", appLanguage) else localText("Выберите станцию в списке", appLanguage), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(if (compactPlayer) 6.dp else 14.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        IconButton(onClick = { if (controller != null) { if (controller.isPlaying) controller.pause() else controller.play() } }, modifier = Modifier.size(if (compactPlayer) 48.dp else 58.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) localText("Пауза", appLanguage) else localText("Воспроизвести", appLanguage), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp))
                                        }
                                        IconButton(onClick = { if (selectedStation.id.startsWith("rb:")) { store.saveRadioBrowserStation(selectedStation); stations = store.stations() }; favorites = store.toggleFavorite(selectedStation.id) }) {
                                            Icon(if (favorites.contains(selectedStation.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = localText("Избранное", appLanguage), tint = if (favorites.contains(selectedStation.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                        }
                                        IconButton(onClick = { controller?.stop() }) { Icon(Icons.Default.Stop, contentDescription = localText("Остановить", appLanguage), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)) }
                                    }
                                } else Text(localText("Выберите радиостанцию", appLanguage), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(state = reorderState.listState, modifier = reorderModifier,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(visibleStations, key = { it.id }) { station ->
                                StationRow(station, station.id == currentId, favorites.contains(station.id),
                                    language = appLanguage,
                                    onDelete = if (!catalogTab && !favoritesOnly) ({ stationToDelete = station }) else null,
                                    deleteLabel = tr("delete_station", appLanguage),
                                    modifier = reorderState.itemModifier(station.id), onClick = {
                                    if (controller != null) {
                                        if (station.id.startsWith("rb:")) store.saveRadioBrowserStation(station)
                                        stations = store.stations(); currentId = station.id
                                        controller.setMediaItem(RadioPlaybackService.item(station)); controller.prepare(); controller.play(); errorMessage = ""
                                    } else errorMessage = localText("Плеер запускается. Попробуйте ещё раз через секунду.", appLanguage)
                                }, onFavorite = {
                                    if (station.id.startsWith("rb:")) { store.saveRadioBrowserStation(station); stations = store.stations() }
                                    favorites = store.toggleFavorite(station.id)
                                })
                            }
                            if (visibleStations.isEmpty() && !catalogLoading) item { Text(if (catalogTab) localText("Нет результатов. Попробуйте изменить название или фильтры.", appLanguage) else localText("Ничего не найдено. Добавьте станцию по URL через кнопку +.", appLanguage), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
                        }
                    }
                }
            }
        }

        }

        stationToDelete?.let { station ->
            AlertDialog(
                onDismissRequest = { stationToDelete = null },
                title = { Text(tr("delete_station", appLanguage)) },
                text = { Text(tr("delete_station_confirm", appLanguage).replace("{station}", station.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (controller?.currentMediaItem?.mediaId == station.id) {
                            controller.stop()
                            controller.clearMediaItems()
                            currentId = ""
                        }
                        store.deleteStation(station.id)
                        stations = store.stations()
                        favorites = store.favorites()
                        favoriteOrder = store.favoriteOrder()
                        stationToDelete = null
                    }) { Text(tr("delete_station", appLanguage)) }
                },
                dismissButton = { TextButton(onClick = { stationToDelete = null }) { Text(tr("cancel", appLanguage)) } },
                containerColor = panel
            )
        }

        if (showSearch) {
            AlertDialog(
                onDismissRequest = { showSearch = false },
                title = { Text(tr("search", appLanguage)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = if (catalogTab) catalogQuery else query, onValueChange = { if (catalogTab) catalogQuery = it else query = it }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("station_search", appLanguage)) }, singleLine = true)
                        Text(if (catalogTab) tr("catalog_search_hint", appLanguage) else tr("my_station_search_hint", appLanguage), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showSearch = false }) { Text(tr("done", appLanguage)) } },
                dismissButton = { TextButton(onClick = { if (catalogTab) catalogQuery = "" else query = "" }) { Text(tr("clear", appLanguage)) } },
                containerColor = panel
            )
        }
        if (showSettings) {
            SettingsDialog(
                rememberLastStation = rememberLastStation, onRememberLastStation = { enabled ->
                    rememberLastStation = enabled
                    prefs.edit().putBoolean("remember_last_station", enabled).apply()
                    if (enabled) controller?.currentMediaItem?.mediaId?.let(store::rememberLastStation)
                    else store.clearLastStation()
                },
                accentChoice = accentChoice, onAccentChoice = { accentChoice = it; prefs.edit().putString("accent_color", it).apply() },
                startSection = startSection, onStartSection = { startSection = it; prefs.edit().putString("start_section", it).apply() },
                continueAfterBt = continueAfterBt, onContinueAfterBt = { continueAfterBt = it; prefs.edit().putBoolean("continue_after_bt", it).apply() },
                language = appLanguage, onLanguage = { languageOverride = it; prefs.edit().putString("language", it).apply() },
                themeMode = themeMode, onThemeMode = { themeMode = it; prefs.edit().putString("theme_mode", it).apply() },
                buffer = bufferSeconds, onBuffer = { bufferSeconds = it; prefs.edit().putInt("buffer_seconds", it.toInt()).apply() },
                low = lowGain, onLow = { lowGain = it; prefs.edit().putInt("eq_low", it.toInt()).apply(); context.startService(android.content.Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_EQ).putExtra("low", lowGain.toInt()).putExtra("mid", midGain.toInt()).putExtra("high", highGain.toInt())) },
                mid = midGain, onMid = { midGain = it; prefs.edit().putInt("eq_mid", it.toInt()).apply(); context.startService(android.content.Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_EQ).putExtra("low", lowGain.toInt()).putExtra("mid", midGain.toInt()).putExtra("high", highGain.toInt())) },
                high = highGain, onHigh = { highGain = it; prefs.edit().putInt("eq_high", it.toInt()).apply(); context.startService(android.content.Intent(context, RadioPlaybackService::class.java).setAction(RadioPlaybackService.ACTION_UPDATE_EQ).putExtra("low", lowGain.toInt()).putExtra("mid", midGain.toInt()).putExtra("high", highGain.toInt())) },
                onDismiss = { showSettings = false }, isLight = lightTheme
            )
        }

        if (showAdd) {
            AddStationDialog(language = appLanguage, onDismiss = { showAdd = false }, onAdd = { name, url, country, genre ->
                store.add(name, url, country, genre)
                stations = store.stations()
                showAdd = false
            })
        }
    }
}

@Composable
private fun StationNavigationButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.2f))
    ) {
        if (expanded) {
            Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        } else {
            Column(Modifier.heightIn(min = 76.dp).padding(horizontal = 3.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                Text(label, fontSize = 11.sp, lineHeight = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun StationArtwork(station: Station, modifier: Modifier = Modifier, isCurrent: Boolean = false) {
    val logo = station.resolvedLogoUrl()
    var failed by remember(logo) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = .22f) else Color(0xFF292938)), contentAlignment = Alignment.Center) {
        if (!logo.isNullOrBlank() && !failed) {
            AsyncImage(model = logo, contentDescription = "Логотип ${station.name}", modifier = Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(10.dp)), onError = { failed = true })
        } else {
            Icon(Icons.Default.Radio, null, tint = if (isCurrent) MaterialTheme.colorScheme.primary else Color(0xFFB0B0C4), modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun StationRow(station: Station, isCurrent: Boolean, isFavorite: Boolean, language: String, modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null, deleteLabel: String = "", onClick: () -> Unit, onFavorite: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(
            (if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface).copy(alpha = 0.90f))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StationArtwork(station, Modifier.size(48.dp), isCurrent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text("${station.country}  ·  ${station.genre}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = deleteLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = localText("Избранное", language), tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowPlayingBar(station: Station, isPlaying: Boolean, isBuffering: Boolean, language: String, onPlayPause: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), MaterialTheme.colorScheme.primary))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Radio, null, tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isBuffering) localText("Подключение…", language) else if (isPlaying) localText("СЕЙЧАС ИГРАЕТ", language) else localText("ВЫБРАННАЯ СТАНЦИЯ", language), color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                Text(station.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            }
            IconButton(onClick = onStop) { Icon(Icons.Default.Stop, contentDescription = localText("Остановить", language), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) localText("Пауза", language) else localText("Воспроизвести", language), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun AddStationDialog(language: String, onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("Custom") }
    var genre by remember { mutableStateOf("Radio") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localText("Добавить радиостанцию", language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(localText("Название", language)) }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(localText("URL аудиопотока", language)) }, placeholder = { Text("https://...") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(country, { country = it }, label = { Text(localText("Страна (необязательно)", language)) }, singleLine = true)
                OutlinedTextField(genre, { genre = it }, label = { Text(localText("Жанр (необязательно)", language)) }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A8A), fontSize = 12.sp)
                Text(localText("Нужен прямой URL аудиопотока, а не обычная страница сайта.", language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = {
            val normalized = url.trim()
            if (name.isBlank()) error = localText("Введите название станции", language)
            else if (!(normalized.startsWith("https://") || normalized.startsWith("http://"))) error = localText("URL должен начинаться с http:// или https://", language)
            else onAdd(name.trim(), normalized, country.trim(), genre.trim())
        }) { Text(localText("Добавить", language)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(localText("Отмена", language)) } },
        containerColor = DarkPanel
    )
}


private val chineseStrings = mapOf(
    "remember_last_station" to "记住上次收听的电台",
    "remember_station_hint" to "启动时恢复上次的电台，点击播放即可继续收听。",
    "delete_station" to "删除电台",
    "delete_station_confirm" to "要从我的电台和收藏中删除“{station}”吗？",
    "cancel" to "取消", "any_filter" to "全部", "no_filter_matches" to "未找到匹配项",
    "loading" to "加载中…", "retry" to "重试", "filter_load_error" to "无法加载列表",
    "refine_filter" to "请细化搜索以查看更多选项", "reorder_hint" to "长按并拖动以调整顺序",
    "start_section" to "启动时打开", "continue_after_bt" to "蓝牙（BT）断开后继续播放",
    "bt_speaker_hint" to "音频将通过扬声器或其他可用的音频输出继续播放。",
    "slogan" to "只有广播，简单纯粹", "search" to "搜索", "settings" to "设置",
    "add_station" to "添加电台", "my_stations" to "我的电台", "catalog_short" to "电台目录",
    "catalog" to "Radio Browser 电台目录", "country" to "国家", "language" to "语言",
    "genre" to "类型", "stations_found" to "个电台", "all_stations" to "全部电台",
    "favorites" to "收藏", "station_search" to "电台、国家或类型",
    "catalog_search_hint" to "搜索 Radio Browser 电台目录", "my_station_search_hint" to "搜索你的电台",
    "done" to "完成", "clear" to "清除",
    "theme_auto" to "自动（跟随系统）", "theme_light" to "浅色", "theme_dark" to "深色",
    "accent_color" to "强调色", "color_purple" to "紫色", "color_blue" to "蓝色",
    "color_teal" to "青色", "color_green" to "绿色", "color_orange" to "橙色", "color_pink" to "粉色"
)

private val translatedStrings = mapOf(
        "remember_last_station" to listOf("Запоминать последнюю радиостанцию", "Запам’ятовувати останню радіостанцію", "Remember the last station", "Letzten Sender merken", "Recordar la última emisora", "Lembrar a última estação"),
        "remember_station_hint" to listOf("При запуске восстановится последняя станция. Нажмите Play, чтобы продолжить.", "Під час запуску відновиться остання станція. Натисніть Play, щоб продовжити.", "Restore the last station on startup. Press Play to continue.", "Beim Start den letzten Sender wiederherstellen. Zum Fortsetzen Play drücken.", "Restaura la última emisora al iniciar. Pulsa Play para continuar.", "Restaura a última estação ao iniciar. Toque em Play para continuar."),
        "accent_color" to listOf("Цвет элементов", "Колір елементів", "Accent color", "Akzentfarbe", "Color de acento", "Cor de destaque"),
        "color_purple" to listOf("Фиолетовый", "Фіолетовий", "Purple", "Violett", "Morado", "Roxo"),
        "color_blue" to listOf("Синий", "Синій", "Blue", "Blau", "Azul", "Azul"),
        "color_teal" to listOf("Бирюзовый", "Бірюзовий", "Teal", "Türkis", "Turquesa", "Turquesa"),
        "color_green" to listOf("Зелёный", "Зелений", "Green", "Grün", "Verde", "Verde"),
        "color_orange" to listOf("Оранжевый", "Помаранчевий", "Orange", "Orange", "Naranja", "Laranja"),
        "color_pink" to listOf("Розовый", "Рожевий", "Pink", "Rosa", "Rosa", "Rosa"),
        "theme_auto" to listOf("Авто (как в системе)", "Авто (як у системі)", "Auto (system theme)", "Automatisch (Systemdesign)", "Automático (tema del sistema)", "Automático (tema do sistema)"),
        "theme_light" to listOf("Светлая", "Світла", "Light", "Hell", "Claro", "Claro"),
        "theme_dark" to listOf("Тёмная", "Темна", "Dark", "Dunkel", "Oscuro", "Escuro"),
        "delete_station" to listOf("Удалить станцию", "Видалити станцію", "Delete station", "Sender löschen", "Eliminar emisora", "Excluir estação"),
        "delete_station_confirm" to listOf("Удалить «{station}» из ваших станций и избранного?", "Видалити «{station}» з ваших станцій та обраного?", "Remove “{station}” from your stations and favorites?", "„{station}“ aus deinen Sendern und Favoriten entfernen?", "¿Eliminar «{station}» de tus emisoras y favoritos?", "Remover “{station}” das suas estações e favoritos?"),
        "cancel" to listOf("Отмена", "Скасувати", "Cancel", "Abbrechen", "Cancelar", "Cancelar"),
        "any_filter" to listOf("Все", "Усі", "All", "Alle", "Todos", "Todos"),
        "no_filter_matches" to listOf("Ничего не найдено", "Нічого не знайдено", "No matches", "Keine Treffer", "Sin resultados", "Nenhum resultado"),
        "loading" to listOf("Загрузка…", "Завантаження…", "Loading…", "Wird geladen…", "Cargando…", "Carregando…"),
        "retry" to listOf("Повторить", "Повторити", "Retry", "Erneut versuchen", "Reintentar", "Tentar novamente"),
        "filter_load_error" to listOf("Не удалось загрузить список", "Не вдалося завантажити список", "Could not load the list", "Liste konnte nicht geladen werden", "No se pudo cargar la lista", "Não foi possível carregar a lista"),
        "refine_filter" to listOf("Уточните поиск, чтобы увидеть остальные варианты", "Уточніть пошук, щоб побачити інші варіанти", "Refine the search to see more options", "Suche verfeinern, um weitere Optionen zu sehen", "Afina la búsqueda para ver más opciones", "Refine a pesquisa para ver mais opções"),
        "reorder_hint" to listOf("Удерживайте и перетаскивайте для сортировки", "Утримуйте й перетягуйте для сортування", "Hold and drag to reorder", "Zum Sortieren gedrückt halten und ziehen", "Mantén pulsado y arrastra para ordenar", "Toque e segure para arrastar e reordenar"),
        "start_section" to listOf("Раздел при запуске", "Розділ під час запуску", "Startup section", "Startbereich", "Sección al iniciar", "Seção ao iniciar"),
        "continue_after_bt" to listOf("Продолжать воспроизведение после отключения Bluetooth (BT)", "Продовжувати відтворення після відключення Bluetooth (BT)", "Continue playback after Bluetooth (BT) disconnects", "Nach dem Trennen von Bluetooth (BT) weiterspielen", "Continuar la reproducción al desconectar Bluetooth (BT)", "Continuar a reprodução após desconectar o Bluetooth (BT)"),
        "bt_speaker_hint" to listOf("Звук продолжится через динамик или другой доступный аудиовыход.", "Звук продовжиться через динамік або інший доступний аудіовихід.", "Audio continues through the speaker or another available output.", "Der Ton wird über den Lautsprecher oder einen anderen verfügbaren Ausgang wiedergegeben.", "El audio continuará por el altavoz u otra salida disponible.", "O áudio continuará pelo alto-falante ou outra saída disponível."),
        "slogan" to listOf("просто радио и ничего лишнего", "просто радіо і нічого зайвого", "just radio and nothing more", "einfach Radio und sonst nichts", "solo radio y nada más", "só rádio e nada mais"),
        "search" to listOf("Поиск", "Пошук", "Search", "Suche", "Buscar", "Pesquisar"),
        "settings" to listOf("Настройки", "Налаштування", "Settings", "Einstellungen", "Configuración", "Configurações"),
        "add_station" to listOf("Добавить станцию", "Додати станцію", "Add station", "Sender hinzufügen", "Añadir emisora", "Adicionar estação"),
        "my_stations" to listOf("Мои станции", "Мої станції", "My stations", "Meine Sender", "Mis emisoras", "Minhas estações"),
        "catalog_short" to listOf("Каталог", "Каталог", "Catalog", "Katalog", "Catálogo", "Catálogo"),
        "catalog" to listOf("Каталог Radio Browser", "Каталог Radio Browser", "Radio Browser catalog", "Radio-Browser-Katalog", "Catálogo Radio Browser", "Catálogo Radio Browser"),
        "country" to listOf("Страна", "Країна", "Country", "Land", "País", "País"),
        "language" to listOf("Язык", "Мова", "Language", "Sprache", "Idioma", "Idioma"),
        "genre" to listOf("Жанр", "Жанр", "Genre", "Genre", "Género", "Género"),
        "stations_found" to listOf("станций в результатах", "станцій у результатах", "stations found", "Sender gefunden", "emisoras encontradas", "estações encontradas"),
        "all_stations" to listOf("Все станции", "Усі станції", "All stations", "Alle Sender", "Todas", "Todas"),
        "favorites" to listOf("Избранное", "Обране", "Favorites", "Favoriten", "Favoritos", "Favoritos"),
        "station_search" to listOf("Станция, страна или жанр", "Станція, країна або жанр", "Station, country or genre", "Sender, Land oder Genre", "Emisora, país o género", "Estação, país ou género"),
        "catalog_search_hint" to listOf("Поиск в каталоге Radio Browser", "Пошук у каталозі Radio Browser", "Search Radio Browser catalog", "Radio-Browser-Katalog durchsuchen", "Buscar catálogo Radio Browser", "Pesquisar catálogo Radio Browser"),
        "my_station_search_hint" to listOf("Поиск среди ваших станций", "Пошук серед ваших станцій", "Search your stations", "Eigene Sender durchsuchen", "Buscar tus emisoras", "Pesquisar suas estações"),
        "done" to listOf("Готово", "Готово", "Done", "Fertig", "Listo", "Concluído"),
        "clear" to listOf("Очистить", "Очистити", "Clear", "Leeren", "Limpiar", "Limpar")
    )

internal fun tr(key: String, lang: String): String {
    if (lang == "zh") return chineseStrings[key] ?: key
    val index = when (lang) { "uk" -> 1; "en" -> 2; "de" -> 3; "es" -> 4; "pt" -> 5; else -> 0 }
    return translatedStrings[key]?.get(index) ?: key
}

@Composable
private fun SettingsDialog(
    rememberLastStation: Boolean, onRememberLastStation: (Boolean) -> Unit,
    accentChoice: String, onAccentChoice: (String) -> Unit,
    startSection: String, onStartSection: (String) -> Unit,
    continueAfterBt: Boolean, onContinueAfterBt: (Boolean) -> Unit,
    language: String, onLanguage: (String) -> Unit,
    themeMode: String, onThemeMode: (String) -> Unit,
    buffer: Float, onBuffer: (Float) -> Unit,
    low: Float, onLow: (Float) -> Unit,
    mid: Float, onMid: (Float) -> Unit,
    high: Float, onHigh: (Float) -> Unit,
    onDismiss: () -> Unit, isLight: Boolean
) {
    val langs = listOf("ru" to "Русский", "uk" to "Українська", "en" to "English", "de" to "Deutsch", "es" to "Español", "pt" to "Português", "zh" to "简体中文")
    val fg = if (isLight) Color(0xFF171724) else Color.White
    val panel = if (isLight) LightPanel else DarkPanel
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 760.dp),
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text(tr("settings", language)) } },
        text = {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 560.dp
            val settingsScroll = androidx.compose.foundation.rememberScrollState()
            Column(Modifier.fillMaxWidth().heightIn(max = if (wide) 620.dp else 520.dp).verticalScroll(settingsScroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppUpdateSettings(language)
                HorizontalDivider()
                Text(tr("start_section", language), fontWeight = FontWeight.SemiBold, color = fg)
                SettingsDropdown(
                    value = startSection,
                    options = listOf("all_stations", "my_stations", "catalog", "favorites").map { section ->
                        section to tr(if (section == "catalog") "catalog_short" else section, language)
                    },
                    onSelect = onStartSection
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("remember_last_station", language), color = fg, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    Switch(checked = rememberLastStation, onCheckedChange = onRememberLastStation)
                }
                Text(tr("remember_station_hint", language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("continue_after_bt", language), color = fg, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    Switch(checked = continueAfterBt, onCheckedChange = onContinueAfterBt)
                }
                Text(tr("bt_speaker_hint", language), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                HorizontalDivider()
                Text(if (language == "zh") "应用语言" else if (language == "ru") "Язык приложения" else when(language) { "uk" -> "Мова застосунку"; "en" -> "App language"; "de" -> "App-Sprache"; "es" -> "Idioma de la aplicación"; else -> "Idioma do aplicativo" }, fontWeight = FontWeight.SemiBold, color = fg)
                SettingsDropdown(value = language, options = langs, onSelect = onLanguage)
                HorizontalDivider()
                Text(if (language == "zh") "主题" else if (language == "ru") "Тема" else if (language == "uk") "Тема" else if (language == "de") "Design" else if (language == "es") "Tema" else if (language == "pt") "Tema" else "Theme", fontWeight = FontWeight.SemiBold, color = fg)
                SettingsDropdown(value = themeMode,
                    options = listOf("auto", "light", "dark").map { it to tr("theme_$it", language) },
                    onSelect = onThemeMode)
                Text(tr("accent_color", language), fontWeight = FontWeight.SemiBold, color = fg)
                SettingsDropdown(value = accentChoice,
                    options = AccentPalette.keys.map { it to tr("color_$it", language) },
                    onSelect = onAccentChoice,
                    optionColors = AccentPalette.mapValues { if (isLight) it.value.second else it.value.first })
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) { Text(if (language == "zh") "预加载缓冲" else if (language == "en") "Preload buffer" else if (language == "de") "Puffer" else if (language == "es") "Búfer de precarga" else if (language == "pt") "Buffer de pré-carregamento" else if (language == "uk") "Буфер попереднього завантаження" else "Буфер предзагрузки", fontWeight = FontWeight.SemiBold, color = fg); Spacer(Modifier.weight(1f)); Text("${buffer.toInt()} s", color = MaterialTheme.colorScheme.primary) }
                Slider(value = buffer, onValueChange = onBuffer, valueRange = 0f..5f, steps = 4)
                Text(if (language == "zh") "三段均衡器（分贝）" else if (language == "en") "3-band equalizer (dB)" else if (language == "de") "3-Band-Equalizer (dB)" else if (language == "es") "Ecualizador de 3 bandas (dB)" else if (language == "pt") "Equalizador de 3 bandas (dB)" else if (language == "uk") "Трисмуговий еквалайзер (дБ)" else "Трёхполосный эквалайзер (дБ)", fontWeight = FontWeight.SemiBold, color = fg)
                val eqBands = listOf(Triple(if (language == "zh") "低音" else "Низкие / Low", low, onLow), Triple(if (language == "zh") "中音" else "Средние / Mid", mid, onMid), Triple(if (language == "zh") "高音" else "Высокие / High", high, onHigh))
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        eqBands.forEach { (label, value, change) ->
                            Column(Modifier.weight(1f)) { Text(label, color = fg, fontSize = 12.sp); Text("${value.toInt()} dB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp); Slider(value = value, onValueChange = change, valueRange = -10f..10f, steps = 19) }
                        }
                    }
                } else {
                    eqBands.forEach { (label, value, change) ->
                        Column { Row(verticalAlignment = Alignment.CenterVertically) { Text(label, color = fg, fontSize = 13.sp); Spacer(Modifier.weight(1f)); Text("${value.toInt()} dB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }; Slider(value = value, onValueChange = change, valueRange = -10f..10f, steps = 19) }
                    }
                }
                Text(if (language == "zh") "缓冲设置在重新播放后生效。均衡器需要正在播放，且设备支持。" else if (language == "en") "Buffer changes take effect after playback is restarted. Equalizer requires an active playback session and device support." else if (language == "ru") "Буфер применяется после перезапуска воспроизведения. Эквалайзер требует активного воспроизведения и поддержки устройством." else "Buffer changes take effect after playback restarts. Equalizer requires active playback and device support.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("done", language)) } },
        containerColor = panel
    )
}

@Composable
private fun CatalogFilterDropdown(
    kind: String, label: String, value: String, language: String,
    modifier: Modifier = Modifier, onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var options by remember(kind) { mutableStateOf<List<RadioBrowserRepository.FilterOption>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(kind, retry) {
        failed = false
        try {
            options = withContext(Dispatchers.IO) { RadioBrowserRepository.filterOptions(kind) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) { failed = true }
    }
    val choices = remember(options, language) {
        val locale = java.util.Locale.forLanguageTag(language)
        val localized = options.orEmpty().map { option ->
            val display = when {
                kind == "countries" && option.code.matches(Regex("[A-Za-z]{2}")) ->
                    java.util.Locale.Builder().setRegion(option.code).build().getDisplayCountry(locale)
                kind == "languages" && option.code.matches(Regex("[a-zA-Z]{2,3}")) ->
                    java.util.Locale.forLanguageTag(option.code).getDisplayLanguage(locale)
                else -> option.value
            }.ifBlank { option.value }
            option.value to display
        }
        if (kind == "tags") localized else {
            val collator = java.text.Collator.getInstance(locale)
            localized.sortedWith { a, b -> collator.compare(a.second, b.second) }
        }
    }
    val matching = remember(choices, search) {
        choices.filter { search.isBlank() || it.first.contains(search.trim(), true) || it.second.contains(search.trim(), true) }
    }
    Box(modifier) {
        OutlinedButton(onClick = { search = ""; expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
            Text(if (value.isBlank()) label else choices.firstOrNull { it.first == value }?.second ?: value,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.width(280.dp).heightIn(max = 400.dp)) {
            OutlinedTextField(value = search, onValueChange = { search = it },
                label = { Text(tr("search", language)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
            DropdownMenuItem(text = { Text(tr("any_filter", language)) },
                onClick = { expanded = false; onSelect("") },
                modifier = Modifier.semantics { selected = value.isBlank() })
            when {
                failed -> {
                    Text(tr("filter_load_error", language), modifier = Modifier.padding(12.dp))
                    TextButton(onClick = { retry++ }) { Text(tr("retry", language)) }
                }
                options == null -> Text(tr("loading", language), modifier = Modifier.padding(12.dp))
                else -> {
                    matching.take(100).forEach { (id, text) ->
                        DropdownMenuItem(text = { Text(text) },
                            onClick = { expanded = false; onSelect(id) },
                            modifier = Modifier.semantics { selected = value == id },
                            trailingIcon = { if (value == id) Icon(Icons.Default.Check, null) })
                    }
                    if (matching.size > 100) Text(tr("refine_filter", language),
                        fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    if (matching.isEmpty()) Text(tr("no_filter_matches", language), modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsDropdown(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    optionColors: Map<String, Color> = emptyMap()
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableIntStateOf(0) }
    val menuWidth = with(LocalDensity.current) { anchorWidth.toDp() }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .onSizeChanged { anchorWidth = it.width },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            optionColors[value]?.let { color ->
                Box(Modifier.size(18.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
            }
            Text(options.firstOrNull { it.first == value }?.second ?: options.firstOrNull()?.second.orEmpty(),
                modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Start)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(menuWidth)
        ) {
            options.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { optionColors[code]?.let { color ->
                        Box(Modifier.size(20.dp).clip(CircleShape).background(color))
                    } },
                    onClick = { expanded = false; onSelect(code) },
                    modifier = Modifier.semantics { selected = code == value },
                    trailingIcon = {
                        if (code == value) Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}
