plugins {
    id("com.android.application") version "8.6.1" apply false
    id("com.android.library") version "8.6.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

extra["p1LintConsumerTypes"] = listOf(
    com.android.build.gradle.internal.lint.AndroidLintTask::class.java,
    com.android.build.gradle.internal.lint.AndroidLintAnalysisTask::class.java)
apply(from = "gradle/verify-lint-tools.gradle")

// Exact source ownership, shared by real module compilers; physical paths stay
// stable during this first extraction. No source is compiled by two modules.
extra["appSources"] = listOf("AppGraph.kt", "AsrImeService.kt", "MainActivity.java",
    "LogsActivity.kt", "ModelManagementActivity.java", "platform/ActivityWindow.kt",
    "transcription/AsrOperation.kt", "transcription/AppReportWriter.java")
    .map { "org/llmasr/minimal/$it" }
extra["runtimeSources"] = listOf("asr/JniNativeTranscription.java", "audio/ForegroundRecorder.kt",
    "model/SafModelSource.kt", "model/AndroidModelVerificationIo.kt", "platform/OperationContext.kt", "ime/ImeBackend.kt")
    .map { "org/llmasr/minimal/$it" }

// javac otherwise implicitly recompiles excluded .java files from a shared
// source root. Dependency types must come only from declared module classpaths.
subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.sourcepath = files()
        // The next host driver compiles this fixture explicitly on isolated fake
        // platform inputs; it must not enter production or real Android/JUnit tasks.
        val hostOnlyFixture = rootProject.file("tests/fixtures/kotlin-next/NextAppContractTest.java").canonicalFile
        val runtimeHostFixture = rootProject.file("tests/fixtures/kotlin-runtime/RuntimeContractTest.java").canonicalFile
        exclude { element -> element.file.canonicalFile == hostOnlyFixture || element.file.canonicalFile == runtimeHostFixture }
    }
}

// Plugin dependencies are also locked; checksums cover plugins and transitive jars.
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

val verifyHost by tasks.registering(Exec::class) {
    group = "verification"
    description = "Existing production host races, ABI, mutants and checker negative fixtures"
    inputs.files(fileTree("android/app/src"), fileTree("core/src"), fileTree("android/asr-runtime/src"),
        fileTree("tests"), fileTree("scripts"))
    // No up-to-date shortcut: negative fixtures must execute for each verify request.
    commandLine("bash", "scripts/test-minimal-apk.sh")
}
val verifyBuildInputs by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(":app:buildNativeArtifact")
    environment("NATIVE_TEST_ARTIFACT", file("android/app/build/native-debug/artifact/libqwen_asr_jni.so").absolutePath)
    inputs.files("scripts/prepare-gradle-inputs.py", "tests/gradle_inputs_test.py",
        "gradle/baseline-inputs.json")
    commandLine("python3", "tests/gradle_inputs_test.py")
}
val verifyApkChecker by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(":app:assembleDebug")
    environment("GRADLE_TEST_APK", file("android/app/build/outputs/apk/debug/app-debug.apk").absolutePath)
    inputs.files("scripts/verify-gradle-apk.py", "tests/gradle_apk_identity_test.py",
        "tests/module_owners_test.py", "tests/module_owners_guard_test.py",
        "gradle/baseline-inputs.json")
    commandLine("bash", "-euo", "pipefail", "-c",
        "python3 tests/gradle_apk_identity_test.py && python3 tests/module_owners_guard_test.py && " +
        "python3 -O tests/module_owners_guard_test.py")
}
val verifyNativeInputs by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(":app:buildNativeArtifact")
    environment("NATIVE_TEST_ARTIFACT", file("android/app/build/native-debug/artifact/libqwen_asr_jni.so").absolutePath)
    commandLine("bash", "-euo", "pipefail", "-c",
        "python3 tests/native_sources_test.py && python3 -O tests/native_sources_test.py && " +
        "python3 tests/native_artifact_test.py && python3 -O tests/native_artifact_test.py")
}
val verifyJni by tasks.registering(Exec::class) {
    group = "verification"
    description = "Generated JNI header, NDK bridge object and actual symbol verification"
    environment("JNI_CHECK_OUT", layout.buildDirectory.dir("jni-check").get().asFile.absolutePath)
    environment("JNI_MNN_SOURCE", file(".work/native-session-reuse/sources/mnn").absolutePath)
    inputs.files(fileTree("android/app/src"), fileTree("core/src"), fileTree("android/asr-runtime/src"),
        fileTree("native/apk"), fileTree("scripts"))
    commandLine("bash", "scripts/compile-jni-object.sh")
}
val verifyFormat by tasks.registering(Exec::class) {
    group = "verification"
    description = "Read-only UTF-8/whitespace gate for explicit maintained text, not raw evidence"
    commandLine("python3", "scripts/check-maintained-text.py")
}
val verifyLocalGuards by tasks.registering(Exec::class) {
    group = "verification"
    description = "Entry allowlist/cache-only and format-checker target negative fixtures"
    commandLine("bash", "-euo", "pipefail", "-c",
        "python3 tests/local_verify_entry_test.py && python3 tests/local_verify_fault_test.py && " +
        "python3 tests/fast_task_graph_test.py && python3 tests/dependency_checksum_fixture_test.py && " +
        "python3 tests/maintained_text_test.py && python3 -O tests/maintained_text_test.py")
}
val verifyArchitecture by tasks.registering(Exec::class) {
    group = "verification"
    description = "Source architecture and platform/privacy checker negative fixtures (not runtime)"
    commandLine("bash", "-euo", "pipefail", "-c",
        "python3 tests/architecture_boundary_test.py && python3 tests/activity_window_source_test.py && " +
        "python3 tests/model_android_source_test.py && python3 tests/text_export_source_test.py && " +
        "python3 tests/app_report_source_test.py")
}
tasks.register("verifyFast") {
    group = "verification"
    description = "35 JUnit/ABI/latch + actual module compile boundaries + source/entry/format negatives; no native/APK"
    dependsOn(":core:test", ":asr-runtime:testDebugUnitTest", verifyArchitecture,
        verifyFormat, verifyLocalGuards)
}
val verifyDependencyEvidence by tasks.registering(Exec::class) {
    group = "verification"
    description = "Isolated real AGP checksum mutant must fail with its target diagnostic"
    commandLine("bash", "tests/gradle_dependency_verification_test.sh")
}
val verifyLintEvidence by tasks.registering(Exec::class) {
    group = "verification"
    description = "Isolated actual library Lint error/restore and tool identity gate evidence"
    commandLine("python3", "tests/gradle_lint_verification_test.py")
    // Parent single worker still has a JVM; run the child only after heavy peers.
    mustRunAfter(verifyHost, verifyJni, verifyDependencyEvidence,
        ":app:lintDebug", ":asr-runtime:lintDebug", ":app:verifyDebugPackage")
}
val verifyLint by tasks.registering {
    group = "verification"
    description = "AGP native debug Lint, no baseline/suppression; app input provenance gates retained"
    dependsOn(":app:lintDebug", ":asr-runtime:lintDebug")
}
val verifyAndroidTestChecks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Shared three-APK policy/DEX/input/format target negatives (no devices)"
    commandLine("bash", "-euo", "pipefail", "-c",
        "python3 tests/android_test_apk_test.py && python3 -O tests/android_test_apk_test.py && " +
        "python3 tests/android_test_inputs_test.py && python3 -O tests/android_test_inputs_test.py")
}
val verifyAndroidTestPackage by tasks.registering(Exec::class) {
    group = "verification"
    description = "Current signed product/host/instrumentation APK isolation and JVM receipt, compiled-only"
    dependsOn(":test-host:testDebugUnitTest", ":test-host:assembleDebug", ":test-host:assembleDebugAndroidTest",
        ":app:assembleDebug", verifyAndroidTestChecks)
    commandLine("python3", "scripts/verify-android-test-apk.py", "--report-dir",
        layout.buildDirectory.dir("verification/android-test").get().asFile)
}
val verifyAndroidTestBuild by tasks.registering {
    group = "verification"
    description = "Build-only standard instrumentation host; never install or run Android tests"
    dependsOn(verifyAndroidTestPackage)
}
val verifyAndroidTestFixtureEvidence by tasks.registering(Exec::class) {
    group = "verification"
    description = "Isolated compiling fixture behavior mutants, JVM only (once per full graph)"
    commandLine("python3", "tests/android_test_fixture_test.py")
    mustRunAfter(verifyAndroidTestBuild, verifyHost, verifyJni, verifyDependencyEvidence, verifyLintEvidence)
}
tasks.register("verifyDebug") {
    group = "verification"
    description = "Host + JNI + AGP signed APK + fail-closed package/identity evidence"
    dependsOn(verifyHost, verifyBuildInputs, verifyNativeInputs, verifyApkChecker, verifyJni,
        ":core:test", ":asr-runtime:testDebugUnitTest", ":app:verifyDebugPackage",
        verifyArchitecture, verifyFormat, verifyLocalGuards, verifyLint, verifyDependencyEvidence, verifyLintEvidence,
        verifyAndroidTestBuild, verifyAndroidTestFixtureEvidence)
}
