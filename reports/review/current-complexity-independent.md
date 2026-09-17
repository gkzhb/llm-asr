# 当前 Android / APK JNI / tests 复杂度与重构优先级审计

## 结论与范围

**建议先小范围收拢任务所有权，再实现模型管理的下一增量；不建议重写已建立保护的录音 gate 或导出 epoch，也不建议先重构 JNI 引擎。** 当前复杂性主要来自生命周期、任务身份、文件事务和 UI 状态交织，而非已测得的圈复杂度；本轮没有运行复杂度分析器，不能用源码行数代替圈复杂度。

审计对象为当前工作树（包括未提交的 `ExportSession.java`、`ResultFiles.java` 和相关测试），不是仅审 HEAD，也未确认已有 APK 与源码一致。完整阅读 Android Java、`native/apk/asr_jni.cpp`、三个 Java 测试及 `tests/p0/test_audio_contract.py`，辅助核对测试脚本、Manifest 和路线文档。以下路径相对 `/home/zhb/gitrep/llm-asr`，行号对应本轮所读内容。

**0.4 状态确认：导入中的用户取消、应用内模型删除均未实现。** 当前 `android/app/AndroidManifest.xml:2` 仍为 `0.3-debug`；这不是仅凭版本号判断，见发现 2 的实现证据。取消目录选择器、取消录音、清除结果，不等于取消已开始的模型导入或删除模型。

本轮除本指定审计报告外未写入文件；未访问设备、未运行模型、未另起代理，也未运行会产生 class/临时文件的测试脚本。结论属于静态审计，交错是源码可达性推演，不冒称设备复现。审计前工作树已有 23 条 Git 状态记录，暂存区为空，既有改动未处理。交付检查时另外观察到 `docs/app-roadmap.md` 修改及 `docs/code-complexity-review.md` 未跟踪记录（共 25 条）；本轮未写这两个路径，不能声称整个工作树在审计期间冻结。结论以实际读取的源码快照为准。

## 发现（最多五项，按建议实施次序）

### 1. 优先级高｜维护性风险：进程任务所有权有效，但任务上下文仍挂在 Activity 上

**位置：** `android/app/src/org/llmasr/minimal/MainActivity.java:23-37,123-136,186-215,273-300,334`。

- **已确认保护：** `RUNNING.compareAndSet(false,true)` 在提交 worker 前取得进程级所有权；单 worker 串行执行；临时文件清理、pending/终态报告、导入、WAV 和 JNI 均在释放所有权前完成。新的 Activity 同样检查静态 CAS，不能因重建直接并发改写共享文件。`requestId` 虽非 volatile，但经 executor 提交发布，后续任务又受 CAS 约束，不能仅凭其是实例字段便判定存在数据竞争。`current` 的实际 UI 解引用在主线程 Handler 内，也不是 worker 直接更新 View。
- **可行时序：** A 取得所有权并执行导入或 JNI → 系统重建为 B → B 可以显示静态状态，但新任务被 CAS 拒绝 → A 的 lambda 继续使用 A 的 `modelDir/requestId/saveReport/getFilesDir`，直至 finally 释放。这里没有证实任务串号；证实的是长任务强引用旧 Activity，且任务状态分散于 static 状态、Activity 实例和闭包。SAF 长时间阻塞会延长这段保留及全局忙状态，现有代码没有通用任务取消机制。
- **最小边界：** 抽出应用级 `TaskCoordinator`，保留同一个串行 executor 和同一所有权，令每个任务持有独立 `TaskContext`（requestId、类型、报告状态、录音控制器）；文件操作只依赖应用 Context/目录，Activity 只负责提交和订阅状态。让 admission 明确返回成功/忙，并在接受任务时绑定对应录音会话。不需要现在引入 Service、warm 引擎或多个 executor；不要把临时文件清理移到不受保护的生命周期回调。
- **验证：** 用可控 executor 和假文件仓库停在 preflight、报告写入和 finally 前；模拟 A 销毁/B 订阅，断言只一个 owner、旧任务使用自己的 ID、忙时不清理活动文件、失败仍释放 owner。当前 host 脚本并未编译 MainActivity，因此这些集成不变量还没有被现有纯 Java 测试直接证明。

### 2. P2（中）｜确认缺陷：崩溃遗留模型 `.part` 可阻塞低存储重试；同时确认 0.4 功能缺口

**位置：** `android/app/src/org/llmasr/minimal/MainActivity.java:227-271,328-331`；`android/app/src/org/llmasr/minimal/ResultFiles.java:10-24`；`tests/ResultFilesTest.java:31-39`。

- **缺陷证据/可行时序：** 复制大模型文件至 `model/<name>.part` 时进程被杀，finally 不执行 → 下次启动只清理 files 顶层精确 UUID 临时文件，模型 `.part` 保留 → 重试先在 MainActivity 第 258 行要求“整个文件大小 + 64 MiB”可用空间 → 到第 262 行才会打开并截断旧 `.part`。例如预期文件为 S、残片为 P、当前空闲为 F，若 `F < S+64MiB` 但 `F+P >= S+64MiB`，本可回收残片后重试，却提前被空间检查拒绝，残片还会继续保留。测试第 39 行明确要求结果清理不删模型残片，因此不能指望现有启动清理恢复它。这是条件明确的恢复性缺陷，不是模型完整性漏洞，也不是本轮实测。
- **已有保护不能误判：** 固定 manifest 白名单、重复名/条目数限制、复制字节上限、SHA 校验后单文件 rename、失败删除当前 part、跳过已校验成品、最终全量验证均已实现。导入与推理共用 owner，且 `verified` 在导入开始置 false；不应声称正常异常会发布未校验文件给并发 JNI。这里的“可恢复”是按完整文件跳过，不是字节级断点续传。
- **0.4 未实现的代码依据：** 第 265 行复制循环及第 230 行哈希循环没有任务取消检查；Back 在有任务时第 331 行明确拒绝中断；onPause 只取消 `RecordingControl`。UI 的清除确认在 `MainActivity.java:106-111` 明确不删除模型，`ResultFiles` 也不递归模型目录。`docs/minimal-apk.md:124` 明列模型删除不在当前增量内。
- **最小边界：** 抽出 `ModelRepository/ImportJob`，先在同一任务 owner 下识别并删除本应用固定清单的遗留 `.part`，检查删除结果，再计算空间；不要扩展通用结果清理器去递归删除模型。后续单独加入合作式导入取消（枚举、复制、哈希、rename 发布边界），以及有确认的模型删除事务；删除先使验证状态失效，与导入/JNI 互斥。通用 `launch` 的取消提示目前硬编码为“录音已丢弃”（202-205 行），引入导入取消时须随任务类型一起拆分，不能仅抛同一个 CancellationException。
- **验证：** 小型假 manifest/输入流/可注入空间查询覆盖上述 F/P/S 条件；故障注入覆盖读写、close、哈希、rename、删除失败及残片重启恢复；用 latch 验证取消先于发布时不发布、发布先赢时如何报告、有效成品继续可复用。SAF 阻塞 read 的即时中止另列能力边界，不能把轮询取消标志说成可靠中断任意 provider。

### 3. 优先级中｜维护性与延迟风险：保留录音 gate，先补真实编排测试，不拆掉启动锁

**位置：** `android/app/src/org/llmasr/minimal/RecordingControl.java:10-25`；`android/app/src/org/llmasr/minimal/ForegroundRecorder.java:13-49`；`android/app/src/org/llmasr/minimal/MainActivity.java:132-154`；`tests/RecordingRaceTest.java:17-60`。

- **本次未确认 gate 竞态缺陷。** cancel、实际 backend start 和 inference commit 在同一个同步门内竞争；worker 独占初始化/read/stop/release，read 使用非阻塞模式。cancel 先赢 → start/commit 被拒绝；stop → release → cancel 先赢 → 不进入 runAudio；commit 先赢 → 后续 cancel 返回 false。测试已覆盖这些主要逻辑顺序，不能重报为“取消检查与转写之间没有原子保护”。
- **真实残余时序：** worker 持 gate 执行 `startRecording()` → 主线程 onPause 调用 cancel 等待同一锁 → 硬件启动返回后才可锁存取消 → worker 在循环/finally 释放麦克风。因此 UI 延迟取决于硬件启动；没有证据证明发生 ANR，也不能承诺 onPause 返回前完成硬件 release。此限制已在 `docs/minimal-apk.md:99-105` 明确披露。
- **精确边界：** `pcm.finish()` 在 capture 的 try 返回表达式内，先生成内存 WAV，再执行 finally 释放，最后才竞争 commit；受 gate 保护的是后续临时 WAV 文件/JNI 发布，并非“取消后绝无任何内存 WAV 编码”。正常与异常资源释放仍由 capture finally 执行；假测试中的 `captureReleased()` 不等于测过实际硬件 release。
- **最小边界与验证：** 保持现有 gate 算法，给 ForegroundRecorder 引入窄 `RecorderBackend` 与 clock 接口，用假后端直接测试实际 capture 编排中的初始化失败、read 错误/无数据超时、取消、stop/release 异常和 commit 前取消。保留现有确定性 gate 测试；稍后经单独授权测量真实 start/取消/release 延迟。不要简单将 `startBackend.run()` 移出锁，否则会重新引入取消先赢但麦克风随后启动的问题。

### 4. 优先级中｜维护性风险：导出 epoch 防护完整，风险在多处调用约定而非缺少锁

**位置：** `android/app/src/org/llmasr/minimal/ExportSession.java:17-37`；`android/app/src/org/llmasr/minimal/MainActivity.java:95-110,302-319,334`；`tests/ResultFilesTest.java:48-89`。

- **本次未确认清除后旧导出复活缺陷。** 快照 supplier 在取得同一个 RUNNING 后才读取文本；清除在 owner 内先 invalidate 再清空文本；pending 和已 take 的 ticket 都受 epoch 约束；写入在维护任务取得 owner 后再次校验。请求号不复用、旧回调不能消费新请求，Activity 本地请求号与销毁 abandon 也已实现。
- **可行交错与结论：** 清除停在 invalidate 与文本清空之间 → beginOwned 因忙而拒绝，连 supplier 都不调用（已有 latch 测试）；旧 ticket 已 take → 清除先取得 owner → 旧写入随后即使获准也被 epoch 拒绝；导出先取得 owner 并通过 valid → 清除只能得到忙，不能在实际写入中间插入。SAF 选择器等待期不持 owner 是设计，不是遗漏。回调遇忙会丢弃本次请求并提示忙，可能留下选择器已创建的空文件，但不会偷偷排队写旧文本。
- **最小边界：** 随发现 1 抽取窄 `ExportCoordinator`，统一 beginSnapshot、接受回调、invalidate、valid/write 的调用顺序；用同一 owner capability 约束，不另加第二把不相干的锁。当前 public `begin/invalidate/valid` 可以被未来调用方绕过 owner 约定，是维护性风险，不是当前生产入口已绕过。保留 request/epoch 语义，重建后提示重试而非持久化私人草稿。
- **验证：** 在已有 helper 测试外用假文档 writer 验证过期/忙/重建旧回调时打开目标次数为零；覆盖取票后失败、provider open/write/close 抛错。外部文件可能为空或部分写入、云 provider 可同步是既有明确边界（`docs/minimal-apk.md:112,130-136`），不是 epoch 可撤回的事务。

### 5. 优先级低（JNI 重构）/中（测试补缝）｜维护性风险：协议解析和编排缺少直接测试，现有测试不等于设备闭环

**位置：** `native/apk/asr_jni.cpp:26-60`；`android/app/src/org/llmasr/minimal/MainActivity.java:279-290`；`scripts/test-minimal-apk.sh:5-10`；`tests/MinimalApkTest.java:25-61`；`tests/p0/test_audio_contract.py:12-54`。

- **JNI 不应先大拆。** 已有 engine mutex、逐请求 unique_ptr 释放、raw stream 比 engine 先声明以保证析构顺序、语言白名单、截断/异常失败路径和保留 pending JNI exception；这些不能误判为无互斥或常驻模型泄漏。主要耦合是“首行三个空格分隔指标 + 换行 + UTF-8 正文”的隐式 Java/C++ 协议。
- **可行演变路径而非已确认缺陷：** 如果后续 C++ 修改首行或格式，Java 第 281-286 行会因缺换行/字段/数值格式异常进入失败报告；当前两端格式匹配，没有证据表明现在就解析错误。不要为此在本轮更改 ABI 或引入持久引擎。
- **测试边界：** host 脚本只编译六个 Android 无关 helper 和三个测试，不编译 MainActivity、ForegroundRecorder 或 JNI。现有 WAV 长度/格式、PCM 边界、gate 交错和导出票据测试有价值；P0 Python 测的是音频契约公式/配置，不是 JNI 或 APK 生命周期运行。ResultFilesTest 的 close 失败案例证明异常传播，不证明 Activity 在 SAF 回调里不报成功；RecordingRaceTest 的假 gate 测试也不证明硬件释放时延。
- **最小边界与验证：** 将 native 响应解析提成纯 Java `NativeResponse`，验证字段数、非负有限耗时和 token 边界；用固定 UTF-8 fixture 覆盖中文/emoji/正文换行、缺换行、缺字段、非法数值。用 fake transcriber/report store 补“pending → 单一终态 → owner 释放”测试，与前四项共享协调器测试，不复制一份脱离生产代码的编排。设备/JNI 加载与 MNN 数值验收继续单独列门槛，本轮不执行。

## 验收声明

未发现应阻止当前只读审计交付的 blocker；确认一项条件性恢复缺陷，并区分了四类维护性/测试风险。后续建议顺序为：先固定任务上下文边界和回归测试，再修复模型残片恢复、实现明确取消/删除；保留录音与导出的已有线性化保护；最后按需要抽出 JNI 协议解析。未以历史测试报告替代本轮执行结果。
