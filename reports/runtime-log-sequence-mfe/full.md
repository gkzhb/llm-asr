# 日志序号极限修复 — 唯一 full 工程验证

任务 `llm-asr-mfe`，owner / 唯一 Beads writer `p1-steward`；验证 leaf `mfe-seq-plan` / `newapi/gpt-6.1-sol` high。父接受 fresh 同模型上下文隔离 `fork:false`（非跨家族）NO BLOCKING，receipt `5e4b5c6742992c262f65bdfb1f729e636b9f5f391069c7842d49f22fb48a9bf4`，无源修复。基点 `31ed79d1222cfc2e55d3c308f88e4399e75ccb23`；本次在已审 **dirty freeze** 下运行，后续提交为内容绑定，不重标为已测试新 commit 对象。

**唯一正式 full 已通过，仍未安全本地提交，不据此提前交付 APK或关闭 mfe/M4/M5/tc3。** 旧 summary/targeted/review 与诊断计划保持冻结，不回写旧 pending 证据。当前状态以本报告及当批 ignored handoff/closure 为准。

## 实际命令与终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

真实 exit `0`，耗时 `531.347s`；主 Gradle `174 actionable tasks: 174 executed`，共 `222` 条唯一 task 行（含无 action 聚合/NO-SOURCE/UP-TO-DATE/SKIPPED，不能冒 action 数）。pipeline Gradle0/tee0/DEXscan1；full closed log SHA `f9727c5f9287abf0a517d0142ad8f57daedb5a2a21ff2d5402a9334db48b7270`。防二次 launch 的 runner 只执行本次一次，未重跑 full/下载 prepare/改锁 metadata/降低 gate。缓存已有 Nix/Gradle strict offline，不等于全新机器 hermetic；单 worker/原 JVM 上限、Kotlin in-process/native 并发2保持。

## 实际测试与原 gate

- 真实 JUnit XML 精确匹配当前源码方法库存：core `34`、runtime `1`、host `7`，共 `42`，failure/error/skip 全0。原 runtimeLogCore 默认 main 的 `seq-noearly/seq-early/seq-overflow/seq-protection` 四组在 host stdout 和实际 core JUnit stdout均逐项存在，非只编译/方法库存。正式 full 跑 fixed 生产路径，旧 Store 三 red/rollback 证据来自冻结 targeted，不谎称 full 再运行旧源。
- 普通/gap/early/once、容量1/4/1000、dense-before-trim/drop、非法 append/clock 无副作用、所有旧 snapshot 字段/codec字节、真实 Worker+Persistence 连续重启、真正非法history/坏V1、sink Error身份/一次再入/确定性跨界并发由默认组实际执行。SequenceScope start/interrupt/cleanup 失败分支先前仅 source-level fresh审查，不冒新 runtime 注入。
- 全 host 混编/Java-Kotlin ABI/latch/PartRecovery、真实 module classpath 边界拒绝、模型3个可编译生产 mutants、ActivityWindow7个可编译 mutants/82checks、原 Worker fatal107checks、采集adapter故障、架构/格式/入口/三包checker普通与-O负例均保留并实际通过。checks数字不是JUnit方法数或设备证明。
- 实际隔离 fixture 五阶段 green0/red-payload/green1/red-cleanup/green2=`0/1/0/1/0`，7 JVM 方法、具体目标 AssertionError与fresh编译核验；隔离默认 AGP Lint green0→唯一 NewApi(API33/min29/精确fileline) red1→原bytes green1，工件/边及同生产validator classpath缺/多/替换拒绝与恢复均核验。
- checksum 原0→唯一 AGP8.6.1 JAR expected全零/actual cacheSHA目标拒绝1→恢复0，fresh HTML目标行、cache artifact与metadata恢复绑定；不把缺cache或通用帮助文字当red。

## Lint / JNI-native / 三包静态

生产 app/runtime Lint Error/Fatal均0，warning实际分布：android/app: {'Warning': 17}；android/asr-runtime: {'Warning': 10}。分类沿原获准 defer，未 baseline/抑制/关error；runtime同源warning重复与非重复差异由当批lint.json显式记录。host Lint实际执行：`False`，身份 resolve不冒Lint执行。

实际 javac生成JNI头、NDK arm64 object编译、Java native描述符与最终linked符号检查通过；578 MNN对象mode/bytes/SHA/inode/mtime保持，JNI复编/增量最终link，不是全nativeclean。最终DSO仍 `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`，原数学/配置/来源/ELF16KiB/静态runtime门槛保持；未做设备JNI或4K/16K运行验收。

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5218141 | `03c1344da938e5911f0cf29a8746b1b79fe7663c21fd2f3c97e3e97c0d614e38` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2586316 | `64cc4f8c23326c54c6451788da66f70710d1383ae50696230e5f6868b4bad645` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

产品版本 `0.6-debug` / code `6`，包 `org.llmasr.minimal`、min29/target35、arm64，唯一 RECORD_AUDIO、原组件/debug/Backup策略/精确assets、单combinedDSO保持。三包实际签名/zipalign/aapt2/全DEX再次解析并与formal receipt核齐，证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c` / v3保持；host/test无意外权限/native/assets/生产重复类或泄漏。当前14个仪器方法仅编译/parsedDEX库存，不是runner发现或执行。

两份独立formal域：产品 `android/app/build/verification/debug/run-53f728c6ad26488b9091b49032749a3b`；三包 `build/verification/android-test/run-fd7dca6f1b5d4b63b72a0c1c05516961/receipt.json`。实际包SHA/metadata/rawtoolreports/JUnit、current build/module/native inputs与两receipt逐SHA一致，精确清单见 [full-inputs.json](full-inputs.json)。

## 输入、备份与保护

当批动态库存 build `213` / module `10` / native `80` 逐SHA在before/launch/after/current/正式receipt一致，数量仅当次事实。native输入域包含Store源指纹，不等native数学或578对象变动；受审六候选modebytesSHA全过程保持。外部tool symlinkentry与resolved对象分域，Nix2.34.9与当前获准身份一致，其他真实SDK/NDK/JVM/Python/Kotlin工具身份前后相同。

开始前备份 `2491` 个已知生成文件（含原三APK/metadata、XML/报告/JNI/正式receipt），backup逐modebytesSHA保持；原保护 `8635` 个对象中 `8630` 个不变、`5` 个只在已备份且获准generated域变化；旧隔离证据 `2827` 个均保持，578对象保持。原closed文档/report/plan/review/diagnosis/build证据未覆盖；父活seq-supervision为明确supplemental例外。所有八候选、日志、输入、备份及closed evidence另由最终closure绑定，排活stdout/自身清单防空hash。

## 未运行及交付顺序

用户跳过设备仍 **not run**：Androidrunner发现/执行、ADB安装/模拟器、麦克风/私人文件、系统SAF/Binder/grant/IME生命周期、JNI-device/4K16K、模型权重计算/下载、远端CI/release/R8。主机/fake/编译/签名包静态不等设备验收。

无stage/commit/push/amend/二级委派/Beads写；本leaf全部命令真terminal及后处理封口后停写。父消费当批handoff/closure，再两阶段候选安全审与另授权本地提交；成功后才可交用户新APK验证入口。picker失败原因项仍未实施，不关闭mfe总任务/M4/M5/tc3。
