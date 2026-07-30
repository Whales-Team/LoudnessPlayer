package com.wzl.loudnessplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wzl.loudnessplayer.ui.LoudnessPlayerApp

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        viewModel.refreshLyricsOverlayState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val picker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenMultipleDocuments(),
            ) { uris ->
                uris.forEach { uri ->
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                viewModel.importTracks(uris)
            }
            val folderPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    viewModel.importFolder(uri)
                }
            }
            val permissionPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) {
                    viewModel.importDeviceAudio()
                } else {
                    viewModel.notifyAudioPermissionDenied()
                }
            }
            val overlayPermissionPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) {
                if (Settings.canDrawOverlays(this)) {
                    viewModel.enableLyricsOverlay()
                } else {
                    viewModel.notifyOverlayPermissionDenied()
                }
            }
            val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            LoudnessPlayerApp(
                state = state,
                onImportFiles = {
                    picker.launch(
                        arrayOf(
                            "audio/*",
                            "application/octet-stream",
                            "application/x-ape",
                        ),
                    )
                },
                onImportFolder = { folderPicker.launch(null) },
                onScanDevice = {
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            audioPermission,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.importDeviceAudio()
                    } else {
                        permissionPicker.launch(audioPermission)
                    }
                },
                onTrackClick = viewModel::playTrack,
                onPlayPause = viewModel::togglePlayback,
                onPrevious = viewModel::playPrevious,
                onNext = viewModel::playNext,
                onSeek = viewModel::seekTo,
                onNormalizationChanged = viewModel::setNormalizationEnabled,
                onTargetLoudnessChanged = viewModel::setTargetLoudness,
                onPlaybackModeChanged = viewModel::setPlaybackMode,
                onLibraryViewModeChanged = viewModel::setLibraryViewMode,
                onSearchQueryChanged = viewModel::setSearchQuery,
                onThemeChanged = viewModel::setAppTheme,
                onLyricsOverlayChanged = { enabled ->
                    when {
                        !enabled -> viewModel.disableLyricsOverlay()
                        Settings.canDrawOverlays(this) -> viewModel.enableLyricsOverlay()
                        else -> overlayPermissionPicker.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    }
                },
                onCreateMusicFolder = viewModel::createMusicFolder,
                onSelectMusicFolder = viewModel::selectMusicFolder,
                onDeleteMusicFolder = viewModel::deleteMusicFolder,
                onTrackFolderChanged = viewModel::setTrackInMusicFolder,
                onLyricsChanged = viewModel::setTrackLyrics,
                onAnalyze = viewModel::analyzeTrack,
                onRemove = viewModel::removeTrack,
                onMessageConsumed = viewModel::consumeMessage,
            )
        }
    }
}
