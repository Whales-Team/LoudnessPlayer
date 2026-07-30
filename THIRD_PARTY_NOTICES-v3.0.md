# 第三方组件说明

LoudnessPlayer v1.2.0 使用以下第三方开源组件。

## FFmpegKit Audio（ARM64）

- 组件：`dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`
- 架构：`arm64-v8a`
- 用途：在 Android 安装包中提供 APE（Monkey's Audio）解码与 FLAC 编码能力
- 项目：https://github.com/ffmpegkit-maintained/ffmpeg
- 许可证：GNU Lesser General Public License v3.0

## FFmpegKit Minimal（ARM32 源码构建）

- 版本：`v8.1.7`
- 架构：`armeabi-v7a`
- 源码提交：`62b07bf097baf26b416c815aea514e05c9ad6d63`
- 用途：提供 FFmpeg 内置 APE 解码器与 FLAC 编码器，不包含额外视频或第三方编解码库
- 项目：https://github.com/ffmpegkit-maintained/ffmpeg
- 构建与哈希：[`third_party/ffmpeg-armv7/BUILD_METADATA.txt`](third_party/ffmpeg-armv7/BUILD_METADATA.txt)
- 许可证：GNU Lesser General Public License v3.0

## Smart Exception

- 组件：`com.arthenica:smart-exception-java:0.2.1`
- 用途：为 FFmpegKit 的 Java 会话错误处理提供运行时支持
- 项目：https://github.com/tanersener/smart-exception
- 许可证：BSD 3-Clause License

## FFmpeg

- 用途：FFmpegKit 内部的音频解复用、解码与编码
- 项目：https://ffmpeg.org/
- 许可证：https://ffmpeg.org/legal.html

第三方组件继续由其各自的版权所有者拥有。LoudnessPlayer 对这些组件的使用不改变其原有许可证。
