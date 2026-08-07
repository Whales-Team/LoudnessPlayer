# LoudnessPlayer v1.5.1 Library Control Design

## Goal

Release v1.5.1 with playback-priority loudness analysis, visible failed-analysis management, opt-in FLAC recovery, multi-select library management, queue-order previews, dependable system lock-screen media controls, and an optional same-artist smart grouping rule.

## Decisions

- Playback always has priority. When the player starts or resumes, active analysis jobs are cancelled and their unfinished tracks return to the pending queue. Analysis resumes only after playback is paused or stopped.
- A failed analysis never pauses, seeks, changes volume, replaces the player queue, or shows a modal dialog. It records a short failure reason and can show one non-blocking snackbar after the current song remains unaffected.
- “Start analysis” processes only tracks with no successful loudness result: unanalysed and failed tracks. Successful results are retained and skipped.
- “Stop analysis” cancels active FFmpeg/Android decoder work and leaves completed results intact. Pending and interrupted tracks remain eligible for a later start.
- The failed-analysis filter is a library view that contains only tracks with a stored analysis failure.
- FLAC recovery is optional and deliberate: the user selects failed tracks, chooses conversion, confirms the exact original files that may be deleted, then each source is converted, imported as FLAC, and re-analysed. The original is deleted only after both conversion and new loudness analysis succeed.
- Conversion output is written beside the source when Android grants write access to that location; otherwise the user chooses a writable target folder. No original is removed without a final confirmation.
- Long press enters multi-select mode. Batch move means add/remove from app-created personal folders; batch delete means remove only the app library records and folder memberships, never delete the phone audio files.
- The in-app playback bar shows an ordered three-item queue preview: previous, current and next. The Android system media card remains the lock-screen control surface and provides previous, play/pause and next actions.
- Smart grouping keeps its current shared-title-field behavior and gains a persisted option to group tracks by the same known artist. This is an app display grouping only, not a filesystem directory operation.

## Data model

`AudioTrack` gains `analysisStatus` and `analysisFailureMessage`.

- `PENDING`: never analysed or interrupted before producing a result.
- `ANALYZING`: currently being analysed; this is transient and not persisted.
- `SUCCESS`: has finite LUFS and peak values.
- `FAILED`: last attempt failed; the persisted message is truncated to a safe short diagnostic.

Existing tracks migrate naturally: a track with `loudnessLufs != null` becomes `SUCCESS`; every other track starts as `PENDING`.

`PlayerPreferences` gains a persisted `groupSameArtistInSmartView` Boolean, defaulting to `false` so existing grouping remains unchanged.

## Analysis scheduling

An `AnalysisCoordinator` owns pending IDs, active FFmpeg sessions and pause/cancel state. It permits two jobs only while playback is idle. A player listener pauses the coordinator before audio playback begins. A callback after pause/stop resumes pending work when the user has not manually pressed Stop.

Each task uses the existing platform decoder first when supported, then the internal FFmpeg EBU R128 fallback. APE/WMA remain FFmpeg-first. Decoder errors are transformed to a concise reason; failures update only their row and filter state.

## FLAC recovery flow

1. User opens the failed-analysis filter and selects one or more tracks.
2. User chooses “convert to FLAC and retry”. The app requests a writable destination folder if it cannot write beside every source.
3. The app displays selected source names and an explicit statement that originals are deleted only after individual conversion and verification success.
4. FFmpeg converts each selected source to `.flac` in the chosen writable location.
5. The app imports and analyses each FLAC output.
6. For each success, the user-confirmed original is deleted through the granted MediaStore/SAF document permission; conversion or verification failure leaves the original untouched and reports the item in the failed list.

## User interface

- Add a compact analysis control row to the drawer/library header: Start, Stop and Failed (with count).
- Failed tracks have a status badge and an expandable one-line reason; normal playback actions remain enabled.
- Long press a `TrackRow` to enter a selection toolbar showing count, “move to folder”, “remove from library”, “convert selected failures”, and Cancel.
- Add previous/current/next queue previews to the bottom player surface, based on the active global or selected personal-folder queue and honoring shuffle order.
- Keep the v1.5.0 MediaSessionService and improve its metadata/queue synchronization so the standard Android lock-screen media card reliably stays available while playback is active. No custom overlay is displayed over the lock screen.
- Add a Smart View toggle labelled “同歌手归为一组”; unknown artists are not grouped together.

## Verification

- Unit tests cover status migration, start/stop eligibility, playback pause/resume rules, failure filtering, original-delete eligibility, smart artist grouping and queue preview resolution.
- FFmpeg command tests cover FLAC output naming and conversion arguments without writing phone files.
- Android CI runs unit tests, lint, debug assembly and signed release assembly.
- Manual device checks use a supported APE file, a deliberately unreadable/corrupt source, a selected personal folder, screen lock controls, and a cancelled conversion; confirm no original is deleted before successful FLAC analysis.

## Release

- Set `versionCode` to `8` and `versionName` to `1.5.1`.
- Keep the fixed v1.4.0+ signing key so v1.5.0 users can install the update over the existing app while retaining application data.
- Add one canonical v1.5.1 section to `README.md`; do not create a second public changelog file.
