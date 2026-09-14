# Android 离线语音输入项目：实施规划

> **路线调整：当前实施顺序以[通用App路线图](app-roadmap.md)为准。** 用户要求暂缓天玑SoC特性优化，先广泛ARM64 CPU支持并完善App；本文其余SoC性能目标为历史规划，不是当前发布门槛。
>
> 已实现最小APK，用户报告在骁龙手机加载模型并正确输出；后续录音/生命周期能力逐版验收。证据见[APK状态](../reports/apk/status.md)与[工作日志](../progress.md)。

## 1. 核心结论

1. **默认模型选 `Qwen/Qwen3-ASR-0.6B`**，从 ModelScope 官方命名空间获取原始权重，在开发机转换并量化。1.7B 只作为之后的质量对照/可选高精度模式，不作为这台设备的首发默认。
2. **主推理路线选 MNN C++ + Android NDK + JNI**，使用 CPU 后端建立基线，解码器优先实验 INT4 权重量化，音频编码器先保留浮点。已有 Qwen3-ASR 专用转换和运行代码，但发现源码语义对齐风险，必须经过 P0 正确性验证，不能直接当作成熟即用方案。
3. **Mali GPU 是实验加速路径，MediaTek APU/NPU 不进入 MVP 依赖**。GPU 算子覆盖、驱动访问、数据搬运和持续温控收益都要在实机验证。没有证据支撑“APU TOPS 可以直接用于 Qwen3-ASR”。
4. **App、系统输入法、API 共用一个推理服务与一份模型**。IME 采用 `InputMethodService`；设备内 API 采用 AIDL/Binder；另提供默认关闭的 loopback HTTP API。
5. **首版是 VAD 分段离线转写，不冒充真流式**。正确性、短句延迟与热稳定性达标后，再做部分结果预览和增量流式状态迁移。
6. **先做端侧可行性原型，再堆 UI**。若 0.6B 经优化仍不适合交互式输入，保留“录完后转写”产品模式，并报告瓶颈；不偷偷换成其他 ASR 模型或云服务。
7. **开发环境由 Nix Flake 管理**：Android 构建环境与模型转换环境分开，固定 SDK/NDK/JDK/Gradle/MNN/Python 依赖；模型权重不放 Git 或 Nix store。

## 2. 需求边界与默认假设

### 必须交付的最终能力

- App：录音、停止/取消、离线识别、复制结果、文件导入、模型下载/导入、设备性能诊断。
- 系统输入法：在普通文本输入框中启动语音输入、预览/提交/取消、删除、空格/回车和切换到其他输入法。
- 推理 API：其他 Android 应用通过 IPC 提交音频并接收结果；开发者可选择开启本机 HTTP 服务。
- 共享引擎：抢占/排队、取消、模型加载与释放、资源限额和错误恢复。
- iQOO Z1 优化：CPU 线程与指令路径、量化、内存、热稳定、可选 GPU。
- 可复现开发：`nix develop`、锁定依赖、模型转换与基准脚本、可审计模型产物。

### 首版不做

- 无限制持续监听、锁屏后台偷偷开麦、无限制 API 并发。
- 完整拼音键盘、自动安装/启用输入法、修改系统设置强制成为默认输入法。
- 说话人分离、全套会议编辑器、词级时间戳、额外大模型润色。
- 默认加载 Qwen3-ForcedAligner；它是额外模型，不是免费附带能力。
- 默认网络服务端推理或自动云端回退。后者只作为明确启用的后续功能。

### 待设备确认

- 手机当前 Android/Funtouch OS/OriginOS 版本、固件、可用存储、后台限制。
- 需要 API 的主要调用者是手机应用还是局域网客户端。当前同时规划 AIDL 和 loopback HTTP，不默认 LAN 暴露。
- 以普通话和中文夹英文为首要测试范围；模型支持的其他语言不等于已经逐一完成产品验收。

## 3. 已核验事实与证据等级

### 3.1 模型并非纯文本 Qwen3

从 ModelScope 官方配置取得：

| 项目 | Qwen3-ASR-0.6B 配置 |
|---|---|
| 顶层架构 | `Qwen3ASRForConditionalGeneration` / `qwen3_asr` |
| 音频编码器 | 18 层，hidden 896，14 attention heads，输出 1024 |
| 声学输入 | 16kHz，128-bin log-Mel，FFT 400，hop 160 |
| 文本解码器 | 28 层，hidden 1024，16 query heads，8 KV heads，head_dim 128 |
| 官方工具链 | Transformers 和 vLLM；官方包的流式接口当前仅 vLLM 后端提供 |
| 型号与许可 | 0.6B / 1.7B；模型卡标注 Apache-2.0，发布前保留 LICENSE/NOTICE 并检查所有依赖 |

完整链路必须包括：**PCM → 重采样/特征 → audio encoder/projector → 音频占位 token 替换 → 文本 prefill → 自回归解码 → ASR 格式解析**。只支持 Qwen3 文本解码器的框架并不自动支持整条链路。[S1–S4]

### 3.2 MNN 主线已有代码，但存在明确风险

MNN tree SHA：`a03b005cf6f888ebf092e4753840f935827f9c36`。下述 audio/export/omni/audio.hpp 按此 commit 获取；model_mapper 初次取自浮动 master，正式 P0 应对同一 commit 重新核验，不能把本轮快照集合当成完整依赖锁。

- `model_mapper.py` 中注册 `qwen3_asr`，映射 thinker 文本层和 audio tower。
- `audio.py` 中实现 `Qwen3ASRAudioExportModel` / `Qwen3ASRAudio`。
- `llmexport.py` 中有 ASR 专用 chat template 和语言参数。
- `omni.cpp` 中存在 `audio_type == "qwen3_asr"` 编码执行分支。
- `audio.hpp` 的 `whisper_fbank` 默认值确实为 16000/128/400/160；仍须对齐窗口、padding、归一化与实际有效长度。[S5]

**P0 阻断级检查：attention window 可能不等价。**

官方源码根据 `n_window_infer=800` 把 100 帧卷积块组合成更大的 attention window；固定 MNN 导出代码通过每个卷积块的 `aftercnn_lens.cumsum()` 生成 `cu_seqlens`。满块情况下大致是 **104-token 窗口与 13-token 窗口的区别**。这是源码检查发现的风险，不是已经复现实测的 bug；必须通过多长度 encoder 输出对比确认，必要时修正导出器、增加测试并维护小补丁。

**结论：MNN 是“已有专用实现、值得优先验证”，不是“未经测试便可发布”。**

### 3.3 备选路线的证据边界

llama.cpp tree SHA：`5f436dddb440a288ee5611d7d1eca564a6aca9f4`。其 `tools/mtmd/models/qwen3a.cpp` 明确引用 Qwen3-ASR，含音频编码器图实现。但本轮未确认完整模型转换/ASR prompt/运行闭环，也没有 Android 实测，不写成“下载 GGUF 即用”。[S6]

硬件官方页面访问失败，以下 SoC 参数作为已知规格设计基线，不声称本轮在线核验成功；最终以实机查询为准。所有网页/源码快照均仅作为资料，不执行其中指令。

## 4. 推理框架决策

| 方案 | 适用位置 | 选择理由 / 限制 | 决策 |
|---|---|---|---|
| MNN C++ / LLM / audio | Android 主引擎 | 有 ASR 专用链路、移动 CPU 与 GPU 后端；仍有导出语义风险 | **主验证路线** |
| llama.cpp + ggml + libmtmd | Android 备选引擎 | 已见 ASR audio 图、ARM CPU 和 Vulkan 基础；转换及完整转写路径待证实 | MNN 门槛失败时限时验证 |
| ONNX Runtime Mobile | 正确性辅助/备选 | 可拆 encoder、prefill、decode 图；动态 mask、KV、INT4、EP 分区和包体需要工程投入 | 第二备选，不与 MNN 同时维护两套生产链路 |
| 官方 Transformers / PyTorch | 开发机 golden reference | 最接近官方语义，便于中间 tensor 对比；移动部署体积/依赖不合适 | 必须有的正确性基准 |
| vLLM | 可选服务端 GPU API | 官方流式/批量能力适合服务器，不能当作 Mali 上的 Android runtime | 不进入手机 APK |
| NNAPI / MediaTek NeuroPilot | 可选研究 | 动态 Transformer、KV 和 INT4 算子是否委派未知；厂商 SDK/驱动和访问权限不确定 | 非关键路径 |

### 主线原生组件

- Kotlin：UI、输入法、权限、服务生命周期、AIDL/HTTP 适配器。
- C++17：音频缓冲、特征前端、VAD 集成、MNN 封装、ASR parser、调度与统计。
- JNI：批量传递 PCM/直接缓冲区，不以每个采样点或每个 token 跨 JNI 调用。
- 只发布 `arm64-v8a` 首版；CPU 通用基线必须在目标 ISA 上可运行。
- 运行时禁止依赖 Python、PyTorch、vLLM；转换与参考推理留在开发机。

### 切换门槛

1. MNN P0 限时调查窗口、mask、特征和 prompt 对齐；遇到小范围差异优先修补并测试。
2. 若需要重写大量算子，或无法在设备跑通正确的单段推理，形成阻塞报告，转向 llama.cpp 小原型验证完整转换/音频输入/解码。
3. 两者都无法满足时，评估 ORT 拆图的成本，由用户确认是否接受额外工期；不擅自更换模型。
4. 不因 GPU 失败而停止项目：CPU 是必须保留的后端；但 CPU 速度不达标须如实降低产品承诺。

## 5. 模型获取、转换与精度管理

### 5.1 国内模型来源

官方 ModelScope 页面：

- `https://modelscope.cn/models/Qwen/Qwen3-ASR-0.6B`
- `https://modelscope.cn/models/Qwen/Qwen3-ASR-1.7B`

官方示例下载形式如下，**实施时必须追加已验证的 revision 锁定，不能直接以浮动 master 构建发布包**：

```bash
modelscope download --model Qwen/Qwen3-ASR-0.6B --local_dir models/raw/Qwen3-ASR-0.6B
```

该命令仅为计划示意，本轮未执行。先用锁定版本的 ModelScope CLI 确认 revision 参数；脚本记录源 revision、各文件 SHA-256、大小、许可与 tokenizer/config 哈希。

### 5.2 产物流水线

```text
ModelScope 官方 safetensors + config + tokenizer + processor
  ├─ 官方 Transformers：保存参考文本/特征/encoder 输出/选定步 logits
  └─ 固定 MNN 导出器 + 已审核补丁
      ├─ 浮点基线（先确认语义，不先量化）
      ├─ encoder 浮点 + decoder INT8 对照
      └─ encoder 浮点 + decoder INT4 发布候选
          → 实机正确性、峰值内存、速度、温控报告
          → 签名 manifest + 可恢复下载的模型包
```

- 初期尽量用原生动态长度；若后端必须做长度桶，测试 2/4/8/16/30 秒有效长度，padding 不得被当作真实音频。
- 转换图必须保留正确的音频长度、tail mask、position、attention window 和 EOS 处理。
- tokenizer 不自行重写 BPE；使用经过单元测试的原生实现并验证特殊 token/prompt 完全一致。
- 中文模式固定语言提示以减少不必要输出；混合/自动识别需单独验收，不把强制语言提示当作准确度改进的保证。
- 首版 greedy decoding、batch=1、有限 `max_new_tokens`（短句从 128/256 实验），遇到上限返回 `truncated`，不能悄悄截断为成功。
- 解析语言标记和 ASR 分隔符后再提交文本，不把特殊 token 插入输入框。

### 5.3 量化次序

1. 浮点对齐：开发机 FP32 或可靠 FP16 参考；手机浮点路径验证，不要求 CPU 必定有快速 FP16 arithmetic。
2. **先量化 decoder 线性层**：MNN 支持的 weight-only INT4，block/group size 由该 commit 的格式约束确定，候选 64/128 仅在支持时比较。
3. 区分权重存储和计算格式：W4 不等于 INT4 激活，也不等于有 ARM INT4 原生矩阵指令。
4. 对比 decoder INT8 与 INT4；敏感的 embedding、lm_head 或其他层仅在精度数据支持时保留更高精度。
5. 音频 encoder/卷积先浮点；后续按算子覆盖和校准结果选择部分 INT8，避免一次全量 4bit 导致不可定位的识别退化。
6. Softmax、LayerNorm/RMSNorm、累加与位置编码保留适当浮点精度。KV 首版 FP16；INT8 KV 是后续独立实验。
7. 校准样本与评测样本分离；包含普通话、夹英文、噪声、方言、数字和专名。权重量化即使不强制校准，也必须做上述任务级回归。

## 6. iQOO Z1 / 天玑 1000+ 性能方案

### 6.1 硬件约束

设计基线为 4× Cortex-A77（最高约 2.6GHz）+ 4× Cortex-A55（约 2.0GHz）、Mali-G77 MC9、LPDDR4X、8GB 统一系统内存。APU 3.0 是否可供普通应用有效使用待核验。

**不作的假设：**

- 8GB 不是 App 独占 8GB，系统、前台宿主 App、GPU 和后台进程共同占用。
- 不假定支持 ARMv9/SVE/i8mm/BF16，不能用新旗舰跑分或内核优化直接外推 A77。
- 不假定所有固件都允许应用加载厂商 OpenCL 库；Android linker namespace 可能阻止。
- 不假定 NNAPI 自动把整个模型放进 APU；局部分区和反复拷贝可能更慢。

P0 设备探针记录 Android API/ABI、`/proc/cpuinfo`、可读 sysfs cpufreq、`getauxval` HWCAP、Vulkan 特性、OpenCL 可访问性、可用内存、`dumpsys thermalservice` 和前后台生命周期。受限信息记录 unavailable，不要求 root 绕过。

### 6.2 CPU 优先

- Release 编译；AArch64 NEON 为基础，FP16/dot-product 通过 HWCAP 与后端实际 kernel 分派验证，不硬编码假定。
- 先测 1/2/3/4 线程，8 线程仅作为对照。prefill/encoder 与逐 token decode 可使用不同线程配置。
- 预填充/encoder 更可能是计算或激活带宽瓶颈，decode 更容易受权重读取带宽限制；分别计时，不只看 token/s。
- 比较系统默认调度与对**本应用计算线程**进行可撤销 affinity 绑定；CPU 集群从 sysfs 探测，不能写死 CPU4–7 是大核。
- 音频采集与 UI 线程不参与推理线程池。避免线程池嵌套与每帧启动线程。
- 不 root、不锁最高频、不修改 governor、不长期持有 wake lock，不通过“性能模式作弊”取得不可持续指标。
- 降低 warm-load 开销：单实例、持久 arena/线程池、可复用特征缓冲；保持真实取消点。

### 6.3 GPU 实验

实验顺序：

1. 全 CPU 正确性与时间分解。
2. 探测 OpenCL；若不可访问则试 Vulkan，不承诺某后端一定可用。
3. 首先验证 encoder/prefill 中实际耗时算子是否能全段覆盖；逐算子记录 fallback、upload/download、kernel 时间和同步。
4. 若 MNN 允许独立 module/backend，实验 encoder GPU + decoder CPU；若接口不支持，先评价全 CPU/可支持的整图路径，不能虚构现成分段配置项。
5. 比较 CPU、GPU 和可实现的混合方案。预热 shader/kernel cache 与首次冷启动分别计时。

只有持续测试下端到端延迟有明显收益（候选门槛 ≥15%，需超过测量噪声）、精度合格、峰值内存和热量可接受，才默认启用 GPU。大核与 GPU 共享内存带宽和散热预算，混合未必更快；失败或驱动异常自动回退 CPU。

### 6.4 内存预算

这里采用 MiB/GiB；以下为**初始预算，必须按实际导出文件和 PSS 修订**。

| 项目 | 初始工作预算 |
|---|---:|
| INT4 decoder + 浮点 encoder 权重常驻/映射页 | 约 0.5–0.9 GiB，依真实层参数分布与混合量化而变 |
| 权重重排/运行时额外缓存 | 约 0.1–0.4 GiB，重点排查重复解量化 |
| KV cache，FP16，1024–2048 总 token | 112–224 MiB |
| encoder 激活、attention/卷积 workspace | 约 0.2–0.7 GiB，长度与后端决定 |
| PCM/Mel/特征缓存 | 尽量 ≤32 MiB |
| Kotlin/JNI/runtime/UI/API | 约 0.15–0.35 GiB，GPU 驱动额外分配需另记 |

目标是正常短句的**全 App 进程树峰值 PSS ≤2.0 GiB**；若超过 2.5 GiB 则触发降配/拒绝大请求。2.5 GiB 是应用保护目标，不能保证 LMKD 不会更早杀进程。GPU buffer 与各进程共享页单独说明，不能把所有预算简单相加称作测量 PSS。

KV 明确按当前配置计算：

```text
bytes/token = 2(K,V) × 28 layers × 8 KV heads × 128 head_dim × 2 bytes
            = 114688 bytes = 112 KiB
1024 tokens = 112 MiB；2048 tokens = 224 MiB
```

- 总 token 包括音频 embedding、prompt 与输出。当前卷积分块满块约 13 个音频 token/秒，30 秒大致 390 个；tail/padding 由真实配置和导出长度验证。
- 首版常规 context 1024，总上限 2048；不是照搬模型 65536 最大位置配置。
- “0.6B × 0.5 byte ≈ 300 MB”只是全权重理想 4bit 下界，不代表混合精度文件大小或运行内存。
- encoder 大中间卷积张量可能比 KV 更值得优化。卷积按等价小批块执行后拼接，**不能为了省内存擅自改变 Transformer attention window**。
- 使用 mmap/外置权重能力前先检查 MNN 当前 API，避免 Java byte[] → native → backend 三份复制。预算包含权重页，不把 mmap 当免费内存。
- `ActivityManager.MemoryInfo`、`onTrimMemory` 与 native 分配计数共同决策；不要只看 Java heap 或开启 `largeHeap`。
- 未开启 API 时，IME/App 离开且无工作后候选 60 秒卸载模型；内存压力立即释放。TTL 与 LMKD 行为须实测。

### 6.5 流式、VAD 与热管理

**第一阶段：分段 final-only。**

- PCM 16kHz 单声道，采集帧 20–30ms；系统不支持该原生采样率时采集可用格式后可靠重采样。
- 候选 WebRTC VAD + 能量门限，起点配置 200–300ms pre-roll、500–800ms endpoint silence，最终以漏字率/延迟调参；它只判端点，不替代 Qwen3-ASR。
- 目标常见语音段 2–8 秒，IME 硬上限候选 15 秒；持续说话在静音边界切段，未找到边界则按策略截段并提示。
- API 首版单次最多 30 秒；较长文件顺序分段并限制总任务时长（候选 5 分钟），不整体载入内存。
- 相邻段必要时使用 200–400ms 音频 overlap，但文本去重不能简单删共同前缀，须用专门边界样本避免删掉真实重复词。

**第二阶段：有界重算的部分预览。**

- 只在短上下文内限频重算，记录额外计算倍数；队列落后时关闭 partial，只输出 final。
- 不能每 200ms 重算从开头累计的几十秒音频。
- partial 带 sequence/revision/final 标记；IME 中不默认自动提交不稳定文本。语言模型 token 输出流不等同于输入音频的增量流式。

**第三阶段：真增量流式，独立里程碑。**

- 对照官方流式状态机，验证音频上下文、回看/回滚、prompt、文本稳定前缀和 cache 语义。
- ASR encoder 有窗口上下文依赖，新增音频可能改变旧特征；不允许未经验证直接拼接旧 KV 当正确实现。

**热与电量：**

- 使用 `PowerManager` thermal status（可用 API 上）和可读温度信号；电池温度只是代理，不当作精确 SoC 温度。
- `MODERATE` 起减少线程/关闭高频 partial；`SEVERE` 切保守配置；`CRITICAL` 暂停并提示。
- 整机实际温度阈值由系统定义，应用不写死一个“安全芯片温度”。
- 连续语音负载至少测 20 分钟；分开报告前 2 分钟与后 5 分钟的 RTF、频率、内存和电耗。

## 7. Android 软件架构

```text
Compose App          InputMethodService           外部 Android App
   │                        │                         │
   └──────────── 共享客户端 / AIDL ────────────────────┤
                                                     ▼
                               InferenceService（:inference 进程）
                               ├─ 鉴权 / 任务队列 / 优先级 / 取消
可选 loopback HTTP ────────────┤
                               ├─ 模型管理 / 生命周期 / 资源限额
                               └─ JNI → AsrEngine → MNN CPU / 实验 GPU
                                                ├─ PCM / VAD / feature
                                                └─ encoder / decoder / parser
```

- 独立 `:inference` 私有应用进程避免 UI/IME 直接持有大模型；不是 Android `isolatedProcess`，模型文件与 IPC 权限仍按本应用管理。
- 本 App 所有入口绑定同一服务，禁止各自重复加载模型。整个进程树一起统计内存。
- 初期单任务执行、有限队列（候选 2 个待处理），交互式 IME 优先；文件/API 工作在片段或解码取消点让出，不能无限期阻塞输入法。
- PCM 通过 `ParcelFileDescriptor` pipe/SharedMemory（按 API 版本）传递，不把 MB 级音频塞进 Binder transaction。
- 客户端死亡触发取消、关闭 FD、回收缓冲；推理服务崩溃后 UI 可重新绑定，结果不跨会话误投递。
- native cancel 是原子标记，在分块/解码步检查；单个长算子未必能即时中断，单独测取消尾延迟，超时隔离处理。

### IME 集成

- 声明 service、`android.permission.BIND_INPUT_METHOD`、`android.view.InputMethod` intent filter 和 `android.view.im` metadata。
- 用户从系统设置手动启用和选择输入法；提供设置入口，不尝试自动替用户切换。
- 预览 → 确认 → `InputConnection.commitText`；对 editor 支持良好的情况可用 composing text，取消清理 composing。
- 每次识别携带 editor/session ID，焦点变化或 `onFinishInput` 后不得把旧结果提交到新 App。
- 密码字段/敏感字段默认禁用语音或至少禁用持久化、history 和网络，首版采用禁用语音的保守策略。
- 提供切换键，避免只有语音输入导致用户无法输入符号或密码。

### 麦克风与 Android 生命周期

- `RECORD_AUDIO` 在可见 Activity 中解释并申请；IME 不应假定可像 Activity 一样弹授权流程。
- 默认仅用户点击后采集，隐藏/切换/取消时停止并释放 `AudioRecord`。
- 背景持续录音如需支持，必须遵守当前 target SDK 的 microphone foreground-service 类型、权限、启动限制和持续通知。IME 的特殊角色不能被解释成普遍后台开麦豁免。
- API 优先接收调用方提供的音频，不允许任意客户端要求本服务静默启动麦克风。
- 前台服务不能成为“无限常驻”的借口；HTTP 长时间服务需要专门验证合法服务类型、时限与用户可见会话。若新版本限制不允许，则限定为可见 App/有效绑定会话内服务，不错误声明 microphone 类型来托管无录音 HTTP。
- Android `RecognitionService` 是可选扩展，不是实现 IME 的必要条件；只有用户需要系统 `SpeechRecognizer` 集成时再开发并验证厂商兼容性。

## 8. API 设计与安全

### 8.1 AIDL：Android 应用内推理的主接口

概念接口（后续冻结为版本化 AIDL，不是现有实现）：

```text
getCapabilities() → apiVersion, modelRevision, languages, limits, backend
transcribe(audioFd, AudioSpec, Options, callback) → jobId
cancel(jobId)
callback.onPartial(jobId, sequence, revision, text)
callback.onFinal(jobId, text, language, metrics, truncated)
callback.onError(jobId, code, message)
```

- `AudioSpec` 显式声明 PCM 编码、采样率和声道；首版只保证 PCM16LE/WAV，其他格式由 App 导入层解码。
- export service 仅在需要跨应用时开启；发布策略为同签名权限默认允许，第三方经用户授权后以 UID/包名/签名记录 allowlist。
- 不将 signature-only 权限作为“开放第三方 API”的唯一方案；如启用第三方入口，所有方法都要统一鉴权，默认拒绝。
- Binder 调用时取得并验证 caller UID，异步前保存身份；包名不能由客户端自报充当凭证；校验 FD/大小/格式/时长与 per-UID 配额。
- 流式 pipe 使用边读边限额，防止永不 EOF 和录音洪泛；结果只回传原调用者。

### 8.2 可选本机 HTTP

- 绑定 `127.0.0.1`，默认关闭。开发机通过用户主动建立的 `adb forward` 访问；不默认 `0.0.0.0`。
- 路由：`GET /healthz`、`GET /v1/models`、`POST /v1/audio/transcriptions`、取消/状态接口；支持异步 job 或明确的超时，响应包含 model revision 与真实 backend。
- 初版兼容常见 transcription multipart 的子集：`file`、`language`、`response_format=json/text`。文档列明不支持的 OpenAI 字段，不自称完全兼容。
- 后续 SSE/WebSocket 的 partial 必须明确标记重算预览还是增量流式。
- 即使 loopback 也使用随机 bearer token、用户轮换/关闭、无宽松 CORS、浏览器 Origin 限制、请求体/时长限制和速率限制；本机其他 App 不是可信边界。
- 录音留在设备；默认不存原音，不记录转写正文到 logcat。临时文件在关闭/失败/重启恢复时清理。
- LAN 模式另行设计 TLS/身份验证与网络授权，不在首版默认开放。

### 8.3 服务端 API（可选后续）

如果“通过 API”还包括独立服务器部署，可增加 `server/`，用官方 qwen-asr/vLLM 提供 GPU 服务和流式能力，复用协议而不是把 Android native 后端强行搬到服务器。默认模型、上传授权、数据保存和网络费用由用户明确选择；本地失败不自动上传。

## 9. Nix Flake 开发环境规划

### 9.1 设计原则

- 首要宿主 `x86_64-linux`（当前是 Nix 环境）；目标 APK 是 Android `arm64-v8a`，两者不可混淆。
- 使用 `nixpkgs.androidenv.composeAndroidPackages` 组合 SDK；`flake.lock` 固定 nixpkgs，不使用浮动 latest 组件。[S7]
- **本轮仅交付设计，不创建一个未验证却声称可用的 flake。** P1 必须实际实现并执行开发环境 smoke test。

### 9.2 建议版本基线

| 组件 | 初始候选 |
|---|---|
| JDK | 17 |
| compileSdk / targetSdk | 35，实施时核对当期发布要求，必要时整体升级 |
| minSdk | 29，适配 Z1 初始 Android 10 级别；以手机实际版本验证 |
| Android Build Tools | 35.0.0 |
| AGP / Gradle Wrapper | 8.9.2 / 8.11.1 候选兼容组合 |
| NDK | r28 系列，实施时选 nixpkgs 可提供的精确版本并固定 |
| CMake / Ninja | 3.22.1 或经 AGP/MNN 验证的固定新版 / nixpkgs 锁定版 |
| Python | 3.12；Torch/qwen-asr/Transformers/ONNX/ModelScope 按同一转换环境锁文件固定 |
| MNN | P0 已验证 commit + 最小补丁集，不自动跟随 master |

这是待 smoke-test 的组合，不宣称当前仓库已经构建成功。NDK/AGP 需要同时检查 16KiB page-size 支持与所有第三方 `.so` 对齐，即使 Z1 本身通常不是 16KiB 页设备。

### 9.3 Flake 输出与环境

```text
nix develop .#android     # JDK、SDK、NDK、CMake、Ninja、adb、clang-format
nix develop .#model      # CPU 转换/参考推理、ModelScope、校验/基准工具
nix develop .#model-cuda # 可选开发机 GPU；与系统驱动匹配，不用于手机构建
nix flake check          # 格式、脚本/manifest schema、原生单测等可离线 checks
```

- `devShells.default = android`；导出 `JAVA_HOME`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`、`ANDROID_NDK_ROOT`。
- SDK 位于只读 Nix store；关闭 Gradle 自动下载 SDK 的隐式依赖。`local.properties` 由脚本生成并忽略，不提交用户绝对路径。
- AGP 从 SDK root 的 `ndk/<version>` 使用固定 NDK，CMake 使用 NDK toolchain；不能误链接宿主 glibc/系统库。
- `android_sdk.accept_license`/unfree 许可选项显式配置并在 README 说明法律前提，禁止 shellHook 静默执行批量接受许可证。
- SDK 工具在 NixOS 的动态链接问题通过 nixpkgs 包装或定点修复解决，不用全局宽泛 `LD_LIBRARY_PATH` 污染 Android 编译。
- Python 用 Nix 可构建依赖或锁定 uv 环境与受控运行包装；纯 NixOS 上不能假定 pip manylinux wheel 自动可运行，P1 必测 import 与最小转换。
- 模型/权重在 `models/` 或用户 cache，禁止放 Nix store 以免重复占磁盘；首次下载是显式命令，不发生在进入 shell 时。

### 9.4 “开发可复现”与“离线构建”分层

- 第一层：flake.lock + Gradle Wrapper checksum + version catalog + Gradle dependency verification + Python lock + MNN commit + model manifest，实现工具链和输入可追踪。
- 第二层：预取 Maven/Google/Python 依赖到受控缓存，验证断网 `./gradlew --offline assembleDebug`。
- 第三层：若要求 `nix build` 在 sandbox 内完全离线构建 APK，需要另外打包 Gradle 依赖闭包/固定输出 derivation；**`nix develop` 本身不保证这一点**。
- 模型 artifact 是否字节级可复现还受导出库、算子融合和量化实现影响，采用哈希记录与数值回归共同验证，不先承诺 bit-for-bit。

## 10. 建议项目结构

以下为后续实施结构，不表示本轮已创建：

```text
flake.nix / flake.lock
README.md / task_plan.md / findings.md / progress.md
docs/
  implementation-plan.md
  research/                 # 本轮小型文本证据和来源索引
  adr/                      # 框架/量化/服务生命周期决策
android/
  app/                      # Compose 配置、录音、文件转写、模型管理
  ime/                      # InputMethodService 和输入法 UI 库
  inference-service/        # 唯一模型进程、资源管理
  api-aidl/                 # AIDL、SDK、示例客户端
  api-http/                 # 可选 loopback adapter
  core/                     # shared contracts / lifecycle / settings
  gradle/libs.versions.toml
native/
  asr-engine/               # backend-independent C++ API
  backend-mnn/
  audio/                    # buffers / VAD / feature validation
  jni/
  tests/
third_party/                # 固定版本依赖和审核补丁，不存权重
model-tools/
  download.py / export.py / quantize.py / verify.py
  pyproject.toml / uv.lock
bench/
  datasets/manifest.json    # 公开或用户授权样本引用
  run-device.sh / report.py
models/                     # gitignored 原始/转换权重
scripts/
  bootstrap.sh / build-apk.sh / device-probe.sh
```

## 11. 验证体系与性能目标

### 11.1 分三类验证

**A. 模型正确性**

- 官方参考、MNN 浮点、MNN 混合量化使用同一音频/语言/prompt/解码参数。
- 首批 20 条 golden 样本，覆盖静音、0.2 秒、1/2/8/15/30 秒、跨 1 秒与 8 秒窗口、非整秒尾部、中文夹英文、重复词、数字、背景音乐。
- 对比 log-Mel 的 shape/max-abs/RMSE，encoder 的长度/相对 L2/余弦相似度，选定步 logits 与 top-k，最后看全文 CER/WER。
- 不盲设“所有浮点后端逐 bit 相同”；依据 FP32/FP16 参考重复运行建立容差，但窗口、mask、token 个数必须语义一致。
- 扩展至少 200 条独立评测样本、累计 ≥30 分钟；报告中文 CER、英文 WER，保留未归一化和规范化结果，不能通过删数字/标点藏掉错误。
- 浮点适配候选门槛：对 golden 不出现系统性退化，独立集 CER 绝对增幅 ≤0.5 个百分点；混合量化相对官方基准候选增幅 ≤1.5 个百分点，同时报告相对增幅与分组表现。这是工程门槛，语料确认后冻结。

**B. Android 功能/稳定性**

- 权限拒绝/撤销、输入法切换、焦点变化、密码框、旋转、后台限制、进程重建。
- API 未授权 UID、伪造包名、错误 token、超大 WAV、无结束 pipe、格式错误、队列满和取消。
- 下载中断、哈希错误、空间不足、模型升级回滚、离线启动。
- 100 次短句循环无崩溃、无麦克风泄漏、无旧结果误提交；进程内存不持续增长。

**C. 实机性能**

```text
T_total = T_load(冷态时) + T_resample/mel + T_encoder
        + T_prefill + T_decode + T_IPC/UI
RTF = 离线推理耗时 / 音频时长
结束延迟 = 停止说话时刻 → final 可提交时刻（包含 VAD 等待与排队）
```

- 单独记录冷加载、warm 首 token、最终结果、encoder/prefill/decode、取消延迟。
- token/s 只解释 decoder，不拿它冒充整体 ASR RTF。
- 测试 2/5/10/15/30 秒，1/2/3/4/8 线程、浮点/INT8/INT4、CPU/可用 GPU；先筛选主要因子再组合，避免无意义全排列。
- 每个候选先 5 次预热、≥30 次短句测量；最终入选配置扩大样本验证 P95。冷态区分进程冷启和系统页缓存冷态，不依赖 root drop_caches。
- Perfetto/simpleperf（权限允许时）、`dumpsys meminfo`、thermal、logcat 标记；权限不足报告受限项。
- 固定亮度、网络/飞行模式、充电状态、环境温度与音频输入；GPU 测量保留输入法/前台 UI 的真实渲染负载。
- 功耗优先外部功率计，设备电池统计只作估算；不把估算 mAh 当实验级能耗。

### 11.2 初始目标（全部未实测）

| 指标 | MVP / 优化目标 |
|---|---|
| 5–10 秒段 warm RTF | 优先争取 ≤0.7；持续运行应 <1 才具备实时追赶基础 |
| 20 分钟连续负载 | 不发生失控积压；后段若持续 RTF ≥1 则关闭预览并降级录后转写 |
| 常见 2–5 秒短句停止说话到 final | 争取 P50 ≤2 秒、P95 ≤4 秒，含 VAD 等待；不从 RTF 自动推导已达标 |
| 模型 cold load | 暂定 ≤5 秒目标，必须单独公布；IME 显示加载态 |
| 常规短句进程树峰值 PSS | ≤2.0 GiB 目标；>2.5 GiB 触发保护/复核 |
| GPU 默认启用 | 持续端到端收益 ≥15%，精度/内存/热量不超门槛 |
| 精度 | 见上文 CER/WER 双基准，噪声/数字组单列 |

若最终不达标，交付真实报告和已可用的录后转写模式，列出可优化瓶颈，再决定是否增加工程投入。**不保证天玑 1000+ 上必然达到上述交互指标。**

## 12. 实施阶段与交付门槛

以下是单名熟悉 Android/C++ 的开发者、可持续使用目标手机时的粗略工作量，不是承诺；环境/模型适配风险可能显著增加工期。

| 阶段 | 工作内容 | 可核验产物与门槛 | 估计 |
|---|---|---|---|
| P0 可行性/正确性 | 锁模型和 MNN；参考推理；修核 window/mask；设备探针；native CPU 原型 | 20 条 golden 对比、至少一条真机完整转写、阶段耗时/PSS 报告；失败先决策再堆 UI | 5–10 工作日 |
| P1 可复现工程 | 实现 flake、锁版本、Gradle/NDK/CMake skeleton、测试脚本 | 干净环境 `nix develop`、debug APK、native 单测、Python import/转换 smoke test；文档列明哪些联网 | 3–5 日，可与 P0 工具准备交错 |
| P2 原生资源优化 | INT8/INT4、线程、内存、warm load、VAD；实验 GPU | 独立 CER/WER、2/5/10/30 秒基准、20 分钟持续热测；选出默认 CPU 配置 | 5–10 日 |
| P3 App 与输入法 | 权限、模型管理、IME commit/cancel/focus、模型唯一服务 | 多个普通编辑器中可离线语音输入、错误恢复与权限测试、循环稳定测试 | 5–8 日 |
| P4 推理 API | AIDL SDK/示例、用户授权、loopback HTTP、配额/取消 | 独立示例 App 调用、adb-forward 请求、未授权/超限/死亡测试 | 3–5 日 |
| P5 发布验收 | 断网验证、签名/许可、安全、后台限制、设备报告 | release APK、模型 manifest、安装/启用说明、benchmark JSON/报告、已知限制 | 3–5 日 |
| P6 可选增强 | 稳定 partial、真流式、1.7B、服务端或 RecognitionService | 独立方案与新增基准，不混进 MVP 承诺 | 另估 |

总量约 24–43 工作日（约 5–9 周），依 P0 结论滚动修订。P0 需要的最小 Nix shell 是工具准备，不必等全部 P1 工程完成才能开始验证。

## 13. 风险与止损

| 风险 | 应对/停止条件 |
|---|---|
| MNN 导出语义差异 | 先查 attention window、padding、token 长度，修补且回归；无法限时解决则启动备选验证 |
| 手机浮点 encoder 太慢 | 分解卷积/attention 时间，等价分块和部分 INT8/GPU 试验；不改错语义换跑分 |
| INT4 准确率退化 | decoder INT8 或敏感层混精度，保存质量/速度 Pareto 表 |
| GPU 不可访问/更慢 | 默认 CPU，GPU 功能开关和异常回退，不依赖私有驱动绕过 |
| LMKD / 内存过峰 | 单模型、严格总 token/段长/队列上限、及时卸载、服务重启恢复 |
| 热降频造成积压 | 减线程、禁用 partial、降低并发、提示录后模式；不无限存音频 |
| 输入法后台/权限限制 | 真实固件测试、用户可见录音、焦点绑定、不滥用 FGS |
| 模型/依赖供应链 | 官方源、锁 revision、SHA-256、签名 manifest、审查模型代码与补丁 |
| 国内下载可用性波动 | 显式缓存/断点续传/手动 SAF 导入；镜像必须校验，不运行不明安装器 |

### 进入后续实施前需要的最小信息

1. 一台可通过 USB/ADB 调试的 iQOO Z1，或用户代跑 device-probe/benchmark 并回传匿名结果。
2. 当前系统版本、剩余存储，以及对默认中文/其他语言的优先级。
3. API 若必须跨局域网/独立服务器使用，需要明确这个额外边界。

没有设备仍可实现环境、参考转换与 Android skeleton，但**不能关闭性能阶段或宣称完成 Z1 优化**。

## 14. 本轮交付与来源

本轮实际交付：规划文档、README、持久工作记录、少量文本/源码证据快照。没有 `.apk`、`flake.lock`、转换权重或性能结果。

### 主要来源

- **[S1]** Qwen3-ASR 官方仓库：<https://github.com/QwenLM/Qwen3-ASR>
- **[S2]** 官方 ModelScope 0.6B：<https://modelscope.cn/models/Qwen/Qwen3-ASR-0.6B>
- **[S3]** 配置：<https://modelscope.cn/models/Qwen/Qwen3-ASR-0.6B/resolve/master/config.json>；processor：<https://modelscope.cn/models/Qwen/Qwen3-ASR-0.6B/resolve/master/preprocessor_config.json>
- **[S4]** 官方建模：<https://github.com/QwenLM/Qwen3-ASR/blob/main/qwen_asr/core/transformers_backend/modeling_qwen3_asr.py>
- **[S5]** MNN 固定源码：<https://github.com/alibaba/MNN/tree/a03b005cf6f888ebf092e4753840f935827f9c36/transformers/llm>；音频头文件：<https://github.com/alibaba/MNN/blob/a03b005cf6f888ebf092e4753840f935827f9c36/tools/audio/include/audio/audio.hpp>
- **[S6]** llama.cpp ASR 音频图：<https://github.com/ggml-org/llama.cpp/blob/5f436dddb440a288ee5611d7d1eca564a6aca9f4/tools/mtmd/models/qwen3a.cpp>
- **[S7]** nixpkgs Android 文档：<https://github.com/NixOS/nixpkgs/blob/nixos-unstable/doc/languages-frameworks/android.section.md>
- **[S8，访问超时，待实施重核]** Android IME：<https://developer.android.com/guide/topics/text/creating-input-method>
- **[S9，访问超时，待实施重核]** FGS 类型与限制：<https://developer.android.com/develop/background-work/services/fgs/service-types>

初始部分资料抓取来自浮动分支，不能当作正式依赖锁；本地证据 SHA-256 见 `research/source-manifest.json`，成功抓取/失败范围与关键发现见 `../findings.md`。框架存在某算子的源码不代表该算子在这台 GPU 上已经运行通过。
