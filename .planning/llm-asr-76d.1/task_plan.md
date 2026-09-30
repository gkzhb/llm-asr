# M0 基线与交接审计 — llm-asr-76d.1

## Goal
只接手 M0：核对 Git/Beads/runtime、现有测试与产物身份，提出稳定基线与范围隔离契约。不实施 epic/M1/M6，不迁移剩余 Java，不改原 Kotlin task ownership。

## Binding
- role: project-agent；task: llm-asr-76d.1；assignee/唯一 Beads 与本计划 writer: llm-asr-m0。
- 用户本轮明确授权接手 M0；原子 claim 成功并读回。不是领取 llm-asr-76d 或 llm-asr-ap5。
- session: 01a0f34c-beea-7015-b76f-af1b670e3a91；Herdr wJ:p1 / wJ:t1。
- cwd/worktree: /home/zhb/gitrep/llm-asr；branch main；本轮起始 HEAD 449f01a3d711f72094ca1a14fe8b1c713fbee1ce。
- 固定计划路径 .planning/llm-asr-76d.1/；不写根累计计划及其他 owner 计划。
- 允许写本目录及 M0 Beads；产品源码/测试/构建配置/native/签名/现 APK 报告只读。

## Current Phase
Phase 3：用户明确暂停ap5并授权基建，本session唯一writer；M0交接与候选核查完成，进入M1。

### Phase 1: 恢复与领取
- [x] 读 AGENTS/路线/根历史及相关活动计划。
- [x] 核对 Git、Beads database/显式 task/依赖与 Herdr runtime。
- [x] 仅原子领取 M0，保留 Kotlin assignee 与状态。
- **Status:** complete

### Phase 2: 基线身份与证据
- [x] 核对现 APK、127 build/133 frozen 输入、native/签名与历史证据。
- [x] 保存回退路径、未执行验证与资源边界，见 baseline-audit.md。
- **Status:** complete

### Phase 3: 交接处置确认
- [x] 用户明确暂停Kotlin迁移，保留其assignee；沿当前449f01a产品2fd7511推进基建，本session唯一writer。
- [x] 官方AGP8.6兼容表确认API35/Gradle8.7/JDK17；候选AGP8.6.1、Gradle8.7、Kotlin2.0.21，实际编译/依赖校验属于M1。
- [x] M0决策验收满足，关闭后领取M1；不关闭原ap5。
- **Status:** complete

## Next Step
按用户新授权在独立 .planning/llm-asr-76d.2/ 执行M1标准构建，不继续ap5、不混入M2+，实际验证工具候选。

## Constraints
不委派/启动/中断其他 Agent；不联系 idle 分析会话冒充原 Kotlin owner。不访问设备/麦克风/私人文件；不下载，不 push，不重写 Git。保留两个既有未跟踪分析目录，不 stage/提交他人成果。单模型所有权、录音 gate、IME 世代、导出 epoch、路径/SHA、包名/组件/min29/target35/数学/签名关系不可变。设备 pending，不将旧测试或只读身份核对当新构建/设备验收。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 根 findings/progress 读取触发50KB截断 | 1 | 分别从315/341补读，未访问本地会话历史 |
| bd status计数与显式ready/blocked口径不一致 | 1 | 依赖以显式show/dep/list及cycles为准，不依赖汇总领取 |
| 探索旧输出路径.work/apk不存在 | 1 | 改按真实reports与build log定位，无构建操作 |

## Blocked stop condition
原 owner/runtime 无法确认、范围有重叠或需要超出 M0 授权时停止；记录已核证据、未知项、解除条件与单一下一输入，不抢占/重派/自行关闭原任务。
