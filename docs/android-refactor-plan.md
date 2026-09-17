# Android 重构优化规划（Phase19 起）

## 1. 目标与原则

基于当前 0.6-debug 工作区（包含未提交源码），降低跨功能依赖和公共组件的业务耦合，保留可验证的文件安全、任务所有权、录音、IME 隐私和日志契约。采用单体应用内功能模块化、轻量分层、AppGraph 手工装配；不以减少类数/行数作为成功标准。

用户已授权先编写规划再执行。本次先完整交付 **R0–R4 行为保持批次**，R5/R6 是后续独立行为/目录迁移批次，不混进此次构建。所有状态以实际测试和审查证据更新，不能将规划当实现。

## 2. 当前基线与已知问题

- 55 个 Java 生产源文件，约 5092 物理行；22 个 Java 测试源文件；直接 SDK/javac/d8 构建，无 Gradle。
- 模型仓库和 ModelReadiness 使用 ModelManagementState 的 ImportPlan/FileDetail，页面 DTO 反向包含空间预算规则。
- RequestRunner 混合通用执行与 App 文本/报告/录音文案；IME 推理使用 MAINTENANCE 绕开副作用，模型维护实现空状态/报告接口。
- TaskCoordinator 的共享变化借道 AppState，IME 和模型页为 busy 状态订阅 App 文本状态。
- AsrOperation 与 ImeBackend 反向引用 MainActivity JNI 静态方法，装配重复；AsrOperation 混入报告 JSON 和摘要 I/O。
- RuntimeLogStore 通知 sink 抛 Error 后 publishing 标记可能滞留；需要生产代码红绿回归，不仅测试原 ASR 成功。
- TXT 外部 provider 写入仍占共享 ASR owner；这是后续 R5 可用性优化，不能用超时提前解锁解决。
- 历史审计 docs/code-complexity-review.md 对应旧版，不用其中行号描述当前源码。

## 3. 本轮执行：R0–R4

### R0：恢复、基线、可回退证据
- [x] 完整恢复 planning 文件与 catchup，保留工作区未提交成果。
- [x] 保存当前 Android/native/tests/scripts 小文件快照、SHA 与旧 APK/报告；不创建 Git 提交。
- [x] 复跑全量 host 与 Android javac 基线；失败先定位，不用编译失败充当行为红证据。

### R1：模型边界解耦
- [x] 将 ImportPlan/FileDetail 移入纯模型结果类型（例如 ModelReports），业务核心不再依赖 ModelManagementState。
- [x] 保持字段、预算计算、SHA、复用、取消/发布及终态顺序不变；同步 host、mutation 和接线检查。
- [ ] 收敛重复 SHA/verify 入口：本轮明确延后（保留异常/边界差异及最终全量验证），随R6独立处理。
- [x] 增加自动架构约束，防止 model 核心重新引用页面状态。
- 仓库文案类型化若造成过大行为变化可另列后续，必须报告实际范围。

### R2：共享任务机制与业务策略分离
- [x] 用明确业务生命周期回调替代 RequestRunner 对正文、报告、verified、TaskKind 的隐式策略；或采用经测试的最小等效设计。
- [x] App 明确拥有 pending/terminal/failure/cancel 报告和状态文案，IME 只拥有 session 预览，模型维护只拥有自己的状态。
- [x] 保持同步 admission、busy 拒绝、提交失败回滚、finish-once、cleanup 和 release 次序；旧结果清除/报告策略不变。
- [x] TaskCoordinator 提供直接失效通知，App/IME/模型页各自订阅，不再通过 AppState 中转 busy。
- [x] 删掉无生产需求的 verified 空端口/兼容入口前检查所有调用与测试，测试改为新生产契约，不能削弱断言。

### R3：Android 装配与报告职责
- [x] AppGraph 集中装配推理依赖，ImeBackend 接收必要依赖而非整个 graph。
- [x] 独立 JniNativeTranscription 实现 NativeTranscription；App/IME 推理不再引用 MainActivity。
- [x] 若迁移 JNI 符号，Java/C++/source guards/包检查同步，保持 byte[] UTF-8 协议、回调 ABI、mutex、引擎配置及 MNN 对象不变。
- [x] 将 App 报告 JSON、摘要和原子写入移到专属报告组件，AsrOperation 保留用例编排；失败/取消/成功格式和临时文件清理不变。

### R4：可靠性、回归与交付
- [x] RuntimeLogStore sink Error 后分发可恢复：明确非致命观测异常隔离与 fatal VM 错误传播，标记不能永久滞留；验证恢复、重入与并发快照次序。
- [x] 全量 host、mutation、架构约束、Android javac、完整 APK/DEX/JNI/签名/权限/报告绑定检查。
- [x] 源码冻结后 fresh 只读独立复核；单 writer 集中修复，变更后重测，不用旧审查归因新源码。
- [x] 更新实施/验证报告、规划状态与 APK 身份；当前保留 0.6-debug，以新 SHA 区分结构重构构建，不冒充设备验收。

## 4. R5/R6 独立批次（Phase20 用户已授权实施）

### R5：TXT 导出隔离和状态所有权

先定义并测试 begin/clear/admit/write 的线性化点：清除撤销尚未开始写的票据；写入开始后外部副作用不可撤回，需用户可见说明。独立有界导出 owner 覆盖 provider open/write/close；禁止无界队列、后台自动重试及超时提前释放旧 worker。不能直接把日志导出生命周期套到文本导出。增加 ResultState/revision 与页面 command/render 分离，在不共享 IME 正文的前提下简化 App 状态。

### R6：按功能拆包及剩余策略整理

目标 app/task/asr/audio/model/transcription/ime/modelmanagement/diagnostics。先落实依赖方向再移动文件；更新递归源码发现、测试编译/变异副本、Manifest/JNI/构建输入和包断言。优先小包而非多 Gradle 模块。模型文件结果文案类型化、controller 锁外通知、日志编码从 persistence worker 归位等按独立测试逐项落地，不造通用 EventBus、SAF 基类或 UseCase 框架。

## 5. 必须保持的不变量

1. 推理和模型修改仍共享同一个 owner；取消必须等待真实 I/O/清理收尾，不承诺 JNI 可安全中止。
2. RecordingControl/ForegroundRecorder 硬件 start/cancel/commit 逻辑不改；无后台自动录音。
3. IME 旧字段/旧 revision 无法接收新结果，确认前不提交，正文不进入 App 报告或日志。
4. READY 只能来自当前 epoch 的 SHA 证明；固定清单、大小/SHA、受限 .part、原子发布和白名单删除规则不放宽。
5. 外部 provider URI、正文和原始异常不进入运行日志；持久化和外部日志导出继续独立。
6. 保留用户所有未提交成果；不 reset/stash/commit/push、不下载模型、不访问设备或私人文件/麦克风，不增加网络/存储权限。
7. 现有模型数学、权重和 MNN 编译对象不变；JNI 桥迁移的库身份需重新核验，不宣称新库与旧库逐字相同。

## 6. 验证矩阵

| 层次 | 验证 | 不能证明 |
|---|---|---|
| 核心行为 | 真实生产组件 + fake executor/stream/native，busy/admission/失败/清理/取消、模型、IME、日志全部回归 | Android 回调与硬件语义 |
| 行为红绿 | 日志 Error 后继续分发，红必须来自断言且旧源码可编译 | 线上已发生故障 |
| 架构约束 | model 核心无页面状态依赖；推理不引用 MainActivity；TaskCoordinator 通知不借 AppState | 所有动态运行正确性 |
| Android 编译 | SDK35 Java8 编译、d8、资源及 JNI 链接 | 真实 SAF/InputConnection/JNI 执行 |
| 产物 | APK SHA、精确权限/组件/ABI、资产、源码指纹与报告绑定 | 用户手机已验收 |
| 手动设备 | 示例、WAV、录音/取消、模型导入/删除、IME切换、日志导出与重建 | 本轮不自动执行，保留 pending |

## 7. 迭代与阻塞策略

一次只有一个源码 writer，分小 slice，父会话维护规划、基线和独立验收。每阶段先更新本文件与 task_plan/progress/findings，再进入下一阶段。工具/编译失败完整记录，修正原因后换路径验证；同一阻塞三种有效方案仍失败则停止相关分支并报告。不得为赶进度放宽安全断言或把仅静态检查称运行成功。

## 8. 实施状态

R0–R4 已实现、完整构建和独立复核交付；Phase20已开始R5/R6，基线通过，实施中，设备回归pending。详见 [验证记录](android-refactor-validation.md)。

### R1 执行结果
ModelReports已提取，预算与文件算法保持；架构负例/全部host/Android javac通过。SHA重复入口与文案枚举明确延后。日志Error恢复窄修复已通过旧生产红/新生产绿，Error仍传播以保留遥测不完整语义，详见progress与.work/refactor-phase19-r1日志。R2开始。

### R2 执行结果
生产AppRequestPolicy与公共Lifecycle已分离，IME使用INFERENCE且无App端口；coordinator直接订阅，移除verified兼容/模型诊断构造器。父host与Android javac复跑exit0；R3开始。

### R3 执行结果
AppReportWriter保留原报告方法体/字段/异常；Graph只new一次InferenceAdapter注入App/IME，JNI声明已搬到独立类，C++仅四处类符号替换。writer全host/Android编译与真实ARM64 JNI object、javac-h签名和nm检查通过；完整链接/独立review进入R4。

### R4 最终结果
完整构建exit0/APK_READY，111构建输入/112冻结输入与最终APK匹配；独立双审与两文件窄复核完成。修复了观察器内无效断言和完整构建生成JNI头防线缺口，产品源码保持双审SHA。
最终APK SHA：`0b891db946992cb649d33b4ec8bf0347738070e4593c306a0558d7f68460e43c`。无设备运行或性能结论，不把Store恢复扩大为Worker Error完整恢复。


### Phase20 分片实施与验收
1. R5先独立实现：ResultState单一正文/revision，编辑拒绝ABA，begin/clear/write-admit共同同步边界；导出own slot覆盖真实provider open/write/close，阻塞不占ASR owner；页面仅命令与渲染，不捕获Activity于worker；status与ASR分离。
2. R6策略独立实现并测试：锁外观察通知（重入/并发不丢更新）、类型化模型文件结果（不改算法/预算）、日志编码回归、SHA入口按实际等价边界收敛或记录不宜合并原因。
3. R6功能包迁移单独实现：先记录允许依赖边再搬迁，禁止以全public/反向app依赖绕过分层；同步编译源枚举、mutant副本、manifest、JNI头/符号及APK源码指纹。
4. 冻结后fresh独立复核；父集中修复并完整构建、确认578 MNN对象/录音逻辑不变。保留0.6版本号，以新APK SHA和本轮报告区分。
设备SAF阻塞/重建/InputConnection与真实JNI运行不在host证据范围，继续标pending。


### Phase20 R5/R6 最终结果
R5 TXT导出独立有界slot/clear epoch/ResultState版本编辑、R6typed模型观察/锁外通知/日志codec/62类功能包迁移完成。原Android注册入口保持；SHA入口因不同取消/限额/异常语义明确保留，未削弱最后校验。全量host、真正fresh classpath变异、Java/NDK object/实际linked DSO/签名包绑定通过；最终双审及5项窄修复复核完成。详见[本轮验证](android-r5-r6-validation.md)，APK SHA `a03c0876ba0f73ccec6532a0eb91c0416a1edb2488b5e57f19f7ffb58b4947f1`。设备未测，未提交。
