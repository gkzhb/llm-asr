# Findings — llm-asr-76d.1

## Git / Beads
- 起始 HEAD 449f01a3d711f72094ca1a14fe8b1c713fbee1ce，main 单 worktree，领先本地 origin/main 5（未 fetch/push）。相对 2fd75116ca51c478071417a6e6906221113f37b8 仅路线/协作文本7文件，无产品输入修改。
- 起始 tracked/index diff 均空；两个既有未跟踪目录 .planning/llm-asr-ap5-toolchain-analysis/ 与 .planning/llm-asr-msj-modern-android-analysis/ 是其他分析成果，保留且不提交。
- bd 1.0.3，project embedded Dolt llm_asr，project_id 6c8fcd38-9218-4430-9d2f-ea689c53b2a9，非 global/redirect/worktree。
- M0 无 blocking 前置，仅 parent-child；M0 阻塞 llm-asr-76d.2/M1 和 llm-asr-76d.7/M6，未解除。依赖无环。
- 原 llm-asr-ap5 assignee llm-asr-kotlin，in_progress，目标仍生产 Java=0；目前10 Kotlin/52 Java。不需要先全迁才能 Gradle，但这不授权并写。
- bd status显示ready3/blocked14，ready --explain列ready7；显式 task/dep/list胜计数。

## Runtime / handoff blocker
- 当前 Herdr wJ:p1 是本 M0 session 01a0f34c-beea-7015-b76f-af1b670e3a91 working。
- 原 Kotlin plan记录 session 01a0f290-8712-7015-b76f-af1852068512，未出现在当前 live agent list。只能标原 owner runtime 未确认，不能推断已退出、释放或交接。
- 同项目 wJ:p2 session 01a0f2dc-a56d-71af-a660-6ca7c8819ca4 idle，标题/独立计划是工具链分析；不是已证实的原 Kotlin owner。
- 原 Kotlin计划 Next Step仍“提交批次2”，Git已存在2fd7511；说明planning未收尾与当前实现事实有差异。不替原 owner改计划/任务，不重复提交或迁移。
- 当前未发现其他已确认的同项目 working产品 writer；仍缺原 owner 身份和继续/暂停/隔离决策，不能据此宣称后续产品写入安全。

## Knowledge / immutable boundary
- 已查并读取 modern-android-migration-boundaries 与 kotlin-d8-compatibility；当前Kotlin2.0.21为已验证回退，D8 metadata异常即使exit0也fail closed。
- 只读分析目录建议新工具链，但未实测候选；本轮不继承其建议为兼容验收，不升级 Kotlin/D8。
- 当前范围 M0审计，产品/构建实现与设备运行均未授权；旧ap5任务授权仍排除Gradle/Compose/协程。

## 身份核对完成
- 127 build/133 frozen清单与当前文件、HEAD、2fd7511 Git blobs逐项全匹配。当前APK3171318 bytes/SHA5de230d55bc81b641682c6be38a13c01fe839ff39e4045475e1df02a2b0a8e26，与报告一致。
- 578 native对象实际SHA无差异，provenance与批次1逐字一致，APK JNI DSO d5bacffc...aa416与前包相同；已绑定signature报告仍e9365bf6...93c16c，未读取密钥或重新apksigner验签。
- 本轮standalone checker通过，不等同新host/Java/JNI/DEX/APK构建。D8报告0bytes；生产10 Kotlin/52 Java。
- 完整交接审计/回退路径见baseline-audit.md。AGP/Gradle精确候选仍未选定；交接未知时停止，不下载/实施。
- claim引发 `.beads/issues.jsonl` 自动导出：检查记录语义只有M0的claim变化，memory为排序变化无内容变更；未暂存。
