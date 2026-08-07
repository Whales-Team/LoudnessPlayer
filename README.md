# LoudnessPlayer（响度播放器）

![音悦应用封面](./app/src/main/res/drawable-nodpi/app_cover.webp)

LoudnessPlayer 是一款 Android 本地音乐播放器，支持 MP3、FLAC、WAV、APE。它可以批量导入手机音频、自动分析整库响度，并在播放时把歌曲补偿到用户设置的目标响度。

[下载最新版 Android APK](https://github.com/Whales-Team/LoudnessPlayer/releases/latest)

## 功能一览

| 类别 | 功能 |
| --- | --- |
| 导入 | 多选文件、递归导入整个文件夹、一键扫描手机媒体库 |
| 格式 | MP3、FLAC、WAV、APE 导入、播放和响度分析 |
| 响度 | 自定义 `-24 LUFS` 至 `-8 LUFS`，整库自动校验，峰值保护 |
| 播放 | 顺序播放、单曲循环、随机播放、进度跳转、平滑切歌 |
| 管理 | 首字母排序、歌曲/歌手搜索、歌手分类、歌曲名智能分类 |
| 文件夹 | 创建个人文件夹，批量加入或移除已经导入的歌曲 |
| 去重 | 同一歌曲只有格式不同时自动保留一个版本，不删除手机文件 |
| 界面 | 浅色、深色、绿色、蓝色主题，可滑出的设置侧边栏 |
| 歌词 | 普通歌词与 LRC 时间轴歌词、桌面歌词悬浮窗 |

## 导入与去重

点击左上角三横线，或从屏幕左边缘向右滑动打开设置侧边栏，然后选择：

- “选择文件”：选择一个或多个音频文件。
- “整个文件夹”：递归导入所选目录及其子目录。
- “一键识别并导入手机全部音频”：扫描 Android MediaStore 已建立索引的音频。

导入后按歌曲标题首字母排序。标题、歌手相同且时长相差不超过 2 秒的跨格式歌曲只保留一个：

1. 已在音乐库中的版本优先，避免丢失歌词和文件夹关联。
2. 同一新批次按 FLAC、APE、WAV、MP3 顺序保留。
3. 元数据不足或格式相同的歌曲不会自动合并。

去重只影响应用音乐库，不会删除手机中的音频文件。

## 响度统一

- 默认目标为 `-14 LUFS`，可设置范围为 `-24 LUFS` 至 `-8 LUFS`。
- 设置目标后，未分析的歌曲自动进入整库校验队列。
- 已分析歌曲会立即按照新目标重新计算播放增益，无需再次解码。
- 增益限制在 `-12 dB` 至 `+9 dB`，并结合测得峰值限制正增益。
- MP3、FLAC、WAV 使用 Android 解码器；APE 使用内置 FFmpeg EBU R128 滤镜。

响度调整只作用于播放，不修改原始音频。不同手机厂商的音频效果实现可能有差异，本功能适合个人本地播放，不替代广播级响度计或母带处理。

## APE 直接播放

APE 不依赖手机系统解码器：

1. FFmpeg 在应用内实时解码 APE。
2. 应用根据 APE 帧计数写入精确的 PCM/WAV 时长头，音频流再通过短生命周期命名管道交给 Media3。
3. 管道随播放关闭，不会生成或保存 FLAC、WAV 副本。
4. APE 响度分析的解码输出直接丢弃，只保存响度和峰值数值；遇到不支持采样峰值的滤镜时会退回到保守的积分响度分析。

v1.2.0 曾把 APE 转换到 `Music/LoudnessPlayer`。v1.3.0 不再创建这种副本，但不会自动删除旧版本已经生成的 FLAC，避免误删用户文件。

## 搜索、分类与个人文件夹

- 搜索框同时匹配歌曲名和歌手名。
- 可查看全部歌曲、按歌手分组，或按歌曲名智能分组。
- 智能分组会拆分歌曲名和歌手名；至少共享两个有效字段时归入同组。
- 创建个人文件夹后，可立即批量选择已经导入的歌曲。
- 进入已有文件夹后，可通过“管理已有歌曲”批量添加或移除。
- 一首歌曲可以属于多个文件夹；删除文件夹不会删除歌曲。

## 桌面歌词与主题

从歌曲菜单选择“编辑/粘贴歌词”，可输入普通歌词或 `[00:12.50]第一句` 格式的 LRC 歌词。在设置侧边栏开启桌面歌词并授予“显示在其他应用上层”权限后，歌词悬浮窗会跟随播放进度更新，并支持拖动和关闭。

设置侧边栏可直接切换浅色、深色、绿色、蓝色四种主题，选择结果自动保存在本机。

## 格式与系统要求

| 格式 | 导入 | 播放 | 响度分析 | 说明 |
| --- | --- | --- | --- | --- |
| MP3 | 支持 | 支持 | 支持 | Android 系统解码 |
| FLAC | 支持 | 支持 | 支持 | Android 8.1 及以上兼容性最佳 |
| WAV | 支持 | 支持 | 支持 | Android 系统解码 |
| APE | 支持 | 支持 | 支持 | 内置 FFmpeg 实时解码，不生成副本 |

系统要求：

- Android 7.0（API 24）及以上。
- ARM64 或 ARMv7 Android 手机，不支持 x86 模拟器。
- 一键扫描需要系统音频读取权限。
- 桌面歌词需要悬浮窗权限。

## 安装

前往 [Releases](https://github.com/Whales-Team/LoudnessPlayer/releases) 下载最新版本中以 `.apk` 结尾的文件。`Source code (zip)` 和 `Source code (tar.gz)` 是源码，不能直接安装。

v1.4.0 起发布包使用固定发布签名。首次从旧的临时签名版本升级时，Android 会要求先卸载旧版；卸载会清除应用内歌单、歌词和设置，但不会删除手机中的原始音频。安装 v1.4.0 后，后续从 Releases 下载的更高版本可直接覆盖安装，并保留应用内数据。

## 本地构建

需要 JDK 17、Android SDK 36：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。

仓库包含经过校验的 ARM32 JNI 库。重建固定版本时，可在 GitHub Actions 手动运行 `Build ARMv7 FFmpeg JNI`。构建参数和二进制哈希见 [`third_party/ffmpeg-armv7/BUILD_METADATA.txt`](third_party/ffmpeg-armv7/BUILD_METADATA.txt)。

## 版本更新记录

### v1.4.0（2026-08-07）

- 修复部分 APE 在播放器中被错误识别为数百小时、播放完实际音频后出现静音尾部的问题：不再把未知长度 WAV 头交给播放器。
- 使用 APE 文件头中的帧数和采样率修正导入歌曲与旧歌单条目的时长，进度条、拖动和播放列表长度以真实时长为准。
- APE 响度分析保留采样峰值优先策略；若 FFmpeg 仅不兼容该峰值选项，则自动退回积分响度校验，并保持保守的峰值保护。
- GitHub Actions 改为使用固定发布签名构建 release APK；从本版开始，后续更新可覆盖安装并保留歌单、歌词、设置和文件夹关联。

### v1.3.1（2026-07-31）

- 修复 FFmpeg 因拒绝打开预创建命名管道而导致 APE 无法播放的问题。
- APE 响度分析改用兼容性更好的采样峰值模式，并增强 FFmpegKit 日志解析。
- 播放和响度失败提示会保留 FFmpeg 返回的关键原因，便于识别损坏文件或权限问题。
- CI 对 ARM64/ARMv7 的 `null` 输出和 `ebur128` 滤镜执行精确能力检查。

### v1.3.0（2026-07-31）

- APE 改为应用内实时解码播放和响度分析，不再保存 FLAC 副本。
- 新增跨格式重复歌曲过滤，保留已有条目或优先保留无损格式。
- 个人文件夹支持批量管理已经导入的歌曲。
- 响度、导入、桌面歌词和主题集中到可滑出的设置侧边栏。
- CI 增加 ARM64/ARMv7 的 APE、PCM/WAV、EBU R128 和无 x86 打包检查。

### v1.2.0（2026-07-30）

- 新增自定义目标响度、整库自动校验和峰值保护。
- 新增桌面歌词、LRC 时间轴歌词和四套主题。
- 新增顺序播放、单曲循环、随机播放、搜索和智能分类。
- 新增个人音乐文件夹。
- 首次加入双 ARM ABI 的 APE→FLAC 转换；该持久转换已在 v1.3.0 移除。
- 启用 R8、资源裁剪，并把封面压缩为 WebP。

### v1.1.0（2026-07-30）

- 新增 FLAC、WAV、APE 的识别与导入。
- 新增全机扫描、整个文件夹递归导入和按歌手分类。
- 增加格式标签、导入状态和重复 URI 跳过。

### v1.0.0（2026-07-30）

- 首次发布本地 MP3 导入、播放和进度控制。
- 新增基于 ITU-R BS.1770 / EBU R128 思路的响度分析。
- 新增 `-14 LUFS` 非破坏式播放补偿和本地音乐库。

## 技术栈与体积优化

- Kotlin、Jetpack Compose、Material 3、Media3 / ExoPlayer。
- Android MediaStore、Storage Access Framework、MediaExtractor、MediaCodec。
- FFmpegKit Audio 与最小 ARMv7 FFmpegKit。
- Debug 与 Release 均启用 R8 和资源裁剪。
- 只打包 `arm64-v8a` 与 `armeabi-v7a`，不包含 x86、x86_64 和无关视频编解码器。
- “音悦”封面使用 WebP；原生库在 APK 中压缩存放。

## 第三方组件与许可证

| 组件 | 版本/架构 | 用途 | 许可证 |
| --- | --- | --- | --- |
| [FFmpegKit Audio](https://github.com/ffmpegkit-maintained/ffmpeg) | `8.1.7` / ARM64 | APE 解码、PCM/WAV 音频流、EBU R128、空输出 | GNU LGPL v3.0 |
| [FFmpegKit Minimal](https://github.com/ffmpegkit-maintained/ffmpeg) | `8.1.7` / ARMv7 | 固定源码构建的最小内置音频能力 | GNU LGPL v3.0 |
| [Smart Exception](https://github.com/tanersener/smart-exception) | `0.2.1` | FFmpegKit Java 会话错误处理 | BSD 3-Clause |
| [FFmpeg](https://ffmpeg.org/) | FFmpegKit 内置 | 音频解复用、APE 解码、PCM/WAV 输出、响度测量 | [FFmpeg License](https://ffmpeg.org/legal.html) |

ARMv7 FFmpegKit 源码提交为 `62b07bf097baf26b416c815aea514e05c9ad6d63`。第三方组件继续由其版权所有者拥有，本项目对这些组件的使用不改变原许可证。

## 隐私

播放器仅在用户主动授权后读取本机音频。媒体列表、歌词、响度结果、主题和个人文件夹保存在应用本地。应用不申请联网权限，不上传音频或个人数据。

## License

[MIT](LICENSE)
