package com.wzl.loudnessplayer.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApeAudioInfoTest {
    @Test
    fun parsesV399ApeFrameCountIntoTheActualDuration() {
        val info = ApeAudioInfo.parse(
            apeV399Header(
                blocksPerFrame = 73_728,
                finalFrameBlocks = 40_068,
                totalFrames = 158,
                sampleRateHz = 44_100,
                channels = 2,
            ),
        )

        assertEquals(263_386L, info?.durationMs)
        assertEquals(11_615_364L, info?.sampleFrames)
        assertEquals(44_100, info?.sampleRateHz)
        assertEquals(2, info?.channelCount)
    }

    @Test
    fun rejectsInvalidOrNonApeHeaders() {
        assertNull(ApeAudioInfo.parse(ByteArray(76)))
        assertNull(
            ApeAudioInfo.parse(
                apeV399Header(
                    blocksPerFrame = 0,
                    finalFrameBlocks = 0,
                    totalFrames = 0,
                    sampleRateHz = 44_100,
                    channels = 2,
                ),
            ),
        )
    }

    @Test
    fun parsesLegacyApeHeaderUsingItsVersionSpecificFrameSize() {
        val info = ApeAudioInfo.parse(
            legacyApeHeader(
                version = 3_970,
                compressionLevel = 2_000,
                finalFrameBlocks = 10_000,
                totalFrames = 2,
                sampleRateHz = 44_100,
                channels = 2,
            ),
        )

        assertEquals(6_914L, info?.durationMs)
        assertEquals(304_912L, info?.sampleFrames)
    }

    @Test
    fun writesFinitePcmWavSizesInsteadOfAnUnknownLengthMarker() {
        val header = PcmWavHeader.create(
            sampleRateHz = 48_000,
            channelCount = 2,
            pcmDataBytes = 768_000L,
        )

        assertEquals(44, header.size)
        assertArrayEquals("RIFF".encodeToByteArray(), header.copyOfRange(0, 4))
        assertEquals(768_036L, littleEndianInt(header, 4))
        assertArrayEquals("WAVE".encodeToByteArray(), header.copyOfRange(8, 12))
        assertArrayEquals("data".encodeToByteArray(), header.copyOfRange(36, 40))
        assertEquals(768_000L, littleEndianInt(header, 40))
    }

    private fun apeV399Header(
        blocksPerFrame: Int,
        finalFrameBlocks: Int,
        totalFrames: Int,
        sampleRateHz: Int,
        channels: Int,
    ): ByteArray = ByteBuffer.allocate(76)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("MAC ".encodeToByteArray())
            putShort(3_990)
            putShort(0)
            putInt(52)
            putInt(24)
            putInt(0)
            putInt(0)
            putInt(0)
            putInt(0)
            putInt(0)
            put(ByteArray(16))
            putShort(2_000)
            putShort(0)
            putInt(blocksPerFrame)
            putInt(finalFrameBlocks)
            putInt(totalFrames)
            putShort(16)
            putShort(channels.toShort())
            putInt(sampleRateHz)
        }
        .array()

    private fun legacyApeHeader(
        version: Int,
        compressionLevel: Int,
        finalFrameBlocks: Int,
        totalFrames: Int,
        sampleRateHz: Int,
        channels: Int,
    ): ByteArray = ByteBuffer.allocate(32)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("MAC ".encodeToByteArray())
            putShort(version.toShort())
            putShort(compressionLevel.toShort())
            putShort(0)
            putShort(channels.toShort())
            putInt(sampleRateHz)
            putInt(0)
            putInt(0)
            putInt(totalFrames)
            putInt(finalFrameBlocks)
        }
        .array()

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int
            .toLong() and 0xffff_ffffL
}
