import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.llmasr.runtime"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 29 }
    sourceSets.named("main") {
        java.setSrcDirs(listOf(rootProject.file("android/app/src")))
        @Suppress("UNCHECKED_CAST")
        java.include(rootProject.extra["runtimeSources"] as List<String>)
        manifest.srcFile("src/main/AndroidManifest.xml")
    }
    sourceSets.named("test") {
        java.setSrcDirs(listOf("src/test/java", rootProject.file("tests")))
        java.include("InferenceAdapterTest.java", "RuntimeRegressionJUnitTest.java")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures { buildConfig = false }
    testOptions.unitTests.all {
        it.useJUnit()
        // InferenceAdapterTest also checks actual caller/native source contracts.
        it.inputs.files(rootProject.fileTree("android/app/src"), rootProject.fileTree("native/apk"))
        it.workingDir(rootProject.projectDir)
        it.maxHeapSize = "512m"
        it.maxParallelForks = 1
        it.include("**/RuntimeRegressionJUnitTest.class")
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
    api(project(":core"))
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<JavaCompile>().configureEach {
    if (name == "compileDebugJavaWithJavac") {
        @Suppress("UNCHECKED_CAST")
        include(rootProject.extra["runtimeSources"] as List<String>)
    }
}
val verifyModuleBoundary by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn("compileDebugJavaWithJavac", "compileDebugKotlin")
    workingDir(rootProject.projectDir)
    doFirst {
        val compile = tasks.named<JavaCompile>("compileDebugJavaWithJavac").get()
        val classpath = files(compile.destinationDirectory, layout.buildDirectory.dir("tmp/kotlin-classes/debug"), compile.classpath, android.bootClasspath).asPath
        commandLine("python3", rootProject.file("tests/module_boundary_test.py"),
            "--module", "asr-runtime", "--classpath", classpath)
    }
}
tasks.configureEach {
    if (name == "testDebugUnitTest") { dependsOn(verifyModuleBoundary) }
    if (name.contains("Release")) {
        doFirst { error("Release variants remain outside M3 (M10 gate)") }
    }
}

// Only these four runtime Kotlin owners may enter the production compiler.
@Suppress("UNCHECKED_CAST")
val runtimeKotlinSources = (rootProject.extra["runtimeSources"] as List<String>).filter { it.endsWith(".kt") }
kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf(rootProject.file("android/app/src")))
    kotlin.include(runtimeKotlinSources)
}
tasks.withType<KotlinCompile>().configureEach {
    if (name == "compileDebugKotlin") {
        val allowed = runtimeKotlinSources.map { rootProject.file("android/app/src/$it").canonicalFile }.toSet()
        exclude { element -> element.file.extension == "kt" && element.file.canonicalFile !in allowed }
    } else exclude("**/*.kt")
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
        allWarningsAsErrors.set(true)
    }
}
