package com.wzl.loudnessplayer.audio

import android.content.Context
import android.net.Uri
import java.io.InputStream
import kotlin.math.ceil

/**
 * Reads the frame counts stored in modern and legacy APE headers. Android's media stack does
 * not reliably expose this metadata, while the frame counts are authoritative for the source file.
 */
internal data class ApeAudioInfo(
    val durationMs: Long,
    val sampleFrames: Long,
    val sampleRateHz: Int,
    val channelCount: Int,
) {
    fun pcmDataBytes(
        outputSampleRateHz: Int,
        outputChannelCount: Int,
        bytesPerSample: Int,
    ): Long? = runCatching {
        val outputFrames = ceil(
            sampleFrames.toDouble() * outputSampleRateHz / sampleRateHz,
        ).toLong()
        Math.multiplyExact(
            Math.multiplyExact(outputFrames, outputChannelCount.toLong()),
            bytesPerSample.toLong(),
        ).takeIf { it in 1L..MAX_RIFF_DATA_BYTES }
    }.getOrNull()

    companion object {
        private const val MIN_SIGNATURE_BYTES = 6
        private const val MIN_LEGACY_HEADER_BYTES = 32
        private const val MIN_NEW_HEADER_BYTES = 76
        private const val MAX_HEADER_BYTES = 1_024
        private const val MIN_APE_VERSION = 3_800
        private const val NEW_HEADER_VERSION = 3_980
        private const val MAX_RIFF_DATA_BYTES = 0xffff_ffffL - 36L

        fun read(context: Context, uri: Uri): ApeAudioInfo? = runCatching {
            context.contentResolver.openInputStream(uri)?.use(::readHeader)
        }.getOrNull()

        fun parse(header: ByteArray): ApeAudioInfo? {
            if (header.size < MIN_SIGNATURE_BYTES || !header.copyOfRange(0, 4).contentEquals(MAGIC)) {
                return null
            }
            val version = header.uint16At(4)
            if (version < MIN_APE_VERSION) return null
            return if (version >= NEW_HEADER_VERSION) {
                parseNewHeader(header)
            } else {
                parseLegacyHeader(header, version)
            }
        }

        private fun parseNewHeader(header: ByteArray): ApeAudioInfo? {
            if (header.size < MIN_NEW_HEADER_BYTES) return null
            val descriptorBytes = header.uint32At(8)
            val headerBytes = header.uint32At(12)
            if (descriptorBytes !in 52L..MAX_HEADER_BYTES.toLong() || headerBytes < 24L) {
                return null
            }
            val audioHeaderOffset = descriptorBytes.toInt()
            if (audioHeaderOffset > header.size - 24) return null

            val blocksPerFrame = header.uint32At(audioHeaderOffset + 4)
            val finalFrameBlocks = header.uint32At(audioHeaderOffset + 8)
            val totalFrames = header.uint32At(audioHeaderOffset + 12)
            val channelCount = header.uint16At(audioHeaderOffset + 18)
            val sampleRateHz = header.uint32At(audioHeaderOffset + 20)
            return fromFrameCounts(
                blocksPerFrame = blocksPerFrame,
                finalFrameBlocks = finalFrameBlocks,
                totalFrames = totalFrames,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount,
            )
        }

        private fun parseLegacyHeader(header: ByteArray, version: Int): ApeAudioInfo? {
            if (header.size < MIN_LEGACY_HEADER_BYTES) return null
            val compressionLevel = header.uint16At(6)
            val blocksPerFrame = when {
                version >= 3_950 -> 73_728L * 4L
                version >= 3_900 || (version >= 3_800 && compressionLevel >= 4_000) -> 73_728L
                else -> 9_216L
            }
            return fromFrameCounts(
                blocksPerFrame = blocksPerFrame,
                finalFrameBlocks = header.uint32At(28),
                totalFrames = header.uint32At(24),
                sampleRateHz = header.uint32At(12),
                channelCount = header.uint16At(10),
            )
        }

        private fun fromFrameCounts(
            blocksPerFrame: Long,
            finalFrameBlocks: Long,
            totalFrames: Long,
            sampleRateHz: Long,
            channelCount: Int,
        ): ApeAudioInfo? {
            if (
                blocksPerFrame <= 0L ||
                finalFrameBlocks <= 0L ||
                totalFrames <= 0L ||
                finalFrameBlocks > blocksPerFrame ||
                channelCount !in 1..8 ||
                sampleRateHz !in 8_000L..384_000L
            ) {
                return null
            }
            val sampleFrames = runCatching {
                Math.addExact(
                    Math.multiplyExact(totalFrames - 1L, blocksPerFrame),
                    finalFrameBlocks,
                )
            }.getOrNull() ?: return null
            val durationMs = runCatching {
                Math.multiplyExact(sampleFrames, 1_000L) / sampleRateHz
            }.getOrNull()?.takeIf { it > 0L } ?: return null
            return ApeAudioInfo(
                durationMs = durationMs,
                sampleFrames = sampleFrames,
                sampleRateHz = sampleRateHz.toInt(),
                channelCount = channelCount,
            )
        }

        private fun readHeader(input: InputStream): ApeAudioInfo? {
            val header = ByteArray(MAX_HEADER_BYTES)
            var count = 0
            while (count < header.size) {
                val read = input.read(header, count, header.size - count)
                if (read <= 0) break
                count += read
                if (count < MIN_SIGNATURE_BYTES) continue
                val version = header.uint16At(4)
                if (version in MIN_APE_VERSION until NEW_HEADER_VERSION && count >= MIN_LEGACY_HEADER_BYTES) {
                    break
                }
                val descriptorBytes = header.uint32At(8)
                if (
                    version >= NEW_HEADER_VERSION &&
                    descriptorBytes in 52L..MAX_HEADER_BYTES.toLong() &&
                    count.toLong() >= descriptorBytes + 24L
                ) {
                    break
                }
            }
            return parse(header.copyOf(count))
        }

        private fun ByteArray.uint16At(offset: Int): Int =
            (getOrNull(offset)?.toInt()?.and(0xff) ?: return -1) or
                ((getOrNull(offset + 1)?.toInt()?.and(0xff) ?: return -1) shl 8)

        private fun ByteArray.uint32At(offset: Int): Long {
            if (offset < 0 || offset + 4 > size) return -1L
            return (this[offset].toLong() and 0xff) or
                ((this[offset + 1].toLong() and 0xff) shl 8) or
                ((this[offset + 2].toLong() and 0xff) shl 16) or
                ((this[offset + 3].toLong() and 0xff) shl 24)
        }

        private val MAGIC = byteArrayOf('M'.code.toByte(), 'A'.code.toByte(), 'C'.code.toByte(), ' '.code.toByte())
    }
}

internal object PcmWavHeader {
    const val HEADER_SIZE = 44

    fun create(
        sampleRateHz: Int,
        channelCount: Int,
        pcmDataBytes: Long,
    ): ByteArray {
        require(sampleRateHz > 0)
        require(channelCount > 0)
        require(pcmDataBytes in 0L..0xffff_ffffL - 36L)
        val bytesPerFrame = channelCount * PCM_BYTES_PER_SAMPLE
        val byteRate = Math.multiplyExact(sampleRateHz, bytesPerFrame)
        return ByteArray(HEADER_SIZE).also { header ->
            header.putAscii(0, "RIFF")
            header.putUInt32(4, pcmDataBytes + 36L)
            header.putAscii(8, "WAVE")
            header.putAscii(12, "fmt ")
            header.putUInt32(16, 16L)
            header.putUInt16(20, 1)
            header.putUInt16(22, channelCount)
            header.putUInt32(24, sampleRateHz.toLong())
            header.putUInt32(28, byteRate.toLong())
            header.putUInt16(32, bytesPerFrame)
            header.putUInt16(34, PCM_BYTES_PER_SAMPLE * 8)
            header.putAscii(36, "data")
            header.putUInt32(40, pcmDataBytes)
        }
    }

    private fun ByteArray.putAscii(offset: Int, value: String) {
        value.encodeToByteArray().copyInto(this, destinationOffset = offset)
    }

    private fun ByteArray.putUInt16(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putUInt32(offset: Int, value: Long) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
        this[offset + 2] = (value ushr 16).toByte()
        this[offset + 3] = (value ushr 24).toByte()
    }

    private const val PCM_BYTES_PER_SAMPLE = 2
}
