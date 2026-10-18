# P3 通用引擎契约：结果投影调查与暂缓提案

## 1. 身份、结论与批准边界

任务 `llm-asr-1ir`；规划 leaf `p3contract-plan`；父 `complexity-steward` 是唯一 Beads writer。源码基点 `eeaf1caf90ba422946bfadf6bcbe9895167685af`（main）；P2 双日志枚举已交 `4e73662`，复杂度基线/审查 skill 已交 `eeaf1ca`，均不重做。**当前处置（2026-10-18）：父已消费并接受 fresh 同 sol、`fork:false` 上下文隔离审查的文稿 NO BLOCKING 与产品 DEFER/REJECT，决定不独立实施 `TranscriptResult`。** 当前 `ImeController.Backend.transcribe(...): String`（`ime/ImeController.java:16–19`）已经提供文本投影；新接口只收窄局部类型，不解除具体 adapter 或返回 descriptor 依赖，独立实施收益不足。下文原候选、12路径 allowlist 及验收设计仅保留为**暂缓的设计记录，非当前实施指令**；本次仅做文稿收口，不是 P3 完成、新 APK 交付或产品实施授权。

**原调查的唯一候选（父已决定暂不独立实施）：建立一个只有 `text(): String` 的 Kotlin 结果投影 `TranscriptResult`，让现 `NativeResponse` 实现，IME 通过它取得已完成引擎特定显示处理的文本；App 仍使用原 `NativeResponse` 写 MNN legacy 报告。** 不引入 engine registry、虚假 selection、capability DTO 或空 compatibility adapter；现 `InferenceAdapter` 继续明确为 MNN 编排。此批没有第二个生产引擎 adapter，不能宣称已经建立可替换引擎的完整 seam。

原提案理由与审查后的限制：App 富报告与 IME 只需文本是不同消费需求，但现 `Backend` 的 String 返回和 `AsrText.display` 已完成投影与集中显示处理。新增方法最多收窄 IME 局部变量可见字段，不增加实质 depth/locality；`InferenceAdapter.transcribe` 的 JVM 返回 descriptor 仍是 `NativeResponse`，删除新接口也不会使解析复杂度重新散落。原计划的 parser→adapter→Java 方法断言仅能检查新转发方法，不能证明实际 `ImeBackend` 返回/提交的内容安全。父因此已决定不独立实施，不为使提案获批而扩成通用 EngineManager。

可恢复来源：[路线 P3–P6](agent-roadmap.md)、[模块/测试归属](module-test-map.md)、[本地验证契约](local-verification.md)、[审查 skill](../.agents/skills/llm-asr-code-review/SKILL.md)、[本批源码索引](../reports/p3-contract-plan/source-index.json)、[复杂度摘录](../reports/p3-contract-plan/complexity-focus.json)。源码索引给每个定位的 SHA256/行数/关键行，本文 file:line 以本基点为准，不要求任何未跟踪研究文件才能读懂。

## 2. 现状：已有接口和真实变异，不把路线名字当实现

下列路径在 `android/app/src/org/llmasr/minimal/` 下，除非另注明；物理目录不是 module owner。

| 面 | 当前 file:line / 事实 | 当前没有什么 |
|---|---|---|
| 编排 | `asr/InferenceAdapter.java:12–24,27–59`：Readiness/Library/NativeTranscription 注入，requireReady→loadLibrary→同步 JNI port→strict parse→returned；返回 NativeResponse | 不是 EngineProvider/registry/ResidentEngine；无泛型 decoder 或通用结果 |
| 结果 | `asr/NativeResponse.kt:14–38,41–67`：私有构造、Java @JvmField 五字段、@JvmStatic parse；AsrText 处理 Qwen 显示 | 不是通用 completion/coverage schema；raw 非空不是成功条件 |
| 请求 | `asr/NativeTranscription.java:10–12`：config/WAV/language/cache + listener，byte[]；`task/RequestContext.kt:6–16`：UUID/事务临时状态 | 无 engine/model/profile/generation selection；不要给 config 路径改名 PinnedResources 就冒充 lease |
| 唯一装配 | `AppGraph.java:63–82,137–149`：一个 repository/readiness/coordinator 和共享 inference，App/IME 同进程 | 单 Java 对象不等于权重 resident；无动态 provider 注册或插件扫描 |
| 就绪 | `model/ModelAccess.java:13–33`：每次受管边界检查，内存 token 可触发 verifyAll；冷 SHA/缓存 readiness；`ModelReadiness.java:49–100` 是进程内 epoch | 无持久 VerificationReceipt，重启不承诺免 hash |
| Native | `native/apk/asr_jni.cpp:84–136`：全局 mutex；局部 unique_ptr create/set_config/load/response，return 后析构 | 每请求 create/load，P3 不免 load；不能把 System.loadLibrary 重用当模型复用 |
| 实际消费差异 | `transcription/AsrOperation.java:156–165` / `transcription/AppReportWriter.java:31–54` 使用 raw/metrics/display；`ime/ImeBackend.java:29–37` 只返回 display | 是 App legacy 富报告与 IME 私有文本投影的真实差异，不是已有第二引擎 |

原有 `docs/multi-engine-adaptation-plan.md` 是本地未跟踪研究，只读参考其身份分层/生命周期风险；P1C 的 `.work/p1c-plan/handoff.md` / `next-slice.md` 是旧研究。旧“暂停双 enum 待恢复”已经由 `4e73662` 实现，不是本轮 blocker。P1C native single/multi-DSO、模型转换包许可/精确闭包、持久 receipt 信任、resident 资源策略仍 unknown/未批准，本文不选方案。

## 3. App / IME 捕获、owner 与回滚：保持现有，不伪称完成 selection

### 当前捕获点

- App：`MainActivity.java:76,231,275` 在点击/文件回调读取语言 String；`AsrOperation.java:81–112` 的 worker lambda 捕获该参数。sample/WAV/录音来源标签也由该请求闭包给出。`runInference:156–160` 在 worker 解析 repository.modelDir/config.json 与 cacheDir，`transcribeFromStream:149–153` 绑定 UUID WAV。当前没有可变模型选择，不等于已经在 admission 捕获模型 generation。
- IME：`AsrImeService.java:138–145` 读取语言；`ImeController.java:63–103` 在同步 start 验 Chinese/English/auto，捕获 session/rev/language/control，admitted 才安装 revision/清 preview/recording。`ImeBackend.java:29–36` 在 worker 写 WAV、解析 config/cache。session 与请求 revision 是 editor 防迟到凭证，不是模型 lease。
- 在未来全 P3 加 selection 时，必须在同 owner 的同步 admitted 中捕获 immutable selection/profile/generation 引用和语言，录音直到 native 真退出都用同一份；worker 不再读取“当前默认”。busy 不调用 supplier，捕获/钩子抛错和 executor reject 归还本次快照/pin 一次，不碰他人的快照。**这只是后续约束，本候选不新增 selection 状态、pin 或失败恢复 UI。**

### 当前 owner / IO 生命周期

`TaskCoordinator.kt:40–71` CAS owner 后同步 admitted，再 enqueue；busy 返回 false 不运行 admitted/abandoned。admitted/notify/executor 抛 RuntimeException 或 Error 会先 abandoned，再 release；`withOwnership` 不是第二个锁。`RequestRunner.kt:42–62` 的 finishOnce 覆盖正常 worker finally、submission abandoned、RejectedExecutionException 捕获；busy 走 lifecycle.busy，executor reject 走 rejected 并返回 false。其他 admission 异常原样传播，不能说全都变成 rejected=false。

`RequestRunner.kt:64–80` preflight→policy→body→terminal→cleanup；cleanup 后 finished hook，最后 coordinator worker finally release。普通 Exception/LinkageError走 policy，其他 Error 仍 finally 清理/释放，不能新增 catch-all 吞 VM 错误。App cleanup 仅删本次 WAV（AsrOperation:64–68）；IME cleanup 是 backend（ImeBackend:39–42）。阻塞 native/cleanup 时不得提前释放 owner。录音 gate 在 `audio/RecordingControl.kt` / `ForegroundRecorder.java`，本候选不改 cancel/commit/start/release 时序。

IME 隐藏/换框 `ImeController.java:39–45,56–58,89–95,107–118` 使旧 preview 失效，仍等真实 worker/native/cleanup 收尾。确认提交前 consume，一次显式提交；连接抛错/拒绝也不自动重试。与 AppState/report 完全隔离。结果投影不能成为“normal return 即自动 commitText”的授权。

## 4. 候选 interface：只承诺现在可证明的文本

建议文件 `asr/TranscriptResult.kt`，core owner，概念形状如下（**非已实现代码**）：

```kotlin
/** Engine-specific display normalization is already complete; may be empty.
 * Not a readiness, completion, coverage, ownership or commit authorization.
 */
fun interface TranscriptResult {
    fun text(): String
}
```

`NativeResponse` 保持现有 private constructor、字段名/类型/@JvmField/静态 parse/异常；仅 `: TranscriptResult` 并增加 `override fun text(): String = display`。普通 interface 方法就足够，不设新工厂/registry，不在 interface 内做 Qwen 解析。`ImeBackend.transcribe` 用 `TranscriptResult r = inference.transcribe(...)`，返回 `r.text()`；不再 import NativeResponse。`InferenceAdapter.transcribe` 和 AppReportWriter 的 descriptor/调用/报告方法体不变，App 仍有准确 MNN 指标，无需 cast、伪造默认值或报告 schema 更新。

约束：投影只保证 String 非 null、允许空；MNN 的合法获取路径仍必须经过原 strict parser 和 JNI 正常完成 gate。interface 本身无法证明来源可信（测试 lambda 当然可实现），因此不能把任意实现的非空 text 当引擎成功、免校验凭证或 native 已退出。未来第二引擎必须在自身真实 adapter 中建立完整 completion/coverage/evidence，不能借实现此 interface 直接进入 IME 成功预览。该最小 interface 只是结果消费合同，未来可由完整结果实现，不预定 full P3 的类树或 registry 默认值。

### 不可退化的 MNN compatibility 语义

1. `NativeResponse.kt:26–38,41–67` strict 标准 UTF8（包括非 BMP）、三个 metrics、有限非负耗时、token 0..128；128 允许，129 拒绝。非法 parser 信息经 `InferenceAdapter.java:53–58` 固定无 cause 的 IOException，不把 payload 写 runtime log。空 byte[] 不合法，metrics 后 empty raw 合法，投影应为空而不是失败。
2. `native/apk/asr_jni.cpp:106–126` CPU2/greedy/max128、NORMAL_FINISHED 才返回；MAX_TOKENS/其他停止抛错。parser 的 token≤128 不替代 native 终止判断，也不能证明测得全音频覆盖。Qwen `audio/AsrText.kt:7–12` 保持 raw→display 清理，不把它推广到 sherpa。
3. `InferenceAdapter.java:30–49` readiness/library/native Throwable 原始身份保持（包括 JNI checked Throwable、LinkageError、VM Error）。projection 不能 catch 它们返回空文本；App 的原始失败报告策略 `AppReportWriter.java:114–117` 仍存 String.valueOf(cause)，是既有 App-only 行为，不虚称已全链统一脱敏。
4. `LoggingInferencePhaseListener.java:52–110` 只有真实 load/infer callback；parse 成功后 returned，request success 不是 App 报告/IME 提交成功。终态前的缺失序列（returned 时检查）、乱序或跨线程 callback 使遥测失败；closed 后的晚到 callback 直接忽略，不追加遥测失败、不撤回或复活/重写结果。成功 ASR 仍可返回，不假造 REQUEST_SUCCESS/FAILURE；真实失败仍记固定 failure。可选仪器 Throwable 不改变 ASR；非仪器异常不能被吞。
5. runtime 日志仅固定事件/安全 detail、requestId/source/时间；不追加 text/raw/config/path/language自由文案。App legacy 正文报告保持其原私有写入策略，IME 不写 App 报告/正文、不持久化 preview；不引新日志版本/事件。

### 原调查的单一选择与当前处置

- 全部 request/result/capability/registry/selection/provider 十几类型：第二引擎、generation、资源信任尚不存在，容易给纯透传接口加伪保证；拒绝本批。
- 先引 request DTO：现 Strings 已由闭包捕获，mutable selection 不存在；若不接 admission/pin 只换参数包，价值小；若接则要求未批准资源状态机，拒绝本批。
- 只提取 decoder/空 MNN adapter：已有 NativeTranscription/NativeResponse seam，复制解析更易放宽旧 gate；拒绝本批。
- **原选择 text 结果投影，现已暂缓/拒绝独立实施**：唯一新增生产类型、一处 consumer、保留 MNN 富数据与 parser 不动，但现 Backend 已提供 String 投影，有限收益不足；不把该记录当下一批指令。无新 ADR/GLOSSARY：没有批准难逆架构或稳定新领域身份，只在本文定义投影，避免另一个 SSOT。

## 5. 原12路径 allowlist（暂缓提案记录，非当前实施指令）

路径 `P = android/app/src/org/llmasr/minimal/`；以下保留原12路径设想，不授权任何产品写入或目录 glob。无删除文件。若未来重启，须父另行批准范围，并补下节所述实际 consumer 可观察证据；不能把此旧清单当完整授权。当前文稿提交候选另见本批 manifest，不混同此产品提案。

| 操作 / 路径 | module owner / 为什么必须 / 最小接线 |
|---|---|
| 新 `P/asr/TranscriptResult.kt` | core；唯一方法 text，Kotlin/Java8，无 Android/依赖/模型身份 |
| 改 `P/asr/NativeResponse.kt` | core；implements + 映射 display；保留全部原 parser/字段/ABI |
| 改 `P/ime/ImeBackend.java` | asr-runtime；仅局部类型/import/返回 text；WAV/路径/cleanup不改 |
| 改 `tests/NativeResponseTest.java` | core test；Java interface 分配/方法描述、Qwen/emoji/empty + 原 invalid UTF8/token边界；直接生产 parse |
| 改 `tests/InferenceAdapterTest.java` | runtime test / host；实际 adapter 结果经 TranscriptResult，保原 payload/metrics检查；sourceContracts 精确查 IME text 调用且 App 不变，不能仅 source grep充行为 |
| 新 `tests/transcript_result_mutant_test.py` | tooling/test；隔离当前生产 NativeResponse 的可编译 text mutants，fresh classes、有界compile/run、目标断言和恢复源SHA；不读旧classes |
| 改 `scripts/test-minimal-apk.sh` | host wiring；host_classes加 asr.TranscriptResult；既有两main不增注册；接新driver（串行有界，timeout预算由实测审） |
| 改 `tests/architecture_boundary_test.py` | tooling/test；PACKAGE_MAP加 TranscriptResult→asr；保既有graph/JNI/report/private guard，新增interface不能依赖platform/model/task；不放宽允许依赖集合 |
| 改 `tests/module_owners_test.py` | tooling/test；显式审核63→64库存（core +1），继续检查真实class direct owner、重复owner、core平台污染，不删除库存门槛 |
| 改 `gradle/baseline-inputs.json` | build identity；只新增Kt SHA、更新两生产改动SHA和必要revision_scope；native/signature/assets/其余SHA保持，不刷新错误native基线 |
| 改 `docs/module-test-map.md` | docs；说明新core owner、两个原JUnit入口和mutant层级；计数按实际盘点，保旧证据历史 |
| 改 `docs/p3-engine-contract-slice.md` | docs；记录获准实现范围/实际身份/未测与full P3剩余，不能把本文计划改成虚假已验收 |

无需改 `AppGraph.java`、`InferenceAdapter.java`、`AsrOperation.java`、`AppReportWriter.java` 或其 frozen report fixture；无需改根/core/runtime Gradle sourceSets：core自动含新Kt，runtime仍仅5Java，app仍8Java。`CoreRegressionJUnitTest.java:13` 已跑 NativeResponseTest；runtime现 `RuntimeRegressionJUnitTest` 已跑 InferenceAdapterTest。原设想不改变现 graph-fast 任务图（断言/方法内增加不是新增 Gradle task）；当前35是 core34/runtime1 的 JUnit 方法数，与任务图节点数是两件事，不能称35个 tasks。方法数如未来实施须用当次 XML实核。既有 DEX checker按 baseline 发现新owner，module owner count增1必须审，不以hard63卡住后顺手删gate。

未来证据/plan路径由父在新实施 task 独占授权，不自动包含旧 P1C/psn/ap5、jrqreview、父supervision或本地未跟踪研究。若任何未列源/工具/fixture需修改，先报具体诊断与最小增补，父批准前停；不绕旧报告body freeze或锁来凑green。

## 6. 原验收设计与 R1 证据缺口（暂缓，当前全部 not run）

### 从当前生产入口构造有效 red

扩展 Java 测试不是手写替身：`NativeResponse.parse(validBytes)`→以 TranscriptResult赋值→`text()`；以及 `new InferenceAdapter(realStore, injectedReady/library/nativePort).transcribe(...)`→同一投影。port只提供字节和真实callback模拟，不声称 JNI已运行。有效输入要含 `language Chinese<asr_text>中文😀<|im_end|>`，分别检查 `raw` 原字节、`display/text` 为 `中文😀`、原 metrics 完全相同；empty raw 给空 text。bad UTF8/129/null/NaN仍由现生产 parser/adapter拒绝且无 REQUEST_SUCCESS。

**R1 限制：以下两个方法 mutants 都不执行或观察实际 `ImeBackend.transcribe`，只能拒绝新 `NativeResponse.text()` 转发错误，不能据此宣称 IME consumer 安全已验。** 源码调用检查、SDK/AGP 编译及 class/DEX owner 检查也不能补此行为缺口。若未来重启并要声称 consumer 安全，须另行批准直接跨实际生产 ImeBackend 的可观察用例及 consumer raw/empty 可编译反例；本次不设计新测试 seam、不扩原scope，更没有运行过这些 mutants。

原driver设想在隔离副本一次只改新增生产方法 `NativeResponse.text()`：

| 可编译变异 / 生产方法 | 断言与目标诊断 | 为什么不是编译红 |
|---|---|---|
| `text() = display` → `text() = raw` | Java测试外部 `text()=="中文😀"`，固定诊断 `TRANSCRIPT_PROJECTS_DISPLAY_NOT_RAW`，同时原raw和metrics断言仍成立 | 实现仍返回String、override/接口/字段均在，先要求compile exit0与目标class存在，再要求对应AssertionError/nonzero |
| `text() = display` → `text() = ""` | 同一非空生产adapter输入，固定诊断 `TRANSCRIPT_NONEMPTY_DISPLAY_PRESERVED`；emptyraw正例仍过 | 非空文本静默丢失是运行行为；既有parser/JNI descriptor均未改，compile必须0 |

driver对同一当前源 green→mutant compile0/目标red→恢复精确源SHA green，分别记录 argv/exit/stdout/stderr/SHA。不能用无interface class、旧classes、kotlinc语法错、assert被listener吞、timeout/缓存失败替red；若诊断顺序导致第二mutant先命中第一条断言，给两case独立测试入口/参数选择并核唯一目标，不泛匹配AssertionError。不要为驱动新增第二份parser或 fake InferenceAdapter。

Java互操作：NativeResponse原 @JvmField和parse静态方法继续由既有Java编译运行；`TranscriptResult.class.getMethod("text").getReturnType()==String.class`，NativeResponse可assign，构造器保持私有。新接口与实现都core实际class owner，runtime只消费、app无多余Kt；actual class/DEX新增一owner并无重复。不要只凭.kt后缀或字符串库存称互操作通过。

owner/隐私回归继续执行现 RequestRunnerTest/AdmissionBoundaryTest/ImeSessionTest/InferenceAdapterTest：admission/busy/reject、blocked IO退出前owner、cleanup、每请求listener、异常身份、telemetry/persisted/exported无正文。此候选不引新snapshot，所以不造一个无生产接线 selection fake来冒完成；未来selection那一批必须另测busy supplier0、admitted throw、executor reject、录音期间切换、stale generation/native阻塞与真退出后release。

### 工程节奏与可用入口

1. 获准后先冻结当次base/allowlist/current inputs，核原断言green；在 fixed Nix `apk --cache-only` 内用既有 `scripts/compile-jvm-sources.sh` 显式当前source list/fresh临时目录做定向Java混编/两个main/driver。新driver复制生产源并调用原混编器，不擅自下载/改锁。然后同源 `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh fast --offline --rerun-tasks`，跑原JUnit/实际模块classpaths和source负例。
2. fast 中 `:asr-runtime:testDebugUnitTest` 依赖 verifyModuleBoundary→compileDebugJavaWithJavac，确实真实编译 ImeBackend 等5个 runtime Java，不是只跑fake。其JUnit仍是主机测试，未运行平台 ImeBackend，也不编译 app 的8个生产Java。审查前另由父授权定向 `scripts/compile-android-java.sh`（同cache-only环境）真实全源SDK混编/Java互操作，明确与AGP模块编译证据区分。host及Android编译和必要mutant/log/输入绑定形成targeted证据。完整host可用现 test-minimal-apk.sh，但重型driver串行；先targeted不提前full。
3. 冻结候选，由新 `newapi/gpt-6.1-sol` reviewer显式 `fork:false` 做fresh上下文隔离审（先核live route，非跨家族）；报告后父接受、原writer最小修复、定点回审关闭阻塞。
4. 审查接受后唯一验证leaf串行必要full：`bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`。保host regressions/模型与录音mutants、实际core/runtime class boundary、JNI generated header/NDK编译/linked符号、MNN对象/provenance/ELF、Lint/checksum负例、真实三包签名/权限/assets/all DEX/receipt。不因native未改而删gate；不要求全MNN clean。缺cache/strict失败停报，不prepare、不刷新metadata或expected值。
5. 动态冻结 build/module/native/external/supplemental各域，核current/frozen/committed与实际APK/receipts、版本签名对应，不能抄本批212/10/80/63或旧APK当未来身份。最后候选安全审→父另授reviewagent精确逐文件正常stage/cached全diff/check/模式SHA→正常新本地commit→父核receipt/Git后才交APK。无push/amend/force-add/stage-all；不夹模型/音频/APK/缓存/key/session/旧研究。

本轮只规划，上列测试/编译/mutants/full/JNI/Gradle/cccc/knots复测均 **not run**。未来即使全绿，也只证明host/实际编译/parsed DEX/包身份；Android runner发现/执行、麦克风、SAF/Binder/grant、IME系统字段、JNI实际运行/4K16K设备与模型质量/性能仍需另授权。本轮不关闭 M4/M5/tc3/ap5/psn 或选择resident预算。

## 7. 复杂度：旧基线定位，非降分承诺

本基点相关源码与 `reports/complexity-baseline/sources.json` 逐SHA相同；直接读已存 normalized 摘录，没有重跑两CLI。C/Y=cccc cognitive/cyclomatic，C/M=knots cognitive/mccabe，不混算。精确记录含line/occurrence/nesting/SLOC见[摘录JSON](../reports/p3-contract-plan/complexity-focus.json)。

| 未来触及/观察函数 | cccc C/Y | knots C/M | 风险和人工处置 |
|---|---:|---:|---|
| NativeResponse.parse（source26/tool24）、decodeUtf841、parseFinite52、parseTokens61：观察不改算法 | 不支持Kt | 7/8、2/3、7/8、5/6 | 新text映射不能放宽这些gate；新方法无before值，单列新增 |
| ImeBackend.transcribe29：触及局部消费 | 1/1 | 0/1 | 低分不免审IME隔离/原cleanup；不重构controller |
| InferenceAdapter.transcribe27 / parse53：观察不改 | 2/3、2/2 | 4/4、2/2 | Throwable/parse→telemetry顺序保持 |
| InferenceAdapterTest.payload96 / successAndTime105 / sourceContracts371：可能触及 | 2/4、9/13、12/17 | 2/4、5/13、12/17 | 后两已有分支信号；只加小型独立投影case，别往sourceContracts无限堆控制流 |
| NativeResponseTest.main17：触及 | 17/18 | 17/4（SLOC84） | 长main既有断言；新投影case分组命名/外部断言，不将测试分数当产品风险 |
| module_owners_test.check12：仅库存常量，不改算法 | 52/24 | 47/28（nest4） | 原热点不为本批大重写；保全负例，解释63→64范围变化 |
| architecture_boundary_test.check_package147：只扩映射 | 1/2 | 0/1 | 顶层命令式分支盲区要全文人工审，不看函数总数减小 |
| RequestRunner.submit42 / TaskCoordinator.submit40：观察不改 | 不支持Kt | 8/10、15/15（nest4） | owner/admission热点，原回归继续，不机械迁repo/controller |

新 `TranscriptResult.kt` 声明可能无 knots 函数记录，不填0；cccc Kotlin不覆盖。新增driver为新函数，无before，对异常/cleanup和目标red路径人工审。scripts shell无双工具覆盖，JSON/文档无函数指标；native cccc partial-parse，native完全不改不因此重算。

未来复杂度复测按skill同版本/二进制/argv/范围。未跟踪新Kt/driver不能被 `git ls-files` collector漏掉：先列精确路径/SHA；在父授权的全新临时**文本源码副本**创建独立Git index/仓库（不动主index）仅登记经审核的维护源码库存及两新增代码文件，以同collector `--root <副本>` 得到current；另保主库tracked-only采集若需要。baseline comparator遇库存新增应返回范围变化2并人工映射，不冒“全覆盖/无退化”；新范围报告独立目录、保旧基线，不更新旧分数消警。Kt路径/接口新增映射与未改parse方法相同SHA可追溯；无需为本次纯文稿跑双全库复杂度。

## 8. Full P3 剩余和停止条件

本候选不交付 engine request/result完整身份、capability交集、selection同步快照、registry/admission或多provider调度；不交MNN resident/P4 receipt/P6新引擎。full P3需后续另有真实实现变化和精确合同：engine/model/profile/bundle/generation身份分层；能力拒绝语义与unknown metrics；completion/coverage/native退出分离；engine-specific结果证据与MNN legacy隔离；显式受控registry/失败不fallback；App/IME admission快照和owner回滚。尚无第二adapter时不能凭一堆接口声称真实替换成功，模型/资源部分不得伪造license/receipt/load证明。

**当前最终决定：父已接受文稿 NO BLOCKING 及产品 DEFER/REJECT，不独立实施 `TranscriptResult`；现 Backend 已有 String 投影，原12路径仅为暂缓设计记录，不是当前指令。** 本次只交文稿差异供原 reviewer 点审及父后续安全文档提交；不扩大架构、不执行产品命令。

未来如讨论重启仍须另授权；stop包括：父未批准本候选或认为其价值不足；owner/runtime冲突；必须改未列路径/权限签名/JNI/math/录音gate/报告格式；新依赖/cache/下载需求；任何测试只能编译失败而无可编译行为red；欲引selection却无法给出generation/rollback真实语义；需批准native布局/模型许可/resident策略或设备/私人数据。停报源码/诊断/已尝试路径与所需最小输入，不自行做产品决策。**当前没有这些权限下的完整P3实现授权；只有本次文稿可交父审。**
