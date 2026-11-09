#!/usr/bin/env bash
# Build the debug APK using a Gradle that is already installed.
# This script never installs or downloads a toolchain and never touches the SDK.
set -euo pipefail

cd "$(dirname "$0")/.."

if ! command -v gradle >/dev/null 2>&1; then
    echo "gradle is not on PATH. Install Gradle 8.9+ yourself; this script installs nothing." >&2
    exit 1
fi

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]; then
    echo "ANDROID_HOME or ANDROID_SDK_ROOT must point at an existing Android SDK." >&2
    exit 1
fi

gradle :app:assembleDebug --no-daemon "$@"

apk="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$apk" ]; then
    echo "APK: $apk"
else
    echo "Build finished but $apk is missing." >&2
    exit 1
fi
