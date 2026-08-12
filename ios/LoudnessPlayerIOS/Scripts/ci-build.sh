#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p build

xcodegen generate --spec project.yml

set -o pipefail
xcodebuild test \
  -project LoudnessPlayerIOS.xcodeproj \
  -scheme LoudnessPlayer \
  -destination "${IOS_TEST_DESTINATION:-platform=iOS Simulator,name=LoudnessPlayer CI}" \
  CODE_SIGNING_ALLOWED=NO \
  | tee build/test.log

xcodebuild build \
  -project LoudnessPlayerIOS.xcodeproj \
  -scheme LoudnessPlayer \
  -configuration Release \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  | tee build/app.log
