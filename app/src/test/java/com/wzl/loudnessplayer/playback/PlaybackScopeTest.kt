package com.wzl.loudnessplayer.playback

import com.wzl.loudnessplayer.data.AudioTrack
import com.wzl.loudnessplayer.data.MusicFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackScopeTest {
    @Test
    fun usesOnlySelectedPersonalFolderTracksInTheirLibraryOrder() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val folder = MusicFolder(id = "folder", name = "收藏", trackIds = setOf("c", "a"))

        assertEquals(listOf("a", "c"), PlaybackScope.resolve(tracks, folder).map { it.id })
    }

    @Test
    fun keepsTheWholeLibraryWhenNoPersonalFolderIsSelected() {
        val tracks = listOf(track("a"), track("b"))

        assertEquals(tracks, PlaybackScope.resolve(tracks, null))
    }

    private fun track(id: String) = AudioTrack(
        id = id,
        uri = "content://audio/$id",
        title = id,
        artist = "artist",
        durationMs = 1_000L,
    )
}
