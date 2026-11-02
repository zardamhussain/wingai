#!/usr/bin/env bash
# Run only on a Mac with full Xcode. No Apple account credentials are stored by this script.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"
if [[ ! -x "$DEVELOPER_DIR/usr/bin/xcodebuild" ]]; then
  echo 'Full Xcode is required. Install Xcode and set DEVELOPER_DIR if it is not in /Applications/Xcode.app.' >&2
  exit 2
fi
MODE="${1:-build}"
XCODEBUILD="$DEVELOPER_DIR/usr/bin/xcodebuild"
COMMON=(-project "$ROOT/WingAI.xcodeproj" -scheme WingAI -derivedDataPath "$ROOT/artifacts/DerivedData")
case "$MODE" in
  build)
    "$XCODEBUILD" "${COMMON[@]}" -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
    ;;
  test)
    : "${IOS_TEST_DESTINATION:?Set IOS_TEST_DESTINATION to an installed simulator, e.g. platform=iOS Simulator,name=iPhone 16 Pro}"
    "$XCODEBUILD" "${COMMON[@]}" -configuration Debug -destination "$IOS_TEST_DESTINATION" CODE_SIGNING_ALLOWED=NO test
    ;;
  archive)
    : "${DEVELOPMENT_TEAM:?Set DEVELOPMENT_TEAM to your own Apple team ID and configure unique bundle identifiers in Xcode first}"
    "$XCODEBUILD" "${COMMON[@]}" -configuration Release -destination 'generic/platform=iOS' \
      -archivePath "$ROOT/artifacts/archives/WingAI.xcarchive" "DEVELOPMENT_TEAM=$DEVELOPMENT_TEAM" archive
    echo 'Archive created. Export/distribute with your authorized signing profile in Xcode Organizer.'
    ;;
  *) echo 'Usage: bash tools/build_ios.sh [build|test|archive]' >&2; exit 2 ;;
esac
