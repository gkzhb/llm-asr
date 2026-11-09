#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
mkdir -p "$root/.work/build" "$root/.work/model-review-fixes"
timeout 30 python3 tests/prepare_model_configs_test.py
timeout 30 python3 tests/android_test_fixture_inputs_test.py
classes=$(mktemp -d "$root/.work/build/minimal-apk-tests-XXXXXX")
cleanup_classes() {
  # A supervisor lease remains while a compiler can read this dependency tree.
  python3 -B tests/mixed_compiler_lifecycle.py safe-remove "$classes" || {
    echo "FATAL mixed exit unconfirmed; retained dependency classes: $classes" >&2
    return 1
  }
}
trap cleanup_classes EXIT
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
  model.FixedModelPackage model.ModelPackagePolicy model.ModelDeployment model.ModelCatalog model.ModelZipArchive model.ModelZipSource
  asr.NativeResponse
  transcription.ResultFiles
  audio.RecordingControl audio.WaveInput audio.AsrText audio.PcmWave
  ime.ImeFieldPolicy ime.ImeSession ime.ImeController
  model.ModelReadiness model.ModelVerification model.ModelResidency
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
  if [ ! -f "$path" ]; then path="${path%.java}.kt"; fi
  [ -f "$path" ] || { echo "FATAL host source missing: $path" >&2; exit 2; }
  if [ -n "${seen[$cls]+set}" ]; then
    echo "FATAL host duplicate class $cls in $path (already in ${seen[$cls]})" >&2
    exit 2
  fi
  seen[$cls]="$path"
  sources+=( "$path" )
done
bash scripts/compile-jvm-sources.sh "$classes" "" "${sources[@]}" \
  tests/KotlinMigrationTest.java \
  tests/RuntimeLogCoreTest.kt \
  tests/RuntimeLogWorkerErrorTest.kt \
  tests/RuntimeLogStoreErrorTest.kt \
  tests/InferenceAdapterTest.kt \
  tests/LogExportTest.kt \
  tests/TextExportTest.kt \
  tests/ModelNotificationTest.kt \
  tests/TextExportFatalTest.kt \
  tests/TextExportSafetyTest.kt \
  tests/MinimalApkTest.kt \
  tests/RecordingRaceTest.kt \
  tests/ResultFilesTest.kt \
  tests/NativeResponseTest.kt \
  tests/ModelRepositoryTest.kt \
  tests/ModelZipImportTest.kt \
  tests/ModelPrivateRootTest.kt \
  tests/PartRecoveryTest.java \
  tests/RequestRunnerTest.kt \
  tests/TaskCoordinatorTest.kt \
  tests/AdmissionBoundaryTest.kt \
  tests/ImeSessionTest.kt \
  tests/ModelReadinessTest.kt \
  tests/ModelOperationControlTest.kt \
  tests/ModelRepositoryCancelTest.kt \
  tests/ModelRepositoryDeleteTest.kt \
  tests/ModelManagementControllerTest.kt \
  tests/ModelAndroidHelpersTest.kt \
  tests/ModelReviewFixTest.kt \
  tests/ModelProviderBoundaryTest.kt
run() { timeout 45 java -cp "$classes:$KOTLIN_HOME/lib/kotlin-stdlib.jar" "$@"; }
run KotlinMigrationTest
# Independent fixed ZIP32/profile and typed SAF picker matrices; original gates/budgets unchanged.
zip_evidence=$(mktemp -d "$root/.work/build/model-zip-XXXXXX")
timeout 6600 python3 -B tests/model_zip_import_contract_test.py --evidence-dir "$zip_evidence/ordinary"
timeout 6600 python3 -O tests/model_zip_import_contract_test.py --evidence-dir "$zip_evidence/optimized"
timeout 1800 python3 -B tests/model_zip_picker_test.py --evidence-dir "$zip_evidence/picker-ordinary"
timeout 1800 python3 -O tests/model_zip_picker_test.py --evidence-dir "$zip_evidence/picker-optimized"
# Controlled custom variants: production policy/catalog and pre-load graph profile, no ASR/model read.
timeout 240 python3 -B tests/model_variants_contract_test.py --mutants --evidence-dir "$zip_evidence/variants-ordinary"
timeout 240 python3 -O tests/model_variants_contract_test.py --mutants --evidence-dir "$zip_evidence/variants-optimized"
timeout 240 python3 -B tests/native_model_graph_policy_test.py --evidence-dir "$zip_evidence/graph-ordinary"
timeout 240 python3 -O tests/native_model_graph_policy_test.py --evidence-dir "$zip_evidence/graph-optimized"
# Selected reverse-direction production contracts, same fresh canonical host dependency.
k2j_evidence=$(mktemp -d "$root/.work/build/kotlin-to-java-XXXXXX")
timeout 4200 python3 -B tests/kotlin_to_java_contract_test.py --contracts "$classes" --evidence-dir "$k2j_evidence/ordinary"
timeout 4200 python3 -O tests/kotlin_to_java_contract_test.py --contracts "$classes" --evidence-dir "$k2j_evidence/optimized"
# The same checker rejects malformed discovery/source fixtures in both Python modes.
timeout 30 python3 -B tests/kotlin_test_slice_test.py --source
timeout 30 python3 -B tests/kotlin_test_slice_test.py --self-test
timeout 30 python3 -O tests/kotlin_test_slice_test.py --self-test
# New isolated production mutations retain the existing dependency lease until owned exit.
slice_evidence=$(mktemp -d "$root/.work/build/kotlin-test-slice-XXXXXX")
timeout 3000 python3 -B tests/kotlin_test_slice_test.py --contracts "$classes" --evidence-dir "$slice_evidence/ordinary"
timeout 3000 python3 -O tests/kotlin_test_slice_test.py --contracts "$classes" --evidence-dir "$slice_evidence/optimized"
# Independent new nineteen-body contracts: five owners x green/red/restored; old budgets unchanged.
timeout 5000 python3 -B tests/kotlin_test_slice_test.py --jvmnext-contracts "$classes" --evidence-dir "$slice_evidence/jvmnext-ordinary"
timeout 5000 python3 -O tests/kotlin_test_slice_test.py --jvmnext-contracts "$classes" --evidence-dir "$slice_evidence/jvmnext-optimized"
# T5 source and checker refusal contracts remain effective under ordinary and -O.
timeout 30 python3 -B tests/kotlin_testhost_migration_test.py --source
timeout 30 python3 -O tests/kotlin_testhost_migration_test.py --source
timeout 30 python3 -B tests/kotlin_testhost_migration_test.py --self-test
timeout 30 python3 -O tests/kotlin_testhost_migration_test.py --self-test
# Three real production mutants x green/red/restored; old route budgets unchanged.
timeout 3200 python3 -B tests/kotlin_testhost_migration_test.py --contracts "$classes" --evidence-dir "$slice_evidence/testhost-ordinary"
timeout 3200 python3 -O tests/kotlin_testhost_migration_test.py --contracts "$classes" --evidence-dir "$slice_evidence/testhost-optimized"
# Same external caller against original Java and maintained Kotlin; bounded real joins.
timeout 2800 python3 -B tests/kotlin_testhost_migration_test.py --faults "$classes" --evidence-dir "$slice_evidence/testhost-faults"
# Lifecycle faults call the real driver/wrapper; fake workers are not ABI evidence.
timeout 180 python3 tests/runtime_log_enum_cleanup_test.py
# Fresh isolated production enum copies: compile0 -> exact Java assertion1 -> restored0.
timeout 1440 python3 tests/runtime_log_enum_mutant_test.py
timeout 1440 python3 tests/runtime_log_enum_mutant_test.py --ports
timeout 1440 python3 tests/runtime_log_enum_mutant_test.py --phase
timeout 2700 python3 tests/runtime_log_enum_mutant_test.py --batch
# 11 fresh compile/run cases * (270s + 45s + bounded owned teardown) < 3900s.
timeout 3900 python3 -B tests/kotlin_next_migration_test.py
timeout 2700 python3 -B tests/kotlin_next_migration_test.py --compat-only
# Independent IME slice: 9 fresh compile/run routes; legacy budgets above unchanged.
timeout 3000 python3 -B tests/kotlin_next_migration_test.py --ime-return-only
# Fake-tool cleanup faults call the actual readiness driver, including Python -O.
timeout 30 python3 -B tests/ime_readiness_cleanup_test.py
timeout 30 python3 -O tests/ime_readiness_cleanup_test.py
# Independent readiness slice: seven fresh 270s compiles / 45s JVMs per Python mode.
ime_ready_evidence=$(mktemp -d "$root/.work/build/ime-model-ready-XXXXXX")
timeout 2400 python3 -B tests/kotlin_next_migration_test.py --ime-model-ready-only --evidence-dir "$ime_ready_evidence/ordinary"
timeout 2400 python3 -O tests/kotlin_next_migration_test.py --ime-model-ready-only --evidence-dir "$ime_ready_evidence/optimized"
# Independent first-use transaction mutants; original host/default/return/readiness budgets unchanged.
ime_first_evidence=$(mktemp -d "$root/.work/build/ime-first-use-XXXXXX")
timeout 2400 python3 -B tests/kotlin_next_migration_test.py --ime-first-use-only --evidence-dir "$ime_first_evidence/ordinary"
timeout 2400 python3 -O tests/kotlin_next_migration_test.py --ime-first-use-only --evidence-dir "$ime_first_evidence/optimized"
# Actual hardware-start R1/R2 arbitration: independent five-compilation route, legacy budgets unchanged.
ime_start_evidence=$(mktemp -d "$root/.work/build/ime-start-XXXXXX")
timeout 2400 python3 -B tests/ime_start_boundary_test.py --evidence-dir "$ime_start_evidence/ordinary"
timeout 2400 python3 -O tests/ime_start_boundary_test.py --evidence-dir "$ime_start_evidence/optimized"
# Independent verification: each mode <=9 mixed compile(180s), <=20 JVM(30s), unchanged legacy gates.
verification_evidence=$(mktemp -d .work/model-verification-contracts-XXXXXX)
timeout 2400 python3 -B tests/model_verification_contract_test.py --contracts "$classes" --evidence-dir "$verification_evidence/ordinary"
timeout 2400 python3 -O tests/model_verification_contract_test.py --contracts "$classes" --evidence-dir "$verification_evidence/optimized"
# Actual Android journal method/FD host contracts, independent fixed budgets (not Android probe).
android_proof_evidence=$(mktemp -d .work/android-proof-io-XXXXXX)
timeout 420 python3 -B tests/android_model_verification_io_test.py --contracts "$classes" --evidence-dir "$android_proof_evidence/ordinary"
timeout 420 python3 -O tests/android_model_verification_io_test.py --contracts "$classes" --evidence-dir "$android_proof_evidence/optimized"
# Added core faults stay in existing compiled production helpers; no old suite timeout changes.
timeout 600 python3 -B tests/model_verification_contract_test.py --corefault --contracts "$classes" --evidence-dir "$verification_evidence/corefault-ordinary"
timeout 600 python3 -O tests/model_verification_contract_test.py --corefault --contracts "$classes" --evidence-dir "$verification_evidence/corefault-optimized"
# Fifteen mixed compile routes (30 compiler commands) + fifteen Java runs + owned teardown < 7200s.
timeout 7200 python3 -B tests/kotlin_runtime_migration_test.py
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
run org.llmasr.minimal.diagnostics.RuntimeLogWorkerErrorTest
run org.llmasr.minimal.RuntimeLogStoreErrorTest
run org.llmasr.minimal.InferenceAdapterTest
run LogExportTest
run TextExportTest
run TextExportSafetyTest
run TextExportFatalTest
run ModelNotificationTest
timeout 180 python3 tests/foreground_recorder_test.py
timeout 180 python3 tests/activity_window_test.py
timeout 900 python3 -B tests/logs_activity_picker_test.py
timeout 15 python3 tests/activity_window_source_test.py
timeout 15 python3 tests/manifest_permission_test.py
timeout 15 python3 -O tests/manifest_permission_test.py
timeout 15 python3 tests/model_android_source_test.py
timeout 15 python3 tests/text_export_source_test.py
timeout 15 python3 tests/architecture_boundary_test.py
timeout 15 python3 tests/app_report_source_test.py
timeout 15 python3 tests/jni_symbol_checker_test.py

for issue in history terminal window boundary deletion liveDeletion planning publication; do run ModelReviewFixTest "$issue"; done
run ModelProviderBoundaryTest
# 3 * (15s compile + 5s Java + two <=3.25s owned teardown budgets) < 90s.
# TERM is handled by the local supervisor; no host cleanup before owned exit.
MODEL_REVIEW_CLASSES="$classes" timeout 90 python3 tests/model_review_mutation_test.py
MIXED_LIFECYCLE_CLASSES="$classes" timeout 180 python3 -B tests/mixed_compiler_lifecycle_test.py
MIXED_LIFECYCLE_CLASSES="$classes" timeout 180 python3 -B tests/mixed_compiler_lifecycle_test.py --cleanup-only
MIXED_LIFECYCLE_CLASSES="$classes" timeout 180 python3 -B tests/mixed_compiler_lifecycle_test.py --lock-only
timeout 90 python3 tests/apk_report_binding_test.py
