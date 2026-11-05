import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

@Suppress("UNCHECKED_CAST")
val platformSources = (rootProject.extra["appSources"] as List<String>) +
    (rootProject.extra["runtimeSources"] as List<String>)
sourceSets {
    main {
        java.setSrcDirs(listOf(rootProject.file("android/app/src")))
        java.exclude(platformSources)
    }
    test {
        java.setSrcDirs(listOf("src/test/java", rootProject.file("tests")))
        java.include("*.java")
        java.exclude("InferenceAdapterTest.java", "InferenceAdapterTest.kt")
    }
}
kotlin.sourceSets.named("main") {
    kotlin.setSrcDirs(listOf(rootProject.file("android/app/src")))
    kotlin.exclude(platformSources)
}
// Exact migrated wrapper and twenty-six maintained core bodies enter test Kotlin.
kotlin.sourceSets.named("test") {
    kotlin.setSrcDirs(listOf("src/test/kotlin", rootProject.file("tests")))
    // Java bodies remain visible to kotlinc's mixed-source resolver, not emitted twice.
    kotlin.include("*.java")
    kotlin.exclude("InferenceAdapterTest.java", "InferenceAdapterTest.kt")
    kotlin.include("CoreRegressionJUnitTest.kt", "MinimalApkTest.kt", "ResultFilesTest.kt",
        "NativeResponseTest.kt", "ModelOperationControlTest.kt", "ModelZipImportTest.kt", "ModelRepositoryDeleteTest.kt",
        "ModelPrivateRootTest.kt", "TextExportFatalTest.kt", "TextExportSafetyTest.kt",
        "AdmissionBoundaryTest.kt",
        "ImeSessionTest.kt",
        "LogExportTest.kt",
        "ModelAndroidHelpersTest.kt",
        "ModelManagementControllerTest.kt",
        "ModelNotificationTest.kt",
        "ModelProviderBoundaryTest.kt",
        "ModelReadinessTest.kt",
        "ModelRepositoryCancelTest.kt",
        "ModelRepositoryTest.kt",
        "ModelReviewFixTest.kt",
        "RecordingRaceTest.kt",
        "RequestRunnerTest.kt",
        "RuntimeLogCoreTest.kt",
        "RuntimeLogStoreErrorTest.kt",
        "RuntimeLogWorkerErrorTest.kt",
        "TaskCoordinatorTest.kt",
        "TextExportTest.kt")
}
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all")
        allWarningsAsErrors.set(true)
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
    api("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("org.jetbrains:annotations:13.0")
    testImplementation("junit:junit:4.13.2")
}
val verifyModuleBoundary by tasks.registering(Exec::class) {
    group = "verification"
    dependsOn(tasks.classes)
    workingDir(rootProject.projectDir)
    commandLine("python3", rootProject.file("tests/module_boundary_test.py"),
        "--module", "core", "--classpath", sourceSets.main.get().runtimeClasspath.asPath)
}
tasks.test {
    // Legacy bodies read the public sample and compile the real source mutant.
    inputs.file(rootProject.file("bench/audio/zh-original.wav"))
    inputs.files(rootProject.fileTree("android/app/src"))
    dependsOn(verifyModuleBoundary)
    useJUnit()
    workingDir(rootProject.projectDir)
    maxParallelForks = 1
    maxHeapSize = "512m"
    include("**/CoreRegressionJUnitTest.class")
    testLogging { events("passed", "failed", "skipped") }
    doLast {
        project.exec {
            workingDir(rootProject.projectDir)
            commandLine("python3", "-B", "tests/kotlin_test_slice_test.py", "--junit", "core")
        }
    }
}
