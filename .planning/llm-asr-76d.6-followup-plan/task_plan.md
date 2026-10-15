# M5 followup 有界范围调查

## Binding
- task llm-asr-76d.6 / M5；role project-agent / leaf m5follow-plan。
- owner 与唯一 Beads writer：父 p1-steward；父 session 01a1405e-8e7e-777f-8029-014daf440e3a / wY:p1。
- 本 leaf runtime：PI_SESSION_ID 01a14061-6fd0-743d-a607-1525aa988957；Herdr wY:p1Y / wY:t1P；newapi/gpt-6.1-sol。
- cwd /home/zhb/gitrep/llm-asr；main / add5d2c854ac8d2980b2bf13d92ad5bc373ad477。
- 本目录唯一 writer m5follow-plan；只另写 .work/m5follow-plan/。不改 Beads、root、父规划、active pointer、产品/测试/构建/锁，不 stage/commit/委派。

## Goal
只读检查生产 SAF TXT/日志导出及 ModelManagement 恢复，比较 2–3 个候选，推荐一个现端口可直接构造、不需设备/新依赖/新全局装配策略的最小 M5 切片。交付 file:line、覆盖/可达性矩阵、精确允许文件、断言/有效负例和未来命令。不是开发或整个 M5 完成。

## Current Phase
Phase 2 complete：调查与方案交接完成，仅编译工程准入建议，未实施。

## Next Step
父消费 .work/m5follow-plan/handoff.md，决定是否授权SAF open→Controller失败/重试两仪器方法的无设备编译工程；若要求行为运行，先解决设备/runtime授权。

### Phase 0: 恢复与身份
- [x] AGENTS、交接、父监督三文件、根历史三文件完整恢复（截断已补读）。
- [x] Git main/add5d2c tracked/index clean；原 unknown20 与父新监督保持。
- [x] Beads readonly show M5 in_progress/p1-steward、blocks M3 closed；不更新。
- [x] 当前 leaf env + Herdr实时身份读取；父idle/本leaf working，无其他同项目writer。
- **Status:** complete

### Phase 1: 有界源码调查
- [x] 核模块/classpath/库存、生产调用与现覆盖。
- [x] 比较 SAF source、TXT/log Android export、ModelManagement 恢复三候选；只推荐SAF open真实适配链。
- **Status:** complete

### Phase 2: 推荐与交接
- [x] 单一范围、允许/禁止文件、测试断言/负例、未来验证命令与 notrun。
- [x] 写 handoff，核最终 Git，停止写入。
- **Status:** complete

## Boundaries
不测试/Gradle/编译/下载/重 hash 大产物；不设备/ADB/模拟器/安装/mic/private/模型计算；不反射 AppGraph/全局 override/硬套 Service 生命周期；不改录音gate/IME隐私/导出世代/模型数学/权限签名。需架构决定或冲突即停并提交最小决策输入。

## Errors Encountered
- 根 progress/findings 单次 read 超50KB，按 offset 补齐。
- 初次 find 猜测 android/runtime、android/core 不存在；后续只用实际目录，不重复猜测。
- 猜测 gradle/module-owners.json、core测试嵌套包路径不存在；改真实root DSL/owner checker与rg --files。
- 精准edit含一个多余不匹配项，原子拒绝无部分修改；去掉该项后两处file:line校正成功。
- ps过滤无匹配exit1是未发现相关构建/测试进程，不是产品测试失败。
