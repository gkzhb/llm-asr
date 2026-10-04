# sherpa研究最终审查处置

监管sherpa-steward；base f07c487bb39a202d7f70e95771fb0a3c0e31c21e。用户要求GPT6.1技术review，最终依据为sherpa-review-2 newapi/gpt-6.1-sol/high的全新隔离上下文审查及同会话窄复审；不依赖被中断MiniMax结论，不称跨家族独立。

## 结果

GPT6.1首轮支持“显式可选CPU PoC、暂不直接替换MNN”的决策，要求研究文稿补正F001/F002。唯一研究writer已补正并新增固定源码S58–S62。

| Finding | 修正与验收 | 状态 |
|---|---|---|
| F001 JNI异常包装范围 | 报告§5.1/§6.2限定已核offline decode/decodeStreams的显式SafeJNI；初始化/createStream/online入口未包装，file/asset同处Validate差异不推广到下游；CPU也需异常转换/资源清理故障gate。S58–S61支持。 | 研究文稿关闭；产品风险待未来PoC |
| F002 Qwen终止/音频完整性 | 报告§6.3说明context裁尾、预算终止仍普通result、公共API缺finish/truncated/覆盖字段，不能凭text/tokens推完整成功；同模型比较须等覆盖/等termination，不放宽现MNN正常EOS契约。S62/S03及本仓库JNI支持。 | 研究文稿关闭；实现能力缺口未修复 |

GPT6.1窄复审completed（1m27s）：两项文稿均关闭，无重大错误或过度承诺。被复审报告SHA256 `1fd6fca5a037ce735cde4d020e1227a6037741cc3ea24e4588ab6073cb69f43a`。窄复审核新增5来源SHA/bytes，没有联网/写文件/Beads/构建/推理/设备；不重审全报告、不宣称产品或设备验收。

## 父核

父确认62来源原始SHA/bytes与manifest相同、13本仓库输入与基准/current一致、product tracked源码无diff。初稿阶段5固定官方源码曾重新HTTPS获取全部match，记录parent-verification.md。研究者和reviewer的证据范围不同，来源条目数量不等于独立网页数量或完整依赖闭包审计。

## 最终建议与边界

sherpa-onnx适合有条件的可选provider PoC：先许可清晰单模型CPU offline，再另授权true streaming，最后逐设备EP profile。模型/依赖许可、native异常/_Exit/日志隐私、结束与音频完整性、实际EP与16KiB/生命周期/质量/性能必须验证。SenseVoice许可不作法律裁定，NNAPI/QNN不能推广为所有厂商NPU支持。不实施替换、不下载权重/APK/音频、不访问设备，不自动新建或领取PoC任务。

父批准研究范围验收完成，接下来仅安全提交研究文本；研究task需提交成功后由唯一Beads writer授权关闭。质量、性能、Java/JNI/APK及设备测试均not run。本轮没有新APK。
