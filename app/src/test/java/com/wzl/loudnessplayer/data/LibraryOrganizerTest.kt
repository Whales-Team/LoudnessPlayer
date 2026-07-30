package com.wzl.loudnessplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun sortsAndSearchesByTitleOrArtist() {
        val tracks = listOf(
            track("2", "Second Singer", "Zulu"),
            track("1", "Alpha Singer", "Alpha"),
        )

        assertEquals(listOf("Alpha", "Zulu"), tracks.sortedByTitleInitial().map { it.title })
        assertEquals(listOf("Alpha"), tracks.matchingSearch("alpha singer").map { it.title })
        assertEquals(listOf("Zulu"), tracks.matchingSearch("zul").map { it.title })
    }

    @Test
    fun groupsTracksSharingAtLeastTwoTitleAndArtistFields() {
        val tracks = listOf(
            track("1", "Ocean Band", "Blue Sky Live"),
            track("2", "Ocean Band", "Blue Sky Remix"),
            track("3", "Another Artist", "Quiet Night"),
        )

        val groups = tracks.groupedBySimilarTitle()
        val similarGroup = groups.first { it.tracks.size == 2 }

        assertEquals(setOf("1", "2"), similarGroup.tracks.map { it.id }.toSet())
        assertTrue(similarGroup.matchingFields.size >= 2)
        assertEquals("其他歌曲", groups.last().label)
    }

    private fun track(id: String, artist: String, title: String) = AudioTrack(
        id = id,
        uri = "content://audio/$id",
        title = title,
        artist = artist,
        durationMs = 1_000L,
    )
}
