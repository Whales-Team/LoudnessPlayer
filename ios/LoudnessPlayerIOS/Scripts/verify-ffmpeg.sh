#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRAMEWORK="$ROOT/Vendor/Artifacts/FFmpegAudio.xcframework"
VERSION="$ROOT/Vendor/Artifacts/FFmpegAudio.version"
grep -Fx 'n8.0.3' "$VERSION"
/usr/libexec/PlistBuddy -c 'Print :AvailableLibraries' "$FRAMEWORK/Info.plist" | grep 'ios-arm64'
/usr/libexec/PlistBuddy -c 'Print :AvailableLibraries' "$FRAMEWORK/Info.plist" | grep 'ios-arm64_x86_64-simulator'
lipo -info "$FRAMEWORK/ios-arm64/libFFmpegAudio.a" | grep 'arm64'
lipo -info "$FRAMEWORK/ios-arm64_x86_64-simulator/libFFmpegAudio.a" | grep 'x86_64' | grep 'arm64'
if grep -E '^[[:space:]]*--enable-(gpl|nonfree)([[:space:]]|$)' "$ROOT/Scripts/build-ffmpeg-xcframework.sh"; then
  echo 'GPL/nonfree configure flag found' >&2; exit 1
fi
echo 'FFmpeg XCFramework verification passed'
