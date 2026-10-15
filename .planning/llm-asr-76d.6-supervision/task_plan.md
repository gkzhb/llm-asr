# P1-B / M5 最小 Android 仪器工程监督计划

## Goal
在不改变生产行为与既有验证护栏的前提下，按已有P1-B范围交付可构建、可审查的测试runner/test APK及最小fake engine/provider/IME host本地工程切片。由leaf完成真实编译、断言负例及必要full/APK输入绑定，经fresh同sol审查和reviewagent限定安全本地提交后交付；设备无授权时运行明确not run，不把构建/源码检查当Android运行。

## Binding
- Beads：llm-asr-76d.6；owner/唯一Beads writer：p1-steward；role：steward。
- 父session：01a13f6d-0939-777f-8029-0148509757bb；Herdr wY:p1。
- cwd：/home/zhb/gitrep/llm-asr；main；base a73f7f339a6d3a5e4f6a7faace7cdd262aade3c4。
- 本监督目录唯一writer为父；leaf执行plan独立目录，归档P1-A与mqn研究不修改。父只协调，不亲自开发/下载/测试/构建复验。
- 用户当前要求阅读交接继续工作；沿用交接同sol开发/审查、reviewagent提交及禁止设备/远端等边界。

## Current Phase
p1b-test唯一full正式exit0/494.275s/174actions全执行，父完整消费handoff/full报告/final-audit。原35+新7JUnit全零、生产App/runtime Lint0ErrorFatal/17和10同源warnings defer；隔离Lint/checksum/fixture真实负例恢复及JNI/native/三包209build80native绑定通过。最终freeze9ce9667a...e087ab，769旧证据保护保持，迟到自动通知已补达不重跑或改冻结报告。工程验证接受，最后安全只读审及reviewagent限定commit后置；未提交不交付，M5和设备不关闭。

## Next Step
委派p1bship-review同sol/high fresh最后只读安全审：核最终full证据/209-80-三包及精确逐文件候选，排除旧20未知路径/产物/私有数据；父接受后另授同reviewer stage审计+新本地commit和提交后绑定。监督目录自此冻结至提交receipt，不再并写。

### Phase 0: 恢复/ownership/runtime
- [x] 恢复根三文件与交接、P1-A监督及mqn路线；截断按offset补齐根历史。
- [x] 核Git a73f7f3、空index/无tracked差异与原excluded边界。
- [x] 核Beads本库llm_asr/M3closed/M5open空owner/ap5deferred及Herdr无重叠child。
- [x] p1-steward合法claim M5，记录范围与唯一Beads writer。
- **Status:** complete

### Phase 1: bounded方案与准入
- [x] leaf核实际runner/布局/cache/现验证入口及依赖方案，handoff已完整消费。
- [x] 接受独立:test-host/SDK标准InstrumentationTestRunner+JUnit3/host既有JUnit4.13.2，无新第三方坐标；明确2A/2B和限定offline新模块锁prepare权限。
- **Status:** complete

### Phase 2: 单writer实施与针对性验证
- [x] 2A独立执行plan、81actions/6JUnit及两个fixture行为负例、宿主/仪器APK编译静态事实；原35JUnit/full尚未重跑，2B补工程接线。
- [x] 2A冻结/交接已消费，writer停写；锁误删恢复事故/隔离prepare和notrun明确。
- [x] 2A fresh审查B1修复及定点回审CLOSED，父接受；N1留2B。
- [x] 2B正式checker/入口/collector/格式库存与文档、针对性验证/冻结交接已消费，writer停写。
- **Status:** complete

### Phase 3: fresh审查/集中修复/完整工程验证
- [x] 同sol fork:false 2B fresh审查NO BLOCKING，父接受；N2旧状态文档随full结果收口。
- [x] leaf唯一full174actions、35+7JUnit/Lint真实负例与原新门槛/三包209-80身份绑定通过，父消费并接受工程证据，Androidnotrun。
- **Status:** complete

### Phase 4: 安全提交及交付
- [ ] reviewagent只读审核后另授限定逐文件stage/安全审计/本地commit。
- [ ] leaf提交后committed-current-APK绑定，父消费receipt与Git更新Beads/监督。
- **Status:** in_progress

## Boundaries
不改模型数学、native生命周期、录音gate、IME会话隐私、生产权限/包名/签名；不全量Kotlin迁移、不接sherpa/resident/Compose。测试不带权重/音频/私人转写；不连接或启动设备/模拟器/ADB、访问麦克风/私人文件。无远端CI/push/amend/rewrite、无stage-A/force-add、无任意依赖下载或自动锁metadata写入凑绿。现固定闭包若缺失先报告，任何新增依赖须项目skill准入。未知旧未跟踪材料原样保留、不夹提交。M4远端CI未决不关闭；M5未整体满足不关闭。所有开发校验构建交leaf；子不二级委派、不写Beads、不自行提交。

## Errors Encountered
| 错误 | 处置 |
|---|---|
| 根progress/findings读取超50KB截断 | 已offset补齐，不当完整读取 |
| bd多任务含长M4 notes合并输出超50KB | context及关键M5/依赖已独立核；不依靠截断首部 |
| ls .planning/llm-asr-76d.6* 无匹配exit2 | 确认尚无M5执行计划，创建独立监督目录；不是产品测试失败 |
| 2B targeted旧格式fixture重复mkdir reports导致FileExistsError | 父确认源码，限定追加授权tests/maintained_text_test.py单行mkdir(exist_ok=True)，不删新库存/断言，原writer续2B |

## Blocked Stop Condition
owner/runtime冲突、新依赖/下载授权不足、无法构造真实可失败断言、需生产架构/权限策略改动、无法保留验证门槛或输入产物身份无法解释时停相应分支报告。保留失败证据，三种有效不同路径仍阻塞则停止并列下一输入，不用source字符串替代运行、不更新hash凑green。
