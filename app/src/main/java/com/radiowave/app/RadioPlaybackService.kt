package com.radiowave.app

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemClock
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background radio playback. Hardware/media-session Next and Previous commands
 * navigate the user's favorites in the same order as the app's station list.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioPlaybackService : MediaSessionService() {
    private lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaSession? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var retryAttempt = 0
    private var retryRunnable: Runnable? = null
    private var equalizer: Equalizer? = null
    private lateinit var audioManager: AudioManager
    private var bluetoothOutputConnected = false
    private var bluetoothRemovedAt = -10_000L
    private var wiredOutputConnected = false
    private var wiredRemovedAt = -10_000L
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            updateBluetoothOutputs()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            if (removedDevices.any { it.isSink && it.isBluetoothOutput() }) {
                bluetoothRemovedAt = SystemClock.elapsedRealtime()
            }
            if (removedDevices.any { it.isSink && it.isWiredOutput() }) {
                wiredRemovedAt = SystemClock.elapsedRealtime()
            }
            updateBluetoothOutputs()
        }
    }
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            val bluetoothDisconnect = bluetoothOutputConnected ||
                SystemClock.elapsedRealtime() - bluetoothRemovedAt < 1500L
            val wiredDisconnect = wiredOutputConnected ||
                SystemClock.elapsedRealtime() - wiredRemovedAt < 1500L
            val continueAfterBt = getSharedPreferences("af_radio_settings", MODE_PRIVATE)
                .getBoolean("continue_after_bt", false)
            if (!continueAfterBt || !bluetoothDisconnect || wiredDisconnect) {
                cancelReconnect()
                exoPlayer.pause()
            }
        }
    }

    private fun AudioDeviceInfo.isBluetoothOutput(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (android.os.Build.VERSION.SDK_INT >= 31 &&
                (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
            (android.os.Build.VERSION.SDK_INT >= 33 && type == AudioDeviceInfo.TYPE_BLE_BROADCAST)

    private fun updateBluetoothOutputs() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        bluetoothOutputConnected = outputs.any { it.isBluetoothOutput() }
        wiredOutputConnected = outputs.any { it.isWiredOutput() }
    }

    private fun AudioDeviceInfo.isWiredOutput(): Boolean = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> true
        else -> false
    }

    override fun onCreate() {
        super.onCreate()
        RadioNetwork.initialize(this)

        val prefs = getSharedPreferences("af_radio_settings", MODE_PRIVATE)
        val stationStore = StationStore(this)
        val bufferSeconds = prefs.getInt("buffer_seconds", 2).coerceIn(0, 5)
        val minBuffer = (bufferSeconds * 1000).coerceAtLeast(500)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, (minBuffer + 5000).coerceAtLeast(5000), 250, 500)
            .build()
        exoPlayer = ExoPlayer.Builder(this).setLoadControl(loadControl)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                .setDataSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(this,
                    androidx.media3.datasource.DataSource.Factory {
                        androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(RadioNetwork.client())
                            .setUserAgent("AF-Radio").createDataSource()
                    })))
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            // Handle noisy events ourselves so the Bluetooth preference does not
            // disable pausing when wired headphones are unplugged.
            setHandleAudioBecomingNoisy(false)
        }
        audioManager = getSystemService(AudioManager::class.java)
        updateBluetoothOutputs()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        androidx.core.content.ContextCompat.registerReceiver(this, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                try { equalizer?.release() } catch (_: Exception) { }
                equalizer = null
                if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
                try {
                    equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
                    applyEqualizer(prefs.getInt("eq_low", 0), prefs.getInt("eq_mid", 0), prefs.getInt("eq_high", 0))
                } catch (_: Exception) { equalizer = null }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) cancelReconnect()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                scheduleReconnect()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retryAttempt = 0
                    cancelReconnect()
                }
                if (playbackState == Player.STATE_IDLE && exoPlayer.playerError == null) cancelReconnect()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null && prefs.getBoolean("remember_last_station", true)) {
                    stationStore.rememberLastStation(mediaItem.mediaId)
                }
                retryAttempt = 0
                cancelReconnect()
            }
        })

        val navigationPlayer = FavoriteNavigationPlayer(exoPlayer) {
            val store = StationStore(this)
            store.orderedFavorites()
        }
        mediaSession = MediaSession.Builder(this, navigationPlayer).build()
        if (prefs.getBoolean("remember_last_station", true)) {
            // Restore the selection without starting sound before the user presses Play.
            stationStore.lastStation()?.let { exoPlayer.setMediaItem(item(it)) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_EQ) {
            applyEqualizer(intent.getIntExtra("low", 0), intent.getIntExtra("mid", 0), intent.getIntExtra("high", 0))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun applyEqualizer(low: Int, mid: Int, high: Int) {
        try {
            val eq = equalizer ?: return
            val count = eq.numberOfBands.toInt()
            if (count < 1) return
            val bands = (0 until count).map { it.toShort() }
            val targets = when (count) {
                1 -> listOf((low + mid + high) / 3)
                2 -> listOf(low, high)
                else -> bands.map { band ->
                    val center = eq.getCenterFreq(band).toLong()
                    when {
                        center < 250_000 -> low
                        center < 4_000_000 -> mid
                        else -> high
                    }
                }
            }
            for (i in bands.indices) {
                val value = (targets[i].coerceIn(-10, 10) * 100).coerceIn(eq.bandLevelRange[0].toInt(), eq.bandLevelRange[1].toInt())
                eq.setBandLevel(bands[i], value.toShort())
            }
        } catch (_: Exception) { /* Some devices do not expose a system equalizer. */ }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun scheduleReconnect() {
        cancelReconnect()
        if (!::exoPlayer.isInitialized || exoPlayer.currentMediaItem == null || !exoPlayer.playWhenReady) return

        val delays = longArrayOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L)
        val delay = delays[retryAttempt.coerceAtMost(delays.lastIndex)]
        retryAttempt = (retryAttempt + 1).coerceAtMost(delays.lastIndex)

        val expectedMediaId = exoPlayer.currentMediaItem?.mediaId
        retryRunnable = Runnable {
            // Do not restart a different station if the user changed stations while waiting.
            if (exoPlayer.currentMediaItem?.mediaId == expectedMediaId &&
                exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.playWhenReady && exoPlayer.playerError != null
            ) {
                exoPlayer.prepare()
            }
        }
        mainHandler.postDelayed(retryRunnable!!, delay)
    }

    private fun cancelReconnect() {
        retryRunnable?.let(mainHandler::removeCallbacks)
        retryRunnable = null
    }

    override fun onDestroy() {
        cancelReconnect()
        unregisterReceiver(noisyReceiver)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        mediaSession?.release()
        mediaSession = null
        try { equalizer?.release() } catch (_: Exception) { }
        equalizer = null
        if (::exoPlayer.isInitialized) exoPlayer.release()
        super.onDestroy()
    }

    private class FavoriteNavigationPlayer(
        private val player: ExoPlayer,
        private val favoriteProvider: () -> List<Station>
    ) : ForwardingPlayer(player) {

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            // stop() leaves ExoPlayer idle; Play must prepare the stream again.
            if (playWhenReady && player.playbackState == Player.STATE_IDLE && player.currentMediaItem != null) {
                player.prepare()
            }
            player.playWhenReady = playWhenReady
        }

        override fun play() = setPlayWhenReady(true)

        override fun stop() {
            player.pause()
            player.stop()
        }

        // Expose skip commands to Android's media controls even though each radio
        // station is a live stream rather than a finite playlist track.
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        override fun seekToNext() = navigateFavorites(direction = 1)
        override fun seekToPrevious() = navigateFavorites(direction = -1)
        override fun seekToNextMediaItem() = navigateFavorites(direction = 1)
        override fun seekToPreviousMediaItem() = navigateFavorites(direction = -1)

        private fun navigateFavorites(direction: Int) {
            val favorites = favoriteProvider()
            if (favorites.isEmpty()) return

            val currentId = player.currentMediaItem?.mediaId
            val currentIndex = favorites.indexOfFirst { it.id == currentId }
            val targetIndex = if (currentIndex < 0) {
                if (direction > 0) 0 else favorites.lastIndex
            } else {
                Math.floorMod(currentIndex + direction, favorites.size)
            }
            val station = favorites[targetIndex]
            player.setMediaItem(item(station))
            player.prepare()
            player.play()
        }
    }

    companion object {
        const val ACTION_UPDATE_EQ = "com.radiowave.app.action.UPDATE_EQ"
        fun item(station: Station): MediaItem = MediaItem.Builder()
            .setUri(station.url)
            .setMediaId(station.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtist("${station.country} · ${station.genre}")
                    .setDisplayTitle(station.name)
                    .build()
            )
            .build()
    }
}
