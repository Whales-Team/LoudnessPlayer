# LoudnessPlayer（响度播放器）

![音悦应用封面](./app/src/main/res/drawable-nodpi/app_cover.webp)

LoudnessPlayer 是一款面向 Android 的本地音乐播放器。它可以批量导入手机音频、自动校验整库响度，并在播放时把歌曲补偿到用户选择的目标响度。

## v1.2.0 主要功能

- 导入 MP3、FLAC、WAV、APE 文件
- 一键扫描手机媒体库，或递归导入整个文件夹
- APE 自动转换为 FLAC，并保存到 `Music/LoudnessPlayer`
- 用户可在 `-24 LUFS` 至 `-8 LUFS` 之间设置目标响度
- 设置目标后自动校验歌单内尚未分析的歌曲
- 顺序播放、单曲循环和随机播放
- 按首字母自动排序，并按歌曲名或歌手名搜索
- 按歌手分类，或按“歌曲名 + 歌手名”共同字段智能分类
- 创建个人音乐文件夹，一首歌曲可加入多个文件夹
- 支持浅色、深色、绿色、蓝色四套界面主题
- 支持桌面歌词悬浮窗，可粘贴普通歌词或 LRC 时间轴歌词
- 音乐库、文件夹、歌词、主题与播放设置均保存在本机

完整版本变化见 [CHANGELOG-v3.0.md](CHANGELOG-v3.0.md)。

## 响度统一

点击首页“统一响度”卡片可以设置适合自己的目标值：

- 默认值为 `-14 LUFS`
- 可选范围为 `-24 LUFS` 至 `-8 LUFS`
- 数值越接近 0，听起来越响
- 已分析歌曲会立即重新计算播放增益
- 尚未分析的 MP3、FLAC、WAV 会自动加入整库校验队列
- 增益限制在 `-12 dB` 至 `+9 dB`
- 应用会结合歌曲峰值限制正增益，尽量避免削波

响度分析结果与目标值相互独立，因此更改目标时无需重复解码已经分析完成的歌曲。

## APE 自动转换

Android 没有统一的 APE 系统解码要求。v1.2.0 使用内置 FFmpeg 执行转换，并同时提供 ARM 32 位和 ARM 64 位原生库：

1. 新导入或旧歌单中的 APE 会自动进入转换队列。
2. 转换结果以 FLAC 保存到手机的 `Music/LoudnessPlayer`。
3. 播放器音乐库中的 APE 条目会替换为新 FLAC。
4. 自建音乐文件夹中的歌曲关联会同步迁移。
5. 新 FLAC 自动进入响度校验。

为避免误删用户数据，原始 APE 文件会保留作为备份。转换失败时，播放器也会保留原 APE 条目并显示提示。

ARM64 使用维护版 FFmpegKit Audio；ARM32 使用同一维护源码固定提交构建的最小变体。ARM32 构建参数与二进制哈希记录在 [`third_party/ffmpeg-armv7/BUILD_METADATA.txt`](third_party/ffmpeg-armv7/BUILD_METADATA.txt)。

## 搜索与分类

- 导入后按照歌曲标题排序；相同标题再按照歌手排序。
- 搜索框同时匹配歌曲名和歌手名。
- “按歌手分类”保留演唱者分组。
- “按歌曲名智能分类”会拆分歌曲名和歌手名；两首歌曲至少共享两个有效字段时归入同一个相似组。
- 没有达到相似条件的歌曲统一显示在“其他歌曲”。

## 我的文件夹

在音乐库控制区域点击“我的文件夹”：

- 新建自定义文件夹，例如“通勤”“学习”“收藏”
- 从歌曲右侧菜单选择“加入我的文件夹”
- 一首歌曲可以同时属于多个文件夹
- 删除文件夹不会删除歌曲文件，也不会把歌曲移出总音乐库

## 桌面歌词

1. 从歌曲右侧菜单选择“编辑/粘贴歌词”。
2. 可输入普通歌词，或粘贴 `[00:12.50]第一句` 格式的 LRC 歌词。
3. 点击顶部歌词图标。
4. 首次使用时允许“显示在其他应用上层”权限。
5. 返回桌面后，可拖动歌词悬浮窗；点击悬浮窗右侧 `×` 可关闭。

没有歌词时，悬浮窗会显示当前歌曲名和歌手。

## 格式支持

| 格式 | 导入 | 播放 | 响度分析 | 说明 |
| --- | --- | --- | --- | --- |
| MP3 | 支持 | 支持 | 支持 | 直接加入音乐库 |
| FLAC | 支持 | 支持 | 支持 | Android 8.1 及以上兼容性最佳 |
| WAV | 支持 | 支持 | 支持 | 直接加入音乐库 |
| APE | 支持 | 转换后播放 | 转换后支持 | 自动保存为 FLAC，原文件保留 |

## 播放模式

播放控制栏左侧的模式按钮会依次切换：

- 顺序播放：按音乐库顺序播放，到末尾停止
- 单曲循环：当前歌曲循环播放
- 随机播放：随机选择下一首

播放器使用 Media3 连续播放队列，并在切歌或目标响度变化时短暂平滑调整音量，减少突兀的音量跳变。

## 主题

点击顶部调色盘图标，可选择：

- 浅色
- 深色
- 绿色
- 蓝色

主题选择会自动保存。

## 系统要求

- Android 7.0（API 24）及以上
- ARM 32 位或 ARM 64 位 Android 手机
- 一键扫描需要系统音频读取权限
- 桌面歌词需要悬浮窗权限
- APE 转换需要足够的临时空间和目标存储空间

转换过程全部在手机本地完成，不上传音频。

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

仓库已经包含校验过的 ARM32 JNI 库。需要从固定源码重建时，可在 GitHub Actions 中手动运行 `Build ARMv7 FFmpeg JNI`。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Jetpack Media3 / ExoPlayer
- Android MediaStore、Storage Access Framework
- Android MediaExtractor + MediaCodec
- FFmpegKit Audio 与最小 ARMv7 FFmpegKit（APE 解码、FLAC 编码）

## 体积优化

- Debug 与 Release 构建均启用 R8 代码压缩和资源裁剪
- 只打包手机常用的 `arm64-v8a` 与 `armeabi-v7a`
- ARM64 使用音频变体，ARM32 只编译 APE→FLAC 所需的最小变体
- 不打包 x86、x86_64 或无关视频编解码器
- 原“音悦”PNG 封面改为视觉近似无损的 WebP
- 原生库在 APK 中压缩存放

经过 CI 校验的双 ABI APK 为 19,853,160 字节；虽然加入了 ARM32/ARM64 APE 转换能力，仍比 v1.1.0 的 23,312,776 字节小约 14.8%。

## 隐私

播放器仅在用户主动授权后读取本机音频。媒体列表、歌词、响度结果、主题和自建文件夹保存在应用本地。应用不申请联网权限，不上传音频或个人数据。

## License

[MIT](LICENSE)
