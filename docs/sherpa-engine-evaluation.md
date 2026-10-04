# sherpa-onnx 作为本项目 Android ASR 引擎的证据化评估

> 研究日期：2026-10-04 UTC；任务 `llm-asr-1lz`；研究者 `sherpa-research`。本轮仅研究，不是替换、下载模型、构建 APK 或访问设备的授权。基线 `f07c487bb39a202d7f70e95771fb0a3c0e31c21e`（main，M3 core/runtime/app）。没有运行 sherpa 推理、基准、麦克风、设备或第三方代码。

## 1. 执行摘要与决策

**结论：值得做“显式可选 provider”的小规模 PoC，不适合现在直接替换 Qwen3-ASR/MNN。** sherpa-onnx 提供真实 online、offline、多模型 Android JNI 和后端扩展的实际源码路径，方向上适合短句听写/IME，也能保留离线 WAV 工作流；但“换框架”不是性能保证。最先验证应是 **arm64 CPU + 一个许可清晰的 offline 模型**，之后单独验证真正 online 的中文/中英模型；QNN/NNAPI 等属于后续有设备证据才启用的 profile，而不是通用默认开关。[S03–S13、S25–S41]

- **技术可行性：较高置信度。** 官方 Kotlin/Java/JNI、online encoder state、endpoint、模型配置、Android AAR/workflow 可定位；本项目已有同步推理注入点与共享 owner，适合增加隔离 adapter。[S03–S07、S25–S28；本仓库接口见 §6]
- **性能/质量收益：未知。** 本项目 sherpa 的 CER/WER、冷/暖加载、首 partial、final 延迟、P95、RTF、PSS/RAM、功耗/热稳定均 **not measured**。文档文件体积不是内存；demo 200ms 调度不是200ms结果保证。
- **直接产品化：有阻塞。** 具体权重再分发许可、原生 `_Exit` 路径、热词错误日志正文、无 native 硬取消保证、16KiB全依赖/运行及回退策略均要解决。GPT6.1回审另核：**CPU初始化/online调用不受全局SafeJNI保护；Qwen3-ASR context/token上限后仍可返回普通文本而没有公开终止/音频完整性状态**，均需作为PoC gate，不把普通text视作完整成功。[S15–S24、S36–S43、S46、S58–S62；§6.2–6.3]
- **SenseVoice 是技术候选而非本轮默认发布选择。** 作者 README 的模型协议、Modelscope 的 Apache 标签和官方许可讨论不一致；#334仍 open，早期肯定答复已补注“等待许可方最终确认”。不能把 MIT 源码或 sherpa Apache 许可当权重授权，也不能把旧答复当最终确认。[S15–S21]
- **暂时保留 MNN fallback，不能偷偷双引擎长期驻留。** 失败转到另一引擎之前，必须确认前一个请求/native 调用真正退出并释放资源，遵守同一个全进程 owner。具体保留期限与驻留预算待用户决定。

**不适用/不能承诺的场景：** 用户要求零改造直接读取既有 MNN 权重；要求任意 MediaTek/Exynos/Qualcomm 手机必有 NPU/GPU 加速；要求 JNI 解码随时硬中断；要求未核许可的模型立即商业再分发；要求把 simulate demo 当持续低延迟增量流式；或要求未实测就保证比当前引擎快/准/省内存。这些均不成立或证据不足。

## 2. 来源版本与边界

证据编号 Sxx 对应 [`sources-manifest.json`](../reports/sherpa-research/sources-manifest.json) 的 **完整 URL、revision、UTC访问时间、原始文本SHA256、字节数与定位行号**；必要短引用在 [`evidence-excerpts.md`](../reports/sherpa-research/evidence-excerpts.md)（保留S01–S57初稿证据）及 [`evidence-corrections.md`](../reports/sherpa-research/evidence-corrections.md)（此次追加S58–S62、F001/F002修正）。官网 live 页面不被假定等于最新代码。

| 材料 | 固定身份/意义 |
|---|---|
| sherpa-onnx 当前源码 | `040afe360a38e25daaa325ce8889abf93ea02609`；下文源码结论默认此 revision |
| latest release | `v1.13.8`，API published `2026-09-10T14:00:23Z`；tag commit `11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf` |
| 发布与当前源码对照 | arm64脚本、session、offline factory、OnlineRecognizer 四文件原始SHA一致；不是全tree或AAR二进制等价证明 |
| sherpa 文档仓库 | `c02f72ca1540163a54019e845127fa52d5de175b`；文档可能含不同年代型号/SDK示例 |
| 用户链接 | live HTML快照（S01），页面含1.13.8等多个版本与模型；不能唯一确定用户指哪一个APK |
| 本仓库 | `f07c487…`；[`local-inputs.json`](../reports/sherpa-research/local-inputs.json)为13个只读接口/配置文件SHA，无音频或权重 |

公开小型文本通过 HTTPS 获取；未上传本仓库数据。原始 HTML/API树/源码仅在 ignored `.work/sherpa-research/sources/`，不提交大tree或二进制。失败和替代路径见 [`fetch-failures.json`](../reports/sherpa-research/fetch-failures.json)。

## 3. 用户页面到底是什么：模拟流式不等于真 online

### 3.1 页面与默认 demo 模型

用户页面标题是 **“APKs for real-time/streaming speech recognition with non-streaming ASR models”**，表格明确分 VAD 与 Non-streaming ASR；并非一个独有中文模型。页面列出 Silero VAD 加 SenseVoice、离线 Paraformer、离线 Zipformer 等多种 APK，语言标签由模型决定。[S01]

固定源码 `SherpaOnnxSimulateStreamingAsr/.../screens/Home.kt` 默认 `asrModelType=15`（workflow会替换），Kotlin配置15映射到 **`sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`**，使用 `model.int8.onnx` 和 `tokens.txt`。VAD默认type0是 `silero_vad.onnx`。这证明的是该revision默认链路，不证明用户所选APK/视频恰是同版本或同模型。[S02 L65–83；S03 L470–481；S06]

### 3.2 端到端链路与体验差异

| 项目 | simulate demo实际 | 真 online候选实际/限制 |
|---|---|---|
| 音频入口 | 16kHz单声道PCM16，约100ms读取块 | `OnlineStream.acceptWaveform(FloatArray, sampleRate)`；本项目统一16k可简化边界 |
| 特征/分段 | Silero512采样窗口；语音开始向前取0.4s；阈值/最小静音及最大片段参数由VAD配置 | Zipformer在线特征/encoder缓存；`isReady`取决于model chunk；endpoint根据trailing blanks和时长规则，不等同外部VAD |
| partial来源 | 语音开始后每约200ms **新建offline stream，重识别从起点到当前offset的整个前缀** | `decode`推进stream encoder states，`getResult`轮询当前结果；库API不是自动callback，需要宿主事件桥 |
| final | VAD队列中的完整语音段再offline decode，替换最后显示 | `isEndpoint`/输入结束后排空ready结果；由宿主定义final，只允许final确认提交 |
| 算力/延迟 | 前缀越长，重复工作可能越大；200ms不是时限，更非first-partial/P95数据 | 延迟有chunk/right-context、线程、模型与endpoint静音组成；不能从“streaming”推零延迟 |
| 上下文 | 每次新建offline stream，没有连续encoder状态；分段边界会改变上下文 | transducer缓存/decoder有限上下文；reset可保留部分decoder/encoder状态，**不等于跨用户无限对话记忆** |
| 停止/尾包 | Home停止AudioRecord、发空数组；已读路径没有显式VAD.flush、补齐剩余512窗口或等待最后native结果的契约 | stream有`inputFinished()`；但不等于任意模型尾音自动正确，须按模型排空/补padding并检验短尾音 |
| 取消 | 修改UI标志/协程停止不保证同步native decode被中断 | Kotlin release是delete；未提供已核可靠“正在decode时硬取消”API |
| 时间戳 | VAD SpeechSegment.start是采样位置，不是词级对齐；模型result字段不保证每模型填充 | online有tokens/timestamps；精度/offset/子采样映射需模型验证，不默认word-grade alignment |

证据：Home L139–243、Vad参数/API、online Kotlin/API、transducer L249/L341–440。[S02–S07] 官方在线Android示例还对 **streaming Paraformer** endpoint加 **0.8s尾部静音padding** 再decode；不能照抄成所有模型的固定padding。[S48]

**产品建议（未实现）：** 明确状态 `partial(revision, segment)` 与 `final`，partial只做可撤销预览，不写目标编辑器、不落盘、不日志；用户停止时finish/排空与取消时discard是不同操作。每次新IME会话应新stream或经过确认的彻底reset，避免把上一个编辑器语音/热词状态传给下一个。逐项测试无静音尾音、刚好window边界、少于chunk、连续长语音、VAD误切与重复结果合并。原demo UI/协程管理不满足本项目现有生命周期要求。

## 4. 框架、模型与许可不能混为一谈

### 4.1 模型路线表

所有质量/延迟/RAM列的本项目状态均 **not measured**；这里“支持”是固定源码/官方文档说明，不是模型已下载、允许再分发或设备验收通过。

| 路线与具体候选 | 中文/多语、模式 | 资产/量化与接口证据 | 许可与决策边界 |
|---|---|---|---|
| **链接demo默认 SenseVoiceSmall 2024-07-17 INT8** | 中文、英文、日文、韩文、粤语；offline；simulate只是前缀重跑 | model.onnx或model.int8.onnx + tokens；文档894M/228M；Kotlin有language/ITN，输出lang/emotion/event字段 | sherpa Apache-2.0；SenseVoice代码MIT；**权重协议冲突/最终解释待确认**，不选作未经澄清的商业发布默认。[S03/S11/S15–21] |
| **streaming Zipformer bilingual zh-en 2023-02-20** | 真online中英；社区模型；官方文档说明训练涉及内部数据 | encoder/decoder/joiner+tokens；FP32约315M/14M/13M，INT8 encoder174M/joiner3.1M（可混FP32 decoder）；encoder state/endpoint实际实现 | 具体权重license **unknown**：模型卡网络不可达、替代平台无该条目；框架Apache不补授权。适合技术streaming候选，进入PoC前补清许可。[S04/S07/S08] |
| **streaming Paraformer bilingual zh-en** | 真online中英（另有中/粤/英版） | encoder/decoder+tokens，有int8与FP32；online配置和0.8s right-context尾补示例 | 不把offline Paraformer许可证自动套给另一checkpoint；具体模型卡/权重版本条款待锁定。作为Zipformer备选。[S04/S10/S48] |
| **offline Paraformer zh-2023-03-28** | 官方说明中英及部分中文方言；offline，不支持此模型timestamps | model.onnx824M或model.int8.onnx214M+tokens；release asset与上游Modelscope链接 | 对应上游Modelscope API明确Apache License2.0；**转换包及随附tokens/测试素材的NOTICE仍未检查**。在法务/资产确认后可优先CPU离线PoC。[S09/S20] |
| **offline Zipformer / Zipformer CTC** | 依具体checkpoint，页面有中文、中英；不能与同名streaming混用 | transducer通常三组件、CTC通常单模型+tokens；需要modelType对应factory | 可作为替代路线，具体checkpoint许可/质量/大小待选择；框架支持不等于每个权重满足产品需求。[S01/S36] |
| **Qwen3-ASR 0.6B** | 当前有**真实offline实现**，不是猜支持；原作者说明多语/方言 | `conv_frontend.onnx` + encoder.int8.onnx + decoder.int8.onnx + tokenizer目录；官方文档样例约42M/174M/721M；Kotlin type61、C++三ORT Session和生成循环 | 模型上游Modelscope `apache-2.0`；作者代码Apache-2.0；转换artifact/来源revision尚须锁定。**不是现有MNN config/weight直接替换**；保留同模型比较可帮助隔离框架和模型收益，但不能省略frontend/数值、**同覆盖音频与同终止语义**验收。该实现context超限可剪去音频特征尾，长度/token预算用尽后仍返回普通result；现公开结果字段不能可靠区分完整成功，见§6.3。[S03/S12/S13/S22/S23/S62] |

文件M数是文档`ls -lh`显示值（非精确字节指纹），不是打包后的大小或峰值RAM。没有实际下载模型；可确认的下载入口是官方文档中的 `k2-fsa/sherpa-onnx/releases` 的 `asr-models`（ONNX）、`asr-models-qnn`（QNN `.so`）、`asr-models-qnn-binary`（特定SoC binary）等。此类release标签集合可变；以后获批必须对所选archive与每个组件记录SHA、来源revision、license/NOTICE，而不是只记录目录名。

### 4.2 独立许可门槛

1. **sherpa-onnx framework源码 Apache-2.0 已核LICENSE**。[S14] 依赖ORT、特征库/FST等还需完整依赖NOTICE清单；本轮没有审计完整依赖闭包或所选AAR。
2. SenseVoice官方README明确代码/权重分开；MODEL_LICENSE v1.1含署名/模型名要求和其他条款。Modelscope元数据Apache标签与此不一致。#334早期肯定商业用途答复，2026-09-22被加注“非最终许可方确认”，重开follow-up和issue open均已核。**不在本报告裁决冲突；要求许可方确认所用权重/转换包及商业再分发条款**，不能引用README旧总结消除gate。[S15–S21]
3. Zipformer候选未核模型许可，不把icefall代码许可或训练语料规模当weight许可。Paraformer/Qwen的上游模型标签已核，但转换产物/素材许可必须另核。APK内demo音频/视频/测试WAV **不因开源框架自动允许再分发**，本项目PoC不直接收录它们。
4. Silero上游MIT及README“Published under…MIT”可核；但链接demo用的具体ONNX版本/哈希未核。Ten-VAD虽框架有API，本轮未选用，不能默认套Silero条款。[S06/S24]
5. QNN SDK及其厂商 `.so`/Stub/Skel另有分发条款/NOTICE（官方文档列QNN_NOTICE），不在Apache许可之下；本轮未下载SDK、未核全分发许可，作为Android EP发布前gate。[S39/S40]

## 5. Android工程与跨设备后端扩展

### 5.1 Android工程表

| 项目 | 实际证据 | 本项目影响/未知 |
|---|---|---|
| Kotlin/Java/JNI | Kotlin online/offline/VAD管理native pointer；Java有同步create/decode/release。**只有已核offline decode/decodeStreams显式调用SafeJNI**；offline/online初始化、createStream和online decode未包此wrapper。file入口在构造前调用config.Validate，asset入口在此JNI函数仅做manager/配置读取检查，未调用同样的Validate；不据此推断下游无检查。[S03–S06/S52/S58–S60] | 适配层放runtime，不将AssetManager引入纯JVM core。SafeJNI不是全局保护，Validate不保证Ort::Session构造成功；CPU Paraformer构造也可抛。Java catch不能修复未包装JNI的C++异常跨界，亦不能catch`_Exit`；初始化/创建stream/online入口须另做native异常边界gate。[S61；§6.2] |
| AAR/原生库 | 官方release AAR；android workflow打包4 ABI jniLibs。shared需`libsherpa-onnx-jni.so`+`libonnxruntime.so`；static把ORT链接进JNI。[S25/S28/S29] | 初期仅arm64-v8a产品ABI；x86_64供未来测试宿主另审。只选一种ORT链接策略，防JNI符号/c++ runtime/重复ORT冲突；不依demo复制所有ABI。 |
| SDK/Gradle | Aar源码compileSdk34/min21/Java8，版本目录AGP8.4.0/Kotlin1.7.20；本项目min29/compile35、Kotlin2.0.21和模块锁定。[S26/S27；local] | 这是源码配置，**没有本轮构建证明该依赖组合可重现**，尤其传递AndroidX约束要实际解析。不能降低本项目SDK或复制demo工程；未来走现Nix/Wrapper、校验/锁/模块边界。 |
| ORT版本 | arm64脚本默认`SHERPA_ONNX_ONNXRUNTIME_VERSION=1.28.2`，可换root/版本；cmake Android默认也1.28.2。v1.13.8抽核脚本一致。[S25/S54/S55] | 官方AAR里ORT版本/EP集合**未检查二进制**，不把脚本默认冒充每个包事实；自编要锁源码/预编库SHA、选EP与算子闭包。 |
| 包大小 | release API普通AAR **50,129,134 bytes**、static AAR **38,691,998 bytes**，只是发布资产metadata；无模型下载。[S29] | 最终APK = ABI native/ORT及算子 + Kotlin/依赖资源 + 选模型/tokenizer + 可选VAD/FST + QNN厂商库/模型上下文；不能把AAR大小当模型总大小。CPU模型可沿用用户本地导入，不新增网络权限。 |
| 16KiB | sherpa CMake Android有`-z,max-page-size=16384`；官方Android页面要求ELF段、ZIP/打包、代码页假设和16KB运行测试。[S30/S46] | **链接标志≠整个APK已16KB可用**：ORT/QNN/vendor/模型.so每个ELF须检查，APK压缩/extractNativeLibs与alignment按现策略验收；测实际page size、load/init/infer/free；不改变现组件/签名。 |
| 线程 | ORT session intra/inter均设置num_threads；VAD另有线程；demo把offline默认1改2。[S32/S06/S53] | 不自动增加线程求快：1/2/4候选profile实测CPU核、并发线程池/ORT多个Session与温度；不同机型限定资源，数值/耗时分开记录。 |
| 冷暖/释放 | recognizer初始化加载后可多次createStream，release删除；demo object长期持有；stream独立release。[S03–S05] | 可以设计有限驻留，但当前MNN每请求销毁，warm不能直接公平比较。必须用户批准idle budget/释放事件；换模型/EP、低内存、退出、请求失效后清buffer与stream。release后PSS是否回落未测。 |
| 取消 | `decode`同步；Qwen `Run(Ort::RunOptions{nullptr})`；QNN `graphExecute(...,nullptr,nullptr)`；release/delete非硬取消。[S13/S38/S47] | 只能确认未提供已核宿主硬取消接线。取消应丢弃结果并等待真实native退出，不能并发delete在用pointer。无法从Java interrupt/close推出安全可中断。 |

### 5.2 支持矩阵：ORT理论 / sherpa接线 / Android产物 / 设备验收四层

矩阵中的“需验收”**全都未在本项目执行**。GPU/NPU支持不是按厂商名字自动选择；CPU是最普适的起点而非所有arm64设备性能保证。

| 后端/设备范围 | (a) ORT或厂商理论能力 | (b) sherpa真实接线/版本条件 | (c) Android官方包或自编路径 | (d) 模型/设备与不可核边界 |
|---|---|---|---|---|
| **arm64 CPU**：Qualcomm/MediaTek/Exynos等Android | CPUExecutionProvider；FP32/适配INT8模型 | `provider=cpu`、线程配置；online/offline均有实际模型实现。[S03/S04/S32] | 官方arm64 JNI/AAR与脚本；ORT默认1.28.2，NNAPI/QNN非必要。 | 首选通用基线；INT8不保证全部算子/机型更快，磁盘与RAM收益分开；需1/2/4线程、冷/重复、质量/热稳定。 |
| **NNAPI**：有驱动的Android | ORT NNAPI要求API27+，推荐28+；算子、shape、量化scale/zero-point约束；NNAPI也可能CPU执行。[S33] | provider解析与session分支存在；**编译`__ANDROID_API__>=27`** 才append；flags为0，FP16/CPU_DISABLED在源码注释；失败可回CPU。[S31/S32] | sherpa arm64默认编译android-21，脚本提示NNAPI需android-27；**本项目min29不改变已编JNI条件**。ORT含NNAPI≠sherpa一定append；普通AAR实效unknown，建议自编锁定+探测。 | 不能默认MediaTek/Exynos NPU可用；实际驱动/算子分区、CPU回退占比/精度/收益须测；无收益或不可确认则CPU。 |
| **XNNPACK**：Android CPU优化 | ORT有XNNPACK EP；有限算子/2D MatMul等限制、独立线程池，线程争用。[S34] | StringToProvider支持；ORT_API>=12且`GetAvailableProviders`含Xnnpack才append，否则日志CPU回退；未见针对本候选的专项线程参数。[S31/S32] | 当前普通AAR是否含EP **unknown**（未检查binary），需有EP的ORT库/自编并核算子覆盖。 | 不是GPU/NPU；Transformer动态/多维算子不能泛化成全图加速；分区/数值/线程实测后决定。 |
| **QNN HTP/NPU**：适配Snapdragon SoC | ORT QNN EP有Android/Windows CPU/GPU/HTP；HTP需量化/static shape与SDK/算子支持。[S35] | **sherpa走direct QNN SDK，不是ORT QNN EP**。`SHERPA_ONNX_ENABLE_QNN=ON`、provider qnn、model.so或context.bin、backendLib/systemLib。offline factory列SenseVoice/Paraformer/Zipformer等；online列Zipformer/NeMo transducer；Qwen3-ASR不在该QNN清单。[S36–S38/S41/S51] | 官方QNN Android demo/workflow及模型配置可核；默认普通CPU构建QNN OFF。需QNN SDK root、自编QNN native及厂商HTP/Prepare/System/对应Vxx Stub+Skel和ADSP_LIBRARY_PATH；doc SDK示例2.40.0.251030。[S25/S39/S40/S49] | 预编SM8850 context是特定SoC profile，不能复制到任意Snapdragon；HTP架构/SDK/驱动、固定5/10/…秒shape、量化校准、模型算子都须匹配。CPU INT8 ONNX不能直接当HTP model.so。厂商库许可、16KiB、SSR/错误恢复是独立gate。 |
| **QNN CPU/GPU**：Qualcomm | ORT官方文档明确QNN CPU为参考实现、GPU FP32/FP16及特定量化路径；理论存在。[S35] | sherpa backendLib经dlopen，接口层不固化只能HTP；但官方QnnConfig/Android候选说明HTP，**没有本轮证据证明这些ASR导出模型在QnnCpu/QnnGpu上可用**。[S39/S41/S50] | SDK/导出/自编/打包需专项验证；不将ORT QNN GPU指南当sherpa普通Android包功能。 | CPU参考不是ORT CPU性能替代；GPU未知、不保证胜CPU；不得宣称所有Zipformer/Paraformer/Qwen均可GPU。 |
| **RKNN**：Rockchip特定板卡/Android固件 | vendor RKNN Runtime，非通用手机EP | factory与CMake条件存在`SHERPA_ONNX_ENABLE_RKNN`，转换模型及librknnrt；文档核rk3588 runtime2.2.0。[S36/S44/S51] | 有release RKNN AAR与Android脚本条件（官方路径确实存在），需匹配厂商runtime/驱动及可能vendor配置。[S25/S29] | **不是通用Android路径**；不能用于MediaTek/Exynos/Qualcomm手机。“有Android RKNN包”也不代表无需板卡固件权限；本轮不设备操作。 |
| **Ascend**：有CANN的设备 | vendor Ascend runtime/CANN | `SHERPA_ONNX_ENABLE_ASCEND_NPU`模型分支、链接ascendcl。[S36/S45/S51] | 官方说明源构建/CANN/Linux式环境；未核通用Android AAR路径。 | **非通用Android手机路径**；不能把华为手机品牌自动等同Ascend CANN NPU。 |
| 其他CPU/vendor/platform | sherpa有CoreML/CUDA/TRT/DirectML/OpenVINO/SpacemiT分支 | 条件编译/平台和available provider各受限制。[S31/S32] | Apple/Windows/Linux路线不是本Android基线；没有本轮证据证明Mali/Vulkan/任意AndroidGPU provider。 | MediaTek NeuroPilot、Samsung专用EP与所选sherpa模型接线 **unknown / 未找到支持证据**；不能因ORT可扩展就宣称已支持。 |

### 5.3 建议的设备profile与fail-closed回退（设计，不实现）

- profile身份至少包含：引擎revision、模型集合SHA/许可、ONNX/量化格式、ABI、OS/API/真实页大小、SoC/driver、ORT EP集合或direct-QNN SDK/HTP架构、线程/shape、endpoint/VAD/ITN配置。不要以`Build.MANUFACTURER`一个字符串决定NPU。
- 初始化前先核模型/库完整性与允许profile；原生库/EP枚举只是资格探测，必须实际session创建/已批准自检才能宣称可用。QNN context缓存绑定模型、SDK、SoC/driver；文件存在/同大小不足够，不能照抄demo `copyAsset`仅size跳过复制的策略。
- 区分 **请求EP / 可加载EP / 实际节点执行EP**。`StringToProvider`对未知名字回CPU、session对缺EP回CPU，本产品不可静默显示“GPU/NPU运行”。确认不了实际EP则此加速profile不发布，明确转CPU或拒绝。ORT `session.disable_cpu_ep_fallback`是理论可用选项，不代表sherpa所有API已接线，更不适用于direct-QNN；需要profile能力证明。
- 安全/integrity/许可错误 **拒绝执行**，不能以fallback绕过模型SHA。加速器不兼容/可恢复初始化失败，在不退出进程、前调用确已退出并释放后，可明确退到已核CPU模型；CPU不可用才考虑用户已选择且完整的MNN，不能自动下载/网络兜底。
- QNN等factory和宏可能`_Exit`，先把失效profile从准入排除并审计错误转换；仅Java try/catch不足以承诺“初始化失败可fallback”。架构若要隔离native进程属于另行授权，不在本轮建议偷加服务/并驻留。
- 同模型FP32CPU→CPU INT8→各EP先核**等覆盖音频与等termination**（Qwen见§6.3），再做输出/中间值可得时数值对照、CER/WER非劣比较，固定ITN/标点规则；再测性能与20–30分钟热稳定，不用单次RTF选择profile。EP可只加速子图，数据复制/编译开销可能抵消GPU收益。

## 6. 与当前M3接口的契合、改造量与阻塞

以下均读取实际源码，并由local-inputs SHA绑定，不以旧路线文档代替当前实现。

| 本仓库真实位置 | 当前契约 | 接入建议/成本（未实现） |
|---|---|---|
| `asr/NativeTranscription.java:10–12` | `invokeWithListener(config,wav,language,cache,listener)`同步返回byte[]；无PCM块/partial | **offline较小适配**：新增显式provider adapter，读已验证WAV→float PCM→offline stream，phase与错误语义保留。真streaming不能伪装进此final-only接口。 |
| `asr/InferenceAdapter.java:27–58` | readiness→load library→调用→NativeResponse严格解析；callback仅phase；解析错误不含原payload | 复用准入/日志；实际模型加载/返回边界才发phase。不能将Kotlin初始化失败/结果正文/路径任意写error。 |
| `asr/NativeResponse.kt:10–74` | `load_s inference_s generated_tokens\nraw`，UTF8严格/非负finite；tokens≤128；AsrText按Qwen输出清理 | 不捏造sherpa token count或截断到128来凑协议。最小新增**typed final result/engine-specific metrics及termination/audio-integrity capability**，MNN保留旧strict parser不放宽。sherpa普通text不证明正常EOS/完整音频覆盖；当前公共结果无finish/truncated，不能仅靠adapter或tokens长度识别，需native状态或证据化输入限制/分段策略（§6.3）。language/timestamps为可选能力，模型特定清理在adapter。 |
| `AppGraph.java:44–91` | process singleton，共享TaskCoordinator、InferenceAdapter供App/IME；硬接qwen_asr_jni | 显式provider选择与capability，不另建第二协调器。库load/模型切换走同owner。core保持Android-free，runtime承接JNI。 |
| `task/TaskCoordinator.kt:47–83` | admitted先行；worker finally/abandoned后才release；同步withOwnership也独占 | **硬不变量**：取消/界面离开不提前release；native仍运行不启动别的引擎、不删在用模型、不close正在decode的pointer。 |
| `audio/ForegroundRecorder.java:12–53`、`RecordingControl.kt` | 非阻塞AudioRecord读取，worker finally硬件release；cancel赢则丢弃；tryCommitInference要求已release且started | offline可完全保留。真online需要**明确批准新录音事务语义**：录音时内存partial与最终commit/discard，不绕过现“capture释放→推理commit”gate；不得顺手破坏cancel获胜。 |
| `ime/ImeController.java:31–140` | 敏感字段不begin；editor/session/revision活性检查；invalidate清预览/取消录音；commit只一次且busy时不可写 | partial按request/editor/revision校验、失效立即丢弃；不可自动把partial commitText到当前新编辑器；final显式确认、不因连接不确定自动重试。 |
| `model/ModelRepository.java`、AppGraph parser | 固定manifest、SHA/bytes、`.part`→原子rename、禁止symlink/path逃逸、single owner管理 | 扩展成显式model/EP bundle仍须保留全校验，tokens/VAD/tokenizer/FST/context一起绑定；多个文件现为**逐文件原子**，不是自动整个bundle原子发布，激活时须完整集合verify与版本选择。QNN model.so可执行代码不得像普通任意权重无审查导入；来源/ABI/签名策略需专项授权。 |
| `native/apk/asr_jni.cpp:88–129` | mutex+每次create/load/release MNN、CPU2线程、正常EOS且≤128，否则失败；无warm/hard cancel | 若比较性能先按等生命周期、**同实际音频覆盖与终止条件**比较，再单独量化warm收益；MNN非正常结束/MAX_TOKENS会失败，不能让sherpa截尾普通text算成功来缩短耗时（§6.3）。只允许一个激活resident engine，切换后真实销毁；不能留MNN+sherpa常驻来美化warm延迟。 |
| core/runtime/app Gradle + Manifest | core JVM-only，runtime JNI adapter边界；min29/target35，无INTERNET；包名、IME权限、签名/组件策略 | 不抄sherpa demo activity/服务/manifest；依赖锁、验证元数据、JNI输入指纹/native/apk检查须扩充而不降低。保持无网络、backup关闭和原升级身份。 |

### 6.1 真streaming的最小抽象，不提前造通用架构

建议将 **final-only engine capability** 与 **stream session capability** 分开，必要概念限于：固定profile/模型身份；begin；有界PCM输入；partial事件（sequence+request/editor/segment）；finish/最终结果；cancel/discard；**调用实际退出的termination acknowledgement**；close-after-quiescence。还须独立于“调用退出”声明**结果termination/audio-integrity capability**：正常EOS或模型等价完成、token/context limit、截断/未知、实际覆盖音频区间。当前sherpa Qwen公共API没有这些状态，不能以普通result补造成功（§6.3）。timestamps/language为可选能力，warm residency按策略声明。核心只持纯类型/接口，Android/JNI及厂商资源在runtime；旧MNN final-only路径不受partial接口强迫。

这不是实现规格已经批准。录音+推理重叠会改变现事务边界、线程/backpressure/取消、IME预览与内存预算，改造成本明显高于offline adapter；需要用户单独授权。

### 6.2 必须先处理的安全/生命周期风险

- **原生进程退出：** sherpa宏 `SHERPA_ONNX_EXIT`用 `_Exit(code)`；不支持QNN模型/未编译支持及部分QNN失败走该路径。即使显式SafeJNI调用也不能catch进程终止，不能宣称自动安全fallback。[S36/S42/S43]
- **F001：JNI异常包装不是全局保护，CPU也有gate。** 固定源码offline `newFromFile` L522–551在Validate后直接new，asset L480–519也直接new且没有同处Validate；offline createStream L582–590未包SafeJNI。只有已核offline decode/decodeStreams L620–655显式调用wrapper。online file L286–315、asset L245–283、decode L363–369及createStream L389–402同样未包装。`common.h` L159–205的SafeJNI只保护显式传入的lambda；CPU候选Paraformer L32–39、asset模型内存session L75–78直接构造Ort::Session，可抛C++异常。因此“配置Validate通过→初始化异常必转Java throwable”不成立，Java adapter catch无法兜住未转换的native异常。PoC必须审计初始化、createStream、online解码等所用入口的native异常转换、资源清理与file/asset校验差异，再用获批坏模型/不兼容ORT/EP条件故障测试验证，不能只测试包装过的offline decode。本轮仅源码trace，**未设备复现崩溃**。[S58–S61]
- **敏感日志：** Android LOGE同时stderr/logcat，transducer热词编码失败日志包含hotwords本身；`debug=false`不关闭这种错误日志。请求正文/热词/URI/模型私有路径不进入持久日志；未来禁用热词还需审计全部选路径/ORT debug/profiling，不仅封装层日志。[S07/S42]
- **释放与取消：** 不在另一线程release native pointer来伪造硬取消；在线小chunk支持在调用之间协作取消，但最长一次decode时延仍须实测。offline长decode只可先失效结果，owner占位到真实退出。若用户要求严格取消期限，未达到前不得选此实现。
- **模型与profile发布：** SHA检查覆盖所有依赖且完整bundle成功才激活；只核size或文件存在会放行不匹配context/模型库。缓存与本地导入不能绕过来源、许可或签名包组件约束。

### 6.3 F002：Qwen3-ASR普通result不等于完整成功

固定revision `040afe…` 的 `offline-recognizer-qwen3-asr-impl.cc` 已核以下路径（S62）：

1. **输入覆盖缩减：** L873–937当`context_len > max_seq_len`时按prompt占用计算`keep_audio`；超限路径可能返回空result，也可能将audio placeholders和`audio_token_len`减至keep_audio，并调用`TruncateAudioFeatures`。该函数L211–257只复制特征tensor前keep_frames，剪去尾部，不是处理完整原音频后仅压缩显示文本。prompt/热词、导出decoder context上限和运行配置都会影响余量，不能凭“音频≤30s”或一个固定token数字推安全。
2. **输出提前停止：** L1028–1039总长度上限触发break，接近max_new_tokens末轮仅记录truncated提示，未转换为失败；后续正常EOS也以break结束（L1078–1086），并可能还有其他提前停止路径。日志不是结构化成功证明，更不能凭`result.tokens.size < max_new_tokens`推出EOS或未截断。
3. **仍是普通结果：** L1125–1153解码text/tokens并return result，L1231–1235直接SetResult；S03 `OfflineRecognizerResult`没有finish reason、truncated或实际音频覆盖字段。**当前公共API返回text—even非空—不足以让调用者可靠区分完整成功、context剪尾和非EOS预算结束**。这不是已在本产品/设备复现的故障，而是静态路径与接口能力缺口。
4. **与现有成功契约不同：** 本项目`native/apk/asr_jni.cpp:115–122`要求`NORMAL_FINISHED`，对MAX_TOKENS或其他异常终止失败，不把部分文本交付为成功；不能因引入sherpa而放宽这一门槛。

**最小能力建议（未实现/待批准）：** 将“native调用已退出”与“转写完整成功”拆开；结果需有正常EOS/模型等价结束、token-limit、context-limit、input-truncated、其他停止/unknown以及实际覆盖音频区间/有效帧等可核状态。优先由native/JNI暴露真实状态；或者为固定模型/导出/配置给出**经过证据验证的输入长度、prompt/context与输出预算限制及完整分段覆盖策略**。不承诺单靠现有公共API可辨，也不把增加max_new_tokens或过滤日志当修复。如果仍不能证明完整覆盖/正常终止，应拒绝标作完整final、拒绝把缺尾文本送入IME成功预览/确认；需要降级展示部分结果时须另获产品授权。

**PoC/比较要求：** 单独记录EOS、token/context limit、输入采样总数/实际覆盖、分段边界与重叠合并、未处理/未知范围、预算及prompt占用；测试恰好/超context、输出预算耗尽、无声EOS、尾音/长句和大prompt条件。日志/报告只保留非敏感状态/计数。质量与性能统计必须对**等输入且等实际音频覆盖、等终止成功条件**做比较；剪掉尾部或提前break少算的耗时不能算同模型加速收益，unknown/截断要计失败或独立分组，不得混入成功P95/RTF。[S03/S62；本repo JNI]

## 7. 非实施PoC路线、设备矩阵与验收授权

### 7.1 推荐最小阶段

| 阶段 | 内容/决策条件 | 明确非目标 |
|---|---|---|
| **P0 研究审查/授权** | 父审查此证据；用户确认目标是质量、延迟还是资源；核一个模型license+转换bundle；批准模型/设备/独立人工golden使用及预算 | 不自动创建实施任务、不下载/跑demo |
| **P1 CPU单模型offline可选provider** | 优先许可核清的Paraformer INT8（或用户选定其他offline）；沿现WAV事务、single owner、final预览；测CPU初始化/createStream/解码native异常边界、phase、模型SHA、完整音频覆盖/termination与独立质量；若选Qwen必须解决§6.3状态缺口；保留MNN显示选择/fallback | 不加VAD simulate streaming、不混NNAPI/QNN、不双长期驻留、不把模型差异归功ORT |
| **P2 真streaming CPU单模型** | 获准后选已核license Zipformer/streaming Paraformer；引入最小stream接口、录音commit/discard、尾包/endpoint/partial revision；与同模型offline/批处理基准对比 | 不把200ms重跑prefix称作在线；不让partial自动写IME；不降低现MNN parser |
| **P3 跨设备加速profile** | 先固定CPU对照，再每设备一个EP：NNAPI/XNNPACK可用性/分区；QNN HTP另资产/SDK/SoC/profile；数值→质量→性能→热稳→回退逐层验收 | 不按SoC品牌自动打开；未测不发布加速标签；QNN GPU不预承诺 |
| **P4 是否默认/替换** | 独立quality、生命周期/隐私、跨设备性能全通过且用户认可后再决定；没有数据就保留MNN，若成本/收益差则停止 | 不本轮迁移或删除现fallback |

预计投入仅供用户批准的初步预算：CPU offline PoC 3–5工程工作日；真streaming事务/IME验证再5–10日；每个厂商EP先预留5–10日（不含SDK许可/设备获取/标注等待）。不是承诺工期，也不授权恢复M4/M5或性能产品批次。

### 7.2 建议设备矩阵（需单独设备授权）

| 样本 | 最小验证路线 | 目的 |
|---|---|---|
| 4–6GB低/中端arm64，Android10/12（Qualcomm或MediaTek） | CPU1/2线程，INT8/FP32资源受限对照 | 内存/LMKD、冷启动、无加速兜底；RAM标称不是可用内存保证 |
| Qualcomm中高端一个较旧HTP架构 + 一个较新SoC | 同CPU基线，再各自QNN SDK/Stub/Skel/context；按型号采profile | 防只在单旗舰成功；核导出shape、厂商驱动、错误/SSR与license |
| MediaTek近代arm64设备 | CPU，获批才NNAPI探测/实际分区 | 不假定NeuroPilot直连可用；跨厂商通用基线 |
| Samsung Exynos设备 | CPU，获批才NNAPI | 防把ORT NNAPI接口当厂商NPU保票 |
| Android15+ **实际16KiB** arm64设备/获批模拟环境 | CPU/native全依赖load→推理→释放；对应QNN若有支持再加测 | 与4KiB对照；ELF/ZIP检查不能替代运行 |
| 可选Rockchip板卡 | 仅用户确有此目标才RKNN | 与普通手机产品分离profile/固件与runtime，不增加无需求成本 |

### 7.3 验收记录与候选门槛（全部待批准，非已实现/已通过）

每条记录绑定APK源码/构建输入/签名身份、模型组件SHA、profile与真实device/OS/driver/page size、测试输入授权及golden版本。不得记录私人转写正文到runtime日志；质量报告用用户批准的非敏感集并控制分发。

| 维度 | 记录/用例 | 建议决策预算或门槛 |
|---|---|---|
| 质量独立 | 至少100条/≥30分钟人工独立有许可golden，覆盖中文短句/专名/数字、中英混说、静音噪声/口音；CER中文、WER英文并给归一化与人工golden规则、分组置信区间 | 相比选定基线CER绝对退化≤1百分点作为**讨论起点**，关键组不得被平均掩盖；未批准前不作acceptance承诺 |
| 冷/重复 | force-stop后的首次加载与缓存冷暖分开；2/5/10/30秒输入、至少5冷/30重复；P50/P95及失败数；warm与每请求释放单列 | offline目标冷模型ready≤3s、重复ready≤0.5s、5–10秒final音频结束后P95≤1.5s，仅候选预算 |
| online低延迟 | 从首语音采样到first nonempty partial；endpoint静音与final计算单列；尾包/空结果/partial churn/无声幻觉 | 首partial P95≤500ms、停止后final P95≤1s作为待批准目标；chunk若天然超过目标就换模型/目标而非伪称通过 |
| 吞吐/热稳定 | compute RTF与含音频等待/加载总RTF分开；20–30分钟热测、温度/频率/电量/后台压力、P95随时间 | warm计算RTF≤0.7、热末段<1；热后P95劣化≤20%候选预算，不以一次峰值判断 |
| 内存/驻留 | 全App进程树peak PSS、native/Java堆、GPU/vendor外部内存可观测项；冷/重复/stream/释放后；长语音缓冲上限 | peak PSS≤1GiB作为中低端PoC讨论目标；连续30请求释放后相对稳定基线增长≤10%+64MiB；必须按真实设备修订，不假设AAR/权重大小=PSS |
| 终止/音频完整性 | 记录正常EOS/模型等价完成、token/context limit/unknown、输入采样数/实际覆盖区间、prompt/输出预算；Qwen超context与预算耗尽、分段尾包/重叠覆盖 | 不以普通text/tokens长度推完整；需native真实状态或证据化限制/分段策略；未证完整不得进成功IME final/质量或性能组，同模型比较必须等覆盖/等终止。[S03/S62] |
| 生命周期/取消 | App↔IME、旋转/前后台、字段换焦、锁屏、开始/停止/cancel竞态、双点击；CPU及各EP的file/asset初始化/createStream/online调用native异常转换与坏模型路径，不只包装过的offline decode | 无并发引擎、无死pointer/崩溃/提前slot释放；cancel后零预览/外部commit，JNI真实退出前owner仍busy；UI失效响应≤100ms但native退出时间独立测、不谎称硬取消 |
| 隐私/安全 | 敏感IME字段不可录、失效partial不回流、hotwords/正文/URI日志扫描、无INTERNET/自动上载；SHA/路径/原子激活/签名组件 | **零安全回归**，不得fallback绕完整性；若native错误会`_Exit`或logcat漏正文则阻塞发布 |
| 数值/EP/fallback | 同一模型/输入且同实际覆盖音频与termination成功条件CPU FP32、INT8、EP比对；actual EP与分区；EP缺失/驱动错误/不支持shape；故障后资源释放 | 阈值按模型批准，质量非劣且实测收益才启EP；requested≠actual要明确拒绝或记录CPU，不静默标加速 |
| 16KiB/包 | 每个.so ELF LOAD alignment、APK打包/ZIP、依赖页假设、实际16KiB init/infer/free与4KiB对照 | 全依赖/实际运行通过，缺vendor库证据列待验，不以CMake标志或arm64 ABI冒充验收 |

旧P0 `20/20`来自两条公开录音衍生工程case，是后端一致性而非独立人工golden；历史native load约11.6s/PSS约3GiB并不是当前APK、更不是sherpa实测。因此既不能据其断言切换必省资源，也不能拿它和官方不同场景数字算倍数。[本repo `docs/requirements-audit.md:37–42`、memory `asr-quality-evidence`]

## 8. 不可核项与本轮停止边界

- 所有本项目质量/性能/设备验收未运行；官方例子/文件列表不是本设备测试，报告未引用跨设备吞吐数字作保证。
- HF模型卡不可达（API/raw均连接超时），Zipformer第三路径Modelscope不存在：具体weight license保留unknown；未下载权重补查。Paraformer/Qwen上游API可读，但转换bundle的全部许可/资产SHA未知。
- SenseVoice商业/再分发解释冲突且仍待许可方最终确认；不是本轮擅自批准或拒绝商业使用。
- 普通AAR内部ORT实际版本/EP集合、JNI/ORT/vendor ELF16KiB和传递依赖没有下载检查；只有源配置/发布metadata。
- QNN CPU/GPU用于所列sherpa ASR导出模型、MediaTek/Exynos vendor直连、通用AndroidGPU/Vulkan路径未获得可用证据；不能从ORT理论能力补成已支持。
- 此次F001/F002仅源码证据：初始化/online未包装的C++异常、Qwen context剪音频尾/预算结束仍普通result已核，**未设备复现**。JNI异常边界及termination/覆盖状态缺口尚未实施修复；当前公共API不能被报告承诺为足以辨别完整成功。[S58–S62]
- 未审计全部native失败/日志路径、第三方依赖闭包、QNN SDK再分发许可、模型库动态代码信任；这些不是一个Javaadapter能自动解决。
- 未改产品/Gradle/native/APK/身份/旧reports/根计划；未编译、安装、ADB、上传、推送或本地commit；task保持 `in_progress` 等父证据审查，不close。

**单一下一步：等待父 `sherpa-steward` 复核F001/F002修正文稿、62项来源与范围；父审查通过后再决定安全提交研究文本，研究者不提交/关闭。**

### 8.1 本次审查与逐项修正历史

父已保存初稿57source/13local与5个公开refetch匹配复验；用户要求的技术回审由**全新隔离上下文 `sherpa-review-2`、newapi/gpt-6.1-sol/high**完成，不依赖被中断MiniMax，属于同家族上下文隔离，**不称cross-family**。父接受F001/F002两项P2，研究者在固定revision复抓5个文本，追加S58–S62并更新§1/§4.1/§5.1/§6.1–6.3/§7.3。不更改原父记录：[parent-verification.md](../reports/sherpa-research/parent-verification.md)、[gpt61-review.md](../reports/sherpa-research/gpt61-review.md)；其中旧审查阶段文字作为历史保留，不代表此次使用了MiniMax结论。该回审和修正不是设备故障复现或产品接入验收。

## 9. 官方来源导航

以下为主要原始入口；**每个S编号的单文件URL**均在[62项来源manifest](../reports/sherpa-research/sources-manifest.json)，精确行号与短引文在[初稿证据摘录](../reports/sherpa-research/evidence-excerpts.md)与[F001/F002补充摘录](../reports/sherpa-research/evidence-corrections.md)。API发布digest仅为上游metadata，未下载核验artifact。四文件revision对照见[revision-comparison.json](../reports/sherpa-research/revision-comparison.json)。S02中“首次10s/后续<1s”是demo硬编码UI提示，不是测量、没有设备/场景依据，本报告不把它列为性能证据。

- S01：[用户官网页面](https://k2-fsa.github.io/sherpa/onnx/android/apk-simulate-streaming-asr-cn.html)。
- S02：[simulate Home.kt](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/android/SherpaOnnxSimulateStreamingAsr/app/src/main/java/com/k2fsa/sherpa/onnx/simulate/streaming/asr/screens/Home.kt#L139-L243)。
- S03–S06：[Kotlin API目录](https://github.com/k2-fsa/sherpa-onnx/tree/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/kotlin-api)；S07：[transducer状态/endpoint/reset源码](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/online-recognizer-transducer-impl.h#L249-L440)。
- S08–S12：[固定文档模型目录](https://github.com/k2-fsa/sherpa/tree/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx)；S13：[Qwen3-ASR三ORT Session及Run](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/offline-qwen3-asr-model.cc#L121-L146)。
- S14：[framework LICENSE](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/LICENSE)；S15：[SenseVoice作者许可分层说明](https://github.com/FunAudioLLM/SenseVoice/blob/586bbdb2b052f4fe19322581825228b6810a9fb3/README.md#L502-L506)；S16：[固定FunASR模型协议](https://github.com/modelscope/FunASR/blob/58830eca4012644aac0c3218c3ccc7d98f003fda/MODEL_LICENSE)。
- S17–S19：[SenseVoice许可issue及pending说明](https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5083546605)、[重开follow-up](https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5463240506)。
- S20–S22：官方Modelscope API对应模型：[Paraformer](https://www.modelscope.cn/api/v1/models/damo/speech_paraformer-large_asr_nat-zh-cn-16k-common-vocab8404-pytorch)、[SenseVoiceSmall](https://www.modelscope.cn/api/v1/models/iic/SenseVoiceSmall)、[Qwen3-ASR-0.6B](https://www.modelscope.cn/api/v1/models/Qwen/Qwen3-ASR-0.6B)；S23/S24作者LICENSE分别见manifest。
- S25：[Android arm64构建脚本](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/build-android-arm64-v8a.sh)；S26/S27：[AAR源码](https://github.com/k2-fsa/sherpa-onnx/tree/040afe360a38e25daaa325ce8889abf93ea02609/android/SherpaOnnxAar)；S28：[Android workflow](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/.github/workflows/android.yaml)；S29：[v1.13.8发布](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8)。
- S30–S32：[CMakeLists](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/CMakeLists.txt#L204-L208)、[provider](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/provider.cc)、[session/NNAPI条件](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/session.cc#L389-L422)。
- S33–S35：[ORT NNAPI](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html)、[XNNPACK](https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html)、[QNN EP](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)（live，不能代替sherpa接线）。
- S36/S37：[offline factory](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/offline-recognizer-impl.cc#L189-L240)、[online factory](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/online-recognizer-impl.cc#L84-L105)；S38/S50：[direct QNN目录](https://github.com/k2-fsa/sherpa-onnx/tree/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/qnn)。
- S39/S40：[QNN Android打包文档](https://github.com/k2-fsa/sherpa/blob/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx/qnn/build-android-demo.rst)、[SDK版本文档](https://github.com/k2-fsa/sherpa/blob/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx/qnn/download-qnn.rst)；S41：[QnnConfig](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/qnn-config.cc)。
- S42/S43：[logcat与_Exit宏](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/macros.h#L22-L59)、[QNN错误宏](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/qnn/macros.h)。
- S44/S45：[RKNN安装](https://github.com/k2-fsa/sherpa/blob/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx/rknn/install.rst)、[Ascend安装](https://github.com/k2-fsa/sherpa/blob/c02f72ca1540163a54019e845127fa52d5de175b/docs/source/onnx/ascend/install.rst)；S46：[Android官方16KB指南](https://developer.android.google.cn/guide/practices/page-sizes?hl=en)。
- S58：[offline JNI初始化/stream/包装范围](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/jni/offline-recognizer.cc#L480-L655)；S59：[online JNI初始化/未包装decode](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/jni/online-recognizer.cc#L245-L402)；S60：[显式SafeJNI helper](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/jni/common.h#L159-L205)；S61：[CPU Paraformer Ort::Session构造](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/offline-paraformer-model.cc#L32-L84)；S62：[Qwen context剪尾/生成预算/普通result](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/offline-recognizer-qwen3-asr-impl.cc#L873-L937)。
- S52为Java OfflineRecognizer、S53为demo初始化/按size复制、S54/S55为Android ORT cmake默认1.28.2、S56为SenseVoice结果时间戳转换、S57为QNN SenseVoice固定帧长元数据；均见manifest原始URL与摘录，结果字段不等于高精度word alignment。
- S47：[offline JNI](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/jni/offline-recognizer.cc)；S48：[online demo尾补](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/android/SherpaOnnx/app/src/main/java/com/k2fsa/sherpa/onnx/MainActivity.kt#L174-L210)；S49：[QNN Android模型配置生成数据](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/scripts/apk/generate-qnn-asr-apk-script.py#L37-L108)；S51：[QNN条件编译](https://github.com/k2-fsa/sherpa-onnx/blob/040afe360a38e25daaa325ce8889abf93ea02609/sherpa-onnx/csrc/CMakeLists.txt#L259-L274)。
