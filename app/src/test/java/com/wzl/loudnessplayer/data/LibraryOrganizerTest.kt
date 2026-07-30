package com.wzl.loudnessplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrganizerTest {
    @Test
    fun groupsArtistsAlphabeticallyAndPlacesUnknownLast() {
        val tracks = listOf(
            track("3", "未知艺术家", "Unknown"),
            track("2", "Beta", "Second"),
            track("1", "alpha", "First"),
            track("4", "alpha", "Another"),
        )

        val groups = tracks.groupedByArtist()

        assertEquals(listOf("alpha", "Beta", "未知艺术家"), groups.map { it.artist })
        assertEquals(listOf("Another", "First"), groups.first().tracks.map { it.title })
    }

    private fun track(id: String, artist: String, title: String) = AudioTrack(
        id = id,
        uri = "content://audio/$id",
        title = title,
        artist = artist,
        durationMs = 1_000L,
    )
}
