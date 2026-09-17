#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
classes=$(mktemp -d "$root/.work/build/minimal-apk-tests-XXXXXX")
trap 'rm -rf "$classes"' EXIT
# Pure Java production components only; no Android stub runtime. The
# recursive compile would pull in classes that depend on android.* / org.json
# and fail the host-only build, so this list is the canonical host-safe
# subset. Paths are resolved uniquely via the migration map (see
# scripts/compile-android-java.sh for the recursive variant used by the
# full Android javac check).
sources=()
host_classes=(
  task.TaskKind task.RequestContext task.TaskCoordinator task.RequestRunner
  transcription.AppRequestPolicy transcription.AppState transcription.ResultState
  model.FileSafety
  model.ModelEntry model.ModelManifest model.ModelReports model.ModelSource model.ModelRepository
  asr.NativeResponse
  transcription.ResultFiles
  audio.RecordingControl audio.WaveInput audio.AsrText audio.PcmWave
  ime.ImeFieldPolicy ime.ImeSession ime.ImeController
  model.ModelReadiness
  modelmanagement.ModelOperationControl modelmanagement.ModelManagementState modelmanagement.ModelManagementController
  model.ModelAccess modelmanagement.ModelPickerTickets modelmanagement.ModelPageSession modelmanagement.ModelUiText
  model.ModelProviderBoundary
  diagnostics.RuntimeLogEventKind diagnostics.RuntimeLogSource diagnostics.RuntimeLogEvent
  diagnostics.RuntimeLogClock diagnostics.RuntimeLogSink diagnostics.RuntimeLogStore
  diagnostics.RuntimeLogPersistence diagnostics.RuntimeLogCodec diagnostics.RuntimeLogWorker
  asr.InferencePhase asr.InferencePhaseListener asr.LoggingInferencePhaseListener
  asr.NativeTranscription asr.JniNativeTranscription asr.InferenceAdapter
  diagnostics.LogExportController diagnostics.LogExportPage
  transcription.TextExportController transcription.TextExportPage
  diagnostics.RuntimeLogText
)
declare -A seen=()
for fq in "${host_classes[@]}"; do
  sub=${fq%%.*}; cls=${fq##*.}
  path="android/app/src/org/llmasr/minimal/${sub}/${cls}.java"
  [ -f "$path" ] || { echo "FATAL host source missing: $path" >&2; exit 2; }
  if [ -n "${seen[$cls]+set}" ]; then
    echo "FATAL host duplicate class $cls in $path (already in ${seen[$cls]})" >&2
    exit 2
  fi
  seen[$cls]="$path"
  sources+=( "$path" )
done
timeout 60 javac --release 8 -encoding UTF-8 -d "$classes" "${sources[@]}" \
  tests/RuntimeLogCoreTest.java \
  tests/RuntimeLogStoreErrorTest.java \
  tests/InferenceAdapterTest.java \
  tests/LogExportTest.java \
  tests/TextExportTest.java \
  tests/ModelNotificationTest.java \
  tests/TextExportFatalTest.java \
  tests/TextExportSafetyTest.java \
  tests/MinimalApkTest.java \
  tests/RecordingRaceTest.java \
  tests/ResultFilesTest.java \
  tests/NativeResponseTest.java \
  tests/ModelRepositoryTest.java \
  tests/ModelPrivateRootTest.java \
  tests/PartRecoveryTest.java \
  tests/RequestRunnerTest.java \
  tests/TaskCoordinatorTest.java \
  tests/AdmissionBoundaryTest.java \
  tests/ImeSessionTest.java \
  tests/ModelReadinessTest.java \
  tests/ModelOperationControlTest.java \
  tests/ModelRepositoryCancelTest.java \
  tests/ModelRepositoryDeleteTest.java \
  tests/ModelManagementControllerTest.java \
  tests/ModelAndroidHelpersTest.java \
  tests/ModelReviewFixTest.java \
  tests/ModelProviderBoundaryTest.java
run() { timeout 45 java -cp "$classes" "$@"; }
run MinimalApkTest bench/audio/zh-original.wav
run RecordingRaceTest
run ResultFilesTest
run NativeResponseTest
run ModelRepositoryTest
run ModelPrivateRootTest
run PartRecoveryTest
run RequestRunnerTest
run TaskCoordinatorTest
run AdmissionBoundaryTest
run ImeSessionTest
run ModelReadinessTest
run ModelOperationControlTest
run ModelRepositoryCancelTest
run ModelRepositoryDeleteTest
run ModelManagementControllerTest
run ModelAndroidHelpersTest
run org.llmasr.minimal.diagnostics.RuntimeLogCoreTest
run org.llmasr.minimal.RuntimeLogStoreErrorTest
run org.llmasr.minimal.InferenceAdapterTest
run LogExportTest
run TextExportTest
run TextExportSafetyTest
run TextExportFatalTest
run ModelNotificationTest
timeout 15 python3 tests/model_android_source_test.py
timeout 15 python3 tests/text_export_source_test.py
timeout 15 python3 tests/architecture_boundary_test.py
timeout 15 python3 tests/app_report_source_test.py
timeout 15 python3 tests/jni_symbol_checker_test.py

for issue in history terminal window boundary deletion liveDeletion planning publication; do run ModelReviewFixTest "$issue"; done
run ModelProviderBoundaryTest
MODEL_REVIEW_CLASSES="$classes" timeout 90 python3 tests/model_review_mutation_test.py
timeout 90 python3 tests/apk_report_binding_test.py
