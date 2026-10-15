# M5 SAF open R1：最小测试预期修复与新 targeted 证据

任务 `llm-asr-76d.6`；base/current HEAD `add5d2c854ac8d2980b2bf13d92ad5bc373ad477`；leaf `m5saf-build`，父唯一Beads writer。**R1代码已最小修复并获取新编译证据，仍待父fresh定点复核，不自行标CLOSED、不full/提交/交付APK或关闭M5。**

## 旧错误与静态路径纠正

fresh审查 `.work/m5saf-review/final-review.md` 的 R1/P2 及父disposition已接受。原suite断言 `failed.fileResults.isEmpty()` 错误；原build findings“failedFiles/fileResults应为空”中后者同样错误，历史原文保留，不覆盖旧seal/freeze/log/receipt/report为修后绿。

实际 `ModelRepository.java:382–384` 在 `source.open` 前调用 `progress.fileResult(model.bin,PENDING)`；controller progress handler与 `publishFileResult` 将其复制进snapshot map。其后provider SecurityException经 `ModelProviderBoundary` 成为固定安全FILE_FAILURE裸IOException；controller terminal只在ModelFileException分支写失败文件，并不清掉已有计划历史。因此正确结果是 **failedFiles空、fileResults恰一项model.bin且status PENDING**，inspect仍应保留该map。

suite仅增加必要 `ModelReports` import、纠正一行注释与精确keySet/status断言；保留failedFiles空、inspect map相等、same controller retry新op/READY/实际字节SHA和所有取消断言。`.work/m5saf-build/r1-fix/static-proof.json` 与 `suite-r1.diff` 证明相对修前suite恰此最小delta，其他方法与流程逐字保持；未改生产凑绿。该依据是**静态确定路径，不是运行Android red/green**。

## 新阶段实际验证

- checker/input普通与 `-O` 四命令均exit0（`python3 -B`及 `python3 -B -O tests/android_test_{apk,inputs}_test.py`），日志与SHA在新work `checker-evidence.json`。既有新增方法missing/duplicate/DEX flags/signature目标负例保持；不能当R1行为运行证据。
- `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh android-test-build --offline --rerun-tasks`：exit0；118 actionable tasks: 118 executed。
- 串行 `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh format --offline`：exit0；1 actionable task: 1 executed。
- 原7 FixtureContractTest JVM名单精确、failure/error/skip全0；总14仪器方法（新增2）仅Java编译/全部DEX静态库存。实际三包checker/15工具exit0、签名v3/原证书/权限/组件/native/assets/target/隔离门槛保持。
- 统一入口触发原产品native/prepareInputs链，JNI对象/linked符号/578对象/配置/ELF16KiB/static-runtime门槛通过；不是全native clean、不是新依赖prepare或full，不执行旧fixture行为driver。既有aapt2 experimental、deprecated API与CMake compatibility warnings保留，不称零warning或新Lint验收。

## 修后当前三包与输入

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5216237 | `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2584412 | `856536f13c8fb7a975663bb27f35f8ce6ca586f2ffee36907d3aca757f58ee7d` |
| test | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

产品仍org.llmasr.minimal / 0.6-debug/code6/min29 target35/arm64，宿主org.llmasr.minimal.testhost / p1b-testhost/code1；仪器org.llmasr.minimal.testhost.test / 标准InstrumentationTestRunner target宿主。三包原证书/v3，测试两包无权限/native/assets。只声明当次SHA，不用修前test SHA代替修后产物。

当次真实collector **209 build / 10 module / 80 native**，相对R1修前build delta仅suite；当前collector=新receipt=新freeze=本报告r1-input-sha256.json，绑定三包、工具、JVM XML。正式receipt：`build/verification/android-test/run-69df974da15048bda048c412a58f57b2/receipt.json`，SHA `7f6e3b744579cbfa8490c4314afcda7049f399fb556914f70d26d5b4429cad55`；逐工具raw及XML副本在新work `receipt/`、`junit/`。

893个保护文件SHA/bytes/mode/mtime/inode前后保持，包括非suite源码/工具/生产/锁metadata/manifest/native/旧planning/report、旧.work/m5saf-build根历史seal/freeze/before/after/audit/receipt/log/handoff以及fresh review证据；原20excluded保持。旧三reports未回写，docs未改，本阶段唯一源delta为suite。自身三plan仅追加纠错/修复阶段及更新当前NextStep。无stage/commit/push/amend、无二级委派或Beads写。

## 边界与下一步

新增SAF assertion实际运行red/green、14方法runner发现/执行/运行失败sentinel、Binder/grant/系统DocumentsProvider/真实Activity/IME/Service/InputConnection、设备4K/16K/JNI-device/AudioRecord/mic/private、模型权重计算/下载、远端CI、release/R8/新签名、当轮full全部 **not run**。provider仍同进程ContentProvider/wrap，不冒系统SAF；cursor/PFD计数不是InputStream关闭故障证据。

本阶段真实命令未因R1发生编译失败，旧R1是静态确认测试预期错误，不捏造行为red。新证据ONLY `.work/m5saf-build/r1-fix/`；具体终态/异常如有以该目录记录为准。父消费新handoff后fresh同sol上下文隔离定点复核R1（非跨家族）；CLOSED前不full/提交，不扩大已核旧范围。
