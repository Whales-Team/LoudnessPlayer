package com.wzl.loudnessplayer

import android.app.Application
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.wzl.loudnessplayer.audio.LoudnessAnalyzer
import com.wzl.loudnessplayer.audio.Normalization
import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.TrackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TrackRepository(application)
    private val analyzer = LoudnessAnalyzer(application)
    private val player = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            tracks = repository.loadTracks(),
            normalizationEnabled = repository.isNormalizationEnabled(),
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var analysisJob: Job? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    publishPlaybackState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _uiState.update {
                        it.copy(currentTrackId = mediaItem?.mediaId?.takeIf(String::isNotEmpty))
                    }
                    applyNormalization()
                    publishPlaybackState()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachLoudnessEnhancer(audioSessionId)
                    applyNormalization()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    showMessage("播放失败：${error.errorCodeName}")
                }
            },
        )
        syncPlayerQueue(autoPlay = false)
        startPositionUpdates()
    }

    fun importTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val existingIds = _uiState.value.tracks.mapTo(mutableSetOf(), AudioTrack::id)
            val imported = mutableListOf<AudioTrack>()
            uris.forEach { uri ->
                runCatching { repository.createTrack(uri) }
                    .onSuccess { track ->
                        if (existingIds.add(track.id)) imported += track
                    }
                    .onFailure { showMessage("无法导入 ${uri.lastPathSegment ?: "文件"}") }
            }

            if (imported.isEmpty()) {
                showMessage("没有发现新的 MP3 文件")
                return@launch
            }

            _uiState.update { it.copy(tracks = it.tracks + imported) }
            persistTracks()
            syncPlayerQueue(autoPlay = false)
            showMessage("已导入 ${imported.size} 首歌曲，开始分析响度")
            analyzeTracks(imported.map(AudioTrack::id))
        }
    }

    fun playTrack(trackId: String) {
        val index = _uiState.value.tracks.indexOfFirst { it.id == trackId }
        if (index < 0) return
        player.seekToDefaultPosition(index)
        player.prepare()
        player.play()
    }

    fun togglePlayback() {
        if (_uiState.value.tracks.isEmpty()) return
        if (player.mediaItemCount == 0) syncPlayerQueue(autoPlay = false)
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekToDefaultPosition(0)
        }
        player.play()
    }

    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L)))
        publishPlaybackState()
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        repository.setNormalizationEnabled(enabled)
        _uiState.update { it.copy(normalizationEnabled = enabled) }
        applyNormalization()
    }

    fun analyzeTrack(trackId: String) {
        analyzeTracks(listOf(trackId))
    }

    fun removeTrack(trackId: String) {
        val wasCurrent = _uiState.value.currentTrackId == trackId
        _uiState.update {
            it.copy(
                tracks = it.tracks.filterNot { track -> track.id == trackId },
                analyzingIds = it.analyzingIds - trackId,
                currentTrackId = if (wasCurrent) null else it.currentTrackId,
            )
        }
        persistTracks()
        syncPlayerQueue(autoPlay = false)
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun analyzeTracks(trackIds: List<String>) {
        analysisJob = viewModelScope.launch {
            trackIds.forEach { trackId ->
                val track = _uiState.value.tracks.firstOrNull { it.id == trackId }
                    ?: return@forEach
                _uiState.update { it.copy(analyzingIds = it.analyzingIds + trackId) }
                runCatching { analyzer.analyze(Uri.parse(track.uri)) }
                    .onSuccess { result ->
                        _uiState.update { state ->
                            state.copy(
                                tracks = state.tracks.map {
                                    if (it.id == trackId) {
                                        it.copy(
                                            loudnessLufs = result.integratedLufs,
                                            samplePeakDbfs = result.samplePeakDbfs,
                                        )
                                    } else {
                                        it
                                    }
                                },
                            )
                        }
                        persistTracks()
                        if (_uiState.value.currentTrackId == trackId) applyNormalization()
                    }
                    .onFailure {
                        showMessage("“${track.title}”响度分析失败")
                    }
                _uiState.update { it.copy(analyzingIds = it.analyzingIds - trackId) }
            }
        }
    }

    private fun syncPlayerQueue(autoPlay: Boolean) {
        val state = _uiState.value
        val currentId = player.currentMediaItem?.mediaId ?: state.currentTrackId
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val startIndex = state.tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val items = state.tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .build(),
                )
                .build()
        }
        if (items.isEmpty()) {
            player.clearMediaItems()
            player.stop()
            _uiState.update {
                it.copy(
                    currentTrackId = null,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = 0L,
                    appliedGainDb = 0.0,
                )
            }
            return
        }
        player.setMediaItems(items, startIndex, currentPosition)
        player.prepare()
        player.playWhenReady = autoPlay
        _uiState.update {
            it.copy(currentTrackId = items.getOrNull(startIndex)?.mediaId)
        }
        applyNormalization()
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
    }

    private fun applyNormalization() {
        val state = _uiState.value
        val track = state.tracks.firstOrNull { it.id == state.currentTrackId }
        val gainDb = Normalization.gainDb(
            loudnessLufs = track?.loudnessLufs,
            enabled = state.normalizationEnabled,
        )
        player.volume = Normalization.attenuationFactor(gainDb)
        loudnessEnhancer?.let { enhancer ->
            runCatching {
                val boost = Normalization.enhancerGainMillibels(gainDb)
                enhancer.setTargetGain(boost)
                enhancer.setEnabled(boost > 0)
            }
        }
        _uiState.update { it.copy(appliedGainDb = gainDb) }
    }

    private fun publishPlaybackState() {
        val safeDuration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        _uiState.update {
            it.copy(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = safeDuration,
            )
        }
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                publishPlaybackState()
                delay(POSITION_UPDATE_MS)
            }
        }
    }

    private fun persistTracks() {
        repository.saveTracks(_uiState.value.tracks)
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    override fun onCleared() {
        analysisJob?.cancel()
        loudnessEnhancer?.release()
        player.release()
        super.onCleared()
    }

    private companion object {
        const val POSITION_UPDATE_MS = 250L
    }
}

data class PlayerUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val normalizationEnabled: Boolean = true,
    val appliedGainDb: Double = 0.0,
    val analyzingIds: Set<String> = emptySet(),
    val message: String? = null,
)
