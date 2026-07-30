package com.wzl.loudnessplayer.lyrics

data class TimedLyricLine(
    val timeMs: Long,
    val text: String,
)

object LrcParser {
    private val timestampPattern = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(content: String?): List<TimedLyricLine> {
        if (content.isNullOrBlank()) return emptyList()
        return buildList {
            content.lineSequence().forEach { rawLine ->
                val matches = timestampPattern.findAll(rawLine).toList()
                if (matches.isEmpty()) return@forEach
                val text = rawLine
                    .substring(matches.last().range.last + 1)
                    .trim()
                    .takeIf(String::isNotEmpty)
                    ?: return@forEach
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                    val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                    val fractionText = match.groupValues[3]
                    val fractionMs = when (fractionText.length) {
                        1 -> fractionText.toLongOrNull()?.times(100L)
                        2 -> fractionText.toLongOrNull()?.times(10L)
                        3 -> fractionText.toLongOrNull()
                        else -> 0L
                    } ?: 0L
                    add(
                        TimedLyricLine(
                            timeMs = (minutes * 60L + seconds) * 1_000L + fractionMs,
                            text = text,
                        ),
                    )
                }
            }
        }.sortedBy(TimedLyricLine::timeMs)
    }

    fun lineAt(
        lines: List<TimedLyricLine>,
        positionMs: Long,
    ): String? {
        if (lines.isEmpty()) return null
        var low = 0
        var high = lines.lastIndex
        var resultIndex = -1
        while (low <= high) {
            val middle = (low + high).ushr(1)
            if (lines[middle].timeMs <= positionMs) {
                resultIndex = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return lines.getOrNull(resultIndex)?.text
    }

    fun fallbackLine(content: String?): String? =
        content
            ?.lineSequence()
            ?.map { timestampPattern.replace(it, "").trim() }
            ?.firstOrNull(String::isNotEmpty)
}
