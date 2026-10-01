# Qwen3-ASR Android（通用离线 App）

面向 **Android 10+ / ARM64、跨厂商 SoC** 的离线语音转文字项目：Android App、系统语音输入法及本地推理 API，计划由 Nix Flake 提供开发环境。

> 已完成最终补丁的 host 20/20 工程回归与手机 native CPU 单条复测；不等同于完整质量验收。当前已生成 **最小 Java/JNI 离线 debug APK**（模型导入、示例/WAV 转写），构建、签名与静态包检查通过；**用户已反馈在骁龙手机上成功加载模型并正确输出**（[手动验证记录](reports/apk/user-snapdragon-validation.md)，尚无自动采集日志/性能数据），仍非完整 IME/API 产品。产物与验证边界见 [APK 状态](reports/apk/status.md)。构建/使用方式见 [最小 APK 指南](docs/minimal-apk.md)，P0 证据与限制见 [P0 报告](reports/p0/report.md)，进展见 [工作记录](progress.md)。

## 当前方向

当前已实现App前台录音/结果管理、语音IME、独立模型管理、运行日志与R5/R6模块化；API、VAD、长音频和加载驻留尚未实现。当前`0.6-debug`的设备回归与完整P0质量仍待完成；旧版用户反馈不代替现版验收。

进度快照见[项目需求核查](docs/requirements-audit.md)，产品方向见[通用 App 路线图](docs/app-roadmap.md)；实时任务/依赖/owner以项目Beads为准：`bd status`、`bd ready`、`bd blocked`。后续批次待用户选择，暂缓天玑专属绑核与GPU/APU优化；不承诺所有SoC/内存配置已实测。

## 阅读入口

- **[现代 Android 工程技术路线](docs/modern-android-roadmap.md)**：Nix＋Gradle/AGP、原生构建、模块/测试/CI、生命周期/UI及发布的分阶段规格；Beads 总任务 `llm-asr-76d`，新 Agent 从 M0 `llm-asr-76d.1` 核验交接边界开始。M0交接、M1标准构建及M2显式原生构建已实施并验证；后续阶段未自动实施，设备仍待验收。
- **[Gradle 标准构建](docs/gradle-build.md)**：固定Wrapper/AGP/Kotlin、同签名debug打包、旧回归/JNI/包检查、依赖SHA/锁与严格离线验证；当前Kotlin迁移按用户决定暂缓。
- **[显式原生构建](docs/native-build.md)**：M2 独立源码准备、CMake OBJECT/JNI目标和AGP当前产物接线；最终全量验证/输入绑定通过，提交身份以Git为准，[M2产物与验证](reports/native-m2/status.md)，设备未通过本轮自动验收。
- **[本机 APK 复现](docs/local-apk-reproduction.md)**：从 Git 记录恢复配置、补齐 KleidiAI、精确重编 native 与新签名 APK 的验证记录。
- **[详细实施规划](docs/implementation-plan.md)**：框架选择、模型转换/量化、硬件优化、内存预算、IME/API 架构、Nix 环境设计、验收指标与里程碑。
- [研究发现](findings.md)：已核验事实、源码风险、资料访问限制。
- [历史执行规划](task_plan.md) / [工作日志](progress.md)：持久化进度与错误记录；当前任务状态查询`bd status`。
- [本地模型与配置说明](docs/local-models-and-config.md)：Git 未追踪模型的位置、下载/导出链路、配置来源、校验和迁移边界。
- [证据来源与哈希](docs/research/source-manifest.json)：本轮下载的小型文本/源码快照，不包含模型权重。

## 推荐方案

| 项目 | 规划选择 |
|---|---|
| 默认模型 | ModelScope 官方 `Qwen/Qwen3-ASR-0.6B` |
| 手机推理 | MNN C++ + NDK/JNI；先验证浮点正确性，再量化 |
| 量化起点 | decoder INT4 候选，audio encoder 保留浮点；INT8 作质量对照 |
| 硬件路径 | CPU 基线；OpenCL/Vulkan GPU 实验；不依赖 APU/NPU |
| 系统输入法 | `InputMethodService`，用户手动启用与选择 |
| API | AIDL 为主；可选且默认关闭的 loopback HTTP |
| 实时能力 | 首版 VAD 分段 final-only；部分预览和真流式分阶段实现 |
| 开发环境 | `bash scripts/nix-env.sh adb\|native\|model\|apk`；tiny mirror 避免复制大模型进入 Nix store |

## 首要技术风险

MNN 已有 Qwen3-ASR 专用导出与运行代码，但源码对照发现 **audio encoder attention-window 划分可能与官方实现不一致**。因此第一步不是直接打包模型，而是建立官方参考，对齐特征、窗口、mask、token 与最终识别结果，再做真机 CPU 原型。

已有手机 native-shell 单次实测，见 P0 报告；APK、warm、持续热稳定与量化成绩须分别验证，不能将目标当实测。完整决策、失败后的备选路线及阶段验收要求见详细规划。
