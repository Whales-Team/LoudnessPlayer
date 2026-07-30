# LoudnessPlayer（响度播放器）

![音悦应用封面](./app/src/main/res/drawable-nodpi/app_cover.png)

一款面向 Android 的本地音乐播放器。它支持从单个文件、整个文件夹或手机媒体库导入音乐，并分析歌曲的感知响度，在播放时自动补偿不同来源的音量差异。

## v1.1.0 功能

- 导入 MP3、FLAC、WAV、APE 文件
- 一次选择一个或多个音频文件
- 递归导入整个文件夹及其子文件夹
- 一键扫描 Android MediaStore 中的全部受支持音频
- 自动读取歌曲名称、演唱者和时长
- 一键按演唱者分组，未知演唱者自动排列在最后
- 显示每首歌曲的文件格式和响度结果
- 播放、暂停、进度跳转、上一首和下一首
- 音乐库、分类方式和分析结果保存在本机
- 不修改、不重新编码原始音频

完整版本变化见 [CHANGELOG.md](CHANGELOG.md)。

## 格式支持

| 格式 | 导入 | 播放 | 响度分析 |
| --- | --- | --- | --- |
| MP3 | 支持 | 支持 | 支持 |
| FLAC | 支持 | 支持 | 支持 |
| WAV | 支持 | 支持 | 支持 |
| APE | 支持 | 取决于手机系统解码器 | 暂不支持 |

Media3 官方直接支持 FLAC 和 WAV，但 Android 没有统一的 APE 系统解码要求。为了保持 APK 轻量且不引入额外 LGPL 解码组件，v1.1.0 会导入并管理 APE，并交由当前手机的系统解码器尝试播放。

## 导入方式

### 一键识别手机音频

点击“一键识别并导入手机全部音频”。首次使用时允许音频读取权限，应用会扫描 Android MediaStore 中的 MP3、FLAC、WAV 和 APE，并跳过已经导入的文件。

### 导入整个文件夹

点击“导入整个文件夹”，在系统目录选择器中指定一个目录。应用会递归扫描该目录及所有子目录，不需要全机音频权限。

### 选择文件

点击“选择文件”，通过 Android 系统文件选择器选择一个或多个音频文件。

## 响度处理原理

分析器解码音频 PCM 数据，应用 ITU-R BS.1770 K-weighting，按 EBU R128 的 400 ms 测量块、100 ms 步进以及绝对/相对门限估算 integrated LUFS。

播放时：

- 音量偏大的歌曲使用数字衰减；
- 音量偏小的歌曲使用 Android `LoudnessEnhancer` 提升；
- 补偿限制在 `-12 dB` 至 `+9 dB`；
- 默认目标为 `-14 LUFS`；
- 原始文件始终保持不变。

不同手机厂商的音频效果实现可能略有差异。本应用适合个人本地播放，不用于母带制作或广播级测量。

## 系统要求

- Android 6.0（API 23）及以上
- MP3 和 WAV 使用 Media3/Android 系统能力播放
- FLAC 在 Android 8.1（API 27）及以上具有统一的系统解码保证，较旧设备取决于厂商解码器
- APE 播放取决于手机系统是否提供对应解码器

## 获取 APK

前往仓库的 [Releases](https://github.com/Whales-Team/LoudnessPlayer/releases) 页面，下载最新版本中以 `.apk` 结尾的文件。`Source code (zip)` 和 `Source code (tar.gz)` 是源码压缩包，不能直接安装。

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
- Android MediaStore、Storage Access Framework
- Android MediaExtractor + MediaCodec

## 隐私

播放器仅在用户主动授权后读取本机音频。媒体列表和分析结果保存在应用本地，不上传音频或个人数据。应用不申请联网权限。

## License

[MIT](LICENSE)
