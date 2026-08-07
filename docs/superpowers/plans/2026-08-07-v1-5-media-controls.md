# LoudnessPlayer v1.5.0 Media Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Release LoudnessPlayer 1.5.0 with import, playback and loudness analysis for M4A, AAC, OGG, Opus and WMA; a brown theme; system lock-screen controls; personal-folder playback scope; and faster loudness checks.

**Architecture:** Keep the library and folders in the existing ViewModel, but resolve an explicit `PlaybackScope` before creating a Media3 playlist. Move the actual player and MediaSession into a foreground `MediaSessionService`, allowing Android to publish the standard lock-screen notification/card and transport controls. Use platform decoding first where possible and a shared FFmpeg decoder/probe fallback for APE and WMA, without producing converted files.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Media3 1.10.1, FFmpegKit audio 8.1.7, coroutines, Android unit tests, Gradle.

## Global Constraints

- Release values are `versionCode 7` and `versionName "1.5.0"`.
- Supported library extensions are MP3, FLAC, WAV, APE, M4A, AAC, OGG, Opus and WMA.
- APE and WMA must decode inside the app; never write a converted FLAC or WAV file to device storage.
- The media transport card is an Android foreground media notification/lock-screen control surface, not a custom overlay shown above the system lock screen.
- A selected app-created personal folder defines the playback queue for sequential, repeat-one and shuffle modes.
- Keep global playback behavior unchanged when no personal folder is selected.
- Add brown alongside the existing light, dark, green and blue themes.
- Preserve user library, folders, settings and signing continuity for direct updates from v1.4.0.

---

## File structure

- Modify `app/build.gradle.kts` to add `media3-session` and set the release version.
- Modify `app/src/main/AndroidManifest.xml` to declare media-playback foreground-service permission and service.
- Modify `app/src/main/java/com/wzl/loudnessplayer/data/AudioFileFormat.kt` to define all supported extensions, MIME aliases and decoder strategy.
- Create `app/src/main/java/com/wzl/loudnessplayer/audio/FfmpegAudioDecoder.kt` for FFmpeg PCM commands and duration probing shared by APE/WMA.
- Create `app/src/main/java/com/wzl/loudnessplayer/playback/PlaybackScope.kt` for deterministic scoped queue resolution.
- Create `app/src/main/java/com/wzl/loudnessplayer/playback/LoudnessPlaybackService.kt` for the Media3 player, session and notification lifecycle.
- Modify `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt` to use the service controller, resolve folder queues and schedule two analyses concurrently.
- Modify `app/src/main/java/com/wzl/loudnessplayer/ui/Theme.kt`, `PlayerPreferences.kt` and `LoudnessPlayerApp.kt` for brown-theme selection and v1.5 copy.
- Modify `app/src/main/java/com/wzl/loudnessplayer/MainActivity.kt` and import UI copy to expose all formats without narrowing Android's document picker.
- Modify `README.md`, `CHANGELOG.md` and `.github/workflows/android-ci.yml` for the single public changelog entry, format matrix and packaged decoder verification.

### Task 1: Define formats and FFmpeg decoder contracts

**Files:**
- Modify: `app/src/main/java/com/wzl/loudnessplayer/data/AudioFileFormat.kt`
- Create: `app/src/main/java/com/wzl/loudnessplayer/audio/FfmpegAudioDecoder.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/data/AudioFileFormatTest.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/audio/FfmpegAudioDecoderTest.kt`

**Interfaces:**
- Produces `AudioFileFormat.from(name: String, mimeType: String?): AudioFileFormat?` for all nine extensions.
- Produces `FfmpegAudioDecoder.decodeToPcm(uri: Uri, format: AudioFileFormat): Array<String>` and `FfmpegAudioDecoder.probeDuration(uri: Uri): Array<String>`.

- [ ] **Step 1: Write failing format and command tests**

```kotlin
assertEquals(AudioFileFormat.WMA, AudioFileFormat.from("song.wma", "audio/x-ms-wma"))
assertEquals(AudioFileFormat.OPUS, AudioFileFormat.from("song.opus", "audio/opus"))
assertTrue(FfmpegAudioDecoder.decodeToPcm(uri, AudioFileFormat.WMA).contains("-f"))
```

- [ ] **Step 2: Run the focused tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*AudioFileFormatTest' --tests '*FfmpegAudioDecoderTest'`

Expected: FAIL because the new enum values and decoder object do not exist.

- [ ] **Step 3: Implement the smallest format table and decoder commands**

```kotlin
enum class DecoderPath { PLATFORM, FFMPEG_PCM }

fun decodeToPcm(uri: Uri, format: AudioFileFormat) = arrayOf(
    "-hide_banner", "-i", uri.toString(), "-vn", "-f", "s16le", "-ac", "2", "-ar", "44100", "-"
)
```

Use `FFMPEG_PCM` for APE and WMA; use `PLATFORM` for M4A, AAC, OGG and Opus while retaining an FFmpeg fallback command for analyzer failures.

- [ ] **Step 4: Run focused tests to verify success**

Run: `./gradlew testDebugUnitTest --tests '*AudioFileFormatTest' --tests '*FfmpegAudioDecoderTest'`

Expected: PASS.

- [ ] **Step 5: Commit the independent format layer**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/data/AudioFileFormat.kt app/src/main/java/com/wzl/loudnessplayer/audio/FfmpegAudioDecoder.kt app/src/test/java/com/wzl/loudnessplayer/data/AudioFileFormatTest.kt app/src/test/java/com/wzl/loudnessplayer/audio/FfmpegAudioDecoderTest.kt
git commit -m "feat: support additional audio formats"
```

### Task 2: Make playback scope deterministic for personal folders

**Files:**
- Create: `app/src/main/java/com/wzl/loudnessplayer/playback/PlaybackScope.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/playback/PlaybackScopeTest.kt`

**Interfaces:**
- Produces `PlaybackScopeResolver.resolve(tracks: List<Track>, selectedFolder: MusicFolder?): List<Track>`.
- Consumes `MusicFolder.trackIds` and returns tracks in library alphabetical order, filtered to the selected folder when present.

- [ ] **Step 1: Write failing queue-scope tests**

```kotlin
assertEquals(listOf("b", "c"), resolver.resolve(tracks, MusicFolder("f", "收藏", setOf("b", "c"))).map { it.id })
assertEquals(tracks.map { it.id }, resolver.resolve(tracks, null).map { it.id })
```

- [ ] **Step 2: Run the queue-scope test to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*PlaybackScopeTest'`

Expected: FAIL because `PlaybackScopeResolver` is absent.

- [ ] **Step 3: Implement folder filtering without changing global queue behavior**

```kotlin
fun resolve(tracks: List<Track>, selectedFolder: MusicFolder?): List<Track> =
    selectedFolder?.let { folder -> tracks.filter { it.id in folder.trackIds } } ?: tracks
```

Call this resolver from `syncPlayerQueue`, `playTrack`, previous and next queue rebuild paths.

- [ ] **Step 4: Run the queue-scope test to verify success**

Run: `./gradlew testDebugUnitTest --tests '*PlaybackScopeTest'`

Expected: PASS.

- [ ] **Step 5: Commit scoped queue behavior**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/playback/PlaybackScope.kt app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/test/java/com/wzl/loudnessplayer/playback/PlaybackScopeTest.kt
git commit -m "feat: keep playback inside personal folders"
```

### Task 3: Add robust playback and loudness fallback with bounded parallelism

**Files:**
- Modify: `app/src/main/java/com/wzl/loudnessplayer/audio/LoudnessAnalyzer.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/audio/ApeLoudnessAnalyzer.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/audio/LoudnessAnalysisRouterTest.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/audio/AnalysisSchedulerTest.kt`

**Interfaces:**
- Produces `LoudnessAnalysisRouter.analyze(track: Track): LoudnessResult` with platform-first and FFmpeg fallback selection.
- Produces `AnalysisScheduler(maxConcurrent: Int = 2)` that never has more than two active jobs.

- [ ] **Step 1: Write failing routing and scheduling tests**

```kotlin
assertEquals(DecoderPath.FFMPEG_PCM, router.pathFor(AudioFileFormat.WMA))
assertEquals(DecoderPath.PLATFORM, router.pathFor(AudioFileFormat.OGG))
assertTrue(peakActiveJobs <= 2)
```

- [ ] **Step 2: Run the new tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*LoudnessAnalysisRouterTest' --tests '*AnalysisSchedulerTest'`

Expected: FAIL because the router and bounded scheduler do not exist.

- [ ] **Step 3: Implement platform-first analysis and FFmpeg PCM fallback**

```kotlin
suspend fun analyze(track: Track): LoudnessResult = try {
    if (track.format.decoderPath == DecoderPath.FFMPEG_PCM) ffmpeg(track) else platform(track)
} catch (error: Exception) {
    ffmpeg(track)
}

private val semaphore = Semaphore(permits = 2)
```

Reuse the finite-header APE stream path for playback. For WMA, use the shared FFmpeg PCM path; do not create any temporary converted audio file. Preserve cancellation and report an actionable per-track failure when both decoders fail.

- [ ] **Step 4: Run focused routing tests and the existing meter tests**

Run: `./gradlew testDebugUnitTest --tests '*LoudnessAnalysisRouterTest' --tests '*AnalysisSchedulerTest' --tests '*R128MeterTest' --tests '*ApeLoudnessAnalyzerTest'`

Expected: PASS.

- [ ] **Step 5: Commit faster, resilient analysis**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/audio app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/test/java/com/wzl/loudnessplayer/audio
git commit -m "feat: speed up loudness analysis with decoder fallback"
```

### Task 4: Publish Android lock-screen media controls

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/wzl/loudnessplayer/playback/LoudnessPlaybackService.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/playback/PlaybackServiceContractTest.kt`

**Interfaces:**
- Produces a manifest-declared `LoudnessPlaybackService : MediaSessionService`.
- Produces `LoudnessPlaybackService.sessionToken(context: Context): SessionToken` for the ViewModel controller connection.

- [ ] **Step 1: Write failing service contract tests**

```kotlin
assertEquals(LoudnessPlaybackService::class.java.name, LoudnessPlaybackService.serviceClassName)
assertTrue(LoudnessPlaybackService.requiredForegroundServiceType.contains("mediaPlayback"))
```

- [ ] **Step 2: Run the service test to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*PlaybackServiceContractTest'`

Expected: FAIL because the MediaSession service has not been added.

- [ ] **Step 3: Create the MediaSession foreground service and connect it**

```kotlin
class LoudnessPlaybackService : MediaSessionService() {
    override fun onCreate() { super.onCreate(); session = MediaSession.Builder(this, player).build() }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session
}
```

Add `androidx.media3:media3-session:1.10.1`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and a non-exported service with `android:foregroundServiceType="mediaPlayback"`. Replace direct activity-owned player lifecycle with a `MediaController` connected to this session; retain track metadata so Android can render title, artist, album art, previous, play/pause, next and repeat/shuffle controls on the lock screen.

- [ ] **Step 4: Run unit tests, lint and a debug assembly**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL and no manifest foreground-service errors.

- [ ] **Step 5: Commit system media controls**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/wzl/loudnessplayer/playback/LoudnessPlaybackService.kt app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/test/java/com/wzl/loudnessplayer/playback/PlaybackServiceContractTest.kt
git commit -m "feat: add lock screen playback controls"
```

### Task 5: Add brown theme and complete format-facing UI

**Files:**
- Modify: `app/src/main/java/com/wzl/loudnessplayer/data/PlayerPreferences.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/ui/Theme.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/ui/LoudnessPlayerApp.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/MainActivity.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/data/PlayerPreferencesTest.kt`

**Interfaces:**
- Produces `AppTheme.BROWN` displayed in the existing drawer theme selector.
- The existing file picker accepts all library formats via `audio/*` and explicitly labels the supported set in the import UI.

- [ ] **Step 1: Write failing theme preference test**

```kotlin
assertEquals(AppTheme.BROWN, AppTheme.valueOf("BROWN"))
assertEquals("棕色", AppTheme.BROWN.displayName)
```

- [ ] **Step 2: Run the theme test to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*PlayerPreferencesTest'`

Expected: FAIL because `BROWN` does not exist.

- [ ] **Step 3: Implement brown colors and import copy**

```kotlin
BROWN("棕色")

private val BrownColors = darkColorScheme(primary = Color(0xFFD6A05E), surface = Color(0xFF261B15))
```

Add the `BROWN` branch in the theme `when`, update the drawer label and describe `MP3 / FLAC / WAV / APE / M4A / AAC / OGG / Opus / WMA` near import. Do not duplicate navigation controls; keep the existing drawer interaction.

- [ ] **Step 4: Run theme tests, lint and debug assembly**

Run: `./gradlew testDebugUnitTest --tests '*PlayerPreferencesTest' lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit UI completion**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/data/PlayerPreferences.kt app/src/main/java/com/wzl/loudnessplayer/ui/Theme.kt app/src/main/java/com/wzl/loudnessplayer/ui/LoudnessPlayerApp.kt app/src/main/java/com/wzl/loudnessplayer/MainActivity.kt app/src/test/java/com/wzl/loudnessplayer/data/PlayerPreferencesTest.kt
git commit -m "feat: add brown theme and import guidance"
```

### Task 6: Package, document and release v1.5.0

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `.github/workflows/android-ci.yml`

**Interfaces:**
- Produces `app-release.apk` signed with the existing v1.4.0-compatible release keystore.
- Produces one canonical v1.5.0 changelog entry with no duplicate release notes.

- [ ] **Step 1: Write a release-information check before changing version data**

```powershell
Select-String -Path app/build.gradle.kts -Pattern 'versionCode 7','versionName "1.5.0"'
Select-String -Path CHANGELOG.md -Pattern '1.5.0'
```

Expected: initially no match for at least one 1.5.0 value.

- [ ] **Step 2: Set release values and document only user-visible changes**

```kotlin
versionCode = 7
versionName = "1.5.0"
```

Document new format support, lock-screen controls, folder-scoped playback, parallel loudness checking and brown theme in one v1.5.0 section. Update CI native-library checks with decoder markers validated from the assembled APK; do not add unverified marker names.

- [ ] **Step 3: Run the complete pre-release verification**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

Expected: BUILD SUCCESSFUL and a signed `app-release.apk` whose package version is 1.5.0.

- [ ] **Step 4: Inspect the release artifact and signing identity**

Run: `Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256; & "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk`

Expected: APK verifies and the signing certificate SHA-256 equals `3F:4E:BB:71:22:1A:98:81:6C:10:3F:21:38:32:F7:4E:F1:A0:A2:C0:CD:66:4E:4F:06:A6:D4:48:63:A5:26:A0`.

- [ ] **Step 5: Commit, push, merge and publish the GitHub release**

```bash
git add app/build.gradle.kts README.md CHANGELOG.md .github/workflows/android-ci.yml
git commit -m "chore: release v1.5.0"
git push origin agent/v1-5-media-controls
```

Open a PR to `main`, wait for CI, merge only after it passes, then create the `v1.5.0` release with the signed APK and its SHA-256 value.

## Self-review

- Requirement 1 is covered by Tasks 1 and 3: all requested formats import, play and analyze through direct or FFmpeg fallback decoding.
- Requirement 2 is covered by Task 5: brown is included in the persisted theme enum and drawer selector.
- Requirement 3 is covered by Task 4: MediaSession foreground service supplies system lock-screen media controls and next-track actions.
- Requirement 4 is covered by Task 2: personal-folder membership supplies the playlist used by all playback modes.
- Requirement 5 is covered by Task 3: a bounded two-job scheduler improves throughput without overloading a device.
- Requirement 6 is covered by Task 6: exact 1.5.0 / 7 values, signing verification and release publishing.
