# M3 监管发现

## 授权与范围
用户明确要求先优化 M3、新建 Agent 执行、父会话监管。当前为 stewardship 协调授权，不启动 goal/loop，也不自动授权设备或推送。

## 分派前事实
- M3 llm-asr-76d.4 为 OPEN，无 assignee；M2 llm-asr-76d.3 CLOSED，ready 列表包含 M3，无其他 in_progress task。
- llm-asr-ap5 DEFERRED，保留 llm-asr-kotlin assignee，不恢复语言迁移。
- Git main HEAD 538dcb66f16d41adeba440d934d3a0d2e8c4eb6d，index/产品 diff 为空；两个既有未跟踪分析目录必须保留，不纳入本轮提交。
- Herdr agent list 与 snapshot 均显示项目只有父会话 working，其他 agent 在不同项目 idle；无重叠产品 writer。
- 当前 settings 只 include(:app)，根 verifyDebug 已接 host/JNI/native/实际包门槛；M3 须扩展边界，不降低旧 gate。
- live pi --list-models 提供 newapi/gpt-6.1-sol、gpt-6-astra、MiniMax-M3、MiniMax-M3.1-Flash-Preview。实施选 gpt-6.1-sol/high 以应对模块迁移及验证接线风险；完成后优先异家族只读审查。

## 复用知识
已读 gradle-nix-verification 和 modern-android-migration-boundaries：保持 Nix patched aapt2、strict verification/locks、拒绝 DEX metadata warning、fresh classpath mutants、单DSO/原native数学/签名/组件身份。离线缓存不等于 hermetic；设备4KiB/16KiB与真实Android行为独立标 not run。

## 约束
单writer在父checkout实施；无并行writer，不必创建worktree。执行者负责 task Beads 和 .planning/llm-asr-76d.4/，父会话只写监督计划，最终负责审批/提交交付。执行者不委派，不push/merge/deploy，不自行关闭任务或最终提交；父审通过后安排提交。
