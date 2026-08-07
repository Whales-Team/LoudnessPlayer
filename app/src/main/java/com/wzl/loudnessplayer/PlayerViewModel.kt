package com.wzl.loudnessplayer

import android.app.Application
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.wzl.loudnessplayer.audio.ApeLoudnessAnalyzer
import com.wzl.loudnessplayer.audio.ApeStreamingDataSource
import com.wzl.loudnessplayer.audio.LoudnessAnalyzer
import com.wzl.loudnessplayer.audio.Normalization
import com.wzl.loudnessplayer.data.AppTheme
import com.wzl.loudnessplayer.data.AudioFileFormat
import com.wzl.loudnessplayer.data.DecoderPath
import com.wzl.loudnessplayer.data.AudioLibraryScanner
import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.LibraryViewMode
import com.wzl.loudnessplayer.data.MusicFolder
import com.wzl.loudnessplayer.data.PlaybackMode
import com.wzl.loudnessplayer.data.TrackRepository
import com.wzl.loudnessplayer.data.selectUniqueImports
import com.wzl.loudnessplayer.data.sortedByTitleInitial
import com.wzl.loudnessplayer.lyrics.LrcParser
import com.wzl.loudnessplayer.lyrics.LyricsOverlayService
import com.wzl.loudnessplayer.lyrics.TimedLyricLine
import com.wzl.loudnessplayer.playback.PlaybackScope
import com.wzl.loudnessplayer.playback.LoudnessPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val repository = TrackRepository(application)
    private val libraryScanner = AudioLibraryScanner(application, repository)
    private val analyzer = LoudnessAnalyzer(application)
    private val apeAnalyzer = ApeLoudnessAnalyzer(application)
    private val player = LoudnessPlaybackService.player(application)

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            tracks = repository.loadTracks().sortedByTitleInitial(),
            normalizationEnabled = repository.isNormalizationEnabled(),
            targetLoudnessLufs = repository.targetLoudnessLufs(),
            libraryViewMode = repository.libraryViewMode(),
            playbackMode = repository.playbackMode(),
            appTheme = repository.appTheme(),
            musicFolders = repository.loadMusicFolders(),
            lyricsOverlayEnabled = repository.isLyricsOverlayEnabled() &&
                Settings.canDrawOverlays(application),
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val pendingAnalysisIds = linkedSetOf<String>()
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var analysisJob: Job? = null
    private var volumeRampJob: Job? = null
    private var cachedLyricsTrackId: String? = null
    private var cachedTimedLyrics: List<TimedLyricLine> = emptyList()
    private var lastOverlayUpdateMs = 0L

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
                    cachedLyricsTrackId = null
                    applyNormalization(smooth = true)
                    publishPlaybackState()
                    updateLyricsOverlay(force = true)
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    attachLoudnessEnhancer(audioSessionId)
                    applyNormalization(smooth = false)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val current = currentTrack()
                    if (current?.format == AudioFileFormat.APE) {
                        val detail = error.causeChain()
                            .mapNotNull { it.message }
                            .firstOrNull { it.startsWith("APE 实时解码失败") }
                        showMessage(
                            (detail ?: "APE 实时解码失败") + "；原文件未被修改",
                        )
                    } else {
                        showMessage("播放失败：${error.errorCodeName}")
                    }
                }
            },
        )
        applyPlaybackMode(_uiState.value.playbackMode)
        syncPlayerQueue(autoPlay = false)
        refreshApeDurations()
        if (_uiState.value.lyricsOverlayEnabled) {
            runCatching { LyricsOverlayService.start(appContext) }
        }
        startPositionUpdates()
        if (_uiState.value.normalizationEnabled) {
            queueMissingLoudnessAnalysis()
        }
    }

    fun importTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importFrom("所选文件") {
            uris.mapNotNull { uri ->
                if (!repository.isSupportedAudio(uri)) return@mapNotNull null
                runCatching { repository.createTrack(uri) }.getOrNull()
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        importFrom("文件夹") { libraryScanner.scanFolder(treeUri) }
    }

    fun importDeviceAudio() {
        importFrom("手机音频库") { libraryScanner.scanDevice() }
    }

    fun notifyAudioPermissionDenied() {
        showMessage("需要音频读取权限才能一键扫描手机音乐")
    }

    private fun importFrom(
        sourceName: String,
        loadTracks: suspend () -> List<AudioTrack>,
    ) {
        if (_uiState.value.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            try {
                val candidates = runCatching { loadTracks() }
                    .onFailure { showMessage("无法读取$sourceName") }
                    .getOrDefault(emptyList())
                val selection = selectUniqueImports(
                    existingTracks = _uiState.value.tracks,
                    candidates = candidates,
                )
                val imported = selection.tracks

                if (imported.isEmpty()) {
                    val duplicateMessage = if (selection.skippedDuplicateCount > 0) {
                        "；已跳过 ${selection.skippedDuplicateCount} 首跨格式重复歌曲"
                    } else {
                        ""
                    }
                    showMessage("${sourceName}中没有发现新的受支持音频$duplicateMessage")
                    return@launch
                }

                _uiState.update {
                    it.copy(tracks = (it.tracks + imported).sortedByTitleInitial())
                }
                persistTracks()
                syncPlayerQueue(autoPlay = false)

                val analyzable = imported.filter { it.format.supportsLoudnessAnalysis }
                val apeCount = imported.count { it.format == AudioFileFormat.APE }
                val details = buildList {
                    if (apeCount > 0) {
                        add("$apeCount 首 APE 将实时解码且不生成副本")
                    }
                    if (selection.skippedDuplicateCount > 0) {
                        add("已跳过 ${selection.skippedDuplicateCount} 首跨格式重复歌曲")
                    }
                }
                val suffix = if (details.isEmpty()) {
                    ""
                } else {
                    details.joinToString(prefix = "；", separator = "；")
                }
                showMessage(
                    "已按首字母导入 ${imported.size} 首音频，正在自动校验响度$suffix",
                )
                queueAnalysis(analyzable.map(AudioTrack::id))
            } finally {
                _uiState.update { it.copy(isImporting = false) }
            }
        }
    }

    fun playTrack(trackId: String) {
        val scopedTracks = activeTracks()
        val index = scopedTracks.indexOfFirst { it.id == trackId }
        if (index < 0) return
        if (player.mediaItemCount != scopedTracks.size) syncPlayerQueue(autoPlay = false)
        LoudnessPlaybackService.ensureStarted(appContext)
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
            LoudnessPlaybackService.ensureStarted(appContext)
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
        player.seekTo(positionMs.coerceIn(0L, playbackDurationMs()))
        publishPlaybackState()
        updateLyricsOverlay(force = true)
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        repository.setNormalizationEnabled(enabled)
        _uiState.update { it.copy(normalizationEnabled = enabled) }
        applyNormalization(smooth = true)
        if (enabled) queueMissingLoudnessAnalysis()
    }

    fun setTargetLoudness(targetLufs: Double) {
        val safeTarget =
            targetLufs.coerceIn(Normalization.MIN_TARGET_LUFS, Normalization.MAX_TARGET_LUFS)
        repository.setTargetLoudnessLufs(safeTarget)
        _uiState.update {
            it.copy(
                targetLoudnessLufs = safeTarget,
                normalizationEnabled = true,
            )
        }
        repository.setNormalizationEnabled(true)
        applyNormalization(smooth = true)
        val missingCount = queueMissingLoudnessAnalysis()
        showMessage(
            if (missingCount == 0) {
                "目标响度已设为 ${safeTarget.toInt()} LUFS，整库增益已更新"
            } else {
                "目标响度已设为 ${safeTarget.toInt()} LUFS，正在自动校验 $missingCount 首歌曲"
            },
        )
    }

    fun analyzeTrack(trackId: String) {
        val track = _uiState.value.tracks.firstOrNull { it.id == trackId } ?: return
        if (!track.format.supportsLoudnessAnalysis) {
            showMessage("该音频格式暂不支持响度分析")
            return
        }
        queueAnalysis(listOf(trackId))
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        repository.setPlaybackMode(mode)
        _uiState.update { it.copy(playbackMode = mode) }
        applyPlaybackMode(mode)
        showMessage("已切换为${mode.displayName}")
    }

    fun cyclePlaybackMode() {
        setPlaybackMode(_uiState.value.playbackMode.next())
    }

    fun setLibraryViewMode(mode: LibraryViewMode) {
        repository.setLibraryViewMode(mode)
        _uiState.update { it.copy(libraryViewMode = mode) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setAppTheme(theme: AppTheme) {
        repository.setAppTheme(theme)
        _uiState.update { it.copy(appTheme = theme) }
    }

    fun createMusicFolder(name: String): String? {
        val cleanName = name.trim().take(MAX_FOLDER_NAME_LENGTH)
        if (cleanName.isEmpty()) {
            showMessage("文件夹名称不能为空")
            return null
        }
        if (_uiState.value.musicFolders.any { it.name.equals(cleanName, ignoreCase = true) }) {
            showMessage("已经存在同名文件夹")
            return null
        }
        val folder = MusicFolder(
            id = UUID.randomUUID().toString(),
            name = cleanName,
        )
        _uiState.update {
            it.copy(
                musicFolders = it.musicFolders + folder,
                selectedFolderId = folder.id,
            )
        }
        persistMusicFolders()
        showMessage("已创建文件夹“${cleanName}”")
        return folder.id
    }

    fun selectMusicFolder(folderId: String?) {
        val validId = folderId?.takeIf { id ->
            _uiState.value.musicFolders.any { it.id == id }
        }
        _uiState.update { it.copy(selectedFolderId = validId) }
        syncPlayerQueue(autoPlay = false)
    }

    fun deleteMusicFolder(folderId: String) {
        val folder = _uiState.value.musicFolders.firstOrNull { it.id == folderId } ?: return
        _uiState.update {
            it.copy(
                musicFolders = it.musicFolders.filterNot { item -> item.id == folderId },
                selectedFolderId = if (it.selectedFolderId == folderId) {
                    null
                } else {
                    it.selectedFolderId
                },
            )
        }
        persistMusicFolders()
        syncPlayerQueue(autoPlay = false)
        showMessage("已删除文件夹“${folder.name}”，歌曲仍保留在音乐库")
    }

    fun setTrackInMusicFolder(
        trackId: String,
        folderId: String,
        included: Boolean,
    ) {
        if (_uiState.value.tracks.none { it.id == trackId }) return
        _uiState.update { state ->
            state.copy(
                musicFolders = state.musicFolders.map { folder ->
                    if (folder.id != folderId) {
                        folder
                    } else {
                        folder.copy(
                            trackIds = if (included) {
                                folder.trackIds + trackId
                            } else {
                                folder.trackIds - trackId
                            },
                        )
                    }
                },
            )
        }
        persistMusicFolders()
        if (_uiState.value.selectedFolderId == folderId) {
            syncPlayerQueue(autoPlay = false)
        }
    }

    fun setTrackLyrics(trackId: String, lyrics: String) {
        val cleanedLyrics = lyrics.trim().takeIf(String::isNotEmpty)
        _uiState.update { state ->
            state.copy(
                tracks = state.tracks.map { track ->
                    if (track.id == trackId) track.copy(lyrics = cleanedLyrics) else track
                },
            )
        }
        persistTracks()
        if (_uiState.value.currentTrackId == trackId) {
            cachedLyricsTrackId = null
            updateLyricsOverlay(force = true)
        }
        showMessage(if (cleanedLyrics == null) "已清除歌词" else "歌词已保存")
    }

    fun enableLyricsOverlay() {
        if (!Settings.canDrawOverlays(appContext)) {
            showMessage("请先允许“显示在其他应用上层”权限")
            return
        }
        repository.setLyricsOverlayEnabled(true)
        runCatching { LyricsOverlayService.start(appContext) }
            .onSuccess {
                _uiState.update { it.copy(lyricsOverlayEnabled = true) }
                updateLyricsOverlay(force = true)
            }
            .onFailure {
                repository.setLyricsOverlayEnabled(false)
                showMessage("无法启动歌词悬浮窗")
            }
    }

    fun disableLyricsOverlay() {
        repository.setLyricsOverlayEnabled(false)
        LyricsOverlayService.stop(appContext)
        _uiState.update { it.copy(lyricsOverlayEnabled = false) }
    }

    fun notifyOverlayPermissionDenied() {
        repository.setLyricsOverlayEnabled(false)
        _uiState.update { it.copy(lyricsOverlayEnabled = false) }
        showMessage("未授予悬浮窗权限，桌面歌词未开启")
    }

    fun refreshLyricsOverlayState() {
        val shouldRun =
            repository.isLyricsOverlayEnabled() && Settings.canDrawOverlays(appContext)
        if (shouldRun && !LyricsOverlayService.isRunning) {
            runCatching { LyricsOverlayService.start(appContext) }
        }
        _uiState.update {
            it.copy(lyricsOverlayEnabled = shouldRun)
        }
    }

    fun removeTrack(trackId: String) {
        val wasCurrent = _uiState.value.currentTrackId == trackId
        pendingAnalysisIds -= trackId
        _uiState.update {
            it.copy(
                tracks = it.tracks.filterNot { track -> track.id == trackId },
                analyzingIds = it.analyzingIds - trackId,
                currentTrackId = if (wasCurrent) null else it.currentTrackId,
                musicFolders = it.musicFolders.map { folder ->
                    folder.copy(trackIds = folder.trackIds - trackId)
                },
            )
        }
        persistTracks()
        persistMusicFolders()
        syncPlayerQueue(autoPlay = false)
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun queueMissingLoudnessAnalysis(): Int {
        val missingIds = _uiState.value.tracks
            .filter { it.format.supportsLoudnessAnalysis && it.loudnessLufs == null }
            .map(AudioTrack::id)
        queueAnalysis(missingIds)
        return missingIds.size
    }

    private fun queueAnalysis(trackIds: List<String>) {
        val validIds = trackIds
            .distinct()
            .filter { trackId ->
                _uiState.value.tracks.any {
                    it.id == trackId && it.format.supportsLoudnessAnalysis
                }
            }
        if (validIds.isEmpty()) return
        pendingAnalysisIds += validIds
        _uiState.update { it.copy(analyzingIds = it.analyzingIds + validIds) }
        if (analysisJob?.isActive == true) return

        analysisJob = viewModelScope.launch {
            repeat(MAX_CONCURRENT_ANALYSES) {
                launch {
                    while (pendingAnalysisIds.isNotEmpty()) {
                val trackId = pendingAnalysisIds.first()
                pendingAnalysisIds.remove(trackId)
                val track = _uiState.value.tracks.firstOrNull { it.id == trackId }
                if (track == null || !track.format.supportsLoudnessAnalysis) {
                    _uiState.update { it.copy(analyzingIds = it.analyzingIds - trackId) }
                    continue
                }
                runCatching {
                    val uri = Uri.parse(track.uri)
                    if (track.format.decoderPath == DecoderPath.FFMPEG_PCM) {
                        apeAnalyzer.analyze(uri, track.format.displayName)
                    } else {
                        runCatching { analyzer.analyze(uri) }
                            .getOrElse { apeAnalyzer.analyze(uri, track.format.displayName) }
                    }
                }
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
                        if (_uiState.value.currentTrackId == trackId) {
                            applyNormalization(smooth = true)
                        }
                    }
                    .onFailure { error ->
                        val detail = error.message?.take(100)
                        showMessage(
                            "“${track.title}”响度分析失败" +
                                if (detail == null) "" else "：$detail",
                        )
                    }
                _uiState.update { it.copy(analyzingIds = it.analyzingIds - trackId) }
                    }
                }
            }
        }
    }

    private fun syncPlayerQueue(autoPlay: Boolean) {
        val state = _uiState.value
        val currentId = player.currentMediaItem?.mediaId ?: state.currentTrackId
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val tracks = activeTracks(state)
        val startIndex = tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val items = tracks.map { it.toMediaItem() }
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
        applyPlaybackMode(state.playbackMode)
        player.prepare()
        player.playWhenReady = autoPlay
        _uiState.update {
            it.copy(currentTrackId = items.getOrNull(startIndex)?.mediaId)
        }
        applyNormalization(smooth = false)
    }

    private fun applyPlaybackMode(mode: PlaybackMode) {
        player.repeatMode = when (mode) {
            PlaybackMode.SEQUENTIAL,
            PlaybackMode.SHUFFLE,
            -> Player.REPEAT_MODE_OFF

            PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
        }
        player.shuffleModeEnabled = mode == PlaybackMode.SHUFFLE
    }

    private fun attachLoudnessEnhancer(audioSessionId: Int) {
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
    }

    private fun applyNormalization(smooth: Boolean) {
        val state = _uiState.value
        val track = currentTrack()
        val gainDb = Normalization.gainDb(
            loudnessLufs = track?.loudnessLufs,
            samplePeakDbfs = track?.samplePeakDbfs,
            targetLufs = state.targetLoudnessLufs,
            enabled = state.normalizationEnabled,
        )
        val targetVolume = Normalization.attenuationFactor(gainDb)
        volumeRampJob?.cancel()
        if (smooth) {
            val initialVolume = player.volume
            volumeRampJob = viewModelScope.launch {
                repeat(VOLUME_RAMP_STEPS) { step ->
                    val progress = (step + 1).toFloat() / VOLUME_RAMP_STEPS
                    player.volume = initialVolume + (targetVolume - initialVolume) * progress
                    delay(VOLUME_RAMP_STEP_MS)
                }
            }
        } else {
            player.volume = targetVolume
        }
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
        val safeDuration = playbackDurationMs()
        _uiState.update {
            it.copy(
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = safeDuration,
            )
        }
    }

    private fun refreshApeDurations() {
        viewModelScope.launch {
            val correctedDurations = repository.refreshApeDurations(_uiState.value.tracks)
            if (correctedDurations.isEmpty()) return@launch
            _uiState.update { state ->
                state.copy(
                    tracks = state.tracks.map { track ->
                        correctedDurations[track.id]
                            ?.let { durationMs -> track.copy(durationMs = durationMs) }
                            ?: track
                    },
                )
            }
            persistTracks()
            publishPlaybackState()
        }
    }

    private fun playbackDurationMs(): Long {
        val trackDuration = currentTrack()
            ?.takeIf { it.format == AudioFileFormat.APE }
            ?.durationMs
            ?.takeIf { it > 0L }
        return trackDuration
            ?: player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L)
            ?: 0L
    }

    private fun startPositionUpdates() {
        viewModelScope.launch {
            while (true) {
                publishPlaybackState()
                updateLyricsOverlay(force = false)
                delay(POSITION_UPDATE_MS)
            }
        }
    }

    private fun updateLyricsOverlay(force: Boolean) {
        val state = _uiState.value
        if (!state.lyricsOverlayEnabled || !LyricsOverlayService.isRunning) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastOverlayUpdateMs < OVERLAY_UPDATE_MS) return
        lastOverlayUpdateMs = now
        val track = currentTrack() ?: return
        if (cachedLyricsTrackId != track.id) {
            cachedLyricsTrackId = track.id
            cachedTimedLyrics = LrcParser.parse(track.lyrics)
        }
        val lyricLine = LrcParser.lineAt(cachedTimedLyrics, state.positionMs)
            ?: LrcParser.fallbackLine(track.lyrics)
        LyricsOverlayService.update(
            context = appContext,
            title = track.title,
            artist = track.artist,
            lyricLine = lyricLine,
        )
    }

    private fun currentTrack(): AudioTrack? =
        _uiState.value.tracks.firstOrNull { it.id == _uiState.value.currentTrackId }

    private fun activeTracks(state: PlayerUiState = _uiState.value): List<AudioTrack> =
        PlaybackScope.resolve(
            tracks = state.tracks,
            selectedFolder = state.musicFolders.firstOrNull { it.id == state.selectedFolderId },
        )

    private fun AudioTrack.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(
                if (format.decoderPath == DecoderPath.FFMPEG_PCM) {
                    ApeStreamingDataSource.streamingUri(uri, format, durationMs)
                } else {
                    Uri.parse(uri)
                },
            )
            .apply {
                if (format.decoderPath == DecoderPath.FFMPEG_PCM) setMimeType(APE_STREAM_MIME_TYPE)
            }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .build(),
            )
            .build()

    private fun persistTracks() {
        repository.saveTracks(_uiState.value.tracks)
    }

    private fun persistMusicFolders() {
        repository.saveMusicFolders(_uiState.value.musicFolders)
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    override fun onCleared() {
        analysisJob?.cancel()
        volumeRampJob?.cancel()
        loudnessEnhancer?.release()
        super.onCleared()
    }

    private companion object {
        const val POSITION_UPDATE_MS = 250L
        const val OVERLAY_UPDATE_MS = 500L
        const val VOLUME_RAMP_STEPS = 8
        const val VOLUME_RAMP_STEP_MS = 20L
        const val MAX_FOLDER_NAME_LENGTH = 40
        const val MAX_CONCURRENT_ANALYSES = 2
        const val APE_STREAM_MIME_TYPE = "audio/wav"
    }
}

private fun Throwable.causeChain(): Sequence<Throwable> =
    generateSequence(this) { it.cause }

data class PlayerUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val normalizationEnabled: Boolean = true,
    val targetLoudnessLufs: Double = Normalization.DEFAULT_TARGET_LUFS,
    val libraryViewMode: LibraryViewMode = LibraryViewMode.ALL,
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val appTheme: AppTheme = AppTheme.GREEN,
    val musicFolders: List<MusicFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val lyricsOverlayEnabled: Boolean = false,
    val appliedGainDb: Double = 0.0,
    val analyzingIds: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val message: String? = null,
)
