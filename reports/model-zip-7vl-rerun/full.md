# 7vl 已修 caller 新正式 full：PartRecovery旧mutation anchor首停

## 原命令与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- BG `b1bfbf5e1` 自动failed/exit1；3319.523s；Gradle `BUILD FAILED in 55m 16s`。
- 128 actionable / 128 executed；164 task行/163唯一路径，task行不冒action。
- pipeline Gradle1/tee0/DEXscan1；无D8/R8/Metadata错误。原CMake deprecated等警告保留，未扩inner/outer timeout/heap/worker或删除门槛。
- 完整log `.work/7vl-full-rerun/full.log` 36838bytes，SHA `016104df2eafd2ea2467c0622919ede43e4cae94876ed41b9e32db33378fd2cd`。
- 原Gradlelog `.work/verify-local/full-XbJcrN/build.log`；start/exit/execution均新CREATE_NEW文件，无旧helper执行。
- main caddb67 dirtyfreeze source37+baseline+审后caller，非测试过新commit对象；singleworker/Gradle1536m/Metaspace512m/JUnit512m且45s/Kotlininprocess/native2与全部旧预算保持。

## 首停：旧PartRecovery mutation oracle（不是有效mutant红）

```text
java.lang.AssertionError: check 6
at PartRecoveryTest.runOwnedRecovery(PartRecoveryTest.java:150)
at PartRecoveryTest.runRedEvidenceAgainstBuggyCopy(PartRecoveryTest.java:122)
Execution failed for task :verifyHost
```

原production PartRecovery green先运行成功。随后旧caller将生产源码精确字符串`reclaimEntryPart(e)`前插提前空间拒绝；当前已审U1源码新增progress provenance参数为`reclaimEntryPart(e, progress)`，所以src.replace未命中、buggy==src，check(!buggy.equals(src))直接失败。此时**尚未写mutant源码、未编译或运行mutant**，不算有效targetred/安全拒绝，也不是缓存/工具timeout。`.work/red`只经原caller清理重建为空，本leaf未额外清理或补写。

没有修PartRecovery或产品，不盲retry，不更新pins/锁metadata/gate；父另授最小test-only处置及必要回归后才可能再full。

## 当批覆盖矩阵

| 层 | 结果 | 当前证据及限制 |
|---|---|---|
| caller ABI | PASS | canonical fresh混编后完整KotlinMigrationTest通过；已审intMode/owner契约，旧NoSuchMethod不再出现 |
| ZIP ordinary/-O | PASS | 各17compile/31JVM=48 ownedchild；初始all body、7halt23/recover0对、八生产mutant各编译0/精确red1/恢复重编0-run0。原targeted --regressions才多9JVM为40，本次未加flag故31，非漏run |
| C1/C2/U1/N1 | PASS/host | all body真实8repair+8U1+11最终owner出口；exit-notify/额外三生命周期mutants仅已接受targeted，不冒正式 |
| typed picker普通/-O | PASS/host | 各3compile3JVM，完整实际Activity/Source/Controller平台fake，typed生产mutant精确red/恢复，不是Android系统picker |
| 原host前段 | PASS/部分 | K2J四mutants双模式、testslice/jvmnext/testhost/faults/enum/默认compatIME及readiness-first-start/proof/io/corefault、runtime15、MinimalApk/RecordingRace/ResultFiles/NativeResponse/Repository/PrivateRoot等实际先行链；不能冒完整host |
| runtime15 | PASS | 默认七mutants、15mixedroutes/30compilercommands/15JVM完整，30ownedwrapper child ledger核全退出回收；非subset |
| PartRecovery | FAIL | initial production green；旧mutation anchor不匹配在编译前失败，不算合法mutantred |
| 后续原host | NOT RUN | RequestRunner开始及尾链见下清单；不借旧taw通过 |
| SDK/四新owner | PASS/编译静态 | core三新class/runtimeSaf class实际输出及产品DEX class_def核，非合成ownerfixture；测试污染由三包checker拒绝 |
| native/JNI子gate | PASS/部分 | buildNativeArtifact实际JNI header/object/symbol、增量final link/578objects/config/ELF门槛；根verifyJni等未到达 |
| JUnit | 部分PASS | fresh testhost7全零；core34/runtime1未执行，旧XML不作本次通过 |
| checksum | PASS | 实际AGP原0/坏checksum1/恢复0，具体expected全零actual9697733a…51c1，不是缺cache冒red |
| production/isolatedLint/fixture | NOT RUN | 未有本次最终production ErrorWarning/isolatedNewApi/五phase结果，旧XML/report不借用 |
| 三包静态 | PASS | 当前v3 cert/zipalign/Manifest/权限/assets/native/全DEX无测试污染/14compiled-only仪器方法；非runner |
| 独立final产品gate | NOT RUN | app:verifyDebugPackage未执行；三包和payload只读等式不替代当前独立产品receipt |

## ZIP八类当批有效红（普通及-O各一遍）

| mutant | 精确外部 AssertionError 诊断 |
|---|---|
| trust | `ZIP trusted manifest cannot be replaced by package hashes` |
| type | `ZIP CEN symbolic link must be refused before model mutation` |
| local | `ZIP local and central names must agree` |
| crc | `ZIP bad CRC cannot pass even with trusted SHA` |
| actual | `ZIP actual byte budget must stop before overflow write` |
| close | `ZIP session cleanup must finish before final SHA and proof commit` |
| dirty | `ZIP durable dirty must precede first model mutation` |
| owner | `ZIP blocked provider close retains shared owner and archive` |

以上每项compile0→精确AssertionError1→原字节恢复重编0/run0，完整各child exit/rootreap和stdout/stderrSHA在inputs。picker另精确`ZIP stale or mismatched picker cannot import`，同样compile0/red1/restored0。PartRecovery失败不列入这个有效红表。

## 当前三包（full失败，非交付）

| path | package/version/code | bytes | SHA256 |
|---|---|---:|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | `org.llmasr.minimal` / `0.6-debug` / 6 | 5432945 | `63981787e54ce061d107d8bedd5f9d8fa758cb6ce21bd0850a71a725587d153b` |
| `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | `org.llmasr.minimal.testhost` / `p1b-testhost` / 1 | 2758372 | `07d53d9f962514ce1d739f8cb5d3d1b3ea0d2da3499c85048ba51c0f5f90ee69` |
| `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | `org.llmasr.minimal.testhost.test` / `not declared in Manifest` / 0 | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

三包receipt `build/verification/android-test/run-f18385272b484c3a8c8276fa654571dc/receipt.json` SHA `28099a54e623a93862b0125b331db22ae0cb93e6e2c096ece5c384df18cbd274`；247build/10module/tool当前SHA绑定。
三包v3 cert `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。metadata路径/SHA/version与每tool exit/rawSHA、DEX数量/14方法见inputs。
- 产品仍0.6-debug/code6、唯一RECORD_AUDIO、无新增权限/模型嵌入；host/test无permissions/native/assets；三包可与前次字节相同，但fresh编译/签名/check确执行。

## Native与当前输入保护

- native `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`保持批准基线；实际578MNNobjects逐SHA pre/post不变，真实JNI复编与incremental final link，不是全native clean/hermetic。
- JNI object `6186abda6167c1a0ec45bff578e36da749e73fdd5650307293bfa0ff5bfbf9ae`，header `25f81ea737cbb954fe6b547c97b53f8cf4c8191194fd5de71e3892bcf7248261`；provenance/config/外部工具逐SHA在inputs。
- pre/post247build/10module/88native/3external/tools/70owners/source37+caller+baseline全等；1995tracked/783excluded保持，旧receipt不覆。native88含Android源码，不称历史84没变。
- 五JUnit/Hamcrest批准工件pre/post SHA保持、未联网/touch；checksum后现有不等持续监控或empty-cache可复现。
- 旧failedfull30文件当前库存保持（runtimeJSON仍误写后的9d16219e…525c），旧原绑定invalid历史保留，父接受透明supplement另供当前准入。不恢复/改旧receipt，不把此次失败改成成功。
- 新报告仅supplemental，写后只读核输入/全部post输出不漂移，不为补文档重full；最终47未来安全候选不等stage授权。

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

## 原host首停后的未运行部分

- PartRecovery mutant compile and target diagnostic
- RequestRunnerTest through host tail including original controller/notification/provider and ModelReviewFixTest eight variants
- foreground/capture12/activity window/logs picker and host source/privacy/architecture/JNI report negatives
- model3mutants/mixed lifecycle/apk report binding after PartRecovery

## 设备、提交与安全边界

Androidrunner发现执行/install/device/systemSAF/Bindergrant/lifecycle/micprivate/实际大ZIP权重ASR/latencyPSS/掉电/4K16K运行/空缓存hermetic全部NOTRUN。strictZIP32/ASCII、docs合成打包例/非全包回滚/低空间安全拒绝及hostclose后fault非vendor/FD证明见review.md。未stagecommitpush；full仍失败，父消费后另授最小处置，不能交付APK。
