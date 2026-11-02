#!/usr/bin/env bash
# Build the workspace's current Android source and publish a debug APK.
# Stage outside paths with spaces: Android NDK/GNU make cannot reliably build there.
# No backend deployment, emulator installation, or device data changes are performed.
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE_ROOT="${WINGAI_BUILD_CACHE:-$HOME/Library/Caches/wingai-android-build-20261101}"
export JAVA_HOME="${JAVA_HOME:-$CACHE_ROOT/jdk-17.0.21+9/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$CACHE_ROOT/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$CACHE_ROOT/gradle}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$CACHE_ROOT/android-user}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
unset KEYSTORE_PATH KEYSTORE_PASSWORD KEY_PASSWORD KEY_ALIAS

BUILD_ROOT="$CACHE_ROOT/source/android"
OUTPUT="$PROJECT_ROOT/artifacts/apk"
mkdir -p "$BUILD_ROOT" "$OUTPUT" "$ANDROID_USER_HOME"
# The staging directory is disposable; exclude generated outputs and local machine config.
rsync -a --delete --exclude='.gradle/' --exclude='build/' --exclude='.cxx/' --exclude='local.properties' \
    "$PROJECT_ROOT/android/" "$BUILD_ROOT/"
cd "$BUILD_ROOT"
bash gradlew --no-daemon --no-configuration-cache --max-workers=2 \
    '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8' \
    :app:assembleDebug "$@"
APK="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
if [[ -z "$APK" ]]; then echo 'Build returned without a debug APK' >&2; exit 1; fi
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$APK"
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -c -P 16 -v 4 "$APK"
cp "$APK" "$OUTPUT/WingAI-modern-ui-debug-arm64.apk"
cd "$OUTPUT"
shasum -a 256 WingAI-modern-ui-debug-arm64.apk > WingAI-modern-ui-debug-arm64.apk.sha256
printf '\nAPK ready: %s/WingAI-modern-ui-debug-arm64.apk\n' "$OUTPUT"
