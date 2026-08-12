#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/Fixtures/Runtime"
mkdir -p "$DEST"

download() {
  local url="$1" output="$2"
  curl --fail --location --retry 3 "$url" --output "$DEST/$output"
  test -s "$DEST/$output"
}

# These are small public FFmpeg FATE conformance samples used only during CI.
download "https://fate-suite.ffmpeg.org/lossless-audio/luckynight-mac388-c2000.ape" "luckynight-mac388-c2000.ape"
download "https://fate-suite.ffmpeg.org/lossless-audio/Mega_Weird_Audio_Test_24bit.wma" "Mega_Weird_Audio_Test_24bit.wma"
download "https://fate-suite.ffmpeg.org/ogg-vorbis/chained-meta.ogg" "chained-meta.ogg"
download "https://fate-suite.ffmpeg.org/audiomatch/tones_opus_48000_stereo.opus" "tones_opus_48000_stereo.opus"
