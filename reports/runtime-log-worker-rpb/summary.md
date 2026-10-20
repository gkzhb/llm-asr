# llm-asr-rpb RuntimeLogWorker fatal slot 恢复（R2-b 窄修当前摘要）

- task `llm-asr-rpb`；父 `p1-steward` 唯一 Beads writer/owner（session01a158a7 / Herdr wY:p1）。main/base `531a11146954ac5f19298560ab9729ba63a6e50d`，无 worktree，本阶段未 stage/commit。
- 原开发/恢复 rpb-build/2/3 使用获准 `newapi/ds-v4-flash/high`；本困难收尾由唯一串行 writer `rpb-build-4`，`newapi/gpt-6.1-sol/high` 实施（用户允许 ds 非排他，不是 fallback）。旧 writer 已停止。
- **R1 CLOSED、R2-a CLOSED；本次仅 R2-b B1–B5 修复，待 fresh 残留回审。** 产品未改；不重开五串行 Error / R1 交错 / 异常 close/reject 已接受覆盖。
- 仅 targeted + fast/format；不独立全 host/SDK，不 full/lint/JNI/native/APK 重建，不设备/ADB/模拟器/mic/private/模型计算/下载/远端CI/push。无新 APK 交付，设备仍 not run。

## 已接受的产品与覆盖（保持冻结）

旧 Worker `ee02ac79…` 在真实 load/restore/failure-callback/save 或 executor.execute 抛 Error 后永久占 slot。R1 又发现 direct executor 同一 Error 穿 inner drain / outer execute 双 frame，旧无身份 release 可清后继 owner。当前 Worker `d4468f5b…` 每 admission 分配 token，正常与异常只 release 自己仍持有的 owner；stale token no-op。Error 原身份在释放自己后传播，不计 queue-full；普通 RuntimeException 隔离，不新造状态/隐私词表；单 drain+dirty、无自动 retry、close 非阻塞/accepted 工作收尾保持。

R1 五真实 Error 路径与 interleave 的旧可编译行为 red/修复 green 保留。R2-a `close-fatal` 在真实 save canonicalization 暂停后 close，再抛同 F，外部核身份/actual exit/closed拒入/无retry/不计queue-full/无part/持久不变；`reject-window` 在真实 FailureSink 暂停前已释放旧 admission，后继获 slot 并在真实 save 阻塞，旧 reject 返回不清后继，第三 onlydirty，最终持久/label清空/dropped保持1。仅支持 direct executor 与既有对象注入，不外推所有 OS/force/rename 故障或 owned shutdown。

## R2-b 一次集中修复

1. `ThreadScope.awaitAll` 使用单一总纳秒 deadline，先看 `isAlive`，正 remainder 拆 `join(millis,nanos)`，绝不 `join(0,0)`；预算尽/中断后仍核全部线程实际状态，保中断而不冒终态。
2. 所有 start/首次观察置保护 try 内；gate 启动前登记，finally 全部 release 然后 bounded actual-exit 确认，T3 在列表。close-block 在保护 try 内先持 persistence monitor 再 start/waitBlocked，恢复确定性。
3. 单次 main 局部 `RunCleanup`，不全局清掉他次保树状态。共享 `finishScope` 未确认先标记目录，清理失败抛出或附到原 Throwable 的 suppressed；main 用同 `delete` guard 保树，以异常返回宿主，无 `System.exit`。
4. 删除 ENFORCE/专用 oldcleanup/joinQuiet 危险分支。held-worker 真实 save 由已登记 gate + 独立 monitor 控制：共享收尾放 gate 后 worker 仍真实 held；999999ns 正极短预算产生真实未确认，随后调用 main 同一 guard 实际拒删且目录存在。外部检查观察中断、原 Throwable 身份与两个 cleanup suppressed；独立 finally 释放 monitor、有界确认实际退出才准删除。
5. 同文件选择器 `teardown-interrupt` 只用于外部宿主验证首次 await 中断失败收尾；没有通用框架/生产 seam。外部 HostContinuation 在同一 JVM 调 main、捕获首次观察中断并继续另一 main，证明不杀宿主。

## 当前候选身份

完整8候选 mode/bytes/SHA 由新证据域 `candidate-end.json` 外置绑定（包括本文 SHA，不自引用）。产品冻结四项：

| 路径 | SHA256 |
|---|---|
| `android/app/src/org/llmasr/minimal/diagnostics/RuntimeLogWorker.java` | `d4468f5bb1fc91e099ce22a8eef4d3c3f745995651b89ca6a6d58d850e52f754` |
| `tests/RuntimeLogCoreTest.java` | `abf9e12b6e48bdb55cef76efd5f9770f3405b97f630c991d2e7c6111caba045d` |
| `scripts/test-minimal-apk.sh` | `5ed2d641014a5844cad9c1bdf1b2c52ec39eb35ff59ff4462fe86afa31f380fd` |
| `gradle/baseline-inputs.json` | `0f4a72d2b3226f44a62a7de31af06c21d1dd0e9b1bd93946cc54e45a541a5525` |

可变四项为 `tests/RuntimeLogWorkerErrorTest.java`、`docs/runtime-logs-validation.md`、`docs/module-test-map.md`、本文。既有 Core/JUnit 接线不变，方法仍 core34/runtime1=35，不将 checks/PASS 行数当用例数或失败路径证明。

## 当批证据与历史更正

新证据只在 `.work/rpb-build/r2b-fix/`：当前4684个列出文件实际 start 身份、fresh 混编/全 selector/真实共享清理负例、同 JVM host、fast/format 命令真实 exit/log、保护 end/delta、逐函数人工路径阅读与正式 handoff/closure。真实自动 terminal 已消费：
- `bb7c41643` completed/exit0：2Kotlin+7Java fresh混编、11 selectors与合并107checks green；held组12checks，真实共享拒删/主身份+两suppressed；同JVM HostContinuation 捕获首次观察中断、保中断/actual cleanup后继续另一次main，exit0。driver log SHA `256fb8be6faaafdfe40c1b388c26eaa5afb6aed9a18dbbcae10b02a39894698e`。
- `b1eaf9b09` completed/exit0：cache-only fast `--offline --rerun-tasks` 一次，23 actionable全部executed、BUILD SUCCESSFUL23s、pipeline0/0/1；log SHA `e36d27c1295f158d31e5ce4bf8e1bd8b3add29d364cbc02392e9c82b9ee9c5b1`。实际JUnit core34/runtime1零failure/error/skip，runtimeLogCore0.232s/system-out含107checks与共享拒删；不是只看35数。
- 同链 format `--offline` 一次，41维护文本/read-only、BUILD SUCCESSFUL6s、exit0；log SHA `7a64ce3e3e893a1fe667a13f0d860282df892e27ba6101e73176aeb7fc53e559`。四可变候选另核UTF8/LF/无尾空格，git diff --check通过。以上主机绿不代fresh审接受或设备验收。

旧 `.work/rpb-build/r2-fix/` 和 review3 全量字节保留。其 current104checks、专用 oldcleanup compile0/目标AssertionError1、fast/format0 与35JUnit 全零是历史真实成功链，但 reviewer3 指出其拒删测试未走共享实现、join 舍入/异常路径/System.exit/close竞态，不能据其绿宣 R2-b CLOSED。

集中更正 review3 P3（旧错误记录不回写）：
- R1 / R2-a 已 CLOSED，仅 R2-b 待审；旧 closure 的“R1/R2均未CLOSED（R1已CLOSED）”是历史矛盾文字。
- 旧 progress `2acc726f…` 不是最终候选 manifest；r2-fix 最终 manifest 实际 `2468ef421035b12a5d92ad7271c33d16fd07cdf8f8543756719dfaa344eefd06`。本轮最终8候选另由新外置 manifest 绑定，不能挪旧 SHA。
- `DRIVER_SCRIPT_EXIT=0` 不在旧 driver.out，实际有 `DRIVER_DONE`，命令 terminal0来自实际 task；`MANIFEST_DONE` 是旧脚本 stdout，不在 protect-end.txt 末行（末行 UNTRACKED）。旧 end SHA `6260dce268587bc10aa26a92feb618875f1b11f05b4750d3454cbad7a4658d41`，不虚构 marker。
- 原 sources209→210 是维护库存文件；cccc输入170→171、knots188→189 是工具输入文件。函数1775→1806 /1350→1371 是 **R1旧22KB测试时点** 的全库candidate，不是当前43KB或本轮代码计数。旧 R2 单文件 raw31→51/21→34也是当时快照，本轮不重跑全库、不冒旧分数覆盖新代码。
- intra(a354fb→d4468f5b) schedulePersist cccc **4/5→4/5**、knots5/4→5/4；5/5属最原 baseline 而非 intra 起点。drain wrapper/body 是拆分不是整体降分；claim 只限变更 Worker 函数，不声称全仓无热点。当前测试逐函数人工映射见 manual-path-review.md，不以复杂度低代正确性。
- R1原链前后7个非摘要候选保持，summary后 supplemental 改，旧freeze不改。r1保护148/149是总行数（含注释/重复），不是独立对象数。旧R2 start测试取旧副本身份不是当时主树实测；本轮 start 直接读当前真实路径，不能补造历史。

## 保护范围 / 唯一下一步

新 protect-start/end逐文件列出域包含旧build/review/driver/closure/manifest、旧3APK2receipt、578 objects/DSO/provenance/asr_jni.o/对象清单、锁metadata/wrapper及既有tracked/excluded非ignored文件；只授权四候选/开发三plan/父plan追加与fast实际JUnit XML变化。仅证明列出域，不保证全部ignored/cache，也不从现在相等反推最初开发全程。

父消费新正式封口后安排 fresh `fork:false` **仅 B1–B5 残留回审**（同模型上下文隔离，非跨家族独立）。不重开已 CLOSED 的R1/R2-a，不凭本leaf done即验收；未获回审接受前不 full/commit/交APK。本leaf不claim/Beads/stage/commit，完成后由父停止唯一writer。
