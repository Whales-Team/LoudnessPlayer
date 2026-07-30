package com.wzl.loudnessplayer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

class LoudnessAnalyzer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun analyze(uri: Uri): R128Meter.LoudnessResult = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(appContext, uri, null)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("文件中没有可解码的音频轨道")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("音频轨道缺少 MIME 类型")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            decode(extractor, codec, inputFormat)
                ?: error("音频过短或没有可测量的声音")
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private suspend fun decode(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
    ): R128Meter.LoudnessResult? {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var meter = R128Meter(sampleRate, channelCount)

        while (!outputEnded) {
            coroutineContext.ensureActive()

            if (!inputEnded) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                        ?: error("无法获取解码器输入缓冲区")
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0L),
                            0,
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val outputFormat = codec.outputFormat
                    val newRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val newChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (newRate != sampleRate || newChannels != channelCount) {
                        sampleRate = newRate
                        channelCount = newChannels
                        meter = R128Meter(sampleRate, channelCount)
                    }
                    outputEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else {
                        AudioFormat.ENCODING_PCM_16BIT
                    }
                }

                MediaCodec.INFO_TRY_AGAIN_LATER,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED,
                -> Unit

                else -> if (outputIndex >= 0) {
                    codec.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                        if (bufferInfo.size > 0) {
                            val pcm = outputBuffer.duplicate().order(ByteOrder.nativeOrder())
                            pcm.position(bufferInfo.offset)
                            pcm.limit(bufferInfo.offset + bufferInfo.size)
                            consumePcm(pcm.slice().order(ByteOrder.nativeOrder()), outputEncoding, channelCount, meter)
                        }
                    }
                    outputEnded =
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
        }
        return meter.result()
    }

    private fun consumePcm(
        buffer: ByteBuffer,
        encoding: Int,
        channelCount: Int,
        meter: R128Meter,
    ) {
        val frame = DoubleArray(channelCount)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_32BIT -> 4
            else -> 2
        }

        while (buffer.remaining() >= bytesPerSample * channelCount) {
            for (channel in 0 until channelCount) {
                frame[channel] = readSample(buffer, encoding)
            }
            meter.addFrame(frame)
        }
    }

    private fun readSample(buffer: ByteBuffer, encoding: Int): Double =
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.toDouble()
            AudioFormat.ENCODING_PCM_8BIT ->
                ((buffer.get().toInt() and 0xff) - 128) / 128.0
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val least = buffer.get().toInt() and 0xff
                val middle = buffer.get().toInt() and 0xff
                val most = buffer.get().toInt()
                ((most shl 16) or (middle shl 8) or least) / 8_388_608.0
            }

            AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2_147_483_648.0
            else -> buffer.short / 32_768.0
        }

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
