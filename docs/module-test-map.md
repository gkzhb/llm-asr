# M3 模块与旧测试覆盖映射

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

## JUnit逐入口完整映射

`core/src/test/java/CoreRegressionJUnitTest.java` 每@Test直接调用原main完整body；不反射、不吞异常、不只查退出码。JUnit4.13.2实际GradleTest执行，XML可核每个方法。原main保留，旧host全部再跑。timeout45000；单fork、512MiB，无并行跑PartRecovery固定工作路径。下列全部core34方法，runtime1方法，总35个；数量不是覆盖等价证明，原body/latch/异常身份/清理断言保持才是依据。

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
