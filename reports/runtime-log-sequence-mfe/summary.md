# Runtime log sequence exhaustion — targeted evidence

任务 `llm-asr-mfe`；父 owner / 唯一 Beads writer `p1-steward`；唯一开发 leaf `mfe-seq-plan`，`newapi/gpt-6.1-sol` / high。源码基点 `31ed79d1222cfc2e55d3c308f88e4399e75ccb23`。本报告为实施 targeted 阶段，不是 fresh 审查、完整构建、本地提交或新 APK 交付。用户跳过真机；设备/Android runner、ADB、模拟器、mic/private、JNI-device/4K16K 全部 not run。

## 契约与最小变更

- 有效 append 真正遇到 `nextSequence == Long.MAX_VALUE` 才密集复制有界保留窗口；先 typed 事件验证及完整副本构造再安装，坏输入/clock RuntimeException 不改变状态或通知。MAX-1 仍合法，不提前预留 N。
- restore 先完整验证历史。普通可表示 offset 保持原 sequence/gap/early-next 语义；不可表示时保留严格合法 `history || early`、dense-before-trim，初始状态 OK。真正 invalid history / V1 文件仍拒绝，未放松 parser。
- sequence 保证单张 snapshot 严格排序，不是跨恢复/极限重排的永久票据；边界可 numeric 回落。旧 immutable snapshot/Event 不变，所有非序号字段保持，drop 只算真实 trim。
- 只改 Store append/restore/private dense helper/注释；publish/trim 主体字节不变。Worker/Persistence/Event/Clock/Sink/Codec、Kotlin enums、API、模型/native/依赖/权限/签名均不改。其余候选为 CoreTest、Store 单项 pin、两份窄文档及本报告。

## 实际生产行为红绿（当前已运行）

证据位于 ignored `.work/mfe-seq-build/`，每次新建 classes、canonical host 源库存及完整 argv/source SHA/log/exit；真实混编 `12 Kotlin + 68 Java`，无 stub、反射设 counter 或测试自带旧算法。父已接受 S1–S4；测试默认 main 执行，无新 JUnit 方法。

| 阶段 | 编译/执行结果 |
|---|---|
| `old-red`，实际 unchanged Store | compile0；三个独立运行均 exit1，具体 AssertionError 为 `near-max no-early append rejected at i=1`、`near-max early append rejected at i=1`、`legal max history was discarded or marked corrupt` |
| `fixed-green` | compile0；三边界组和 protection 组均0；默认 CoreTest 12组（含原 WorkerErrorTest107 checks）、StoreErrorTest20 checks、LogExportTest100 checks、InferenceAdapterTest10组354 checks 均0 |
| `old-rollback-red` | compile0；exact old Store 隔离副本的三条独立具体 AssertionError 再次均 exit1 |
| `fixed-restored-green` | compile0；恢复 fixed Store 的三边界组及 protection 全0 |

不是缺 cache、编译错误、timeout 或构造非法 Event 充当目标 red。目标历史由生产 Persistence save/load 验证为合法 V1，再经真实 Worker.restore，noearly/early 在第二次 append 才触发旧故障。新测试的 try-with-resources scope 先解 gate、总 nano join(millis,nanos)、确认线程实际退出才删除目录，回调仅记录，断言带出至外部线程；主 Throwable 保持，cleanup 走 suppressed。

Green 覆盖：普通2/5 gap与原40/41early offset/empty/once；capacity1/4/1000；dense-before-trim精确后缀/drop；MAX状态非法UUID/detail/enum/duration及clock失败无状态/通知变化、clock一次读取；全字段旧snapshot和codec bytes冻结；真实Worker+Persistence两次额外重启继续追加且OK/最新V1 exact；duplicate/decreasing/MAX坏V1仍CORRUPT；sink RuntimeException隔离/Error同对象传播后恢复、一次reentrant、阻塞sink期间三producer跨极限追加后只发布旧→最新因果状态（允许numeric下降）。

## Targeted / 复杂度 / 保护状态

- 四阶段均使用同一最终 CoreTest SHA `77ff00d89183a23e203200a5496e7999eeea57dd5bb4a9e82491887adeb398c7`；旧 Store SHA `4e7dac50…2e8c` / fixed `9cdde653…779cd`。每条真实命令/closed log 完整 SHA 在当批 executions/result/targeted-audit 中，不把 driver exit0 冒充内部目标red exit0。
- 固定 Nix cache-only、strict/offline fast `--rerun-tasks` exit0，23 actionable tasks 全执行；真实 XML core34/runtime1，failure/error/skip 全0，含默认 runtimeLogCore 新组；format exit0。pipeline Gradle0/tee0/DEXscan1（无诊断），format41维护文本；mock/source负例不冒设备或 APK 验收。
- 双工具同版本/二进制/完整argv/同库存真实 before→after/compare exit0：cccc1.8.3（`--no-config --no-cache --jobs 2`）1855→1887函数/171文件；knots1.18.0（`--format json --jobs 2`）1407→1426/189。collector13项回归通过，固定 Nix 工具 before/after 身份相同，不更新旧基线消警。append(elapsed) cccc C/Y1/2→5/5、knots C/M1/1→4/4 命中相对增长信号，源于真实耗尽判断/validation-staging/install分支；restore7/7→9/7、9/9→10/8，新增denseCopy两工具1/2。publish/trim指标与方法体保持。新增测试收尾热点 close46/20、47/19/nesting4 单列人工处置，不与产品平均；跨界并发/拒绝/重启各组也单列，精确记录与开发处置见 `.work/mfe-seq-build/complexity-disposition.md`，待 fresh reviewer 接受，不称复杂度变好或低分证明安全。
- 入场保护7102路径，含旧146 excluded/旧诊断plan+证据、先前mfe证据/实际三APK/formal receipts及578 native对象；父活跃seq-supervision目录明确supplemental例外。before动态输入213build/10module/80native，仅当批计数，不作永久门槛。最终候选/输入/日志/保护封口随 handoff/closure 保留，不覆旧报告或产物。
- 当前磁盘 APK 仍为上一批已提交输入产物，不宣称含本修复。本阶段禁止 full/stage/commit/Beads writes/二级委派/下载或设备访问。父消费 targeted 封口后安排 fresh 同模型上下文隔离 `fork:false` 审查（非跨家族），接受后才唯一 full 与两阶段安全本地提交。

## 待办边界

当前仅序号切片，picker 启动失败原因覆盖仍未实现，不关闭 mfe；M4/M5/tc3/设备验收保持原状态。最终准确结果以本阶段封口后的 handoff/closure/候选 SHA 为准。
