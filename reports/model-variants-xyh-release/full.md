# 当前版本7 formal full

唯一原入口：
```text
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```
实际由新work owned runner调用同原inner argv，15000s上限，Gradle worker1/nonparallel/heap1536m/Kotlininprocess、nativeparallel2和全部原内部超时不变。无clean/skip/propertyoverride/download/锁metadata更新；无并行模型/第二full。

真实exit0/owned_exited/root_reaped；`184 actionable tasks: 184 executed`；pipeline Gradle0/tee0/DEXscan1。完整日志 `.work/verify-local/full-47L0GV/build.log`，SHA `865294e54040c13f4b6189fb23622a35b149ec66ebf0e0fd2f492f27411b240b`；full stdout SHA `e4522188742070d83c932fe22ce63ea25b753ff2e830b40b454423d4f6d9a121`，execution SHA `59948fa3adef82c05e0225a229c74d1b2ca3215f37ff85f2a7b56184d36eac29`。

本次日志明确runUUID：product `android/app/build/verification/debug/run-c87cf2611bc141bda1ca8fc9579d77b4`，三APK `build/verification/android-test/run-cca6183714e948ad83352aefe158cf53`，非mtime选旧包。版本source/DSL/AGPmetadata/badging/result均7/0.7-debug，证书/v3/zipalign、全部DEXclass_def唯一owner、RECORD_AUDIO-only、IME组件/assets/native/reportbinding通过。原host普通/-O ZIP完整矩阵/catalogcleanup/picker/variants/guard、ABI/竞态/可编译mutants、模块/JVM/JNI/native故障、Lint/checksum/工具identity/fixture隔离和包gate保留执行，未以targeted替full。

| 包 | 当前路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5513993 | `ba46228ab4608a5e1dccbc533013008d2437e2ce33e330be35c0c95f902820ec` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2826552 | `d1763213c39994556e8b0779f102287dd037ca85235a7b25d3d94d339049d39f` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

JUnit实际XML库存：
- `core/build/test-results/test/TEST-CoreRegressionJUnitTest.xml`：tests=34，failure/error/skip=0/0/0，SHA `ebdf1b290eac343ed4fc3e7ff15e1b1686feeb1ba1cea8caf8f7dd07d03cb4a2`。
- `android/asr-runtime/build/test-results/testDebugUnitTest/TEST-RuntimeRegressionJUnitTest.xml`：tests=1，failure/error/skip=0/0/0，SHA `dc4b2e11eeadd6193cfd25d831e9b3058e52222c132a90cabc3d97abe84f9c56`。
- `android/test-host/build/test-results/testDebugUnitTest/TEST-org.llmasr.minimal.testhost.FixtureContractTest.xml`：tests=7，failure/error/skip=0/0/0，SHA `946532c6aeeb1d9c177cf8df63dd2e6639610bd00e98bd30de19da9b37a4cf97`。
合计42；仪器方法仅编译/DEX库存不冒runner发现执行。product DEX文件12、实际class定义1483；输入build/module/native为257/10/92动态实核。

原23source/test/build/baseline、原封口和工具/external/native输入前后保持；E5报告为成功后supplemental文本，不改变产品构建输入。固定red按精确授权正常重建，旧原位置binding失效保留，原字节由red-backup映射保全，未改旧receipt。6service visibility限制保留。

测试发生在base `d9db46a7eb8363bb76e1878a4a80ce29f6472aa8` 的dirtyfreeze下，freeze SHA `722be07ea7eddcb55a563c10e9b525a3a84f7aaf1f2e0f804264b77e986be428`，未来commit不是本次已测试对象；安全commit后须逐mode/blob/SHA对应绑定。当前无stage/commit/push，不声明APK交付。设备/runner/SAF/Binder/grant/mic/private/modelinference/质量性能NOTRUN。
