# Phase12 当前冻结源码独立只读 review

## 结论与证据边界

**本轮未发现可由当前 0.3 正常生产调用路径证明的发布阻塞。解耦确有实质改善，但不能把 243 个 host checks 表述为 Android 端到端覆盖；存在一项非阻塞的测试证据质量问题。**

审阅对象为 `.work/refactor-baseline/android/app/src` 的旧 0.3、当前 `android/app/src/org/llmasr/minimal` 及当前 tests/scripts；未采用前两稿 review 的结论作为当前证据。以下路径相对于 `/home/zhb/gitrep/llm-asr`，行号来自本轮冻结文件。

独立执行 `bash scripts/nix-env.sh apk bash scripts/test-minimal-apk.sh`，退出成功：**32 + 20 + 41 + 25 + 33 + 15 + 42 + 14 + 22 = 243 checks**，与 `.work/reviews/phase12-parent-tests.txt` 一致。没有完整 APK build，没有设备、麦克风或模型推理，没有子代理，没有改动或暂存任何源码。获准的 host 脚本会生成/覆盖 `.work/build/minimal-apk-tests`、`.work/red` 及临时测试文件；本报告是本轮唯一主动写入的交付文件。

## Findings（最多六项）

### 1. P2／非发布阻塞：部分测试名和注释声称覆盖的错误路径，实际没有被执行

**位置：** `tests/ModelRepositoryTest.java:92-103,142-155,238-269`；`scripts/test-minimal-apk.sh:9-21`；`tests/RequestRunnerTest.java:210-221`。

- “bad-hash” 用例清单期望 1024 字节、实际提供 2048 字节，在 `ModelRepository.java:134` 就因超长退出，**未到 SHA 比较** `:157`。仅移除 SHA 比较不会使这个用例失败。
- “rename failure” 预先建立目标目录；`ModelRepository.java:100` → `FileSafety.java:28` 先拒绝非普通目标，**未到 rename** `ModelRepository.java:144`。它证明的是目录边界，不是发布失败后的残片清理。
- “duplicate” fake 在同一 `HashMap` 连续 put 相同 key，第二次覆盖第一次；随后返回 0 字节输入，测试只断言任何 IOException，实际由 `ModelRepository.java:138` 长度不符通过。**没有验证 SAF 重名拒绝** `SafModelSource.java:42`。
- “over cap” fake 自己直接抛异常，仅证明仓库传播异常，并未执行 `SafModelSource.java:38` 的计数上限。host 编译列表根本不含 SafModelSource。
- `RequestRunnerTest` R11 声称 cleanup 读取了 body 的 `inputWav`，但 fake cleanup 只记录 id/kind，断言也只检查字符串前缀；它不证明路径身份。实际路径身份本轮通过源码追踪确认，见第 4 项。

**影响：** 243 次断言全部真实通过，但不能据此宣称坏 SHA、真实 rename 失败、SAF 枚举边界和 Android cleanup adapter 都已由自动化验证。不是生产缺陷证据，也不推翻其余直接运行生产类的测试。后续应补同长度坏 hash、确实到达发布阶段的失败、明确异常原因和 cleanup 同一对象/同一路径断言；在此之前收窄报告措辞。当前冻结轮不要求改源码。

### 2. 已验证／架构：耦合真正下降，但不是总代码量或 Android 依赖已经消失

**位置：** 旧 `MainActivity.java:188-215,238-300`；当前 `MainActivity.java:48-93,177-194`、`AsrOperation.java:39-53,66-182,205-210`、`OperationContext.java:21-32`、`AppGraph.java:37-46`。

旧 Activity 同时持有执行器、全局 busy、请求身份、报告标记、模型 IO/JNI 和长任务 lambda；当前 Activity 只调用命名操作方法，worker 的 lambda 在 AsrOperation 内构造，捕获操作层和字符串/Uri/RecordingControl 等局部值。`OperationContext.Android` 强制保存 application context；没有从进程级任务到 Activity 实例的强引用路径。UI listener 为 weak-reference 静态类。RequestRunner 和 ModelRepository 的算法已独立为真正由生产调用、可直接 host 执行的纯 Java 类，不是复制到 tests 的业务实现。

量化边界：MainActivity **335→208 行（约下降 38%）**；整个该 Java 包 **8→23 文件、578→1712 行**，包括注释和适配层，不能据此宣称总体复杂度下降同样比例。AsrOperation 仍有 330 行，依赖 Uri/ContentResolver/org.json，且为保留 JNI 符号仍反向静态调用 `MainActivity.invokeTranscribe`。`AppGraph` 仍向 UI 暴露多个部件；这不是彻底单向、全平台无关架构，但不再有 Activity 生命周期与任务资源的原强耦合。

**测试边界：** host 运行的是真实 RequestRunner/TaskCoordinator/ModelRepository 等核心；未编译/运行 MainActivity、AsrOperation、AppGraph、OperationContext、ForegroundRecorder、SafModelSource。`AdmissionBoundaryTest.java:45-57` 自己提供 recording admission/body/finish hooks，证明生产 runner 的时序契约，不等于调用真实 `AsrOperation.startRecording()`。本轮对实际装配的证明来自读源码。

### 3. 已验证／时序：主线程 UI、owner 释放通知以及 admission 后 worker 前失去前台的录音取消均有保护

**位置：** `MainActivity.java:102-135,138-145`；`TaskCoordinator.java:42-60,71-78`；`AppGraph.java:40`；`AsrOperation.java:110-126`；`ForegroundRecorder.java:16-24,42-49`；`tests/AdmissionBoundaryTest.java:49-65,91-96`。

可达时序：主线程点击录音 → coordinator CAS 获取 owner → **同步执行 admitted**，写 volatile `recording=control` → 才提交 worker。随后即使 worker 尚未执行，`onPause()` 也能取到 control 并锁存 cancel；捕获入口先看 cancelled，启动硬件前再经 `RecordingControl.start()` 同一数学 gate。因此不会因为会话只在 worker 中建立而丢取消。commit 仍在 capture finally 的 release 确认之后；commit 赢后无法取消 native 的旧政策保留。

任务结束顺序为 request cleanup/recording finalization → `running=false` → 通知。`AppGraph` 明确把 coordinator 通知接到 AppState，不是只依赖最后一次正文/状态更新；因此状态通知先于 release 时，UI 不会永远停留 busy。UiRefresh 始终 `Handler(Looper.getMainLooper()).post`，执行时读取**当前** owner，且检查 foreground/destroyed/finishing；onResume 同步刷新，onPause 移除 listener。未见 worker 直接操作 View。初始化仍在 onCreate 读取有 1 MiB 上限的清单并检查模型目录，不应声称“所有文件 IO 均离开主线程”；模型复制、哈希、WAV、JNI 不在主线程。

queued-executor 测试实际验证了 admission-before-worker、cancel 后 backend 不启动、completion 通知观察 owner=false、拒绝执行时清会话并释放 owner。真实硬件 start/release 延迟不在此证明范围。

### 4. 已验证／事务：preflight、pending 失败均进入失败终态尝试；cleanup 身份为请求局部

**位置：** `RequestRunner.java:49-68,70-94`；`RequestContext.java:10-18`；`AsrOperation.java:47-53,186-190,232-237,286-294`；`tests/RequestRunnerTest.java:224-249`；`tests/AdmissionBoundaryTest.java:84-89`。

非 maintenance 的正文清空在 admission 完成，而不是推迟到 worker。preflight 与 writePending 均位于同一 try 内；任一抛 Exception/LinkageError 会跳过 body/成功报告，尝试为**同一 RequestContext/request_id**写失败报告，finally 始终调用 cleanup。取消独立写 cancelled；报告自身失败会显式显示持久化失败，而不是静默成功。maintenance 保留正文且不覆盖推理报告，和旧 launch(report=false) 政策一致。

WAV 路径先以 UUID 构造并存入 `ctx.inputWav`，再 canonicalize，因此部分写入失败也能按该请求的路径删除；没有 Activity.requestId 或永久 request-id map。cleanup/finalization 在 coordinator 释放前执行，不会删除下一请求的 WAV。推理详细报告写完才设置 `ctx.inferenceReported=true`，不会被默认 model-operation 终态覆写。

**限定：** “也终态”是运行器必然尝试终态、UI 明确失败，不是存储不可写时仍能保证磁盘终态。`saveReport` 的失败 `.part` 不由 WAV cleanup 当场清理，后续请求/startup 的 UUID 白名单 preflight 负责恢复；`ResultFiles.java:10-25` 与旧版逐字相同。未发现新跨请求误删路径。

### 5. 已验证／导出：快照、清除失效化、实际写入使用同一 owner，旧票不能跨清除写出

**位置：** `ExportSession.java:27-33,49-62`；`MainActivity.java:81-89,177-185,204-206`；`AsrOperation.java:128-149`；`tests/AdmissionBoundaryTest.java:72-82`。

快照读取在 `coordinator.withOwnership` 内，所以 clear 已拿到 owner 但尚未擦正文时，导出不会读取旧文本。picker 等待期间释放 owner 是有意设计；clear 的 `invalidate()` 和删除/擦正文在同一个 maintenance owner 内。返回后 `exportText` 再取得**同一个 coordinator**，先验 epoch，再打开目标流并写入；clear 无法穿插到校验与写入之间。clear 先赢则旧 ticket 拒绝，export 先赢则写入发生在 clear 之前。Activity 本地 requestCode 和 onDestroy abandon 防止重建页面消费旧票，取消也不写目标。

`beginOwned(AtomicBoolean, ...)` 保留为旧 host 测试入口，生产无调用；旧 ResultFilesTest 的 raw-gate 测试不能单独证明当前装配，但新增 AdmissionBoundaryTest 确实直接运行 `beginUnderCoordinator` 和真实 runner/coordinator。实际 SAF 输出流失败/close 失败的 Android 装配仍仅静态验证。

### 6. 已验证／模型与旧 gate：固定清单、先校验再单文件原子发布、残片回收顺序正确；JNI/数学 gate 未改

**位置：** `AppGraph.java:58-79`；`ModelManifest.java:23-44`；`ModelRepository.java:59-76,99-115,123-150`；`AsrOperation.java:97-107`；`SafModelSource.java:28-43`；`scripts/build-minimal-apk.sh:19-32`；`tests/PartRecoveryTest.java:68-100,117-165`。

清单只从 bundled asset 解析为不可变 entries，不采信导入目录的清单；构建脚本固定七个名称，并校验小配置及其相对引用。清单拒绝空、重复、非法名称/大小/SHA 和 `.part` 名碰撞。文件入口检查目录根、路径边界、symlink 和非普通对象。实际 startImport 在枚举之前先回收**所有固定清单残片**，所以即使残片属于另一个或已验证文件，也不会占空间阻断当前文件；仓库逐项另有 reclaim-before-space 防线。

复制受清单长度约束，两个流正常关闭、size+SHA 均通过后，才将同目录 `.part` rename 成正式文件；没有先删旧目标的空窗；最后 verifyAll，AsrOperation 才设置 verified=true。复用文件也先 size+SHA。独立重跑 PartRecoveryTest 运行的 green 是生产 importFrom，red 是只交换回收/空间检查次序的隔离副本，实际日志 `.work/red/buggy-run.log` 为 `EXPECTED_FAIL 存储空间不足：p.bin`。并非测试先手工回收再伪称生产修复。

这里的“原子”是应用私有同目录、Android/Linux 上的**单文件 rename 发布**，不是七文件全事务，也不是 fsync 断电耐久保证；固定清单和最终校验/下次重验是其恢复边界。未要求本阶段引入版本化模型等 0.4 功能。

基线逐字比较：`RecordingControl`、`ForegroundRecorder`、`PcmWave`、`WaveInput`、`AsrText`、`ResultFiles` 全部相同。RecordingControl SHA 为 `50bd19ba89a63cb1c2d2e292b1e042b81528514444721df67af0c04544de519f`，匹配 `.work/refactor-baseline/sha256.json`。源码基线目录未包含 JNI，故另用旧 `reports/apk/pre-refactor-0.3/build-input-sha256.json` **仅作旧输入身份凭据**：当前 `native/apk/asr_jni.cpp` SHA `67dfeaa2f286df633726d1d7b90e8822352f2fb50bb40100554920ce17bd3dbd`、固定模型清单、link 脚本均匹配旧记录；JNI git diff 为空。JNI 符号仍为 `Java_org_llmasr_minimal_MainActivity_transcribe`（native `:23`），128 token、CPU/线程、每请求析构策略未改。没有据此声称本轮验证了 APK 中二进制或设备行为。

## 残余风险与交付意见

- 不阻塞当前冻结重构，但父报告应明确第 1 项测试证据缺口，避免把 PASS 总数等同于错误分支覆盖率。
- 主线程生命周期、真实 AudioRecord 和 SAF、JSON/报告落盘 adapter、APK/native 打包身份未通过本轮运行验证；父会话构建结果可补打包证据，不能替代设备证据。
- 同目录 rename 不代表断电 durability；低存储导致终态持久化自身失败时，磁盘可能仍留旧/pending 报告，UI 会明示失败。此边界未被误判为本次新回归。
- 本轮没有可达的新数据越界、owner 丢失、取消丢失或 Activity 长任务持有问题；不追加未实现的 0.4 功能要求。
