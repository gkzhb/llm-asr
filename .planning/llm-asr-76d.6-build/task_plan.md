# P1-B 2A 执行计划

## Binding
Task llm-asr-76d.6；Beads owner/唯一writer p1-steward；唯一实施leaf p1b-build（project-agent），session 01a13f7f-d6a3-746d-bfba-a1f811faf8fe / Herdr wY:p1J。cwd /home/zhb/gitrep/llm-asr，main，base a73f7f339a6d3a5e4f6a7faace7cdd262aade3c4。父session 01a13f6d-0939-777f-8029-0148509757bb。
本目录三文件唯一writer p1b-build；输出.work/p1b-build；不写Beads/其他plan，不stage/commit/push，不委派。

## Goal
实现独立:test-host最小2A模块（共享fixture、JUnit4 host、SDK JUnit3 instrumentation），仅settings和授权新文件。实际strict/offline新模块锁准备、JUnit XML与可编译green-red-green、两测试APK构建和静态包证据。不是full、不是交付APK、不是M5完成；所有Android执行not run。冻结全部新增维护输入与既有195build/80native域。

## Current Phase
Phase 5 complete：2B正式checker/入口/collector/格式/docs接线与针对性验证/209输入三包+35/7JUnit/JNI/原产品gate收据完成，旧151证据与358旧P1保持。全部写入停止，待fresh2B审查；最终full/生产Lint另验证leaf，禁prepare/提交/交付。

## Next Step
父消费.work/p1b-build/2b/handoff-2b.md、final-freeze/audit后fresh只读审2B，再另授权验证leaf full/生产Lint与最终安全提交；本leaf已停写，不提前交付。

### Phase 0: 恢复与冻结
- [x] 完整读取AGENTS/依赖skill两参考/plan handoff/父监督三文件/现Gradle入口。
- [x] Beads explicit M5 in_progress/p1-steward，依赖M3 closed；Git main/a73f7f3，tracked/index空；既有未跟踪保留。
- [x] 接缝源码、工具入口和输入基线冻结（195/80/358历史文件）。
- **Status:** complete

### Phase 1: 实现与限定prepare
- [x] 独立host/fixtures/JUnit/仪器provider及InputConnection用例源码落盘，未编译。
- [x] 真实tasks/configs，strict offline隔离prepare新模块锁证明，主树旧锁metadata当前不变；中间事故完整记录。
- **Status:** complete

### Phase 2: 真实针对性验证
- [x] JVM精确6case/0skip/error/fail XML；隔离copy两个合法可编译行为变异精确AssertionError，5阶段0/1/0/1/0。
- [x] 宿主/仪器APK assemble81actions执行；实际Manifest/全部DEX/原v3签名/无权限native资产检查exit0，11仪器方法静态exact。
- **Status:** complete

### Phase 3: 封口交接
- [x] current输入/保护域SHA与产物绑定、静态自审/状态报告、完整handoff。
- [x] 行为链terminal已消费；无Gradle/Kotlin/fixture进程；设备/2B/full/独立审查/提交未运行，不交付。
- **Status:** complete

### Phase 4: B1 narrow regression and fix
- [x] 完整消费fresh审查/父disposition/监督计划，核owner p1-steward、HEAD a73f7f3/index空/runtime唯一writer。
- [x] 新共享回归+JUnit wrapper在旧FakePorts取得真实编译成功exact AssertionError red（7case唯一reusedBackendQueuedCancellation / same request cleanup，36actions，exit1）。
- [x] 最小成功cleanup清request，host7case绿；原两个行为mutant复跑0/1/0/1/0、新7/12精确清单。
- [x] 两APK重建/实际静态包检查/新freeze及handoff-b1.md，停止全部写入等定点fresh回审。
- **Status:** complete

### Phase 5: 2B wiring and targeted verification
- [x] 完整消费父监督三文件/disposition、fresh review-2与原方案2/5节，owner p1-steward/HEAD a73f7f3/index空/runtime唯一writer核齐。
- [x] 正式三包checker/negative fixtures/collector与format库存真实temp新增变更负例。
- [x] root/local精确入口与full接线、N1隔离prepare文档，fast精确35图保持。
- [x] Python普通/-O、shell语法、dryrun/graph-fast/fast35、新android-test-build真实三包/7JUnit证据；不最终full/生产Lint。
- [x] 独立2b freeze/audit/handoff，停全部写入等fresh审查。
- **Status:** complete

## Boundaries
2A不改root build/verify-local/collector/checker/format/docs或任何生产源码Manifest、旧锁metadata、native模型工具版本签名；不下载/补缓存、设备模拟器ADB麦克风私人文件/远端CI。重型链串行bg_run，显式bash脚本/单worker，terminal后接续，无sleep/poll。缺cache、strict不符、禁区必改、owner冲突停报。三种有效不同方法同blocker无解停止。

## Errors Encountered
- 2B b6eb4f614 exit1：host Lint identity ONLY RESOLVE6consumer/66artifact171edges与JNI object及产品实际包门槛全过，但临时调用传relative apk-dir，旧checker最终receipt apk.relative_to(ROOT)异常。只修本地调用absolute路径，单独补产品receipt，不改产品checker身份条件/不重复resolve-JNI-build。
- 2B bb7c35b82 newlayer exit1/118actions执行：native/JNI/三包组装+7JUnit通过，正式checker将AGP生成宿主R类误判fixture泄漏。真实全DEX核仅ImeHostActivity/R；仅允许自身R/R$资源类，新增green+FakePorts仍拒负例，未弱放fixture/产品隔离。重验targeted+newlayer。
- 2B bacc21988 exit1：旧格式fixture加载共享checker时scripts不在sys.path，dex_class_inventory导入失败；仅check-maintained-text共享loader补固定脚本路径，再targeted-import-fixed。不是目标red/不改fixture策略。
- 2B be89b47e6 exit1：checker/输入/入口负例已过，旧maintained_text_test.py构造新增reports/status库存后再次raw.parent.mkdir()报FileExistsError；不是安全负例red。最小修为该旧测试raw.parent.mkdir(exist_ok=True)，不在本次允许文件清单，停该分支向父申请单行权限；不移除status库存降低门槛。
- B1封口独立XML复核脚本对failure.message漏预期java.lang.AssertionError前缀而exit1；原driver正确匹配type+诊断且真实行为链exit0。仅修封口核验期望为Gradle实际完整message重新只读检查，不重跑构建，不当行为red。
- 初次只读检查ls祖先AGENTS不存在、rg新目录不存在产生非0；预期缺文件，不是测试red。
- prepare be93ae3c7 exit1：init直接取configuration所有artifact导致:asr-runtime多artifactType project variant歧义（不是cache缺失/metadata失败/行为red）。resolutionResult图已解析，改成componentFilter仅外部Module artifact view核真实bytes，project仍从图记录；正常AGP任务自带artifactType选择不受影响。失败未生成锁，旧锁metadata SHA未变。一次runtime/磁盘诊断无遗留Gradle、180GiB余量。不改生产配置/不下载/不降低strict。
- 第二次prepare be4550d61解析13configs exit0、新host lock生成，但Gradle --write-locks将deactivate的root buildscript旧锁删除（日志Persisted buildscript）。立即停止验证，确认文件确实缺失后用git show base只恢复本leaf删除，SHA与before exact，index不动。旧锁/metadata全部恢复不变。该路径有安全缺陷，不再主树写锁；第三种有效方法改隔离temp项目prepare只导出并逐bytes比较新host锁，主树旧输入前后SHA检查。此事故完整披露，不能当合格prepare覆盖。
- module-build bf9384def exit1：JVM六case全passed/host APK assembled，但instrumentation javac报CancelGate不是函数接口（含reservePublish）。以匿名双方法gate替代方法引用，reservePublish明确抛AssertionError，枚举不得发布。编译失败不是行为red，不改生产接口。
- post-build bce89ed47 exit1：临时包checker写成sdkVersion而实际aapt2输出minSdkVersion，真实host Manifest为29/35无策略失败。仅修checker字段名重查；不是行为red，实际包未改。随后读尚未生成的test报告ENOENT，未当包缺失。
- 最终inline进程audit误把自身bash argv中的java字面量匹配为JVM，exit1；非真实遗留进程/测试red。改/proc/exe真实basename==java判定，仅重做只读封口审计，不重跑构建。
