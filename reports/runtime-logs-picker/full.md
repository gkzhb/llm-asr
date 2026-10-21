# Picker 失败原因稳定修复 — 唯一 full 工程验证

任务 `llm-asr-mfe`，owner/唯一 Beads writer `p1-steward`，验证 leaf `mfe-picker-build` / `newapi/gpt-6.1-sol` high。基点 `2bb5e152591c3195e38590991e769aab73010019`。父接受 fresh 同模型上下文隔离 `fork:false`（非跨家族独立）R1 CLOSED/NO BLOCKING，receipt `921941f9b99fdc8a28c3fdf02b11bf3bc469cb767acf4b7be5659469b980fa12`；显著 Linux 测试 host 债务已接受，N1 P3 合成 helper 一致性明确非阻塞 defer，不追加加固。旧九候选、targeted/build/R1/review封口冻结不改；本次只新增本报告和输入报告。

**本轮唯一正式 full 已通过，但仍未安全本地提交，不据此提前交付 APK 或关闭 mfe/M4/M5/tc3/ap5。** full在已审 dirty freeze内容上运行，未来提交只能内容绑定，不重标为测试过新 commit 对象。用户跳过设备仍 not run。

## 一次真实命令及终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

真实 exit `0`，`569.808s`；主 Gradle `174 actionable tasks: 174 executed`，主图 `222` 条唯一 task行（含无action聚合/NO-SOURCE/UP-TO-DATE/SKIPPED，不冒action数），pipeline Gradle0/tee0/DEXscan1。完整关闭日志 SHA `b648bac31fed170839ec4f8321f7d0cf5d9c63e86124da4e18c07a8c5f85ea3f`。run-full防第二launch，本批正式full恰一次，不重跑成功链、不下载prepare/改锁metadata/gate。Nix cache-only+Gradle strict/offline仅已有cache复验，非新机器hermetic。原单worker/JVM上限、Kotlin in-process/native并发2保持。

## 正式host新路径与完整门槛

- **新 adapter 在本次正式 `verifyHost → test-minimal-apk.sh` 默认路径真实执行**：`PASS owned runner timeout/KILL/parent-exit/interruption/refusal/restoration`恰1、实际 LogsActivity consumer production/restored各123checks；render-priority/retry-clear/pause-clear三mutants各freshcompile0→准确外部AssertionError1。adapter最终SHA `a1a937ed199094de4e68cf61e986e2e71f53d907302ad744cd5fb9be1713c03d`与full启动输入一致。完整full默认普通adapter，不谎称此full额外重复-O；普通/-O已有本批冻结targeted证据。
- 合成故障同runner覆盖TERM抵抗需KILL、父先退后代新pgrp/持输出、注入中断身份、未确认退出拒删/保目录及证据、恢复清理、已确认失败保诊断。拒删为exit确认probe注入（真实子进程已退出），不是D-state卡死实验。Linux `/proc` PID-start/pidfd/subreaper受控工具前提，非恶意session逃逸沙箱；实际多信号/SIGTERM/proc/launch/evidence-write/PIDreuse对抗故障未运行，不因默认组PASS扩证。
- 真实当前源码JUnit方法库存与fresh XML逐一相符：core `34`、runtime `1`、host `7`，总 `42`，failure/error/skip全0。增强LogExportTest完整main108checks、旧WRITING/close/Error/阻塞owner保持；原Worker fatal/teardown、model维护日志、Store序号四组均经host及coreJUnit默认body实际执行，不把checks冒方法数。
- 所有原host混编/Java-Kotlin ABI/latch/PartRecovery真实生产mutant、模型3可编译生产mutants、ActivityWindow7mutants/82checks、capture适配故障、module真实classpath边界、架构/格式/入口/包checker普通/-O、JNI实际生成头/NDKobject/linked符号、native来源/数学配置/ELF16KiB/静态runtime/签名权限assets全DEX gate均保留并实际通过。
- 隔离fixture本次五阶段0/1/0/1/0，确切断言/7JVM/fresh编译核验；默认AGP Lint green0→唯一NewApi(API33/min29/精确fileline) red1→原bytes恢复0，工件/边及同生产validator classpath缺/多/替换拒绝/恢复均核验。实际checksum原0→唯一AGP8.6.1 jar expected全零/actual cacheSHA目标拒绝1→恢复0，fresh HTML/metadata绑定；缺cache或通用文字不作目标红。

## Lint、native与实际三包

生产app/runtime Lint Error/Fatal均0；本次warning分布 android/app: {'Warning': 17}；android/asr-runtime: {'Warning': 10}，原分类defer未baseline/抑制/关error。runtime同源warning重复与非重复差异显式记录lint.json。host Lint真正执行 `False`；身份resolve不是Lint运行。

578 MNN对象mode/bytes/SHA/inode/mtime保持，仅JNI复编/增量最终链接，不是全nativeclean。最终DSO `d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`，原配置/来源/ELF16KiB/静态runtime保持；4K/16K与JNI-device未测。

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5218237 | `5e3bd45fa2db2220fc245b668dd337613b2bd99d7cce31d199f6074275bbbdb5` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2586316 | `64cc4f8c23326c54c6451788da66f70710d1383ae50696230e5f6868b4bad645` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

产品 `org.llmasr.minimal` / `0.6-debug` / code `6`、min29/target35/arm64，唯一RECORD_AUDIO、原组件/debug/Backup策略/精确assets/单combinedDSO保持。三包实际签名/zipalign/aapt2/全部DEX重解析与formalreceipt相符，证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`及v3保持；host/test无额外权限/native/assets/产品重复类泄漏。当前14仪器方法仅编译/parsedDEX库存，**不是Android runner发现或执行**。

两份当次独立正式证据：产品 `android/app/build/verification/debug/run-df59f5c602aa4e31b875bde8052319ef`；三包 `build/verification/android-test/run-d787dd684bac416cad869355f91628f3/receipt.json`。实际APK/metadata/rawtoolreports/JUnit与动态输入逐SHA核齐，完整来源见[full-inputs.json](full-inputs.json)。

## 当前输入、备份与保护

动态build `214` / module `10` / native `80`，before/launch/after/current及两formalreceipt逐SHA一致；计数为本次事实非永久门槛。native输入包含Activity指纹，但native578对象/数学无变化。九受审候选modebytesSHA不变，工具当前PATH entry/lstat/link和resolved targets独立记录，前后相同且符合获准身份，不抄历史symlink分类。

开始前备份 `1959` 个已知generated文件（旧三APK/metadata/两formalreceipt、XML/报告/JNI/classes等），副本逐modebytesSHA保持；`3760`旧保护对象中`3760`不变，`0`保护对象属于获准generated变化；独立备份域有`31`个正常generated文件变更（其余`1928`个字节保持），全部原件副本保留；`3099`旧隔离证据保持，578对象内容与元数据保持。未clean缓存/模型、不复制key/私人文件；旧build/review/R1及研究报告保持，父supervision supplemental明确例外。最终11候选/closed证据/保护delta/无ownedpending由本次ignored closure绑定，不自采活stdout或自身清单。

## 未运行与后续

无stage/commit/push/amend/二级委派/Beads写；完整命令真实terminal及post封口后停写，父消费再两阶段只读安全审与另精确本地提交授权，提交成功父核后才交用户验证APK。

Androidrunner发现/执行、设备ADB安装/模拟器、麦克风/私人文件、系统picker/SAF/Binder/grant/IME真实生命周期、JNI-device/4K16K、模型运算/下载、远端CI/releaseR8全部not run。主机fake/编译/签名包静态不是设备验收。mfe总任务三项最终关闭由父核，不连带关闭M4/M5/tc3/ap5。
