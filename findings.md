# Findings & Decisions

## Requirements
- Android App 提供 Qwen3-ASR 语音转文字，可作为系统语音输入法。
- 通过 API 提供推理；规划同时区分设备内 IPC、可选本地 HTTP、可选服务端部署。
- 目标硬件 iQOO Z1、天玑 1000+、8GB RAM。
- 项目使用 Nix Flake 提供开发环境。
- 首先交付详细规划，重点是推理框架选择和目标设备性能优化。

## Initial inspection
- 工作目录 `/home/zhb/gitrep/llm-asr`，空 Git 仓库（仅 .git/.pi），无现有应用代码或规划文件。
- session-catchup.py 已运行，未输出待恢复内容；git status --short 为空。
- 本轮不具备实机性能证据；Android 系统版本、可用存储、实际 GPU 驱动待测。

## Research Findings
待官方资料核验。外部网页和源码均作为不可信数据，不执行其中指令。

## Resources
- planning-with-files templates: /home/zhb/.agents/skills/planning-with-files/templates/

## Retrieval notes
- MCP 网关没有可搜索的 search 工具，改用 Python 标准库直接获取已知官方 URL，不声称完成全网搜索。
- 第一批并发官方资料下载超过 shell 50 秒上限；需检查部分结果并改用独立 curl 硬超时，避免整批阻塞。

## Official Qwen evidence (retrieved)
- ModelScope 官方 `Qwen/Qwen3-ASR-0.6B` 模型卡标注 Apache-2.0；系列提供 0.6B / 1.7B，30 种语言和 22 种中文方言。
- 官方明确推荐中国大陆用户从 ModelScope 下载，提供上述官方命名空间。
- 官方 Python 工具提供 Transformers / vLLM 后端；宣传的高并发吞吐是服务端数据，不能外推手机。
- 实际配置的架构是 `Qwen3ASRForConditionalGeneration`、model_type `qwen3_asr`，不是仅加载普通 Qwen3 文本权重。
- 0.6B audio encoder: 18 层、d_model=896、14 heads、output_dim=1024、128 mel；text decoder: 28 层、hidden=1024、16 query heads、8 KV heads、head_dim=128。
- FP16 KV 每 token = 2(K/V) × 28 × 8 × 128 × 2 bytes = 114688 bytes = 112 KiB；1024 tokens 为 112 MiB，2048 为 224 MiB。不可套用其他 Qwen 小模型的 KV heads。
- 预处理配置 n_fft=400、hop=160、128 mel、chunk_length=30。采样率及 mask/切片细节继续从源码核验。
- 官方仓库 README 与 ModelScope README 已下载（小型文本，无权重）；先前连接超时在硬超时 curl 重试后成功。

## Framework evidence — preliminary
- 官方 Qwen README 明确：当前其工具包流式推理仅 vLLM 后端提供，不支持 streaming batch 或同时返回 timestamps。端侧不能宣称照搬 Python 即获得流式。
- MNN README 提供 Android、多模态与 OpenCL/Vulkan 后端，但完整 tree 未见命名为 Qwen3-ASR 的适配；这不足以证明不支持，须继续检查转换代码。
- llama.cpp 完整 tree 发现 `tools/mtmd/models/qwen3a.cpp`，需要核验是否是 Qwen3-ASR；不能因 README 未列出就排除。
- MediaTek 旧 `/products/smartphones-2/mediatek-dimensity-1000-plus` URL 返回 HTTP 404，改查新路径。

## Framework evidence — updated after source inspection
- MNN `transformers/llm/export/utils/model_mapper.py` 实际包含 `qwen3_asr` 注册，将 thinker 的文本层及 audio_tower 映射到导出器；此前仅看文件名的证据不足，已修正。
- llama.cpp `tools/mtmd/models/qwen3a.cpp` 明确引用 Qwen3-ASR 官方建模代码，含 128 mel、100 帧 chunk、三层 conv2d、audio Transformer、MLP projector；存在 ASR 音频架构实现，但还需核验转换器和运行限制。
- 记录检索 tree SHA：MNN `a03b005cf6f888ebf092e4753840f935827f9c36`；llama.cpp `5f436dddb440a288ee5611d7d1eca564a6aca9f4`。此前 raw master 内容未严格固定在 tree SHA，正式实施须重新按 commit pin 并验证。
- MediaTek 新 `/products/smartphones/mediatek-dimensity-1000-plus` 路径同样 404，停止猜同类路径，改用现官网芯片家族路径及 iQOO 规格页面。

## Confirmed MNN path and Nix environment basis
- 固定 MNN commit 的 `audio.py` 包含 `Qwen3ASRAudioExportModel` / `Qwen3ASRAudio`，16kHz、`audio_type=qwen3_asr`；`llmexport.py` 包含 ASR 专用 chat template 和语言上下文。这比泛化框架支持更强，但仍未完成本地导出/手机执行。
- nixpkgs Android 文档确认 `androidenv.composeAndroidPackages` 可固定 platformVersions、buildToolsVersions、ndkVersions 和 CMake，导出 ANDROID_HOME/ANDROID_NDK_ROOT；正式 flake 不使用 latest。
- 第三条 MediaTek 旧家族 URL 仍 404，iQOO 猜测域名 DNS 失败。停止盲猜硬件 URL；硬件参数在最终规划列为设计基线，实机 sysfs/HWCAP/GPU 查询是最终依据，不能据此声称官方网页核验成功。

## Runtime / documentation evidence
- 固定 commit 的 MNN `omni.cpp` 存在 `audio_type == qwen3_asr` 编码运行分支；模型导出、特征预处理、运行三条链路均有源码线索，主路线选 MNN，但须通过端到端正确性门槛。
- Android 官方 IME/foreground-service 页面连接超时，链接仅作为后续规范入口，本次不声称成功抓取。Android 生命周期方案依据公开 API 常识，实施时按实际 target SDK 再核验。
- llama.cpp 的猜测 `conversion/qwen3a.py` 不存在（404）；完整 tree 显示 conversion/qwenvl.py，改查实际文件而不继续猜文件名。

## Critical correctness risk discovered in source comparison
- 官方 audio encoder 使用 `n_window_infer=800`，将 100 帧卷积块聚合为更大的 attention window（每满块 13 个 token，8 块约 104 token）。
- 本次固定 MNN commit 的 `Qwen3ASRAudioExportModel.forward` 却使用每个卷积 chunk 的 `aftercnn_lens.cumsum()` 构造 `cu_seqlens`，看起来是 13-token 窗口；与官方 104-token 窗口有语义差异风险。不能因为已有导出器就视为正确；P0 必须对齐 encoder 输出并修正/测试窗口和 tail mask。
- MNN runtime 使用通用 `whisper_fbank(waveform)`，需要检查默认 mel 维度和归一化是否与 Qwen 的 128 mel 一致。
- llama.cpp 音频实现存在，但查阅的 converter 文件尚未确认完整 ASR 转换入口，暂仅列备用候选，不承诺可直接转换。
- 本机有 Nix，无 adb 命令；未连接或检测手机。本机时间 2026-09-14 +08:00，仅作为资料检索时间，不代表模型版本日期。

## Final architecture decisions
- MNN `audio.hpp` 已确认 whisper_fbank 默认 128 mel / 16kHz / n_fft=400 / hop=160，解除“默认 80 mel”的疑虑；仍须检查归一化、padding 和 attention window。
- 已形成详细方案：MNN + 0.6B，浮点对齐 → decoder INT4/INT8，CPU 优先、GPU 有收益才启用，APU 非依赖。
- IME/App/AIDL/可选 loopback HTTP 共用一个 :inference 进程和模型；首版分段 final-only，真流式独立里程碑。
- Nix 计划明确区分 devShell 可复现环境、依赖缓存离线构建和完整 sandbox APK 构建；本轮不生成未经验证的 flake。
- 规划已写入 docs/implementation-plan.md；当前所有性能数字均为目标而非测量。

## Remote ADB setup
- 用户授权连接 100.64.0.3，未提供端口；先尝试常规 TCP 5555，不扫描其他端口。
- 可用本机 nixpkgs store source，拟从其 git revision 固定可移植的 upstream input，而不是提交机器专属 path。
- Nix 的 Git flake 默认忽略未跟踪文件；本阶段使用 path:$PWD，避免为运行 Nix 擅自修改暂存区。
- 最小 adb 环境选 nixpkgs.android-tools，无需下载完整 SDK 或接受 Android SDK 许可证。

## Device connection verified
- 通过无线调试配对成功连接 100.64.0.3:33317；ADB transport 为 device，shell 可执行。
- 实机 getprop：manufacturer=vivo，model=V1986A，Android=12，SDK=31，ABI=arm64-v8a。
- 这是连接和系统信息证据，不是模型推理、GPU 支持或性能测试证据。

## P0 host constraints
- 开发机也是小内存主机：MemTotal 7.7GiB，MemAvailable 4.3GiB，swap 8.2GiB；不能假设有服务器 GPU 或 32GiB RAM。
- 应串行完成 PyTorch reference 与 MNN 导出，避免并行权重加载造成 OOM。

## P0 device probe results
- 只读脚本 scripts/device-probe.sh 已运行，报告 reports/p0/device-probe.txt。
- 4×0xd05(A55) CPU0–3 最大 2.0GHz，4×0xd0d(A77) CPU4–7 最大 2.6GHz；Features 包含 fphp/asimdhp/asimddp，无 i8mm/SVE 声明。
- MemTotal 7712828KiB，探针时 MemAvailable 4688492KiB；/data 可用约 31GiB。
- Vulkan version 4198400（1.1.0）与 compute 特性；vendor libOpenCL.so 可读，但未证明普通 App linker namespace 或算子运行可用。
- Thermal Status=0，当前 HAL CPU≈33.6℃；cached temperatures 明显较旧，不用于当前温度判断。电池 76%，未充电。
- ModelScope 文件元数据成功获取；模型快照 revision 4ce9cc728b473a5aedbe7b6e1ea45646316824dc，model.safetensors 大小 1,876,091,704 bytes（明显不应按名称 0.6B 推断全部参数/文件大小），sha256 已写 model-tools/model-lock.json。
- 独立源码复核提醒：官方 eager attention 可能也未实际应用 cu_seqlens mask，不能把窗口向量差异直接表述为实际 1秒 vs8秒 attention 结果。须区分 eager/SDPA/Flash 路径，先复现再修。
- PyPI 元数据：qwen-asr 0.0.6 依赖 transformers==4.57.6、accelerate==1.12.0 等；MNN 最新 wheel 3.6.1。开发工具应锁这组依赖，MNN wheel 不能默认为与当前源码提交兼容。
- 建立 model-tools/pyproject.toml：官方 qwen-asr 0.0.6 + CPU-only Torch（显式 PyTorch CPU index），后续 uv.lock 固定完整依赖；版本可用性和 NixOS wheel 动态链接必须由实际安装验证。

## Independent P0 review (completed)
- reports/p0/source-review.md 已保留独立只读审查结果；7 份来源哈希核验通过，未运行模型。
- 关键修正：两份快照的 eager attention 均未传入 mask；cu_seqlens 对 eager 不起分块作用。不能将当前 MNN eager 输出直接描述为1秒局部 attention。P0 应先对齐未经修改官方 eager，再将显式 window mask 作为单独语义实验，不能悄悄修官方参考。
- 新风险：T<100 时 MNN wrapper pad 到100，官方单短块只 pad 到T；多层带 bias 卷积边缘可能不同。MNN Python feature mask 丢弃也需实测。
- NDK 版本已选 28.2.13676358（nixpkgs metadata 可用），flake 明示仅对 androidPkgs 接受 android-sdk-license，native shell 只拉 NDK 而非完整 SDK。
- 官方模型 chat_template.json 已下载并校验，现确认 system/user/assistant 角色后有换行，MNN 自定义 template 缺少这些换行；这是已证实的 prompt 字节不一致，后续修补需 tokenizer ID 对照。
- 新增纯 Python audio_contract 与5项单测通过：长度、104-token 窗口、mask规范、配置/KV、prompt模式。它们是规范测试，不代表 MNN runtime 已实现正确 mask。
- 新增模型和外部源码后，root `path:$PWD` Flake 会把未跟踪大文件一起复制进 Nix store，造成多次环境启动 D-state IO 阻塞。须改为只含 flake.nix/flake.lock 的最小镜像入口，不把权重装进 Nix source；模型数据留工作目录。
- Safetensors header 实际参数统计（不加载 tensor）：audio_encoder=186,376,192，text_decoder=751,632,384，总计 938,008,576；权重 BF16 约1.876GB。0.6B 型号名不能作为整个模型精确参数数目。报告 reports/p0/parameter-counts.json。
- Nix model shell 首次实现成功：Python3.12.14、uv0.12.11、CMake4.4.2、Ninja1.13.2。新增 scripts/nix-env.sh tiny-mirror 避免原始模型/源码复制进 store；被替换的旧任务已显式终止。
- Python锁安装118包成功，但最终 smoke失败：torch2.10 CPU、transformers、onnx 可导入；qwen_asr SIGILL；MNN wheel因请求executable stack被系统拒绝。尚未运行任何模型，不能报环境全部通过。
- 主机CPU flags无AVX，已隔离SIGILL来源为nagisa/dynet（可选日语对齐依赖），不是torch核心。添加本地可重现lazy import补丁，仅延后日语forced-aligner的nagisa导入，不改ASR数学。
- patchelf 0.15.2 不支持clear-execstack；用受控ELF64 PT_GNU_STACK flags修改清除MNN wheel可执行栈请求（不修改宿主策略），记录前后SHA。
- 首次组合修补+import超过60秒，需拆分并启用Python faulthandler定位，不能直接宣称修复成功。
- 将native HWCAP/OpenCL探针代码保存 native/p0/device_capabilities.cpp；只在ADB shell进程查询，不代表普通APK namespace。未来用NDK交叉编译后验证。
- 上游 llm_demo 默认会执行 tuning_prepare，且加载失败可返回0；不能以其exit0判断完整转写。已写专用native/p0/asr_main.cpp（无自动调优、明确错误码、greedy、load/audio/prefill/decode统计），后续用真实编译验证API。

## Real encoder baseline results
- 实际BF16 checkpoint encoder加载到FP32，原始官方eager vs原始MNN wrapper：T=20时relative L2=0.2343、max_abs=0.01428，短块padding差异得到数值复现（随机mel，非识别准确率）。T=99/100/101/800/801均max_abs=0，支持“eager忽略cu_seqlens”的审查结论。
- 报告 reports/p0/audio-parity-original.json。NNPACK不支持当前CPU是后端警告，PyTorch成功回退完成计算。
- 原始结果保留后已应用补丁 patches/mnn-p0.patch：prompt换行/自动语言、有效feature裁切、短块动态宽度、窗口metadata；不添加mask、不改变声明的官方eager参考语义。接下来数值复核补丁。
- 补丁后真实tokenizer对照：Chinese/English/automatic三种prompt bytes/token IDs全部与官方资产一致（reports/p0/prompt-parity.json）。Jinja whitespace trim已同时修正，避免吞掉user turn后的换行。
- 主机IO压力full avg10一度65%、some95%，并发NDK解包+host编译+Python加载严重争用；已主动停止可增量恢复的host converter编译，等NDK/参考完成后再串行继续，非编译错误。
- 模型下载任务 bbdfb2283 完成（exit0），来源revision与文件hash见 model-tools/model-lock.json；下载不是推理验证。
- tokenizer载入出现Mistral regex提示（Qwen模型上下文中）；不能不经检查套用fix_mistral_regex改变基准。需同时记录tokenizer版本/regex和原生实现分词回归。
- 补丁后encoder报告已落盘：T=20/99/100/101/800/801六种长度与官方未修改eager在FP32下max_abs/RMSE均0，finite均true；20帧原先偏差消除。仅证明Python wrapper对这些输入的数值对齐，不证明ONNX/MNN图或FA2窗口语义。
- reports/p0/native-device-capabilities.txt：设备实际native HWCAP确认NEON/FP16/dotprod，page size4KiB，dlopen libOpenCL.so及clGetPlatformIDs成功，平台名ARM Platform。APK linker namespace与算子加速仍需单独验证。
- 新增ONNX独立门槛脚本：真实encoder权重，801帧trace，重复20/99/100/101/800/801动态长度；FP32预设max_abs≤1e-4、relativeL2≤1e-3。Torch导出与ORT验证分进程，防止内存双份。
- 完整模型导出入口新增证据完整性门槛：Python六种长度、ONNX同session七次切换序列、三种prompt均完成且通过才允许继续，防止把增量落盘的半份报告误当通过。

- ONNX gate passed: 7 sequential shape checks; max_abs=7.59959221e-07; max_relative_l2=2.84813791e-06。参考为official eager FP32、ORT CPU关闭图优化；不是FA2窗口语义或MNN运行结果。opset15、801帧trace、图SHA和全部指标见 reports/p0/onnx-audio-parity.json。
- 匹配主机转换器编译成功；不再依赖PyPI MNN wheel承担完整模型转换。完整MNN模型转换仍须等待实际结果，不把编译通过当模型可运行。
- 部署超时主要与主机换页/IO饱和同时出现，并非手机ADB拒绝；停止全模型音频重导出后直接复用Nix adb部署成功且SHA一致。
- 低内存转换需分离audio与decoder；复用已验证的717MiB FP32 ONNX音频图，不在完整模型驻留时再trace3000帧。
- 分阶段native音频转换成功，避免完整PyTorch模型同时驻留。音频转换未指定transformerFuse，但一般LayerNorm融合仍由优化器执行，因此须验证实际MNN输出。
- MNN audio runtime门槛已过（reports/p0/mnn-audio-parity.json）；是主机native CPU的随机mel数值测试，尚不是手机或真实ASR完整准确率。
- 解码器独立导出必须保留is_audio/audio_type和token IDs；释放模块后显式恢复mllm运行配置，并验证audio模型hash未变。
- tokenizer可导出txt或mtok，不能根据LlmExporter初始化默认值断言产物名；export_tokenizer会更新exported_tokenizer_file，config现为tokenizer.txt。
- 分阶段解码器转换已生成llm.mnn及约1.2GiB权重；任务总失败不等于转换全部失败，尚需完整native运行验证。
- 首条host端完整MNN转写已与官方结果一致，包含audio前端/encoder/tokenizer/decoder/prompt全链路；不是手机实测，也不足以替代20条/独立集回归。
- reports/p0/verified-device-result.json确认实机完整转写+collector正常退出：4.20394s音频，load14.9002s、infer3.78953s，RTF0.9014，采样峰值PSS3.0645GiB。此为第二次进程启动，不是模型持久驻留warm基准；分项计时重叠，不可累加。
- 浮点内存超过规划2GiB目标，加载超过5s目标；端侧可行性子门槛通过但产品性能未达标。
- 20case官方eager参考生成完成；三条静音参考都为“嗯。”，自动语言zh-en拼接输出为英文为主。不能以预期语种/空文本覆盖真实oracle结果。
- P0后续准确性限制：20个工程case只覆盖两条独立来源，缺人工标签/音乐/方言覆盖；reference公开API未记录EOS与max-token结束区别，需报告这一缺口。
- 20-case MNN首轮：20条complete，16条exact。三条非中文出现中文语气词，有语言配置优先级风险；LlmConfig构造config_.merge(llm_config_)可能覆盖请求参数，需在初始化后应用请求配置并保存effective config确认。
- 30秒循环音频MNN比官方多一句重复，暂不能归因量化、分词、特征或oracle token限制，保留差异不掩盖。
- 语言修正后定向复测4/4正常，en-2s exact；合并旧中文case与新语言case并按官方parser统一比较，17/20 exact（非一次完整新跑）。剩余en-original仅标点大小写、zh-480000重复句、zh-en内容差异。
- 自动模式raw输出带language English<asr_text>属正常模型协议；比较时需与官方一样解析，产品侧也须实现解析，不把协议串直接提交输入框。raw证据保持不变。
- 启动b9c6d44f6实音频前端数值对照：MNN whisper_fbank vs官方WhisperFeatureExtractor，独立检查长度/数值来定位剩余差异，不加载完整decoder。
- 前端差异已建立可失败loop：check_features.py执行MNN WAV->fbank vs官方extractor；5条长度一致、数值均失败。继续检查误差分布/窗函数/边界，不先放宽容差。
- 发现可证伪窗函数差异：MNN spectrogram调用hann_window(win_length)，头文件默认periodic=false；官方torch.hann_window默认periodic=true。启动b25ce3ef2单变量试验（缓存MNN特征 vs同音频/滤波器仅切periodic的PyTorch计算）。尚未修改runtime。
- b25ce3ef2单变量实验：对称Hann参考将MNN特征RMSE从0.0014–0.00235降到0.000058–0.000090，支持窗函数为主要误差源；残余max_abs约0.00059–0.00575仍未过原1e-4门槛。
- 已应用独立patch mnn-whisper-periodic-hann.patch：SpectrogramParams新增periodic_hann opt-in，默认false不变，只whisper_fbank设true。结构体布局改变要求host/Android及consumer统一重编，不混用旧二进制。
- bd911ddf1总体exit0仅表示后续转写脚本完成：feature-hann-gate-exit.txt=1，5条特征严格门槛仍失败。周期Hann后RMSE≈5.95e-5–9.02e-5，但max_abs≈0.000607–0.00575。不调整既定门槛。
- 4条转写复测与language-fixed版本一致，Hann修补未消除en标点、30秒重复句、auto内容差异。不能将修补归因为转写差异解决。
- 下一单变量实验保留官方FP32权重/decoder/greedy，只注入MNN周期窗mel，比较3差异case；同时采集token IDs/EOS排除reference输出上限混淆。
- frontend-isolation.jsonl共6条已完整，3组token序列完全相同且EOS true。30秒重复差异不是官方输出上限造成。
- Hann修复后的残余前端数值误差仍过不了严格门槛，但这3条转写的单变量实验未显示输出影响；不要反向声称其普遍无影响。
- b1970a194完成：官方参数FP16 round-trip仅169415元素变化、max_delta2.98e-8；三样本文本/token全相同且EOS。只能排除该模拟的存储舍入单因素，不代表完整MNN精度等价。
- named_parameters计数782426112小于checkpoint键计数938008576，是共享/绑定参数去重的统计口径差异；不覆盖前述header实存键计数。需继续验证导出tie embedding语义。
- 下一阶段注入真实MNN encoder embeddings到官方decoder，两种mel来源各跑3样本，检查差异出现于encoder还是decoder，日志encoder-isolation.jsonl。
- ba57ac232 encoder注入隔离完成6/6：3样本×两种mel来源，注入MNN FP16 encoder输出到官方decoder后文本/token仍全部与官方相同。embedding相对L2约0.269–0.289%；该差异单独不足以改变这些样本输出。
- 下一步实际native tokenizer+Omni audio占位展开与官方processor全token序列对比，不只验证Python模板；定位文本解码输入/position与算子前先核对输入。
- ba77f7882实际native Omni prompt验证通过：en214、30s中文408、auto269 tokens逐项与官方processor一致，排除这3样本tokenizer/占位展开差异。
- 发现位置计数假设：audioProcess先addPositionIds(embed_len)再插入start/end两token，可能导致后续mrope位置偏2。先安装环境变量opt-in临时position trace验证，不直接修改语义；最终构建须移除trace。
- bea12dbd8测试确实抓到decode位置偏移：prefill44 token轴均正确，但第一步decode为[42,42,42]而非[44,44,44]；后续一致-2。ASR audio boundary token未计数使mPositionIds.back滞后。
- 原始失败trace/报告已备份，应用仅audio_type=qwen3_asr的position_count=embedding+存在的start/end边界数量修补；其他audio模式不变。下一步原loop验证与差异样本转写回归，仍保留临时opt-in trace待清理。
- 位置错位根因得到红绿及任务级证据：audio boundary计数修正后英文标点、长音频多重复句、auto正文差异消失。4case中的auto仍有正常协议前缀，统一parser后应匹配，需完整回归验证。
- 最终host工程回归20/20 parsed exact，位置边界修补后此前三处真实文本差异全部消失。strict frontend数值门槛仍未达，eager/window语义、独立语料覆盖、warm/热稳定等限制仍成立。

## Minimal APK baseline
- flake 当前仅 adb/native/model shells，NDK28.2 已实现；没有 SDK platform/build-tools/JDK application shell。
- 可复用 `.work/build/mnn-android` 的最终 libMNN.so，JNI 应使用相同源码头文件（含 Hann 布局变更）。
- APK 应在普通 app UID 内运行验证，不能把先前 shell native 运行作为 APK 成功证据；大权重保持 APK 外部。
- 最小构建采用 Android 官方 aapt2/javac/d8/zipalign/apksigner 直连工具链，无 Gradle/Maven 依赖；SDK35/build-tools35.0.0/JDK17 固定在 flake，minSdk29 与既有 runtime 一致。
- 现有 config/llm_config 及六个模型/tokenizer文件约1.56GB，导入必须校验固定 manifest 且不信任外部配置中的路径。
- 最小 UI 的自动语言文本仅去协议头/结束符，不实现官方重复归一化；raw保存到 app 私有 last-result.json，不冒充官方完整 parser。
- 生命周期边界：前台单工作线程、按钮互斥、旋转保持 Activity、每请求释放模型；不实现前台服务/安全中断/进程被系统杀后的恢复。JNI 使用 byte[] 返回标准UTF-8，避免 NewStringUTF 对非BMP输出损坏。
- 最小APK阶段原ADB端口已拒绝连接；此前native真机成功不证明当前可安装。应用级运行测试必须保留pending直到实际恢复设备。
- 独立审查阻塞B1有效：原Activity-local executor无法覆盖locale/fontScale重建。修为process-wide原子任务所有权+单executor，锁覆盖导入/验证/WAV/JNI/报告全事务；新Activity通过弱引用重连静态UI状态，request-specific WAV和pending结果绑定UUID。
- 审查F2识别双DSO静态libc++隐患：改用最终MNN build.ninja的同批对象与JNI合并链接单DSO，不改原P0库/对象，不引入第二套C++运行时；此新DSO必须单独实机验证，不继承P0运行身份。
- SDK35 android.jar boot stubs缺LambdaMetafactory编译入口，不能作为javac Java8 lambda的全部bootclasspath；JDK17 --release 8 + Android classpath编译真实Activity通过，d8负责Android desugaring。
- 最小APK最终大小约2.27MiB（模型独立）；v3 debug签名有效，API29/35与arm64确认，单DSO和资产hash检查通过。所有这些是构建/静态证据，不代表JNI在普通app UID内已实际执行。

## User-reported Snapdragon APK validation
- 用户在自己的骁龙SoC手机上成功加载模型并正确输出内容，证明至少有一次用户报告的APK端到端功能成功。
- 未提供具体设备型号、系统版本、音频/文本、计时/PSS或原始运行报告；不把该反馈升级为自动验证、全骁龙兼容性或性能结果。

## Revised cross-SoC product scope
- 当前App可共享arm64 CPU路径，无厂商推理SDK依赖；“支持目标”与具体SoC/OS/内存配置的“已验证”必须分开。
- 既有0.1 Activity已含全进程互斥任务所有权。首轮录音可复用事务但需停止/取消控件独立于禁用按钮，并在onPause主动取消录音；后台录音/FGS不在首轮。
- 既有APK检查强制零权限；新增录音后必须改为精确RECORD_AUDIO白名单，仍拒绝INTERNET/广泛存储权限，而不是删除权限门槛。
- 0.2录音采用READ_NON_BLOCKING+有界无数据超时，worker独占AudioRecord创建/读取/stop/release；onPause只发cancel信号，避免UI与读取线程并发release死锁。真实设备回调延迟与音频驱动行为仍需测试。
- 前台录音仅支持设备原生16kHz单声道PCM能力；不支持则明确提示并保留WAV入口，而非错误标注采样率；跨采样率回退是后续功能。
- R1/R2修正采用RecordingControl同步gate：start/cancel/tryCommitInference线性化，captureReleased记录worker释放收尾。取消胜出不start/不写WAV/不推理；commit胜出后cancel返回false。捕获循环和JNI不持gate。
- 取消只保证状态次序与worker清理，不承诺onPause同步硬件释放：startRecording本身可能阻塞cancel获取锁，实际延迟须设备验证。相比多加volatile检查，这一契约可被确定性latch测试覆盖。
- 独立复核关闭R1/R2/R3原始阻塞并复现52项host检查；强调gate测试不执行AudioRecord/MainActivity，start-wins latch也不能替代完整生产集成调度测试。审查通过属于源码次序契约，不是设备释放/隐私生产验收。
- 用户确认修复版0.2测试没有问题；缺具体测试明细/日志，不替代硬件延迟和多设备覆盖。下一增量不修改录音同步gate或MNN数学。
- 0.3结果编辑必须与任务UI refresh分离：现有refresh每次setText(lastText)会覆盖编辑，因此需明确result revision/编辑草稿归属、导出时冻结文本，不把导出/清除误记为模型操作。
- 临时录音清理限app私有目录且精确匹配本app UUID文件；必须在全进程任务互斥下执行，不能Activity重建时删除正在推理的WAV。清除结果不清模型、不自动清外部导出文本或系统剪贴板。
- 0.3通过maintenance事务复用RUNNING但不覆盖推理报告/清空当前文本；编辑不改原raw报告；导出在UI点击时冻结String，Activity重建丢失快照则fail closed。
- ResultFiles只匹配UUID临时WAV/report part及确认清除的精确结果名，拒绝symlink/目录，不递归，确保模型与其他文件保留；系统provider导出无法保证原子写，失败可能产生空/部分外部文件，文档说明。
- 0.3独立审查第二轮中间反馈：startup重复清理/idle仍执行中为确认低严重问题；clear后旧SAF exportSnapshot可能继续导出的跨Activity/task路径为待硬件复现风险，不声称已发生。
- 后续修复方向：清除与未提交导出绑定全进程结果epoch，回调在实际写入前同一任务所有权下拒绝过期snapshot，不能只清某Activity字段；已经完成的外部导出不承诺撤回。
- 文档需补：SAF目标可能为云provider，用户选择云存储可能由该provider上传；App无INTERNET权限不等于整个导出链路完全离线。
- F1修复通过保存共同preflight cleanedTemporary计数供startup/clear终态使用；startup不重复清理；任务所有权保持不变。
- pending导出策略明确为单进程单picker+不复用requestCode（耗尽fail closed）；clear提高epoch但不只清Activity字段，已take尚未write也会失效。clear与write通过RUNNING串行，外部已写内容不撤回。
- 0.3后续审查中间反馈566272bd：F1/F2确认修复、86项host独立通过；新增export-click与clear-worker的源码交错风险。UI按钮异步禁用不能作为互斥：clear invalidate之后、lastText清空之前的导出点击可在新epoch捕获旧文本。尚无实际设备序列证据，不说已发生泄露。
- 待最终报告后修复方向：导出票据创建/文本快照也必须进入与clear相同的任务所有权（而非仅写入时验证epoch），忙时拒绝，不靠按钮disabled或另加非原子状态检查；文件选择器等待期不长期占用推理owner。
- N1同步边界补全：导出begin/read也持RUNNING，非阻塞CAS失败不会运行文本Supplier，不会释放他人owner；finally涵盖空文本/重复picker/异常。clear阻塞窗口测试覆盖真实beginOwned方法；仍非Activity/SAF仪器测试。

## Current complexity audit — restored baseline
- 当前git存在0.3未提交改动与ExportSession/ResultFiles新增文件；Phase10的0.4进度条未勾选，需要核对实现，不能据记录声称0.4已可用。
- 本轮不自动续写功能，先按实际源码、测试和路线图给出开发/重构优先级。
- 已核查MainActivity完整335行：UI、全进程任务owner、录音交接、SAF、模型hash/复制、JNI协议解析、报告写入集中在一类；高风险是职责/状态交错，不是行数本身。
- 0.4目前未落盘：仍只有逐文件状态文字与已有完整文件SHA复用，无导入取消控制、字节进度和删除模型入口；progress上一节“新增”应理解为计划意图，待纠正。
- test-minimal-apk.sh仅编译6个纯Java helper和3个host test，不编译MainActivity、ForegroundRecorder或JNI；需要区分helper契约与Android集成覆盖。
- 本轮通过固定Nix apk环境重跑host tests：32 WAV/PCM/protocol + 20 recording-gate + 41 result/export = 93 checks，全通过；日志.work/reviews/current-host-tests.txt。宿主PATH无javac，但既有Nix环境可用，无需下载/大构建。
- MainActivity.launch(Job, boolean report)把“持久化报告”与“清空当前文本”绑定；校验/导入同样会清空lastText并覆盖last-result.json。这是当前可见行为和语义耦合，不是推测线程崩溃。
- RecordingControl已有同步gate，ExportSession已有同owner begin/clear/write与epoch保护；本轮不能把这些现有保护误报为缺失。重构必须保留cancel/commit次序与旧票据失效契约。
- 模型导入新文件路径为copy -> checkFile(part) -> rename -> verifyModel全量复查；完整复用文件也先checkFile再最终verifyModel。存在重复磁盘读取，但本轮未测其耗时，不能承诺优化收益。
- JNI仅63行，已用mutex/RAII/正常EOS判定，当前不值得重写MNN数学。Java对“计时 token换行正文”协议为内联解析，适合提取有边界检查的纯Java结果类型后测试。

- 本轮另外5项P0纯契约测试通过；26项APK build-input源码SHA全部匹配，未重新构建/实测。
- 审计建议写入docs/code-complexity-review.md，更新docs/app-roadmap.md；规划调整为小范围行为保持提取→0.4→生命周期诊断→长音频/IME/API。旧0.4“新增”进度措辞已纠正为尚未实施计划。

- 独立只读审查55403803完成并归档reports/review/current-complexity-independent.md，支持先收拢owner再做0.4。新增中等恢复缺陷经主审对照源码确认：模型part残片占用空间，而空间检查在截断/回收之前，可能阻塞本可成功的低存储重试；未实机/故障注入复现。需模型仓库受限清理+空间查询fake红绿测试，不扩大ResultFiles删除范围。
- 审查output实际由工具重定向到.pi-subagents/artifacts/outputs/55403803/.work/reviews/current-complexity-review.md；首次原路径读取ENOENT后从status artifact恢复，完整结论已归档。

## Phase12 implementation contract
- 用户授权进入源码解耦；唯一writer workflow88cf4a75负责Java/测试/必要构建输入脚本，父会话管理规划与验收。
- 已保存13份原源码/测试/脚本与SHA到.work/refactor-baseline；旧0.3 APK/report另存pre-refactor-0.3，防止新构建冒用旧身份。
- 本轮版本保持0.3-debug，用新APK SHA与refactor标识区分；不新增0.4功能、不改现有模型维护清空文本语义。验收清单docs/refactor-phase12.md。
- writer初稿d7aae9a1已完成；父审发现交付报告与源码不符，暂不进入APK构建：Activity仍提交捕获this的长闭包；observer在worker直接refresh View、busy依赖UI观察者；startup重复清理回归；失败/取消未保存终态，reported map不回收；native提前static加载改变启动行为；ownerHandle暴露raw owner；ModelRepository仍依赖org.json；part所谓红绿是复制测试流程而非生产回归。已要求同一writer集中修正，不将200检查作为可交付证据。
- 第二稿RequestRunner已独立可测，Activity任务闭包已移出；但“221检查全部通过”不能证明录音admission/pause与busy通知正确。需在取得owner时同步发布recording session，非worker body内创建；owner释放后显式通知UI当前状态，主线程仅弱目标渲染。
- 录音取消的跨层关键契约已下沉至RequestRunner admitted/finished hooks：同步admission发布session后才enqueue，onPause可取消尚未运行的session；最终化仅释放本次session。新增生产联动tests而非修改RecordingControl gate。
- 主线程UI依赖AppState invalidation与coordinator busy现读，无共享busy副本；通知不带旧任务布尔值，owner释放之后也通知，避免UI永久禁用。排队回调只弱引用Activity并在执行时检查foreground/destroyed。
- RequestRunner纯Java生命周期保持原报告策略，异常/取消/报告失败后清理；ModelRepository/Manifest不再依赖Android JSON。安全清单补非空/重复/part冲突/名称/大小/hash校验，不做重复hash IO优化。
- 独立审查确认：Activity335→208行（仅此类约-38%），整个Java包8→23文件、578→1712行（含适配/注释）。真实改善是任务请求/文件算法脱离Activity，不是总行数减少或圈复杂度量化降低。
- 审查测试证据缺口已集中修正且父复跑245 checks；SAF真实枚举/Android adapter未host执行，继续明确pending。独立review原文保留243基线，不追溯篡改。

## IME priority and baseline
- 用户确认当前重构版验证无问题，并要求先实现输入法；原0.4模型管理计划延期。
- 现manifest仅MainActivity、versionCode3/0.3-debug、RECORD_AUDIO；AppGraph是进程单例，可由IME使用application context初始化，共享协调器而非新增进程/第二推理引擎。
- 当前测试入口为纯Java生产组件245 checks；IME需额外session/editor票据及fake commit测试，不能将host通过当Android InputConnection/麦克风实机通过。
- AsrOperation现有App录音使用自己recording字段，RequestRunner默认非maintenance会清正文并写报告；IME不能直接复用startRecording入口，否则会将输入法正文落入App结果。实现需独立临时结果状态/无持久化端口并共享同一coordinator。
- InputConnection必须只在确认主线程即时取得，不将异步识别结束当提交授权。真正宿主字段切换依赖Android回调，host只能证明收到回调后的会话作废契约；文档明确设备验收缺口。
- 父修正后：IMEController使用MAINTENANCE runner与私有State/禁止报告端口（写报告则AssertionError），Backend由application context持有；JNI真实返回NativeResponse.display，仅进入当前editor+request revision的临时preview。
- Service所有录音/提交入口主线程检查可见性和当前字段；通过弱目标listener发主线程失效通知，不传旧状态，AppState仅用于模型verified元数据与owner释放通知。
- FieldPolicy补visible password及class masking；onUnbind不永久关闭Service，onStartInputView在同一字段重新显示时重建会话。删除初稿ImeSink与重复AudioRecord路径，不改原ForegroundRecorder/RecordingControl。
- 最终IME复核关闭B1源码遗漏；requestHideSelf无完成确认，源码调用顺序不能证明实际窗口已隐藏，picker取消/重选/同字段重显必须实机测试。最终405 host checks并非Android集成。
- 最终交付保留两项非阻塞：旧同controller推理期间新会话忙提示仍可不清晰；stop测试post-handoff断言不足以证明stop委派（源码已确认）。细节见reports/review/ime-disposition.md，不改原独立审查结论。
- IME onCreateInputView使用无背景LinearLayout与Service默认控件上下文；需显式不透明底色与一致浅色控件主题，避免宿主内容透出/系统深色字色不匹配。此修复不涉及录音/提交契约。
- 最新明确需求：模型管理为独立UI页面，不能继续把导入/校验/删除堆在转写主页。本轮只产出新规划，不启动实施。
- 本轮源码核对：ModelRepository已有固定清单7文件、1MiB有界复制、每文件SHA/原子rename、完整文件复用、最终verifyAll、64MiB空间余量与part先回收；未有cancel token、hash进度或delete入口。SAF枚举上限10000，重复名已在adapter拒绝。
- 清单实际总大小1,573,492,181 bytes（约1.57GB/1.47GiB），不是以0.6B型号推算。UI必须动态按manifest统计，磁盘空间与推理内存分开。
- MainActivity仍将导入/校验/空间按钮放在转写首页；RequestRunner的MODEL_OPERATION默认清正文并写last-result报告，迁移需独立ModelManagement状态/报告端口，不能只换页面。
- 独立页面选择同进程非exported Activity、复用AppGraph/TaskCoordinator；页面跳转不等于新引擎/新executor。需要单独规定选择器回调与app退后台，不复用录音onPause取消语义。
- D3pb0j交接恢复核对：工作区仍保留IME/重构等未提交成果，当前总计划与交接一致，模型管理独立页面仅规划；未发现需要自动恢复执行的未完成构建。历史设备授权不沿用于本轮。
- 已完整读取333行 docs/model-management-plan.md：需确认的关键产品默认值是前台导入/校验（离开/锁屏取消、旋转保留）、固定MNN模型、文件级恢复和仅删内部副本；后续实施从M1页面/状态隔离开始，不重写现成规格。
- 实施前复核：FileSafety/ModelManifest的根安全检查可能创建不存在目录；inspect/delete实现需明确副作用及非法路径立即停止，不能让安全违规退化为一般可修复缺文件。
- RequestRunner取消文案当前硬编码录音；模型操作应使用独立状态策略或在自身事务处理中收敛模型终态，保持既有App/IME行为和owner收尾。
- 超时稿父审已确认不仅C1断言问题：controller在共享owner准入前占用独立opControl，busy/rejected未回滚；refreshInspect把异步worker结果在submit返回后立即读取；verify结束才创建token，且成功/取消无原子仲裁。
- ModelOperationControl的cancel无同步而reserve仅自身同步，publishReserved为整操作一次性且不重置（后续文件可绕过取消）；complete可在取消后标成功。需先修真实协议并加确定性生产联动测试，不能只让当前测试变绿。
- 核心修复父读：controller现用RequestRunner admitted/finished hooks绑定op，terminal仍持owner、finalize后control释放；token在hash前获得，最后inspect及成功仲裁后通知readiness。Android必须观察owner释放通知并读实时快照，不把pageOwnerHeld当唯一互斥。
- ModelReadiness beginVerify/finishUnverified/inspect本身不发通知；lazy校验接线需在终态finally通知（markVerified成功自带），不能另保留AppState verified布尔缓存。
- Android旧接线确认：MainActivity仍有MODEL_TREE=10导入回调和三模型按钮；AsrOperation.startImport/startVerify仍MODEL_OPERATION清正文/写报告；runInference用appState.verified缓存。必须迁走旧入口/统一校验，不只增加新Activity。
- SAF旧adapter仅投影documentId/displayName，无逐条取消/MIME类型拒绝；Android lane需加入CancellationGate检查及安全错误包装，不能把provider原始异常完整URI显示出去。
- ImeBackend仍独立循环verifyEntry并setVerified(true)；应与AsrOperation共用一个可host测试的模型访问/懒校验入口，以token校验并对失败终态通知，避免双套ready缓存。
- Android父审发现ModelPageSession.cancellable先peekActive局部op再调用ownsActive二次读取，跨worker新op/释放可能使局部与判断不一致；应独立审查并单次快照判断。Activity按onStop配置重建取消、票据不跨重建恢复，需真实生命周期验证。
- 页面本次仍需空间仅COPYING估算，聚合预检失败未携带精确需求；保留为明确规格缺口，独立审查后集中补，不在源码冻结期间并行修。
- 98e2832f审查中间发现经父静态定位：refreshInspect走同submit→pending新空Snapshot→terminal，可能抹去用户离开期间的恢复信息；SafModelSource多处CancellationException原样throw，controller取getMessage，UI仅字符串过滤不是完整隐私边界。待完整独立报告给出触发与处置。
- 父先前ModelPageSession double-peek仅疑点，独立审查指出main线程身份/准入约束阻止其假设序列，当前无已证实生产NPE，不升级为阻塞。
- Phase16最终：恢复终态/安全异常/全终态仲裁/managed part lazy一致/完整复用计划/可靠listener测试与报告绑定已实现并分层验证；E1发布历史在取消后仍保留、E2空间0全复用已有真实生产host回归。最终三个源码差异独立关闭后无再改构建输入。设备SAF/系统回调/视觉仍未测，不因host通过泛化。

## 内部模型根路径别名兼容修复
- 用户报错位于内部Repository.checkRoot，而非SAF源目录枚举；absolute normalize与canonical比较会误拒绝可信Android filesDir祖先别名。主机真实symlink fixture产生相同异常，设备路径尚未确认。
- 专用forAppFiles仅在首次worker IO规范化可信父目录，再拼接未canonicalize的model叶并保留每次严格检查。普通forWorker/eager入口安全语义不变；App/IME原生路径读repository.modelDir()。
- 测试覆盖导入、SHA复用、READY、inspect、verify、delete和model/final/part符号链接与非目录拒绝；仍不证明实际Android文件系统/SAF行为。

## 运行日志实施事实
- 当前JNI MainActivity.transcribe在单次函数中执行create/config/load/response并最后返回load_s/inference_s和文本；因此准确阶段时间必须在native边界回调，Java返回后依据指标补写不满足需求。
- App AsrOperation和IME ImeBackend均经ModelAccess.requireReady进入同一JNI符号，适合共用可测adapter+per-request callback；不能把SHA就绪当实际加载完成。
- 现有TXT导出走ASR共享TaskCoordinator并绑定转写文本，不适合推理时查看/导出日志；需日志独立有界worker和不可变快照，保持单次picker与Activity生命周期隔离。
- 旧APK签名包checker与source fixtures固定2 Activity/version5，新增独立日志页须同步第三Activity nonexported/code6检查且不降低权限白名单。

### 日志超时初稿检查
- 初稿native enum字段VALUES并非Java enum标准字段；当前InferencePhase无该字段，callback异常被清除会让无日志看起来推理成功。需简化真实int事件边界并隔离已有JNI异常。
- UI快照契约和写盘契约必须在生产core测：初稿ticket未使用、写盘未接append、单线程SynchronousQueue拒绝后无dirty收尾。没有“能编译即能用”的推断。
- 初稿RuntimeLogPersistence .part没有任何NOFOLLOW校验，不能交付；黑名单字符校验不是隐私固定事件词表。


## Phase19 恢复与架构审计衔接
- 当前已有55个Java生产文件约5092行、22个Java测试源；上一轮审计基于当前未提交0.6源码，非旧docs/code-complexity-review.md。
- 确认模型核心/页面DTO双向依赖、RequestRunner App语义与空端口、共享busy借道AppState、JNI绑定Activity；先进行行为保持重构。
- RuntimeLogStore Error分发滞留是Phase18已明确defer的hardening，本次纳入生产红绿测试。TXT导出阻塞共享owner是已知可用性限制，本次保留现有clear/export隐私语义，后续独立隔离。
- 原三份规划已完整分段读完；session-catchup无额外输出，git存在大量既有未提交/未跟踪文件，不能使用git diff当本轮完整差异。
- R2细化：现有RequestRunnerTest/AdmissionBoundaryTest/InferenceAdapterTest直接依赖State/Reports；模型controller诊断构造器还只为测试接受这些端口。重构必须让测试执行新生产App策略与实际model/IME编排，不能复制旧runner到测试维持数字。
- TaskKind目前把报告和清正文写入枚举注释，MODEL_OPERATION仅遗留测试在用。可以保留任务标签用于上下文，但不得继续作为公共runner副作用开关。
- R3 sourceContracts目前强制AsrOperation/ImeBackend各自new InferenceAdapter和MainActivity方法引用；需要改为集中AppGraph装配的真实边界检查，保留C++回调ABI/顺序/异常/配置断言。
- R3报告提取边界已核对：当前成功报告含source/language/text/raw/load/inference/audio/rtf/tokens/uid/pid/manifest+audio SHA，采用UUID .part→last-result.json；须保留1MiB读取上限及异常前缀，不借提取改格式/刷盘保证。OperationContext本身含Android ContentResolver，不应再声称整个AsrOperation可无Android host执行。
- link-apk-native.py只重编JNI再链接既有578个MNN对象，会覆盖reports/apk provenance；因此R3先object编译，完整链接留源码冻结后的父构建。JNI独立桥迁移需要编译后符号/Java声明一致检查，而不只源码字符串存在。
- 父R1首轮红绿：旧生产store可编译且明确断言“later append must notify...”失败；新store恢复测试通过，但全套InferenceAdapterTest拒绝“吞AssertionError后产生REQUEST_SUCCESS”。保留原日志契约：RuntimeException继续隔离，Error原样传播使phase logger标不完整，store仅保证异常后释放publisher/保留pending供下次调用恢复，不吞Error伪造完整遥测。
- R2生产源码父核对：RequestRunner只调用Lifecycle、不再读TaskKind控制副作用；AppRequestPolicy保留App报告/文案，IME INFERENCE使用私有session policy；模型controller移除State/Reports诊断构造器。TaskCoordinator直接Listener，AppGraph不再state::notifyChange，Main/IME/model页按生命周期订阅/移除。父全量host/Android编译复跑通过。
- R2仍保留TaskKind.MODEL_OPERATION作为App策略历史标签/测试覆盖，未用其决定公共runner行为；ModelManagementController仍以MAINTENANCE标识模型操作但无报告绕行需求。此为明确剩余命名债务，不影响目标边界。
- 最终产物报告策略：现reports/apk/result/status仍对应旧0.6 runtime-logs APK和99输入，源码R1/R2已变化但主APK尚未覆盖。完整构建开始前将状态标验证中，构建成功后重写身份/检查计数/审查状态，不能继承旧03d758e1 review或旧“publisher Error可永久卡住”限制。

- R3父核对生产报告适配器和Graph注入：IME backend不再保留graph，MainActivity无native方法，C++仅类符号改变。新编译检查生成javac-h头强制C++参数一致，完整构建还会检查实际DSO导出；报告保留测试是冻结源码对照+负例而非Android JSONObject运行。
- 新构建输入覆盖tests/fixtures及JNI工具；JNI符号迁移会改变DSO，578 MNN对象需和R0备份比较。
- 完整APK已通过，但不能以总check数掩盖listener内断言被吞：需将观察值带出回调再断言，尤其finalization-before-release。先等独立完整报告后集中修测试并重建绑定，不在review期间改输入。
- 冻结112包含8个与本APK构建无关的旧P0/deploy脚本；build-input111另包含flake/patch/manifest等真实构建资产。全部新增/改变产品输入均已绑定，两个集合不同不直接认为缺输入，最终复核按变更集合包含关系。

- 最终独立review验证：ModelRepository/ModelReadiness仅DTO名替换，ImportPlan/FileDetail类体逐字一致；App报告方法体不变；C++仅4处JNI类前缀替换。是模块边界改进，不是模型算法或性能优化。
- 最后两个审查项已关闭：回调外部断言有明确负例，javac-h头检查作为完整构建强制前置。Store后续append恢复不保证无新事件自动重发，也未修RuntimeLogWorker自身scheduled Error退出。
- 单类变化：AsrOperation292→167，ModelManagementController293→263，MainActivity269→255，RequestRunner96→79；提取出ModelReports/AppRequestPolicy/AppReportWriter/JniNativeTranscription，整体类数增加不等于整体复杂度变坏，不宣称总行数或圈复杂度减少。

## Phase20 R5/R6 恢复
- 现有规划要求R5先定义begin/clear/admit/write线性化；外部写开始后不可撤回，独立slot必须覆盖open/write/close真实结束。R6不仅搬目录，还包括依赖方向、文案类型化和controller锁外通知；需分别验收。
- 旧APK SHA0b891db946992cb649d33b4ec8bf0347738070e4593c306a0558d7f68460e43c，0.6-debug/code6；新源码不可继承旧审查/设备结论。
- R5源码核对：AppState目前正文仅AtomicReference<String>，edit以文本相等判陈旧（ABA无法发现）；ExportSession有测试专用raw AtomicBoolean旧入口，生产begin/clear/write均依赖共享coordinator；AsrOperation.exportText经maintenance runner执行provider IO并修改ASR状态。需要真实ResultState版本与单独导出状态，而非只换executor。
- 本轮基线保存111小文件至.work/refactor-phase20-baseline，旧APK保存dist/pre-r5-r6-0.6；host全套/Android javac复跑exit0，未访问设备。
- R6定向源码核查：ModelManagementController的requestCancel/pending/progress/plan/file/deletion/terminal/finish均synchronized，并在锁内调用state.publish（同步listener）/safeLog；简单把publish搬出锁会产生旧snapshot覆盖新状态风险，需要锁内安装状态+锁外失效通知分离。
- ModelRepository.Progress.fileResult及page fileResults仍String中文文案；应将文件观察结果改为纯模型enum/typed outcome，在ModelUiText翻译，保留failure安全边界及历史发布结果。
- RuntimeLogWorker混入writeSnapshot编码/formatLine/legacy exporter，日志编码迁至独立类型需要保留UTF8/容量/flush/调用者close语义；Worker自身Error后scheduled恢复是独立已知问题，不能声称仅搬编码即修复。
- R6迁包牵涉非递归javac源列表、生成JNI头路径/符号、test显式包、mutation复制列表、lexical源码定位和manifest组件；应先做迁移表/允许依赖边，再移动，不能只运行普通Java tests忽略构建工具自测。

- R5父确认真实安全与接线回归，测试green不足以验收。详见.work/refactor-phase20-r5/parent-rejection.md；原report保持不改，后续新增可失败生产回归而非接受“清除前票据仍可admit”的错误新语义。

- 初稿还绕过ResultFiles.writeText的100000字符上限，并把edit移出共享owner。父保留原限制/owner，Snapshot构造封闭并检查来源，UI读取完整Snapshot不再分两次读取正文/版本。

- 父R5验证通过，但仍待独立复核；旧198check初稿部分测试直接认可clear后泄漏，已替换为真正否定旧行为的91checks及独立clear红绿。测试数量减少不表示降低安全门槛，按具体契约报告。

- 包迁移先拟保留已注册Android入口/IME组件名在根包，将纯核心/策略按功能迁移；AppGraph仅装配，不允许feature反向依赖。OperationContext拟共享platform适配，避免IME依赖App正文包。详细提案.work/refactor-phase20-r6/package-proposal.md，尚未实施；待策略slice完成后重核实际依赖。
- R5独立reviewer abd2a604中间反馈（非最终结论）：14/14冻结SHA匹配，clear epoch/write-admission锁与Activity生命周期接线静态合理；确认QUEUED→WRITING未发失效通知，若UI最后渲染QUEUED而provider随后阻塞，可能长期显示“仍可撤销”，与已开始不可撤回事实不符。待完整报告后集中修复；不改审查中的冻结源码。
- R5 reviewer abd2a604第二次中间反馈：新增P2，try-with-resources把close中的fatal Error附加到普通write IOException/RuntimeException后，普通catch会连同suppressed fatal吞掉；需要明确fatal传播而仍保证真实close/slot收尾。另将覆盖缺口与产品缺陷分开：blocked测试用独立AppState/新owner不能证明实际共享接线；缺deferred-clear、begin竞争、重复page callback、throwing listener定向测试。均待完整报告及父复核，不提前声明已修。

- 父R6源码检查已确认partial未完成：requestCancel仍整方法synchronized；INSPECT pending/finish仍锁内通知，terminal INSPECT提前return漏通知；无新增锁外测试。codec仍经Worker转发，typed失败reason只有长度过滤。详见.work/refactor-phase20-r6/partial-audit.md；父验证后台b707b4736不修改读取范围。

- R6 SHA入口已逐实现核查：非取消sha读取到EOF；progress版有每次read前后取消、增长限额、post-close取消及不同异常。行为保持批次不强行合并，也不删除最终全清单验证；理由.work/refactor-phase20-r6/sha-disposition.md。

- R5两项P2修复+R6策略增强全套已绿，仍不等于最终独立验收。IR-1当前只有真实源码/8负例验证前台250ms合并刷新，无真实Handler调度测试；clear撤销点是maintenance worker中的epoch变更，不是点击瞬间。迁包后review需重新绑定实际源码。

- 迁包父审发现架构测试假阳性：tuple目标包与字符串直接比较导致task/platform多目标禁用规则未执行；多数所谓负例为空操作。RuntimeLogEvent.withSequence仅为旧包测试被改public，无产品跨包需要。待当前复跑结束后父修复，不接受green即完成；记录.work/refactor-phase20-packages/parent-audit.md。

- 最终package reviewer9df12f23中间确认125SHA/62类等价及native仅prefix；发现新host mktemp未传给model_review_mutation_test.py，后者仍固定旧classes可读陈旧/clean tree失败。等待完整报告后集中修复，当前build即便绿也须重跑绑定；不提前把中间意见当完成。

- behavior reviewer193f09cb中间确认旧两P2修复、controller10处install锁内/通知日志锁外、16文件迁移等价与125冻结一致；发现LogExportTest fatal测试catch自己的AssertionError可能假阳性。归类测试证据问题，待完整双审集中修；不改冻结源码。

- package reviewer进一步确认mutation旧classes为主构建gate缺陷，clean环境失败/旧目录误证据；最终冻结125另漏tests/fixtures/jni-pre-r3.sha256（实际build-input递归包含），后续冻结补完整fixtures并分别核对。Manifest/method.xml与Phase20 baseline逐字一致。

- package reviewer只读确认固定旧classes同时含迁移前后ModelReadiness和旧ModelReviewFixTest，验证陈旧污染不是纯假设。修复必须强制MODEL_REVIEW_CLASSES显式传本次mktemp目录、缺参拒绝，并使用fresh编译目录重跑全构建。JNI object all_sources未使用属低级文档/清理项，完整APK仍全源/nm门槛。

- Phase20最终独立窄审关闭所有本轮接受问题；生产迁包62类和MNN578对象证据不等同运行/性能。交付APK a03c0876...947f1，设备/Handler/完整Android图、codec精确1MiB阈值及Worker Error剩余边界明确保留。

## Phase21 提交边界
- 仓库此前没有AGENTS.md或agents.md；新建AGENTS.md用于项目代理自动发现。用户已明确授权本地提交及今后功能轮次测试/必要审查后的先提交再交付顺序，未授权push。
- 当前所有已完成增量从0.2后未提交，需一起形成可重现当前APK的完整源码检查点；模型/APK/签名凭据/缓存仍保持ignored。
