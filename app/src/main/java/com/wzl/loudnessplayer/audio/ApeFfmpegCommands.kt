package com.wzl.loudnessplayer.audio

import java.util.Locale

internal object ApeFfmpegCommands {
    private const val OUTPUT_SAMPLE_RATE_HZ = 48_000
    private const val OUTPUT_CHANNELS = 2
    private const val PCM_BYTES_PER_SAMPLE = 2
    private const val PCM_BYTES_PER_SECOND =
        OUTPUT_SAMPLE_RATE_HZ * OUTPUT_CHANNELS * PCM_BYTES_PER_SAMPLE

    fun stream(
        input: String,
        outputPipe: String,
        requestedBytePosition: Long,
    ): Array<String> {
        val seekSeconds = requestedBytePosition
            .coerceAtLeast(0L)
            .toDouble() / PCM_BYTES_PER_SECOND
        return buildList {
            // registerNewFFmpegPipe creates the FIFO before FFmpeg opens it, so overwrite is required.
            addAll(listOf("-y", "-nostdin", "-hide_banner", "-loglevel", "error"))
            if (seekSeconds > 0.0) {
                addAll(listOf("-ss", String.format(Locale.US, "%.3f", seekSeconds)))
            }
            addAll(
                listOf(
                    "-i",
                    input,
                    "-map",
                    "0:a:0",
                    "-vn",
                    "-sn",
                    "-dn",
                    "-ac",
                    OUTPUT_CHANNELS.toString(),
                    "-ar",
                    OUTPUT_SAMPLE_RATE_HZ.toString(),
                    "-c:a",
                    "pcm_s16le",
                    "-f",
                    if (requestedBytePosition == 0L) "wav" else "s16le",
                    outputPipe,
                ),
            )
        }.toTypedArray()
    }

    fun analyze(input: String): Array<String> =
        arrayOf(
            "-nostdin",
            "-hide_banner",
            "-nostats",
            "-loglevel",
            "info",
            "-i",
            input,
            "-map",
            "0:a:0",
            "-vn",
            "-sn",
            "-dn",
            "-af",
            "ebur128=peak=sample",
            "-f",
            "null",
            "-",
        )
}
