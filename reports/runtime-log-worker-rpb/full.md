# RuntimeLogWorker rpb：唯一完整工程验证

## 状态与来源

任务 `llm-asr-rpb`，父 `p1-steward` 为唯一 Beads writer；验证 leaf `rpb-test`，`newapi/gpt-6.1-sol/high`。父已接受 review4 同模型 fresh 上下文隔离审查 `NO BLOCKING`（不是跨家族独立审查），R1、R2-a、R2-b B1–B5 CLOSED。本次不重开设计、不改已审 8 候选或旧 summary/build/review 证据。

**本轮唯一 full 真实 exit0，工程证据与输入/产物绑定核验通过；尚未本地提交，不据此宣告 APK 交付、rpb/M4/M5 关闭或设备通过。** 测试发生于 `main` / HEAD `531a11146954ac5f19298560ab9729ba63a6e50d` 的 dirty freeze；后续提交是内容绑定，不重标为已测试新 commit 对象。当前输入与必要哈希见 [full-inputs.json](full-inputs.json)，原 targeted/修复/审查历史仍见 [summary.md](summary.md)。完整原始证据仅保存在 ignored `.work/rpb-full/`，不将数百份 raw 报告纳入本轮候选。

## 唯一 full 与真实终态

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
```

- 后台 `ba229192b` 自动 `completed/exit0`；2026-10-20T12:42:02.925740Z 至 12:51:15.146423Z，552.220688s。
- 完整日志 `.work/rpb-full/attempt3/full.log`：45197 bytes，SHA256 `defc6d0917561acc532a2c87b753c895f9ed7c0101188ea2ee278a866af4e39e`。真实执行记录 `attempt3/full-execution.json` 绑定 exact argv、启动源码 SHA、时间与退出码。
- Gradle **174 actionable / 174 executed**；222 条唯一 task 行含 185 无状态执行/聚合行、22 NO-SOURCE、4 SKIPPED、11 无 action UP-TO-DATE。222 或 185 不是 action 数。所有 required 主 gate 精确执行，无 FROM-CACHE、失败或设备任务。
- 主入口 pipeline 为 `Gradle=0 / tee=0 / scan=1`（完整 DEX 异常扫描未命中）。未重跑 full、未删除任务/放宽 expected/strict/签名或权限；无 clean、无依赖 prepare/下载。原单 worker、Gradle JVM1536MiB、JUnit512MiB、Kotlin in-process、native parallel2 及隔离 Lint heap768MiB 保持；本批未新增资源峰值采样，不继承旧批 RSS 数值。

## 实际工程证据

| 层 | 当批实际结果与边界 |
|---|---|
| JVM | actual JUnit XML：core34、runtime1、test-host7，全部 failure/error/skip0，名单与当前维护源码逐项相符。core `runtimeLogCore` 实际0.24s，stdout包含新 Worker 异常 suite。不是把断言数字当 JUnit 用例数。 |
| Worker 异常 | 主 host 通过 RuntimeLogCore 与独立入口各实际执行107断言，覆盖 load/restore/callback/save/execute、异常后继交错、blocked close、rejection、fatal close、reject successor window、共享 cleanup tiny-budget 真拒删及原 Throwable/suppressed 身份。实际 JUnit 也经 Core 调用同 body。旧 Worker 可编译目标 red 属已接受 targeted 历史，本 full 未重造旧 red；teardown-interrupt/same-JVM continuation 的额外 targeted 历史不冒本 full 新 selector 执行。 |
| 原 host 与 mutants | 12 Kotlin +68 Java host 混编；322 ABI/枚举/生产持久化断言、13 cleanup lifecycle 故障组、两生产枚举 swap 均 compile0→精确 AssertionError1→原源码恢复 green0；3 model 生产 mutants 可编译且命中各自目标诊断；PartRecovery、ActivityWindow82断言/7可编译mutants、capture12 fake平台阶段、原 owner/录音/IME/日志/模型/导出及 source/entry/format/包 checker 负例保留。fake Android 不算设备。 |
| 模块/编译/JNI | 当前 core/runtime/app/test-host Java/Kotlin 与标准仪器源码真实编译；模块 classpath 正例和 core !→Android/JSON/JNI/App、runtime !→app 真实编译负例；生成 JNI 头、NDK arm64 bridge object、真实 Java descriptor/linked DSO 符号核验通过。 |
| native | 当前80输入逐 SHA 同 frozen/正式 native receipt；578 MNN对象内容、mode/inode/mtime保持；JNI重编与增量最终链接，DSO/provenance字节保持。ELF64 AArch64、LOAD16KiB/W^X、RELRO/nonexec-stack、单DSO/static C++/DT_NEEDED门槛均通过。不是全 native clean、空缓存或 hermetic 复现。 |
| fixture行为 | 一次新 `.work/p1b-build/2b/behavior-ogzyvbuk`，实际5阶段0/1/0/1/0；每阶段新编译7个host方法。red分别唯一 `enginePayloadAndOrder` / `cleanupRetainsOwner` AssertionError，原 bytes 恢复 green。仅 JVM fixture，不是 Android runner。 |
| Lint负例 | `.work/gradle-lint-negative/run-ricfwkt1`：实际默认AGP green0=0→唯一 NewApi API33/min29/精确文件行 Error=1→原源码恢复0；工件/边身份及同生产 validator 缺/多/替换 classpath 均目标拒绝1，普通marker恢复0。不是将编译、cache或网络失败冒目标 red，也不是默认typed AGP classpath可变性试验。 |
| checksum | `.work/gradle-m1/dependency-negative-23YDyz`，strict offline help0→唯一 AGP `com.android.tools.build:gradle:8.6.1/gradle-8.6.1.jar` checksum拒绝1→metadata恢复0；fresh HTML目标行 expected全零、actual/cache SHA `9697733a215f2ed42e5b2fc4053484ec832eab29584ebced94d92c91090051c1`，目标组件/工件/行精确核验。 |

### 生产 Lint 与 warning 处置

生产 app/runtime 两份 fresh XML 均 **0 Error / 0 Fatal**。13个gate及8个实际consumer均66工件/171边；仅 resolve 的消费者名称不冒 Lint执行。test-host Lint 未接主图，**not run**。

- app17 warnings：UnusedAttribute3（API33 Back属性在旧系统忽略）、GradleDependency1（annotations固定13.0的升级提示）、ChromeOsAbiSupport1（当前明确arm64-only）、DataExtractionRules1（Android12+备份/迁移策略待后续独立审计，不能把allowBackup=false当设备传输已验）、UsableSpace1（空间可分配能力建议）、MissingApplicationIcon1（开发包图标）、SetTextI18n9（中文/拼接文案国际化）。
- runtime10：UsableSpace1 + SetTextI18n9，按具体message及源位置都是上述app同源重复，无额外类别。
- 全部如实披露、维持既有 defer 边界；没有新增baseline、抑制warning/Error、关闭gate或顺手升级依赖。CMake最低兼容版本deprecated、javac API deprecated及AGP固定aapt2 override experimental提示另列工具提示，不冒零warning。

## 三个 signed APK 与两个正式 receipt

三包均原证书 SHA256 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`，v3有效；实际 SDK apksigner/aapt2/zipalign exit0，raw报告哈希及 ZIP/全部 DEX 再解析与正式receipt逐项相符。

| 包 | 路径 | bytes | SHA256 |
|---|---|---:|---|
| product | `android/app/build/outputs/apk/debug/app-debug.apk` | 5217649 | `9788895c8c548cb48d38117df0af83ae6844716aa2e9aadb34eebef2c8d900e3` |
| host | `android/test-host/build/outputs/apk/debug/test-host-debug.apk` | 2585824 | `f5ba0a3fc61bfd9b6a7762cf90a1dd0baef43c3db4fb3fa628fd43a093622bcf` |
| instrumentation | `android/test-host/build/outputs/apk/androidTest/debug/test-host-debug-androidTest.apk` | 52318 | `950619bd90608e77c73cf7152a5d7c027f4f28fdfaeed8a4770a8655c0e6ba41` |

- product `org.llmasr.minimal` / 0.6-debug/code6 / min29,target35 / arm64；唯一 RECORD_AUDIO、原三Activity/IME组件与debug/backup/back策略保持；唯一JNI库与精确公开资产/许可清单，无模型权重、私人音频或转写资产。公开测试sample不是设备录音。
- product12 DEX/1327类、host10 DEX/1235类、test2 DEX/43类，完整解析且无重复definition、产品无fixture/JUnit/mock/runner泄漏。两测试APK无权限/native/assets/意外组件或launcher，host nonexported Activity/version1/p1b-testhost，标准runner/target/required libraries正确。
- test精确14个public concrete `()V`方法只是**编译与parsed DEX库存**，不证明Android runner发现或执行。
- 独立产品正式报告：`android/app/build/verification/debug/run-090bb9a0e0c04f3eb071dbdc4fe5e8f2/`；三包正式报告：`build/verification/android-test/run-c05e2d3c599d43639d7205bd31a51166/receipt.json`。
- 当前实际collector **213 build / 10 test-module / 80 native**，包含未跟踪新Worker测试。frozen/current/两正式receipt逐路径SHA一致，APK/signature/版本/metadata/外部公开sample与配置哈希绑定；docs/review/本报告独立 supplemental，不冒构建输入。

## 准入、保护与外部工具差异

- attempt1 `bef95250e` exit1 是本leaf辅助脚本误拒合法外部工具symlink；attempt2 `bc7c3e32b` exit1 是宿主Nix入口2.34.9不等旧2.34.8严格拒绝。均在 full之前，未跑产品测试，原脚本/log/freeze字节保留，不改写失败历史。
- 父明确批准**唯一宿主Nix external delta**：旧2.34.8 SHA `2931868bfa32a943f00212aafb9084778ba91b9129aa42bba2c65eb6c8adca3b` → 当前系统profile既装2.34.9 `/nix/store/9jw399l4lllgai8p7fd4y61lqhi8qzja-nix-2.34.9/bin/nix`，mode555/4078544bytes/SHA `cac9e1cdced503201d9d175b48732cd29b027b066398a358ac98159c0c9128b1`。attempt3实际cache-only内完整观测path/link/resolved/SHA/version/env通过，其他JDK/SDK/NDK/Kotlin/CMake等原pin均保持；不切旧PATH、不改flake/locks/metadata。新Nix来源/CVE/签名/传递动态闭包**未重新研究**，不声称完成供应链审计。
- attempt3 `bb24e97d5` 真terminal0仅补准入封口；实际复核8候选、213/10/80输入、578对象、4819显式repo保护、2283旧isolated证据；2383份已知generated备份逐mode/bytes/SHA保持，0重复制/0删除。cccc/knots入口link及resolved target单独external域，repo候选仍严格regular。
- post-full保护：4819列出文件仅父progress合法追加与4个已备份generated身份改变（product/host APK、core/runtime XML）；2383旧生成输出中2358保持、25已枚举合理delta，原字节全部留attempt2/baseline。delta是APK重建、JUnit报告/时序输出、Lint HTML、两份JNI-check中的Worker.class、当前stage/native verification输入更新；stage/provenance与native verification相对旧版的唯一产品源hash delta均为Worker。无删除。独立新正式receipt随机目录新增，旧两receipt及历史review/build报告保持。
- 保护限于明确列出文件，不宣称遍历全部ignored/cache/private内容。未触签名私钥/凭据、未复制模型或音频；index空及原tracked diff保持。完整允许delta列表、起终输入、证据SHA与无后台/PID-start核验见ignored封口receipt。

## 未执行与唯一下一步

设备/Android runner发现执行、安装/ADB/模拟器、麦克风/私人文件、系统SAF/Binder/grant/InputConnection/真实生命周期、JNI-device/4KiB16KiB运行、模型计算/下载、远端CI、release/R8、push/amend均 **not run**。主机/ELF/parsed APK通过不替代这些验收。

本leaf未stage/commit，不交APK、不关闭任务。父消费 `.work/rpb-full/handoff.md`、closure/receipt及实际证据后，安排候选安全只读审查；随后父另阶段授权精确allowlist本地提交，提交成功且身份绑定核实后才能告知可验证APK。
