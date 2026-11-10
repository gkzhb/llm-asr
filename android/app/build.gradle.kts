import com.android.build.api.artifact.SingleArtifact
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val generated = layout.buildDirectory.dir("controlled-inputs")
// Source preparation is deliberately separate from configuration/compilation.
val nativeSources = rootProject.layout.projectDirectory.dir(".work/native-session-reuse/sources")
val nativeBuild = layout.buildDirectory.dir("native-debug")
val buildNativeArtifact by tasks.registering(Exec::class) {
    description = "Build pinned explicit MNN OBJECT/JNI artifact and recheck source/config/ELF identity"
    workingDir(rootProject.projectDir)
    inputs.dir(nativeSources)
    inputs.files(rootProject.fileTree("native"), rootProject.fileTree("patches"),
        rootProject.fileTree("android/app/src"), rootProject.fileTree("scripts"),
        rootProject.file("flake.nix"), rootProject.file("flake.lock"))
    outputs.dir(nativeBuild.map { it.dir("artifact") })
    // No Gradle shortcut: even cached native must pass current source/object/provenance gates.
    outputs.upToDateWhen { false }
    commandLine("bash", rootProject.file("scripts/build-native-artifact.sh"),
        nativeSources.asFile, nativeBuild.get().asFile)
}
val prepareInputs by tasks.registering(Exec::class) {
    description = "Stage verified public assets/current native artifact and identity-preserving manifest"
    dependsOn(buildNativeArtifact)
    workingDir(rootProject.projectDir)
    inputs.files(rootProject.file("gradle/baseline-inputs.json"),
        rootProject.file("scripts/prepare-gradle-inputs.py"), file("AndroidManifest.xml"),
        rootProject.file("reports/p0/mnn-model-manifest.json"),
        rootProject.file("bench/audio/zh-original.wav"),
        nativeBuild.map { it.file("artifact/libqwen_asr_jni.so") })
    inputs.dir("assets")
    inputs.dir("src")
    inputs.dir(rootProject.file("native/apk"))
    inputs.dir(rootProject.file("patches"))
    inputs.files(rootProject.file("models/mnn-16/config.json"),
        rootProject.file("models/mnn-16/llm_config.json"),
        rootProject.file("scripts/prepare-model-configs.py"))
    outputs.dir(generated)
    commandLine("python3", rootProject.file("scripts/prepare-gradle-inputs.py"),
        "--output", generated.get().asFile,
        "--native", nativeBuild.get().file("artifact/libqwen_asr_jni.so").asFile)
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
        versionCode = 8
        versionName = "0.8-debug"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets.named("main") {
        manifest.srcFile(generated.map { it.file("AndroidManifest.xml") })
        java.setSrcDirs(listOf("src"))
        @Suppress("UNCHECKED_CAST")
        java.include(rootProject.extra["appSources"] as List<String>)
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

// Only the exact five declared app Kotlin owners may enter this compiler.
@Suppress("UNCHECKED_CAST")
val appKotlinSources = (rootProject.extra["appSources"] as List<String>).filter { it.endsWith(".kt") }
kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf("src"))
    kotlin.include(appKotlinSources)
}
tasks.withType<JavaCompile>().configureEach {
    if (name == "compileDebugJavaWithJavac") {
        @Suppress("UNCHECKED_CAST")
        include(rootProject.extra["appSources"] as List<String>)
    }
}
tasks.named("preBuild") { dependsOn(prepareInputs) }
tasks.withType<KotlinCompile>().configureEach {
    // Android's Java source root is also added to the Kotlin task by AGP.
    // Source-set exclusion alone doesn't constrain that task-level collection.
    if (name == "compileDebugKotlin") {
        val allowed = appKotlinSources.map { file("src/$it").canonicalFile }.toSet()
        // AGP contributes an additional shared-root tree after source-set includes.
        // Filter actual task files, not just patterns on one contributed root.
        exclude { element -> element.file.extension == "kt" && element.file.canonicalFile !in allowed }
    } else exclude("**/*.kt")
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
    implementation(project(":core"))
    implementation(project(":asr-runtime"))
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
            "--report-dir", layout.buildDirectory.dir("verification/debug").get().asFile,
            "--native-build", nativeBuild.get().asFile)
    }
}
// Fail closed rather than accidentally deliver a new release identity.
tasks.configureEach {
    if (name.contains("Release")) {
        doFirst { error("Release variants are outside M1; use verified debug only (M10 gate)") }
    }
}
