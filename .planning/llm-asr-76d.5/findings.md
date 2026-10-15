# P1-A Findings

## B2最终恢复收尾（p1b2-build，2026-10-15；以下旧阻塞为历史）
- run-ukwens2p成功结果已恢复核对，不重跑fixture。当前gate/manifest/wrapper/metadata与副本逐字节相等、模板+driver literal精确重建build；所有目标command/execution/exit/logSHA/sourcebytes/XMLSHA及uniqueNewApi(API33/min29/file/line)一致。默认AGP真正0/1/0，与普通DefaultTask调同productionvalidator闭包/真实passedConfiguration的files缺多替换1/restore0明确分域，不冒称typed mutableconsumer。production外围成功record/fcidentity未取消，纯record模型也不冒称实际AGP变异。
- 限制：历史未存launch driver完整SHA，当前driver mtime在早期missing命令期间、默认green0之前；不能证明完整launch-time driver bytes，只声明当前payload/实际记录恢复一致，不伪attestation。旧typed三strike交接另存原字节history，原handoff.md亦保留，SHA3689a35d...209e；永久不恢复typedrequiredinputs模拟路线。
- 缺失后置串行bg b6763c0ee自动completed0已消费：最新mainapp6/runtime7仅2gate actions ONLY RESOLVE，各66artifacts/171edges含2constraints；graph-fast exact35无heavy；fast rerun23action/35JUnit无fail/error/skip；checksumhelp0/唯一target1/0；Python3.12.14及NixPATH3.14.7各normal/-O七项矩阵通过。193build map前/后/当前精确相等，源实现无改动。
- final-protection.json证明HEAD原412artifact字段/hash/origin/config完整保持，仅批准23candidate exact新增（435总），5legalonlyPOM不存在；manifest approvedActual完整171/2/66投影exact，主4locks/94protected及已审helper/tests不变。未碰production/native/collector/pins/权限签名/录音IME隐私。A/B1许可/来源既有边界不重审，P10/CVE/PGP/SBOM/kxmlvariant/BCtag deferred。
- final-input-freeze.json是全新B2 build/native/supplemental/evidence域，不覆盖旧183/192/193历史；native为未变输入子集/external为旧观测，非本轮产品构建。B3productionLint/full/JNI/native/APK/package/currentAPKbinding/freshreview/device/commit均未做。唯一下一步父只读review，完成交接后本writer停止。

## B2接管事实（p1-build-2）
- 真实run-mqgvsbpg green0失败在:lintAnalyzeDebug doFirst的Configuration source形态检查，此前独立gate已全7consumer66artifact/171edge通过；无targetLintXML，不可当NewApi红。
- 固定Gradle8.7可信DefaultConfigurableFileCollection字节码显示finalizeNow→calculateFinalizedValue会把source collector换为ResolvedItemsCollector；getFrom来自PathSet。因此执行期from类型不保证保留原Configuration。独立生命周期诊断bced34ba2真实failed1确认：gate原1Configuration、consumer doFirst66FileCollectionAdapter，同66files；Gradle FinalizePropertiesTaskExecuter出现在stacktrace。当前API不兼容触发父stop条件，不调整产品gate/不反射。
- read-only语义复核：412old metadata全部字段/origin不变、configuration不变、23added与批准candidate exact；94受保护v4 inputs（production/module/native/pins/mainlocks/collector）不变。manifest与B1comparison-policy.approvedActual根规范化投影逐完整edge/artifact多重集一致，171edges含2constraints，66artifact及policySHA准确。五legalPOM未merge，无新下载。

## B2 test-owned fixture stop
- 父批准normalfinalization修复与test-owned AndroidLintAnalysisTask mutableconsumer；产品guard代码已按task对象/actualconfig引用/完整图SHA+currentfilescanonicalSHA实现，未放宽身份或反射。
- test-owned三不同有效attempt全部真实failed1：b7c2f3a32配置早于AGPtasks注册；b1020f2b7 afterEvaluate能成功gate/mutation但taskscalar未填；b77b753da补第一批scalar后下一批nested/model属性validation仍缺。均在doFirst之前，无我方target拒绝/markerrestore/NewApiXML，不能算目标红。
- 三strike触发停构建/写入，不能无界补字段或drop验收；下一输入由父决定完整有界fixture初始化或替代证据。其默认AGP immutability与test-ownedmarker语义一直区分。

## B2事实
- B1strict实际消费与named审计lock差异已闭合：actual171edges含2原constraints、named多66锁constraints，不把named消费持久锁化。本批采用actualconsumer exact gate不替换AGPconfig。
- 主metadataprepare只+23受审条目，旧412及configuration语义一致；5legalPOM未merge，main/root插件reflect1.9.22锁无变。
- 首Groovyapply独立classloader无法importAGP，未解析消费者；由主typedKotlin/fixtureGroovy确定class literal传入辅助，非反射，retry实际onlyresolve通过2actions22s。
- 真实主覆盖app6/runtime7debugtypedconsumer（含分析/报告/fix/baseline任务只读realize，未执行baseline/fix）：全部66artifact/171edges相同，包含2actualconstraints。主Lint/native/full都没跑。
- typedconsumer依赖gate+doFirst真实fc.files/artifacts exact。fixturechild合法AGP lint heap768m，主16GiB仍worker1/JVM1536m，父子峰值B3未验收。

- 父owner p1-steward，Beads M4 in_progress，M3 closed；Herdr仅父done及本leaf working（另有不同项目idle），无产品writer冲突。HEAD/base7eace17，tracked/index diff为空。
- scripts/verify-gradle.sh 原入口执行 verifyDebug，严格checksum和DEX诊断拒绝；现参数仅黑名单，允许任意Gradle参数是待收紧面，保留 offline/rerun/clean 合法用法。
- verifyDebug 原包含 host/JNI/两模块JUnit/native输入/APK checker及签名包检查；真实35JUnit和module classpath负例应全部保留。
- :core:test 自带ABI、34JUnit、PartRecovery生产mutant；runtime1JUnit并不触发app native。快速可复用这两个JUnit task及轻量source architecture fixtures，避免重复host编译。
- app preBuild -> prepareInputs -> buildNativeArtifact，全app AGP Lint可能依赖该链；需要先实测dry-run，不删preBuild gate去凑fast。
- 现缓存 com.android.tools.lint 仅列 lint-model / lint-typedef-remover；Lint runtime无法实测，外层固定Nix宿主closure不齐，不能下载补齐。原调查误用普通nix-env，发生cache.nixos.org替代下载，被父停止；exit1，Gradle任务未执行。
- 新nix-env --cache-only显式offline/禁止写更新flake锁/空substituters和builders/max-jobs0。真实预检exit1拒绝缺失FixNullPtrCast.patch、alsa-lib、NDK28.2及JDK17/21closure；日志无copying path，不能用旧shell冒充当次固定环境。M3 flake.nix/lock当前SHA相同，说明缓存缺失而非本轮改工具。
- 已实现统一fast/full/lint/format/graph-fast入口及严格参数allowlist；full仍保留所有旧gate，另加原生Lint、限定格式/source/入口fixture与隔离真实checksum变异。cache缺失时保持full失败，不将Lint删出full。
- fast任务接core34/runtime1JUnit和实际模块编译边界，不接app/native链；真实Gradle任务图尚未执行，因此当前仅设计/代码事实，不能声称图已验收。
- 格式9显式维护文本，针对UTF8/LF/final newline/trailing space/tab/NUL和symlink真实checker负例普通/-O有效；raw历史fixture保持原字节。
- Lint任务直接接AGP两个debug任务，不加baseline/抑制/关error；app原preBuild链保留，可能重型，明确不属于fast。缺Lint依赖/strict登记或现产品诊断需后续授权准备/证据决策，当前unknown。
- 格式checker限定维护文本/本轮文件，历史readelf/报告原始证据不纳入；不引入格式框架，不机械修改历史。
- 依赖/工具版本不变，Gradle单worker/JVM1536MiB/Kotlin in-process仍保留。offline不等hermetic或设备验收。

## A子批
- 父授权仅入口/graph/checksum与ignored冻结修复，不改collector/locksmetadata，不Lint/full。工具cache-only由p1-test解除。
- R1旧入口目标fixture实际执行：tee23被吞exit0、grep2被吞exit0；Gradle7仍exit7但缺tee诊断。目标pre-fix red日志a-pre-fix-red.log/exit1，不是无关失败。
- R2旧入口Lint/checksum/JNI变体/CMake/package任务被接受exit0；本轮完整task解析、全部heavy family先检查再检查module/root范围，允许两个确切library JAR stage（不是APKbundle）。空/畸形/重复图拒绝。
- R1修复已目标绿：tee保留23、Gradle7/tee23仲裁7并报告两码、grep2保留2、各恢复0；一次捕所有PIPESTATUS，DEX扫描不-q。
- 首轮旧入口fixture遗漏verifyFast合法图导致新的parser正确missing-root拒绝但测试期待heavy；补完整合法图而非放宽parser。首次失败已记录，不算实现gate退化。
- R3采用ignored freeze-a.py仅执行已审collector AST inventory片段及native input_hashes AST；不导入APK/native顶层逻辑、不改已正确collector。新build_inputs精确keys/hash、supplemental分域；source-lock及native输入子集有目标缺失/漂移拒绝。
- R4checksum辅助纯parser唯一component/artifact/SHA断言、XML序列化只改目标value；HTML必须唯一tbody行且expected零actual原SHA，真实cached artifact另核。完整恢复正例尚待真实运行；Lint部分明确B未做。
## A收尾A1–A4
- 父p1-review-3处置已读，原R1/source-lock漏/checksum部分关闭，不重做其逻辑。未知core/runtimetask及leading whitespace真实目标tests先旧程序红，日志polish-red-entry/graph；片段变异旧generator到exec被无害observer拦下，新src未入库存，polish-red-freeze1。
- 仅改graph parser+两个相关tests及docs/plan/report；不改根Gradle/R1/checksum/collector/metadata。APPROVED_PATHS来源真实35main任务（另外保留原clean契约），未知module task明确拒绝。
- ignored generator执行前比对完整human-approved源码span SHA：collector c3eb2478...，native85701edb...；不是末端名白名单安全证明，不执行未知片段、无自动更新身份。临时tree新增src/script/native/缺锁/源段变异/import alias/重绑定均目标拒绝和恢复，不访问main敏感数据。

- 实际无plugin Gradle7组图已通过：6目标拒绝（direct Lint/checksum/host/JNI/APK与indirect native）Gradle本身BUILD SUCCESSFUL、入口exit1；恢复图exit0，graph-real-vt9g1iit日志。
- 主图Gradle真实KotlinDSL配置exit0/图success，parser首拒asr-runtime:packageDebugResources。这是runtime test必需资源stage，未构建APK；仅该module/task确切例外，不允许app package或packageDebug/Apk，tests同步。当前main图35任务、无app/native/Lint/full链。最终执行证据待任务终态。
