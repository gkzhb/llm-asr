# Qwen3-ASR Android（通用离线 App）

面向 **Android 10+ / ARM64、跨厂商 SoC** 的离线语音转文字项目：Android App、系统语音输入法及本地推理 API，计划由 Nix Flake 提供开发环境。

> 已完成最终补丁的 host 20/20 工程回归与手机 native CPU 单条复测；不等同于完整质量验收。当前已生成 **最小 Java/JNI 离线 debug APK**（模型导入、示例/WAV 转写），构建、签名与静态包检查通过；**用户已反馈在骁龙手机上成功加载模型并正确输出**（[手动验证记录](reports/apk/user-snapdragon-validation.md)，尚无自动采集日志/性能数据），仍非完整 IME/API 产品。产物与验证边界见 [APK 状态](reports/apk/status.md)。构建/使用方式见 [最小 APK 指南](docs/minimal-apk.md)，P0 证据与限制见 [P0 报告](reports/p0/report.md)，进展见 [工作记录](progress.md)。

## 当前方向

以[通用 App 路线图](docs/app-roadmap.md)为准：先前台录音与App易用性，再模型/结果管理、生命周期/分段、IME/API。暂缓天玑专属绑核与GPU/APU优化；不承诺所有SoC/内存配置已实测。

## 阅读入口

- **[详细实施规划](docs/implementation-plan.md)**：框架选择、模型转换/量化、硬件优化、内存预算、IME/API 架构、Nix 环境设计、验收指标与里程碑。
- [研究发现](findings.md)：已核验事实、源码风险、资料访问限制。
- [任务状态](task_plan.md) / [工作日志](progress.md)：持久化进度与错误记录。
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
