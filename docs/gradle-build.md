# M1 标准 Android 构建

> 本页保存 M1 已完成批次的工具与验证背景。M2 当前实现已将 AGP 的 native 过渡路径替换为显式原生目标；独立源准备、当前命令和验证中状态见 [原生构建](native-build.md)。以下关于“现成 DSO / MNN 不重编 / M2 未实施”的表述仅对应 M1 历史批次，不作为 M2 当前交付结论。

任务 `llm-asr-76d.2`。用户已明确暂缓 `llm-asr-ap5`，保留其 Kotlin 迁移 owner；当前基建 writer 不继续迁移剩余52Java。在线与缓存后的严格离线全量验证、checksum负例、直接自审和最终输入核对已通过。**本地提交成功后才交付APK**，提交身份以Git为准；当前身份与证据见[本轮报告](../reports/gradle-m1/status.md)，设备仍pending。

## M3 当前模块/测试入口

M3已建立真实`:app → :asr-runtime → :core`和app直接core依赖；源码物理路径暂保留，core纯JVM独立编译/JUnit，runtime Android library，app装配/UI。当前实际生产63类（M6新增ActivityWindow）而非M1的62快照。详细唯一归属、逐旧main/参数路径JUnit覆盖映射、真实classpath编译负例与旧gate见[模块测试映射](module-test-map.md)。`verifyDebug`追加core34/runtime1真实JUnit及模块边界；继续所有原host/ABI/生产mutants/JNI/native/包gate。模块class输出和全部DEX定义均拒绝重复、缺失与core平台污染；APK指纹包含新模块配置/锁/src/测试。

M3为父监管下中间交接，最新测试身份见[实施报告](../reports/modules-m3/status.md)；未经父审/复验/安全本地提交，不关闭任务或宣称新APK已交付。下列M1段落保留为历史工具背景，不是当前模块归属。

## 工具分工和范围

- Nix `scripts/nix-env.sh apk` 提供既有 JDK17、SDK35/build-tools35.0.0、NDK28.2.13676358、Kotlin2.0.21 与 Python。
- Gradle Wrapper8.7已生成，distribution SHA固定为`544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d`，Wrapper JAR官方SHA `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8`核对一致；Gradle8.7在Nix JDK17实际启动通过。AGP8.6.1、Kotlin Gradle plugin2.0.21已实际全源编译/DEX/签名打包；官方AGP8.6兼容表支持API35/要求Gradle最低8.7/JDK17。Gradle自身内置Kotlin1.9.22是构建DSL工具，不是应用编译器版本。
- AGP管理现10Kotlin/52Java混合编译、资源/Manifest、DEX及原debug身份签名打包；不Exec调用旧整包脚本。sourceSets接原目录，不改产品源码/组件/权限/min29/target35/数学。
- M1 native采用 `gradle/baseline-inputs.json` 明确列出的既有DSO path/SHA过渡；生产/JNI/patch与小资产SHA核对，错误即拒绝。不解析Ninja重新链接，也不声称已完成M2干净native构建。
- source Manifest保持原字节，生成Manifest去冗余package/version/uses-sdk/extractNativeLibs，等值namespace/version/sdk/legacy packaging由DSL提供；检查最终真实包身份。

## 验证入口

在固定Nix环境执行；verify脚本固定严格依赖校验、Nix SDK aapt2并拒绝DEX异常，不允许重写校验元数据/锁：

```bash
bash scripts/nix-env.sh apk bash scripts/verify-gradle.sh
# 仅在缓存准备/依赖校验通过后验证离线（本轮实际通过）：
bash scripts/nix-env.sh apk bash scripts/verify-gradle.sh --offline --rerun-tasks
# 等价直接Wrapper命令，必须显式使用Nix SDK的patched aapt2：
GRADLE_USER_HOME="$PWD/.cache/gradle-user-home" bash scripts/nix-env.sh apk bash -c \
  './gradlew verifyDebug --offline --dependency-verification strict --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2"'
# 隔离checksum篡改负例，不更改主metadata/cache：
bash scripts/nix-env.sh apk bash tests/gradle_dependency_verification_test.sh
```

`verifyDebug`连接旧生产host/竞态/Java ABI/mutants/实际包checker负例、真实JNI生成头/NDK object、AGP debug APK和新产物检查。不以build输出存在或exit0代替签名/DEX/native/report绑定；metadata诊断必须审核拒绝，不吞warning。

AGP APK位于 `android/app/build/outputs/apk/debug/`，每次包核对结果位于 `android/app/build/verification/debug/run-<id>/`，不会覆盖既有 `reports/apk` 或 `dist`。产物检查运行apksigner实际验签、aapt2实际组件/权限树、ZIP/runtime类定义/10Kotlin owner、native SHA、资产与报告绑定。旧手工APK是回退证据，不代表新AGP包通过。

Wrapper distribution与直接/传递依赖SHA验证、可锁配置锁定和仓库白名单随本轮源码纳入Git。407依赖文件缓存SHA全核对、主要AGP/stdlib与官方仓库sidecar交叉核对；其余受控HTTPS仓库首次bootstrap信任固定，未逐项独立签名认证。校验元数据防后续漂移，不证明发布者绝对可信；版本目录不等于传递依赖锁。缓存后offline不代表干净缓存hermetic，仍需显式准备公开样例、配置、现native和原key。插件/应用/编译器配置锁保留；Gradle8.7不允许locking和failOnDynamicVersions并用，采用显式selector拒绝动态/range/latest/SNAPSHOT。

AGP debug包当前9个DEX，checker遍历全部class_def且拒绝重复，不以首DEX缺owner误判；包大小/DEX容器变化是构建系统差异，native/模型数学未变，不声称性能提升。单模型/录音/IME/导出契约保留；真实运行未验证。

## 资源/签名/边界

Gradle单worker、非并行、无长期daemon、JVM heap1536MiB、Kotlin in-process；不与大native编译/模型推理同时跑。JNI单object验证独立输出，MNN不重编。

沿用 `.cache/android-signing/debug.p12` 和证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，不在本任务创建或替换key，签名不匹配fail closed。不修改升级包名/IME注册或自动卸载。release构建/新正式签名/R8优化属于M10，当前禁用release任务执行。

模型/音频/缓存/密钥/APK/编译产物不入Git；wrapper JAR属于必要官方构建启动源输入，校验hash后单独审计，不混同生成的编译输出。没有设备/麦克风/私人文件授权，不安装APK。设备/SAF/IME/JNI实际运行验收始终pending。完成当前测试/自审/指纹与安全本地提交后才报告新APK交付。
