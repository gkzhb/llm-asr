# B3a 生产 Lint 验证计划

## Binding
- task llm-asr-76d.5；父 owner / 唯一 Beads writer p1-steward。
- role project-agent；当前 B3b 唯一验证/本目录 writer p1full-test（Herdr wY:p1C，session 01a13e31-a17e-71ba-9b87-5ec2f5ca575e）；permfix-build/review、lintfix-build/review 已完成停写，以下历史完整保留。父 session 01a13d7f-ed59-777f-8029-01440851d699。
- cwd /home/zhb/gitrep/llm-asr；main；base 7eace17a8ea636db6010f95d2229c28dc2f78467；无 worktree。
- 当前用户明确授权自主补 runtime library 既有 RECORD_AUDIO 声明（与App相同且无附加属性、无新权限种类），必要精确测试/构建输入接线/文档及统一Lint。此前 ActivityWindow/录音安全异常/stage三修复已审无阻塞，不重开。不 claim/update/close/stage/commit/push/amend、不二级委派、不读私人 session。
- 本轮临时证据只写 .work/p1-permission-fix/；原 .work/p1-lint-fix/、review、.work/p1-b3/、B2 freeze/plan/evidence 及父监督目录只读。无依赖/锁/版本/ABI/录音gate/IME隐私/签名变更，无下载、设备/private/权重/key 内容访问。

## Goal
固定 Nix cache-only，Gradle strict/offline/no-daemon、workers1/JVM1536m，实际串行运行统一入口 lint --offline --rerun-tasks，保留生产 app preBuild/native 必需链。保存启动脚本/argv/SHA、当前195build/80native输入前后及对旧blocked194的delta、六份旧生成XML备份、任务/log/XML/HTML/exit/SHA。本轮只取meminfo前后、不宣称峰值资源采样，给父fresh差异review后full的证据。

## Current Phase
B3b FULL complete：唯一bg b619c639e durable completed0/428.052s/111action全executed；35JUnit零fail/error/skip、生产Lint两份0blocking及本次真实fixture/checksum红绿恢复、原host/JNI/native/APK包checker通过。195build/80native/currentAPK报告exact，578对象及DSO保持；632history保持。工程验证通过待安全提交，最终报告/freeze/handoff交父后停止写入，不stage/commit。以下PF1与LF/B3a均历史。

PF1 complete：旧空Manifest目标红→唯一声明最小修复/normal-O纯测试/stage/原host全通过；原唯一生产Lint终态exit0/123.521s/72actions全部executed，两模块0Error/Fatal，App17/runtime10同源warning类别保持明确defer。六份fresh XML精确权限/完整树检查通过；新195build对旧blocked194为3changed+1added/191保持，native80保持/protected94仅授权runtimeManifest变而93保持，528旧证据保持。LF历史blocked freeze957051...不改、不冒current。完整handoff/freeze收尾后停写。

## Next Step
父消费 `.work/p1-full/handoff.md` / `final-input-freeze.json` 与 full 报告，另授reviewagent只读交付证据/安全清单审，随后限定本地提交；p1full-test完成终态收尾后停止全部写入，不stage/commit/push。

### Phase FULL: 完整验证与最终交接
- [x] 读权限修复及同模型fresh只读无阻塞review、依赖规范/两参考、入口实际任务、监督与执行历史；HEAD7eace17/index空，Herdr仅父及本leaf，无相关构建冲突。
- [x] 166旧生成输出baseline备份/hash，195build/80native exact、632history保持；12generated限定clean，精确argv/runner SHA/PIDstart与5秒86次有界RSS采样。
- [x] 唯一full真实终态0，111actions全部executed、全部原gate/35JUnit/生产Lint及新fixture/checksum目标红恢复证据齐全。
- [x] 当前signedAPK/包签名/报告/195输入exact绑定，最终分域freeze/非敏感报告/handoff；停写交父，安全提交由reviewagent另授。
- **Status:** complete

### Phase 0: 恢复与启动前身份
- [x] 已读 AGENTS、依赖 skill/map、B2 handoff/review/disposition、执行三文件与本地验证契约。
- [x] Git / Beads / Herdr / 旧进程冲突复核：M4 in_progress/父owner、M3/M0 closed，主checkout仅父done与本leafworking，无相关Java/native后台。
- [x] 审入口、buildNativeArtifact/prepareInputs链及approved helper；准备只读输入检查、生成输出备份、固定argv/脚本SHA与有界资源采样wrapper。实际before及backup紧贴子命令启动落盘。
- **Status:** complete

### Phase 1: 实际生产 Lint
- [x] 原bg b49d5db96收到durable failed/exit1，消费bg_logs；唯一生产命令145.254s，无重启/timeout，终态无相关Java/native后台。
- [x] App freshXML 11Error+1Fatal/18Warning；87Task行/69action、13gate+7consumer pass及日志行序保存。runtime report未执行到，不伪零警告。
- **Status:** complete

### Phase 2: 核对与交接
- [x] 输入 before/after/current193build/80native exact、HEAD/index/tracked diff不变；138生成旧文件备份，134历史B2文件未变。
- [x] 282次0.5秒采样，最多3相关JVM/同时采样RSS和1959516KiB，host16GiB；非全机峰值。仅必需JNI重编/增量DSO重链，未full/APK/device/commit。
- [x] .work/p1-b3/lint-handoff.md / diagnostics / command-exit-log-report-SHA索引已落盘，停止写入，persistent idle待父。
- **Status:** complete

### Phase LF0: 接管与范围复核
- [x] 已读 AGENTS/依赖 skill 及两参考/诊断/planning/stewardship、根三文件（截断补齐）、B3 handoff/diagnostics、B2 final handoff/review、父监督计划。
- [x] HEAD/base7eace17、index空；Beads owner父/in_progress；Herdr仅父idle与本leafworking，无其他项目writer/GradleLint。既有成果保留。
- **Status:** complete

### Phase LF1: 目标红绿与最小修复
- [x] ActivityWindow 注解契约与真实 SDK guard 边界/变异回归；旧source目标红，生产仅两个TargetApi。
- [x] 真实 ForegroundRecorder + Android fakes + 真实 RecordingControl/PcmWave 构造/start/read权限撤销与finally测试；旧生产8权限phase断言红，修复未复制实现。
- [x] 真实 stage fixture/反例、源Manifest保持、仅两处批准生产SHA接线；旧stage debug目标红。
- **Status:** complete

### Phase LF2: 串行验证及交接
- [x] 相关host/原gate/必要Android Java-JNI编译全通过；一次统一strict offline/cache-only Lint已真实终态1，App/runtime两报告齐但不是Lint pass。
- [x] 新blocked输入freeze/delta/current、旧193/94保护差异解释、全部warnings分类defer、commands/exits/log/XML/SHA索引。
- [x] 完整 .work/p1-lint-fix/handoff.md，Next Step=父fresh同模型review及scope决定，停写不full/提交。
- **Status:** complete

### Phase PF1: runtime 静态权限契约
- [x] AGENTS/依赖skill及参考、历史handoff/review/独立checks、根历史与监督plan读取；HEAD7eace17/index空/父owner/in_progress，Herdr仅父及本leaf，无相关构建冲突。
- [x] 旧空Manifest目标红；最小RECORD_AUDIO-only声明，缺/额外权限/附加属性拒绝，测试接主host库存。
- [x] 相关纯测试normal/-O和必要host通过；唯一统一strict offline Lint终态0，两模块无Error/Fatal及六份fresh真实merge/stage XML检查通过。
- [x] 新freeze对194逐项delta/历史528保持、命令exit/log/XML/guard/hash索引及完整handoff；本leaf无full/提交。
- **Status:** complete

## Blocked Stop Condition
owner/runtime 冲突、缺缓存或需下载、源码身份漂移、产品 Lint error / 超scope 修复或无法守 gate 即停报父。禁止 baseline/suppress/关error/删gate/写hash-lock凑绿；同阻塞三有效方案仍无解停止，不无界追 requiredinputs。

## Errors Encountered
- 最终freeze首次拒绝：误预期protected94全保持；真实清单含runtimeManifest，故恰授权此一变/93保持。保留finalize-attempt1失败log/exit，修正明确delta断言与报告，不更改任何产品/依赖hash。
- permfix-build前两次ordinary final只报启动/待通知，父收到了completed但本任务未完成；父明确resume后接续原唯一Lint。一次核进程仍真running，resumed bg_status原ID未知（registry未跨resume携带），随后原runner独立落terminal exit0/完整execution/log/freshXML证据。未重启/不伪durable通知；本次最终只在完整handoff后返回。协调错误已纠正，不把前两次started/yield算完成。
- 本轮统一Lint b8c883f16 failed1：App0blocking/17warning，runtime MissingPermission1“Missing permissions required ...RECORD_AUDIO”/10warning，空libraryManifest静态契约缺口。原App撤销异常诊断已关闭，但runtime此前未report过。权限/Manifest扩scope禁止，立即停，不抑制、不猜测重跑。见.work/p1-lint-fix/diagnosis-runtime.md。
- JUnit证据采集一次猜错包名前缀FileNotFound，find真实XML路径后成功保存35tests零fail/error/skip；不是测试红。
- 生产Lint真实exit1：MissingPermission1、NewApi10、HardcodedDebugMode Fatal1，18warning，非缓存/工具/资源失败；只读定位/最小scope建议已交父，未降gate或改源码。
- 首次启动检查点过早返回导致persistent turn idle，父要求接续；原task一次性接续确认后等待自动终态，无第二链，现真实终态证据完整。
- 恢复时根历史 findings/progress 输出截断，已补读未显示段，不以截断作完整读取。
