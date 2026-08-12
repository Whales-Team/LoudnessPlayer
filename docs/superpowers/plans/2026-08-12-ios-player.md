# LoudnessPlayer iOS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build and publish a native iPhone/iPad LoudnessPlayer 1.0.0 whose local-library, playback, loudness-normalization, organization, lyrics, and system-media behavior matches Android v1.5.1, with an unsigned simulator app verified by GitHub-hosted macOS CI.

**Architecture:** A SwiftUI application owns a main-actor `AppModel`, while focused services handle security-scoped library access, queue construction, AVAudioEngine playback, FFmpeg fallback decoding, R128 analysis, recovery, and system media integration. XcodeGen 2.46.0 generates the project from declarative YAML; FFmpeg 8.0.3 is built from its exact upstream tag as an LGPL-only XCFramework and accessed through a small C bridge.

**Tech Stack:** Swift 6, SwiftUI, AVFoundation, MediaPlayer, UniformTypeIdentifiers, WidgetKit, ActivityKit, XCTest, XcodeGen 2.46.0, FFmpeg 8.0.3 (`libavformat`, `libavcodec`, `libavutil`, `libswresample`, `libavfilter`), GitHub Actions on macOS 15 / Xcode 16.4.

## Global Constraints

- Minimum deployment target is iOS 16.0; support both iPhone and iPad with portrait-first adaptive layouts.
- Product name is `LoudnessPlayer`, Chinese display name is `音悦`, version is `1.0.0`, and build number starts at `1`.
- Bundle identifier is `com.wzl.loudnessplayer.ios`; widget bundle identifier is `com.wzl.loudnessplayer.ios.widget`.
- All iOS files live below `ios/LoudnessPlayerIOS`; Android source and release workflows remain unchanged.
- Imported source audio is referenced through security-scoped bookmarks and is never copied into the app container during normal import or playback.
- Supported formats are MP3, FLAC, WAV, APE, M4A, AAC, OGG, Opus, and WMA for import, playback, and loudness analysis.
- AVFoundation is tried first for MP3, FLAC, WAV, M4A, and AAC; the bundled FFmpeg libraries handle APE, WMA, OGG, Opus, and native-decoder failures.
- Normal playback and analysis never create converted audio copies; FLAC conversion is available only through an explicit recovery workflow.
- Loudness normalization is non-destructive, uses EBU R128 / ITU-R BS.1770 integrated loudness, accepts targets from `-24` through `-8` LUFS, and applies peak protection.
- Playback has priority over analysis. Analysis runs at most two jobs while playback is idle and cancels/requeues active analysis when playback begins.
- Removing tracks or batches from the library never deletes source files. Source deletion is reachable only after successful user-requested FLAC conversion, import, and loudness verification, followed by a second confirmation.
- iOS system equivalents replace Android overlays: lock-screen/Control Center media controls, Live Activity, widget, and in-app synchronized lyrics; no overlay over other applications is attempted.
- No Apple Developer account, physical-device IPA, TestFlight upload, or App Store submission is part of this release.
- The committed repository must remain buildable without secrets. GitHub CI produces only a code-signing-disabled simulator `.app` artifact.
- FFmpeg is configured without `--enable-gpl`, without nonfree components, and with only the audio protocols, demuxers, decoders, encoder, parsers, filters, and libraries required by this application.

---

## File and target map

`ios/LoudnessPlayerIOS/project.yml` is the project source of truth. It declares `LoudnessPlayer`, `LoudnessPlayerWidget`, `FFmpegAudioBridge`, `LoudnessPlayerTests`, and `FFmpegAudioBridgeTests` targets plus the shared app/widget schemes.

`ios/LoudnessPlayerIOS/Sources/Domain` contains framework-independent Codable value types and pure algorithms. `Sources/Library` owns JSON persistence, security bookmarks, imports, searching, grouping, and personal-folder mutations. `Sources/Audio` owns decoder selection, PCM transport, queue state, playback, loudness analysis, and recovery. `Sources/SystemMedia` owns Now Playing, remote commands, lyrics parsing, shared widget state, and Live Activity attributes. `Sources/App` owns `AppModel`; `Sources/UI` contains SwiftUI screens, rows, sheets, theme tokens, and assets. `Widget` contains the WidgetKit and ActivityKit extension entry points.

`Vendor/FFmpegAudioBridge` contains the public C API and implementation that wrap FFmpeg without exposing libav types to Swift. `Scripts/build-ffmpeg-xcframework.sh` builds `Vendor/Artifacts/FFmpegAudio.xcframework`; `Scripts/verify-ffmpeg.sh` validates slices and codec availability. `Fixtures` contains tiny generated PCM/LRC/metadata fixtures, never copyrighted songs.

`.github/workflows/ios-ci.yml` installs the pinned generator, builds/caches FFmpeg, generates the project, runs tests, builds all simulator targets, and uploads the unsigned `.app` plus logs. `docs/IOS.md` explains architecture, local/macOS build, GitHub artifact download, security-scoped access, codec licensing, and the limitation that the artifact cannot be installed on an iPhone.

### Task 1: Reproducible project, app shell, and CI smoke build

**Files:**
- Create: `ios/LoudnessPlayerIOS/project.yml`
- Create: `ios/LoudnessPlayerIOS/Config/App-Info.plist`
- Create: `ios/LoudnessPlayerIOS/Config/Widget-Info.plist`
- Create: `ios/LoudnessPlayerIOS/Sources/App/LoudnessPlayerApp.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/RootView.swift`
- Create: `ios/LoudnessPlayerIOS/Widget/LoudnessPlayerWidgetBundle.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Smoke/AppIdentityTests.swift`
- Create: `ios/LoudnessPlayerIOS/Scripts/ci-build.sh`
- Create: `.github/workflows/ios-ci.yml`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: no application interfaces.
- Produces: Xcode targets `LoudnessPlayer`, `LoudnessPlayerWidget`, and `LoudnessPlayerTests`; app entry point `LoudnessPlayerApp`; executable script `Scripts/ci-build.sh`.

- [ ] **Step 1: Write identity tests and the smallest compiling views**

```swift
import XCTest
@testable import LoudnessPlayer

final class AppIdentityTests: XCTestCase {
    func testReleaseIdentity() {
        XCTAssertEqual(AppIdentity.version, "1.0.0")
        XCTAssertEqual(AppIdentity.displayName, "音悦")
        XCTAssertEqual(AppIdentity.minimumIOS, "16.0")
    }
}
```

Create `AppIdentity` in `LoudnessPlayerApp.swift` with the three exact constants above, and make `RootView` initially render `Text(AppIdentity.displayName)` so the first build has one executable screen.

- [ ] **Step 2: Declare the XcodeGen targets and schemes**

Use these project-wide settings in `project.yml`:

```yaml
name: LoudnessPlayerIOS
options:
  bundleIdPrefix: com.wzl.loudnessplayer.ios
  deploymentTarget:
    iOS: "16.0"
settings:
  base:
    SWIFT_VERSION: "6.0"
    MARKETING_VERSION: "1.0.0"
    CURRENT_PROJECT_VERSION: "1"
    CODE_SIGN_STYLE: Automatic
targets:
  LoudnessPlayer:
    type: application
    platform: iOS
    sources: [Sources]
    info: { path: Config/App-Info.plist }
  LoudnessPlayerWidget:
    type: app-extension
    platform: iOS
    sources: [Widget, Sources/SystemMedia/Shared]
    info: { path: Config/Widget-Info.plist }
  LoudnessPlayerTests:
    type: bundle.unit-test
    platform: iOS
    sources: [Tests]
    dependencies: [{ target: LoudnessPlayer }]
schemes:
  LoudnessPlayer:
    build: { targets: { LoudnessPlayer: all, LoudnessPlayerWidget: all } }
    test: { targets: [LoudnessPlayerTests] }
```

Add `UIBackgroundModes = [audio]`, `NSSupportsLiveActivities = true`, document types for the nine supported audio extensions, and `UIApplicationSupportsIndirectInputEvents = true` to the app plist. Use the WidgetKit extension point in the widget plist.

- [ ] **Step 3: Add the pinned generator and unsigned CI commands**

`Scripts/ci-build.sh` must run these commands with `set -euo pipefail`:

```bash
xcodegen generate --spec project.yml
xcodebuild test -project LoudnessPlayerIOS.xcodeproj -scheme LoudnessPlayer \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro,OS=latest' \
  CODE_SIGNING_ALLOWED=NO | tee build/test.log
xcodebuild build -project LoudnessPlayerIOS.xcodeproj -scheme LoudnessPlayer \
  -configuration Release -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData CODE_SIGNING_ALLOWED=NO | tee build/app.log
```

The workflow uses `runs-on: macos-15`, `maxim-lobanov/setup-xcode@v1` with `xcode-version: '16.4'`, downloads XcodeGen release `2.46.0`, verifies the version string, calls the script, and uploads `build/DerivedData/Build/Products/Release-iphonesimulator/LoudnessPlayer.app` plus `build/*.log`.

- [ ] **Step 4: Run all checks available on Windows**

Run:

```powershell
python -c "import pathlib,yaml; yaml.safe_load(pathlib.Path('ios/LoudnessPlayerIOS/project.yml').read_text(encoding='utf-8')); print('project.yml OK')"
python -c "import plistlib,pathlib; [plistlib.loads(pathlib.Path(p).read_bytes()) for p in ['ios/LoudnessPlayerIOS/Config/App-Info.plist','ios/LoudnessPlayerIOS/Config/Widget-Info.plist']]; print('plists OK')"
```

Expected: both commands print `OK`. The first GitHub Actions run is the authoritative compile/test gate because this Windows host has no Xcode.

- [ ] **Step 5: Commit the smoke-build slice**

```bash
git add .github/workflows/ios-ci.yml .gitignore ios/LoudnessPlayerIOS
git commit -m "feat(ios): add reproducible SwiftUI project"
```

### Task 2: Domain model, sorting, deduplication, grouping, and queue algorithms

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Domain/AudioFormat.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Domain/AudioTrack.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Domain/LibraryModels.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Domain/LibraryOrganizer.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Domain/PlaybackQueue.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Domain/AudioFormatTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Domain/LibraryOrganizerTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Domain/PlaybackQueueTests.swift`

**Interfaces:**
- Consumes: Foundation only.
- Produces: `AudioFormat.from(url:mimeType:) -> AudioFormat?`; `LibraryOrganizer.sorted(_:query:) -> [AudioTrack]`; `LibraryOrganizer.preferredDuplicates(_:) -> [AudioTrack]`; `LibraryOrganizer.groups(_:mode:mergeSameArtist:) -> [TrackGroup]`; `PlaybackQueue(scope:mode:seed:)`; `PlaybackQueue.preview -> QueuePreview`.

- [ ] **Step 1: Write failing format, ordering, duplicate, grouping, and queue tests**

Cover all nine extensions and case-insensitive matching. Assert Chinese titles sort by `localizedStandardCompare`, empty artists sort last, and duplicate selection follows this lossless-first order: FLAC, APE, WAV, ALAC-in-M4A, Opus, OGG, AAC/M4A, WMA, MP3. Define a duplicate as the same normalized title and artist with duration difference no greater than two seconds; preserve an existing library record over all new candidates.

```swift
func testPersonalFolderIsCompleteQueueScope() {
    let queue = PlaybackQueue(scope: [trackB, trackC], mode: .sequential, seed: 7)
    XCTAssertEqual(queue.preview.current?.id, trackB.id)
    XCTAssertNil(queue.preview.previous)
    XCTAssertEqual(queue.preview.next?.id, trackC.id)
}
```

Also test repeat-one, deterministic shuffle with seed `7`, wrapping previous/next, title-token grouping when two or more normalized tokens overlap, and same-artist grouping that never combines blank/`未知歌手` values.

- [ ] **Step 2: Run the domain tests in CI and observe failures**

Run the `LoudnessPlayerTests` scheme. Expected: compile failures naming the missing domain types and algorithms.

- [ ] **Step 3: Implement Codable domain types and pure algorithms**

Use these stable cases and fields:

```swift
enum AudioFormat: String, Codable, CaseIterable { case mp3, flac, wav, ape, m4a, aac, ogg, opus, wma }
enum AnalysisStatus: String, Codable { case pending, running, succeeded, failed }
enum PlaybackMode: String, Codable { case sequential, repeatOne, shuffle }
enum LibraryViewMode: String, Codable { case all, failed, titleGroups, artistGroups }
enum AppTheme: String, Codable, CaseIterable { case light, dark, green, blue, brown }

struct AudioTrack: Identifiable, Codable, Equatable, Sendable {
    let id: UUID
    var bookmark: Data
    var fileName: String
    var format: AudioFormat
    var title: String
    var artist: String
    var duration: TimeInterval
    var fileSize: Int64
    var contentFingerprint: String?
    var integratedLoudness: Double?
    var samplePeak: Double?
    var analysisStatus: AnalysisStatus
    var analysisMessage: String?
    var lrcText: String?
}
```

`PlaybackQueue` owns ordered IDs, current index, mode, and a seeded `SplitMix64` shuffle. It exposes `mutating func advance()`, `mutating func retreat()`, `mutating func select(id:)`, and a preview with real previous/current/next tracks.

- [ ] **Step 4: Run tests and commit**

Expected: all domain tests pass on macOS CI.

```bash
git add ios/LoudnessPlayerIOS/Sources/Domain ios/LoudnessPlayerIOS/Tests/Domain
git commit -m "feat(ios): add library and queue domain"
```

### Task 3: Versioned library persistence and security-scoped bookmarks

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Library/LibraryDocument.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Library/LibraryStore.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Library/BookmarkAccess.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Library/LibraryStoreTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Library/BookmarkAccessTests.swift`

**Interfaces:**
- Consumes: `AudioTrack`, `MusicFolder`, `AppTheme`.
- Produces: `LibraryStore.load() async throws -> LibraryDocument`; `LibraryStore.save(_:) async throws`; `BookmarkResolver.resolve(_:) throws -> ResolvedBookmark`; `ResolvedBookmark.withAccess<T>(_:) throws -> T`.

- [ ] **Step 1: Write persistence, migration, and stale-bookmark tests**

Test round-trip JSON, atomic recovery from `library.previous.json`, migration from schema `0` with missing folders/preferences, and retention of stale track records. Inject a fake resolver that records matched `start` and `stop` calls and assert access stops both after success and after a thrown decoder error.

- [ ] **Step 2: Run tests and confirm missing-type failures**

Run `xcodebuild test ... -only-testing:LoudnessPlayerTests/LibraryStoreTests -only-testing:LoudnessPlayerTests/BookmarkAccessTests`. Expected: compile failure before implementation.

- [ ] **Step 3: Implement schema 1 storage and balanced URL access**

```swift
struct LibraryDocument: Codable, Equatable, Sendable {
    static let currentSchema = 1
    var schema: Int
    var tracks: [AudioTrack]
    var folders: [MusicFolder]
    var preferences: UserPreferences
}

actor LibraryStore {
    init(directory: URL, fileManager: FileManager = .default)
    func load() throws -> LibraryDocument
    func save(_ document: LibraryDocument) throws
}
```

Write `library.next.json` using `.atomic`, rotate the valid current file to `library.previous.json`, then replace current. Bookmark creation uses `.withSecurityScope`; resolution uses `.withSecurityScope` and detects `bookmarkDataIsStale`. A stale result keeps the track and returns `.reauthorizationRequired` to the caller.

- [ ] **Step 4: Run focused and complete tests, then commit**

Expected: both focused suites and all earlier suites pass.

```bash
git add ios/LoudnessPlayerIOS/Sources/Library ios/LoudnessPlayerIOS/Tests/Library
git commit -m "feat(ios): persist security-scoped music library"
```

### Task 4: File and directory importing without audio copies

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Library/AudioMetadataReader.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Library/FileImporter.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Library/ImportResult.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/ImportDocumentPicker.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Library/FileImporterTests.swift`

**Interfaces:**
- Consumes: `AudioFormat`, `AudioTrack`, `LibraryOrganizer.preferredDuplicates(_:)`, `BookmarkCreating`, AVFoundation metadata.
- Produces: `FileImporter.importFiles(_:existing:) async -> ImportResult`; `FileImporter.importDirectory(_:existing:) async -> ImportResult`; `ImportResult(imported:skipped:failures:)`.

- [ ] **Step 1: Write importer tests with an in-memory file tree**

Test recursive traversal, hidden-file skipping, nine supported extensions, unsupported/corrupt/empty files being summarized rather than aborting the batch, bookmark creation once per retained track, and cross-format duplicate retention. Assert imported records contain bookmarks and metadata but no copied destination URL inside Application Support.

- [ ] **Step 2: Run the focused tests and verify failure**

Expected: missing `FileImporter` and `ImportResult` symbols.

- [ ] **Step 3: Implement cancellable import and picker adapters**

`AudioMetadataReader` reads common title/artist/duration with `AVURLAsset`; when duration is unavailable it keeps `0` and lets the decoder update it. `FileImporter` checks `Task.checkCancellation()` during recursive enumeration, uses a 128 KiB beginning/end hash plus file size and normalized metadata as `contentFingerprint`, deduplicates before bookmark creation, and always closes security access.

`ImportDocumentPicker` wraps `UIDocumentPickerViewController` twice: `.open` with multiple selection for files and `.open` with `UTType.folder` for directory selection. Accepted types include generic audio plus filename extension types for APE and WMA.

- [ ] **Step 4: Run importer/all tests and commit**

```bash
git add ios/LoudnessPlayerIOS/Sources/Library ios/LoudnessPlayerIOS/Sources/UI/ImportDocumentPicker.swift ios/LoudnessPlayerIOS/Tests/Library/FileImporterTests.swift
git commit -m "feat(ios): import files and folders without copies"
```

### Task 5: Decoder protocol, native decoder, and FFmpeg XCFramework

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/PCMChunk.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/AudioDecoder.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/NativeAudioDecoder.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/FFmpegAudioDecoder.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/DecoderFactory.swift`
- Create: `ios/LoudnessPlayerIOS/Vendor/FFmpegAudioBridge/include/FFmpegAudioBridge.h`
- Create: `ios/LoudnessPlayerIOS/Vendor/FFmpegAudioBridge/FFmpegAudioBridge.c`
- Create: `ios/LoudnessPlayerIOS/Scripts/build-ffmpeg-xcframework.sh`
- Create: `ios/LoudnessPlayerIOS/Scripts/verify-ffmpeg.sh`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/DecoderFactoryTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/DecoderSmokeTests.swift`
- Modify: `ios/LoudnessPlayerIOS/project.yml`
- Modify: `.github/workflows/ios-ci.yml`

**Interfaces:**
- Consumes: a security-accessible source `URL`.
- Produces: `AudioDecoder.open(url:) throws -> AudioStreamInfo`; `AudioDecoder.read(maxFrames:) throws -> PCMChunk?`; `AudioDecoder.seek(to:) throws`; `AudioDecoder.cancel()`; `DecoderFactory.make(for:) -> any AudioDecoder`.

- [ ] **Step 1: Write factory and deterministic PCM contract tests**

```swift
protocol AudioDecoder: AnyObject, Sendable {
    func open(url: URL) throws -> AudioStreamInfo
    func read(maxFrames: AVAudioFrameCount) throws -> PCMChunk?
    func seek(to seconds: TimeInterval) throws
    func cancel()
}
```

Assert native-first selection for MP3/FLAC/WAV/M4A/AAC, FFmpeg-first for APE/WMA/OGG/Opus, FFmpeg fallback after a native open error, finite duration derived from decoded samples when container duration is invalid, interleaved float32 PCM, and cancellation returning `DecoderError.cancelled`.

- [ ] **Step 2: Implement the native decoder and C bridge API**

`NativeAudioDecoder` uses `AVAudioFile` plus `AVAudioConverter` to output 48 kHz or the source rate, float32 stereo/mono PCM. The public C bridge exposes only opaque handles and POD values:

```c
typedef struct LPFFmpegDecoder LPFFmpegDecoder;
typedef struct { int sample_rate; int channels; int64_t total_frames; double duration; } LPFFmpegStreamInfo;
LPFFmpegDecoder *lp_ffmpeg_open(const char *path, LPFFmpegStreamInfo *info, char *error, int error_size);
int lp_ffmpeg_read(LPFFmpegDecoder *decoder, float *interleaved, int max_frames, char *error, int error_size);
int lp_ffmpeg_seek(LPFFmpegDecoder *decoder, double seconds, char *error, int error_size);
void lp_ffmpeg_cancel(LPFFmpegDecoder *decoder);
void lp_ffmpeg_close(LPFFmpegDecoder *decoder);
```

The bridge opens the best audio stream, decodes send/receive packets, converts with `swr_convert`, derives duration from decoded frames when `AV_NOPTS_VALUE`, caps reported duration to finite nonnegative values, and frees every packet/frame/context on all exits.

- [ ] **Step 3: Build a pinned slim LGPL FFmpeg XCFramework**

The script clones `https://git.ffmpeg.org/ffmpeg.git` at tag `n8.0.3`, records the resolved commit in `Vendor/Artifacts/FFmpegAudio.version`, and builds `arm64-iphoneos`, `arm64-iphonesimulator`, and `x86_64-iphonesimulator`. Configure each slice with:

```bash
--disable-programs --disable-doc --disable-debug --disable-network --disable-autodetect
--disable-everything --enable-avcodec --enable-avformat --enable-avutil
--enable-swresample --enable-avfilter --enable-protocol=file
--enable-demuxer=ape,asf,ogg,matroska,wav,mov,mp3,flac,aac
--enable-decoder=ape,wmav1,wmav2,wmapro,wmalossless,vorbis,opus,flac,aac,mp3,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,alac
--enable-parser=ape,vorbis,opus,flac,aac,mpegaudio
--enable-encoder=flac --enable-muxer=flac
--enable-filter=ebur128,aformat,aresample,anull
```

Merge simulator static libraries with `lipo`, wrap public headers and the five static libraries as `FFmpegAudio.xcframework` using `xcodebuild -create-xcframework`, and cache the artifact in CI by the tag plus script SHA-256. Never pass `--enable-gpl` or `--enable-nonfree`.

- [ ] **Step 4: Add real codec/architecture verification**

`verify-ffmpeg.sh` fails unless the XCFramework has `ios-arm64` and `ios-arm64_x86_64-simulator`, `lipo -info` reports the expected slices, the version file says `n8.0.3`, and a small bridge utility successfully opens generated APE, WMA, OGG/Vorbis, Opus, FLAC, AAC, MP3, and WAV fixtures and decodes at least 4,800 non-silent frames from each. Generate fixtures during CI from a one-second sine PCM source using the just-built encoder support; use bundled binary fixtures only for formats whose encoders are intentionally disabled.

- [ ] **Step 5: Run CI tests/build and commit**

Expected: simulator tests decode all nine formats, APE duration is within 0.05 seconds of fixture duration, and both app and widget link with signing disabled.

```bash
git add ios/LoudnessPlayerIOS .github/workflows/ios-ci.yml
git commit -m "feat(ios): bundle FFmpeg audio decoding"
```

### Task 6: R128 loudness measurement, gain policy, and playback-priority analysis

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/R128Meter.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/NormalizationPolicy.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/AnalysisCoordinator.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/R128MeterTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/NormalizationPolicyTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/AnalysisCoordinatorTests.swift`

**Interfaces:**
- Consumes: float32 `PCMChunk`, `DecoderFactory`, `LibraryStore` update closure, playback activity stream.
- Produces: `R128Meter.consume(_:)`; `R128Meter.result() throws -> LoudnessResult`; `NormalizationPolicy.gain(target:measured:peak:) -> GainDecision`; `AnalysisCoordinator.start(tracks:)`; `stop()`; `playbackDidStart()`.

- [ ] **Step 1: Write reference-fixture and coordinator tests**

Use programmatically generated silence, -20 dBFS mono sine, alternating gated blocks, stereo, and clipped samples. Assert absolute gate `-70 LUFS`, relative gate `ungated - 10 LU`, finite integrated result, sample peak, target clamping, and peak-protected gain. Coordinator tests use controllable fake decoders to assert maximum concurrency `2`, successful tracks skipped, pending/failed included, Stop cancellation retained, playback start cancels both active jobs within one scheduler turn, and failures emit one banner without stopping a fake playing track.

- [ ] **Step 2: Run focused tests and record expected failures**

Expected: missing meter, policy, and coordinator symbols.

- [ ] **Step 3: Implement BS.1770 gating and safe gain**

Implement K-weighting biquads, 400 ms blocks stepped every 100 ms, channel weighting, absolute/relative gating, and sample peak. `NormalizationPolicy` clamps the target to `[-24, -8]`, computes `target - integrated`, limits gain so predicted peak remains at or below `-1 dBFS`, and returns linear gain plus the effective dB value.

`AnalysisCoordinator` is an actor with two child tasks. It commits each success immediately, converts cancellation back to `.pending`, keeps decode errors as `.failed(message)`, and observes playback activity before dequeuing and inside every PCM loop.

- [ ] **Step 4: Run all tests and commit**

```bash
git add ios/LoudnessPlayerIOS/Sources/Audio ios/LoudnessPlayerIOS/Tests/Audio
git commit -m "feat(ios): add priority-aware loudness analysis"
```

### Task 7: Gap-minimized playback, scoped modes, and system media controls

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/PlaybackEngine.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/AudioSessionController.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/SystemMedia/NowPlayingSnapshot.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/SystemMedia/RemoteCommandService.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/PlaybackEngineTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/SystemMedia/RemoteCommandServiceTests.swift`

**Interfaces:**
- Consumes: `PlaybackQueue`, `DecoderFactory`, `NormalizationPolicy`, `AnalysisCoordinator`, `AudioTrack` bookmark access.
- Produces: `PlaybackEngine.play(queue:startAt:)`, `pause()`, `resume()`, `seek(to:)`, `next()`, `previous()`, `setMode(_:)`; published `PlaybackSnapshot`; `RemoteCommandService.bind(to:)`.

- [ ] **Step 1: Write engine state-machine and Now Playing tests**

Use fake decoder streams and an injected graph sink. Test folder-scoped sequential/repeat/shuffle behavior, truthful previous/current/next preview, analysis cancellation before decoder open, 250 ms next-track prebuffer, ramped normalized gain, seek clamping, invalid-duration correction, interruption pause/resume policy, route-change pause, end-of-file advance, and failure skipping with a non-blocking message. Test metadata maps title, artist, corrected duration, elapsed, playback rate, and artwork; test play/pause/seek/previous/next command closures call the engine once.

- [ ] **Step 2: Run tests to verify the engine is absent**

Expected: compile failures for `PlaybackEngine`, `PlaybackSnapshot`, and `RemoteCommandService`.

- [ ] **Step 3: Implement AVAudioEngine playback and queue prebuffer**

Use one `AVAudioEngine`, mixer node gain, and two alternating `AVAudioPlayerNode`s. Decode the current track off the main actor into bounded buffers, schedule them, pre-open the next queue item and retain at most 250 ms PCM, and cross-ramp mixer/player volumes over 30 ms at track boundaries. Do not let analysis use the audio engine or session. Set `.playback`, `.longFormAudio`, and `allowAirPlay`; react to interruptions and old-device-unavailable route changes.

- [ ] **Step 4: Implement lock-screen and Control Center integration**

Register `MPRemoteCommandCenter` once, remove all targets during shutdown, publish `MPNowPlayingInfoCenter.default().nowPlayingInfo`, set playback state, and update elapsed time at most once per second. Artwork decoding is asynchronous and bounded to 1,024 pixels. Previous/next actions always use the actual scoped queue.

- [ ] **Step 5: Run all tests and commit**

```bash
git add ios/LoudnessPlayerIOS/Sources/Audio ios/LoudnessPlayerIOS/Sources/SystemMedia ios/LoudnessPlayerIOS/Tests
git commit -m "feat(ios): add normalized background playback"
```

### Task 8: Safe FLAC recovery and library/folder editing operations

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/Audio/FLACRecoveryService.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/Library/LibraryEditor.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Audio/FLACRecoveryServiceTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/Library/LibraryEditorTests.swift`

**Interfaces:**
- Consumes: `FFmpegAudioDecoder`, FFmpeg FLAC encoder bridge, `R128Meter`, bookmarks, `LibraryStore`.
- Produces: `FLACRecoveryService.convert(track:destination:) -> AsyncStream<RecoveryEvent>`; `confirmOriginalDeletion(recoveryID:)`; `LibraryEditor` batch move/remove/rename/folder methods.

- [ ] **Step 1: Write recovery boundary and batch-edit tests**

Test cancel, conversion failure, verification failure, destination import failure, and provider deletion failure all retain the original file and record. Test success emits `.awaitingOriginalDeletionConfirmation`; decline keeps original; confirm calls the injected file deleter once only after the new FLAC bookmark and successful loudness result are persisted. Batch removal deletes records/memberships only. Rename changes display title/artist only. Folder deletion preserves tracks; one track may belong to multiple folders.

- [ ] **Step 2: Run focused tests and observe missing services**

Expected: compile failures for `FLACRecoveryService` and `LibraryEditor`.

- [ ] **Step 3: Implement transactional recovery and editing**

Recovery writes a `.partial` file in the user-selected destination, decodes to PCM and encodes FLAC through the bridge, closes and renames it, creates a bookmark, imports the new record, runs R128 analysis, and commits the updated document. Only a stored state containing the original URL, new track ID, and succeeded analysis accepts `confirmOriginalDeletion`. Failed partial files are removed; source files are untouched.

`LibraryEditor` is a main-actor facade whose mutations save through `LibraryStore` before publishing the new document. It sanitizes folder names, prevents case-insensitive duplicates, supports add/remove membership, batch library removal, and display-only metadata edits.

- [ ] **Step 4: Run all tests and commit**

```bash
git add ios/LoudnessPlayerIOS/Sources/Audio/FLACRecoveryService.swift ios/LoudnessPlayerIOS/Sources/Library/LibraryEditor.swift ios/LoudnessPlayerIOS/Tests
git commit -m "feat(ios): add safe recovery and library editing"
```

### Task 9: Lyrics, shared Now Playing state, widget, and Live Activity

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/SystemMedia/LRCParser.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/SystemMedia/Shared/NowPlayingSharedState.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/SystemMedia/LyricsActivityAttributes.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/SystemMedia/LiveActivityController.swift`
- Create: `ios/LoudnessPlayerIOS/Widget/NowPlayingWidget.swift`
- Create: `ios/LoudnessPlayerIOS/Widget/LyricsLiveActivity.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/SystemMedia/LRCParserTests.swift`
- Create: `ios/LoudnessPlayerIOS/Tests/SystemMedia/SharedStateTests.swift`
- Modify: `ios/LoudnessPlayerIOS/project.yml`

**Interfaces:**
- Consumes: playback snapshot and persisted LRC text.
- Produces: `LRCParser.parse(_:) -> [LyricLine]`; `LyricTimeline.line(at:)`; `NowPlayingSharedState`; `LiveActivityController.update(_:)`.

- [ ] **Step 1: Write parser, timeline, and shared-state tests**

Cover `[mm:ss.xx]`, `[hh:mm:ss.xxx]`, multiple timestamps on one line, metadata tags, unsorted input, duplicate timestamps, blank translations, current-line selection at boundaries, JSON round trip, and privacy-safe empty state.

- [ ] **Step 2: Implement parser and unsigned-compatible shared storage**

Store widget state in the App Group suite when `group.com.wzl.loudnessplayer` is available; otherwise fall back to the extension/app shared standard defaults so unsigned simulator targets compile and render fixture state. Limit stored artwork to a small JPEG and current/previous/next lyric lines.

- [ ] **Step 3: Implement widget and Live Activity views**

The widget shows artwork, title, artist, playback state, progress, and current lyric in system small/medium families. The Live Activity shows title/current lyric on the lock screen and compact/minimal Dynamic Island presentations when available. It exposes no fake playback buttons; system media controls remain the authoritative controls.

- [ ] **Step 4: Run tests, build both targets, and commit**

Expected: app and extension build with `CODE_SIGNING_ALLOWED=NO`; parser/shared-state tests pass.

```bash
git add ios/LoudnessPlayerIOS/Sources/SystemMedia ios/LoudnessPlayerIOS/Widget ios/LoudnessPlayerIOS/Tests/SystemMedia ios/LoudnessPlayerIOS/project.yml
git commit -m "feat(ios): add synchronized lyrics surfaces"
```

### Task 10: Main application model and complete SwiftUI workflow

**Files:**
- Create: `ios/LoudnessPlayerIOS/Sources/App/AppModel.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Theme/ThemePalette.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Library/LibraryScreen.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Library/TrackRow.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Library/SelectionToolbar.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Player/NowPlayingBar.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Player/NowPlayingScreen.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Player/LyricsPanel.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Settings/SettingsDrawer.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Settings/AnalysisPanel.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Settings/FolderManager.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Recovery/RecoverySheet.swift`
- Create: `ios/LoudnessPlayerIOS/Sources/UI/Assets.xcassets/Contents.json`
- Create: `ios/LoudnessPlayerIOS/Tests/App/AppModelTests.swift`
- Modify: `ios/LoudnessPlayerIOS/Sources/UI/RootView.swift`

**Interfaces:**
- Consumes: every service and domain interface from Tasks 2–9.
- Produces: one `@MainActor @Observable final class AppModel` used by all SwiftUI views; complete iPhone/iPad user workflow.

- [ ] **Step 1: Write AppModel orchestration tests**

Test initial load, query by title/artist, A–Z ordering after import, all/failed/title-group/artist-group views, same-artist toggle, personal-folder scope, Start/Stop analysis, skipping succeeded tracks, target LUFS re-evaluation without re-analysis, import summary banner, playback priority, selection/move/remove/edit, back-to-top request token, theme persistence, recovery prompts, stale-bookmark reauthorization, and playback queue preview.

- [ ] **Step 2: Implement AppModel with explicit dependencies**

```swift
@MainActor @Observable
final class AppModel {
    private(set) var document: LibraryDocument
    private(set) var playback: PlaybackSnapshot
    var query = ""
    var viewMode: LibraryViewMode = .all
    var selectedFolderID: UUID?
    var selection: Set<UUID> = []
    var banner: AppBanner?
    var isDrawerOpen = false
    var scrollToTopToken = 0
}
```

Inject store/importer/editor/player/analyzer/recovery/system-media protocols. Service callbacks hop to the main actor; decoder/scanner/meter work never runs on it.

- [ ] **Step 3: Build the library, drawer, multi-selection, and recovery UI**

Use `NavigationStack` on compact width and `NavigationSplitView` on regular width. The library includes search, folder scope, group selector, same-artist toggle, failed filter, A–Z list, empty/error states, long-press selection, batch move/remove, single-track display metadata edit, folder create/delete/manage, and an always-visible back-to-top button after the first screenful.

The leading three-line button and right-edge swipe open `SettingsDrawer`. It contains file/folder imports, target loudness slider, Start/Stop analysis, analyzed counts, failed-song view/recovery, themes, lyrics, and about/version. Destructive confirmations use clear source-versus-library wording.

- [ ] **Step 4: Build now-playing and lyrics views with semantic themes**

`NowPlayingBar` shows previous/current/next titles, progress, sequential/repeat-one/shuffle mode, previous, play/pause, and next. `NowPlayingScreen` adds artwork, seek, gain status, current LUFS, queue order, and synchronized lyrics. Define semantic palette tokens for light, dark, green, blue, and brown; maintain WCAG-readable text contrast and Dynamic Type without hard-coded text heights.

Use the existing green `音悦` artwork for the app/empty state, generate all required app-icon sizes from the repository source asset, and add accessible labels/hints to icon-only controls.

- [ ] **Step 5: Run AppModel tests, all tests, and both simulator builds**

Expected: all tests pass; iPhone and iPad simulator builds succeed; no Swift concurrency warning is treated as an error.

- [ ] **Step 6: Commit the complete user interface**

```bash
git add ios/LoudnessPlayerIOS/Sources/App ios/LoudnessPlayerIOS/Sources/UI ios/LoudnessPlayerIOS/Tests/App
git commit -m "feat(ios): complete LoudnessPlayer interface"
```

### Task 11: Documentation, privacy, licensing, and final CI acceptance

**Files:**
- Create: `docs/IOS.md`
- Create: `ios/LoudnessPlayerIOS/THIRD_PARTY_NOTICES.md`
- Create: `ios/LoudnessPlayerIOS/PrivacyInfo.xcprivacy`
- Create: `ios/LoudnessPlayerIOS/Scripts/validate-source.ps1`
- Create: `ios/LoudnessPlayerIOS/Scripts/validate-source.sh`
- Modify: `README.md`
- Modify: `.github/workflows/ios-ci.yml`

**Interfaces:**
- Consumes: completed project and CI outputs.
- Produces: user/developer documentation, privacy manifest, licensing notices, deterministic acceptance scripts, downloadable unsigned simulator artifact.

- [ ] **Step 1: Add source validation before documentation claims**

Both validators must fail when any supported format is absent from import/decoder/analysis tests, when version/name/bundle values differ between plist/project/docs, when `UIBackgroundModes` lacks audio, when the FFmpeg tag or license flags differ, when a source-deletion call exists outside `FLACRecoveryService`, or when Android build files changed relative to the branch point except shared documentation.

Run on Windows:

```powershell
pwsh -File ios/LoudnessPlayerIOS/Scripts/validate-source.ps1
```

Expected: `iOS source validation passed`.

- [ ] **Step 2: Write user-facing and maintainer documentation**

`docs/IOS.md` must explain features, Files-based import, security bookmarks, personal folders, playback modes, target loudness/analysis controls, failed-song recovery, lock-screen controls, lyrics surfaces, five themes, and supported formats. Include exact macOS generation/test/build commands, GitHub Actions artifact download steps, and a prominent statement that the unsigned simulator `.app` cannot install on a physical iPhone. Document the future developer-account signing steps separately.

Update the root README platform table and release history without weakening Android v1.5.1 documentation. List FFmpeg 8.0.3 LGPL libraries, build configuration, source URL, XcodeGen 2.46.0 MIT license, and Apple frameworks in third-party notices.

- [ ] **Step 3: Add the privacy manifest and hardened artifact metadata**

Declare no tracking, no collected data, and only actually used required-reason APIs. CI creates `LoudnessPlayer-iOS-1.0.0-simulator.zip` and `SHA256SUMS.txt`, uploads test/build/FFmpeg verification logs, and uses a 14-day artifact retention. Add concurrency cancellation and one retry for the FFmpeg source download; never retry failed tests.

- [ ] **Step 4: Run complete local validation and push the branch**

Run:

```powershell
pwsh -File ios/LoudnessPlayerIOS/Scripts/validate-source.ps1
git diff --check
git status --short
```

Expected: validator passes, `git diff --check` prints nothing, and only intended iOS/docs/workflow files remain before the documentation commit.

```bash
git add README.md docs/IOS.md ios/LoudnessPlayerIOS .github/workflows/ios-ci.yml
git commit -m "docs: publish iOS build and privacy guide"
git push -u origin agent/ios-player
```

- [ ] **Step 5: Verify GitHub-hosted macOS acceptance**

Watch the `iOS CI` workflow for `agent/ios-player`. The run is accepted only when XcodeGen generation, all XCTest suites, real FFmpeg codec/slice checks, Release simulator app build, widget/Live Activity build, source validation, artifact ZIP, and checksum upload all succeed. Download the artifact, verify its SHA-256, inspect that `LoudnessPlayer.app` and widget extension exist, and record the successful workflow URL in the delivery note.

- [ ] **Step 6: Integrate the verified branch and tag source release**

After CI succeeds, merge `agent/ios-player` into `main` without rewriting Android release history, push `main`, and create annotated tag `ios-v1.0.0` at the verified merge commit. Do not create an IPA or attach the simulator artifact to an Android release.

```bash
git checkout main
git merge --no-ff agent/ios-player -m "release: add LoudnessPlayer iOS 1.0.0"
git push origin main
git tag -a ios-v1.0.0 -m "LoudnessPlayer iOS 1.0.0 source release"
git push origin ios-v1.0.0
```

The final delivery report includes the repository/commit/tag links, passing workflow link, simulator artifact name, implemented format matrix, and the explicit statement that physical iPhone installation still requires a future Apple Developer team and signed build.
