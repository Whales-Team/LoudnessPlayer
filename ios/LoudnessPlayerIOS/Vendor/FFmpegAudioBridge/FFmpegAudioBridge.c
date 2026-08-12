#include "FFmpegAudioBridge.h"

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/channel_layout.h>
#include <libavutil/error.h>
#include <libavutil/rational.h>
#include <libavutil/samplefmt.h>
#include <libswresample/swresample.h>
#include <stdatomic.h>
#include <errno.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct LPFFmpegDecoder {
    AVFormatContext *format;
    AVCodecContext *codec;
    SwrContext *resampler;
    AVPacket *packet;
    AVFrame *frame;
    int stream_index;
    int sample_rate;
    int channels;
    int draining;
    int eof;
    atomic_int cancelled;
    float *pending;
    int pending_frames;
    int pending_offset;
};

static void lp_error(char *buffer, int size, const char *prefix, int code) {
    if (!buffer || size <= 0) return;
    char detail[AV_ERROR_MAX_STRING_SIZE] = {0};
    av_strerror(code, detail, sizeof(detail));
    snprintf(buffer, (size_t)size, "%s: %s", prefix, detail);
}

static int lp_interrupt(void *opaque) {
    LPFFmpegDecoder *decoder = opaque;
    return decoder && atomic_load(&decoder->cancelled);
}

static void lp_free(LPFFmpegDecoder *decoder) {
    if (!decoder) return;
    free(decoder->pending);
    swr_free(&decoder->resampler);
    av_frame_free(&decoder->frame);
    av_packet_free(&decoder->packet);
    avcodec_free_context(&decoder->codec);
    avformat_close_input(&decoder->format);
    free(decoder);
}

LPFFmpegDecoder *lp_ffmpeg_open(
    const char *path, LPFFmpegStreamInfo *info, char *error, int error_size
) {
    if (!path || !info) return NULL;
    LPFFmpegDecoder *decoder = calloc(1, sizeof(*decoder));
    if (!decoder) return NULL;
    atomic_init(&decoder->cancelled, 0);

    decoder->format = avformat_alloc_context();
    if (!decoder->format) { lp_free(decoder); return NULL; }
    decoder->format->interrupt_callback.callback = lp_interrupt;
    decoder->format->interrupt_callback.opaque = decoder;

    int result = avformat_open_input(&decoder->format, path, NULL, NULL);
    if (result < 0) { lp_error(error, error_size, "open", result); lp_free(decoder); return NULL; }
    result = avformat_find_stream_info(decoder->format, NULL);
    if (result < 0) { lp_error(error, error_size, "stream info", result); lp_free(decoder); return NULL; }

    const AVCodec *codec = NULL;
    result = av_find_best_stream(decoder->format, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (result < 0) { lp_error(error, error_size, "audio stream", result); lp_free(decoder); return NULL; }
    decoder->stream_index = result;
    AVStream *stream = decoder->format->streams[result];
    decoder->codec = avcodec_alloc_context3(codec);
    if (!decoder->codec) { lp_free(decoder); return NULL; }
    result = avcodec_parameters_to_context(decoder->codec, stream->codecpar);
    if (result < 0 || (result = avcodec_open2(decoder->codec, codec, NULL)) < 0) {
        lp_error(error, error_size, "codec", result); lp_free(decoder); return NULL;
    }

    decoder->sample_rate = decoder->codec->sample_rate;
    decoder->channels = decoder->codec->ch_layout.nb_channels;
    AVChannelLayout output_layout;
    av_channel_layout_default(&output_layout, decoder->channels);
    result = swr_alloc_set_opts2(
        &decoder->resampler, &output_layout, AV_SAMPLE_FMT_FLT, decoder->sample_rate,
        &decoder->codec->ch_layout, decoder->codec->sample_fmt, decoder->codec->sample_rate,
        0, NULL
    );
    av_channel_layout_uninit(&output_layout);
    if (result < 0 || (result = swr_init(decoder->resampler)) < 0) {
        lp_error(error, error_size, "resampler", result); lp_free(decoder); return NULL;
    }

    decoder->packet = av_packet_alloc();
    decoder->frame = av_frame_alloc();
    if (!decoder->packet || !decoder->frame) { lp_free(decoder); return NULL; }

    double duration = NAN;
    if (stream->duration != AV_NOPTS_VALUE) duration = stream->duration * av_q2d(stream->time_base);
    else if (decoder->format->duration != AV_NOPTS_VALUE) duration = decoder->format->duration / (double)AV_TIME_BASE;
    int64_t frames = isfinite(duration) && duration >= 0 ? (int64_t)llround(duration * decoder->sample_rate) : -1;
    *info = (LPFFmpegStreamInfo){decoder->sample_rate, decoder->channels, frames, duration};
    return decoder;
}

static int lp_copy_pending(LPFFmpegDecoder *decoder, float *output, int max_frames) {
    int available = decoder->pending_frames - decoder->pending_offset;
    int frames = available < max_frames ? available : max_frames;
    if (frames <= 0) return 0;
    memcpy(output, decoder->pending + decoder->pending_offset * decoder->channels,
           (size_t)frames * decoder->channels * sizeof(float));
    decoder->pending_offset += frames;
    if (decoder->pending_offset == decoder->pending_frames) {
        free(decoder->pending); decoder->pending = NULL;
        decoder->pending_frames = decoder->pending_offset = 0;
    }
    return frames;
}

int lp_ffmpeg_read(
    LPFFmpegDecoder *decoder, float *interleaved, int max_frames, char *error, int error_size
) {
    if (!decoder || !interleaved || max_frames <= 0) return AVERROR(EINVAL);
    if (atomic_load(&decoder->cancelled)) return AVERROR_EXIT;
    int copied = lp_copy_pending(decoder, interleaved, max_frames);
    if (copied > 0) return copied;

    for (;;) {
        int result = avcodec_receive_frame(decoder->codec, decoder->frame);
        if (result >= 0) {
            int capacity = swr_get_out_samples(decoder->resampler, decoder->frame->nb_samples);
            decoder->pending = malloc((size_t)capacity * decoder->channels * sizeof(float));
            if (!decoder->pending) return AVERROR(ENOMEM);
            uint8_t *output[] = {(uint8_t *)decoder->pending};
            result = swr_convert(
                decoder->resampler, output, capacity,
                (const uint8_t **)decoder->frame->extended_data, decoder->frame->nb_samples
            );
            av_frame_unref(decoder->frame);
            if (result < 0) { lp_error(error, error_size, "convert", result); return result; }
            decoder->pending_frames = result;
            decoder->pending_offset = 0;
            return lp_copy_pending(decoder, interleaved, max_frames);
        }
        if (result == AVERROR_EOF) return 0;
        if (result != AVERROR(EAGAIN)) { lp_error(error, error_size, "decode", result); return result; }

        if (decoder->draining) {
            decoder->eof = 1;
            return 0;
        }
        result = av_read_frame(decoder->format, decoder->packet);
        if (result == AVERROR_EOF) {
            decoder->draining = 1;
            avcodec_send_packet(decoder->codec, NULL);
            continue;
        }
        if (result < 0) { lp_error(error, error_size, "read", result); return result; }
        if (decoder->packet->stream_index == decoder->stream_index) {
            result = avcodec_send_packet(decoder->codec, decoder->packet);
        }
        av_packet_unref(decoder->packet);
        if (result < 0 && result != AVERROR(EAGAIN)) {
            lp_error(error, error_size, "send packet", result); return result;
        }
        if (atomic_load(&decoder->cancelled)) return AVERROR_EXIT;
    }
}

int lp_ffmpeg_seek(LPFFmpegDecoder *decoder, double seconds, char *error, int error_size) {
    if (!decoder || !isfinite(seconds)) return AVERROR(EINVAL);
    AVStream *stream = decoder->format->streams[decoder->stream_index];
    int64_t timestamp = (int64_t)llround(seconds / av_q2d(stream->time_base));
    int result = av_seek_frame(decoder->format, decoder->stream_index, timestamp, AVSEEK_FLAG_BACKWARD);
    if (result < 0) { lp_error(error, error_size, "seek", result); return result; }
    avcodec_flush_buffers(decoder->codec);
    free(decoder->pending); decoder->pending = NULL;
    decoder->pending_frames = decoder->pending_offset = decoder->draining = decoder->eof = 0;
    return 0;
}

void lp_ffmpeg_cancel(LPFFmpegDecoder *decoder) {
    if (decoder) atomic_store(&decoder->cancelled, 1);
}

void lp_ffmpeg_close(LPFFmpegDecoder *decoder) { lp_free(decoder); }
