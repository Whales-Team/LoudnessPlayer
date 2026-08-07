# LoudnessPlayer v1.5.1 Library Control Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Release v1.5.1 with non-disruptive loudness analysis, failed-track recovery, multi-select library management, queue previews, lock-screen media control continuity, editable display metadata, smarter artist grouping and a back-to-top action.

**Architecture:** Separate persistent loudness outcome from transient active work, and move scheduling policy into an `AnalysisCoordinator` that yields immediately to playback. Keep conversion destructive-safe through a per-track state machine: write FLAC, analyse it, then request/delete the original only after success. Extend existing Compose state with selection, filter and queue-preview data rather than adding a parallel library screen.

**Tech Stack:** Kotlin, coroutines, Jetpack Compose Material 3, Media3, FFmpegKit Audio, Android Storage Access Framework, JUnit 4, Gradle.

## Global Constraints

- Set `versionCode = 8` and `versionName = "1.5.1"`.
- Playback has the highest priority: running loudness work must not alter or interrupt playback.
- A successful loudness result is never analysed again by one-click Start.
- Batch remove operates on application library records only; it never deletes phone audio files.
- FLAC conversion never deletes an original before conversion and loudness verification both succeed, and requires an explicit final user confirmation.
- APE/WMA remain in-app FFmpeg decode paths; no persistent conversion copy is created for normal playback or analysis.
- The lock-screen surface is Android’s MediaSession media card, not a custom overlay above the lock screen.
- Preserve v1.4.0+ signing identity so v1.5.0 installs update in place and retain library data.

---

## File structure

- Modify `data/AudioTrack.kt` and `data/TrackRepository.kt` for durable analysis outcomes and edited display metadata.
- Create `audio/AnalysisCoordinator.kt` for pending/active/manual-stop scheduling.
- Create `audio/FlacRecoveryConverter.kt` for FFmpeg FLAC commands and safe conversion outcomes.
- Modify `PlayerViewModel.kt` to bind player state, analysis, conversion, selection and queue preview.
- Modify `data/LibraryOrganizer.kt` and `data/PlayerPreferences.kt` for artist grouping preference and filtered lists.
- Modify `ui/LoudnessPlayerApp.kt` for analysis controls, failure filter, long-press selection, editor dialog, queue preview and back-to-top button.
- Modify `MainActivity.kt` and `AndroidManifest.xml` only where SAF create/delete permissions are required.
- Modify `README.md` and `app/build.gradle.kts` for one canonical v1.5.1 record and release values.

### Task 1: Persist analysis outcome and metadata edits

**Files:**
- Modify: `app/src/main/java/com/wzl/loudnessplayer/data/AudioTrack.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/data/TrackRepository.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/data/TrackRepositoryTest.kt`

**Interfaces:**
- Produces `enum class AnalysisStatus { PENDING, SUCCESS, FAILED }`.
- Produces `AudioTrack.analysisStatus: AnalysisStatus` and `AudioTrack.analysisFailureMessage: String?`.
- Produces `AudioTrack.withEditedMetadata(title: String, artist: String): AudioTrack`.

- [ ] **Step 1: Write failing persistence and migration tests**

```kotlin
assertEquals(AnalysisStatus.SUCCESS, repository.loadTracks().single().analysisStatus)
assertEquals(AnalysisStatus.PENDING, repository.loadTracks().single().analysisStatus)
assertEquals("New Title", track.withEditedMetadata(" New Title ", " New Artist ").title)
```

- [ ] **Step 2: Run focused tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*TrackRepositoryTest'`

Expected: FAIL because the status fields and metadata helper do not exist.

- [ ] **Step 3: Implement backward-compatible JSON migration**

```kotlin
val migratedStatus = if (item.has("analysisStatus")) {
    AnalysisStatus.valueOf(item.optString("analysisStatus"))
} else if (loudnessLufs != null) AnalysisStatus.SUCCESS else AnalysisStatus.PENDING
```

Persist `FAILED` with a message truncated to 160 characters. Store edited title and artist in the existing application library JSON only; do not write source-file tags.

- [ ] **Step 4: Run focused tests to verify success**

Run: `./gradlew testDebugUnitTest --tests '*TrackRepositoryTest'`

Expected: PASS.

- [ ] **Step 5: Commit durable analysis and metadata state**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/data/AudioTrack.kt app/src/main/java/com/wzl/loudnessplayer/data/TrackRepository.kt app/src/test/java/com/wzl/loudnessplayer/data/TrackRepositoryTest.kt
git commit -m "feat: persist analysis outcomes and edited metadata"
```

### Task 2: Schedule loudness work around playback

**Files:**
- Create: `app/src/main/java/com/wzl/loudnessplayer/audio/AnalysisCoordinator.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/audio/AnalysisCoordinatorTest.kt`

**Interfaces:**
- Produces `AnalysisCoordinator.start(ids: Collection<String>)`, `stop()`, `onPlaybackChanged(isPlaying: Boolean)` and `eligibleIds(tracks: List<AudioTrack>): List<String>`.
- Emits `AnalysisEvent.Success(id, result)`, `AnalysisEvent.Failure(id, message)` and `AnalysisEvent.Interrupted(id)`.

- [ ] **Step 1: Write failing scheduler tests**

```kotlin
coordinator.start(listOf("pending", "failed", "success"))
assertEquals(listOf("pending", "failed"), coordinator.eligibleIds(tracks))
coordinator.onPlaybackChanged(true)
assertTrue(coordinator.activeIds.isEmpty())
assertTrue(coordinator.pendingIds.contains("pending"))
```

- [ ] **Step 2: Run the scheduler test to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*AnalysisCoordinatorTest'`

Expected: FAIL because the coordinator is absent.

- [ ] **Step 3: Implement two-worker idle-only scheduling**

```kotlin
private const val MAX_CONCURRENT_ANALYSES = 2

fun onPlaybackChanged(isPlaying: Boolean) {
    if (isPlaying) cancelActiveAndRequeue() else resumeIfStarted()
}
```

Call `onPlaybackChanged` from the existing Media3 `onIsPlayingChanged` listener. `start` ignores `SUCCESS`, retains `PENDING` and `FAILED`, and resets only selected failure messages on retry. `stop` cancels all active FFmpeg sessions, keeps incomplete IDs pending and prevents automatic resume until Start is tapped again.

- [ ] **Step 4: Run scheduler and existing loudness tests**

Run: `./gradlew testDebugUnitTest --tests '*AnalysisCoordinatorTest' --tests '*R128MeterTest' --tests '*ApeLoudnessAnalyzerTest'`

Expected: PASS.

- [ ] **Step 5: Commit playback-priority analysis**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/audio/AnalysisCoordinator.kt app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/test/java/com/wzl/loudnessplayer/audio/AnalysisCoordinatorTest.kt
git commit -m "feat: prioritize playback over loudness analysis"
```

### Task 3: Filter failed tracks and recover them as FLAC safely

**Files:**
- Create: `app/src/main/java/com/wzl/loudnessplayer/audio/FlacRecoveryConverter.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/MainActivity.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/audio/FlacRecoveryConverterTest.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/data/LibraryOrganizerTest.kt`

**Interfaces:**
- Produces `FlacRecoveryConverter.convert(source: Uri, destination: Uri): ConversionResult`.
- Produces `PlayerViewModel.failedTracks(): List<AudioTrack>`, `startAnalysis()`, `stopAnalysis()` and `recoverFailedTracks(trackIds: Set<String>, destinationTree: Uri?)`.
- Produces `RecoveryOutcome(originalId: String, flacTrackId: String?, deleteOriginalAllowed: Boolean, message: String?)`.

- [ ] **Step 1: Write failing failed-filter and FFmpeg command tests**

```kotlin
assertEquals(listOf("failed"), tracks.failedAnalysis().map { it.id })
assertTrue(FlacRecoveryConverter.arguments(input, output).contains("flac"))
assertFalse(RecoveryOutcome("old", null, false, "decode failed").deleteOriginalAllowed)
```

- [ ] **Step 2: Run focused tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*FlacRecoveryConverterTest' --tests '*LibraryOrganizerTest'`

Expected: FAIL because recovery types and failed filter do not exist.

- [ ] **Step 3: Implement conversion and confirmation boundary**

```kotlin
val deleteOriginalAllowed = conversion.isSuccess && recoveredTrack.analysisStatus == AnalysisStatus.SUCCESS
```

Create `.flac` through a user-writable SAF destination. Prefer the source parent only when a writable document/tree URI is available; otherwise launch the existing directory picker for a destination. Do not invoke `ContentResolver.delete` until the UI has shown a final confirmation listing only outcomes where `deleteOriginalAllowed` is true. A conversion failure updates the original track to `FAILED` and leaves all original content untouched.

- [ ] **Step 4: Run conversion tests**

Run: `./gradlew testDebugUnitTest --tests '*FlacRecoveryConverterTest' --tests '*LibraryOrganizerTest'`

Expected: PASS.

- [ ] **Step 5: Commit failed-track recovery**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/audio/FlacRecoveryConverter.kt app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/main/java/com/wzl/loudnessplayer/MainActivity.kt app/src/test/java/com/wzl/loudnessplayer/audio/FlacRecoveryConverterTest.kt app/src/test/java/com/wzl/loudnessplayer/data/LibraryOrganizerTest.kt
git commit -m "feat: recover failed tracks as flac"
```

### Task 4: Add selection, batch library actions and metadata editing

**Files:**
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/ui/LoudnessPlayerApp.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/data/LibraryOrganizerTest.kt`

**Interfaces:**
- Produces `selectedTrackIds: Set<String>`, `toggleTrackSelection(id: String)`, `clearTrackSelection()`, `moveSelectedToFolder(folderId: String, included: Boolean)`, `removeSelectedFromLibrary()` and `editTrackMetadata(id: String, title: String, artist: String)`.

- [ ] **Step 1: Write failing selection and metadata tests**

```kotlin
assertEquals(setOf("a", "b"), selection.toggle("b"))
assertEquals(emptySet<String>(), selection.toggle("b"))
assertEquals("Artist", edited.artist)
```

- [ ] **Step 2: Run focused tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*LibraryOrganizerTest'`

Expected: FAIL because selection operations and metadata edit behavior are absent.

- [ ] **Step 3: Implement long-press selection UI**

```kotlin
Modifier.combinedClickable(
    onClick = { onTrackClick(track.id) },
    onLongClick = { onTrackLongClick(track.id) },
)
```

Show a selection toolbar with personal-folder move/add/remove, app-library removal, conversion only when all selected tracks are `FAILED`, and Cancel. Add a title/artist editor dialog only for exactly one selected track. Removing tracks calls existing library removal logic and must not call `ContentResolver.delete`.

- [ ] **Step 4: Run unit tests and debug assembly**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit batch management**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/main/java/com/wzl/loudnessplayer/ui/LoudnessPlayerApp.kt app/src/test/java/com/wzl/loudnessplayer/data/LibraryOrganizerTest.kt
git commit -m "feat: add batch library management"
```

### Task 5: Extend views, queue preview and lock-screen synchronization

**Files:**
- Modify: `app/src/main/java/com/wzl/loudnessplayer/data/LibraryOrganizer.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/data/PlayerPreferences.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/playback/LoudnessPlaybackService.kt`
- Modify: `app/src/main/java/com/wzl/loudnessplayer/ui/LoudnessPlayerApp.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/data/LibraryOrganizerTest.kt`
- Test: `app/src/test/java/com/wzl/loudnessplayer/playback/PlaybackScopeTest.kt`

**Interfaces:**
- Produces `LibraryViewMode.FAILED`, `groupSameArtistInSmartView: Boolean`, and `List<AudioTrack>.groupedBySmartRule(groupSameArtist: Boolean)`.
- Produces `QueuePreview(previous: AudioTrack?, current: AudioTrack?, next: AudioTrack?)` from the active queue and current Media3 index.

- [ ] **Step 1: Write failing grouping and queue-preview tests**

```kotlin
assertEquals(1, tracks.groupedBySmartRule(groupSameArtist = true).size)
assertEquals("previous", preview.previous?.id)
assertEquals("next", preview.next?.id)
```

- [ ] **Step 2: Run focused tests to verify failure**

Run: `./gradlew testDebugUnitTest --tests '*LibraryOrganizerTest' --tests '*PlaybackScopeTest'`

Expected: FAIL because the artist option, failed view and preview type are absent.

- [ ] **Step 3: Implement filtered and ordered views**

```kotlin
fun preview(queue: List<AudioTrack>, currentIndex: Int): QueuePreview = QueuePreview(
    previous = queue.getOrNull(currentIndex - 1),
    current = queue.getOrNull(currentIndex),
    next = queue.getOrNull(currentIndex + 1),
)
```

Persist the same-artist option, exclude unknown artists from artist-only groups, show a failed count filter, and attach preview metadata to the active global/personal-folder queue. For shuffle, use the actual Media3 next/previous media-item indices. Keep `MediaSessionService` alive only during active playback and update current title/artist metadata whenever a queue transition occurs.

- [ ] **Step 4: Implement back-to-top affordance and run verification**

```kotlin
if (listState.firstVisibleItemIndex > 2) {
    FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) { Icon(Icons.Default.VerticalAlignTop, null) }
}
```

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit library-view and playback presentation**

```bash
git add app/src/main/java/com/wzl/loudnessplayer/data/LibraryOrganizer.kt app/src/main/java/com/wzl/loudnessplayer/data/PlayerPreferences.kt app/src/main/java/com/wzl/loudnessplayer/PlayerViewModel.kt app/src/main/java/com/wzl/loudnessplayer/playback/LoudnessPlaybackService.kt app/src/main/java/com/wzl/loudnessplayer/ui/LoudnessPlayerApp.kt app/src/test/java/com/wzl/loudnessplayer/data/LibraryOrganizerTest.kt app/src/test/java/com/wzl/loudnessplayer/playback/PlaybackScopeTest.kt
git commit -m "feat: show queue order and smarter grouping"
```

### Task 6: Publish v1.5.1 safely

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `README.md`
- Modify: `.github/workflows/android-ci.yml` only if a verified new FFmpeg marker is needed

**Interfaces:**
- Produces a signed `app-release.apk` with package version `1.5.1`.

- [ ] **Step 1: Write a failing release value check**

```powershell
Select-String -Path app/build.gradle.kts -Pattern 'versionCode = 8','versionName = "1.5.1"'
```

Expected: initially no v1.5.1 match.

- [ ] **Step 2: Set release values and single canonical release record**

```kotlin
versionCode = 8
versionName = "1.5.1"
```

Add one README v1.5.1 section covering playback priority, one-click analysis controls, failed recovery confirmation, selection behavior, queue preview, metadata editing, lock-screen media card, smart artist grouping and back-to-top. Do not create a second public changelog file.

- [ ] **Step 3: Run full build and inspect signing**

Run: `./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease`

Expected: BUILD SUCCESSFUL.

Run: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`

Expected: certificate SHA-256 is `3F:4E:BB:71:22:1A:98:81:6C:10:3F:21:38:32:F7:4E:F1:A0:A2:C0:CD:66:4E:4F:06:A6:D4:48:63:A5:26:A0`.

- [ ] **Step 4: Publish through GitHub Actions and Releases**

```bash
git add app/build.gradle.kts README.md .github/workflows/android-ci.yml
git commit -m "chore: release v1.5.1"
git push origin agent/v1-5-1-library-control
```

Open a PR, require successful Android CI, merge to `main`, download the signed release artifact, record its SHA-256 and create GitHub Release `v1.5.1` with `app-release.apk`.

## Self-review

- Task 1 supplies persistent failure/success state and display-only metadata editing.
- Task 2 supplies Start/Stop behavior, no re-analysis of successes and playback-first cancellation/resume.
- Task 3 supplies failed filtering and confirmation-gated FLAC conversion/deletion.
- Task 4 supplies long-press selection, app-only removal and personal-folder movement.
- Task 5 supplies failed view, artist grouping, queue preview, lock-screen synchronization and back-to-top.
- Task 6 supplies exact versioning, testing, signing and Release publication.
