# 运行日志验证矩阵（0.6 实施中，尚非验收结果）

## 主机与构建门槛
| 验证面 | 所需证据 | 当前状态 |
|---|---|---|
| 时间与安全格式 | 固定事件、墙钟毫秒时区、单调耗时、任务来源ID；私密字符串不出现在日志/导出 | 待实现/测试 |
| 顺序与失败真实性 | 真native加载前/成功后、推理前/正常结束后回调；fake执行生产adapter成功/失败/回调异常 | adapter 9组330检查及父复跑通过；Android Java/NDK object编译通过；JNI runtime未测 |
| 本地持久日志 | 有界记录/队列、重启恢复、损坏/IO失败降级、目录别名与符号链接拒绝、并发快照 | core 8组专项+父复跑通过；不含Android导出接线 |
| UI与导出 | 非exported第三Activity、insets、缓存快照/生命周期、安全单次导出票据、provider失败/close失败 | 待实现/测试 |
| 回归 | 旧模型路径修复、模型管理、App/IME会话、结果文件、owner、权限检查保持 | 待执行 |
| 完整包 | 0.6-debug/code6、Java/DEX/JNI链接签名、固定MNN对象不变、APK/派生报告绑定 | 待父构建 |
| 独立审查 | 真实事件边界与隐私、持久/导出并发与生命周期；集中处理阻塞 | 待独立审查 |

## 手动手机检查（需用户后续确认）
1. 覆盖安装保留模型，打开运行日志，确认日期、毫秒、时区可见且背景/文字清晰。
2. 转写内置样例，在推理进行时打开日志：实际加载开始、加载完成、推理开始应先后出现，而非全部等推理结束才显示；最终出现推理完成和任务完成。
3. 确认“加载完成”并非“SHA已验证”；现版每请求重新加载，两次请求有不同任务ID。
4. 通过IME录音并预览（用户主动麦克风授权），回App看相同日志页面可见IME来源事件；不得看到输入正文或录音内容。
5. 导出UTF-8 TXT到用户指定位置。日志显示与文件均有时间戳；说明导出采用选择前快照/实现确定的时点，选择期间新增日志不造成半条或混合快照。
6. 取消保存、旋转/重建页面、返回后重新导出，不应重复写入或误用旧请求；provider不可写时提示失败而非成功。外部部分文件可能残留，不自动删除用户文件。
7. 关闭再打开App确认已刷盘日志恢复；不把崩溃/强杀保证不丢任何事件作为契约。
8. 记录达到上限后旧条目受限淘汰；页面不卡顿，历史淘汰提示明确；日志失败不阻止推理。
9. 模型删除/重导入、清除转写结果后日志仍保留，且导出不包含模型/音频/文本内容。

## Worker fatal 恢复（llm-asr-rpb，2026-10-20，含 R1 修订）

窄修复：`RuntimeLogWorker` 每次 admission 持有显式 slot token。正常空 dirty 交接、drain 异常退出与 execute 未启动失败都只释放**自己 token 仍持有的 owner**（`releaseSlot(token)`/`releaseLocked(token)`，token 不匹配即无操作），因此 direct executor 同一 fatal 穿过内层 drain 与外层 execute 两个 frame 时，旧 frame 不可能清掉后继 slot。fatal `Error` 保留原对象身份并在自己释放后继续传播；普通 `RuntimeException` 隔离（已知 persistence/store/failure 边界已记录 status/-1/固定词表，不新造标签）；`executor.execute` 抛 `Error`（非拒绝）同样只释放自己的 token 后原样重抛，不计 `droppedSchedules`。单 drain+dirty、无自动 retry、close 非阻塞且 accepted 工作收尾、恢复/失败状态与隐私词表不变。

确定性 host 回归 `tests/RuntimeLogWorkerErrorTest.java`（load/restore/failure-callback/save/execute 五条真实 Error 路径 + R1 interleave：direct executor 双层 fatal 窗口内后继在真实 save 阻塞、第三 schedule 只 dirty 不新增 drain + close 期间阻塞 IO/closed 拒新 + reject-before-run 恢复）接入既有 `runtimeLogCore` 入口，JUnit 数不变；隔离副本 a354fb（R1 有效 red 输入）interleave 目标 AssertionError（`submissions=4`）→ 修复后 66 checks green 已本机核验。真实设备/SAF/磁盘故障仍 not run。

## Worker R2 残留（llm-asr-rpb，2026-10-20，含 R2 测试/收尾）

R1 产品修复冻结后只补测试与文稿。新组 `close-fatal`/`reject-window`/`teardown-hold`（同一文件、同一 `runtimeLogCore` 入口，JUnit 数不变）：

- **close-fatal**：真实 save 在受控 `File.getCanonicalFile`（相同真实生产路径）被 latch 阻塞时先 `close`，外部证明 close 不等 IO、closed 无新准入、阻塞期间无新 drain；放开后同一 fatal 抛出，原对象身份保留、worker 实际退出、无 auto retry、不计 `queue-full`/dropped，失败 save 未写任何 staging 文件且已持久文件不变。owned shutdown 不作新观察面，不新增生产 seam。
- **reject-window**：正常恢复后开启 reject-before-run；拒绝先释放自己的 admission，再进入真实 FailureSink 回调有界暂停；窗口内另一线程获后继 slot 并在真实 save 阻塞；旧拒绝 frame 返回后不清后继，第三 schedule 只 dirty 不新增；后继实际结束并持久化，`queue-full` 固定标签与 `droppedSchedules==1` 核实。只用受支持 direct executor 与既有 FailureSink，不引恶意 executor 框架。
- **R2-b 收尾窄修**：每次 main 独立 `RunCleanup`，所有 start/首次观察在保护 try 内、所有 gate 先登记；finally 解控后用总纳秒预算 `join(millis,nanos)`，先看 `isAlive`，正剩余不转成无限 `join(0)`，中断保留且不冒终态。未确认由共享 `finishScope` 标保树，main 使用同一删除 guard，以异常返回宿主（无 `System.exit`）；原 Throwable 主身份不变，清理失败 suppressed。close-block 恢复先持 persistence 锁再 start/waitBlocked。
- **teardown-hold**：真实 save 受 gate 与独立 monitor 控制；共享收尾放 gate 后仍实际 held，999999ns 预算耗尽产生真实未确认错误，再调用 main 同一删除 guard 实际拒删并观察目录仍在。外部断言观察中断、同一主 Throwable 带两个 cleanup suppressed、宿主继续执行；独立 finally 放 monitor 后 bounded 确认实际退出才允许删除。`teardown-interrupt` 专用于宿主验证首次 await 被中断的失败路径。无专用 oldcleanup/ENFORCE 分支或生产 seam。

状态：R1 / R2-a 已 CLOSED；R2-b 修复待 fresh B1–B5 回审，不能用旧成功链代替失败路径证明。新证据 `.work/rpb-build/r2b-fix/`；旧 `.work/rpb-build/r2-fix/`（含专用旧清理演示的历史 red/green）字节保留，不再称其证明共享拒删实现。真实设备/SAF/磁盘故障仍 not run。

## 模型维护日志闭环（llm-asr-mfe，2026-10-20）

本切片仅修模型 IMPORT/VERIFY/DELETE 的 best-effort 日志：已准入 STARTED 与正常决定的终态在状态安装后、controller lock 外先 emit 再通知 presentation；`finish` 只在自身合成 FAILED 时补既有失败事件，不重复正常 terminal，INSPECT 不产生模型维护事件。保留原 observer/worker Error 传播、optional telemetry 隔离、业务状态/ready epoch/取消仲裁及 owner/control 最终释放；不加入字段、事件、outbox、重试或原始异常内容。健康 Store 的每操作日志路径一次尝试不等于坏 Store、磁盘持久化或进程 fatal 存活保证。

`tests/ModelManagementControllerTest.java` 默认 main 与既有 JUnit `modelManagementController` 直接运行新增 logging-enabled 行为组（无新 JUnit 方法/host wrapper）：三种 admitted executor 拒绝、真实 queued worker abnormal unwind、已决定 terminal/pending observer Error，以及正常 success/failure/cancel、无重复、INSPECT/busy/stale、重试新 UUID、RuntimeException/Error telemetry 隔离与合成隐私 sentinel 的真实 codec 导出。断言均在通知回调外，使用同步 Queue；新增 fixture 确认任务实际结束才清理合成模型目录。

当前验证结果、输入身份、失败与边界见 [本切片报告](../reports/runtime-log-model-mfe/summary.md)。near-Long.MAX 历史恢复后耗尽及 picker 失败原因覆盖仍未实施；不因本切片关闭 mfe。完整工程构建/新 APK/Android runner/设备未完成前不称交付。

## 合法极限序号恢复与继续追加（llm-asr-mfe，2026-10-20）

`RuntimeLogStore` 在有效 append 真正遇到 `nextSequence == Long.MAX_VALUE` 时，先验证本次事件、复制当前保留窗口为密集序号并加入新事件，成功后才安装与按原容量 trim。普通历史/间隙和最后合法的 MAX-1 序号不提前改写；不靠预留固定 N 个序号推迟失败。恢复仍先验证完整历史，仅原 offset 无法表示时密集复制 `history || early`，再 trim；严格合法极限历史保留且 Worker 初始恢复状态为 OK，而非丢弃标 CORRUPT。真正非法/乱序/坏文件仍拒绝。只有真实 trim 计 dropped，重编号不计；时间、elapsed、来源、UUID、固定事件/详情及旧不可变 snapshot 全保持。

序号只用于一张快照内严格排序，不是跨恢复或极限重排的永久票据；极限处下一张快照 numeric sequence 可以回落。原单 publisher/coalescing 的因果先后仍保持，publish/trim 主体、Worker/Persistence/Event/Codec/接口均不改。V1 保存/重启恢复及冻结导出兼容，不新增日志内容、格式、权限或依赖。

默认 `RuntimeLogCoreTest`/原 `runtimeLogCore` JUnit 入口新增四组，覆盖真实持久化 noearly/early 边界、多次重启继续追加、容量/drop、普通 gap/early offset、非法 append/clock 无副作用、所有事件字段与旧导出快照不变、非法历史/文件、sink 故障/再入及有界跨界并发。旧 MAX-1+early 的 CORRUPT 断言明确改为合法保留/OK，并保留实际非法历史及坏 V1 文件 CORRUPT 保护。当前真实红绿与后续 targeted/复杂度状态见 [本切片报告](../reports/runtime-log-sequence-mfe/summary.md)；未 fresh 审查/full/安全本地提交，不据此交付新 APK。设备按用户选择跳过，仍为 not run；picker 失败文案项仍未完成，不关闭 mfe/M4/M5。

## 证据边界
- 主机fake验证不了Android系统picker/ContentResolver、真实JNI回调时刻与耗时、Activity前台绘制/IME、SELinux及实际磁盘故障。
- native编译和源码检查不能替代真机加载/推理。新增回调会改变JNI DSO，不能沿用前版DSO逐字相同结论；底层MNN对象身份可独立核对。
- 不加入READ_LOGS、存储或INTERNET权限。导出到云提供方可能联网同步，由用户选择。
