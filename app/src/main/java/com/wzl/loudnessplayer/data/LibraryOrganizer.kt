package com.wzl.loudnessplayer.data

import java.text.Collator
import java.util.Locale

data class ArtistGroup(
    val artist: String,
    val tracks: List<AudioTrack>,
)

data class SmartTitleGroup(
    val label: String,
    val tracks: List<AudioTrack>,
    val matchingFields: List<String> = emptyList(),
)

fun List<AudioTrack>.sortedByTitleInitial(): List<AudioTrack> {
    val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { left, right ->
        val titleComparison = collator.compare(left.title, right.title)
        if (titleComparison != 0) {
            titleComparison
        } else {
            collator.compare(left.artist, right.artist)
        }
    }
}

fun List<AudioTrack>.matchingSearch(query: String): List<AudioTrack> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return this
    return filter { track ->
        track.title.contains(normalizedQuery, ignoreCase = true) ||
            track.artist.contains(normalizedQuery, ignoreCase = true)
    }
}

fun List<AudioTrack>.groupedByArtist(): List<ArtistGroup> =
    groupBy(AudioTrack::artist)
        .map { (artist, tracks) ->
            ArtistGroup(
                artist = artist,
                tracks = tracks.sortedByTitleInitial(),
            )
        }
        .sortedWith(
            compareBy<ArtistGroup> { if (it.artist == "未知艺术家") 1 else 0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.artist },
        )

fun List<AudioTrack>.groupedBySimilarTitle(): List<SmartTitleGroup> {
    if (isEmpty()) return emptyList()
    val sortedTracks = sortedByTitleInitial()
    val fieldsByTrack = sortedTracks.map(::searchableFields)
    val parent = IntArray(sortedTracks.size) { it }
    val tracksByFieldPair = mutableMapOf<String, MutableList<Int>>()

    fieldsByTrack.forEachIndexed { index, fields ->
        for (left in 0 until fields.lastIndex) {
            for (right in left + 1 until fields.size) {
                val key = "${fields[left]}\u0000${fields[right]}"
                tracksByFieldPair.getOrPut(key, ::mutableListOf).add(index)
            }
        }
    }

    tracksByFieldPair.values
        .filter { it.size > 1 }
        .forEach { indexes ->
            val first = indexes.first()
            indexes.drop(1).forEach { union(parent, first, it) }
        }

    val groupedIndexes = sortedTracks.indices.groupBy { find(parent, it) }
    val similarGroups = mutableListOf<SmartTitleGroup>()
    val ungroupedTracks = mutableListOf<AudioTrack>()

    groupedIndexes.values.forEach { indexes ->
        if (indexes.size == 1) {
            ungroupedTracks += sortedTracks[indexes.single()]
            return@forEach
        }
        val commonFields = indexes
            .flatMap { fieldsByTrack[it] }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 2 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(2)
            .map(Map.Entry<String, Int>::key)
        similarGroups += SmartTitleGroup(
            label = if (commonFields.isEmpty()) {
                "相似歌曲"
            } else {
                "相似：${commonFields.joinToString(" · ")}"
            },
            tracks = indexes.map(sortedTracks::get).sortedByTitleInitial(),
            matchingFields = commonFields,
        )
    }

    val result = similarGroups.sortedBy { it.label.lowercase(Locale.getDefault()) }.toMutableList()
    if (ungroupedTracks.isNotEmpty()) {
        result += SmartTitleGroup(
            label = "其他歌曲",
            tracks = ungroupedTracks.sortedByTitleInitial(),
        )
    }
    return result
}

internal fun searchableFields(track: AudioTrack): List<String> =
    FIELD_PATTERN
        .findAll("${track.title} ${track.artist}".lowercase(Locale.ROOT))
        .map(MatchResult::value)
        .filter { it.length >= 2 && it !in IGNORED_FIELDS }
        .distinct()
        .take(MAX_FIELDS_PER_TRACK)
        .sorted()
        .toList()

private fun find(parent: IntArray, index: Int): Int {
    if (parent[index] != index) {
        parent[index] = find(parent, parent[index])
    }
    return parent[index]
}

private fun union(parent: IntArray, left: Int, right: Int) {
    val leftRoot = find(parent, left)
    val rightRoot = find(parent, right)
    if (leftRoot != rightRoot) parent[rightRoot] = leftRoot
}

private val FIELD_PATTERN = Regex("[\\p{L}\\p{N}]+")
private val IGNORED_FIELDS = setOf("feat", "featuring", "version", "music", "audio")
private const val MAX_FIELDS_PER_TRACK = 12
