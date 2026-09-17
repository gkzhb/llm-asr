# Phase19 Android 重构验证记录

## 结论

**R0–R4工程交付完成：源码实现、完整APK、独立双审与最终窄复核通过。** R5 TXT并发隔离、R6全量拆包等后续批次未实施；真实设备运行pending。

- APK：`dist/qwen-asr-minimal-debug.apk`
- 版本：0.6-debug/code6，2,466,664 bytes
- SHA-256：`0b891db946992cb649d33b4ec8bf0347738070e4593c306a0558d7f68460e43c`
- 最终完整构建：`.work/refactor-phase19-final/build-final.log`，exit0/APK_READY。
- 身份与限制：`reports/apk/result.json`；111构建输入、112冻结输入均匹配，41项本轮变更输入全部在构建指纹内，APK与6报告绑定通过。

## 输入与回退

起点为当前未提交0.6工作区，不以Git HEAD 0.2作为基线。100个小文件快照在`.work/refactor-phase19-baseline/inputs/`，对应`sha256.json`。旧APK为`dist/pre-refactor-0.6/qwen-asr-minimal-debug.apk`；旧报告在`.work/refactor-phase19-baseline/apk-reports/`。未Git提交/推送，未改变模型/权重/MNN对象/录音gate。

## 实施结果

| 项目 | 结果 |
|---|---|
| 模型边界 | ModelReports承接ImportPlan/FileDetail；仓库/readiness不再依赖页面状态，文件算法和预算不变 |
| 请求机制 | RequestRunner仅处理Lifecycle与事务展开；AppRequestPolicy拥有正文/报告策略；IME使用私有INFERENCE策略；模型去掉伪App端口 |
| 状态通知 | coordinator直接add/remove Listener，页面不再借AppState获取busy；弱UI目标/主线程/生命周期检查保留 |
| 推理装配 | Graph创建一个InferenceAdapter，注入App与窄依赖ImeBackend；独立JniNativeTranscription，构造不加载库 |
| 报告职责 | AppReportWriter承接原JSON/摘要/UUID part/rename，成功发布顺序不变 |
| 日志可靠性 | RuntimeLogStore异常释放publisher并保留pending，下次append恢复；Error仍传播使遥测不完整语义保留 |

## 执行证据

以下R0/R1/R2/R3简称目录分别为`.work/refactor-phase19-baseline`、`.work/refactor-phase19-r1`、`.work/refactor-phase19-r2`、`.work/refactor-phase19-r3`。

| 验证 | 证据 | 结果 |
|---|---|---|
| 起点全host/Android编译 | R0/host.log、javac.log | exit0 |
| 日志恢复旧代码红 | R1/red-compile.log、red.log | 编译exit0，运行exit1：后续append未通知的明确断言；不是编译失败/超时 |
| R1生产绿 | R1/host-final.log、javac.log | 全host含恢复/fatal身份/有界并发/一次重入和架构负例、Android编译通过 |
| R2父独立复跑 | R2/parent-host.log、parent-javac.log | 全host/Android通过，执行实际App策略和IME/model编排 |
| R3分层验证 | R3/host-final.log、android-javac.log、jni-object.log | host/Android/ARM64 object及Java派生头/符号检查通过 |
| 监听断言负例 | `.work/refactor-phase19-final/assertion-negative/` | 同测试仅把观察值改0，javac通过，主线程AssertionError exit1；真实全host fix-host.log通过 |
| 最终完整构建 | `.work/refactor-phase19-final/build-final.log` | host→Java派生JNI头/ARM64 C++→实际链接→全Java/native声明与DSO导出→资源/DEX/签名/对齐/包与绑定全部通过 |
| 最终输入身份 | frozen.json、reports/apk/build-input-sha256.json | 112/111全部当前匹配，41变更全部构建绑定；578个MNN对象与R0相同 |

测试单位不相加：最终包含57 request runner、22 coordinator、67 admission、177 IME、日志恢复20 checks、日志core8组、adapter10组354 checks、日志导出96 checks，以及原parser/model/readiness/cancel/delete/provider/alias/part、8 review组、3个可编译mutants、词法/报告/JNI负例、APK绑定fixture。完整列表以最终日志为准。

## 审查与失败处置

- [核心双审报告](../reports/review/phase19-core-review.md)
- [Android/JNI双审报告](../reports/review/phase19-android-jni-review.md)
- [最终两文件窄复核](../reports/review/phase19-final-fix-review.md)
- [逐项处置](../reports/review/phase19-disposition.md)

独立审查仅读源码/日志、不运行测试；执行证据由父/实现任务提供。原报告未被改写。产品Java/C++保持双审版本，最终只改测试外部断言和构建JNI前置，窄复核确认112冻结中仅此两项变化且无新增阻塞。

R1子writer20分钟超时，无最终报告；父拒绝日志初稿和不可靠测试，修复正常释放原子性、有界重入和线程失败传回。父首次吞AssertionError被既有adapter测试拒绝后，保留Error传播策略，仅修publisher收尾。R2两次fixture编译错误及postDelayed词法守卫过窄均修复；所有失败记录见task_plan/progress，不将管道exit0、编译失败或超时冒充行为红绿。

## 剩余风险与非目标

1. 真实SAF、AudioRecord、InputConnection、系统回调顺序、UI效果和普通APK JNI执行未在设备验证；报告适配器为源码体保持+Android编译，不是JSONObject host运行。
2. RuntimeLogStore恢复要有后续发布触发，Error会中断当轮后续sink；未修RuntimeLogWorker自身Error时scheduled滞留。特定正常publisher交接未有定向确定性测试，独立静态检查当前实现正确。
3. TXT导出仍占ASR owner，provider可阻塞；R5独立设计clear/write线性化与用户提示。不能超时提前放锁。
4. R6拆包/仓库文案枚举/controller锁外通知/SHA入口统一尚未实施；现包结构仍为org.llmasr.minimal。
5. JNI不可安全中止；日志异步保存无断电fsync保证、序列耗尽等历史defer不因本次完成而消失。
6. 不承诺启动/推理速度、内存、总行数或圈复杂度的量化改进。JNI DSO身份改变但MNN对象不变。

## 建议手机回归

覆盖安装后分别验证：示例/WAV/录音停止和取消；模型导入/复用/校验/删除；IME新字段/切换/预览确认；运行日志/导出；页面重建和切后台。确认模型保留、IME正文不进入App报告、旧会话不自动提交、日志无正文/路径。不自动操作设备或麦克风。
