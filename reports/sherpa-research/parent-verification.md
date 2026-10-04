# sherpa研究父证据复验

监管 sherpa-steward；base f07c487bb39a202d7f70e95771fb0a3c0e31c21e。纯研究，无产品构建/设备授权。

父后台 b7feb84d9 正式completed/exit0，执行自写文本审计 `.work/sherpa-research-parent/audit.py`，原始结果 `.work/sherpa-research-parent/audit-result.json`。

- 57项source原始文本SHA/bytes逐项与manifest匹配；13本仓库输入SHA与当前文件和基准commit相同；产品tracked diff为空，只有本任务Beads记录变化。
- 父有界HTTPS重新获取5个固定官方源码，全部与研究快照SHA相同：S02 simulate Home、S13 Qwen3-ASR model、S32 session/NNAPI、S36 offline factory/QNN、S42 logging/exit macros。没有执行下载代码。
- 父实际读取源码核：simulate每200ms创建offline stream重识别前缀；NNAPI受编译API>=27；QNN provider走条件factory并有不支持失败路径；SHERPA_ONNX_EXIT为_Exit且Android错误日志可写logcat。结论为源码能力/风险，不是设备运行结果。
- SenseVoice许可comment的pending状态只作为所抓取公开讨论的状态证据，未作法律裁定，不能据框架许可批准权重发布。
- 未推理/未模型权重、APK或音频下载/未设备/未Java-JNI-APK构建/未性能或质量测试，均not run。

异家族sherpa-review只读审查待结果；该审计不替代完整依赖闭包、模型许可证审计、所有EP和具体AAR二进制检验。研究结论尚待最终处置和安全提交，不是引擎接入交付。
