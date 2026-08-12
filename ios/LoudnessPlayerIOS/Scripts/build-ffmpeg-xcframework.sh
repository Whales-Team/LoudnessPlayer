#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_ROOT="$ROOT/build/ffmpeg-source"
BUILD_ROOT="$ROOT/build/ffmpeg-build"
ARTIFACT_ROOT="$ROOT/Vendor/Artifacts"
TAG="n8.0.3"

mkdir -p "$ROOT/build" "$ARTIFACT_ROOT"
if [[ ! -d "$SOURCE_ROOT/.git" ]]; then
  git clone --depth 1 --branch "$TAG" https://github.com/FFmpeg/FFmpeg.git "$SOURCE_ROOT"
fi
resolved="$(git -C "$SOURCE_ROOT" rev-parse HEAD)"

common=(
  --target-os=darwin --enable-cross-compile --enable-pic --enable-static --disable-shared
  --disable-programs --disable-doc --disable-debug --disable-network --disable-autodetect
  --disable-everything --enable-avcodec --enable-avformat --enable-avutil --enable-swresample --enable-avfilter
  --enable-protocol=file
  --enable-demuxer=ape,asf,ogg,matroska,wav,mov,mp3,flac,aac
  --enable-decoder=ape,wmav1,wmav2,wmapro,wmalossless,vorbis,opus,flac,aac,mp3,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,alac
  --enable-parser=vorbis,opus,flac,aac,mpegaudio
  --enable-encoder=flac --enable-muxer=flac
  --enable-filter=ebur128,aformat,aresample,anull
)

build_slice() {
  local name="$1" sdk="$2" arch="$3" min_flag="$4"
  local prefix="$BUILD_ROOT/$name/install" work="$BUILD_ROOT/$name/work"
  rm -rf "$work" "$prefix"; mkdir -p "$work" "$prefix"
  pushd "$work" >/dev/null
  extra=(--disable-x86asm); [[ "$arch" != "x86_64" ]] && extra=(--enable-asm)
  "$SOURCE_ROOT/configure" "${common[@]}" "${extra[@]}" --arch="$arch" \
    --cc="$(xcrun --sdk "$sdk" --find clang)" --ar="$(xcrun --sdk "$sdk" --find ar)" \
    --ranlib="$(xcrun --sdk "$sdk" --find ranlib)" --sysroot="$(xcrun --sdk "$sdk" --show-sdk-path)" \
    --prefix="$prefix" --extra-cflags="-arch $arch $min_flag -fembed-bitcode-marker" \
    --extra-ldflags="-arch $arch $min_flag"
  make -j"$(sysctl -n hw.logicalcpu)"; make install
  libtool -static -o "$prefix/lib/libFFmpegAudio.a" \
    "$prefix/lib/libavformat.a" "$prefix/lib/libavcodec.a" "$prefix/lib/libavfilter.a" \
    "$prefix/lib/libswresample.a" "$prefix/lib/libavutil.a"
  popd >/dev/null
}

build_slice ios-arm64 iphoneos arm64 "-miphoneos-version-min=16.0"
build_slice sim-arm64 iphonesimulator arm64 "-mios-simulator-version-min=16.0"
build_slice sim-x86_64 iphonesimulator x86_64 "-mios-simulator-version-min=16.0"

SIM="$BUILD_ROOT/simulator-universal"
rm -rf "$SIM"; mkdir -p "$SIM/lib" "$SIM/include"
cp -R "$BUILD_ROOT/sim-arm64/install/include/." "$SIM/include/"
lipo -create "$BUILD_ROOT/sim-arm64/install/lib/libFFmpegAudio.a" \
  "$BUILD_ROOT/sim-x86_64/install/lib/libFFmpegAudio.a" -output "$SIM/lib/libFFmpegAudio.a"

rm -rf "$ARTIFACT_ROOT/FFmpegAudio.xcframework"
xcodebuild -create-xcframework \
  -library "$BUILD_ROOT/ios-arm64/install/lib/libFFmpegAudio.a" -headers "$BUILD_ROOT/ios-arm64/install/include" \
  -library "$SIM/lib/libFFmpegAudio.a" -headers "$SIM/include" \
  -output "$ARTIFACT_ROOT/FFmpegAudio.xcframework"
printf '%s\n%s\n' "$TAG" "$resolved" > "$ARTIFACT_ROOT/FFmpegAudio.version"
