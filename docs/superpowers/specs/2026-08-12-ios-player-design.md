# LoudnessPlayer iOS 1.0.0 Design

## Goal

Build a native iPhone and iPad edition of LoudnessPlayer whose functional baseline matches Android v1.5.1. The source code, tests, and unsigned simulator build will live in `Whales-Team/LoudnessPlayer` and be continuously verified on GitHub-hosted macOS runners. Physical-device, TestFlight, and App Store distribution remain disabled until an Apple Developer Program team is available.

## Supported systems and release identity

- Minimum deployment target: iOS 16.0.
- Devices: iPhone and iPad, portrait-first with adaptive two-column iPad layouts.
- Product name: LoudnessPlayer; Chinese display name: 音悦.
- Initial iOS version: 1.0.0, documented as feature-equivalent to Android v1.5.1.
- Placeholder bundle identifier: `com.wzl.loudnessplayer.ios`. It may be changed before the first signed distribution because no App Store record exists yet.
- The iOS project is contained in `ios/LoudnessPlayerIOS`; Android source and release workflows remain independent.

## Technical approach

Use a native SwiftUI application with an observable application model and small services behind protocols. AVFoundation provides the audio session, native decoding, output graph, volume control, and background playback. MediaPlayer provides Control Center, headset, and lock-screen commands. ActivityKit and WidgetKit provide the platform equivalents of Android's lyrics overlay.

Use a hybrid decoder policy:

- AVFoundation first for MP3, FLAC, WAV, M4A, and AAC.
- A slim LGPL-compatible FFmpeg XCFramework for APE, WMA, OGG, Opus, and native-decoder failures.
- Every decoder exposes a common stream of interleaved PCM frames plus duration and stream metadata.
- FFmpeg integration is isolated behind `FFmpegAudioDecoder`; the app still compiles in a CI stub configuration before the binary XCFramework is vendored. The complete-format acceptance gate requires the real framework and decoder smoke tests on both arm64 device slices and arm64/x86_64 simulator slices.

The project will be generated from `project.yml` with XcodeGen. This keeps the project auditable on Windows and reproducible on GitHub macOS runners instead of committing a fragile hand-edited `.pbxproj` as the source of truth.

## Modules and boundaries

### Domain

Defines `AudioTrack`, `AudioFormat`, `AnalysisStatus`, `PlaybackMode`, `LibraryViewMode`, `MusicFolder`, theme, queue preview, and recovery state. Domain types are `Codable`, independent of SwiftUI and audio frameworks, and have unit tests for migration, sorting, matching, deduplication, and grouping.

### Library

`LibraryStore` persists one versioned JSON document in Application Support. It stores tracks, security-scoped bookmarks, display-only title/artist edits, loudness values, lyrics, personal folders, and preferences. Source audio bytes are not copied into the app container.

`FileImporter` supports multiple documents and directory selection through the Files app. It recursively enumerates a user-selected folder while security access is active, creates bookmarks, reads metadata, filters supported extensions, and performs cross-format duplicate detection. Existing library records win; otherwise lossless formats are preferred. If a bookmark becomes stale or a file moves, the record remains visible and prompts for reauthorization instead of disappearing.

### Audio

`AudioDecoder` is the shared interface for native and FFmpeg paths. `PlaybackEngine` owns `AVAudioSession`, `AVAudioEngine`, player nodes, queue state, seek state, and a short next-track PCM prebuffer. It supports sequential, repeat-one, and shuffle modes. When a personal folder is selected, its membership is the complete queue scope.

Normalization is non-destructive. `LoudnessMeter` implements EBU R128 / ITU-R BS.1770 integrated loudness and sample-peak measurement from decoded PCM. `NormalizationPolicy` computes bounded gain for the user target (`-24` through `-8` LUFS), including peak protection. Playback applies the gain to the audio graph and ramps it during track transitions.

`AnalysisCoordinator` runs at most two jobs only while playback is idle. Start includes pending and failed tracks but skips successful results. Stop cancels active decoder work, retains completed results, and leaves incomplete work restartable. Starting playback cancels and requeues active analysis immediately; failures update durable status and emit one non-blocking banner.

### Recovery

Failed tracks expose an opt-in FLAC recovery flow. The user chooses a Files destination. The app writes a new FLAC, imports it, and performs loudness verification. Only after both operations succeed does the UI present a final delete-original confirmation. Cancel, conversion failure, verification failure, or provider deletion failure always leaves the original library record and source file intact. Normal APE/WMA playback never creates a converted copy.

### System media and lyrics

`RemoteCommandService` publishes title, artist, duration, elapsed time, artwork, and playback state to `MPNowPlayingInfoCenter`, and handles play, pause, seek, previous, and next commands. The audio background mode is enabled. Lock-screen controls reflect the actual queue.

LRC parsing and current-line selection are shared by:

- an in-app synchronized lyrics screen and mini lyrics panel;
- a WidgetKit now-playing widget using shared App Group state when signing becomes available;
- an ActivityKit Live Activity for current lyric and playback state on supported devices.

iOS does not permit a persistent overlay over other apps. Live Activity, Dynamic Island, lock-screen media card, widget, and in-app lyrics are the accepted platform equivalents. Simulator CI compiles widget and Live Activity targets without distribution entitlements; signed deployment will add the final App Group identifier.

## User interface

SwiftUI uses a compact library screen on iPhone and `NavigationSplitView` on iPad. The existing 音悦 artwork and green visual identity remain the default. Light, dark, green, blue, and brown themes share semantic color tokens.

The main surface contains search, library/folder scope, classification selector, track list, back-to-top affordance, and a persistent now-playing bar. The bar shows previous, current, and next tracks plus seek, playback mode, previous, play/pause, and next controls. A trailing menu or edge gesture opens import, normalization, analysis, theme, lyrics, and folder actions.

Long press enters selection mode. Users can move selected tracks into personal folders or remove them only from the app library. Exactly one selected track can edit its display title and artist; source tags are not modified. Failed view supports retry and FLAC recovery. Smart grouping can optionally combine known identical artists; unknown artists are never combined into one artist group.

## Data and playback flow

1. The document picker returns security-scoped URLs.
2. The importer starts access, reads metadata, creates bookmarks, deduplicates, and writes library records.
3. The queue builder derives the active queue from global or personal-folder scope and playback mode.
4. Playback resolves a bookmark, selects native or FFmpeg decoding, prebuffers PCM, starts the audio graph, applies normalization, and publishes system metadata.
5. Idle analysis resolves the same bookmark and decoder, measures loudness without playing audio, and persists the result.
6. UI changes are driven by the application model on the main actor; decoding, scanning, and metering run in cancellable background tasks.

## Error handling and safety

- Unsupported, encrypted, corrupt, empty, or inaccessible files are skipped individually with a summarized import result.
- A decode failure never stops another playing track and never changes source audio.
- Bookmark access is balanced with `startAccessingSecurityScopedResource` / `stopAccessingSecurityScopedResource` through a scoped helper.
- Library persistence uses atomic replacement and retains a recoverable previous document during schema migration.
- Batch removal never calls file deletion.
- File deletion exists only inside the confirmed successful FLAC recovery state.
- Audio interruptions and route changes pause safely and update Now Playing state.
- The application works offline and requests no network access.

## Testing and continuous integration

Unit tests cover:

- JSON persistence and migrations;
- format recognition, sorting, search, duplicate selection, title similarity, artist grouping, and personal-folder queue scope;
- LRC parsing and lyric selection;
- BS.1770/R128 meter fixtures, normalization limits, and peak protection;
- coordinator playback priority, Start/Stop, cancellation, and success skipping;
- playback sequence and previous/current/next preview;
- FLAC recovery state transitions and the deletion confirmation boundary;
- bookmark-stale behavior through injected resolvers.

GitHub Actions uses a pinned macOS runner and Xcode version to:

1. install the pinned XcodeGen release;
2. generate the Xcode project;
3. run Swift unit tests on a named iPhone simulator;
4. build the iOS app, widget, and Live Activity for the simulator with code signing disabled;
5. archive build logs and the simulator `.app` as CI artifacts;
6. validate that the real FFmpeg package, when enabled, contains required architectures and decoders.

Pull requests require the iOS CI check. No IPA, TestFlight upload, or App Store submission is attempted without an Apple Developer team and signing secrets.

## Delivery phases and definition of complete

Implementation is split for verification, not scope reduction:

1. Reproducible project, domain/library features, core SwiftUI library UI, and macOS simulator CI.
2. Native playback, queue, normalization, R128 analysis, background audio, and lock-screen controls.
3. FFmpeg XCFramework integration for APE/WMA/OGG/Opus, safe FLAC recovery, and format smoke tests.
4. Full batch management, metadata editing, folders, themes, lyrics, widget, Live Activity, README, and final CI hardening.

The iOS 1.0.0 source release is complete only when all Android v1.5.1-equivalent behaviors described here are implemented, all tests pass on GitHub macOS, and a code-signing-disabled simulator build is downloadable from Actions. A simulator `.app` is not presented as installable on a physical iPhone.

## Future signed distribution

When an Apple Developer Program account and team are available, create the App ID and App Group, replace placeholder identifiers if necessary, enable automatic or managed signing, test on physical iPhone/iPad, add TestFlight delivery, complete FFmpeg and encryption/export declarations, prepare App Store metadata, and submit for review. These distribution tasks do not change the local-library or audio architecture.
