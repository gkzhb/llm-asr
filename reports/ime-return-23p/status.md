# llm-asr-23p：返回上一输入法

基线 `7a3aa5317dec905c6a952f16beff10a58ca9c481`；唯一产品 leaf `ime-build`，父 `p1-steward` 为唯一 Beads writer。本页为 targeted 阶段状态，不是 full/提交/APK 交付或设备验收。

## 行为与范围

仅两生产 owner：`AsrImeService.kt` 增加显式返回按钮、单次 previous 与失败单次 picker、通用脱敏提示及明确手动 fresh 重开；`ime/ImeController.kt` 使 hide RuntimeException 不阻断外部请求。先 invalidate/cancel，再 best-effort hide，再外部操作；不自动录音/确认/切换，不改变 native/IO/cleanup 真实收尾 owner。权限、敏感字段、当前字段与忙状态保护保留。系统历史不保证 fcitx5，previous=true 不冒称切换完成。

## 验证状态

- Targeted 普通模式：工程 **FAIL**，首次 fresh 混编 exit0，首个 `ime-return` run exit1，诊断 `destroyed service cannot restart`。此处不是 mutant 目标红，也不是功能通过。依授权首停，未重跑或改 gate。
- 静态诊断：fixture 在销毁后调用 `onCreateInputView()` 创建新树，但 `ImeFixture.root` 仍指向原树，随后 `button()` 查的是旧树；失败断言不能据此证明当前新树按钮状态。需父消费后授权最小 fixture 修正及后续验证，当前未改失败现场。
- Python -O、四独立 mutants/red/restore、`ImeSessionTest` 新实际 worker 组、真实 SDK35 全源编译、fast、graph-fast、format：**NOT RUN**（首停）。
- cccc/knots 同工具同范围采集/比较及人工热点处置：**NOT RUN**。仅建立两个七文件有限文本 snapshot HEAD，未采集/比较，不冒通过。
- 新功能 host 入口为独立 `--ime-return-only`，9 次 compile / 9 次 run 上限，每次270/45秒，外3000秒；旧默认五 mutants / compat 及所有原命令预算不变。

测试调用真实生产 Service、Controller、RequestRunner、RecordingControl 与 TaskCoordinator。平台 fake 只提供可控 boolean、异常、窗口/按钮及事件记录，不证明 Android 系统行为；cccc 不覆盖 Kotlin，复杂度与编译证据分列。

终态：后台 `bcd54fbff` exit1，自动通知已消费；外层命令与内层 compile/run 均 `owned_exited=true/root_reaped=true`，无 timeout。原始输入/输出/账本保留在 ignored `.work/ime-build/commands/` 和 `.work/ime-build/contracts/run-muhrt4p3/evidence/`。27项旧三 APK/元数据/正式 receipt 哈希保护核同，主 index 空，HEAD不变；没有相关活后台。

## 首停后授权续验（第二次首停）

父接受旧树引用诊断并另授最小 caller 修正：保留旧 restart 按钮，销毁后点击旧 listener 外断言 session仍null/capture及write不增；显式重建时接住返回新树并检查其按钮禁用。未改生产与其他测试，旧失败证据保留。

新后台 `b5310c9f1` 自动 terminal 已消费，仍在首个普通 initial green 工程失败：fresh mixed compile0 / run1，精确断言 `late success/failure/cleanup cannot revive preview capture`（`ImeSessionTest.kt:101`）。销毁修正路径已越过，未将部分执行冒整套通过。

静态诊断：新 controller 测试错误要求 fresh session 的 `status().isEmpty()`；真实 `ImeSession` 构造默认 status 为「点击录音，最长30秒；转写后确认输入。」，不是空字符串。应对照 fresh 创建时的原始 status 检验未被迟到成功/失败/清理覆盖，不能直接要求空。**尚未获准修正该测试或重试**，依首失败停。没有 mutant执行，`-O`、默认/compat、SDK/fast/graph/format及复杂度collect/compare仍NOT RUN。

新证据 `.work/ime-build/commands-v2/`、`contracts/run-nsud67ia/evidence/` 全部三个 owned命令确认退出并root reaped，无timeout/活BG。七源新current-v2有限snapshot commit `6960302` 仅准备未采集；主HEAD/index及旧两snapshot/旧失败树保持，27保护项核同。

## 最新 targeted 封口（前述两失败保留为历史）

父另授后仅将 fresh 初始 status 保存并与真实 worker 终态比较，保留全部 owner/join/错误/预览断言。产品源码从首候选起冻结，未用产品变更修测试。

| 路线 | 当前证据 / 结果 |
|---|---|
| controller sanity | fresh compile0/run0，221 checks；未重复成功链 |
| IME ordinary / `-O` | 每模式9 compile / 9 run，4独立生产 mutants 各 compile0 / 精确 AssertionError1 / restore0；真实按钮与Controller测试通过 |
| 旧默认 / compat | 原5 mutants与Java ABI通过；compat四nullable receiver精确目标红/恢复通过，原预算与路由未删 |
| 真实 SDK35 | 全源45 Kotlin +18 Java JVM8编译0；标准deprecated javac说明保留，不是系统执行 |
| fast | strict/offline/cache-only exit0，27actions executed，core34+runtime1 JUnit零failure/error/skip，真实module边界负例通过 |
| graph-fast / format | exit0，38项精确dry-run task无heavy；100项维护文本通过；graph不算测试执行 |
| 同口径复杂度 | 两collect与compare0，cccc1.8.3/knots1.18.0同七文件；工具stderr空，人工热点处置见下 |

新真实终态：`be6d32d70` completed0（剩余工程）与 `bdcfbe17c` completed0（复杂度）自动通知均立即消费；所有本次attempted child ledger owned_exited/root_reaped确认。早期`b6a902c08` outer退出码/原因UNKNOWN但inner sanity/ordinary独立完整0证据已核当前源码，未重跑；`bb6d3bffe` signal15中断的优化尝试未作通过/断言红，信号来源及outerexitUNKNOWN，完整优化模式另在新域通过。旧registry/raw未改写。

复杂度仅窄范围：cccc不覆盖Kotlin，shell双工具无覆盖；knots生产Service render22/14→23/15（C/M，nesting4），只增加alive/controller可重开按钮条件；leave4/3→6/4，Controller leave1/2→2/3，三个新helper各C2且nesting1。既有start/commit热点未改，真实owner/迟到测试覆盖；测试externalExitRetainsOwner C30/M29/nesting6、Java矩阵C20/M17与C16/M17、driver C14/M11分别人工核有界latch/join、实际按钮、四精确mutant、严格owned恢复，不把测试分数混成生产缺陷。原始JSON/21ambiguous映射及逐项处置在ignored `.work/ime-build/complexity/manual-disposition.md`。有限snapshot仅临时副本提交，主index/ref不动，基线不覆盖。

27项旧三APK/metadata/formalreceipt字节SHA保持；223current build/10module输入与本批候选分域核验，文档/报告为supplemental。没有相关活后台，主HEAD7a3aa53、index空；全host shell（除本批选定driver）、full/Lint/JNI/native/APK与fresh审仍未执行，不以targeted替代。

## Fresh 审查 R1 定点修复（等待回审，不自称 CLOSED）

父接受 `ime-review` 唯一R1/P2：新driver外层裸finally在unknown/corrupt账本时会用cleanup失败替换原BaseException；输入保留有效，未发现生产阻塞。仅新 `ime_return_suite` 退出层保存primary并bare raise同对象；cleanup异常在有primary时附固定refusal note，无primary仍抛出。其余8候选冻结，共享runner/default/compat/产品/门槛预算不改。旧outerUNKNOWN没有归因于R1。

普通/`-O` 同一ignored harness调用实际suite、仅替换prepare/execute为合成已完成命令记录：每模式10case，KeyboardInterrupt/TimeoutExpired × unknown/missing/corrupt/confirmed，外部核same object、附note、精确mutant/输入保留或confirmed恢复；另无primary cleanup失败仍抛同cleanup对象、完整confirmed恢复。旧driver同harness每模式exit1且6case实际出现CleanupRefused/JSONDecodeError替代primary；新driver每模式exit0/10case通过。不是失控compiler/真实native/Android故障注入，也非新进程监督框架。

新driver真实普通/`-O` 各9compile/9run，四独立mutants0/精确AssertionError1/restore0通过，36inner命令owned/root确认；format通过。后台 `b2bea6ab9` completed0自动terminal已消费，旧default/compat/SDK/fast成功链未重跑。新同七源复杂度窄delta/输入与保护封口另见 `.work/ime-r1-fix/` 当前handoff；fresh R1定点回审仍由父另授。

## 最新正式 full 成功（此前未full文字为历史检查点）

父接受fresh R1 CLOSED后，原writer唯一cache-only/strict/offline完整入口真实exit0，耗时2042.620秒，184actions全部executed，Gradle0/tee0/DEXscan1。完整原/新host与mutants、42JUnit（34core+1runtime+7host）零failure/error/skip、JNI实际编译/符号/native provenance/ELF16KiB、真实checksum0→目标1→恢复0、真实Lint NewApi负例恢复、fixture行为链及三包签名parsed检查均通过；生产Lint Error/Fatal0，App24/runtime17warning分类保留defer不抑制。详细见 [full报告](full.md) 与 [分域输入及产物](full-inputs.json)。

本轮产品APK `android/app/build/outputs/apk/debug/app-debug.apk`，5282885bytes，SHA256 `c7d8e3cf52e495480ea8f60f0301091ce9ba9e7b81be8d808e8476ebb8c0be2f`；0.6-debug/code6/min29target35/arm64/RECORD_AUDIO-only/原v3证书。当前223build/10module/80native与三包/双正式receipt输入核同；本报告追加属supplemental，source冻结不变。后台 `b09197bd3` actualcompleted0自动terminal已消费，无相关活BG，主index空/HEAD7a3aa53未改。

**当前仍未stage/本地提交，不是APK交付指令。** 父消费后安全两阶段提交另授；系统IME/fcitx5往返、Androidrunner、设备/麦克风/private/模型/4K16K运行仍NOTRUN。前述NOTRUN/fullpending为各自旧冻结时点，本段给出当前工程状态，不回写历史失败或旧outerUNKNOWN。

## 本阶段未执行

fresh 上下文隔离审查、formal full、assemble/D8/sign/本轮三 APK、正式 receipt 更新、安全 stage/本地提交均另授。旧三 APK/正式 receipt 保持，不代表本轮源码。Android runner、设备、fcitx5 往返、系统 picker/Binder/窗口回调、麦克风/私人数据/模型计算、下载/新依赖/推送均 NOT RUN。

父消费 targeted handoff 后另行 fresh 同 sol 上下文隔离审查（非跨家族），随后唯一 full 和两阶段安全本地提交；提交成功前不宣称 APK 可验证。
