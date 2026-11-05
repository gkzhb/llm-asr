# 7vl 当次正式 full：失败事实与分层证据

## 原入口与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- BG `b5b63fd8a` 自动 failed / exit1；214.860s；Gradle `BUILD FAILED in 3m 32s`。
- 128 actionable tasks / 128 executed；164 task行 / 163唯一path（verifyHost重复FAILED行），不能冒164actions。
- pipeline Gradle1 / tee0 / DEX scan1，无D8/R8/Metadata异常；另有原CMake deprecated和Gradle Metaspace预警，未调整资源/抑制。
- 完整日志 `.work/7vl-full/full.log`，19343bytes，SHA-256 `25d7c00ad3089fad45a11cb3a222f28c8bf97dd4acb74167122ee7fd48ea74ab`。
- 原Gradle日志 `.work/verify-local/full-7wOW0n/build.log`。真实exit/argv/start/finish见 `.work/7vl-full/full.execution.json`。
- single worker、Gradle1536MiB/Metaspace512MiB、JUnit512MiB/45s、Kotlin in-process、native parallel2及原独立driver预算保持；未加skip/init/写锁或未知flags。

## 首停诊断

```text
java.lang.NoSuchMethodException: org.llmasr.minimal.modelmanagement.ModelPickerTickets$Ticket.<init>(int)
at KotlinMigrationTest.batchPrivateConstructor(KotlinMigrationTest.java:578)
at KotlinMigrationTest.batchAdapterAndAbi(KotlinMigrationTest.java:566)
Execution failed for task :verifyHost
```

正式host先fresh混编68Kt+18Java成功；初始KotlinMigrationTest部分ABI输出后在旧private单int构造反射处失败。当前Ticket真实构造为private `(int, Mode)`，对应新不可变typed picker。此处只读诊断，没有修改caller/产品；失败不是可编译行为mutant精确AssertionError红，不是缺缓存，不自动修/盲重试。

## 覆盖矩阵

| 层 | 结果 | 当次证据与边界 |
|---|---|---|
| 冻结/准入 | PASS | main caddb67 dirtyfreeze，index空；live准确sol/high；37candidate+baseline逐modeSHA、semantic10改4新及其余域不变 |
| 原host | FAIL/部分执行 | 配置16检查/fixture collector、canonical fresh compile、KotlinMigrationTest部分ABI；余链未到达 |
| ZIP/picker | NOT RUN | 普通/-O driver、8生产mutants、7 halt/recovery、C1/C2/U1/N1和owner出口body均未正式执行；旧targeted只作已接受来源 |
| 原runtime15/compiledmutants/latch | NOT RUN | 初始ABI首停，未到后续IME/readiness/start/proof/runtime15及原host业务链，不借taw旧成功 |
| SDK编译/owner | PASS/静态 | app/core/runtime真实编译；四新owner实际module class与产品DEX确认，非合成fixture冒SDK产物；三包checker验证测试污染隔离 |
| Native/JNI子门槛 | PASS/部分 | buildNativeArtifact内部JNI真实全70源51Kt19Java、生成头/object/symbol、增量link及native gate；根verifyJni等后续任务未执行 |
| JUnit | 部分PASS | 当前host7全零；core34/runtime1任务未执行，旧XML非证据 |
| checksum | PASS | 正式full内隔离原0/指定AGP checksum红1/恢复0；expected全零、actual9697733a…51c1，非工具/cache失败 |
| Production/isolatedLint | NOT RUN | 当次最终XML/Error/warnings不借旧报告；Metaspace预警不据此改资源 |
| fixture五phase | NOT RUN | 无当次0/1/0/1/0，不借旧fixture成功 |
| 三包静态 | PASS | fresh构建、v3 cert/zipalign/Manifest/权限/全DEX去重/14compiled-only仪器方法；不等runner发现/执行 |
| 独立最终产品gate | NOT RUN | app:verifyDebugPackage未执行；三包与只读payload等式不替代finalcheckerreceipt |

## 当前三 APK（非交付）

| path | package/version/code | bytes | SHA-256 |
|---|---|---:|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | `org.llmasr.minimal` / `0.6-debug` / 6 | 5432945 | `63981787e54ce061d107d8bedd5f9d8fa758cb6ce21bd0850a71a725587d153b` |
| `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | `org.llmasr.minimal.testhost` / `p1b-testhost` / 1 | 2758372 | `07d53d9f962514ce1d739f8cb5d3d1b3ea0d2da3499c85048ba51c0f5f90ee69` |
| `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | `org.llmasr.minimal.testhost.test` / `` / 0 | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

三包v3同证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`；product0.6-debug/code6未改，test Manifest不声明version（metadata空name/code0）。
三包receipt `build/verification/android-test/run-dd6df06e025b4a3db727d11fea1a3e43/receipt.json`，SHA `9b8bbd637f2af0bf2f4550070e9604ffdf3c0622c6164fb1f8b44ce6f5b5ec40`，当前247build/10module与全部工具逐SHA一致。
- metadata路径/SHA/所有tool exit和rawSHA/Manifest/DEX数量及实际新owner class SHA见inputs.json；product1456/host1340/test47 class_def，14仪器方法仅静态。
- 产品唯一RECORD_AUDIO、资产逐SHA精确baseline，无新增权限或模型嵌入；host/test无权限/native/assets。
- host/product新字节变化为ZIP生产类及core依赖传入host；test字节保持，但fresh编译/package/tool均本次执行。

## Native、输入和保护域

- native `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`仍批准身份；578 MNN objects逐SHA pre/post不变，只有JNI复编+增量final link，非全nativeclean。
- JNI object SHA `6186abda6167c1a0ec45bff578e36da749e73fdd5650307293bfa0ff5bfbf9ae`；header SHA `25f81ea737cbb954fe6b547c97b53f8cf4c8191194fd5de71e3892bcf7248261`；源码/native-link/provenance/当前input工具SHA见inputs.json。
- pre/post247build/10module/88native/3external/工具全等；native域含Android源，不冒与旧84全部一致。8新untracked均记录，7源码/tests入collector，格式docs单列supplemental。
- 1995tracked/777原excluded modeSHA保持；72111 ZIP历史证据、368旧taw证据（后者首次捕获在新full启动后，不冒prelaunch）及旧正式receipt均不覆写。
- 五已批准JUnit/Hamcrest工件pre/post存在且SHA相同；checksum子链后未发现丢失，未联网或手工touch，不称连续监控/empty-cache hermetic。
- 真实终态后一次/proc检查无相关Gradle/host/编译残留；未poll或重试。

## 未执行的正式必需后续任务

- `:verifyJni`
- `:verifyBuildInputs`
- `:verifyNativeInputs`
- `:verifyApkChecker`
- `:verifyArchitecture`
- `:verifyFormat`
- `:verifyLocalGuards`
- `:verifyLintEvidence`
- `:verifyAndroidTestFixtureEvidence`
- `:app:verifyDebugPackage`
- `:app:lintDebug`
- `:asr-runtime:lintDebug`
- `:core:test`
- `:asr-runtime:testDebugUnitTest`
- `:verifyDebug`

## 报告/提交/设备边界

四新非敏感报告为supplemental，写后只读复核current输入/产物不变，不再full。产品37+baseline继续冻结，未stage/commit/push。构建在caddb67 dirty工作树，不冒测试过新commit对象。full失败阻塞交付；父消费后另授最小caller处置及必要重验，不由本leaf擅改scope/gate。

- NOT RUN: ZIP/picker ordinary-O drivers, all their actual formal mutants/crash/repair bodies; full stopped before those invocations
- NOT RUN: original host ABI remainder/latch/default/runtime15 chain/mutants and associated owned child ledgers
- NOT RUN: core/runtime35 JUnit/production Lint/isolated Lint/five-phase fixture/final independent product gate and other required tasks listed
- NOT RUN: Android runner discovery/execute/install/device/system SAF/Binder grants/lifecycle/mic/private/actual large weights/ASR/latencyPSS/power loss/4K16K runtime/empty-cache hermetic

已接受fresh审摘要见review.md；strictZIP32/ASCII限制、docs打包例、非全包回滚/低空间拒绝和host syntheticclose非vendorclose边界保留。
