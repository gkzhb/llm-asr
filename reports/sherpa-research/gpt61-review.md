# GPT6.1 隔离上下文技术审查

审查者 sherpa-review-2；模型 newapi/gpt-6.1-sol / high；全新独立上下文，未依赖此前MiniMax审查结论。这是同家族上下文隔离审查，不称跨家族独立。base/main f07c487bb39a202d7f70e95771fb0a3c0e31c21e。审查对象为未跟踪研究报告及证据，产品tracked源码未改、index为空。

## 总体结论

“值得作为显式可选引擎做CPU PoC，但不应立即替换现Qwen3-ASR/MNN”的决策有证据支持。未发现推翻该决策的当前P0/P1阻塞。研究报告定稿前须补以下两项P2技术边界；未来实现/故障验证属于另行授权PoC，不本轮改产品或推理。

已核simulate offline前缀重跑而非真streaming；Qwen3-ASR真实offline实现但MNN资产不可直接互换；NNAPI编译API门槛与AAR未知；QNN direct SDK而非ORT QNN EP、Qwen不在QNN factory清单、SoC资产条件；CPU/profile/fallback/single owner/IME方向；SenseVoice许可标签和pending材料未作过度法律裁定；性能与文件大小/源码默认已基本分开。

## F001 — SafeJNI描述过宽，初始化及online入口仍有未包装路径（P2 confirmed / trace-backed）

报告位置：docs/sherpa-engine-evaluation.md初稿L89、L148。
固定源码revision：040afe360a38e25daaa325ce8889abf93ea02609。

- jni/offline-recognizer.cc L522–551：newFromFile Validate后直接构造recognizer，没有SafeJNI/catch；asset初始化也未包装。
- jni/online-recognizer.cc L286–315、L363–369：文件初始化和online decode未包装。
- csrc/offline-paraformer-model.cc L32–39：CPU候选初始化直接创建Ort::Session。
- jni/common.h L159–205：SafeJNI为显式调用包装，不是全局保护。

Trace：初始化ORT/C++异常→未包装native构造调用→不能保证转换成Java throwable；Validate不证明session创建一定成功。应限定“offline decode/decodeStreams有包装”，补初始化/createStream/online decode异常转换及Validate文件/asset路径差异为PoC安全gate。CPU也需处理；Java adapter catch不能修复此JNI边界。未复现设备崩溃，不称本产品已发生P1故障。

## F002 — Qwen3-ASR截断后仍返回普通文本，缺终止/音频完整性状态（P2 confirmed / trace-backed）

报告位置：初稿L71、L131、L137、L185。
同固定revision csrc/offline-recognizer-qwen3-asr-impl.cc：

- L873–937：context超限截去音频特征尾部后继续识别。
- L1028–1039：总长度达到上限break，token预算耗尽只日志提示。
- L1125–1153、L1231–1235：仍产生普通result并SetResult。
- S03 OfflineRecognizerResult L5–19没有finish reason/truncated字段。
- 本仓库native/apk/asr_jni.cpp L117–122对非正常EOS/MAX_TOKENS失败，不交付成功文本。

Trace：context或token预算超限→截断/提前停止→普通文本→仅当前公开结果字段adapter不能可靠区分完整成功。可能缺尾final进入IME确认，或处理较少内容而误判性能。须补termination/integrity capability与EOS/token/context/实际覆盖音频记录；需要native暴露状态或有证据输入限制/分段策略，不能凭tokens长度推断完整性，也不能放宽现MNN成功协议。

## 审查范围与not run

完整读报告/manifest/摘录/版本证据，直接核simulate/online状态/reset/endpoint、offline-online JNI/factory、Qwen生成循环、QNN和构建脚本、本仓库13接口。57来源SHA/bytes、13current/base、四head-release文本一致。被审初稿SHA256 82f4576a93fdace815ed1e26dc0581277ab229b0ce3bfbf74eb58d4d569cb8ca。

审查者额外仅有界获取4个固定官方小型源码文本，未执行代码，未改文件/Beads/memory。未审全部native/依赖闭包/AAR二进制，未权重/APK/音频下载、构建、推理、设备、麦克风。许可解释、实际EP、16KiB、质量/性能继续待核。

父处置：接受F001/F002，退回唯一研究writer补证据和文本，修正后父复核并视需要复审；本报告不代表引擎/设备验收。
