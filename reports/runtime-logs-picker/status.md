# llm-asr-mfe picker 失败原因稳定性（targeted）

基点 `2bb5e152591c3195e38590991e769aab73010019`。本批仅单 Activity 私有 notice：保留固定选择器启动失败原因，不改 Page/Controller/Text/phase/public API、日志后端或 Store 通知。有效新 ticket 开始才清除；pause/resume 保留；新 Activity 不继承。catch 保留 abandon(true) 的 slot 释放与 EXPIRED 语义，不记录异常 message/cause/URI。

## 测试契约与证据边界

`tests/logs_activity_picker_test.py` 编译真实完整 LogsActivity 与精确列出的日志生产 Java/Kotlin，平台、AppGraph 装配及 ActivityWindow 为测试侧 fakes。通过真实导出按钮→真实 startActivityForResult RuntimeException catch→真实 Refresh.run/render→页面固定位置 status 的外部断言；并测不变/append snapshot、单个1000ms tick、无 auto retry/零 export IO、slot 释放、重试新 requestCode/同页、旧 callback 拒绝、取消及真实内存流成功、正常 pause/defer/resume、失败 pause/resume、destroy/recreate EXPIRED/notice 不继承。独立 persistence 队列永不运行，worker close 后清理，无活 IO 或测试线程。

默认 adapter 同时执行三项 fresh 可编译 consumer mutants（render 优先删除、retry clear 删除、pause 错清）和恢复正例；不是 ignored driver 独占测试。增强 LogExportTest 的短 helper 保护 abandon/noIO/retry/旧 callback/destroy-deferred，原完整 main 的 Error/close/阻塞 owner 测试保持。

这是 **host JVM platform-fake consumer**，不是 Android runner、系统 picker/SAF/Binder/grant/真实生命周期设备验收。设备/ADB/模拟器/麦克风/私人文件/模型及下载均未运行。

## 当前执行状态

- before 双工具与13项 collector 回归已通过（cccc1887/knots1426函数记录，工具与范围绑定见 ignored `.work/mfe-picker-build/complexity-before/`）。
- 最终 test SHA256 `9f55b89e42dd1ee3ee1ad8c08e6bba5471faf3d355605e1f54283662bd99166b`；旧 Activity `89f921c43c9f5a92e962eb4d03e3b990121f73fe2b2ab0cdd3a5b8b4e2def801`，修复 Activity `2b2bb518a33953d1262f3e7c4e6e667f466ac7f52f32c3b8848bb9b0d9ddf671`。同 test SHA 四阶段各 fresh compile0，运行 **旧1→修复0→旧复红1→修复复绿0**；两个旧阶段均准确 `picker-unavailable reason must survive foreground refresh` AssertionError，两个修复各123 checks，主树未回退。完整 argv/inputs/exit/raw 在 `.work/mfe-picker-build/{old-red,fixed-green,old-replay-red,fixed-replay-green}/`。
- 正式 adapter 普通与 `-O` 均 exit0；各模式正例/恢复各123 checks，三个 mutants 各 fresh compile0→准确外部 AssertionError1。实际测试 SHA 与上列一致；编译失败从不计目标红。
- Nix cache-only `fast --offline --rerun-tasks` exit0，23 actionable 全执行；actual core34/runtime1 JUnit 全零 failure/error/skip，完整 LogExportTest **108 checks**（含原 close/Error/阻塞 owner）。`format --offline` exit0，当次46维护文本通过。原 gate、签名、权限与依赖配置未放松；未下载补 cache。
- 同版本/同 tracked 范围 before→after：cccc1887→1889、knots1426→1427函数，增量为短保护helper（含cccc lambda）。生产 render cccc C/Y7/9→8/10，knots C/M6/9→6/10；达到分支10信号的唯一新增为必要notice三元优先，真实consumer与mutant证据支撑有界处置；beginExport分支保持。新helper两工具C6/分支9；旧main C68/M40保持，仅加调用。new Python显式补测各8函数（cccc max C7/Y6、knots C9/M8），exact生成17 Java补测cccc86/knots84函数（两工具max C5/分支9），stderr空/exit0、cccc解析错误0；不把Python字符串当Java覆盖，不stage补库存。完整工具身份/argv/raw/manifest与热点人工处置见 `.work/mfe-picker-build/complexity-{before,after}/`、`complexity-extra-execution.json`、`complexity-review.md`；旧公开baseline不改，既有JNI partial/Kotlin/DSL/shell盲区仍保留，复杂度不作正确性证明。
- 当批9候选及closed文本证据最终SHA、1918保护对象delta、命令终态封口见 `.work/mfe-picker-build/{candidate-manifest.json,evidence-sha256.json,closure.json,handoff.md}`。未将活stdout纳入SHA；旧三APK/receipt保持。
- 上述为初始 targeted 历史；随后 fresh 同模型上下文隔离审查仅 R1 runner timeout teardown REQUEST CHANGES，生产 notice 无其他阻塞。下节为父接受 R1 后的最终身份，不能用旧测试 SHA 声称新版本已测。full、JNI/native/APK 构建及本地提交仍未授权 / **not run**。旧三 APK 已备份且仅上批身份，不是本批 APK；未交付新 APK。

## R1 runner 生命周期修复与最终 targeted

父接受 fresh 唯一 P2/R1（原报告为静态发现，不冒运行故障）；仅 adapter 与本三文档更新，其他五候选及生产 notice/pin 全冻结，不修改共享 compiler/依赖/权限。新 Linux/Nix owned-session runner 使用 `/proc` PID-start、pidfd 信号、当前进程 subreaper、实际 wait/reap；嵌套 timeout/tool 的不同 pgrp 仍归 session。超时/中断有界 TERM→KILL→reap，partial stdout/stderr 与原因/exit/退出确认落盘，保原异常/KeyboardInterrupt，可捕获 SIGTERM 同路径；退出无法确认保目录拒删且不采活输出 SHA。已确认退出但失败仍保默认工作目录诊断，成功目录才自动删除。不承诺恶意脱离 session 或不可捕获 host SIGKILL 的任意进程沙箱。

- **真实旧故障红**：轻量合成后代（无真实卡编译器）经旧 execute 超时后仍活且缺 terminal ledger，外部准确 AssertionError `owned descendant must be reaped before generated directory cleanup`，不是 TimeoutExpired 本身。测试 finally 精确身份有界 KILL/reap。最终同契约/测试 SHA 下旧 runner 仍该目标红，修复普通/-O 与恢复绿；正式默认组覆盖 TERM 抵抗必须 KILL、父先退且后代持输出/新 pgrp、注入中断身份、未确认退出拒删/保目录及证据、恢复清理、已确认失败保证据。拒删为确认 probe 故障注入（真实子进程已退出），不冒真实 D-state 故障。
- **最终测试 SHA256 `a1a937ed199094de4e68cf61e986e2e71f53d907302ad744cd5fb9be1713c03d`**。最终 picker 四阶段各 fresh compile0，old1→fixed0→old1→fixed0，两个旧源均原准确 consumer AssertionError，两修复各123 checks；正式普通/-O 各三 freshcompile0/exactassertion1 mutants 与恢复123checks，并实际运行新故障组。
- 最终 cache-only fast offline reruntasks exit0，23actions全执行、actual core34/runtime1零 failure/error/skip、完整 LogExportTest108checks；format offline exit0/46维护文本。资源链 `bca78addc` 真实 terminal0 后已消费，不重跑成功 before/full。此前中间SHA证据单独保历史，不改旧 build/review 封口。
- **规模显著增加并交 fresh 定点回审**：adapter366→733物理行（+367），346→679非空行（+333），AST函数8→26（+18）、新2类；新增限于 R1 所有权/退出/异常终态/拒删与正式故障测试，不扩通用跨OS框架。新Python双工具 cccc28/knots26函数记录，`execute` cccc C/Y37/22、knots C/M36/24/nesting3/SLOC80；cleanup_workspace两工具C11/分支10；synthetic_case cccc14/14、knots12/15；runner_fault_contract knots10/11。均按异常仲裁/外部断言/退出后清理逐分支处置，复杂度非安全证明，未通过平均或拆wrapper消信号。完整必要性与热点审查材料 `.work/mfe-picker-r1-fix/complexity-review.md`；新after tracked指标与已接受生产处置保持，exact17Java cccc86/knots84、max C5/分支9、parseerror0，工具identity/argv/raw/source manifest绑定最终SHA；旧before/13collector回归只SHA引用，不重跑/改旧baseline。
- 新9完整候选与全部closed证据、2353旧对象保护/四路径授权delta、五冻结候选/旧build-review/APKreceipt保持与无自有后台事实见 `.work/mfe-picker-r1-fix/{candidate-manifest.json,evidence-sha256.json,protection-check.json,closure.json,handoff.md}`。父 fresh 定点回审、full/APK及提交尚未完成；设备/Android runner等原 not-run 边界不变。
