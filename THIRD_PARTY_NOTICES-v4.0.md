# 第三方组件说明

LoudnessPlayer v1.3.0 使用以下第三方开源组件。

## FFmpegKit Audio（ARM64）

- 组件：`dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`
- 架构：`arm64-v8a`
- 用途：提供 APE（Monkey's Audio）实时解码、PCM/WAV 音频流、EBU R128 响度分析和空输出能力
- 项目：https://github.com/ffmpegkit-maintained/ffmpeg
- 许可证：GNU Lesser General Public License v3.0

## FFmpegKit Minimal（ARM32 源码构建）

- 版本：`v8.1.7`
- 架构：`armeabi-v7a`
- 源码提交：`62b07bf097baf26b416c815aea514e05c9ad6d63`
- 用途：提供 FFmpeg 内置 APE 解码、PCM/WAV 音频流、EBU R128 响度分析和空输出能力，不包含额外视频或第三方编解码库
- 项目：https://github.com/ffmpegkit-maintained/ffmpeg
- 构建与哈希：[`third_party/ffmpeg-armv7/BUILD_METADATA.txt`](third_party/ffmpeg-armv7/BUILD_METADATA.txt)
- 许可证：GNU Lesser General Public License v3.0

## Smart Exception

- 组件：`com.arthenica:smart-exception-java:0.2.1`
- 用途：为 FFmpegKit 的 Java 会话错误处理提供运行时支持
- 项目：https://github.com/tanersener/smart-exception
- 许可证：BSD 3-Clause License

## FFmpeg

- 用途：FFmpegKit 内部的音频解复用、APE 解码、PCM/WAV 输出和 EBU R128 响度测量
- 项目：https://ffmpeg.org/
- 许可证：https://ffmpeg.org/legal.html

v1.3.0 不再把 APE 转换为 FLAC 并保存到手机。FFmpeg 解码输出仅通过短生命周期命名管道交给播放器，或在响度分析时交给 `null` 输出直接丢弃。

第三方组件继续由其各自的版权所有者拥有。LoudnessPlayer 对这些组件的使用不改变其原有许可证。
