# taw 新正式完整工程验证

## 1. 唯一命令与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- BG `b96238094`：自动 `completed / exit0` 已消费；无等待轮询、无相关运行残留。
- 真实执行 3188.692s；Gradle `BUILD SUCCESSFUL in 53m 6s`。
- 184 actionable tasks / 184 executed；230 task行、229唯一task（core:test输出分段重复一行），不把聚合/NO-SOURCE/UP-TO-DATE/SKIPPED冒充actions。
- pipeline Gradle0 / tee0 / DEX scan1（无匹配）；无D8/R8/metadata异常。
- 完整日志 `.work/taw-full-rerun/full.log`，71305bytes，SHA-256 `c6003946c8b4c1a569452a84199b458597ceb80f9e4e1b4856ccd0b3294611a0`。
- 原Gradle日志 `.work/verify-local/full-ip4Jwn/build.log`；execution、pre/post、cache audit、closure保留在本轮独占work。
- 运行准入由本leaf环境/live Herdr/catalog交叉核newapi/gpt-6.1-sol/high。宿主BG过滤PI_PROVIDER/MODEL，execution中null如实保留，不作为route依据。

## 2. cache恢复与checksum后可用性

父已接受 `.work/taw-cache-prepare/handoff.md` SHA32846eb4ab5a668ac9fd35a55bcad29cb49bb6cc7e0ddc6c60bb8207c59a829a。仅三已批准POM正常Gradle恢复29756B，原两JAR不变；本leaf不下载、不改锁/metadata/库/仓库/GC policy。

本次pre/post只读audit五工件批准SHA、size、mode、mtime完全一致。post在正式退出及checksum三个child清理之后完成，结合后续真实runtime Lint/JUnit消费成功，不再有availability错误；不是逐文件连续监控，也不追溯宣称从未瞬时消失。checksum子daemon2857905/2858018/2858123完整日志SHA、清理时间及post时间在artifacts/cache。日志有正常VFS清理，无新的LRU/file-deletion目标行；不能把日志无行提升成完整GC审计。

| 工件 | bytes | 批准/实际 SHA-256 |
|---|---:|---|
| `junit-4.13.2.jar` | 384581 | `8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3` |
| `junit-4.13.2.pom` | 27018 | `569b6977ee4603c965c1c46c3058fa6e969291b0160eb6964dd092cd89eadd94` |
| `hamcrest-core-1.3.jar` | 45024 | `66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9` |
| `hamcrest-core-1.3.pom` | 766 | `fde386a7905173a1b103de6ab820727584b50d0e32282e2797787c20a64ffa93` |
| `hamcrest-parent-1.3.pom` | 1972 | `6d535f94efb663bdb682c9f27a50335394688009642ba7a9677504bc1be4129b` |

## 3. 实际覆盖

| 层 | 当次事实与边界 |
|---|---|
| 完整host | 原脚本全部运行，ABI/null/monitor、latch/owner、actual module compile负例、production compiled mutants/restores、model/proof/resident、录音12fake相位、provider/log/worker/error/cleanup及旧default/compat/IME return保持；host fakes不冒Android |
| 新IME | readiness与first-use普通/-O各7compile7JVM；actual start boundary普通/-O各5compile11JVM，真实编译intent/resident mutant精确AssertionError1后恢复0；owned child退出与输出SHA验证 |
| 旧runtime | 正式default15mixed compile路线、30compiler命令、15JVM，七mutants非subset，compile0/精确目标red1/恢复0；原270/45/7200预算不变 |
| SDK/JNI/native | core/runtime/app真实Java/Kotlin SDK编译，NDK两真实JNI object与生成头/符号检查；578MNN对象保持，真实JNI复编与增量final link/独立native/ELF门槛执行 |
| JUnit | 当前core34/runtime1/host7合42，XML均fresh，failure/error/skipped全0；runner exact discovery名单为JVM，不是Android |
| 生产Lint | app/runtime新XML均Error0/Fatal0；identity gate13个记录及consumer照实际日志，warnings逐类披露；test-host Lint只identity resolve不冒执行 |
| isolatedLint | 工件/边负例及同生产validator closure的实际Configuration files缺/多/替换拒绝，普通marker恢复；default AGP green0/NewApi red1/green1，具体API33/min29目标file/line，非typed默认classpath可变性声称 |
| fixture | 单次正式driver五阶段0/1/0/1/0，每阶段freshKotlin编译+exact7JUnit；payload/cleanup合法可编译生产fixture mutants命中对应外部AssertionError；owned exit确认 |
| checksum | 隔离AGP目标原0→坏hash1→原metadata恢复0，expected全0/actual9697733a…0051c1，fresh HTML目标诊断；主metadata不变 |
| 包 | 三APK真实compile/package/sign/zipalign/Manifest/权限/全DEX isolation14仪器method静态检查，加独立产品最终native/assets/包/actual JNI-owner checker；两receipt当前inputs/module/tools逐SHA绑定 |

## 4. Fresh JUnit 与 Lint

| JUnit XML | tests | failure/error/skip | SHA-256 |
|---|---:|---|---|
| `core/build/test-results/test/TEST-CoreRegressionJUnitTest.xml` | 34 | 0/0/0 | `af2f86e9284e198a613ffbbe83a2a29385b3ae700d4b424cabc03b1ff97063f4` |
| `android/asr-runtime/build/test-results/testDebugUnitTest/TEST-RuntimeRegressionJUnitTest.xml` | 1 | 0/0/0 | `11902ff80c575f7a96a55fa9fcee781f3f16e9974972081a8569294b0d84a9d7` |
| `android/test-host/build/test-results/testDebugUnitTest/TEST-org.llmasr.minimal.testhost.FixtureContractTest.xml` | 7 | 0/0/0 | `0d06363018c2d51de4eee7f9dd1cf8d868ff16664bb8b996e31f9620a3eee009` |

| Production Lint | Error/Fatal | Warning | 分类 |
|---|---|---:|---|
| app | 0/0 | 24 | UnusedAttribute=3; GradleDependency=1; ChromeOsAbiSupport=1; DataExtractionRules=1; UsableSpace=2; MissingApplicationIcon=1; SetTextI18n=15 |
| runtime | 0/0 | 17 | UsableSpace=2; SetTextI18n=15 |

这些为现有warning类别；未加baseline/抑制/关error。runtime的SetTextI18n15+UsableSpace2与app同源重复，不冒无warning；warning继续分类披露/defer，非新功能/权限失败。CMake最低版本deprecated提示仍保留，非DEX warning。

## 5. 当次三APK（待提交，不交付）

| APK | 身份/版本 | bytes | SHA-256 |
|---|---|---:|---|
| `android/app/build/outputs/apk/debug/app-debug.apk` | `org.llmasr.minimal` 0.6-debug/6 | 5372553 | `5574e0afb81e63932eeb4a1d334bf60cd4ed068dbeffcb8c3bfcbb3e443a11be` |
| `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | `org.llmasr.minimal.testhost` p1b-testhost/1 | 2701228 | `1f612142a37f94e7b25180d3857ac7d8c6db73c646b1ce0d1289b0599a223854` |
| `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | `org.llmasr.minimal.testhost.test` Manifest无version/未声明 | 66382 | `4757d34d7d6ca604ecb335fb02431fe32849a3d0fc4ca1793f1c17d0d6e674d8` |

三包均v3，证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`；test manifest无version，AGP metadata code0/name空，不能填host版本。metadata完整path/value/hash、tool raw hashes、payloadSHA、parsed DEX class数量/14方法见artifacts.json。

新三包receipt `build/verification/android-test/run-fcd3a8d4a8d04cb1864cbb42ee41227a/receipt.json` SHA`5ff9e61a8eeeb1a159761fb1ccbaaf57b1ce657f0b85b1f75ec9c4af5a6f6975`；独立产品receipt `android/app/build/verification/debug/run-c76ffef58bea463c9e4d4b0d3f48e63b/result.json` SHA`088684ba1477bfb1eb6148edd30661ffa963f4eff0b42f6b82b34388665b7b53`。二者均本次新run，非旧失败receipt复用。三APK bytes与pre恰相同，但全部fresh任务/receipt证明实际重新执行。

## 6. Native与输入封口

native SHA `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481` 与baseline一致；578原MNN object逐SHA保持。JNI两个object SHA `6186abda6167c1a0ec45bff578e36da749e73fdd5650307293bfa0ff5bfbf9ae`，真实重编且native link实际执行，非native clean/空缓存复现。16KiB ELF静态/RELRO/nonexec-stack/static C++/needed白名单通过，不等设备4K16K运行。

19受审候选+baseline精确保持；240build/10module/84native/3external逐SHApre/post全等，包含untracked boundary test；1986tracked/762原excluded字节和mode不变。84native collector含app源，不能拿旧31保护误称全产品无变化。旧失败work及四报告SHA全同。正常生成新增receipt26文件，原1855已库存输出字节全保持；允许生成输出不是提交候选。新四报告仅supplemental，写后再collector/产物核等，不再无理由full。

## 7. 验收边界/下一步

R1/R2 fresh CLOSED由父已接受，本leaf验证不冒重审。P3 late各资格独立测试gap按父接受非阻塞defer，不改源/测试扩scope。缓存恢复不代表CVE/PGP/SBOM/legal-release重新审计；cache-only成功不等全新cache/hermetic。

Android runner发现/执行、install/connected/设备、系统IME/SAF/Binder/grant、麦克风/private/模型计算/ASR/latency/PSS、4KiB16KiB实际运行、远端CI/release/R8均NOT RUN。host合成小文件/假平台不替代设备验收。

未stage/commit/push。工程full通过不等已交付APK；构建在main3899242 dirty freeze下，不宣称测试过新commit对象。父消费当前封口后另派两阶段安全清单审及本地提交，成功后再交付。
