#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
classes="$root/.work/build/minimal-apk-tests"
mkdir -p "$classes"
javac -encoding UTF-8 -d "$classes" android/app/src/org/llmasr/minimal/{WaveInput,AsrText,PcmWave,RecordingControl}.java tests/MinimalApkTest.java tests/RecordingRaceTest.java
java -cp "$classes" MinimalApkTest bench/audio/zh-original.wav
java -cp "$classes" RecordingRaceTest
