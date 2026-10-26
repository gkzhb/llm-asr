# llm-asr-zcm 提交安全阶段1 — resident-review-8

## 绑定与权限
- Task: `llm-asr-zcm`，assignee/唯一 Beads writer `p1-steward`；leaf `resident-review-8`，role: project-agent / read-only reviewer。
- 父 session: `01a16ecc-7157-76a9-8065-dbcf8e84589a`；Herdr reviewer pane `wY:p5J`。
- cwd `/home/zhb/gitrep/llm-asr`，main，base `f7d1d795f591299a3aa70194537652ad33691275`。
- 权限来自 `../llm-asr-zcm-supervision/safety-review-authorization.md`；同模型新上下文隔离，不是跨家族独立。
- 写域仅本三文件与 ignored `.work/resident-ship-review/`；无产品修改、Beads 写入、二级派遣、stage/commit、构建测试/复杂度执行、下载设备私人数据模型计算权限。

## Goal
对54维护路径、18明确目录的74个顶层Markdown及本三计划做只读字节/路径/安全/证据绑定审计，形成精确逐文件候选清单；未闭合门槛明确阻止stage2，不用旧产物、重哈希或无条件pass掩盖问题。

## Current Phase
阶段1结果落盘；**整体 NOT APPROVED / 不可提交**。原128路径结构/字节/绑定审计通过，但完整人工全文/diff阅读不可完整证明，且历史授权缺末尾LF待父处置。没有阶段2授权。

### Phase 1 — 恢复与范围
**Status:** complete
已核AGENTS/skills/父三计划/授权/Beads embedded llm_asr及显式task/主树base/index/runtime，枚举54+74路径。index起点无cached差异。父为唯一Beads writer，原writer不被抢占。

### Phase 2 — 独立只读审计
**Status:** complete
对128原候选完整字节扫描、UTF8/CR/NUL/模式/非符号链接/Git blob/JSON/Python语法/Markdown/链接/whitespace/敏感模式核查；独立重建227build/10module/82native库存；9tool、两receipt、3APK、30seal、335owned与670streams、42JUnit、Lint及mutant/corefault原始证据已核。没有执行产品脚本或重跑工程验证。

### Phase 3 — 人工审阅完整性门槛
**Status:** pending
已有大量源码/完整diff/计划/报告阅读与接受review链消费，但长read输出多次截断，不能把标准库读全文件称为完整人工阅读；覆盖不可完整证明。本轮不宣称54维护路径和全部74计划逐字人工全文/diff审完。继续只能在冻结输入下补可追踪小块人工阅读，报告保留NOT APPROVED。

### Phase 4 — 冻结与受阻交接
**Status:** complete
本三计划在生成最终清单前定稿冻结；输出域保存safety-review、exactallowlist、commit-plan、verification、handoff及哈希。清单预期131文件，42modify/89add，属于候选库存而非stage授权。父未接受缺LF/人工覆盖门槛前禁止stage2。

## Errors Encountered
| 错误 | 处置与边界 |
|---|---|
| audit初始断言要求所有文件末尾LF，遇历史授权缺LF停止 | 保留原字节，改reviewer脚本记录finding；不修冻结候选。 |
| binding脚本把`not run`误写为`NOT RUN`断言 | 按真实receipt schema修reviewer断言后通过；非工程失败。 |
| backup库存白名单漏jni-check和两个reports/apk JSON | 检查原generated manifests后只补已知生成域，三批备份逐SHA通过。 |
| route审计错断言父runtime为idle | Herdr实际父working；保留事实，独立candidate/index/status仍完全一致，不把working推断成产品写入，也不声称全员idle。 |
| 部分长read输出被截断 | 不伪造人工全文完成；列为未满足审查门槛。 |
| reviewer计划未在实质审计前建立 | 本文件仅事后真实恢复记录，不冒充先建计划；后续不以该流程缺口降低门槛。 |

## 冻结规则
本三计划定稿后不追加stage2进度；任何候选/HEAD/index/input/APK漂移均停报，不自动重写freeze。仅ignored审查产物可补受阻说明或授权后的后续证据。

## Next Step
父消费受阻handoff；确认缺末尾LF的明确处置，并在同一冻结输入下授权/接续补全可追踪人工全文与完整diff阅读，门槛闭合后才考虑另授精确stage2。
