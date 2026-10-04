# M3 监管计划

## Goal
监管用户授权的 M3（llm-asr-76d.4）：由新 Agent 完成少量 Gradle 模块边界与核心 JUnit 回归迁移，保持原业务安全契约；父会话核验实现、测试、审查和源码/APK指纹，安全本地提交后才交付。不推送、不访问设备/麦克风/私人文件，不扩展到 M4/M5/M7、Compose/协程或推理性能修改。

## Binding
- role: steward；本计划唯一 writer：m3-steward（父会话）。
- session: 01a107d7-3865-75a0-abef-30c1dddfbf36；Herdr wJ:p1。
- cwd/worktree: /home/zhb/gitrep/llm-asr；branch main。
- base: 538dcb66f16d41adeba440d934d3a0d2e8c4eb6d。
- 执行 Agent：m3-build；单产品 writer、唯一 Beads task writer，负责原子 claim 与任务执行记录。
- 执行计划：.planning/llm-asr-76d.4/；父会话不写该计划。
- 监管计划：.planning/llm-asr-76d.4-supervision/。

## Current Phase
Phase 4：父复验与独立审查处置完成，无工程阻塞；待执行writer状态文本收尾及安全本地提交。

### Phase 1: 分派核查与契约
- [x] 核任务依赖、assignee、Git 与 Herdr runtime。
- [x] 恢复历史计划及相关 Gradle memories。
- [x] 启动 m3-build，收到工具 launch acknowledgement（不是claim或实现结果）。
- **Status:** complete

### Phase 2: 实施与阻塞监管
- [x] Agent 建立实际63源归属/core-runtime-app与35JUnit完整body映射，完成实施/直接自审。
- [x] 收到自动交接后读取执行三文件、实际diff、配置/adapter/checker/报告及Beads/memory；未直接认定完成。
- **Status:** complete

### Phase 3: 父验收与只读审查
- [x] 查看实际diff/测试映射、异家族m3-review完成；父逐项处置无阻塞，原报告及disposition留证。
- [x] 父clean模块后70任务/35JUnit/全gate与checksum复验通过，174inputs/APK匹配。
- **Status:** complete

### Phase 4: 安全提交与交付
- [ ] 暂存清单/差异安全核对，本地提交成功后才报告 APK。
- [ ] 执行 Agent 按批准结果记录 Beads 和执行计划；仅满足验收时关闭 M3。
- [ ] 报告提交/APK身份及设备 not run，不自动领取下一任务。
- **Status:** in_progress

## Next Step
接收m3-build只改状态文本的短收尾，安全暂存限定文件并核174输入/index/APK，成功本地提交后批准唯一Beads writer关闭任务。

## Stop Conditions
ownership/runtime冲突、必要工具或依赖来源无法验证、安全契约无法保持、需要设备/私人数据/密钥或超范围架构决策时停止对应路径，记录证据、已尝试方案与用户下一输入；不强行关闭任务或交付旧包。
