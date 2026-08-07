package com.wzl.loudnessplayer.data

import java.text.Collator
import java.util.Locale

fun List<AudioTrack>.failedAnalysis(): List<AudioTrack> =
    filter { it.analysisStatus == AnalysisStatus.FAILED }
import kotlin.math.abs

data class ArtistGroup(
    val artist: String,
    val tracks: List<AudioTrack>,
)

data class SmartTitleGroup(
    val label: String,
    val tracks: List<AudioTrack>,
    val matchingFields: List<String> = emptyList(),
)

data class ImportSelection(
    val tracks: List<AudioTrack>,
    val skippedDuplicateCount: Int,
)

/**
 * Keeps one representative when the same recording is offered in different formats.
 *
 * Existing library entries always win to preserve folder membership and playback state. Within a
 * new batch, lossless compressed formats are preferred before WAV and MP3.
 */
fun selectUniqueImports(
    existingTracks: List<AudioTrack>,
    candidates: List<AudioTrack>,
): ImportSelection {
    val existingIds = existingTracks.mapTo(mutableSetOf(), AudioTrack::id)
    val selected = mutableListOf<AudioTrack>()
    var skippedDuplicateCount = 0

    candidates
        .distinctBy(AudioTrack::id)
        .filterNot { it.id in existingIds }
        .sortedWith(
            compareByDescending<AudioTrack> { it.format.importPreference }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.artist.lowercase(Locale.ROOT) },
        )
        .forEach { candidate ->
            val crossFormatDuplicate = (existingTracks + selected).any { accepted ->
                accepted.format != candidate.format &&
                    accepted.isSameRecordingAs(candidate)
            }
            if (crossFormatDuplicate) {
                skippedDuplicateCount += 1
            } else {
                selected += candidate
            }
        }

    return ImportSelection(
        tracks = selected,
        skippedDuplicateCount = skippedDuplicateCount,
    )
}

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

private fun AudioTrack.isSameRecordingAs(other: AudioTrack): Boolean {
    if (durationMs <= 0L || other.durationMs <= 0L) return false
    val normalizedTitle = title.normalizedIdentityField()
    val normalizedArtist = artist.normalizedIdentityField()
    if (
        normalizedTitle.isEmpty() ||
        normalizedArtist.isEmpty() ||
        normalizedTitle in UNKNOWN_IDENTITY_FIELDS ||
        normalizedArtist in UNKNOWN_IDENTITY_FIELDS
    ) {
        return false
    }
    return normalizedTitle == other.title.normalizedIdentityField() &&
        normalizedArtist == other.artist.normalizedIdentityField() &&
        abs(durationMs - other.durationMs) <= DUPLICATE_DURATION_TOLERANCE_MS
}

private fun String.normalizedIdentityField(): String =
    lowercase(Locale.ROOT)
        .replace(IDENTITY_FIELD_PATTERN, "")

private val AudioFileFormat.importPreference: Int
    get() = when (this) {
        AudioFileFormat.FLAC -> 9
        AudioFileFormat.APE -> 8
        AudioFileFormat.WAV -> 7
        AudioFileFormat.M4A -> 6
        AudioFileFormat.OPUS -> 5
        AudioFileFormat.OGG -> 4
        AudioFileFormat.AAC -> 3
        AudioFileFormat.WMA -> 2
        AudioFileFormat.MP3 -> 1
    }

private val FIELD_PATTERN = Regex("[\\p{L}\\p{N}]+")
private val IDENTITY_FIELD_PATTERN = Regex("[^\\p{L}\\p{N}]+")
private val IGNORED_FIELDS = setOf("feat", "featuring", "version", "music", "audio")
private val UNKNOWN_IDENTITY_FIELDS = setOf("未知曲目", "未知艺术家", "unknown")
private const val MAX_FIELDS_PER_TRACK = 12
private const val DUPLICATE_DURATION_TOLERANCE_MS = 2_000L
