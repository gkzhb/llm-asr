# P1-A 本地分层验证契约

任务 `llm-asr-76d.5`，P1-A 本地护栏切片。**当前 B3b 完整工程验证通过，待安全本地提交，尚非 APK 交付或 M4 关闭**；当前证据见 [full 报告](../reports/local-verify-p1/full.md) 与 [状态](../reports/local-verify-p1/status.md)。下文 B2/三修复/权限修复的 not-run/blocked 描述均为各自历史检查点，不代表本次 full 未执行。命令是未来 CI-ready 的同源入口；**未选择远端 provider，未创建远端 workflow、触发 CI 或上传产物**。当前 runtime 静态录音权限契约补全以 [权限修复状态](../reports/local-verify-p1/permission-fix.md) 及 `.work/p1-permission-fix/` 为准；三处生产 Lint 修复的历史 blocked 检查点见 [窄修复状态](../reports/local-verify-p1/lint-fix.md) 及 `.work/p1-lint-fix/handoff.md`（当时 runtime library 静态权限声明尚缺，不能当作补全后的 current 身份）；旧 B2 执行结果见 [B2 状态](../reports/local-verify-p1/b2.md)、`.work/p1-b2/final-handoff.md` 及 `.planning/llm-asr-76d.5/`；`.work/p1-build/handoff.md`、`.work/p1-repair/batch-a-handoff.md` 和 `.work/p1-b2/handoff.md` 均为历史检查点，不能拿旧 M3 报告或旧 A freeze 当 B2 当前通过。

## P1-B 2B 测试工程层（非设备验收）

任务`llm-asr-76d.6`新增`android-test-build`层；2A/B1已接受，2B fresh上下文隔离审查NO BLOCKING已由父接受（非跨家族独立审查）。最终唯一cache-only strict offline full已真实通过，仍未安全本地提交，不据此交付APK/关闭M5；当前结果见[full报告](../reports/android-test-p1/full.md)及[状态](../reports/android-test-p1/status.md)。Android runner/SAF/Binder/grant/InputConnection及生产Service系统生命周期均not run；SDK deprecated标准runner仅编译候选，不自造runner。

```bash
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh android-test-build --offline --rerun-tasks
```

固定`verifyAndroidTestBuild`依赖host 7 JVM、宿主/仪器两APK与当前产品APKassemble、正式三包checker与纯checker负例。无install/connected/managed-device/ADB任务；它会触发产品原native/prepare链，不是fast。原`verifyFast`精确35任务图/35JUnit不纳新模块；full保留全部旧host/JNI/native/Lint/checksum/签名门槛，并新增此工程层和**一次**隔离fixture行为driver。该前序targeted阶段不提前full；冻结审查接受后，本次验证leaf已唯一执行完整入口，实际exit0/174actions全部executed、原35及新7host JUnit全零、生产app/runtime Lint Error/Fatal0，warnings分类defer；host Lint未接根任务所以未执行（不以identity resolve冒执行）。full内一次fixture行为7case五阶段0/1/0/1/0、隔离Lint真实NewApi与checksum目标拒绝恢复、三包/独立产品receipt当前209build及80native绑定完成，详full报告。docs/report为supplemental，旧P1-A历史不重写。

`scripts/verify-android-test-apk.py`从三个AGP debug output-metadata精确单APK读取，调用当次SDK apksigner/aapt2/zipalign生成独立`build/verification/android-test/run-*/receipt.json`：原证书/v3三包一致，测试两包无权限/native/assets/意外组件或launcher、Backup false、正确标准runner/target/base与runner required库；全部DEX class_def无重复/产品无fixture/JUnit/mock/runner、test不重复target生产类，class_data精确12个public concrete `()V`方法（不是runner发现）。记录7host JUnit XML、完整新module含未跟踪源码库存及SHA、当前build collector、SDK optional/tool输入、三APK/hash/命令/退出/raw hash，拒执行中输入漂移。正式产品旧包身份/native/assets门槛不放松，仍由`:app:verifyDebugPackage`在full独立完成。

纯`tests/android_test_apk_test.py`普通/-O调用同checker策略和DEX解析，有限坏runner/target/权限/export/backup/library/native/assets/class/method及产品泄漏拒绝；它们是parsed-report/DEX fixture，不是签名APK变异或设备运行。`tests/android_test_inputs_test.py`在临时tree真实新增未跟踪源、变更/遗漏/SHA错/格式错、缺lock及build/cache排除，调用相同module/product/format collector，非源码grep。新module maintained全库存动态扫描src/fixtures+build.gradle.kts/lock，排除生成build/cache。

### N1：依赖prepare安全边界

**旧ignored历史主树`deactivateDependencyLocking + --write-locks`路径已废弃，禁止重放；它曾删除root旧锁，不能作为推荐入口。** 历史日志/init只为事故审计保留。正常verify入口始终拒绝所有写锁/metadata/init/任意属性覆盖。2B不重做prepare、无新依赖/下载或锁变更。

未来仅在另行明确prepare授权后：复制必要受维护配置/源码到全新temp项目（无key/权重/音频/缓存/产物），正常启用已有locking，strict/offline固定cache与wrapper；在隔离副本解析实际配置并生成锁，**仅导出获准的新模块锁**，校验主树所有旧锁/metadata在复制前、导出前后SHA一致，并逐项审图/工件SHA/新锁scope。不得deactivate旧锁或主树--write-locks，不使用--write-verification-metadata/宽trusted/下载凑绿；缺cache或strict不符停报。事故恢复已审，不抹历史或无理由重跑旧成功链。

## 统一入口

```bash
# 外层 Nix 也必须 cache-only；Gradle --offline 本身不会约束 Nix。
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh fast --offline --rerun-tasks
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh graph-fast --offline
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh lint --offline --rerun-tasks
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-local.sh format --offline
# 完整入口，旧脚本保持 full 语义，不默认变成 fast：
bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks
# 完整真实 checksum 变异检查（与主缓存/metadata隔离）：
bash scripts/nix-env.sh apk --cache-only bash tests/gradle_dependency_verification_test.sh
```

`nix-env.sh apk --cache-only` 显式给 Nix `--offline`、禁止更新/写flake锁、空 substituters/builders、`--max-jobs 0`；缓存不齐即拒绝，不自行下载或构建宿主工具。旧 `nix-env.sh apk ...` 的联网准备语义保留，但**本轮不获授权使用它补缓存**。离线缓存通过不等于全新机器/hermetic。

`verify-local.sh` 只接受明确 layer 和 `--offline` / `--rerun-tasks` / `clean`；拒绝任意其他任务、属性、init script、缩写、写锁/metadata、strict覆盖、排除任务或并发覆盖参数。不通过扩参绕过 gate。日志与 exit 位于 ignored `.work/verify-local/<layer>-<id>/`。管道一次保存Gradle/tee全部状态；Gradle非零优先保留，否则tee非零保留，日志捕获失败不可称成功。DEX扫描完整读取，0命中拒绝/1未命中允许/其他读取执行error拒绝，保留原失败码。`graph-fast` 仅 dry-run 任务图，不算测试通过。完整解析每条 `:module:task SKIPPED` 行，恰一次根verifyFast、无重复/畸形任务；拒绝Lint/checksum/host/JNI/native/CMake/NDK/APK/安装/设备等重型families及未审module/root任务。core/runtime也只允许已有真实35任务登记的精确path，未知如core:downloadArtifact拒绝；审批点是 `APPROVED_PATHS`，变化需真实图与语义窄审，不自动扩容。前导space/tab的task候选直接拒为畸形，不忽略/strip接受。两个确切runtime library JAR staging任务不是APK/AAB，单独允许；真实主图确认 `:asr-runtime:packageDebugResources` 为JUnit必需资源stage，亦仅此模块/确切任务允许（不允许app的同名任务或packageDebug/APK）。所有图中任务都检查，包括间接依赖；禁止借任务名子串或空图凑green。`clean` 不应在只查看图时被误认为已执行。

| 层 | Gradle任务/实际内容 | 明确不证明 |
|---|---|---|
| fast | `verifyFast`：core34/runtime1 JUnit，Java/Kotlin ABI、全部原 main body/latch、PartRecovery生产mutant、两个实际 module classpath 编译负例；source架构/隐私接线负例；入口/格式checker目标负例 | 不运行完整 host 复编、模型3mutants/ActivityWindow行为3mutants、JNI/native/APK或Android运行；不包含Lint |
| lint | `verifyLint`：原生 AGP `:app:lintDebug`、`:asr-runtime:lintDebug` | app的preBuild→prepareInputs→buildNativeArtifact原链保留，故不是保证轻量/无native的fast任务；缺缓存/生产遗留问题必须报阻塞 |
| format | `verifyFormat`：显式维护文本列表的UTF-8/LF/终止换行/行尾空白/首部tab检查，只读 | 不是全仓Java/Kotlin语义格式化，不扫描历史readelf/原始报告 |
| full | `verifyDebug`：所有原host/ABI/latch/mutants/JNI/native/35JUnit/模块/APK checker/签名/strict输入身份，再加source架构/入口/格式、Lint及隔离真实checksum负例 | host/ELF静态检查不等于设备、麦克风、SAF/IME/JNI或4KiB/16KiB运行验收 |

完整 `verifyDebug` 的原依赖没有删除。坏依赖checksum脚本已作为 `verifyDependencyEvidence` 接入full；亦可单独运行诊断，执行strict offline help原正例→唯一AGP jar SHA变异→恢复正例。变异目标component/artifact/SHA节点须各恰1，其他metadata语义不变；当次HTML目标tbody行必须expected全零而actual等原SHA，并实核cache artifact SHA，不用页面通用帮助文字或缺缓存作红，仍须实际目标checksum diagnostic，不把工具/网络失败当红。快速不是最终产品交付门槛。各重型命令串行，保留单worker/1536MiB Gradle JVM、512MiB JUnit fork、Kotlin in-process。

## Lint与格式政策

- 不新增格式框架/AndroidX或升级工具。Lint沿AGP8.6.1现配置，默认error失败，不加baseline、宽抑制、关闭error或删除gate。默认warning仍须在报告中披露/审查，不能声称warning不存在。若需超slice生产修复，先报范围和证据。
- Lint runtime是AGP按需解析依赖；插件可配置成功不等于Lint依赖已缓存或checksum已登记。不得在verify中自动写锁/verification metadata。
- `scripts/check-maintained-text.py` 的 `INVENTORY` 是可审查窄范围。新维护文本加入列表需同步真实fixture，不纳入历史原始readelf证据，不机械全仓格式化。测试普通与 `python3 -O` 均有效；修复A另以固定Python3.12.14普通/-O运行入口故障、图、checksum parser，记录PATH Python3.14.7差异，不改flake隐藏它。
- `tests/local_verify_entry_test.py` 使用隔离mock executable证明shell参数/退出码/诊断传播，不冒称真实Gradle/Lint acceptance；真实任务图/JUnit/Lint/full和checksum负例必须另外运行并保存exit。

## B2 Lint实际工具消费门槛

`gradle/verify-lint-tools.gradle` 按固定AGP8.6.1类型getter读debug Lint报告/分析任务的真实detached Configuration及classpath.files。每个consumer依赖只resolve的verifyLintToolIdentity；gate首次要求唯一真实Configuration，完整通过后以task对象保存该引用（不是named审计配置或旧report布尔值）。Gradle8.7在action前会将classpath源最终化为文件适配器，doFirst因此使用已成功gate的actual引用再次核验完整图及当前实际classpath.files的canonicalpath/SHA多重集，并拒重复canonical路径；不要求最终化from仍Configuration、不反射/替换AGP内部。exact根/flags/attrs/节点/完整171边（含2原constraints）/66JAR SHA与批准manifest相符，未知/缺/多节点边或文件均拒。不是named审计配置锁，也不替换AGP内部指向；主plugin reflect1.9.22与Lint工具图1.9.20分域，主锁不被fixture锁覆盖。

B2准备只合并23受准入artifact新增，旧412artifact/value/origin/configuration保持；legal-only5POM默认未合并。来源声明host准入不等NOTICE/legal release或CVE无风险，CVE/PGP/SBOM及kxmlvariant/P10边界留待发布审计。

`tests/gradle_lint_verification_test.py` 是单一隔离library实际driver：无app/native/model/key/audio装配，strict offline固定API33 guard→NewApi目标Error/Fatal→原bytes恢复。XML必须fresh、具体file/line/API33/min29；非目标工具失败不算红。identity manifest副本工件SHA/边变异须先gate拒绝不运行Lintconsumer。AGP CreationAction初始化时已对默认consumer classpath调用fromDisallowChanges，不能合法post-gate修改它。driver的普通DefaultTask测试适配器取得真实productiongate通过的Configuration引用，传独占mutableFileCollection给**同一个生产validator闭包对象**，不复制图/工件或validator实现；缺/多/替换必须共享完整validator具体files mismatch拒绝且无测试marker/目标XML，恢复仅执行普通测试marker。生产外层仍要求task-specific成功record和同fc对象。此为普通Gradle测试任务调用同生产validator/真实Configuration的集成负例，不是typed/default AGP mutation或恶意root防护；defaultAGP真实green-red-green另跑。旧test-ownedtypedconsumer三失败保留，路线停止；Gradleimmutable/validation异常不算目标红。纯identity与XML检查普通/-O区分于实际Lint证据。full新增verifyLintEvidence（fast不接）；父/子single-use Gradle JVM及Lintworker仍存在，fixture独立worker1/JVM1536m，固定AGP合法experimental lint heap768m，仅child作用。约16GiB主机仍串行，full驻父峰值须B3验证，不声称no-daemon等于无额外JVM。

## B2 历史执行状态（2026-10-15）

恢复核对已有 `run-ukwens2p` 成功 fixture，未无理由重跑：默认 AGP Lint green0=0→唯一 NewApi Error（API33/min29、目标 file/line）red=1→原 bytes green1=0，green XML 无 Error/Fatal；工件/边身份及普通共享 validator files 缺/多/替换均目标拒绝 1、普通任务恢复 0。当前 gate/manifest/wrapper/metadata 与隔离副本一致，模板与 driver literal 注入精确对应。历史未保存启动时完整 driver SHA，且当前 driver mtime 落在早期 missing 命令期间、默认 green0 之前；因此只声明恢复后的 payload/实际命令/源快照/log/XML 一致，不伪称 launch-time driver attestation。不是默认 typed AGP classpath 可变性试验。

最新共享 validator 实现另以固定 Nix cache-only、Gradle strict/offline 串行复验：主 app6/runtime7 consumer ONLY RESOLVE（各 66 工件/171 边含 2 constraints，仅两个 gate task action，无生产 Lint）；graph-fast 精确 35 任务无重型链；fast rerun23 action、core34/runtime1 JUnit 零 failure/error/skip；checksum help 原0→唯一目标1→恢复0；固定 Python3.12.14 与 Nix PATH3.14.7 各普通/-O 七项纯测试通过。193 个 build inputs 在回归前/后/当前一致，新 B2 freeze 分 build/native/supplemental/evidence 域；旧 A v4/blocked/typed-stop 快照保留而不称 current。

以上为 B2 历史快照：其后 fresh 同模型审查 NO BLOCKING，父已接受；原 B3a production Lint 真实失败（11 Error + 1 Fatal / 18 Warning），证据 `.work/p1-b3/` 只读保留。当时用户批准三处修复：已有 SDK guard 下 helper 精确 API 契约、录音权限撤销固定安全 IOException、AGP staged Manifest 去冗余 debug 属性。源 legacy Manifest、debug DSL、权限/组件/签名/录音 gate 不变。新 host Android fakes 只证明生产 adapter/cleanup 契约，不是设备测试；staging fixture 不证明最终 APK，后续 full 必须核 merged/packaged debug 身份。

B2 freeze 的193 build/94 protected是历史输入，不再宣称全部等于修复后 current；本轮仅允许的两份生产源、两项 baseline 源码 pin、stage/host接线及窄测试变化必须由新 delta 明确解释，依赖 metadata/locks/native数学保持。本轮唯一统一 Lint 真实 exit1：App原12blocking关闭/17warning，runtime首次standalone报告缺既有RECORD_AUDIO静态声明1Error/10warning（同源warning重复）。当时未授权改library权限Manifest，故停报父、不抑制/不重复猜绿；此为保留的历史失败状态。用户其后明确授权本轮必要窄修复自主推进，当前只补runtime与App相同的RECORD_AUDIO无附加属性声明并验证最终权限集合不增。**完整 full/APK/package/current APK 输入签名绑定、设备与提交仍未完成**。本状态不授权验证旧 APK、不代表 M4 关闭或产品交付。旧 B2 命令及 freeze 不重写，新修复证据独立保存。

## Runtime 静态录音权限契约

`tests/manifest_permission_test.py` 用小型 unittest 共享 XML checker 校验源 runtime/App、真实 stage 与当前生成 merged/packaged XML：精确一条无额外属性的 RECORD_AUDIO-only 声明，拒缺失/额外权限/重复/sdk23变体/maxSdkVersion/flags，保 App microphone required=false 与 debug DSL 策略。normal/-O 接主 host gate，递归 build collector 收录测试及 runtime Manifest；该 Manifest 不在现 baseline.files 静态 pin 内，不为此修改依赖 checksum 或无关身份。Manifest 声明不授予运行时权限，原权限 gate/安全异常仍必需。本轮唯一统一Lint已真实exit0、72actions全executed；App0Error/Fatal+17Warning、runtime0Error/Fatal+10同源重复Warning，原类别明确defer未抑制。六份fresh生成XML整树比较：App保持、runtime只加RECORD_AUDIO；当前195build新freeze对旧blocked194逐项解释，旧freeze保历史。详当前权限修复报告；这些不替代后续fresh差异审查/full签名APK包检查，现有包检查精确权限白名单不放宽。

## B3b 当前完整工程验证（2026-10-15）

唯一获准入口 `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks` 真实 exit0，428.052s；Gradle `111 actionable tasks: 111 executed`，完整日志140条唯一task行（含无action聚合、12 NO-SOURCE、6无action UP-TO-DATE及3 SKIPPED），不能把140或119无状态task行冒充action数。所有原required gate实际执行；pipeline Gradle0/tee0/DEXscan1。先备份166生成文件及旧APK，只清12个已知generated class/test/APK目录，保留缓存与578 MNN对象；不是全native clean或空缓存复现。

core34/runtime1共35 JUnit零failure/error/skip，原host/ABI/latch/生产PartRecovery和model3mutants、ActivityWindow82checks/7可编译mutants、capture12phase、真实module compile边界、JNI生成头/实际符号、native配置/provenance/ELF16KiB与包checker均通过。本次生产Lint两份fresh XML均0Error/Fatal；App17/runtime10同源warning继续明确defer，未baseline/抑制/关error。13 actual identity gate及8consumer各66工件/171边通过。full内新独立fixture实际完成工件/边及共享validator files缺/多/替换拒绝、marker恢复、默认AGP Lint0→唯一NewApi API33/min29 Error1→原bytes恢复0，保存本次启动driverSHA，补足旧B2历史launchSHA局限（不改旧历史）。实际checksum原0→唯一AGP目标1→恢复0，fresh HTML expected全零/actual与cacheSHA精确；非网络错误算红。

现signedAPK由既有checker真实通过，路径 `android/app/build/outputs/apk/debug/app-debug.apk`，5216237 bytes，SHA256 `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10`。0.6-debug/code6/min29/target35/arm64，唯一RECORD_AUDIO、原debug/组件/签名身份保持。包及生成报告/195build/80native输入与批准权限修复freeze exact；native仍d5bacffc…a416，578对象SHA全保持，仅JNI重新编译/增量最终链接。632旧证据SHA保持，旧blocked快照不冒current。最终文档/plan/证据分域freeze见 `.work/p1-full/final-input-freeze.json`，仓库仅非敏感摘要/输入和产物hash/JUnit清单。

5秒有界86采样观察最多4相关JVM，同时JVM RSS和最大3125124KiB（约2.980GiB）、相关树3155308KiB；host MemTotal16293168KiB，采样MemAvailable最低9748416KiB。RSS共享页可重复计，短生命周期/未见detached进程可能漏，不是PSS/全机峰值；no-daemon不等无额外JVM。单worker/JVM1536m、JUnit512m、Kotlininprocess/native并发2，fixture768m独立Lint heap策略保持，无并行第二Gradle。

本leaf未stage/commit/push；父消费后由reviewagent另授安全清单审与限定提交，成功前不宣称可验证APK已交付。已接受审查为用户指定同模型fresh上下文隔离只读、非跨家族独立，非本leaf重跑。设备4K/16K、麦克风、IME/SAF/JNI运行、模型权重计算、远端CI全部not run；不关M4。

## 执行冻结辅助工具边界

ignored freeze-a.py仅读真实collector库存span和native input_hashes span，经已人工审完整源码行片段SHA匹配后才compile/exec；任何片段变化须先审批准新身份，不能自动rehash凑通过。此处不是通用AST安全解释器或future unknown code sandbox。新freeze独立build/review/external及generator/tests身份，旧179/13/A-v2历史保留。临时tree新增script/native/source、缺source-lock及unknown源码片段有真实拒绝测试，未知片段不执行；不在main生产源码取红。

## 未覆盖/授权边界

上述P1-A历史slice未覆盖P1-B；当前新增P1-B本地测试工程范围见本文2B章节及reports/android-test-p1/status.md。P1-C策略ADR、远端CI配置、release/R8/新签名、模型性能和设备运行仍不在本slice。若宿主工具或Lint缺缓存，保留入口代码及可验证检查点，报告所缺准备输入，由父决定后续授权；不交旧APK、不称本轮全部完成或M4关闭。
