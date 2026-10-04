# 研究监管发现

用户要求：创建新调研Agent，评估 https://k2-fsa.github.io/sherpa/onnx/android/apk-simulate-streaming-asr-cn.html 的sherpa项目可否作为当前ASR引擎，并考虑跨设备推理适配优化扩展。不是替换/实施授权。

核查：main f07c487，M3已closed，当前只有两个既有未跟踪analysis目录；Herdr项目只父working，Beads没有活动实施任务。已查backlog不存在同题研究，创建llm-asr-1lz。本研究不恢复ap5/M4/M5或性能/VAD既有任务。

当前项目：Qwen3-ASR+MNN CPU，现代工程core/runtime/app，单owner/录音gate/IME字段代际/无网络权限/模型文件SHA与安全发布须保持。P0两条公开来源衍生20case并非独立人工质量集，历史native load/PSS不是当前APK或sherpa基准。研究比较必须区分框架、具体模型和平台EP的实际启用路径。

研究路线关注CPU跨arm64基线、真流式vs模拟/分段、ORT版本/providers与Android binding、Snapdragon QNN/NNAPI/其他厂商后端的可核支持，模型format/算子/量化与fallback，ARM32/x86与16KiB仅可核证据，许可证分framework/models/APK demo。性能数据明确文档宣称/历史/目标/not measured。

模型live catalog已核；复杂架构/设备支持证据研究选newapi/gpt-6.1-sol/high。新Agent为leaf，公开网络获取有界，不执行下载的第三方代码、不外传项目上下文；写范围仅研究报告/非敏感证据manifest及自己的plan，父不并写。
