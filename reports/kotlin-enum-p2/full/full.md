# P2 双日志枚举唯一 full 工程验证

任务 llm-asr-ap5；leaf p2enum-test；owner/唯一Beads writer父p1-steward。main/base `a5204512cc779ed8e85116fdf7ea4453abf0b9ce`，无worktree。R1 review5 CLOSED / F1 review6 CLOSED 已由父接受，本轮不重开产品审；原dev/review冻结不改。当前生产12 Kotlin+51 Java，非Java0，不关闭ap5。

## 真实命令终态与监督缺口

唯一完整命令：
```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh full --offline --rerun-tasks
```

exit **0**；534.415s，2026-10-16T10:36:46.133813+00:00 → 2026-10-16T10:45:40.549410+00:00。**174 actionable tasks 全部 executed**；222条唯一主task行包括聚合/NO-SOURCE/UP-TO-DATE/SKIPPED，不把222当actions。Gradle/tee/DEXscan=**0/0/1**，完整实际Gradle日志扫描无DEX诊断命中。outer完整log SHA `8ae5048bfe5ad66eaa36e5b1c20181752b99acff62f714f69e70ac076233a0c5`；实际pipeline日志及SHA见supplement-checks.json。

原bg任务b0189748e默认自动通知未在本会话接到；中途父曾核Java还活，无exit当时确在执行。后用户明确指出无bg任务，一次exact registry查unknown；此时完整execution/exit0/log已落，原runner1444473/command1444474 PID-start消失。由disk真实终态恢复消费，**不是伪造自动通知，也未重跑full**。之前仍称等待是状态滞后，已纠正。preflight辅助脚本一次SyntaxError单列，修后preflight0；不是产品full失败/行为red。

## 实际 gate / JUnit / 行为

- 所有原host/ABI/latch/PartRecovery/模型3可编译mutants/ActivityWindow7mutants与82checks、capture12phase、module真实正负编译、源架构/入口/格式/权限负例保留并实际执行。Java Kotlin ABI **322 checks**，真实生产日志persistence/codec格式门槛通过。
- 本次唯一真实enum链10记录：5fresh混编exit0，Source/Kind两指定`java.lang.AssertionError: ... inventory/order mismatch`各exit1，逐原SHA恢复并重编green0。R1实际driver/base-wrapper生命周期13case通过，含无法确认停止保tree/source、actualmain观察及finally身份回收。完整raw logs/results见evidence。
- 真实XML逐名核core34/runtime1/test-host7，failure/error/skip全部0；动态从当前XML/方法库存核，不为凑绿改数量。14仪器public concrete `()V`方法只编译/parsed全部DEX，不是Androidrunner发现或执行。
- full内独立 `.work/p1b-build/2b/behavior-yj6aaz3a`：当前collector94复制输入逐SHA与current一致（含生产63=12kt51Java），五阶段0/1/0/1/0，各7JUnit/36actions和真实Kotlin/Javafresh编译；payload/cleanup精确AssertionError且恢复SHA，非编译故障冒red。
- 独立Lint `.work/gradle-lint-negative/run-vt1z0yi9`：artifact/edge身份及同production validator files缺/多/替换真实目标拒绝/恢复；default AGP API33/min29 `NewApi`精确file/line Error拒绝→原bytes恢复。checksum `.work/gradle-m1/dependency-negative-ucX7Lt`：0→1→0，唯一AGP8.6.1 jar目标HTML expected全零/actual cacheSHA精确核，不用网络/工具失败冒red。

## Lint与warning

生产app Error/Fatal0、17Warning：UnusedAttribute3、GradleDependency1、ChromeOsAbiSupport1、DataExtractionRules1、UsableSpace1、MissingApplicationIcon1、SetTextI18n9。runtime Error/Fatal0、10Warning（UsableSpace1、SetTextI18n9，全部同源重复；独立比较非重复数0）。原warning明确defer，未baseline/抑制/关error；host Lint **未执行**，identityresolve不可冒执行。实际identity gate13/consumer8均66artifacts/171edges。另有aapt2实验提示、Java/deprecated标准runner与CMake minimum-policy弃用warning保留；DEX scan无命中不等所有warning不存在。

## 当前输入/正式receipt/三包

动态212 build /10 module /80 native /63 source 在before→full正式三包receipt→独立产品receipt→after/current逐库存SHA一致；源码/锁/verification metadata/flake不改。实际Nix JDK/Kotlin/Python/bash、SDKtools/optional/android.jar/NDKcompiler/readelf/CMake/Ninja字节前后核，详tools-before/full-inputs。缓存已准备的strict/offline通过，不是空缓存或hermetic复现。

正式三包receipt `build/verification/android-test/run-0e2133b3376549d8a87a1a8dad2c215f/receipt.json`；独立产品receipt `android/app/build/verification/debug/run-a25045bf4de44130a92ce742fa66ec88/result.json`。15次SDK命令raw/exit/SHA与当前APK、metadata、全DEX class_def/14方法重新解析一致；不是引用旧APK。全部生产12Kotlin实际class_def唯一（含新两enum），不是拿source数量代DEX。正式checker验证精确权限/签名/native/assets；原证书/v3 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。

| 包 | 当前路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5217189 | `92c3603ba89cb1045c04a663c35cd3d13a4aea53c8674be3a02251dc77de4256` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2585364 | `316cbbcf9fde9043872eefbf368347b2a6eac6d3327b6daadf18217a514ecd11` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

product org.llmasr.minimal /0.6-debug/code6/min29target35/arm64，RECORD_AUDIO-only、原debug组件/IME/back策略、单JNI DSO和原assets精确保持。host p1b-testhost/code1，test标准InstrumentationTestRunner target host；两测试包无权限/native/assets/意外组件/launcher。testAPK字节同旧不等未重编，引用本次实际task/receipt。

## JNI/native与资源

本次NDK桥真实重编生成头/实际JNI符号核通过；CMake实际`[1/1] Generating artifact/...`增量最终链接，native DSO SHA `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`保持。**578 MNN对象实际逐SHA及mode/inode/mtime全保持**；source/config/provenance/ELF16KiB/static-runtime gate通过，不冒全native clean或设备运行。未clean任何目录、未改输出策略，保Nix/Gradle/native缓存。

5秒有界107采样：最多5相关JVM，同时JVM RSS和最多3087916KiB，相关树3131340KiB，host MemAvailable最低9695048KiB。RSS共享页可能重复计、短命/未见detached进程可漏，非PSS/全机峰值。worker1/Gradle1536m/JUnit512m/Kotlin inprocess/nativeparallel2/childLint768m不改，无并行第二full。

## 保护/候选/边界

先将1237已知生成证据/旧APK/JNI输出ignored备份并逐SHA核；终点1212原位置字节保持、**25原位置expected generated delta**，原字节全部仍在baseline。旧32包/receipt证据30原位保持、2旧APK由备份保持，不谎称全部原位不变。原513候选和两Java删除不漂移，619当前候选/review/证据全核；783旧隔离证据逐SHA保持；26excluded仅lstat未读正文或计算SHA。旧2561大历史沿已锚定审查及map+16关键锚抽核，不机械重复制/不冒本次全重hash。公开仅必要非敏感文本/hash/完整日志XML，不含APK/DEX/ELF/classes/模型音频/private/key/session/cache。

本full新增plan/report为supplemental，最终candidate manifest由513基追加，产品freeze不改。当前无stage/commit/Beads写/二级委派，未宣称APK交付。状态与最终closure/handoff由本轮独立文件给出；旧冻结报告pending历史不改。

## NOT RUN / 唯一下一步

- Android runner discovery/execution and14 instrumented method behavioral red-green/sentinel
- device/ADB/emulator/SAF Binder grants/InputConnection/real Activity IME Service lifecycle
- device JNI/4KiB16KiB runtime/microphone/AudioRecord/private inputs/model computation
- download/prepare/dependency changes/remote CI/release R8/new signing/push
- stage/commit/Beads writes/delegation; ap5 not Java0 remains open

父消费本次full/closure→最后候选只读安全审→另授权reviewagent逐文件stage/cached完整安全审→正常新本地commit。**未commit不宣称可验证APK已交付；工程通过不等设备验收。**
