#ifndef FFMPEG_AUDIO_BRIDGE_H
#define FFMPEG_AUDIO_BRIDGE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct LPFFmpegDecoder LPFFmpegDecoder;

typedef struct {
    int sample_rate;
    int channels;
    int64_t total_frames;
    double duration;
} LPFFmpegStreamInfo;

LPFFmpegDecoder *lp_ffmpeg_open(
    const char *path,
    LPFFmpegStreamInfo *info,
    char *error,
    int error_size
);
int lp_ffmpeg_read(
    LPFFmpegDecoder *decoder,
    float *interleaved,
    int max_frames,
    char *error,
    int error_size
);
int lp_ffmpeg_seek(
    LPFFmpegDecoder *decoder,
    double seconds,
    char *error,
    int error_size
);
void lp_ffmpeg_cancel(LPFFmpegDecoder *decoder);
void lp_ffmpeg_close(LPFFmpegDecoder *decoder);

#ifdef __cplusplus
}
#endif

#endif
