# M3 父会话独立复验

基准 HEAD 538dcb66f16d41adeba440d934d3a0d2e8c4eb6d；父监管 m3-steward，session 01a107d7-3865-75a0-abef-30c1dddfbf36。本页只记录实际执行证据，不替代独立审查或设备验收。

## 实际命令与结果

在固定 Nix apk 环境、GRADLE_USER_HOME=.cache/gradle-user-home，串行执行：

1. `./gradlew :core:clean :asr-runtime:clean --offline --no-daemon --dependency-verification strict --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2"`
2. `bash scripts/verify-gradle.sh --offline --rerun-tasks`
3. `bash tests/gradle_dependency_verification_test.sh`

父后台任务 bbb64c7ae 正式 completed/exit0。完整日志 `.work/gradle-m3-parent/verify.log`，Gradle `.work/gradle-m1/verify-ZiKuUN/build.log`；70 actionable tasks全部执行，BUILD SUCCESSFUL（3m16s）。实际checksum篡改隔离Gradle按目标拒绝exit1，测试脚本exit0，诊断 `.work/gradle-m1/dependency-negative-B1gAHq/rejection.log`。

## 核对

- 当前实际class唯一归属 app8/runtime5/core50；真实Gradle classpath编译负例按目标依赖拒绝，正例成功。
- JUnit XML复核：core34/runtime1，failure/error/skipped均0；完整原host/ABI/mutants/JNI/native/实际APK门槛全部执行通过。
- 真实生成JNI头/NDK对象和当前DSO符号、native数学配置/provenance/ELF静态16KiB门槛通过。
- 本次实际包报告 `android/app/build/verification/debug/run-87899ebad8fd4437ac86dc0a05eeabed/`。
- APK `android/app/build/outputs/apk/debug/app-debug.apk` SHA256 `a0494d25f09b4352d30847af00228ee29a2589dbf98e812276018eb12072a3f3`，与执行Agent冻结报告一致。
- 父逐SHA核对 `reports/modules-m3/build-input-sha256.json` 的174输入均与当前文件一致；APK大小/SHA与报告相符。`git diff --check`通过。

## 验收边界

异家族m3-review只读审查仍待结果，尚未安全暂存/提交/关闭/交付。Android仪器、设备、麦克风、SAF/IME/JNI运行及4KiB/16KiB设备各not run。父清理模块classes重跑不等于干净缓存hermetic或设备验收；已有原生缓存由当前provenance/身份gate再次核验。
