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

    @Test
    fun keepsPreferredFormatWhenNewBatchContainsSameRecording() {
        val selection = selectUniqueImports(
            existingTracks = emptyList(),
            candidates = listOf(
                track("mp3", "Singer", "Same Song", AudioFileFormat.MP3, 180_000L),
                track("ape", "Singer", "Same Song", AudioFileFormat.APE, 181_200L),
                track("flac", "Singer", "Same Song", AudioFileFormat.FLAC, 180_500L),
            ),
        )

        assertEquals(listOf("flac"), selection.tracks.map { it.id })
        assertEquals(2, selection.skippedDuplicateCount)
    }

    @Test
    fun preservesExistingLibraryEntryInsteadOfReplacingItsFormat() {
        val selection = selectUniqueImports(
            existingTracks = listOf(
                track("existing", "Singer", "Same Song", AudioFileFormat.MP3, 180_000L),
            ),
            candidates = listOf(
                track("new", "Singer", "Same Song", AudioFileFormat.FLAC, 180_500L),
            ),
        )

        assertTrue(selection.tracks.isEmpty())
        assertEquals(1, selection.skippedDuplicateCount)
    }

    @Test
    fun doesNotMergeSameFormatOrMateriallyDifferentDurations() {
        val selection = selectUniqueImports(
            existingTracks = emptyList(),
            candidates = listOf(
                track("mp3-a", "Singer", "Song", AudioFileFormat.MP3, 180_000L),
                track("mp3-b", "Singer", "Song", AudioFileFormat.MP3, 180_000L),
                track("flac-live", "Singer", "Song", AudioFileFormat.FLAC, 200_000L),
            ),
        )

        assertEquals(3, selection.tracks.size)
        assertEquals(0, selection.skippedDuplicateCount)
    }

    private fun track(
        id: String,
        artist: String,
        title: String,
        format: AudioFileFormat = AudioFileFormat.MP3,
        durationMs: Long = 1_000L,
    ) = AudioTrack(
        id = id,
        uri = "content://audio/$id",
        title = title,
        artist = artist,
        durationMs = durationMs,
        format = format,
    )
}
