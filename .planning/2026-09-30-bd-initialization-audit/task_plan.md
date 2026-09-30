# Task Plan: 初始化项目 Beads 并核对需求进度

## Goal
在已授权项目根初始化独立 Beads 数据库，核对现有规划、Git 与实现/证据，形成不重复的真实待办与阻塞清单；不实现新产品功能或访问设备。

## Binding
- role / owner / 唯一 writer：steward / llm-asr-steward（当前会话）
- Beads ID：llm-asr-874；assignee / Beads writer=llm-asr-steward，claim 已成功。
- cwd / worktree：/home/zhb/gitrep/llm-asr
- branch / baseline：main / d5c1b64d655a8f29a7f1efc8dd3c15760bafa8b2
- runtime：Herdr wJ:p1（本轮核查身份快照，后续须重新查询，不读取或提交代理会话记录）
- plan：/home/zhb/gitrep/llm-asr/.planning/2026-09-30-bd-initialization-audit/
- 历史上下文：根 task_plan.md / findings.md / progress.md，已完整恢复（截断段已补读）。本计划独立 writer，不替代历史规划。

## Current Phase
全部审计阶段完成；本地协作检查点提交是否成功以Git历史和最终回报为准

## Phases
### Phase 1: 身份与上下文恢复
- [x] 核对根/祖先 AGENTS、干净工作区、main/HEAD 和 worktree。
- [x] 恢复根三文件，并核实 bd 帮助与 Herdr runtime。
- [x] 确认无已有 .beads、重定向或现存 Beads 数据引用，创建独立审计计划。
- **Status:** complete

### Phase 2: 项目数据库初始化与验证
- [x] 使用 llm-asr 前缀、内嵌 Dolt，跳过 instructions 与 Git hooks 生成。
- [x] 验证 context / ping / status / memories，核查新增文件。
- [x] 绑定本次审计实际 Beads ID 与唯一 writer。
- **Status:** complete

### Phase 3: 核对规划、实现与证据
- [x] 核对当前路线、APK身份与122项构建输入；继续展开生产入口及未关闭审查项。
- [x] 区分已实现、设备待验、旧计划已替代和需要用户决定的功能。
- [x] 查重后登记10条未完成需求和8条必要依赖，均未分派产品实施，候选/设备状态保留blocked。
- **Status:** complete

### Phase 4: 持久化与报告
- [x] 查重保存并读回3条长期知识；AGENTS补已存类别导航。
- [x] 最小纠正规划过期的当前阶段/下一步，保留历史记录；需求快照及任务索引已落盘。
- [x] 最终核查bd逐条任务/依赖及数据边界；llm-asr-874已显式关闭，导出11任务+3memory。
- [x] 提交范围明确为本轮非敏感协作文本，未含产品输入；提交前强制检查暂存文件/差异，失败如实报告，不影响后续未授权状态。
- **Status:** complete

## Constraints
仅授权初始化项目库与规划核查/待办整理；不新建 Agent、不领取产品任务、不改产品源码，不访问设备/麦克风/私人音频，不下载模型或联网推送，不自动创建 goal 或启用 autonomous/gated。数据库或 runtime 查询失败时停止分派并如实记录；不使用 global/force/reinit 等绕过。

## Git checkpoint
- bd init自动提交：9f843db（已逐文件确认仅初始化配置/ignore）。
- 后续审计文档/任务文本协作检查点：执行前检查index；成功身份以Git为准，不自动amend或push，失败则报告未提交。

## Decisions Made
| Decision | Rationale |
|---|---|
| 项目级内嵌 Dolt，prefix=llm-asr | 与仓库名一致，不占用全局库，无需外部 server |
| 独立规划目录并使用明确绝对路径 | 不覆盖累计24阶段的历史执行上下文 |
| 本轮只初始化/审计与记录待办 | 用户未要求开始后续功能，避免自主扩大实现范围 |

## Errors Encountered
| Error | Resolution |
|---|---|
| 初始化前 bd context 报 no .beads directory found | 属预期缺库状态；bootstrap dry-run 也确认无活动 workspace，不切库绕过 |
| 首轮 findings/progress 读取超50KB截断 | 分别从315/341行补读至末尾；已完整恢复 |
| 初步 ls 某些不存在目录返回2 | 已确认不存在，不将其当数据库故障或修改仓库 |
| rg/read个别源码路径使用旧目录名返回ENOENT | 按实际rg --files定位到native/apk、task/RequestRunner、modelmanagement等，不将工具定位失败当产品测试失败 |
| bd status汇总与list/ready逐条数量不一致 | 通过list/show确认5open/5blocked；blocked视图只含依赖阻塞4条。记录工具限制，不变更真实任务状态凑数 |

## Next Step
审计结束并向用户报告；等待用户选择 llm-asr-msj 下一批或解除 llm-asr-tc3 设备授权阻塞，不自动开展产品实施。
