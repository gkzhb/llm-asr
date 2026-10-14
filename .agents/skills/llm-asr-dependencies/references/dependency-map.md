# 当前依赖地图与事实核验

本页是skill编写时基线`4ef49875`的导航，不是永远固定的任务状态。相对链接从本参考文件解析；`../../../../`指项目根，读取后仍以当前文件为准。

## Android/JVM

- [settings.gradle.kts](../../../../settings.gradle.kts)：集中plugin repositories（Google/Maven Central/Plugin Portal）及普通repositories（Google/Maven Central），FAIL_ON_PROJECT_REPOS；plugin useModule显式AGP/Kotlin版本。
- [根构建](../../../../build.gradle.kts)：AGP8.6.1、Kotlin2.0.21，app/runtime精确source owner，plugin classpath lock与verifyDebug任务图。
- [app](../../../../android/app/build.gradle.kts)、[core](../../../../core/build.gradle.kts)、[runtime](../../../../android/asr-runtime/build.gradle.kts)：固定selector校验+locking；stdlib2.0.21/annotations13.0，JUnit4.13.2为测试依赖。
- 锁：[buildscript-gradle.lockfile](../../../../buildscript-gradle.lockfile)、[core lock](../../../../core/gradle.lockfile)、[app lock](../../../../android/app/gradle.lockfile)、[runtime lock](../../../../android/asr-runtime/gradle.lockfile)。检查列出的真实configuration，不假设所有未来variant已锁。
- [verification-metadata.xml](../../../../gradle/verification-metadata.xml)：直接/传递artifact metadata/checksum。
- [Wrapper properties](../../../../gradle/wrapper/gradle-wrapper.properties)：Gradle8.7 distribution SHA；Wrapper JAR摘要由[verify入口](../../../../scripts/verify-gradle.sh)核。
- 当前无libs.versions.toml、完整分层CI/Lint/Android仪器体系；它们属于后续批准建设，不在新增skill时自动实施。

## Nix、native、Python

- [flake.nix](../../../../flake.nix)/[flake.lock](../../../../flake.lock)：JDK17/SDK35/build-tools35.0.0/NDK28.2.13676358/固定Kotlin等；[nix-env入口](../../../../scripts/nix-env.sh)以tiny mirror避免把权重复制进store。
- [native源码锁](../../../../native/source-lock.json)：MNN revision、MNN/KleidiAI归档、三patch和准备tree摘要；不是Maven lock。
- [native构建说明](../../../../docs/native-build.md)：prepare源码→CMake显式target→AGP buildNativeArtifact/prepareInputs；现单JNI DSO及静态C++runtime。
- [Python pyproject](../../../../model-tools/pyproject.toml)/[uv.lock](../../../../model-tools/uv.lock)：官方参考/转换独立工具环境。首次执行/升级需源审查和任务授权。
- [模型锁](../../../../model-tools/model-lock.json)、[模型部署清单](../../../../reports/p0/mnn-model-manifest.json)：来源/revision/精确assets，不是APK内权重。

## 证据与新架构边界

- [Gradle说明](../../../../docs/gradle-build.md)含M1历史段落；最新模块/产物核[modules-m3](../../../../reports/modules-m3/status.md)，不能把历史旧reports/apk当现AGP包。
- [模块测试映射](../../../../docs/module-test-map.md)：真实core/runtime/app边界，Java tests与生产mutants/JNI gate。
- [Kotlin迁移](../../../../docs/kotlin-migration.md)：metadata/interop风险；任务状态以Beads为准。
- [sherpa评估](../../../../docs/sherpa-engine-evaluation.md)：先前研究，不代表SDK已依赖/模型已下载。sherpa和ORT native加入需新scope与布局决策。

查看当前状态时，读配置/Git/Beads，而非把该页版本复制到新提交；报告需注明差异和其理由。
