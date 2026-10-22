# AP5 runtime4 targeted 状态（R1/R2 修复待定点回审）

Task `llm-asr-ap5`；base `01dd907c3e1bfb539119553334f897b241f73496`。

仅 `ForegroundRecorder`、`ImeBackend`、`SafModelSource`、`OperationContext` 四owner210原Java行迁移，目标47Kotlin/16Java/63owner，不是Java0。原Java同维护caller fresh baseline63checks通过；此前fixture计数reset校准失败保历史。父已精确接受Recorder唯一普通Java可达 `Companion.capture` 附属入口，同descriptor/IOException、outer真正static保持，不声称可达API全集或反射不变。

## 原 targeted 受审版历史（旧证据保持）

四owner已迁为47Kotlin/16Java/63owner；原受审版普通/-O各五真实可编译生产mutants全部compile0→外部精准red1→fresh恢复0，原最终维护caller保持63checks。真实SDK全63源Werror/Java编译0；实际AGP隔离compile-only及owner8app/5runtime/50core、Recorder精确附属ABI/Java访问正负/alias有限fake契约通过，不冒正式APK或Android运行。runtime锁仅导入父接受的精确候选86f544db…，无主树write-locks/metadata修改。

nullable返回传播按父另授窄扩两已迁caller五个原receiver检查、sourceguard唯一wiringstring；原JavaContext旧caller与KtContext新caller同一ignored契约各68checks0，成员形状同，仅五Code变化，原pure identity helper2bb0不变。`!!`在三URI路径确提前于纯identity实参评估；其无副作用且mode常量，不称同opcode/NPE消息/stack或全反射不变。当时新增范围最多27paths，InferenceAdapterTest为该受审版noop；本次guard续修后它已变真实M，不继续声称noop。

原受审版所有原host gates已在其当时源码执行（含next default五mutants、compat四SDKnullable和JavaTWR、Logs/Window/model/lifecycle）；唯一新增runtime driver不重复已冻结普通/-O成功组，targeted副本仅引用同输入证据，不冒正式full。fast实际25actions、35JUnit全部零失败/错误/跳过；graph-fast38实际task通过精确allowlist；format及owner/graph负例通过。后续唯一正式full应使用未删任何route的维护入口。

**非所有命令通过**：旧sourceguard普通0；优化模式真实1，原APK checker提取的7个assert被-O移除后负例正确报错。父仅接受既存优化模式限制，本批不改checker/删负例/追-O绿；新runtime普通/-O行为mutants是独立有效层。fresh审须复核此有界处置。复杂度双工具采集及13工具测试0，但自动compare真实exit2/FAIL-UNKNOWN（跨后缀/新增scope），CCCC对Kt/knots对DSL零记录盲区保持；Recorder/Saf及新测试工具热点有人工映射，不以降分重构。新work封口引用complexity-disposition及原始logs。

实施中原fixture计数reset、SDK输出目录输入warning、nullablecaller真实SDK诊断、新receiver记录器继承static字段误写、镜像缺2份文本资源等失败均分域保留，不当生产行为red。原成功baseline/shape/P0/P1/receiver等不重跑追绿；修复改变Ime源和维护caller后，下节按新输入重新验证必要范围，不以旧63checks/旧五mutants冒新版本。本页不是新APK交付。

## Fresh R1/R2 修复与新验证（尚未自判 CLOSED）

Fresh同模型新上下文审查正式REQUESTCHANGES：R1发现request!!早于原filesDir副作用；R2发现inference!!早于model/cache实参副作用。父接受两P2后集中最小修复，仅ImeBackend.transcribe显式保存nullable receiver，先按Java原序求RHS/全部参数，再检查receiver调用；没有新生产helper/API/字段、lambda编号或Throws/Signature变化，capture/cleanup源码保持。真实SDK和AGP均见filesDir11→requestcheck39→requestId42，以及savedinference150→modelDir179→cacheDir213→lateinferencecheck230→invoke245。

新最终维护caller SHA42e7ad55…保原63checks方法字节并新增43checks，原四Java同caller fresh compile0/run0/**106checks**；受审Kt同caller compile0、R1/R2各精准外部AssertionError1。修后普通/-O各**七**生产mutants（原五＋两次序earlycheck）均compile0→精准red1→freshrestore0；每mode15mixedroutes/30compilercommands/15Java，4875秒预算低于原host7200，各子超时不变。SDK全63源Werror/Java0、实际隔离AGP编译/owner8-5-50/精确ABI/访问正负/aliasfake0均为新Ime输入一次；不代表formal full或设备。

本次普通host首链真实exit1：旧InferenceAdapterTest字面inline检查不接受正确savedreceiver表达。父另授窄改该测试仅Kt-Ime分支：唯一有序8局部赋值/lateinvoke检查、comment/string伪满足处理、同checker10负例；原AsrOperationKt/Java、全部构造/重复/no-orchestration/JNI/行为方法保持。host仅注释11/22/11同步15/30/15，所有可执行行/timeout7200不变。新恢复链重建canonical真实classpath编一次，完整InferenceAdapterTest **10groups/383checks**通过；然后原LogExportTest起全部suffix及fast25actions/**35JUnit零失败错误跳过**、graph38、format58files、owner/graph负例通过。原昂贵成功prefix含nextdefault/compat有原closedlog，联合suffix覆盖，不把新suffix0冒原host从头0，不重复成功七mutants/SDK。现原27候选**27changed/0noop**，本次仅7既有路径delta。

复杂度仅修复3代码文件及新guard单文件两个独立delta各同版本collect/compare0，仍是review-required-not-a-gate；Ime transcribe C6/M5不变、SLOC20→28，新增测试/guard热点有人工解释留点审。原全scopecompare2UNKNOWN、CCCC-Kt/DSL等盲区及既存guard优化1非通过限制均保；先前fresh已接受Recorder/Saf必要热点，不无证重开。静态审计首轮rawCode CP索引重排误判保失败，后仅解析已有class指令/常量核正，不重编成功源。

最新generated原件分本轮起956及恢复前956两代保护（954精确同SHA复用、2当前redledger新增原bytes），旧build/review不可变输出、19旧产物、583native inode、HEAD/index/key-stat保护逐项封口；mutable历史索引通过对应当代backup解释，不回写旧日志/旧candidate为绿。全部阻塞是否CLOSED仍待父完整消费与原reviewer定点回审；未formal full/两full摘要/新APK覆盖/提交。

`UriRef`真实publicfinalclass0x31/privatefinalholder，普通子类mutablehandle反例不可达；不改父、不用反射Unsafe。Saf测试只支持域nullref/nullhandle/wrongtype/normalUri与真实JavaTWR sameAssertionError self/distinct/close-only；旧P0/P1报告freeze不回写。

R1/R2及新增guard的同sol fork:false定点回审（非跨家族）、正式full/两摘要、安全两阶段本地提交及父核actualAPK全部后置另授。当前旧三APK只保护身份，不冒本轮产物。设备/Androidrunner/麦克风/SAF Binder/grant/系统IME/真实JNI4K16K/私人文件/模型计算/远端CI/release仍NOT RUN。
