# M2 显式原生构建与干净复现 — llm-asr-76d.3

## Goal
从锁定 MNN 源码、补丁、KleidiAI 与 NDK，在新构建目录生成单 DSO JNI 产物并接入 AGP，不再解析 build.ninja 或依赖旧 .work 对象；通过 host/JNI/完整 APK/包检查、provenance 和输入指纹核验，必要审查与安全本地提交后交付。原 native SHA 门槛不放宽，4KiB/16KiB 设备运行无授权则明确 not run。

## Binding
- 用户请求：继续认领后明确“开始开发”；授权实施 M2，按本计划验证、审查和安全本地提交。
- role: project-agent；task: llm-asr-76d.3；assignee/唯一源码、Beads 与 plan writer: llm-asr-m2。
- session: 01a0f602-b855-7015-b76f-af1c25ea4bfd；Herdr: wJ:p1。
- cwd/worktree: /home/zhb/gitrep/llm-asr；branch: main；起始 HEAD: 04462179a18ed87461c04221dcf213567ada551c。
- 固定 plan: /home/zhb/gitrep/llm-asr/.planning/llm-asr-76d.3/；不用共享 active_plan 或根历史三文件。
- 前置 llm-asr-76d.2 已 closed；M0 已 closed，ap5 deferred 且保留 llm-asr-kotlin，不接管语言迁移。

## Scope / Boundaries
允许范围为 M2 所需原生构建目标、来源校验/准备、AGP 任务图接线、测试、provenance 与文档；具体技术路线须在核查当前实现后决定。保留 min29/target35、包名/组件/签名关系、单 C++ runtime、CPU 数学/模型清单和权限/隐私策略。不混做 M3 模块/JUnit、M6 UI、日志修复、模型质量集或产品优化。不访问设备、麦克风、私人文件；不 push、重写历史或委派。保留他人两个分析目录，不纳入本轮提交。约 8GiB 主机，构建低并发，不与模型参考推理并行。

## Current Phase
Phase4 complete：最终验证/审查/安全提交169731b成功，161commit输入匹配，Beads已closed；用户报告本轮手动验证通过，代理设备测试仍not run。

### Phase 1: 认领、基线与现路径调查
- [x] 阅读 AGENTS、路线、M0/M1/ap5 与目标依赖、相关 memories 和 M1 planning。
- [x] 核验 Git/current diff、项目内嵌库身份与 Herdr；原子 claim 成功并读回 assignee。
- [x] 当前APK/native/归档SHA与M1基线一致，旧链接与AGP过渡路径已核；签名最终构建再实测。
- **Status:** complete

### Phase 2: 显式构建设计与实现
- [x] 首轮采用 CMake deferred hook/OBJECT target生成显式inventory及独立artifact target，待真实字节验证。
- [x] 独立离线归档校验/源码准备与编译分开，显式KleidiAI源，7项负例测试通过。
- [x] 首轮接入 AGP 显式构建/current staging与JNI/config/ELF/provenance检查，待整轮真实验证。
- [x] 完成新负例接线和自审修正；末次报告/测试增强再绑定。
- **Status:** complete

### Phase 3: 干净复现与回归
- [x] 两个新目录native重编无旧对象依赖，strip DSO及最终APK字节与M1相同。
- [x] host/负例/全源/JNI/实际APK身份、LOAD16KiB/包装/161输入及73产品核对通过。
- [x] 未发生最终DSO字节差异；574对象路径debug差异披露，不更新SHA门槛。
- **Status:** complete

### Phase 4: 审查、提交与交付
- [x] 直接源码自审及阻塞修复，最终源码/测试/产物绑定通过，无独立Agent委派。
- [x] 文档/知识/36文本暂存安全审计及169731b本地提交，161commit指纹匹配。
- [x] Beads显式close并读回，设备4KiB/16KiB各not run；收尾文本另提交，不amend。
- **Status:** complete

## Next Step
等待下一轮明确任务授权，不自动claim M3或恢复ap5；本轮用户手动验证反馈已记录，M2保持closed。

## Decisions Made
| Decision | Rationale |
|---|---|
| 接续 M2，不领取整 epic | M1 已完成，M2 直接解除 M3 的依赖且范围明确；用户只要求继续认领 |
| 本轮先完成认领与计划，不声称实现交付 | 尚未运行新测试或构建 |
| 不抢占 ap5，不改变其他任务 | 用户暂停决定及原 assignee 持续有效 |

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 根 findings/progress 单次读取超过 50KB | 1 | 分别从 315/341 行补读至末尾，未把截断当完整恢复 |
| bd status ready/blocked 计数与逐条输出口径不同 | 1 | 用 ready、list --status blocked 和显式 show 核查目标与前置，不依赖计数 |
| bbda599af native报告input_hashes引用不存在脚本 | 1 | 核实际依赖并移除虚构路径，新增清单存在性生产回归；整轮be4fde0bc重跑 |

## Blocked stop condition
owner/runtime 冲突、工具/来源不可验证、需要未授权设备/私人数据/架构决定、原生身份或安全契约无法保持时停止；在本 task 与本 plan 记录证据、尝试、blocker 和所需下一输入。不得抢占、静默降级、降低 SHA/测试门槛或用旧 APK 充当本轮证据。
