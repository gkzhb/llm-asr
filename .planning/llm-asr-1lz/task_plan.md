# llm-asr-1lz：sherpa 引擎只读研究

## Goal
证据化评估 sherpa-onnx 的 Android ASR 接入与跨设备扩展；不实施产品变更。

## Binding
- Task: llm-asr-1lz；唯一 writer/assignee: sherpa-research；父: sherpa-steward。
- cwd: /home/zhb/gitrep/llm-asr；branch main；base f07c487bb39a202d7f70e95771fb0a3c0e31c21e。
- PI session: 01a10839-8f25-7123-9f1f-11afd82546ea；subagent 6f2027b8-b02d-4b79-a9bf-acd22890f47a；Herdr wJ:p5（已查询 working）。
- Plan: .planning/llm-asr-1lz/；不修改根历史计划、父计划或他人目录。
- 允许报告、少量文本证据、此计划和显式 task/memory。禁止产品/构建/设备/模型/推理/提交/关闭。

### Phase 1: 身份与基线核查
**Status:** complete
已读 AGENTS/skills/task/memories；Git仅既有 task 登记和他人分析目录；ready 后原子 claim，assignee 已核为本人。

### Phase 2: 官方文档和固定源码调查
**Status:** complete
小型 HTTPS 文本获取，固定 revision、SHA 和时间；模型/EP/Android/取消分层核查。

### Phase 3: 当前接口只读对照及报告
**Status:** complete
形成中文决策、风险、非实施 PoC/验收建议。

### Phase 4: 证据自检与交接
**Status:** complete
核范围/链接/manifest/状态；不提交不关闭，由父审查。

### Phase 5: GPT6.1研究审查F001/F002证据与文本修正
**Status:** complete
按父接受的两项P2补固定revision源码证据；限定JNI异常包装，补Qwen终止/实际音频覆盖完整性。保留57项初稿研究与父审查历史，父supervision/gpt61-review/parent-verification只读。纯研究，不产品/构建/推理/设备/下载模型或APK/提交/关闭。

## Next Step
等待父sherpa-steward复核F001/F002修正文稿与62项证据；研究者不提交/关闭。

## Errors Encountered
- urllib整批240s/120s超时：改有限并发curl总20s限时，失败落fetch-log。
- HF API/raw连接超时：第三路径Modelscope候选Zipformer 404，模型license标未知不再尝试；作者GitHub用于SenseVoice/Qwen许可分层。
- qnn/CMakeLists 404：从树定位实际csrc/CMakeLists，不继续猜。
- Android官网主域超时：官方google.cn镜像成功。
- 一次精确edit批次因无关oldText不匹配整体未应用：去除无效条目后成功，未造成产品修改。

## Verification / Handoff
2026-10-04T19:16:40Z文本自检PASS：57项source SHA/bytes、13local输入与base相等、报告引用/本地链接、4个revision抽核、仅Beads tracked diff、纯文本bounded输出；git diff --check退出0。status.md完整记录not run与dirty边界。研究已落盘；task in_progress等待父独立验收，不close/commit。

2026-10-04T19:39:48Z修正自检PASS：62source SHA/bytes；S58–S62固定revision/UTC/摘录逐行；13local/base不变；report引用/链接；父两报告SHA不变；tracked product diff空且index空；F001/F002 gate有记录；纯文本小型产物。git diff --check退出0。仅源码trace未构建/设备复现，task仍in_progress待父复核。
