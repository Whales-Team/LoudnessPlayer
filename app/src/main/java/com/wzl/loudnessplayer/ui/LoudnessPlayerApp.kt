package com.wzl.loudnessplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.wzl.loudnessplayer.R
import com.wzl.loudnessplayer.PlayerUiState
import com.wzl.loudnessplayer.audio.Normalization
import com.wzl.loudnessplayer.data.AudioFileFormat
import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.groupedByArtist
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
    onGroupedByArtistChanged: (Boolean) -> Unit,
    onAnalyze: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMessageConsumed: () -> Unit,
) {
    LoudnessPlayerTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        var importMenuExpanded by remember { mutableStateOf(false) }
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
                                "MP3 · FLAC · WAV · APE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        Box {
                            FilledTonalButton(
                                onClick = { importMenuExpanded = true },
                                enabled = !state.isImporting,
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (state.isImporting) "导入中" else "导入")
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
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
                        appliedGainDb = state.appliedGainDb,
                        analyzedCount = state.tracks.count { it.loudnessLufs != null },
                        totalCount = state.tracks.size,
                        onEnabledChanged = onNormalizationChanged,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "音乐库",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${state.tracks.size} 首",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                onGroupedByArtistChanged(!state.groupedByArtist)
                            },
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (state.groupedByArtist) "显示全部" else "按演唱者分类")
                        }
                    }
                }

                if (state.tracks.isEmpty()) {
                    item {
                        EmptyLibrary(
                            onImportFiles = onImportFiles,
                            onImportFolder = onImportFolder,
                            onScanDevice = onScanDevice,
                        )
                    }
                } else if (state.groupedByArtist) {
                    state.tracks.groupedByArtist().forEach { group ->
                        item(key = "artist:${group.artist}") {
                            ArtistHeader(
                                artist = group.artist,
                                count = group.tracks.size,
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
                                onClick = { onTrackClick(track.id) },
                                onAnalyze = { onAnalyze(track.id) },
                                onRemove = { onRemove(track.id) },
                            )
                        }
                    }
                } else {
                    items(state.tracks, key = AudioTrack::id) { track ->
                        TrackRow(
                            track = track,
                            selected = track.id == state.currentTrackId,
                            analyzing = track.id in state.analyzingIds,
                            onClick = { onTrackClick(track.id) },
                            onAnalyze = { onAnalyze(track.id) },
                            onRemove = { onRemove(track.id) },
                        )
                    }
                }
            }
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
private fun ArtistHeader(
    artist: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            artist,
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
private fun NormalizationCard(
    enabled: Boolean,
    appliedGainDb: Double,
    analyzedCount: Int,
    totalCount: Int,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    "统一响度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "目标 ${Normalization.TARGET_LUFS.toInt()} LUFS · 已分析 $analyzedCount/$totalCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
                if (enabled && appliedGainDb != 0.0) {
                    Text(
                        "当前补偿 ${formatGain(appliedGainDb)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack,
    selected: Boolean,
    analyzing: Boolean,
    onClick: () -> Unit,
    onAnalyze: () -> Unit,
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
            if (analyzing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(22.dp),
                    strokeWidth = 2.dp,
                )
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
                        text = {
                            Text(
                                if (track.format.supportsLoudnessAnalysis) {
                                    "重新分析响度"
                                } else {
                                    "APE 暂不支持响度分析"
                                },
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.BarChart, null) },
                        enabled = track.format.supportsLoudnessAnalysis,
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
            loudnessLufs?.let { String.format(Locale.US, "%.1f", it) } ?: "待分析",
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
            "支持 MP3、FLAC、WAV、APE",
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
private fun NowPlayingBar(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun formatGain(gainDb: Double): String =
    String.format(Locale.US, "%+.1f dB", gainDb)
