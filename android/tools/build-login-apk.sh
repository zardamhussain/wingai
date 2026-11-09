#!/usr/bin/env bash
# Build the existing ARM64 keyboard plus optional Firebase login, even when the checkout
# path contains spaces. All build outputs stay in this checkout; only a temporary symlink
# is created outside it for GNU Make, then removed. Requires JDK 17 and the configured NDK.
set -euo pipefail
android_root="$(cd "$(dirname "$0")/.." && pwd)"
: "${ANDROID_HOME:?Set ANDROID_HOME to an Android SDK with NDK 27.1.12297006}"
ndk="$ANDROID_HOME/ndk/27.1.12297006/ndk-build"
alias_root="$(mktemp -d "${TMPDIR:-/tmp}/wingai-ndk.XXXXXX")"
trap 'rm -f "$alias_root/android"; rmdir "$alias_root"' EXIT
ln -s "$android_root" "$alias_root/android"
short="$alias_root/android"
mkdir -p "$android_root/app/build/wingai-native"
"$ndk" -j2 NDK_PROJECT_PATH=null \
    APP_BUILD_SCRIPT="$short/app/src/main/jni/Android.mk" \
    NDK_APPLICATION_MK="$short/app/src/main/jni/Application.mk" \
    APP_ABI=arm64-v8a APP_PLATFORM=android-26 NDK_DEBUG=1 \
    NDK_OUT="$short/app/build/wingai-native/obj" \
    NDK_LIBS_OUT="$short/app/build/wingai-native/lib"
cd "$android_root"
if [ "$#" -eq 0 ]; then set -- :app:assembleDebug; fi
./gradlew "-PwingaiNativeLibs=$android_root/app/build/wingai-native/lib" \
    --no-daemon --no-configuration-cache --max-workers=2 \
    -Dorg.gradle.jvmargs=-Xmx2048m --console=plain "$@"
