# llm-asr-1lz：sherpa 引擎只读研究

## Goal
证据化评估 sherpa-onnx 的 Android ASR 接入与跨设备扩展；不实施产品变更。

## Binding
- Task: llm-asr-1lz；唯一 writer/assignee: sherpa-research；父: sherpa-steward。
- cwd: /home/zhb/gitrep/llm-asr；branch main；base f07c487bb39a202d7f70e95771fb0a3c0e31c21e。
- PI session: 01a10839-8f25-7123-9f1f-11afd82546ea；subagent 6f2027b8-b02d-4b79-a9bf-acd22890f47a；Herdr wJ:p5（已查询 working）。
- Plan: .planning/llm-asr-1lz/；不修改根历史计划、父计划或他人目录。
- 允许报告、少量文本证据、此计划和显式 task/memory。原研究阶段禁止关闭；父安全提交后显式授权仅关闭llm-asr-1lz。仍禁止产品/构建/设备/模型/推理/stage/commit/push及创建/领取PoC。

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

### Phase 6: 已提交研究验收与显式关闭
**Status:** complete
父研究提交9c753c480d9a681b9ea9ab089b3ed01102cad190；18限定纯文本、复审report SHA、62source/13base核通过；GPT6.1全审+窄复审F001/F002文稿关闭。经本次显式授权bd close llm-asr-1lz成功且show读回CLOSED，仅关闭研究，产品风险未修复/引擎未接入，性能质量全部not run。技术报告/manifest/摘录/父review冻结，最新状态见reports/sherpa-research/status.md。

## Next Step
父sherpa-steward安全提交本次Beads关闭记录及本人plan/status终态文本；不创建/领取PoC。

## Errors Encountered
- urllib整批240s/120s超时：改有限并发curl总20s限时，失败落fetch-log。
- HF API/raw连接超时：第三路径Modelscope候选Zipformer 404，模型license标未知不再尝试；作者GitHub用于SenseVoice/Qwen许可分层。
- qnn/CMakeLists 404：从树定位实际csrc/CMakeLists，不继续猜。
- Android官网主域超时：官方google.cn镜像成功。
- 一次精确edit批次因无关oldText不匹配整体未应用：去除无效条目后成功，未造成产品修改。
- 关闭核验首次试图所有18文件current=commit，父独占supervision/progress并发追加导致assert失败；未覆盖父文件。后续限定提交范围/技术证据+13base输入核验PASS。串行命令未因Python失败停掉bd close，已明确记录并补齐核验，不把首次失败称通过。

## Verification / Handoff
2026-10-04T19:16:40Z文本自检PASS：57项source SHA/bytes、13local输入与base相等、报告引用/本地链接、4个revision抽核、仅Beads tracked diff、纯文本bounded输出；git diff --check退出0。status.md完整记录not run与dirty边界。研究已落盘；task in_progress等待父独立验收，不close/commit。

2026-10-04T19:39:48Z修正自检PASS：62source SHA/bytes；S58–S62固定revision/UTC/摘录逐行；13local/base不变；report引用/链接；父两报告SHA不变；tracked product diff空且index空；F001/F002 gate有记录；纯文本小型产物。git diff --check退出0。仅源码trace未构建/设备复现，task仍in_progress待父复核。

2026-10-04T19:51:35Z终态核验PASS：完整研究commit及18纯文本范围，commit whitespace check，复审报告SHA，reports提交/current相等，62sources与13base/current，index空。Beads显式close成功/show CLOSED，最新状态文本待父另提交。
