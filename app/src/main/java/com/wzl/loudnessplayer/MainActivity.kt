package com.wzl.loudnessplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wzl.loudnessplayer.ui.LoudnessPlayerApp

class MainActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

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

            LoudnessPlayerApp(
                state = state,
                onImport = { picker.launch(arrayOf("audio/mpeg", "audio/mp3", "audio/*")) },
                onTrackClick = viewModel::playTrack,
                onPlayPause = viewModel::togglePlayback,
                onPrevious = viewModel::playPrevious,
                onNext = viewModel::playNext,
                onSeek = viewModel::seekTo,
                onNormalizationChanged = viewModel::setNormalizationEnabled,
                onAnalyze = viewModel::analyzeTrack,
                onRemove = viewModel::removeTrack,
                onMessageConsumed = viewModel::consumeMessage,
            )
        }
    }
}
