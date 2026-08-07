package com.wzl.loudnessplayer.playback

import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.MusicFolder

/** Resolves the active queue without changing the order the library already established. */
object PlaybackScope {
    fun resolve(
        tracks: List<AudioTrack>,
        selectedFolder: MusicFolder?,
    ): List<AudioTrack> =
        selectedFolder?.let { folder -> tracks.filter { it.id in folder.trackIds } } ?: tracks
}
