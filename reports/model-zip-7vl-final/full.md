# 7vl 当批完整正式full：PASS，待安全提交

## 原命令与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- BG `b34489620` 自动completed/exit0；3914.049s，Gradle `BUILD SUCCESSFUL in 1h 5m 11s`。
- 184 actionable / 184 executed；231task行/229唯一路径（core:test输出段重复），不冒task行数为actions。
- pipeline Gradle0 / tee0 / DEXscan1；无D8/R8/Metadata异常。Nix自身cache-only、Gradlestrict/offline、单worker/JVM1536m/metaspace512m/JUnit512m+45s/Kotlininprocess/nativeparallel2/所有原inneroutertimeout保持。
- full.log `.work/7vl-full-final/full.log` 75283bytes，SHA `404dcbae3aaa24046113c806f9dc7044a92c4e5b2e5e95e3975e82f36f776a02`。
- 原Gradlelog `.work/verify-local/full-xsDb6c/build.log` SHA `ae844d2a15fc81a10c2a03f1a7ed706de3908b19b614cbb92406fe37644f07c0`；执行argv/start/finish/exit及preSHA在full.execution.json。
- 无额外targeted/下载/init/skip/改pins锁metadata/gate，非空缓存/hermetic复现。构建时dirtyfreeze main caddb67，非测试过新commit对象。

## 当前正式覆盖

| 层 | 结果与当次证据 |
|---|---|
| ZIP普通/-O | 各17mixedcompile/31JVM=48ownedchild；initial all body、7实际halt23/recover0、8真实productionmutantscompile0/精确red1/恢复重编0-run0。31为原formal入口，带--regressions targeted才多9JVM成为40 |
| C1/C2/U1/N1 | 全8repair+8U1+11最终资源出口body真实green；exit-notify独立case/最终额外3生命周期mutants仅此前targeted，不冒正式 |
| picker普通/-O | 各3compile3JVM=6ownedchild；真实Activity/Source/Controller对平台fakes，typedmutant精确red→restore；不是系统picker设备行为 |
| 原host完整 | ABI/null/monitor/default/compat/IMEreturn/readiness/first/start/proof/nativeowner/cleanup/latch/runtime15、所有旧compiledmutants、录音12fakephase、ActivityWindow/logpicker、ModelReviewFix8/provider/通知、model3mutants/mixedlifecycle/源报告guard完整执行 |
| PartRecovery | host完整main15+coreJUnit对应case通过；实际freshmutantcompile0/run0并输出EXPECTED_FAIL存储空间不足：预检，run0意为观察到缺陷拒绝，不是buggy实现green；production前后green |
| runtime15 | 原default15mixedroutes/30compilercommands/15JVM、七mutants恢复，30ownedwrapper逐SHA确认退出回收，非subset |
| JUnit42 | core34/runtime1/host7 fresh XML全部failures/errors/skips0，方法库存与XMLSHA见inputs；不含Androidrunner |
| 模块与SDK | 实际core55/app8/runtime7共70owner，4新ZIPowner真实moduleclass与产品DEX存在，test污染隔离；真实classpath compiled边界拒绝通过 |
| JNI/native | 独立verifyJni及buildNative内部真实生成头/object/symbol，578实际MNNobjects/prepost逐SHA保持，incremental final link/native provenance/config/ELF16KiB/staticruntime门槛通过 |
| ProductionLint | 新app/runtime报告Error0/Fatal0，app24/runtime17同源warnings分类defer，无baseline/抑制/关error；host仅identity resolve，未跑hostLintconsumer |
| isolatedLint | 实际defaultAGP0→唯一NewApi API33/min29具体file/line红1→恢复0；identity工件/边及同生产validator真实Configuration缺/多/替换拒绝1、marker恢复0；非typed默认AGP可变classpath声称 |
| checksum | 正式隔离原0→AGP唯一checksum目标1→恢复0，expected全零actual9697733a…51c1，主metadata不变；非缺cache/tool失败作red |
| fixture五phase | 一次driver0/1/0/1/0，每phasefreshKotlin/确切7JUnit；payload/cleanup各唯一外部AssertionError，原字节恢复/真实owned退出 |
| 三包+final产品 | 本次三APK v3cert/zipalign/Manifest权限assets/native/全DEX隔离/14compiled-only方法及独立app:verifyDebugPackage都执行，currentreceipt与输入绑定 |

## ZIP/picker正式有效红

| mutant | 精确AssertionError诊断（双模式均compile0/red1/restored0） |
|---|---|
| trust | `ZIP trusted manifest cannot be replaced by package hashes` |
| type | `ZIP CEN symbolic link must be refused before model mutation` |
| local | `ZIP local and central names must agree` |
| crc | `ZIP bad CRC cannot pass even with trusted SHA` |
| actual | `ZIP actual byte budget must stop before overflow write` |
| close | `ZIP session cleanup must finish before final SHA and proof commit` |
| dirty | `ZIP durable dirty must precede first model mutation` |
| owner | `ZIP blocked provider close retains shared owner and archive` |

picker另为`ZIP stale or mismatched picker cannot import`。PartRecovery target为`EXPECTED_FAIL 存储空间不足：预检`，原旧NoSuchMethod/check6不是安全red。所有ZIP/picker/IME/runtime15/fixture关键child ledger owned_exited/root_reaped实际真；额外有限当前driver审计709JSON/616owned record occurrences全确认（含嵌入/独立重复，不等616唯一命令），无unknown记录。完整stdout/stderrSHA只列hash，不复制raw日志。

## Fresh JUnit与Lint

- `core/build/test-results/test/TEST-CoreRegressionJUnitTest.xml`：34测试/0failure0error0skip，SHA `3b74780dd81e70439642541013da9d4eae6d6a0371eeedf437d2a63a427c1f80`。
- `android/asr-runtime/build/test-results/testDebugUnitTest/TEST-RuntimeRegressionJUnitTest.xml`：1测试/0failure0error0skip，SHA `20b73d1e0b8c440ce25ff36d41923e4b082e96f1d819ced72de36eddc34fc017`。
- `android/test-host/build/test-results/testDebugUnitTest/TEST-org.llmasr.minimal.testhost.FixtureContractTest.xml`：7测试/0failure0error0skip，SHA `7daf1dadfdd7835c9fe53e23e7b1c6c3a061c0d9afc00822eba803688bd0bb7f`。
- app Lint：Error0/Fatal0/24warnings，类别 `{"ChromeOsAbiSupport": 1, "DataExtractionRules": 1, "GradleDependency": 1, "MissingApplicationIcon": 1, "SetTextI18n": 15, "UnusedAttribute": 3, "UsableSpace": 2}`，fresh XML SHA `7a7dbb6f097271e6eb0d3ac2e2388e0a8ba6491776d419d1ef812cc93e8f5910`。
- runtime Lint：Error0/Fatal0/17warnings，类别 `{"SetTextI18n": 15, "UsableSpace": 2}`，fresh XML SHA `359b09d5e41ac99ccdcf568e0472669495ed7c7e5287c8cc49173fa656e103c9`。

warnings按既有分类defer，不声称无warning。runtime为共享源重复，不能简单加总成独立41问题。host Lint未执行，identityresolve不是consumer。
- isolatedLint receipt `.work/gradle-lint-negative/run-okuoj_8i/result.json` SHA `b2c6f85d1f4699245af52d17c55cb250f3a0e173322aa90d663eb511571a6793`。
- fixture receipt `.work/p1b-build/2b/behavior-rw6261ct/result.json` SHA `c440dfa7d72b979b14a2a367f396931e6b133bf58d878f89b5672c6d846d51ee`，phase0/1/0/1/0。

## 当前三包及独立receipt

| path | package/version/code | bytes | SHA256 |
|---|---|---:|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | `org.llmasr.minimal` / `0.6-debug` / 6 | 5432945 | `63981787e54ce061d107d8bedd5f9d8fa758cb6ce21bd0850a71a725587d153b` |
| `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | `org.llmasr.minimal.testhost` / `p1b-testhost` / 1 | 2758372 | `07d53d9f962514ce1d739f8cb5d3d1b3ea0d2da3499c85048ba51c0f5f90ee69` |
| `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | `org.llmasr.minimal.testhost.test` / `Manifest未声明` / 0 | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

三包receipt `build/verification/android-test/run-06d5d13d8b094d779058f38240fa8136/receipt.json` SHA `cae3db4736bb6184e12640d7e91417e65e45d6b1ff9c62c66f8dd202f838d00b`。
独立产品receipt `android/app/build/verification/debug/run-b770120b3b96497483b72973449c0cf4/result.json` SHA `24c7ade6602e0e6b62fd043420b24a306140f5cc2afa0dcd1659cf7a1574ac6c`；buildinputs `47a09288b5676ee069b8efc03359dbcff5cd58f01a3704a782e747b44467f818`，current247build精确相等。
三包v3 cert `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，5tool/包全部exit0，metadata/Manifest/rawSHA/DEXclass/method及payloadSHA见inputs。
- product仍org.llmasr.minimal/0.6-debug/code6，hostp1b-testhost/1，testManifest不声明version/metadata空name0；不用改版本区分本批，APKhash及当前receipt绑定。三APK恰同prerun允许的失败ZIP产物，但本次fresh编译/package/sign/check/gates全部执行，不借旧receipt。
- 产品唯一RECORD_AUDIO，固定资产精确SHA/native不变，无模型嵌入/新增权限；host/test无权限/native/assets，product无JUnit/runner/mock/fixture污染，14仪器方法仅编译parsedDEX，不冒runner发现执行。

## 输入/native/历史保护

- source37冻结aa389786…21e8，baseline `4db0023cb5c3ece585cad2c2c19c2d68431c56fbfe53c931fc0a64ab513475cc`；KotlinCaller31e13b5d…3e76与PartCallera45b75ec…b8ac为唯一额外已审testdelta。原native/sample/signing/source_commit/locksmetadata不变。
- 当前247build/10module/88native/3external及工具、70owners、1995tracked/789excluded全部pre/post相同；8新untracked纳入库存，7源码/tests属真实collector，格式docs属supplemental。native88含Android源，不冒历史84全等。
- native `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`，578 MNNobjects逐SHA保持；JNI buildobject `6186abda6167c1a0ec45bff578e36da749e73fdd5650307293bfa0ff5bfbf9ae`、independentobject `6186abda6167c1a0ec45bff578e36da749e73fdd5650307293bfa0ff5bfbf9ae`、header `25f81ea737cbb954fe6b547c97b53f8cf4c8191194fd5de71e3892bcf7248261`。真实JNI复编+incrementallink，非nativeclean。
- 五JUnit/Hamcrest已批准工件pre/post存在SHA不变，checksum子链后当前完整，真实Lint/JUnit成功消费；不称持续presence监控、空缓存或机器外复现。
- 旧两full50本地证据与8failedreports保持当前库存，原runtime-final历史绑定仍invalid（期望b1cd7414…f729/current9d16219e…525c），父接受透明supplement供当前准入，不恢复未知旧字节、不改旧receipt/重标旧failed成功。
- 新四报告supplemental，不是编译collector输入；写后再只读核输入及全部post产物不变，不因补文档重full。

## 未测与交付边界

Androidrunner/install/设备/systemSAF/Bindergrant/lifecycle/micprivate/实际大权重ZIP/ASR/latencyPSS/掉电/4K16K运行/emptycachehermetic/remoteCI/releaseR8均NOTRUN。hostclose后syntheticfault非vendorclose/FD释放；strictZIP32/ASCII/docs配方、非全包rollback/保守空间安全拒绝详review。

未stagecommitpush，52路径未来安全候选（37+baseline+双caller+三批12报告）只供父另授两阶段安全审/正常本地提交；本leaf不把工程pass冒APK交付或设备通过。
