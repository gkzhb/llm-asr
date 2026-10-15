# P1-B 最终本地完整工程验证（未提交/未交付）

Task `llm-asr-76d.6`；owner/唯一Beads writer `p1-steward`；验证leaf `p1b-test`。2B fresh上下文隔离审查NO BLOCKING已由父接受，B1 CLOSED不重开；非跨家族独立审查。

## 唯一真实full

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

实际exit **0**，494.275s；Gradle **174 actionable tasks: 174 executed**。222条唯一任务行不是action数；无action聚合/NO-SOURCE/SKIPPED精确见full-evidence.json。pipeline Gradle0/tee0/DEXscan1。一次full，fixture行为driver只在此图执行一次，未额外手动重跑。

开始 `2026-10-15T14:58:29.615310+00:00`；完成 `2026-10-15T15:06:43.890956+00:00`。日志`.work/p1b-full/full.log`，SHA256 `05372398e30013e0439eae9ac989e6aa3ce09f9fb3c5f0804400daeaf0f034e3`。PID/start/启动driver SHA与普通执行收据`.work/p1b-full/full-launch.json`、`full-execution.json`，不冒attestation。自动通知启用但未送达当前会话；具体延迟证据后仅一次registry检查取得completed/exit0，并消费原PIDstart磁盘execution及terminal日志，未重启成功链，详`.work/p1b-full/terminal-consumption.json`。

Nix真实offline、空substituters/builders/maxjobs0且禁止锁更新；Gradle strict/offline、单worker、JVM1536m/JUnit512m/Kotlininprocess保持，隔离Lint child768m。缓存已准备不等新机器hermetic。先备份231生成文件（完整三APK/metadata、JUnit/receipts/raw与Lint等），仅清13已知项目classes/javac/test/APK目录，不clean native/cache；旧578显式MNN对象逐SHA保持。

## 实际测试与门槛

- 原core34/runtime1精确35 JVM JUnit、新host7分别非零且failure/error/skip全零，XML逐方法清单见full-junit.json；**不是42 Android测试**。
- 全部原host/43ABI/latch/PartRecovery生产mutant、model3可编译mutants、ActivityWindow82checks/7可编译mutants、capture12phase、module实际编译负例、JNI生成头/实际符号、native配置/provenance/ELF16KiB/static runtime、原产品签名/资产/RECORD_AUDIO-only gate保持并实际执行。
- 新三包checker、普通/-O策略与合成DEX负例、temp真实collector/格式库存/入口负例、host7/两测试包和产品assemble真实执行；无用UP-TO-DATE或0test代替required gate。
- 生产Lint实际执行app/runtime报告consumer；host Lint实际执行：**false**，根任务未接的hostLint不要求且不能以identity resolve冒执行。actual消费者见full-evidence.json。
  - `android/app`：Error/Fatal0，实际严重度{'Warning': 17}，warning类别{'UnusedAttribute': 3, 'GradleDependency': 1, 'ChromeOsAbiSupport': 1, 'DataExtractionRules': 1, 'UsableSpace': 1, 'MissingApplicationIcon': 1, 'SetTextI18n': 9}；warnings明确defer，不baseline/抑制/关error。
  - `android/asr-runtime`：Error/Fatal0，实际严重度{'Warning': 10}，warning类别{'UsableSpace': 1, 'SetTextI18n': 9}；warnings明确defer，不baseline/抑制/关error。
- 本次隔离Lint `.work/gradle-lint-negative/run-jhapq5pg`：实际default AGP绿0→唯一NewApi/API33/min29 Error红1→原bytes恢复绿0；工件/边身份先gate拒绝，普通任务调用同生产validator的files缺/多/替换拒绝及marker恢复。普通共享validator负例不是typed默认AGP classpath可变性试验。启动driver SHA `d07d69757b599dc50ebbe73d4ebe8f1fe8c69a6afef852b6db2c4b478d3e690f`。
- checksum `.work/gradle-m1/dependency-negative-y8mkvK`：0→唯一AGP目标1→恢复0；当次唯一HTML目标行expected全零、actual/cacheSHA `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1`，恢复metadata与主树exact，不将工具/网络失败算红。
- fixture行为 `.work/p1b-build/2b/behavior-u73b2dki`：精确7case/阶段，五阶段0/1/0/1/0，fresh compiler成功，两个red各唯一AssertionError（enginePayloadAndOrder / engine payload display、cleanupRetainsOwner / cleanup must wait for release），恢复green。父仅批准此本次新behavior子目录输出例外，旧2B文件不覆盖；证据已复制`.work/p1b-full/evidence/`。启动driver SHA `fbb4af0660042b8f2992bc4fb1ee92f126294af6ddc03bf927f4bf5bfb0ff89a`。

## 当前三包与输入身份

| 包 | 路径 | bytes / SHA256 |
|---|---|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5216237 / `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2584412 / `856536f13c8fb7a975663bb27f35f8ce6ca586f2ffee36907d3aca757f58ee7d` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 40158 / `770c1a7287730557afcf36aa4308d78f7b36744c102475285bbdd508b9442981` |

产品原包名org.llmasr.minimal/version0.6-debug/code6/min29/target35/arm64，唯一RECORD_AUDIO，单JNI DSO/精确资产保持。host org.llmasr.minimal.testhost/version1/p1b-testhost，instrumentation .test无独立version声明；两测试包无权限/native/assets，Backup false/标准runner-target/base与runner required，host非export无launcher。
三包原证书/v3单签 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。12个public concrete()V仪器方法只compiled-only，不等runner发现。实际SDK工具15次exit0/rawSHA与tool/optionalSHA绑定，无读key/新签名。

三包正式receipt `build/verification/android-test/run-94e0758d46b84858bf639fe527ae7de2/receipt.json`，原产品独立receipt `android/app/build/verification/debug/run-5e6c95b0a6f24b5291922db77512362c/result.json`；两者当前**209build**与冻结/current逐SHA一致，AGP metadata exact，本次真实assemble。包bytes可同P1A/B1历史，不因此否认本次重组装。native80与单DSO `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`、578MNN对象/准备source report保持；JNI重编与增量最终链接，不冒全clean native。

旧2Bfreeze `204f12b051accccf0f2dd3780c45cbaf4d9dbfd76c0ae6e811e868aecf32405d` 的209/80和151旧2A-B1-review/358P1证据均保持；另完整保护769旧文件。docs/status/本报告与计划仅supplemental，最终允许变更单列，不冒旧2Bsupplemental全部current或重写旧freeze。新freeze/audit见`.work/p1b-full/final-input-freeze.json`、`final-audit.json`。

## 失败、局限与下一步

本次正式full与独立证据核验成功；未为凑绿修改源码/版本pin/locksmetadata/gate、未补下载、未抑制Lint。开工仅固定driver输出目录的授权冲突经父最小追加授权解除，旧成功链未重启。历史2A/B1/2B中间失败原证据保留，本次报告不改写。

**Android runner发现/执行/sentinel、provider/SAF/Binder/grant/InputConnection、真实Activity/IME系统生命周期、JNI-device/AudioRecord/mic、4K/16K设备、模型权重计算/下载、远端CI、release/R8/newsignature均not run。** 无模拟器/设备/ADB启动连接或install/connected；本地fake与compiled-only不等设备验收。

本leaf不stage/commit/push/amend/Beads写或委派，HEAD保持a73f7f3/index空；未提交前不交付APK，M5/路线不关闭。单一下一步：父消费报告和新freeze/audit，交reviewagent做最后限定文件安全审查及另授本地提交；提交成功后再做committed-current绑定并由父报告。
