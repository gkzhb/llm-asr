# p9：19 JVM 测试体 Kotlin 迁移 — targeted 已完成，待父 fresh 审查

Task `llm-asr-ap5`；base `ee0e01553e424b8889f00840b4ea86af64348e3a`；sole writer `jvmnext-build`，唯一 Beads writer `p1-steward`。本阶段精确49路径，禁止full/提交，未交付新APK，不关闭AP5/M4/M5。

## 当前实施

19个原测试实现真实迁Kt（不是转发壳或隐藏Java），保原测试断言、生产路径与共享fixture闭包。core18新Kt＋前批8体＋原wrapper；runtime仅原wrapper＋InferenceAdapterKt canonical两path。原KotlinMigration/PartRecovery Java oracle、两JUnit wrappers、前批Kt、生产63源与依赖锁/metadata原字节保持。迁后预计物理Java34、维护27（生产16不变、历史7单列），不是全部Java迁移完成。

测试helper最小internal形状及11原fault字段18mangled访问器获父精确接受，仍需当批actualAGP形状后验；标准Companion/staticmain、synthetic/private实现附属不称classfile或反射全集等价。未知新增public业务helper/bridge仍停报。

## 已有证据与限制

- 当代3791生成原件真实bytes备份；2157strict/578native stat/6lockinode保护已核，未读复制模型/音频/key、未nativeclean。
- 原Java fresh fast：28actions、实际core34/runtime1 XML全零、actualmodule编译边界正负通过；canonical51生产＋29body混编及同caller路线冻结。
- 原Java39路线00–35/37–38 run0；额外36 `teardown-interrupt` 精确InterruptedException/exit1获父单独异常基线处置。原失败保留，不冒allgreen或mutantred。该路径返回flag/每worker动态未观测，只有源码finally与owned退出证据；default路径实际检查primary identity/两suppressed顺序/拒删/独立释放后确认清理。
- ignored Kotlin完整19闭包65Kt＋15Java混编通过，39同caller路线完成同分类；PrivateRoot原Kt实际重编并运行0。146class逐javap/19外层publicctor/staticmain/Throws捕获；两原nullable-switch点新代修正后compile0/affected2main0。
- 草稿阶段真实失败保留：private访问/泛型null/数值类型；错误外层重复lease使Part拒绝，两个reservation由父独立核真实owner退出后精确授权备份与unlink恢复，未改共享guard/Part或放宽门槛。不能把这些工具/编译失败算行为red。

## 当批维护入口真实 targeted 结果

- `fast --offline --rerun-tasks`：53.569s/exit0，27actions全部executed，实际core34/runtime1 XML精确方法集、fail/error/skip均0；wrapper45000ms/ctor/delegate保持。19实际Kt输出和127nestedclass逐javap，core仍真实编2Java oracle、runtimeJava NO-SOURCE而Kt实际编译执行；无旧Java迁移类残留。AGP KotlinCompile现Werror保持。
- SDK全部63生产混编13.276s/exit0，**现脚本没有Werror**；不写成SDK-Werror。actualmodule classpath正负随fast实际通过，production owner源码63原字节保持。
- graph-fast9.916s/exit0：38完整dry-run任务，无重型family；只是图不是测试。format9.287s/exit0，91维护文本；普通/-O实际格式/fixture/graph负例随fast入口执行。
- 完整维护host1042.141s/exit0：当前65Kt＋15Java真实混编、29body/default与Review8、原809Java ABI/14Part oracle、全部enum/ports/phase/batch/Next5/compat4/runtime7/picker3/window7/lifecycle/绑定等旧gate保留且实际执行。Next原Java fixture的两个main调用真实javac/行为通过，旧model三red-only继续在15s/5s/90s预算内用当前Ktcaller运行。
- 新5owner普通/-O各15mixedroutes（共30）逐实际compile0→精准red1→原SHA恢复freshcompile/run0，日志/工具/caller/classes身份绑定且无timeout，owned/root退出确认。provider红为合成`CancellationException: Private account alice@example.test Confidential`而非AssertionError；其余四项为预定外部AssertionError。两mode均保5000s上界、旧全部gate预算不增。
- 当前actualAGP额外selector36仍精确InterruptedException/exit1，未当host失败豁免或allgreen；返回flag/每worker该selector动态仍NOT OBSERVED。默认107checks两cleanup markers/identity/两suppressed顺序/拒删与确认退出后删除实际通过。
- 同版本同显式范围before29/after30的cccc1.8.3、knots1.18.0均exit0，同binarySHA；跨语言与Kts覆盖**UNKNOWN**，非退化0。WorkerError/Core/Management/Cancel/Ime及新driver热点逐函数人工处置与行为证据在ignored复杂度报告，待fresh独立上下文裁决，不改旧baseline。
- 最终保护：2157strict、578native真实stat、3791初始原bytes备份及后续有限增量、6lockinode/index保持；前批16095条source-after记录精确current或真实原bytes映射，无unknown；父协调supplemental单列。当前候选49、动态221build/10module/80native＋工具与supplemental分域绑定。生成保护按bytes/stat分层，不冒全fresh缓存或设备验收。

旧失败均保留：原extra36分类、draft编译/private/null/数值错误、Part重复lease与父精确人工恢复、首AGP Array不变性失败（19main只读`Array<out String?>?`保Java数组读取域/宽null/descriptor，原wrapper不改）；不能把编译/工具失败当mutantred。全部成功链不无理由重跑。

Fresh同模型上下文隔离审查（非跨家族）、formal full/42JUnit/三APK绑定及两阶段安全本地提交仍后置未授权/未执行。本阶段不提供新APK验收声明。设备/Android runner/麦克风/SAF/Binder/IME系统回调/JNI4K16K/真实模型计算/私人数据/远端CI/releaseR8均 **not run**。
