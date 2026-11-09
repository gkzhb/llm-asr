# M3 模块与旧测试覆盖映射

## llm-asr-xyh 受控变体（实施中）

新增纯core `ModelPackagePolicy`/`ModelDeployment`/`ModelCatalog`，既有AndroidModelVerificationIo内嵌catalog adapter不新增runtime owner；app/runtime归属不变，待正式baseline采纳后生产顶级owner预期73。native/apk/model_graph_policy.h只在JNI编译固定schema资源profile，不改578vendor对象或数学。当前baseline/native预期尚未采纳，不以旧class/APK库存冒本轮通过。

独立 `model_variants_contract_test.py` 编译生产policy/catalog/proof接口，合成FP16/INT8/INT4/MIXED声明、正确重绑SHA的恶意配置、身份冲突/持久选择/重装隔离；`native_model_graph_policy_test.py`真实生产header+缓存schema微图正负，不运行ASR。picker继续完整Activity对受控平台fakes，覆盖全局导入发现/字节/取消真实close收尾与绑定前失败重入、空custom记录确认删除/耐久写失败重试及未知文件保护，不是Android runner。native selector参数负例/完整清单、ConvTranspose3D错union仅guard拒绝亦由同driver覆盖。新增host入口不删旧JUnit/owner/ABI/ZIP/cleanup门槛。全部当前证据待targeted/fresh审/full/输入身份绑定与本地提交，不能以本页声称交付。

## llm-asr-7vl 固定 ZIP 导入

当前生产owner由66增至70：新增 core `FixedModelPackage.kt`（固定trust/严格JSON）、`ModelZipArchive.java`（有界CEN/LFH/Inflater/CRC）、`ModelZipSource.kt`（私有archive/session）；runtime仅新增 `SafModelZipSource.kt`（净化SAF单stream）。原app8、runtime由6至7、core由52至55；生产测试体不进入release。`ModelVerification`/`AndroidModelVerificationIo`/`ModelAccess`/`ModelResidency`/`ModelOperationControl`只读不变。

`tests/ModelZipImportTest.kt`接入既有 core JUnit modelRepository 方法（原45000ms不变）与host混编；独立 `model_zip_import_contract_test.py` ordinary/-O 测生产ZIP/事务/资源/fault/cancel/crash/mutants，默认非quick复用已编译classes执行全部六个catalog清理回归（targeted-only复用同helper），`model_zip_picker_test.py`编译完整Activity+typed page/ticket及受控provider，不等Android runner。精确库存、输入collector、DEX owner负例保持fail-closed；基线pin采纳与full后置另授，不以旧66输出或旧APK冒本轮green。详[定向入口](local-verification.md)及[格式](model-package-format.md)。

## llm-asr-zcm B：显式准备与单进程复用（targeted工程，尚不交包）

文件校验证明与内存loaded分开：启动只轻恢复proof，模型管理页显式prepare/release；prepare在共享worker加载一次，同有效generation幂等，App/IME共用唯一resident。未prepare在asset/provider/capture之前拒绝，adapter末次复核；请求无load fallback，warm返回load_s=0且阶段仅3/4。prepare独立UUID的1/2事件，不生成REQUEST_SUCCESS。busy release立即关新准入，原native/cleanup/RequestRunner真退出后由既有worker卸载；release失败保EVICTING/pending，UI_HIDDEN20/页面离开/IME切换不卸载，无TTL或新线程。proof第四参maintenance lease覆盖current/generation/真实Repository写事务，先unload完成再DIRTY/资产首写，期间prepare拒绝。

本轮状态及证据见[模型驻留targeted](../reports/model-residency-zcm/status.md)。真实production状态/adapter/proof/repo/owner及可编译mutants、actual JNIbridge受控port只证明工程控制流；SDK/JNI对象编译不等Android执行。用户明确改为官方正常复用合同：Module4深清撤回，6成员只清Llm/Omni/Tokenizer请求状态；正常onForward自动resize/rebind，不每请求重建Session/清Command。旧U1全私有状态枚举/固定图读取前置被有效寿命/下一执行正确输入及KV隔离合同取代，历史不抹、具体可达缺陷仍block。维护入口`tests/native_request_cleanup_test.sh`显式build/prebuilt，真实definitions、Add/Reshape/MatMul/Attention/Session及LlmOmni/BPE、ownedexit/完整receipt；测试源4新增路径见[native文档](native-build.md)，数值负控不是上游bug。无模型资产读取/实际ASR/设备/PSS/性能/ASan验收。native期望SHA保A待另授identitybootstrap，full/新APK/安全提交未执行，旧A包不是B交付。

任务 llm-asr-76d.4，实施基准538dcb66；实现、本机验证、父独立复验和异家族审查完成，无本轮阻塞。功能已由父安全本地提交 `9ec98d3b9e9a2f28af21d86f811639e3a0302ade`，父批准工程验收并授权关闭M3成功。当前APK可供用户手动验证，但设备未验收；关闭状态文本待父另提交。

## 实际模块图与生产归属

```text
:app ────────> :core (纯JVM Java-library + Kotlin-JVM)
  └─> :asr-runtime (Android library) ──api─> :core
```

保留 `android/app/src` 物理路径、包名、63生产类的源码字节（M6新增ActivityWindow，不能沿用旧62快照）和发布组件名。第一轮以sourceSets和实际JavaCompile过滤建立唯一编译归属，不是执行旧APK脚本包装出的模块。根Gradle `appSources` / `runtimeSources`是小型精确适配清单；剩余50类为core（10Kotlin/40Java），runtime5Java，app8Java。javac显式空sourcepath禁止从共享目录隐式重编其他模块源码，AGP编译task也按同一清单include；app无生产Kotlin owner。

| 模块 | 归属 | 不允许 |
|---|---|---|
| core | task/录音控制/波形/ASR协议与注入port；模型SHA/文件发布；IME状态/策略；模型页策略；日志持久化/导出票据/结果状态 | android.*、org.json、JNI实现、app UI/装配依赖 |
| asr-runtime | JniNativeTranscription、ForegroundRecorder、SafModelSource、OperationContext、ImeBackend | AppGraph、Activity/UI、app依赖 |
| app | AppGraph、MainActivity、LogsActivity、ModelManagementActivity、AsrImeService、ActivityWindow、AsrOperation、AppReportWriter（平台JSON适配） | 复制core/runtime生产owner |

native显式M2构建与打包任务暂留app，runtime持有Java JNI桥；不改变nativeflags/SHA/输出位置。AppGraph手工DI保留。核心不需Android **runtime或SDK classpath**；项目配置仍配置两个Android工程，推荐通过固定Nix入口提供工具环境，不能把这一点称为无SDK整仓配置。

## 当前测试源码 Kotlin 切片（llm-asr-ap5）

当前 core wrapper 为 `core/src/test/kotlin/CoreRegressionJUnitTest.kt`，runtime wrapper 为
`android/asr-runtime/src/test/kotlin/RuntimeRegressionJUnitTest.kt`。仍为普通 public 无参类，
34+1 个实例 JUnit4 方法/原 delegate 参数/45000ms timeout；不是 object、反射分派或改名后的未发现方法。
core test Kotlin 只精确登记该 wrapper 与根 tests 的 MinimalApkTest、ResultFilesTest、NativeResponseTest、
ModelOperationControlTest、ModelRepositoryDeleteTest、ModelPrivateRootTest、TextExportFatalTest、TextExportSafetyTest
八个 `.kt` body。其 public static main(String[]) 二进制入口、原六个 throws Exception 与两无 checked throws 保持；
ResultFilesTest 仍可继承。仅这些 body 未被外部维护 caller 使用的包 helper 收敛为 private 测试实现，新增 Companion
属附属形状差异，不称测试 classfile 全等，也不授权生产包能力改变。TWR 保 Java Throwable 成员 suppression 顺序。

runtime test Kotlin 只允许 compileDebugUnitTestKotlin 编译唯一新 wrapper，生产四 Kotlin owner 与其他 variant 过滤不放宽；
Java InferenceAdapterTest 及所有其余 Java bodies/ABI、SAM/default/switch/null、真实 javac 模块负例保留。
host shell 八路径、next driver 明确八 Kt caller（拒缺失/同 stem 双源）和 legacy 三指纹同步，不递归纳 JUnit/host-only fixture。
新 tests/kotlin_test_slice_test.py source/self-test/XML guard 使用同 checker 精确方法/class/count/无 failure/error/skip 拒绝，
core/runtime Test 执行后核实际 XML；注解/ctor/main/Throws 另由 fresh javap 与真实 runner/main 执行附证。
新三条真实生产可编译 mutants（token cap/clear epoch/compound fatal）普通和 -O 各最多九 routes，compile0→目标外部
AssertionError1→恢复0，沿现 owned session/lease 清理；不是编译失败或源码 grep 当行为红。原所有 gates/预算不变。

上述接线本身不证明本轮测试已通过：当次命令/输入/发现与保护证据在独占 `.work/alljava-build/`；targeted 后需 fresh
上下文隔离审查、必要正式 full 与安全本地提交，之后才可交付对应 APK。test-host 7 JVM、14 仪器方法与两个共享 fixture
本批原样；Android runner/设备/麦克风/SAF/IME/Binder/JNI 运行均未在此切片验收。下文 M3 结果保历史，不沿用为本轮证据。

## P10 / T5 test-host Kotlin 维护切片

五个维护 Java 实现迁至同名 Kotlin：fixtures/java 下 FakePorts、FixtureChecks，src/test/kotlin 下
FixtureContractTest，src/main/kotlin 下 ImeHostActivity，src/androidTest/kotlin 下 FixtureInstrumentationTest。
FakeDocumentsProvider.java 原字节保留；不是 Kotlin 文件包 Java 字符串或隐藏旧实现。main/unit/androidTest
精确 Kotlin owner 为 1/3/3；main/unit javac 预期 NO-SOURCE，但须核 fresh Kotlin task 和实际 class，
AndroidTest javac 仍仅真实 Provider。KGP2.0.21/AGP8.6.1/JVM1.8/Werror 与正常单 module 锁固定。

正式 host 接 `kotlin_testhost_migration_test.py` 的 source/self 普通及 -O、三生产 mutant 各九 routes
普通/-O、原 Java 与 Kotlin 相同外部 consumer 的故障 probe；保留所有原命令和预算。
独立 `android_test_fixture_test.py` 仍为原两个合法 fixture mutant、五阶段每600秒与真实七方法 XML，
仅语言锚点和 fresh Kotlin / Java NO-SOURCE 检查相应更新。方法库存、Throws、fields、nullable 延迟读取、
Java Throwable 成员 suppression 不因迁移删减。APK collector 强制五 Kotlin 加 Provider 非空，
DEX private lambda 仅逐名/flags 精确例外，不放开任意 private/test 前缀或外 namespace。

本批只授 targeted，实际结果及 NOTRUN 见 [P10状态](../reports/kotlin-testhost-p10/status.md)。
原 SDK/Java源码互操作与静态形状证据可复用但非本轮重跑：共同迁移不保证任意旧 owner 二进制热替换；
原 Aactual 的 Companion NoSuchFieldError 永久保留，A′source 与 Bactual 分层。允许的 Companion/static-final/
private owner getter/匿名 lambda/Metadata 使反射/classfile 全集不同；不是生产包 API 的泛豁免。
fault5 实际 fixture 内部 worker/cleanup 的 self/distinct close/Error/InterruptedException 注入未完成，
fault6 SAF/Provider blocked IO、stop-before-delete 及 runner/Binder/grant/系统 IME 真实动态仍 NOTRUN。
本阶段无 assemble/D8/签名/full/提交；旧三 APK 不冒当前切片交付。

## JUnit逐入口完整映射

`core/src/test/kotlin/CoreRegressionJUnitTest.kt` 每@Test直接调用原main完整body；不反射、不吞异常、不只查退出码。JUnit4.13.2实际GradleTest执行，XML可核每个方法。原main保留，旧host全部再跑。timeout45000；单fork、512MiB，无并行跑PartRecovery固定工作路径。下列全部core34方法，runtime1方法，总35个；数量不是覆盖等价证明，原body/latch/异常身份/清理断言保持才是依据。

| 原tests入口/参数 | JUnit方法 | 保留契约 |
|---|---|---|
| KotlinMigrationTest | kotlinJavaAbi | Kotlin/Java ABI、SAM/default/空值/listener identity |
| MinimalApkTest（公开sample.wav） | waveAndRecordingProtocol | 波形正负边界、PCM、stop/cancel |
| RecordingRaceTest | recordingRaces | 确定性录音竞态 |
| ResultFilesTest | resultFiles | 文件边界/清理 |
| NativeResponseTest | nativeResponseProtocol | 严格生产协议解析/负例 |
| ModelRepositoryTest | modelRepository | SHA/大小/path/原子发布 |
| ModelPrivateRootTest | privateModelRoot | 私有根/symlink边界 |
| PartRecoveryTest | partRecoveryProductionMutant | 真生产green/编译mutant同fixture red/重green |
| RequestRunnerTest | requestRunner | 阻塞真实退出前owner不可释放、异常身份/清理 |
| TaskCoordinatorTest | taskCoordinator | 单owner状态机 |
| AdmissionBoundaryTest | admissionOwnerBoundary | start/cancel/commit/准入 |
| ImeSessionTest | imeSessionPrivacy | 字段世代失效/敏感保护/明确提交 |
| ModelReadinessTest | modelReadiness | listener锁/ready语义/回调断言不吞 |
| ModelOperationControlTest | modelOperationControl | 操作cancel/terminal |
| ModelRepositoryCancelTest | modelCancelCleanup | IO阻塞退出/清理/latch |
| ModelRepositoryDeleteTest | modelWhitelistDeletion | 白名单、安全删除 |
| ModelManagementControllerTest | modelManagementController | 真实controller/owner/provider；llm-asr-mfe logging-enabled 三kind admitted拒绝/finalizer失败、queued abnormal Error、pending/terminal observer Error，原Error身份/owner释放；正常success/failure/cancel、exact terminal/INSPECT零事件/busy/stale/retry UUID、optional telemetry隔离与codec隐私；默认main执行，无新JUnit方法 |
| ModelAndroidHelpersTest | modelPageHelpers | 纯host页面策略（非Android runtime） |
| diagnostics.RuntimeLogCoreTest | runtimeLogCore | 持久化/隐私/worker/清理；llm-asr-mfe 序号极限组默认main执行：真实合法near-MAX noearly/early多次append、合法不可偏移history normalize为OK、普通gap/early/单次restore、capacity1/4/1000与dense-before-trim/drop、非法append及clock失败不变/全字段旧snapshot与codec冻结、真实Worker+Persistence连续重启、非法history/坏文件仍拒绝、sink Error身份/一次再入/跨界并发coalescing（numeric回落不等旧publication），无新JUnit方法；llm-asr-rpb fatal slot 恢复组（load/restore/callback/save/execute 五条 Error 路径 + R1 interleave/close-block/reject token 协议）；R2 残留同入口新增 close-fatal（阻塞真实 save 中 close 后同 fatal 身份/实际退出/无 auto retry/不计 queue-full）、reject-window（拒绝先释放自己 admission→真实 FailureSink 有界暂停→后继入 slot 真实 save→旧 frame 不清后继→第三 schedule 只 dirty→最终持久）与 teardown-hold（所有 start/首次观察在 try 内、gate 预登记；总纳秒预算 join(millis,nanos)+实际 isAlive；run-local 共享收尾/删除 guard 真实拒删 held save，主 Throwable+suppressed/保中断/宿主 noexit；独立释放确认实际退出才删）。R1/R2-a 已 CLOSED，R2-b 待 fresh B1–B5 回审；新证据 r2b-fix，旧专用清理变体仅历史演示，不冒共享拒删证明 |
| RuntimeLogStoreErrorTest | runtimeLogErrorIdentity | Error身份/publisher |
| LogExportTest | logExportGenerations | clear/export世代、阻塞owner；picker abandon(true) slot释放/零IO/同页新code重试、旧callback拒绝、destroy-deferred跨page EXPIRED，原完整main与close/Error收尾保留 |
| TextExportTest | textExport | result/ticket/worker |
| TextExportSafetyTest | textExportSafety | clear-before-admission |
| TextExportFatalTest | textExportFatalIdentity | compound fatal/close |
| ModelNotificationTest | modelNotifications | 通知/ready |
| ModelProviderBoundaryTest | providerErrorAndStreamBoundary | 异常净化/身份/stream owner |
| ModelReviewFixTest history | reviewHistory | 历史记录 |
| ModelReviewFixTest terminal | reviewTerminal | 晚到cancel不覆盖terminal |
| ModelReviewFixTest window | reviewWindow | picker窗口票据 |
| ModelReviewFixTest boundary | reviewBoundary | provider边界 |
| ModelReviewFixTest deletion | reviewDeletion | 删除细节 |
| ModelReviewFixTest liveDeletion | reviewLiveDeletion | 活动删除 |
| ModelReviewFixTest planning | reviewPlanning | 发布计划 |
| ModelReviewFixTest publication | reviewPublication | 原子发布 |
| InferenceAdapterTest（runtime） | RuntimeRegressionJUnitTest.inferenceAdapterAndJniConstruction | 注入生产adapter/异常身份/回调/owner/构造JNI不加载；真实App/IME/native source clauses，不是JNI运行 |

## 保留但不冒称JUnit/设备的gate

根 `verifyDebug` 继续连接以下旧gate，额外依赖两个JUnit task和模块边界编译负例：
- `verifyHost → scripts/test-minimal-apk.sh`：全28 main入口/8参数path、Kotlin ABI、上述原body和3真实模型production mutants（readiness-monitor/provider-cancel-trust/terminal-cancel）；fresh mktemp classes、不读历史classes。
- `activity_window_test.py`：生产ActivityWindow对可控假平台的行为及3编译mutants；其source负例与pre-M6 red保留。假平台不是仪器/设备。
- `logs_activity_picker_test.py`：真实完整LogsActivity＋日志core、测试侧platform/AppGraph/ActivityWindow fakes；默认实际按钮launch异常→真实Refresh/render失败原因稳定，slot/零IO/新code/旧callback/pause-defer-resume/destroy-recreate保护与3项可编译consumer mutants及恢复正例。经test-minimal-apk.sh有界接入verifyHost/full；fast不运行此adapter，独立targeted运行普通/-O。所有断言在外部harness，fresh混合编译与有限timeout；R1追加同runner轻量后代故障保护（TERM抵抗需KILL、父先退后代持输出、中断身份、未确认退出拒删保目录/证据、恢复清理），Linux owned session/PID-start/pidfd/subreaper有界退出reap后才删目录。默认普通/-O均执行，不用外层timeout本身冒行为红；不修改共享compiler，不承诺恶意session逃逸沙箱。不是Android runner/系统picker/SAF设备验收，详[本批状态](../reports/runtime-logs-picker/status.md)。
- `prepare_model_configs_test.py`、`model_android_source_test.py`、`text_export_source_test.py`、`architecture_boundary_test.py`、`app_report_source_test.py`、`jni_symbol_checker_test.py`、`apk_report_binding_test.py`：原构建/隐私/接线/检查器负例保留；lexical不是runtime。
- `verifyBuildInputs`：真实staging8负例；`verifyNativeInputs`：源码准备9/原生4，普通和Python -O都执行。
- `verifyApkChecker`：实际AGP包identity变异；`verifyJni`：全63混合源真实生成头/NDKobject/symbol；`verifyDebugPackage`：实际签名APK、组件权限/资产/全部DEX生产owner/JNI/native/provenance/输入指纹。
- `tests/module_boundary_test.py`：以Gradle实际module runtime/compile+boot classpath运行javac正例；core分别拒绝android/org.json/JNI桥/AppGraph，runtime接受Android/JNI/core且拒绝AppGraph。编译负例需要指明目标diagnostic，不接受无关工具/语法失败。
- `module_owners_guard_test.py`：复制本轮真实class输出，拒绝缺顶级owner、重编依赖owner、core平台字节污染和旧app Kotlin class；普通及-O执行，不能以内嵌class存在掩盖缺失顶级定义。
- `gradle_apk_identity_test.py`：实际本轮包缺Java/Kotlin owner、跨DEX重复定义与原签名/native/Manifest负例共9项。
- `tests/gradle_dependency_verification_test.sh`：隔离metadata真实AGP jar checksum篡改负例。

JUnit依赖仅test配置，JUnit/Hamcrest不入APK；版本、5jar/pom(含Hamcrest parent)SHA256固定，Maven Central HTTPS+官方SHA1 sidecar交叉，SHA256为本地真实计算；不声称官方发布SHA256/独立签名认证。传递Hamcrest1.3由Gradle锁定。offline仅缓存后验证，非hermetic。

## P1-A 分层入口（保留本页 M3 历史证据）

后续任务 `llm-asr-76d.5` 新增同源 `scripts/verify-local.sh fast|full|lint|format|graph-fast`，旧 `scripts/verify-gradle.sh` 仍为 full，所有上述原 gate 保留。fast 执行35JUnit/ABI/实际module编译边界及轻量source/入口/格式负例，不触发native/APK；Lint独立分层，full额外依赖Lint/限定格式。不新增baseline或绕过strict。外层Nix也必须使用 `apk --cache-only`，否则内层Gradle `--offline` 不会阻止宿主工具下载。具体契约与本轮未验收边界见[本地分层验证](local-verification.md)，新结果不能复用本页M3身份。

## 验证入口

```bash
# 核心独立编译/真实JUnit/实际classpath负例；不构建native或运行Android
bash scripts/nix-env.sh apk bash -c 'GRADLE_USER_HOME="$PWD/.cache/gradle-user-home" ./gradlew :core:clean :core:test --offline --rerun-tasks --dependency-verification strict --console=plain -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/35.0.0/aapt2"'
# 全量原gate + 两模块JUnit + 包/native输入绑定
bash scripts/nix-env.sh apk bash scripts/verify-gradle.sh --offline --rerun-tasks
bash scripts/nix-env.sh apk bash tests/gradle_dependency_verification_test.sh
```

所有Android仪器/真实设备/4KiB与16KiB运行/麦克风/SAF/IME runtime均not run（无授权）；不以host通过等同设备验收。本轮pass/fail日志与APK输入见执行计划及[实施报告](../reports/modules-m3/status.md)；父bbb64c7ae独立clean后strict offline rerun70action/35JUnit/全部gate及checksum通过，174current一致、APK SHA a0494d25...a3f3不变。异家族审查及父逐项处置见reports/modules-m3/{independent-review,review-disposition}.md，无本轮阻塞。保留现有core test exclude、app sourcepath保护与hard63门槛；不按review错误前提改源码。功能提交9ec98d3已实核，174current/committedSHA、4external及APK一致；父授权关闭M3成功，功能APK可供用户手动验证，但设备仍未验收。下一步仅等待父提交关闭状态文本，不关闭tc3/质量/epic，不领取下一任务。

## p9：维护 JVM 测试体 Kotlin 迁移（本轮状态另列）

19个原Java测试体真实迁至同名Kt，18进入core、InferenceAdapter进入runtime；连同前批8体，共27个迁移body。两个Kt JUnit wrapper原方法/参数/45000ms不改，实际发现仍须当批core34/runtime1 XML逐方法核，不以class库存冒执行。test-host7不在fast，本阶段未跑完整42/设备runner。core的两Java语言oracle KotlinMigration/PartRecovery保真实javac与host能力，不隐藏源码或删除gate凑清零。

共享测试fixture以最小internal集合保持同module闭包，额外11既有fault字段的18mangled访问器已精确授权；普通class+Companion/static main保持Java调用，实际反射/classfile全集不同。原PrivateRoot Kt与Next两个Java main调用者原字节保留，当前编译/执行身份按当批证据分层记录。TWR使用Java Throwable成员suppression；锁同实例、worker IO确认退出前不得删目录或释放lease。额外teardown-interrupt selector原Java精确InterruptedException/exit1单列，不推广为host失败豁免；default两suppressed/拒删门槛不变。

维护host新增独立`--jvmnext-contracts`普通/-O各5owner×green/red/restored15路由，原全部gate与预算保持：readiness锁外通知、provider取消脱敏（精确CancellationException red）、terminal迟到cancel、Worker后继owner、日志compound fatal。编译错/timeout不是red，恢复源码同SHA后必须fresh compile/run0。SDK全63编译脚本没有Werror，不冒SDK-Werror；实际AGP KotlinCompile Werror独立核。真实结果/失败与not run见[p9状态](../reports/kotlin-tests-p9/status.md)，旧本页M3身份不回写，不拿旧APK当当前p9交付。
