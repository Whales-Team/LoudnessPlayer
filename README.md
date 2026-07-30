# LoudnessPlayer（响度播放器）

一款面向 Android 的本地 MP3 播放器。它会在导入后分析每首歌曲的感知响度，并在播放时自动补偿差异，让不同来源的歌曲听起来更接近同一音量。

## 功能

- 通过 Android 系统文件选择器一次导入一个或多个 MP3
- 播放、暂停、进度跳转、上一首和下一首
- 自动读取歌曲名称、艺术家和时长
- 导入后在后台分析响度
- 以 `-14 LUFS` 为默认目标进行非破坏式播放补偿
- 音乐库和响度结果保存在本机
- 不修改、不复制、不重新编码原始 MP3
- 不申请联网权限或广泛存储权限

## 响度处理原理

分析器解码 MP3 的 PCM 数据，应用 ITU-R BS.1770 K-weighting，按 EBU R128 的 400 ms 测量块、100 ms 步进以及绝对/相对门限估算 integrated LUFS。

播放时：

- 音量偏大的歌曲使用数字衰减；
- 音量偏小的歌曲使用 Android `LoudnessEnhancer` 提升；
- 补偿限制在 `-12 dB` 至 `+9 dB`，避免极端文件产生过大的音量变化；
- 原始文件始终保持不变。

> 不同手机厂商的音频效果实现可能略有差异。当前版本适合个人本地播放，并不用于母带制作或广播级测量。

## 系统要求

- Android 6.0（API 23）及以上
- 支持系统解码器可读取的 MP3 文件

## 获取 APK

打开仓库的 **Actions → Android CI → 最近一次成功运行 → Artifacts**，下载 `LoudnessPlayer-debug` 并解压得到 APK。

## 本地构建

需要 JDK 17、Android SDK 36：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Jetpack Media3 / ExoPlayer
- Android MediaExtractor + MediaCodec

## 隐私

播放器仅访问用户通过系统文件选择器主动选中的文件。媒体列表和分析结果保存在应用本地，不上传任何音频或个人数据。

## License

[MIT](LICENSE)

