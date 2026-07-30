package com.wzl.loudnessplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wzl.loudnessplayer.PlayerUiState
import com.wzl.loudnessplayer.R
import com.wzl.loudnessplayer.audio.Normalization
import com.wzl.loudnessplayer.data.AppTheme
import com.wzl.loudnessplayer.data.AudioFileFormat
import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.LibraryViewMode
import com.wzl.loudnessplayer.data.MusicFolder
import com.wzl.loudnessplayer.data.PlaybackMode
import com.wzl.loudnessplayer.data.groupedByArtist
import com.wzl.loudnessplayer.data.groupedBySimilarTitle
import com.wzl.loudnessplayer.data.matchingSearch
import com.wzl.loudnessplayer.data.sortedByTitleInitial
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoudnessPlayerApp(
    state: PlayerUiState,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onTrackClick: (String) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onNormalizationChanged: (Boolean) -> Unit,
    onTargetLoudnessChanged: (Double) -> Unit,
    onPlaybackModeChanged: (PlaybackMode) -> Unit,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onThemeChanged: (AppTheme) -> Unit,
    onLyricsOverlayChanged: (Boolean) -> Unit,
    onCreateMusicFolder: (String) -> Unit,
    onSelectMusicFolder: (String?) -> Unit,
    onDeleteMusicFolder: (String) -> Unit,
    onTrackFolderChanged: (String, String, Boolean) -> Unit,
    onLyricsChanged: (String, String) -> Unit,
    onAnalyze: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMessageConsumed: () -> Unit,
) {
    LoudnessPlayerTheme(theme = state.appTheme) {
        val snackbarHostState = remember { SnackbarHostState() }
        var importMenuExpanded by remember { mutableStateOf(false) }
        var themeDialogVisible by remember { mutableStateOf(false) }
        var targetDialogVisible by remember { mutableStateOf(false) }
        var folderDialogVisible by remember { mutableStateOf(false) }
        var lyricsTrack by remember { mutableStateOf<AudioTrack?>(null) }
        var folderTrack by remember { mutableStateOf<AudioTrack?>(null) }

        val selectedFolder = state.musicFolders.firstOrNull {
            it.id == state.selectedFolderId
        }
        val visibleTracks = state.tracks
            .let { tracks ->
                selectedFolder?.let { folder ->
                    tracks.filter { it.id in folder.trackIds }
                } ?: tracks
            }
            .matchingSearch(state.searchQuery)
            .sortedByTitleInitial()

        LaunchedEffect(state.message) {
            state.message?.let {
                snackbarHostState.showSnackbar(it)
                onMessageConsumed()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("响度播放器", fontWeight = FontWeight.SemiBold)
                            Text(
                                "v1.2 · 自动响度 · 桌面歌词",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                onLyricsOverlayChanged(!state.lyricsOverlayEnabled)
                            },
                        ) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = if (state.lyricsOverlayEnabled) {
                                    "关闭桌面歌词"
                                } else {
                                    "开启桌面歌词"
                                },
                                tint = if (state.lyricsOverlayEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { themeDialogVisible = true }) {
                            Icon(Icons.Default.Palette, contentDescription = "界面主题")
                        }
                        Box {
                            IconButton(
                                onClick = { importMenuExpanded = true },
                                enabled = !state.isImporting,
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "导入音乐")
                            }
                            ImportMenu(
                                expanded = importMenuExpanded,
                                onDismiss = { importMenuExpanded = false },
                                onImportFiles = onImportFiles,
                                onImportFolder = onImportFolder,
                                onScanDevice = onScanDevice,
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NowPlayingBar(
                    state = state,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    onPlaybackModeChanged = onPlaybackModeChanged,
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    NormalizationCard(
                        enabled = state.normalizationEnabled,
                        targetLoudnessLufs = state.targetLoudnessLufs,
                        appliedGainDb = state.appliedGainDb,
                        analyzedCount = state.tracks.count { it.loudnessLufs != null },
                        totalCount = state.tracks.count {
                            it.format.supportsLoudnessAnalysis
                        },
                        analyzingCount = state.analyzingIds.size,
                        convertingCount = state.convertingIds.size,
                        onEnabledChanged = onNormalizationChanged,
                        onConfigure = { targetDialogVisible = true },
                    )
                }

                item {
                    ImportActionsCard(
                        isImporting = state.isImporting,
                        onImportFiles = onImportFiles,
                        onImportFolder = onImportFolder,
                        onScanDevice = onScanDevice,
                    )
                }

                item {
                    LibraryControls(
                        state = state,
                        selectedFolder = selectedFolder,
                        visibleCount = visibleTracks.size,
                        onSearchQueryChanged = onSearchQueryChanged,
                        onLibraryViewModeChanged = onLibraryViewModeChanged,
                        onSelectMusicFolder = onSelectMusicFolder,
                        onCreateFolder = { folderDialogVisible = true },
                        onDeleteSelectedFolder = {
                            selectedFolder?.let { onDeleteMusicFolder(it.id) }
                        },
                    )
                }

                if (state.tracks.isEmpty()) {
                    item {
                        EmptyLibrary(
                            onImportFiles = onImportFiles,
                            onImportFolder = onImportFolder,
                            onScanDevice = onScanDevice,
                        )
                    }
                } else if (visibleTracks.isEmpty()) {
                    item {
                        EmptyResult(
                            isFolderEmpty = selectedFolder != null && state.searchQuery.isBlank(),
                        )
                    }
                } else {
                    when (state.libraryViewMode) {
                        LibraryViewMode.ALL -> {
                            items(visibleTracks, key = AudioTrack::id) { track ->
                                TrackRow(
                                    track = track,
                                    selected = track.id == state.currentTrackId,
                                    analyzing = track.id in state.analyzingIds,
                                    converting = track.id in state.convertingIds,
                                    onClick = { onTrackClick(track.id) },
                                    onAnalyze = { onAnalyze(track.id) },
                                    onEditLyrics = { lyricsTrack = track },
                                    onManageFolders = { folderTrack = track },
                                    onRemove = { onRemove(track.id) },
                                )
                            }
                        }

                        LibraryViewMode.ARTIST -> {
                            visibleTracks.groupedByArtist().forEach { group ->
                                item(key = "artist:${group.artist}") {
                                    GroupHeader(
                                        label = group.artist,
                                        count = group.tracks.size,
                                        smart = false,
                                    )
                                }
                                items(
                                    items = group.tracks,
                                    key = AudioTrack::id,
                                ) { track ->
                                    TrackRow(
                                        track = track,
                                        selected = track.id == state.currentTrackId,
                                        analyzing = track.id in state.analyzingIds,
                                        converting = track.id in state.convertingIds,
                                        onClick = { onTrackClick(track.id) },
                                        onAnalyze = { onAnalyze(track.id) },
                                        onEditLyrics = { lyricsTrack = track },
                                        onManageFolders = { folderTrack = track },
                                        onRemove = { onRemove(track.id) },
                                    )
                                }
                            }
                        }

                        LibraryViewMode.SMART_TITLE -> {
                            visibleTracks.groupedBySimilarTitle().forEachIndexed { index, group ->
                                item(key = "similar:$index:${group.label}") {
                                    GroupHeader(
                                        label = group.label,
                                        count = group.tracks.size,
                                        smart = true,
                                    )
                                }
                                items(
                                    items = group.tracks,
                                    key = AudioTrack::id,
                                ) { track ->
                                    TrackRow(
                                        track = track,
                                        selected = track.id == state.currentTrackId,
                                        analyzing = track.id in state.analyzingIds,
                                        converting = track.id in state.convertingIds,
                                        onClick = { onTrackClick(track.id) },
                                        onAnalyze = { onAnalyze(track.id) },
                                        onEditLyrics = { lyricsTrack = track },
                                        onManageFolders = { folderTrack = track },
                                        onRemove = { onRemove(track.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (targetDialogVisible) {
            TargetLoudnessDialog(
                initialTarget = state.targetLoudnessLufs,
                onDismiss = { targetDialogVisible = false },
                onConfirm = {
                    targetDialogVisible = false
                    onTargetLoudnessChanged(it)
                },
            )
        }
        if (themeDialogVisible) {
            ThemeDialog(
                selectedTheme = state.appTheme,
                onDismiss = { themeDialogVisible = false },
                onThemeSelected = {
                    onThemeChanged(it)
                    themeDialogVisible = false
                },
            )
        }
        if (folderDialogVisible) {
            CreateFolderDialog(
                onDismiss = { folderDialogVisible = false },
                onConfirm = {
                    onCreateMusicFolder(it)
                    folderDialogVisible = false
                },
            )
        }
        lyricsTrack?.let { track ->
            LyricsEditorDialog(
                track = track,
                onDismiss = { lyricsTrack = null },
                onSave = { lyrics ->
                    onLyricsChanged(track.id, lyrics)
                    lyricsTrack = null
                },
            )
        }
        folderTrack?.let { track ->
            FolderMembershipDialog(
                track = track,
                folders = state.musicFolders,
                onDismiss = { folderTrack = null },
                onCreateFolder = {
                    folderTrack = null
                    folderDialogVisible = true
                },
                onMembershipChanged = { folderId, included ->
                    onTrackFolderChanged(track.id, folderId, included)
                },
            )
        }
    }
}

@Composable
private fun ImportMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onScanDevice: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text("选择音频文件") },
            leadingIcon = { Icon(Icons.Default.Add, null) },
            onClick = {
                onDismiss()
                onImportFiles()
            },
        )
        DropdownMenuItem(
            text = { Text("导入整个文件夹") },
            leadingIcon = { Icon(Icons.Default.Folder, null) },
            onClick = {
                onDismiss()
                onImportFolder()
            },
        )
        DropdownMenuItem(
            text = { Text("一键扫描手机音频") },
            leadingIcon = { Icon(Icons.Default.PhoneAndroid, null) },
            onClick = {
                onDismiss()
                onScanDevice()
            },
        )
    }
}

@Composable
private fun ImportActionsCard(
    isImporting: Boolean,
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onScanDevice: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onImportFiles,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("选择文件")
                }
                FilledTonalButton(
                    onClick = onImportFolder,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("整个文件夹")
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = onScanDevice,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isImporting) "正在识别手机音频…" else "一键识别并导入手机全部音频")
            }
            if (isImporting) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NormalizationCard(
    enabled: Boolean,
    targetLoudnessLufs: Double,
    appliedGainDb: Double,
    analyzedCount: Int,
    totalCount: Int,
    analyzingCount: Int,
    convertingCount: Int,
    onEnabledChanged: (Boolean) -> Unit,
    onConfigure: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConfigure),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "统一响度 ${targetLoudnessLufs.toInt()} LUFS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "已校验 $analyzedCount/$totalCount · 点击设置适合你的响度",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                if (analyzingCount > 0 || convertingCount > 0) {
                    Text(
                        listOfNotNull(
                            analyzingCount.takeIf { it > 0 }?.let { "响度校验 $it 首" },
                            convertingCount.takeIf { it > 0 }?.let { "APE 转 FLAC $it 首" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (enabled && appliedGainDb != 0.0) {
                    Text(
                        "当前补偿 ${formatGain(appliedGainDb)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onConfigure) {
                Icon(Icons.Default.Tune, contentDescription = "设置目标响度")
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }
    }
}

@Composable
private fun LibraryControls(
    state: PlayerUiState,
    selectedFolder: MusicFolder?,
    visibleCount: Int,
    onSearchQueryChanged: (String) -> Unit,
    onLibraryViewModeChanged: (LibraryViewMode) -> Unit,
    onSelectMusicFolder: (String?) -> Unit,
    onCreateFolder: () -> Unit,
    onDeleteSelectedFolder: () -> Unit,
) {
    var viewMenuExpanded by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    selectedFolder?.name ?: "音乐库",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$visibleCount/${state.tracks.size} 首",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("搜索歌曲名或歌手名") },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { viewMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            state.libraryViewMode.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = viewMenuExpanded,
                        onDismissRequest = { viewMenuExpanded = false },
                    ) {
                        LibraryViewMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.displayName) },
                                onClick = {
                                    onLibraryViewModeChanged(mode)
                                    viewMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    FilledTonalButton(
                        onClick = { folderMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            selectedFolder?.name ?: "我的文件夹",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    DropdownMenu(
                        expanded = folderMenuExpanded,
                        onDismissRequest = { folderMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部歌曲") },
                            onClick = {
                                onSelectMusicFolder(null)
                                folderMenuExpanded = false
                            },
                        )
                        state.musicFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text("${folder.name}（${folder.trackIds.size}）") },
                                onClick = {
                                    onSelectMusicFolder(folder.id)
                                    folderMenuExpanded = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("新建文件夹") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = {
                                folderMenuExpanded = false
                                onCreateFolder()
                            },
                        )
                    }
                }
            }
            if (selectedFolder != null) {
                TextButton(
                    onClick = onDeleteSelectedFolder,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("删除当前文件夹（不删除歌曲）")
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    label: String,
    count: Int,
    smart: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (smart) Icons.Default.AutoAwesome else Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "$count 首",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack,
    selected: Boolean,
    analyzing: Boolean,
    converting: Boolean,
    onClick: () -> Unit,
    onAnalyze: () -> Unit,
    onEditLyrics: () -> Unit,
    onManageFolders: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 0.dp else 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (selected) Icons.Default.GraphicEq else Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${track.artist} · ${formatDuration(track.durationMs)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FormatBadge(track.format)
            if (analyzing || converting) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        if (converting) "转 FLAC" else "校验",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                LoudnessBadge(track.loudnessLufs)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑/粘贴歌词（支持 LRC）") },
                        leadingIcon = { Icon(Icons.Default.Subtitles, null) },
                        onClick = {
                            menuExpanded = false
                            onEditLyrics()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("加入我的文件夹") },
                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                        onClick = {
                            menuExpanded = false
                            onManageFolders()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (track.format.supportsLoudnessAnalysis) {
                                    "重新分析响度"
                                } else {
                                    "APE 正在等待转换"
                                },
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.BarChart, null) },
                        enabled = track.format.supportsLoudnessAnalysis && !converting,
                        onClick = {
                            menuExpanded = false
                            onAnalyze()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("从音乐库移除") },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatBadge(format: AudioFileFormat) {
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            format.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LoudnessBadge(loudnessLufs: Double?) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            loudnessLufs?.let { String.format(Locale.US, "%.1f", it) } ?: "待校验",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyLibrary(
    onImportFiles: () -> Unit,
    onImportFolder: () -> Unit,
    onScanDevice: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.app_cover),
            contentDescription = "音悦应用封面",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(28.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "还没有音乐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "支持 MP3、FLAC、WAV；APE 会自动转为 FLAC",
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(onClick = onScanDevice) {
            Icon(Icons.Default.PhoneAndroid, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("一键导入手机音频")
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onImportFolder) {
            Icon(Icons.Default.Folder, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("导入整个文件夹")
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onImportFiles) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("选择音频文件")
        }
    }
}

@Composable
private fun EmptyResult(isFolderEmpty: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (isFolderEmpty) Icons.Default.Folder else Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(if (isFolderEmpty) "这个文件夹还没有歌曲" else "没有找到匹配的歌曲")
    }
}

@Composable
private fun NowPlayingBar(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlaybackModeChanged: (PlaybackMode) -> Unit,
) {
    val currentTrack = state.tracks.firstOrNull { it.id == state.currentTrackId }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (currentTrack != null) {
            Slider(
                value = state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat()),
                onValueChange = { onSeek(it.roundToLong()) },
                valueRange = 0f..state.durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 8.dp),
            )
        } else {
            LinearProgressIndicator(
                progress = { 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    currentTrack?.title ?: "选择一首歌曲",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (currentTrack == null) {
                        "导入后点击歌曲播放"
                    } else {
                        "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { onPlaybackModeChanged(state.playbackMode.next()) },
                enabled = state.tracks.isNotEmpty(),
            ) {
                Icon(
                    when (state.playbackMode) {
                        PlaybackMode.SEQUENTIAL -> Icons.Default.PlaylistPlay
                        PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne
                        PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                    },
                    contentDescription = state.playbackMode.displayName,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(
                onClick = onPrevious,
                enabled = currentTrack != null,
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }
            FilledIconButton(
                onClick = onPlayPause,
                enabled = state.tracks.isNotEmpty(),
                modifier = Modifier.size(50.dp),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                )
            }
            IconButton(
                onClick = onNext,
                enabled = currentTrack != null,
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
        }
    }
}

@Composable
private fun TargetLoudnessDialog(
    initialTarget: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var value by remember(initialTarget) { mutableFloatStateOf(initialTarget.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
        title = { Text("设置目标响度") },
        text = {
            Column {
                Text(
                    "${value.toInt()} LUFS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = Normalization.MIN_TARGET_LUFS.toFloat() ..
                        Normalization.MAX_TARGET_LUFS.toFloat(),
                    steps = 15,
                )
                Text(
                    "数值越接近 0，听起来越响。推荐 -14 LUFS；系统会自动校验整个歌单，并限制峰值避免削波。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.toDouble()) }) {
                Text("应用到全部歌曲")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun ThemeDialog(
    selectedTheme: AppTheme,
    onDismiss: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Palette, contentDescription = null) },
        title = { Text("选择界面主题") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = theme == selectedTheme,
                            onClick = { onThemeSelected(theme) },
                        )
                        Text(theme.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
        title = { Text("新建音乐文件夹") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("文件夹名称") },
                placeholder = { Text("例如：通勤、学习、收藏") },
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun LyricsEditorDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var lyrics by remember(track.id) { mutableStateOf(track.lyrics.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
        title = { Text("编辑歌词 · ${track.title}") },
        text = {
            Column {
                Text(
                    "可粘贴普通歌词或 LRC，例如：[00:12.50]第一句",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    label = { Text("歌词") },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(lyrics) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun FolderMembershipDialog(
    track: AudioTrack,
    folders: List<MusicFolder>,
    onDismiss: () -> Unit,
    onCreateFolder: () -> Unit,
    onMembershipChanged: (String, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
        title = { Text("将“${track.title}”加入文件夹") },
        text = {
            if (folders.isEmpty()) {
                Text("还没有自建文件夹，请先创建一个。")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    folders.forEach { folder ->
                        val included = track.id in folder.trackIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMembershipChanged(folder.id, !included)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = included,
                                onCheckedChange = {
                                    onMembershipChanged(folder.id, it)
                                },
                            )
                            Text(folder.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onCreateFolder) { Text("新建文件夹") }
        },
    )
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatGain(gainDb: Double): String =
    String.format(Locale.US, "%+.1f dB", gainDb)
