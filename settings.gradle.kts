pluginManagement {
    // The application/library and Android/JVM plugins share already pinned
    // implementations; don't introduce unverified marker artifacts per module.
    resolutionStrategy.eachPlugin {
        when (requested.id.id) {
            "com.android.application", "com.android.library" ->
                useModule("com.android.tools.build:gradle:8.6.1")
            "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm" ->
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "llm-asr"
include(":app", ":core", ":asr-runtime")
project(":app").projectDir = file("android/app")
project(":asr-runtime").projectDir = file("android/asr-runtime")
