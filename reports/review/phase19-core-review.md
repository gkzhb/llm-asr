## 独立审查结论

**未发现本次 R1/R2/RuntimeLogStore 差异引入的生产安全契约阻塞；发现一项新增测试的断言会被吞掉，应阻塞“新增测试可靠性已验收”的结论。**

已独立比较指定基线与当前文件，未沿用 writer 结论。两次核验 `.work/refactor-phase19-final/frozen.json`：**112 项全部匹配，无冻结漂移**。全程只读，未写源码、规划或报告文件，未运行测试或构建；暂存区检查为空。

以下路径均相对于 `/home/zhb/gitrep/llm-asr`。

## 具体发现

### 阻塞项：测试可靠性，不是已证实的生产回退

**[P2] `tests/TaskCoordinatorTest.java:108–110`：监听器内的关键断言会被生产隔离逻辑吞掉。**

- 第 110 行在监听器中执行 `check(finalized.get() == 1)`，试图验证释放通知发生前资源已经完成收尾。
- `android/app/src/org/llmasr/minimal/TaskCoordinator.java:78–82` 对非致命 `Throwable` 做隔离，其中包括该断言抛出的 `AssertionError`。
- 测试第 109 行先增加调用次数、记录 busy；外围第 118–119 行只检查次数和 busy 序列。因此，即使第 110 行失败，这个用例仍可能通过。
- 这是可从控制流直接确认的假阳性，不需要运行测试才能成立。

**处置建议：**监听器只记录观察值，在 `queue.drain()` 后的测试线程断言；或保存回调失败，再在外围显式检查。`AdmissionBoundaryTest.java:110–122` 已采用外围检查顺序的方式，其错误路径覆盖不能代替修正这一处失效断言。

### 非阻塞项与测试缺口

**[P2，覆盖缺口] `tests/RuntimeLogStoreErrorTest.java:54–93` 没有覆盖正常释放瞬间的跨线程 publisher 交接。**

第 85 行先等待全部并发生产者退出，第 86 行才放行原 publisher；第 92 行的下一次 append 又发生在 publisher 已 join 后。该用例可靠覆盖并发积累、一次重入及闲置后重启，但**不能证明正常释放后，旧 publisher 的 finally 不会误清新 publisher 的所有权**。当前实现经静态检查正确，故此项不认定为生产阻塞。

**[P3，范围限制] Store 的 Error 恢复不等于持久化 worker 的 Error 恢复。**

- `android/app/src/org/llmasr/minimal/RuntimeLogStore.java:126–132` 恢复的是 `pending/publishing`。
- 未改动的 `android/app/src/org/llmasr/minimal/RuntimeLogWorker.java:98–130` 没有对应的异常退出复位；例如 `store.restore(history)` 的 sink 抛出 Error，worker 可在 `scheduled=true` 时退出，后续调度在第 81 行直接返回。
- 这是**基线已有残余风险**，不是本次新增回退；但不能把本次结论扩大为“日志持久化链路在 Error 后全面恢复”。

## 安全契约逐项判断

### 1. ModelReports：确认仅迁移类型及引用

进行了只读文本归一化比较：

- `android/app/src/org/llmasr/minimal/ModelRepository.java` 与基线在替换 `ModelManagementState.` → `ModelReports.` 后**全文相同**。
- `android/app/src/org/llmasr/minimal/ModelReadiness.java` 同样**全文相同**。
- `android/app/src/org/llmasr/minimal/ModelReports.java:18–47` 的两个类体，与原嵌套 `ImportPlan`、`FileDetail` **逐字相同**。

因此，零复制零额外预算、`Math.addExact` 溢出拒绝、列表防御复制、SHA/epoch 证明、不制造 readiness 等运行时语义保持。旧嵌套类型名称被移除，属于源码/二进制 API 变化，不应描述成外部 API 完全兼容；冻结输入内的调用点已迁移。

### 2. RequestRunner / AppRequestPolicy：基线事务顺序保持

关键位置：

- `android/app/src/org/llmasr/minimal/RequestRunner.java:42–61`
- `android/app/src/org/llmasr/minimal/RequestRunner.java:64–76`
- `android/app/src/org/llmasr/minimal/AppRequestPolicy.java:25–53`

确认：

- owner 获取后同步 admission，随后才允许 worker 执行；
- preflight → pending → body → terminal 的顺序不变；
- `inferenceReported` 避免重复 terminal；
- MAINTENANCE 不清文本、不写 App 报告的规则迁入 App policy，而非残留在通用 runner；
- busy 不执行资源 admission/finalization；executor rejection 对已 admission 的资源做一次 finalization；
- cleanup 位于 finally，finalization 又位于其外围 finally，最后释放 owner；
- `Exception | LinkageError` 的失败处理以及其他 Error 向外传播、同时展开清理的边界保持。

未发现提前释放、重复 finalization，或因提取 policy 而跳过 cleanup 的新增路径。

### 3. TaskCoordinator：所有权保持，监听异常策略有明确变化

`android/app/src/org/llmasr/minimal/TaskCoordinator.java:45–76` 保持 owner CAS、提交失败回滚和一次释放；第 76 行新增 finally，保证释放通知抛出致命错误时 completion latch 仍被释放。

第 78–82 行**不是纯结构迁移**：原先仅隔离 `RuntimeException`，现在隔离除 `VirtualMachineError/ThreadDeath` 外的 `Throwable`。这改善普通监听器故障隔离，但也正是上述测试断言失效的原因。

订阅生命周期配对及弱 UI 目标已核对：

- `MainActivity.java:121–148`
- `ModelManagementActivity.java:201–215,240–259`
- `AsrImeService.java:35–38,121–124,158–166`

未发现此次改动引入的 Activity/Service 强目标保留，或释放后仍依赖 App 文本通知刷新 busy 的遗漏。

### 4. IME / model：隔离保持，不再依赖 maintenance 绕过 App policy

- `android/app/src/org/llmasr/minimal/ImeController.java:63–96` 使用私有 lifecycle，任务元数据改为 `INFERENCE`；session/revision 检查、录音提交门槛、preview 发布和收尾身份判断保持。
- `android/app/src/org/llmasr/minimal/ModelManagementController.java:25–33,56–68,246–261` 不再注入 App 状态/报告端口；模型终态及 operation 完成仍由自身逻辑处理，并位于共享 owner 内。
- `android/app/src/org/llmasr/minimal/AppGraph.java:51–56` 共享的 `InferenceAdapter` 没有新增请求级可变字段；`InferenceAdapter.java:25–27` 仍逐请求创建 phase listener。

未发现 IME 写 App 报告、覆盖 App 文本，或 model 维护任务制造 SHA readiness 的新增路径。Android 实际旋转、销毁和消息队列行为此次未执行验证。

### 5. RuntimeLogStore：正常原子释放正确，Error 后为“后续触发恢复”

`android/app/src/org/llmasr/minimal/RuntimeLogStore.java:103–132`：

- 第 116 行的空队列检查与 `publishing=false` 位于同一 monitor 临界区；
- `released=true` 阻止正常退出 finally 再次清除后继 publisher；
- 快照成功复制后才清 `pending`；
- sink 回调仍在 monitor 外；
- Error 不被降格成成功；异常 finally 恢复 `pending=true/publishing=false`，后续有效 append 能重新发布。

**限制：**Error 会中断当轮后续 sink；移除坏 sink 本身不会触发重发，没有下一次发布触发时也没有立即补发保证。这与新增测试实际验证的“后续 append 恢复”一致。

## 新增测试可靠性总结

- `AdmissionBoundaryTest.java:99–122` 用异常对象身份检查，捕获范围虽宽，但不会把自己的 “error swallowed” 哨兵误认成预期错误；顺序断言在外围，可靠。
- `RuntimeLogStoreErrorTest.java:29–51` 对 Error 做身份检查；第 58、63、71、81、87 行将工作线程失败带回主线程，未静默忽略断言。
- `RuntimeLogStoreErrorTest.java:12–16,57–69` 等待/join 有 3 秒上限，重入受一次 CAS 限制；未发现新增测试无界重入。
- `TaskCoordinator.awaitFree()` 有 5 秒上限；测试脚本 `scripts/test-minimal-apk.sh:36` 还有进程级 timeout。
- 除已指出的监听器断言外，尚缺正常 publisher 交接边界，以及自定义 lifecycle 在取消/cleanupFailed 回调抛错等更完整矩阵。不能以本次静态审查替代测试执行证明。

**最终判断：静态证据支持维持本次范围内的基线生产安全契约；新增测试可靠性存在一处应修正的验收阻塞。**