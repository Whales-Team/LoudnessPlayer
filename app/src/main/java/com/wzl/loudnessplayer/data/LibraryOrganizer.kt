package com.wzl.loudnessplayer.data

import java.util.Locale

data class ArtistGroup(
    val artist: String,
    val tracks: List<AudioTrack>,
)

fun List<AudioTrack>.groupedByArtist(): List<ArtistGroup> =
    groupBy(AudioTrack::artist)
        .map { (artist, tracks) ->
            ArtistGroup(
                artist = artist,
                tracks = tracks.sortedBy { it.title.lowercase(Locale.getDefault()) },
            )
        }
        .sortedWith(
            compareBy<ArtistGroup> { if (it.artist == "未知艺术家") 1 else 0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist },
        )
