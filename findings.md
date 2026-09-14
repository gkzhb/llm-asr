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
