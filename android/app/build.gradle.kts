import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val generated = layout.buildDirectory.dir("controlled-inputs")
val prepareInputs by tasks.registering(Exec::class) {
    description = "Stage only hash-verified public assets/native and identity-preserving manifest"
    inputs.files(rootProject.file("gradle/baseline-inputs.json"),
        rootProject.file("scripts/prepare-gradle-inputs.py"), file("AndroidManifest.xml"),
        rootProject.file("reports/p0/mnn-model-manifest.json"),
        rootProject.file("bench/audio/zh-original.wav"),
        rootProject.file(".work/build/minimal-apk/package/lib/arm64-v8a/libqwen_asr_jni.so"))
    inputs.dir("assets")
    inputs.dir("src")
    inputs.dir(rootProject.file("native/apk"))
    inputs.dir(rootProject.file("patches"))
    inputs.files(rootProject.file("models/mnn-16/config.json"),
        rootProject.file("models/mnn-16/llm_config.json"),
        rootProject.file("scripts/prepare-model-configs.py"))
    outputs.dir(generated)
    commandLine("python3", rootProject.file("scripts/prepare-gradle-inputs.py"),
        "--output", generated.get().asFile)
}

android {
    namespace = "org.llmasr.minimal"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "org.llmasr.minimal"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.6-debug"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets.named("main") {
        manifest.srcFile(generated.map { it.file("AndroidManifest.xml") })
        java.setSrcDirs(listOf("src"))
        res.setSrcDirs(listOf("res"))
        assets.setSrcDirs(listOf(generated.map { it.dir("assets") }))
        jniLibs.setSrcDirs(listOf(generated.map { it.dir("jniLibs") }))
    }
    signingConfigs.named("debug") {
        // Same pre-existing local identity; never create/substitute another key here.
        storeFile = rootProject.file(".cache/android-signing/debug.p12")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
        storeType = "PKCS12"
        enableV1Signing = false
        enableV2Signing = false
        enableV3Signing = true
    }
    buildTypes {
        named("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        // Release construction/publication is M10, not implicitly authorized by M1.
        named("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libqwen_asr_jni.so"
        }
    }
    buildFeatures { buildConfig = false }
}

tasks.named("preBuild") { dependsOn(prepareInputs) }
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
        allWarningsAsErrors.set(true)
    }
}
configurations.configureEach {
    resolutionStrategy {
        // Gradle8.7 forbids failOnDynamicVersions together with locking.
        // Enforce fixed selectors explicitly rather than disabling the lock.
        eachDependency {
            val version = requested.version.orEmpty()
            check(version.isNotBlank() && !version.contains('+') &&
                !version.contains('[') && !version.contains(']') &&
                !version.contains('(') && !version.contains(')') &&
                !version.startsWith("latest.") && !version.endsWith("-SNAPSHOT")) {
                "Non-fixed dependency selector rejected: $requested"
            }
        }
        activateDependencyLocking()
    }
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("org.jetbrains:annotations:13.0")
}

androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
    tasks.register<Exec>("verifyDebugPackage") {
        group = "verification"
        description = "Check this AGP variant's signed APK and record isolated current evidence"
        dependsOn("assembleDebug")
        inputs.dir(apkDirectory)
        inputs.files(rootProject.file("gradle/baseline-inputs.json"),
            rootProject.file("scripts/verify-gradle-apk.py"))
        commandLine("python3", rootProject.file("scripts/verify-gradle-apk.py"),
            "--apk-dir", apkDirectory.get().asFile,
            "--report-dir", layout.buildDirectory.dir("verification/debug").get().asFile)
    }
}
// Fail closed rather than accidentally deliver a new release identity.
tasks.configureEach {
    if (name.contains("Release")) {
        doFirst { error("Release variants are outside M1; use verified debug only (M10 gate)") }
    }
}
