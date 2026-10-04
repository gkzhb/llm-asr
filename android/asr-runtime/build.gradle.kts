plugins { id("com.android.library") }

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
    dependsOn("compileDebugJavaWithJavac")
    workingDir(rootProject.projectDir)
    doFirst {
        val compile = tasks.named<JavaCompile>("compileDebugJavaWithJavac").get()
        val classpath = files(compile.destinationDirectory, compile.classpath, android.bootClasspath).asPath
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
