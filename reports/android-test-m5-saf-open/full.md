# M5 SAF open 正式统一full（未提交/未交付）

任务`llm-asr-76d.6`，父/唯一Beads writer `p1-steward`，verification leaf `m5saf-test`。R1 fresh同sol上下文隔离定点CLOSED已由父接受，非跨家族独立审；其余已审范围不重开。

## 唯一正式命令

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

实际exit **0**，494.175s；**174 actionable tasks: 174 executed**，222唯一主task行（聚合/NO-SOURCE/SKIPPED不等action）。pipeline Gradle0/tee0/DEXscan1；一次full，无重复启动成功链或额外fixture行为driver。
开始`2026-10-15T17:34:44.896456+00:00`；结束`2026-10-15T17:42:59.071590+00:00`。完整日志`.work/m5saf-full/full.log`，SHA `9c22560d5d38f49a86fe0bcd8900786c1447851fb60ecbd4a3d3abaf16a1f985`；PID/start/driverSHA与磁盘终态见launch/execution JSON，通知消费见terminal-consumption.json。普通receipt非attestation。
备份289份生成身份/三包/receipt/reports及必要小文件，仅清13个既有generated dirs。578 MNN对象缓存逐SHA保持；JNI重新编译与增量最终链接，不是全native clean、空cache或机器外hermetic复现。Nix真实cache-only/offline、Gradle strict/offline，不下载或写锁metadata。

## 实际验证与目标负例

- 原core34/runtime1精确35 JVM、新host7 JVM，全部failure/error/skip0；原7和原35逐名XML见full-junit.json。**不能称35+7 Android测试。**
- 原host/Java-Kotlin ABI43/latch/PartRecovery生产变异、model3可编译mutants、ActivityWindow82checks/7mutants、capture12phase、真实module classpath编译边界、JNI生成头/对象/实际linked符号、native578配置/ELF16KiB/static-runtime、原签名/权限/native/assets gate均实际执行。
- 新三包正式checker及普通/-O策略/DEX负例、动态collector/模块库存负例、host7/三包assemble及唯一fixture行为driver均实际执行，required tasks见full-evidence.json。14仪器方法只实际编译/全DEX静态库存，不是runner发现/执行。
- host Lint实际执行 **False**；identity resolve不能当host Lint运行。app/runtime真实Lint XML均Error/Fatal0：
  - `android/app`：{'Warning': 17}；warning IDs {'UnusedAttribute': 3, 'GradleDependency': 1, 'ChromeOsAbiSupport': 1, 'DataExtractionRules': 1, 'UsableSpace': 1, 'MissingApplicationIcon': 1, 'SetTextI18n': 9}。同源重复warning明确defer，未baseline/抑制/关闭error。
  - `android/asr-runtime`：{'Warning': 10}；warning IDs {'UsableSpace': 1, 'SetTextI18n': 9}。同源重复warning明确defer，未baseline/抑制/关闭error。
- 隔离真实Lint `.work/gradle-lint-negative/run-m8y1lcao`：default AGP 0→唯一NewApi/API33/min29/file-line目标Error1→原bytes恢复0；artifact/edge身份先gate拒绝，普通任务经同生产validator对files缺/多/替换拒绝，marker恢复0。后者不是typed/default AGP classpath可变性试验。
- checksum `.work/gradle-m1/dependency-negative-wKmvzp`：原0→唯一AGP目标1→恢复0；当次唯一HTML目标行expected全零、actual等cacheSHA `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1`，metadata原bytes恢复；不把缺cache或工具/网络错误算红。
- fixture `.work/p1b-build/2b/behavior-3qvy_1wn`：精确7case每阶段，fresh compiler与五阶段0/1/0/1/0；两个red各唯一AssertionError（enginePayloadAndOrder/engine payload display，cleanupRetainsOwner/cleanup must wait for release），随后恢复。仅本次新随机behavior目录获旧owner输出例外，原目录内容不覆盖；新目录原字节证据复制`.work/m5saf-full/evidence/`并核旧保护SHA。启动driverSHA均保存。

## 当次三包与current绑定

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5216237 | `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2584412 | `856536f13c8fb7a975663bb27f35f8ce6ca586f2ffee36907d3aca757f58ee7d` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

当次三包receipt `build/verification/android-test/run-89e47fb33e5547a0b63a13fd1de31b69/receipt.json`，独立产品receipt `android/app/build/verification/debug/run-58447c85ca574d19b9d5c20d664a2908/result.json`；current=本full冻结=两receipt **209build/10module80native**，四工程delta均收录，suite受审SHA `e9627ca45fc6c3a092c35ff7bfe8ca3fd194238f81f03973472685ad7b1c8a92`。实际包/metadata/全部DEX/classes/14精确方法逐项再解析与receipt一致；不继承旧R1包hash，不以metadata相同否认实际重建。
产品仍org.llmasr.minimal/0.6-debug/code6/min29 target35/arm64，唯一RECORD_AUDIO、单DSO和原精确assets；host org.llmasr.minimal.testhost/p1b-testhost/code1，test .test/标准InstrumentationTestRunner target host；两测试包无权限/native/assets，host非export无launcher。三包原证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c` /v3，15 SDK工具调用exit0及当前工具/optional输入SHA核齐，没有变更签名身份。
保护2209旧文件SHA/bytes/mode/mtime/inode保持（含原20excluded hash-only、旧P1B full与本轮targeted/R1/review及旧reports）。仅status当前导航授权替换且旧字节/hash备份，docs仅追加；旧build/input/R1报告不改。报告/docs/plan是supplemental，不冒build inputs一套。

## 局限、警告与后续

上述主机/静态工程门槛通过，不等设备或SAF行为验收。同进程ContentProvider/wrap不是系统DocumentsProvider/Binder/grant，cursor/PFD计数不是InputStream关闭故障运行证明。原R1静态预期错误已fresh CLOSED，不宣称Android redgreen。既有aapt2 experimental/deprecated/CMake警告保留，具体完整日志及Lint分类可核；无生产诊断自动扩修。

以下全部 **not run**：SAF assertion Android red/green；14 instrumentation runner discovery/execution/failure sentinel；system DocumentsProvider/Binder/grant/revocation/InputConnection；real Activity/IME/Service lifecycle；JNI-device/AudioRecord/microphone/private files；4KiB/16KiB device runtime；model weights computation/download；remote CI/release/R8/new signing。

终态通知：自动通知消费前未到；界面错误后用户Continue恢复，完整disk exit0/execution/log及launcher/runner/command PID均已消失。仅一次exact bg registry恢复completed0，不循环poll、不伪造通知、不重复full。最终身份以本文件所列当前磁盘execution/log SHA与PID-start为准，launcher PID与实际Python runner PID分别记录。。无设备/ADB/模拟器/mic/private/权重/远端CI访问。源码/锁metadata/版本pin/gate没有改动，不补下载凑绿。

**尚未stage/commit/push/amend，HEAD保持add5d2c、index空；不宣称本轮APK交付、不关闭M5。** 父消费本full/handoff/freeze/audit后，由另派reviewagent最后清单安全审查及另阶段本地提交，提交成功并绑定committed-current后才由父报告。
