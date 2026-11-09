---
name: llm-asr-delivery
description: 本项目新功能或 bugfix 的 APK 版本递增、派遣开发或验证 leaf、接续未完后台、fresh 审查及安全本地提交交付时使用；约束单 writer、异步终态、行为负例、当批输入与 APK 绑定和两阶段提交，防止 agent done 冒充验收。
---

# llm-asr 证据驱动交付

## 适用与入口

从项目根 [AGENTS](../../../AGENTS.md) 恢复授权；路线问题读 [受控长期路线](../../../docs/agent-roadmap.md)，现工程命令/层级读 [本地验证契约](../../../docs/local-verification.md)。本 skill 规定项目协调流程，不复制通用 Herdr API 或宿主工具教程；实际调用服从当前宿主协议，服务不可用标 unknown 而非无人执行。

当前用户约定：**父仅协调、消费证据与监督，全部开发、下载校验、构建由获准 leaf 完成**；开发与 review 使用 `newapi/gpt-6.1-sol`，review 显式 `fork:false` 新上下文，称“同模型上下文隔离”，不能称跨家族独立。每次按 live catalog 核准确 route/可用性及任务复杂度对应 thinking；后续用户明确授权可更新约定，不能假设模型永远可用或偷偷 fallback。本 skill 不扩大设备、麦克风、私人文件、下载、架构、远端 CI 或推送权限。

## 1. 准入：先恢复事实，再划范围

核 Git/cwd/worktree/ref/current diff 与未跟踪归属；核本项目 `bd context`（embedded `llm_asr`，异常不自动 init/global/redirect）、显式目标 show、owner、验收和真正 `blocks` 依赖，再读关联 plan 三文件和 Herdr runtime。`parent-child` 不作阻塞依赖，`ready` 不作实施授权；M0 已交不默认重做。重叠 working/unknown/owner 冲突先协调，原 owner 仍在不换名抢占。

父为唯一 Beads writer，leaf 不自动 claim/改状态；各 leaf 有独占 `.planning/<task-id>-<role>/` 三文件和证据目录，一计划一 writer，不抢共享 `.active_plan`，根历史只作历史。当前窄任务在自身授权满足后实施，不等待全路线获批。先确认写域隔离，再并行只读调查；产品 writer 串行，共享 scope 审查时冻结。大构建、模型计算串行并保资源上限。

**完成条件**：任务/owner/依赖/runtime/base/允许范围均有当次证据，唯一 writer 与 plan 绑定清楚；缺项停报，不先开工后补授权。

## 2. 分派：一个 leaf，一个可核验结果

发送以下有界交接，使用 `<task-slug>-<role>` 稳定名称；leaf 默认不二级委派。宿主启动后给名称/类型/角色/准确模型/worktree 摘要，父只协调依赖和消费结果，不替 leaf 偷跑构建。

```text
role: project-agent；leaf 名称/任务 ID/合法 owner/唯一 Beads writer：
runtime/session、回报渠道、cwd/worktree/branch、Git base：
目标（一个窄结果）、允许逐文件/目录、禁区与原 excluded：
绑定 plan 三文件、独占证据与 handoff 路径：
验证命令/目标诊断/完成标准、输入/产物身份、not run 项：
是否提交：默认否；只读审禁止 stage/commit，提交须父另阶段授权：
stop：归属冲突、需新权限/依赖/scope、身份不符或安全 gate 无法维持时报父：
```

独立并行 writer 仅从已提交 base 用不同 worktree；未提交主树改动不会自动带入。单 writer 或顺序依赖 writer 可在主树工作。只读 reviewer 不领取任务、不修改受审 scope。

**完成条件**：leaf 能仅靠交接定位输入、输出和停止点；父能检查其结果而不猜“完成”含义。

## 3. 终态：回合结束不等命令结束

分清 **agent final → 命令 terminal → 工程验收 → Git commit → 设备验收**。普通 final 若仍有后台命令，只是 checkpoint：保留同一 writer，resume 原 session 接原 chain，未封口前不开 review/full/第二 writer。默认依自动完成通知，不用 poll/sleep 或反复日志读取等待。

可信成功终态到达即消费 log/exit 与后处理封口，不为等旧通知重跑昂贵成功链。仅当出现具体矛盾——例如重复等待，而 disk exit/完整 execution/log 已落且原 PID-start 身份已消失——按宿主协议做**一次 exact task registry/PID-start 恢复**，记录 task 身份、退出码、日志 SHA 与通知缺口；不能伪造通知或把异常恢复变成常态轮询。若确仍运行，继续原监督链等待真实终态。

**完成条件**：交接包含真实命令 exit/完整 log、输入产物绑定、保护域审计封口与无相关后台事实；父独立消费实际证据，不凭 agent completed 认定验收。经验依据见 [终态与工程案例](references/evidence-and-recovery.md)。

## 4. 验证：有效红、实际路径、分层结论

先定目标诊断及生产路径，行为 mutants 必须可编译且由真实生产实现拒绝；编译错误、工具失败、缺 cache/网络失败不能冒目标 red。listener 内异常可能被生产吞掉，断言放外部测试线程；worker Throwable 带出，有界 latch/join、finally 清理，不删除仍被活 IO 使用的目录，owner 在实际 worker/native/provider 完成后释放。

按当批来源区分 host/JVM、真实编译、parsed DEX/APK 静态、Android runner 与设备：方法库存/checker green 不证明 runner 发现/执行；JVM fake 不冒 Android、Binder/grant/系统生命周期或真实 native。library 静态权限声明不授运行权限。工程负例和故障案例按 [工程经验分支](references/evidence-and-recovery.md) 处理。

输入分 **current build / module / native / external tool / supplemental docs-review-evidence** 域；动态库存逐 SHA 绑定，不将历史输入数或仪器方法数写为永久门槛。说明受审 freeze 与 current 差异，别用旧产物替当前。

### APK 版本门槛（用户明确约定）

**每轮包含新功能或 bugfix 的 APK 交付，必须递增 `versionCode` 并更新 `versionName`；两者均须区别于上一交付版本。** 在冻结构建输入和正式 full 前完成版本变更，同一轮尚未交付的修复/构建重试可共用该轮新版本，不按每次编译递增。仅文档、skill 或独立模型包变化且不交付产品 APK 时，无需为此改 APK 版本。

- 从当前版本声明及最近实际交付记录确定旧版本，记录旧→新；`versionCode` 必须单调递增，`versionName` 按项目现有命名约定更新。版本来源冲突先协调，不能仅改 APK 文件名或沿用旧号掩盖产品变化。
- 同步当前有效的 Gradle/Manifest 声明、版本校验脚本和测试预期；保留既有版本一致性与包身份检查，历史已冻结报告及旧产物身份不追改。
- 从本轮实际 APK 解析并核对版本与源码、输入指纹和验证 receipt 一致，提交包含版本变更及必要测试/文档，交付报告列出新版本。若 full 后才补版本，旧 full/APK 证据不覆盖新输入，须重做受影响的构建与验证，再完成新产物绑定。

**完成条件**：旧→新版本可追溯、`versionCode` 增大、`versionName` 已更新、当批 APK 实测版本与提交源码一致；缺一则暂停 APK 交付。

### 其他验证门槛

- 产品变更先必要同源 targeted，fresh 审查与修复接受后，再由唯一验证 leaf 跑必要 full；保旧 host/JNI/native/包/签名安全 gate，不以 fast 代最终验收。
- 固定 Nix cache-only + Gradle strict/offline，缓存通过不是全新缓存/hermetic；依赖新增/准备或缺 cache 先读 [dependencies skill](../llm-asr-dependencies/SKILL.md)，报 scope，由父另授权。verify 不写锁/metadata、不自行下载补绿。
- **纯文档批**做 frontmatter/触发入口/UTF-8 LF/围栏/新增链接/全文 diff、安全路径与情境检查，并核产品输入/既有 receipt/APK 字节未变；无产品变化不无理由重建、full 或依赖 prepare。Pi runtime reload 真实加载未做则 not run，静态结构通过不冒运行加载。

**完成条件**：每项 pass/fail/not run 各有证据与边界；目标 red 有具体失败诊断、恢复 green；当批 current 输入与对应产物可解释，未测设备保留未测。

## 5. 审查：先报告，集中最小修复

冻结受审 scope，fresh reviewer 先只读给问题/严重性/位置/依据及证据边界，不能边审边改。父消费并明确接受处置后，原 writer 集中做必要最小修复与对应回归，再 fresh 定点回审到闭合。无新增证据不重复全域审，不把无界 hardening 扩成下一批功能。R1 静态发现是审查发现，不虚称实际运行 red。

**完成条件**：阻塞项有接受处置、当前修复与回审 CLOSED/无阻塞证据；审查版本与当前输入一致，或明确列出仅 supplemental 变化。

## 6. 提交：reviewagent 分两阶段，父消费后交付

1. **只读候选安全审**：逐路径 allowlist、完整内容/diff、文件类型/权限/SHA、敏感内容/产物排除、原 excluded 与保护域、当前输入/receipt/APK 绑定。审查 leaf 不自行 stage 或提交。
2. **父另授权提交阶段**：同 reviewagent 按精确 allowlist **逐文件 stage**，再核 `cached` 全部路径、完整 diff/check、blob mode/SHA 与已审当前候选完全一致。发现候选漂移先停，父确认差异/必要重验，不自动更新 freeze 凑一致；index 有未知内容先协调，不 reset 用户成果。
3. 正常新本地 commit，不 `-A`、不 force-add；不夹产物/权重/音频/私文/key/session/cache，不 push/amend/rewrite，不跳 hooks 或签名安全策略。失败保留事实，未成功不交付。
4. 产出 receipt：commit/tree/base、精确文件/模式/blob/SHA、验证证据、committed/current/frozen 输入与 APK 身份、index 空/tracked clean、原 excluded 保持。full 在提交前 dirty freeze 下运行就如实记录，不把它重标为测试过新 commit 对象。
5. 父消费 receipt 与实际 Git 后才告知可验证 APK；明确版本/路径/身份和设备 not run。纯文档仅交文档，不制造新 APK 验收。旧冻结报告 pending 保历史，当前状态走 Beads/receipt；允许的 supplemental 差异显式记录，不为事后状态文案制造新 dirty。

**完成条件**：本轮允许提交内容与受审/受测版本可追溯，无夹带与身份漂移；任务只有自身验收满足才由唯一 Beads writer 关闭，不连带关闭 M4/M5/设备或长期路线。

## 7. 停止与回报

scope、归属、许可、权限、身份或资源安全无可辩护路径即停该分支；缺设备直接 not run，不凑三次无意义尝试。诊断中三条**确实不同且有效**路径仍失败则止损，保留各自错误、证据与所需下一输入，不重复原命令/改 SHA/删 gate。

最终 handoff 给 task/owner/base/允许文件、精确候选 SHA、命令终态与测试证据、not run/失败、保护域与产品不变或变化说明、后台是否结束、提交权限/状态及唯一下一步。附 [情境核查表](references/scenarios.md) 可做文档桌面推演；未实际运行 agent 的测试不能称压力测试。

**完成条件**：接手者不用私人聊天或 session 正文，也能由 AGENTS + Beads + plan + Git + receipt 恢复并选择下一动作。
