#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MX_DIR="$ROOT_DIR/MX_APP"

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}"
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

ensure_sdkmanager() {
  local sdkmanager_bin="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  if [[ -x "$sdkmanager_bin" ]]; then
    return 0
  fi

  echo "[INFO] Android cmdline-tools not found under $ANDROID_SDK_ROOT, bootstrapping..."
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
  local zip="/tmp/android-cmdline-tools.zip"
  curl -fsSL -o "$zip" "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
  rm -rf /tmp/android-cmdline-tools-extract
  unzip -q "$zip" -d /tmp/android-cmdline-tools-extract
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  cp -r /tmp/android-cmdline-tools-extract/cmdline-tools/* "$ANDROID_SDK_ROOT/cmdline-tools/latest/"
}

ensure_android_packages() {
  ensure_sdkmanager
  local sdkmanager_bin="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
  yes | "$sdkmanager_bin" --licenses >/dev/null || true
  "$sdkmanager_bin" \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;35.0.0" \
    "ndk;26.3.11579264"
}


ensure_android_packages

# MX app compiles Rust for Android; ensure NDK is visible.
if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  if [[ -d "$ANDROID_SDK_ROOT/ndk" ]]; then
    ANDROID_NDK_HOME="$(find "$ANDROID_SDK_ROOT/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
  fi
fi
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME is required (or install NDK under ANDROID_SDK_ROOT/ndk)}"
export ANDROID_NDK_HOME

LOCAL_PROPERTIES="$MX_DIR/local.properties"
if [[ ! -f "$LOCAL_PROPERTIES" ]] || ! grep -q '^sdk.dir=' "$LOCAL_PROPERTIES"; then
  echo "sdk.dir=$ANDROID_SDK_ROOT" > "$LOCAL_PROPERTIES"
fi

if ! command -v rustup >/dev/null 2>&1; then
  echo "[ERROR] rustup not found; MX_APP requires Rust targets." >&2
  exit 1
fi

echo "[INFO] Ensuring Rust Android targets"
rustup target add aarch64-linux-android x86_64-linux-android

GRADLE_BIN="${GRADLE_BIN:-$MX_DIR/gradlew}"
if [[ "$GRADLE_BIN" == "$MX_DIR/gradlew" ]]; then
  chmod +x "$MX_DIR/gradlew"
fi

echo "[INFO] Building MX_APP debug APK"
(
  cd "$MX_DIR"
  "$GRADLE_BIN" clean :app:assembleDebug --no-daemon -Dkotlin.daemon.enabled=false
)

APK_OUT="$MX_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK_OUT" ]]; then
  echo "[ERROR] Missing MX debug APK artifact: $APK_OUT" >&2
  exit 1
fi

echo "[OK] Built MX artifact:"
echo "- $APK_OUT"
