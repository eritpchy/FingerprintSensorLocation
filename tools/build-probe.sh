#!/usr/bin/env bash
set -euo pipefail
if [[ $(uname -s) != Linux ]]; then echo 'Build on the remote Linux builder' >&2; exit 1; fi
root=$(cd "$(dirname "$0")/.." && pwd)
sdk=${ANDROID_HOME:?Set ANDROID_HOME}
out="$root/.work/probe"
mkdir -p "$out/classes" "$out/dex"
unzip -p "$root/android/build/outputs/aar/android-release.aar" classes.jar > "$out/android.jar"
core="$root/core/build/libs/core-0.1.0.jar"
platform="$sdk/platforms/android-37.2/android.jar"
javac -source 8 -target 8 -classpath "$platform:$core:$out/android.jar" -d "$out/classes" "$root/probe/src/main/java/io/github/eritpchy/fingerprintlocation/probe/Main.java"
jar cf "$out/probe-classes.jar" -C "$out/classes" .
"$sdk/build-tools/37.0.0/d8" --release --min-api 23 --lib "$platform" --output "$out/dex" "$core" "$out/android.jar" "$out/probe-classes.jar"
jar cf "$out/fingerprint-location-probe.jar" -C "$out/dex" classes.dex
echo "$out/fingerprint-location-probe.jar"
