plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
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
    inputs.files(fileTree("android/app/src"), fileTree("tests"), fileTree("scripts"))
    // No up-to-date shortcut: negative fixtures must execute for each verify request.
    commandLine("bash", "scripts/test-minimal-apk.sh")
}
val verifyBuildInputs by tasks.registering(Exec::class) {
    group = "verification"
    inputs.files("scripts/prepare-gradle-inputs.py", "tests/gradle_inputs_test.py",
        "gradle/baseline-inputs.json")
    commandLine("python3", "tests/gradle_inputs_test.py")
}
val verifyApkChecker by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(":app:assembleDebug")
    environment("GRADLE_TEST_APK", file("android/app/build/outputs/apk/debug/app-debug.apk").absolutePath)
    inputs.files("scripts/verify-gradle-apk.py", "tests/gradle_apk_identity_test.py",
        "gradle/baseline-inputs.json")
    commandLine("python3", "tests/gradle_apk_identity_test.py")
}
val verifyJni by tasks.registering(Exec::class) {
    group = "verification"
    description = "Generated JNI header, NDK bridge object and actual symbol verification"
    environment("JNI_CHECK_OUT", layout.buildDirectory.dir("jni-check").get().asFile.absolutePath)
    inputs.files(fileTree("android/app/src"), fileTree("native/apk"), fileTree("scripts"))
    commandLine("bash", "scripts/compile-jni-object.sh")
}
tasks.register("verifyDebug") {
    group = "verification"
    description = "Host + JNI + AGP signed APK + fail-closed package/identity evidence"
    dependsOn(verifyHost, verifyBuildInputs, verifyApkChecker, verifyJni, ":app:verifyDebugPackage")
}
