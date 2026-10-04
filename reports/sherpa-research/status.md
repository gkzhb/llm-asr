# sherpa研究状态与验证证据

- Task：`llm-asr-1lz`；**最终研究状态：CLOSED（bd close成功，show读回）**；assignee/writer：`sherpa-research`；监管父：`sherpa-steward`。
- 研究已由父提交：`9c753c480d9a681b9ea9ab089b3ed01102cad190`（短hash `9c753c4`）；本次关闭状态文本待父另提交。
- PI session：`01a10839-8f25-7123-9f1f-11afd82546ea`；subagent：`6f2027b8-b02d-4b79-a9bf-acd22890f47a`；Herdr pane：`wJ:p5`（开工已核working）。
- cwd/branch/base：`/home/zhb/gitrep/llm-asr` / `main` / `f07c487bb39a202d7f70e95771fb0a3c0e31c21e`。
- 报告：`docs/sherpa-engine-evaluation.md`；证据：本目录；执行plan：`.planning/llm-asr-1lz/{task_plan,findings,progress}.md`。
- 长期memory：`sherpa-provider-verification-boundaries`（查重无命中后写入，recall验证）。AGENTS已有ASR导航，本轮未改。

## 初稿文本自检（2026-10-04T19:16:40Z，历史57项）

本地Python只读验证（没有执行下载代码）输出：

```text
PASS: 57 official source hashes/lengths, 13 local inputs identical to base, report Sxx/local links valid, four revision matches, no tracked product diff, text-only bounded artifacts
```

具体检查：
1. manifest 57项成功获取材料的完整文本SHA256/bytes与ignored原始文件逐项相等。
2. report所有显式Sxx编号存在，所有本地Markdown链接存在（不声称再次联网复核所有网页）。
3. local-inputs 13个文件SHA相等，并逐项与`git show f07c487:<path>`相等。
4. head/release抽核4文件SHA相等；只证明这4个文本，不证明whole-tree/AAR等价。
5. `git diff --name-only`只有`.beads/issues.jsonl`，没有tracked产品/构建变化。
6. 输出均.md/.json无NUL，单文件<120KB，无外部完整tree/HTML/二进制。
7. `git diff --check`退出0，无输出。报告/摘录尚未提交，由父独立审查文本。

## 未运行 / 边界

- Java/JNI编译、Gradle测试、APK构建/包/签名/16KiB二进制检查：**not run**，纯研究，无产品变更或构建授权。
- 模型下载/推理/质量/性能/设备/麦克风/ADB/安装/基准：**not run**，未获授权。
- 官网/GitHub raw/API/ORT官方资料可访问；HF API/raw失败，Zipformer替代Modelscope404，具体模型权重license保留unknown。Android主域失败，官方google.cn成功。
- 重点发布gate：SenseVoice许可冲突及最终确认待定；native `_Exit`错误路径与敏感热词logcat；hard cancel无已核支持；QNN direct SDK与SoC/模型条件；普通AAR实际EP/全依赖16KiB待二进制/设备验证。此次另补CPU初始化/online入口native异常包装与Qwen termination/实际音频覆盖完整性gate，均未设备复现、未实施修复。
- 已更正草稿误记Qwen Kotlin selector为61；没有照搬SenseVoice过时商业答复作为最终许可。

## Git与交接（研究过程历史，终态见末节）

- 未stage、未commit、未close、未push/upload/install；任务保持in_progress等父审查，不宣称引擎替换或APK交付。
- 本任务新文件：`docs/sherpa-engine-evaluation.md`、`reports/sherpa-research/`、`.planning/llm-asr-1lz/`；任务/memory写入导致`.beads/issues.jsonl`变化（父初始任务登记已有修改）。
- 既有他人未跟踪目录保留：`.planning/llm-asr-ap5-toolchain-analysis/`、`.planning/llm-asr-msj-modern-android-analysis/`；父独占`.planning/llm-asr-1lz-supervision/`未改。
- 原始公开文本、临时抓取/生成脚本仅ignored `.work/sherpa-research/`，不提交；未修改旧reports/根历史计划/产品/构建或APK。
- 无已知conflict。最后范围检查以父交接时`git status`为准。

## GPT6.1回审F001/F002修正（2026-10-04，最新62项）

- 用户要求的审查者`sherpa-review-2`使用newapi/gpt-6.1-sol/high、全新隔离上下文；不依赖被中断MiniMax，不称cross-family。父接受两项P2研究修正；父gpt61-review/parent-verification及supervision只读，旧阶段文字保留历史。
- F001：报告§1/§5.1/§6.2/§7.1/§7.3限定offline decode/decodeStreams显式SafeJNI；file/asset初始化、createStream及online decode未包。common helper非全局，CPU Paraformer构造Ort::Session可抛；Java catch不能修复native边界。file/asset Validate差异限定到已读JNI函数，不声称下游全无检查。
- F002：报告§4.1/§5.3/§6接口表/§6.1/新增§6.3/§7.3补Qwen context剪尾、长度/token预算后普通result、公开结果无finish/truncated字段。需native状态或证据化输入限制/分段；普通text/tokens长度不能证明完整，质量/性能比较须同实际覆盖与termination，现MNN异常终止失败门槛不放宽。
- 追加5项固定040afe文本S58–S62（五次重新获取；其中offline JNI及Qwen impl也与初稿原始SHA相同），manifest现在62项；新短引用`evidence-corrections.md`，S01–S57及旧excerpts保持不改。完整URL/UTC/SHA/bytes/行号可定位。
- 本轮只文本/static trace：未设备复现崩溃或截尾，未运行构建/推理/产品测试。未stage/commit/close，task保持in_progress。
- 最新文本自检（2026-10-04T19:39:48Z）输出：

```text
PASS: 62 source hashes/bytes; S58-S62 UTC/revision/line excerpts; 13 local/base unchanged; report links/IDs; parent review hashes unchanged; no product diff or staged files; F001/F002 gates present; bounded text artifacts
```

追加定位逐行与raw匹配，S58复抓SHA与原S47相同、S62与初稿raw相同；父gpt61-review SHA `bfed7eb861bfe204396b2f2ae1ab0af9933c74a144bf452af9e9612815d628b9`、parent-verification SHA `57d21792fd88d6c9466c3fa07a8df54aba5e29c3ec669a24be37e66cbdfcbb55`保持不变。`git diff --check`退出0。初稿57项结果保留，不冒充最新62项验收。

memory原位保留旧知识并新增两项已证边界，recall核成功；未改AGENTS。以上文本检查不证明未包装入口运行行为或Qwen风险已修复，仍not run设备/构建/推理。

## 最终文稿验收与摘录展示格式（2026-10-04T19:47:55Z）

GPT6.1窄复审已通过F001/F002文稿，父`review-disposition.md`批准研究范围验收；技术文稿无阻塞，产品/设备风险仍待未来授权PoC。被复审报告SHA `1fd6fca5a037ce735cde4d020e1227a6037741cc3ea24e4588ab6073cb69f43a`保持不变。

父限定暂存18纯文本，但`git diff --cached --check`发现两摘录带行号空行尾随空格，exit2停止提交，**未提交**。研究者仅清理展示格式：excerpts 345行、corrections 60行执行rstrip，文件头说明“带行号展示省略行尾空白，原始字节SHA见manifest”；原始ignored文本字节、manifest和所有技术/父审查文稿不改。旧19:39的工作树diff检查不覆盖当时未跟踪文件全文，不能替代新文件cached检查，现补齐工作树全文空白检查。

```text
PASS: 2271 numbered display lines match raw.rstrip; no trailing whitespace; 62 source hashes/bytes; 13 local/base inputs; technical report/manifest/parent review SHA unchanged; index untouched
```

两摘录工作树`git diff --check`退出0；index前后`git ls-files --stage`逐字相同，由父管理。**cached中仍是原展示，需父重新暂存再检查，不宣称cached已通过。** 未stage/commit/close、未产品/构建/推理/设备操作；task保持in_progress。

以上为格式补齐阶段历史，随后父提交成功；最新终态如下。

## 研究已提交并显式关闭（2026-10-04T19:51:35Z）

- Git已核完整研究commit `9c753c480d9a681b9ea9ab089b3ed01102cad190`，18限定纯文本，无产品/构建/模型权重/音频/APK变更。commit whitespace检查通过，已提交报告SHA仍为`1fd6fca5a037ce735cde4d020e1227a6037741cc3ea24e4588ab6073cb69f43a`。
- 62来源SHA/bytes、13本仓库base/current输入一致；父初稿5固定官方源码公开refetch match。GPT6.1全审+窄复审为同家族隔离上下文，不称cross-family、不依赖中断MiniMax；F001/F002**研究文稿**关闭，父接受研究验收。
- 已按本次显式授权读取`bd close --help`，只关闭`llm-asr-1lz`；命令成功且`bd show llm-asr-1lz`读回CLOSED。关闭理由记录研究提交、证据/审查、结论和not run边界，不关闭其他task、不创建/领取PoC。
- 结论仍是值得有条件可选provider PoC，不直接替换MNN；先许可清晰CPU offline，再另授权真streaming/逐设备EP。**未接入引擎、未修复产品风险、未设备验收，没有新APK。** 性能/质量/CER/WER/RAM/Java-JNI-APK/推理/设备全部not run。
- 首次把全部18文件current=commit的检查因父独占supervision/progress并发追加失败；未修改父文件。shell随后仍执行了已授权close，此失败已记录。后续将current相等范围限定为冻结技术报告/证据，完整commit范围/格式及62source/13input重新核验PASS，未把首次失败冒充通过。

```text
PASS: full commit identity, 18 text-only authorized paths, committed whitespace/report reviewed SHA, committed/current reports unchanged, 62 source SHA/bytes, 13 base/current inputs, empty index
```

本文件为状态权威补充：已提交技术报告内“待父审查/in_progress”是研究时快照，不再改其正文以保持复审SHA；manifest/摘录/父review同样冻结。此次只有Beads关闭记录、本人plan三文件与本status终态变更，研究者未stage/commit/push，父另安全提交关闭文本。

**单一Next Step：父sherpa-steward核查并安全提交关闭状态文本；研究执行结束，不自动开展PoC。**
