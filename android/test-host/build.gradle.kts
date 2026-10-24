import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.llmasr.minimal.testhost"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    // Standard SDK runner/base APIs; never package these stubs or a homemade runner.
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    defaultConfig {
        applicationId = "org.llmasr.minimal.testhost"
        testApplicationId = "org.llmasr.minimal.testhost.test"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "p1b-testhost"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    sourceSets.named("test") { java.srcDir("fixtures/java") }
    sourceSets.named("androidTest") { java.srcDir("fixtures/java") }
    signingConfigs.named("debug") {
        storeFile = rootProject.file(".cache/android-signing/debug.p12")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
        storeType = "PKCS12"
        enableV1Signing = false
        enableV2Signing = false
        enableV3Signing = true
    }
    buildTypes.named("debug") {
        isDebuggable = true
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("debug")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures { buildConfig = false }
    testOptions.unitTests.all {
        it.useJUnit()
        it.maxHeapSize = "512m"
        it.maxParallelForks = 1
        it.include("**/FixtureContractTest.class")
        it.testLogging { events("passed", "failed", "skipped") }
    }
}
configurations.configureEach {
    resolutionStrategy {
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
    testImplementation("junit:junit:4.13.2")
}
tasks.configureEach {
    if (name.contains("Release")) {
        doFirst { error("Release test hosts are outside P1-B 2A") }
    }
}

// Exact maintained Kotlin owners; shared fixtures belong only to test variants.
val javafinalKotlinOwners = mapOf(
    "main" to listOf("org/llmasr/minimal/testhost/ImeHostActivity.kt"),
    "test" to listOf("org/llmasr/minimal/testhost/FixtureContractTest.kt",
        "org/llmasr/minimal/testhost/FakePorts.kt", "org/llmasr/minimal/testhost/FixtureChecks.kt"),
    "androidTest" to listOf("org/llmasr/minimal/testhost/FixtureInstrumentationTest.kt",
        "org/llmasr/minimal/testhost/FakePorts.kt", "org/llmasr/minimal/testhost/FixtureChecks.kt"))
kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf("src/main/kotlin"))
    kotlin.include(javafinalKotlinOwners.getValue("main"))
}
kotlin.sourceSets.named("test") {
    kotlin.setSrcDirs(listOf("src/test/kotlin", "fixtures/java"))
    kotlin.include(javafinalKotlinOwners.getValue("test"))
}
kotlin.sourceSets.named("androidTest") {
    kotlin.setSrcDirs(listOf("src/androidTest/kotlin", "fixtures/java"))
    kotlin.include(javafinalKotlinOwners.getValue("androidTest"))
}
tasks.withType<KotlinCompile>().configureEach {
    val rootsAndOwners = when (name) {
        "compileDebugKotlin" -> listOf("src/main/kotlin" to javafinalKotlinOwners.getValue("main"))
        "compileDebugUnitTestKotlin" -> listOf("src/test/kotlin" to javafinalKotlinOwners.getValue("test"),
            "fixtures/java" to javafinalKotlinOwners.getValue("test"))
        "compileDebugAndroidTestKotlin" -> listOf("src/androidTest/kotlin" to javafinalKotlinOwners.getValue("androidTest"),
            "fixtures/java" to javafinalKotlinOwners.getValue("androidTest"))
        else -> emptyList()
    }
    val allowed = rootsAndOwners.flatMap { (root, names) -> names.map { file("$root/$it").canonicalFile } }.toSet()
    // Filter Kotlin ownership; the AndroidTest Java provider keeps its original root.
    exclude { element -> element.file.extension == "kt" && element.file.canonicalFile !in allowed }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
        allWarningsAsErrors.set(true)
    }
}
