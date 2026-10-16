# P2 enum R1-local targeted（非 fresh CLOSED / 非 APK 验收）

任务 llm-asr-ap5；合法 owner/唯一 Beads writer 父 p1-steward；实施接续 leaf p2enum-build-3，session01a143ce-b6d9-739d-abc9-acc7b2c4f3da / HerdrwY:p2K / newapi/gpt-6.1-sol high。父仅协调，leaf唯一产品writer，不写Beads/委派/stage/commit。main/base a5204512cc779ed8e85116fdf7ea4453abf0b9ce，无worktree。原writer provider流中断是历史，不读旧session正文。

## 最小实现与边界

父接受 review-2 对共享 foreground 策略的静态拒绝，决定不扩大治理所有共享 caller。共享 `scripts/compile-jvm-sources.sh` 已恢复 base 精确 SHA256 `097cf23ad7f0e0a5cd8ebcf4e5f87523d3505bc8f90d389d9434ad5b76131adf`，本轮未再次修改；保持120秒默认 GNU timeout，其他 caller 行为不变。

仅枚举 driver 以 start_new_session 创建自有 SID，捕获 leader PID/start/SID，扫描该 SID 所有普通 PGID 的非 Z/X 工作。每个信号经 pidfd 绑定实际目标并重核 start/SID，TERM宽限后KILL、有界重复观察；实际空 session、直接child wait后再次确认才返回，不以 shell wait 冒全部后代停止。PID/SID身份异常、/proc观察/信号失败、超时或再次中断均 StopUnconfirmed，实际 owned_tree scope 保留可能仍被工作使用的 tree。普通 GNU timeout 新组、kotlinc shell 非exec JVM链在范围，主动换SID daemon/sandbox、host SIGKILL等不可捕获终止不保证。Z/X仅停止执行，不称driver回收全部孤儿。

## 真实 red / green

- 历史原base wrapper+旧group-only driver的 live-owned-worker-before-cleanup red 已在前批保存并回审核实，本轮只消费不重跑、不冒当前输入。
- 新目标 red：实际 driver.run/owned_tree、真实base wrapper、普通shell/worker，在受控 stop观察异常后树仍被自动删除，命中精确 `RuntimeError: R1 stop-unconfirmed deleted owned tree`（exit1）。观察时实际workers活着，finally PID/start安全回收。红版driver/测试快照按已知精确seam edits逆变换重建、SHA匹配当时inputs，明确不是当时原位snapshot。
- 正式 normal / -O / host 各12场景：kotlinc/javac/Java 的TERM/HUP/driver timeout九项、kotlinc/javac内deadline两项、观察失败保树一项。通过实际driver.run/owned_tree与原wrapper；GNU timeout实际PGID!=SID，shell非exec子worker。删tree前实际无运行worker；非零退出/取消码及timeout基础设施诊断外传。观察失败时确有活worker且scope退出后树保留，最后测试finally收回wrapper/timeout/shell/worker后才清树。
- 内deadline PATH shim只将120→0.35秒并exec真实GNU timeout，验证base argv和124退出，非120wall实测，不再期待foreground/137或声称改变base策略。
- 一次开发矩阵失败单列：前9项通过，foreground external child令Bash defer trap，最后deadline变成outer timeout；改测试fixture普通child+wait使trap及时退出，仍同SID非exec。此失败不是枚举行为red或当前green。
- 新driver真实生产mutants普通+host两链，各5fresh编译0、Source/Kind两精确Java inventory/order AssertionError1、原源SHA恢复重编green0；相同Java caller正常322checks。两枚举/Java caller/baseline不再修改，63生产文件=12kt51Java，未Java0。

## 终态证据与通知缺口

完整command-index、各真实exit/execution/logSHA在.work/p2enum-build/r1-local，公开必要日志/commands及verified-command-index；cleanup逐case observations/ready/finally/worker日志以单run JSON bundle保留原相对路径、bytes/SHA和完整内容，避免再复制数百小文件。真实生产mutants日志/result保留。未导出fake jar/Probe/compiler产物。

host be9979346曾在checkpoint恢复后丢失自动通知；父提供具体磁盘/runtime矛盾后仅一次exact registry lookup=unknown，原launch PID1392274不存在。磁盘execution finished2026-10-16T08:47:38.047256Z exit0、exit文件0，完整log SHA `cb3987f3e4011a7236caa788d1a7c349af5d93b38ea07b9bfa73bd9388b4ed0a`核齐并全文消费。不得声称收到自动host通知；未重跑成功链。见host-terminal-recovery.json。

最终fast/format终态、实际JUnit与保护/候选封口见同目录补充数据及私有handoff/closure；本文不是单独的交付或review-CLOSED证据。

## 多域 freeze / 保护

当批211build/10module/80native/63source；r1-local before→current build3delta（wrapper恢复base、driver/cleanup），module0、native1wrapper恢复、source0、tracked2（wrapper恢复/docs追加）。从上一R1新增wrapper delta回到base，不称native collector全图相等或重新链接。578native对象/DSO/provenance与32历史包/receipt保护保持，旧APK不能作本次source验收。

旧77/345manifest、旧freeze/两轮报告/原work的820历史文件SHA保护，原26excluded不夹入候选。父监督接续记录单列合法supplemental，两个review plan只读保护，开发原plan保原身份历史追加新绑定。当前精确candidate modes/bytes/SHA由最后manifest给出，共享wrapper恢复后从candidate移除。

## not run / 单一后续

未full/verifyDebug/Lint/全Android编译/Android-test-build/APK/JNI/native构建或链接/新DEX签名包验收/runner或设备/ADB模拟器mic/private/modelweights/下载prepare/remoteCI/push；未stage/commit，不关ap5。父消费完整新handoff/freeze/manifest/closure后 fresh exact sol fork:false 仅R1点审（同模型上下文隔离非跨家族）；review CLOSED后父才安排唯一full，再另授权reviewagent两阶段安全本地提交。此leaf正式closure后停写。

## 最终 targeted gate 补充

fast/format后台b140453af已自动completed0，strict/offline fast --rerun-tasks23actions全执行，实际XML core34/runtime1=35JUnit无failure/error/skip，format1action maintained41；两pipeline0/0/scan1，既有aapt2实验warning披露。新8工程commands包括有效red1、开发fixture失败1及6成功0，全部真实execution/exit/logSHA已核齐。7后台均终态（host一次异常恢复不假称自动通知），当前无pending链；最终保护/候选与无相关后台事实见closure。
