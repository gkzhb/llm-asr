# Progress Log

## Session: 规划交付
### Phase 1 — in_progress
- 已检查现有规划文件（不存在）、运行 session-catchup（无输出）、检查 Git 状态（干净）。
- 已读取 skill 模板，在项目根目录创建 task_plan.md / findings.md / progress.md。
- 下一步：获取官方模型、推理框架和硬件资料，记录可用证据与未知事项。

## Verification
| Check | Result |
|---|---|
| 上下文恢复 | 无旧规划，无 catchup 内容 |
| 仓库基线 | 无应用代码；无用户修改 |
| 实机、构建、性能测试 | 未执行；本轮为规划 |

## Errors
无。

### Phase 1 — complete
- 已获取 Qwen 官方模型卡、配置、预处理参数和建模代码；未下载权重。
- 已核验 MNN 专用 ASR 导出/运行支持，并发现 attention-window 对齐风险；llama.cpp 保留为备选而非已验证替代品。
- 已获取 nixpkgs Android SDK 组合文档；Android/硬件部分页面访问失败，错误记录在 task_plan.md，未重复盲猜。
- 已推导正确 KV 预算：0.6B FP16 每 token 112 KiB。

### Phase 2 — complete
- 主路线：先官方基准、MNN 数值对齐与设备 CPU 原型，再量化/试验 GPU、最后封装 App/IME/API。
- 尚未导出模型、构建 APK、运行 flake 或实机性能测试。


### Phase 3 — complete
- 写入 README.md 与 516 行详细 docs/implementation-plan.md。
- 创建 docs/research/source-manifest.json，记录 22 份文本证据的来源、SHA-256、保存时间与失败来源；无模型权重。
- 检查通过：22 个哈希、4 份 JSON、本地 Markdown 链接、KV=112 KiB/token、11 个需求标记、代码围栏配对、README/主规划空白检查。
- 人工复核：明确浮动分支与固定 commit 边界，性能目标不伪装实测，分段不伪装真流式，开发 shell 不伪装完全离线构建。
- 未执行：nix develop/flake check、Gradle/NDK build、模型导出/量化、APK 安装、手机 benchmark。
- 剩余风险：MNN encoder window 对齐；GPU/固件能力；2GiB/延迟目标实际可达性；具体 SDK 组合需 smoke test。

## Reboot check
| Question | Answer |
|---|---|
| 当前阶段 | 本轮规划交付完成 |
| 后续方向 | 详细规划 P0：官方基准、模型转换正确性、真机 CPU 推理 |
| 目标 | Qwen3-ASR Android App/IME/API + Z1 优化 + Nix 环境 |
| 已知事实 | 见 findings.md 与 source-manifest.json |
| 本轮工作 | 文档/证据核验，未实现应用或测量性能 |

## Session: 开始实施 / 远程 ADB
- 用户指定通过 Nix Flake 提供 adb，再连接 100.64.0.3，手机授权由用户操作。
- 已恢复规划；当前主机 x86_64-linux、Nix 2.34.8、可用磁盘约 312GiB，有本机 nixpkgs registry 缓存。
- 尚未连接设备；先添加最小 Flake，暂不拉完整 SDK。
- 新增 flake.nix：devShells.default/adb、adb app、android-tools package 与 adb-version check；不自动连接手机。
- 新增 .gitignore 保护 adb key 与未来构建/模型文件，新增 docs/remote-adb.md 并更新 README。
- 本机 registry 仅提供无 upstream revision 的 path，不能用于可移植锁；改用显式 github:NixOS/nixpkgs/nixos-unstable 并生成 lock。
- 已启动后台 Nix 锁定/依赖实现/检查任务 b49f21f18；日志 .pi/tasks/session-525127-525127/b49f21f18.output。完成通知后执行 adb connect；当前尚未连接手机。
- 后台任务 b49f21f18 失败原因已确认：packages.${system} 两次动态属性定义。已合并为单属性集，不重复原命令而不修复。
- flake.lock 已成功生成，nixpkgs revision eaad089433ca2bb662274377d33df3d0e51ef28b；失败发生在 devShell 评估，尚未启动 adb 或连接设备。
- android-tools 37.0.0 / ADB 1.0.41 已由 Nix devShell 成功执行。
- b102e3ecb 的总退出码 0 不代表检查成功：日志显示 fish 的 set -e 错误、Nix check 沙箱 HOME 创建失败、5555 被拒绝。已修正 check 的 HOME，后续显式 Bash fail-fast。
- 用户指定端口 33317，已执行 adb connect 100.64.0.3:33317，返回 failed to connect；adb devices -l 为空，尚未完成授权或连接。

- TCP 33317 可达，但首次 ADB 未配对而失败；用户提供配对端口后成功 pair，未将配对码写入项目。
- adb connect 100.64.0.3:33317 成功；devices -l 状态 device；只读 shell 返回 vivo / V1986A / Android 12 / API 31 / arm64-v8a。
- 已更新 docs/remote-adb.md。启动 b932211e0 检查修正后的 Nix check 与 adb app；日志 .pi/tasks/session-525127-525127/b932211e0.output。未安装 APK、读取个人数据或修改手机设置。

### Phase 4 — complete
- b932211e0 显式 Bash fail-fast 任务成功，日志确认 nix flake check：all checks passed；adb app 输出 1.0.41 / android-tools 37.0.0。
- 最小 Nix ADB 环境、无线配对、远程连接和只读系统信息确认均完成。
- 后续仍需完整 Android SDK/NDK、模型正确性验证、设备能力探测与真实推理基准；不将本阶段完成视为 App 或性能优化完成。

## Session: 自主推进 P0
- 用户要求自行完成后续 P0；已恢复全部规划和现有 flake。
- 主机只有 7.7GiB RAM / 4.3GiB available，swap 8.2GiB，磁盘剩余约 311GiB；需要模型加载/转换串行与低并发编译。
- 本阶段允许官方模型/工具链下载和手机专用目录的 native 测试，不进行私有内容采集或系统调优写入。
- 设备探针通过，已落盘报告；模型锁与校验下载脚本已创建。
- 活跃任务：bbdfb2283 模型下载校验；b65157c03 model shell；b5c942bf6 固定 framework source archives；5dc67a9c 源码独立审查。各日志见 .pi/tasks 或 reports/p0/source-review.md。
- 独立审查已归档，新增5项无权重 contract 测试通过。
- 准备受控 scripts/build-mnn-p0.sh：host MNNConvert/llm_demo、Android CPU llm_demo，固定 commit，编译并发2，关闭 HTTP resource/GPU。
- Nix model/native shell 和锁定 Python 安装均为后台任务；不会同时启动 reference 与导出大模型。
- 创建20-case公开音频衍生 smoke 语料生成器（明确仅两条独立来源，无人工标注，不冒充独立准确率语料）、官方 eager reference runner、只载入encoder的数值比较脚本；尚未执行模型计算。
- 参数 header 统计表明完整 checkpoint 共938,008,576参数。后续内存预算必须按实际参数/权重而非0.6B名称计算。
- Python依赖锁已生成，118包安装成功；发现no-AVX主机使可选nagisa/dynet导入SIGILL，补丁只延后日语对齐依赖导入。MNN wheel可执行栈标记已清理，独立import成功。
- Qwen import重新验证在后台执行，faulthandler显示先前短超时卡于磁盘读取torch模块，不是再次SIGILL；减少竞争并给有界合理超时。
- b39bd834b：Qwen import → 公开smoke语料 → 原始encoder边界数值基线。尚未应用MNN补丁，保证先保留原始结果。
- b39bd834b QWEN_IMPORT_OK，lazy nagisa补丁有效；随后公开英文采样率不符使语料生成安全失败，未进入encoder测试。已加入可重现重采样并记录来源归一化。
- 发现英文官方样本实际为48kHz PCM24，改用soundfile+scipy polyphase归一化，保留源SHA；尚未声称独立20条golden质量评测通过。
- reports/p0/status.md记录当前验证/待办/限制；docs/remote-adb.md已改用tiny-mirror包装器，防止后续root path flake复制GB级权重。
- 原始encoder数值基线已完成：20帧短块relative L2≈23.43%，99/100/101/800/801帧误差0；已应用并排队验证窄补丁，不改官方eager全局attention语义。
- 添加专用native ASR harness（无llm_demo自动调优、明确失败码）、交叉编译脚本、受限目录设备运行/内存采样脚本。
- host converter使用与Android相同MNN commit源码构建（b88941796），避免3.6.1 wheel转换器与新图算子不匹配；导出脚本设Python encoder/prompt通过门槛。
- 当前主要活跃工作：b83d197aa NDK+Android编译，b7fec9f0a 补丁encoder验证→2条官方转写参考。b88941796 host converter已主动停止以缓解磁盘争用，保留增量产物，后续串行恢复。
- Chinese/English/automatic提示词token对照全部通过；原始encoder20帧差异已复现，补丁后数值结果尚待任务完成。
- P0仍未结束：未导出MNN模型、未跑设备完整转写/峰值PSS、无独立20条人工golden评测。后续按任务完成通知继续，无需用户逐步确认。
- 已收到 bbdfb2283 正式完成通知（exit0）；官方ModelScope所有锁定文件均下载并通过大小/SHA256校验，模型下载阶段结束。
- b7fec9f0a tokenizer提示通用Mistral regex警告；不照搬跨模型修复参数，先保留官方Qwen tokenizer语义，后续检查实际regex与原生tokenizer一致性。Chinese/English/auto prompt token对照已通过。
- 已收到 b5c942bf6 正式完成通知（exit0）：固定commit的MNN/Qwen官方源码归档获取成功；archive SHA256已保存 reports/p0/source-archive-sha256.txt 和 model-tools/source-lock.json。该通知不代表原生构建完成。
- 补丁可重现性检查通过：在临时目录从两份原始MNN快照运行 patch_mnn.py，生成diff与 patches/mnn-p0.patch 逐字节一致；未修改正在测试的源文件。
- 收到 b65157c03 正式完成通知（exit0），确认此前已检查的 model devShell 工具验证：Python3.12.14、uv0.12.11、CMake4.4.2、Ninja1.13.2。此任务仅验证开发工具，不等同于后续Python wheel兼容、模型数值或Android构建通过；相关兼容补丁与独立结果已另行记录。
- 收到 ba82963ed 正式完成通知（exit0）：20-case工程smoke语料生成与原始encoder数值比较完成。结果此前已检查并用于生成窄补丁：T20 relative L2≈0.2343，T99/100/101/800/801 max_abs=0。该成功表示测试执行完整，不表示原始MNN实现正确性通过。
- 检查 reports/p0/audio-parity-patched.json：六种长度全部max_abs=0，短块修复有效。后续仍需导出图动态长度验证及完整ASR参考/设备运行。
- 收到 b88941796 killed 通知：这是之前为缓解IO争用主动停止的host converter构建，无编译失败结论；增量构建目录保留。检查替代主线日志：b83d197aa已完成NDK准备并进入Android MNN编译（581项中的前35项），仍未完成。host converter待Android构建结束后串行恢复。
- b83d197aa正式完成：NDK28.2与Android MNN libMNN.so/llm_demo 581项全部编译链接成功。已启动bf7778cb7恢复host converter增量构建。
- 专用harness短shell执行超过60s，尚无产物，改后台有界执行。
- reference-initial.jsonl已出现两条真实官方CPU转写结果：中文“甚至出现交易几乎停滞的情况。”及英文完整结果。主机峰值RSS≈3.77GiB，受no-AVX与IO影响的主机耗时不能作为手机性能。
- b390cfe90失败发生在ADB设备断连，不是编译：p0_asr/p0_capabilities/libMNN.so均编译成功并已记录SHA。
- 对原地址33317显式重连成功，三份产物推送至 /data/local/tmp/qwen-asr-p0；native probe执行成功。
- native probe: page_size4096，HWCAP0x119fff，NEON/FP16/dotprod均true，OpenCL platform query成功（ARM Platform）。仅ADB shell进程，不声称普通APK或MNN GPU可用；未开始完整模型推理。
- 用户要求继续尝试；新增b7448a1ce音频ONNX动态图门槛任务，先仅加载encoder，Torch导出和ORT分进程串行。
- 专用native harness强化错误判定：配置失败、TIMEOUT/CANCEL/未完成均非成功；MAX_TOKENS返回truncated并非零退出码，避免将截断当完整转写。须在下一次部署前重编译此更新。
- 收到 b7fec9f0a 正式完成通知（exit0）：三种prompt token对齐、补丁encoder六种长度FP32对齐、两条官方CPU参考转写均完成。结果已分别保存在 prompt-parity.json、audio-parity-patched.json、reference-initial.jsonl。后续ONNX/MNN图与手机完整转写仍未通过，P0继续。

- b7448a1ce完成（exit0），真实encoder ONNX动态图验证通过：ONNX gate passed: 7 sequential shape checks; max_abs=7.59959221e-07; max_relative_l2=2.84813791e-06。同一个ORT session依次切换长度，短块修复未被trace冻结。尚未完成MNN转换/设备转写。
- bf7778cb7正式完成(exit0)：host匹配MNN源码的794项构建全部完成，MNNConvert/依赖库/llm_demo已链接。
- 已启动b7a3bf988完整ASR浮点权重转换，入口先核查Python/ONNX/prompt证据完整性，HF/Transformers offline模式读取锁定本地模型，使用匹配commit的MNNConvert。日志 .pi/tasks/session-525127-525127/b7a3bf988.output。
- b4df85e55完成(exit0)：带严格状态/截断检查的p0_asr重新编译通过；.work/bin及reports/p0/native-artifact-sha256.txt已更新。手机上仍是之前版本，须在模型测试前重新推送新产物。
- 新harness部署尝试经Nix启动超过65s未返回输出；当前仅确定本地构建成功，不确定远端更新。改为后台有界部署并校验远端SHA。
- b5188cecb超时未输出；诊断主机memory/IO PSI full约70%，转换进程RSS约4GiB，严重换页。主动停止b7a3bf988，不声称转换完成。
- 直接使用已由项目Nix提供的adb路径，手机仍已连接；strict p0_asr推送成功，本地/远端SHA均1e0bb2c4699dcf5237848bcdc44f2cd26ea9f274af162139d114fca6f9634902。
- 新任务b4b058741在独立native进程转换已验证audio.onnx，FP16存储、无transformer fusion；不加载完整PyTorch/decoder。
- b4b058741完成exit0，日志Converted Success：audio.mnn约2.9MiB，外置FP16权重约356MiB。实际计算正确性尚待验证。
- 启动b3819ecc8匹配commit native MNN audio运行验证；同session输入多种长度，与已保存official FP32数组比较。预设FP16存储误差门槛max_abs≤0.002、relativeL2≤0.02；不将其等同于任务级CER。
- b3819ecc8完成exit0：匹配commit MNN CPU实际运行audio.mnn，六种动态长度形状正确、finite、全部通过预设FP16存储门槛；最大max_abs≈0.000432，relativeL2≈0.002590。
- 新增export_decoder.py并启动b302c2647：捕获audio配置后释放encoder，仅导出decoder/tokenizer/config，保留ONNX证据，不调用会重新trace audio的全模型export流程；校验原audio模型前后hash不变。
- b302c2647失败在脚本末尾硬编码tokenizer.mtok；日志与文件确认decoder/audio权重、tokenizer.txt和config均已生成。修正为按config中的tokenizer_file核验，不重跑导出。
- 新b56640af2执行finalize-existing（部署清单/SHA）→专用host harness构建→中文完整MNN转写，严格错误/截断判定，结果reports/p0/host-mnn-transcription.txt。
- b56640af2完成exit0：部署产物清单/哈希生成；host MNN完整中文转写“甚至出现交易几乎停滞的情况。”与官方参考一致，status1正常结束、truncated=false。host load16.34s、inference6.60s，仅是主机结果。
- 启动b1a7de226手机完整链路：检查空间、按manifest逐文件核验本地/远端SHA、推送native runtime与公开音频、执行有界推理并采集自身PID内存。远端只写/data/local/tmp/qwen-asr-p0。
- b1a7de226未进入推理：audio图/权重及llm图远端SHA通过；llm.mnn.weight(1192493056 bytes) push900s超时。手机仍device且约30GiB可用，失败后完整weight不存在。
- deploy脚本改为大文件16MiB块传输(-Z)，逐块/最终hash核验，已验证整文件跳过；保留块以便续传，空间预检覆盖两份副本。下一次继续部署后才运行原生转写。
- b9f6a13f9全部部署文件SHA核验成功后采集脚本exit124；不是传输或模型失败。手机p0_asr已退出，远端输出显示完整正确中文、status1/truncated=false。
- 已保留recovered-output/memory及first-device-result.json：load14.7791s，inference4.26394s，音频4.20394s，RTF≈1.0143，采样峰值PSS3226431KiB≈3.077GiB。shell退出码未可靠捕获；分项计时存在重叠，不可相加。
- 修改采集脚本：后台stdin关闭、移除独立540秒watchdog，使用采集循环有界终止该测试PID；下一步复测收尾，复用已部署模型，不重传。

- b7e90952c完成exit0，远端EXIT=0且status1、未截断，文本与官方一致。第二次独立启动load14.9002s、推理3.78953s、RTF≈0.9014、采样PSS峰值3213372KiB≈3.0645GiB。采集收尾修正有效；不是persistent warm测量。
- 单条实机完整转写P0子门槛通过，但完整20case回归/覆盖与独立审查仍待完成，内存/加载目标未达标。
- 已启动b4769969e完整20-case官方eager参考，串行单模型，输出reference-smoke-20.jsonl；两条独立音频衍生smoke语料不能冒充20条独立人工golden或任务级准确率。
- b4769969e正式完成exit0：20case参考记录ID与manifest完全一致，累计host推理约300.6s；保留全部输出。
- 官方三条纯静音均输出“嗯。”，产品需VAD/静音门控；不要当作转换差异。reference runner未暴露终止token，长样本参考是否触及max_tokens仍未判定。
- 新b42a90a14串行执行20case MNN host对照：逐样本语言配置、严格exit/status/truncation、原始日志、文本差异；度量是backend agreement而非人工CER或手机性能。
- b42a90a14完成exit0：20/20正常结束、16/20逐字匹配。差异en-original/en-2s/zh-en/zh-480000；这表示测试执行完成，不表示一致性全部通过。
- 源码发现LlmConfig构造时在用户config之后merge llm_config，可能覆盖per-case asr_language。harness改为createLLM后显式set_config语言并输出effective config；旧20case证据保留。
- 新b0785c794重编host harness并定向复测4个差异case，独立报告tag language-fixed-subset；不重导出/修改模型权重。
- b0785c794完成：4条均complete，en-2s修复后exact；en-original只剩标点/大小写2字符差异；30秒多重复句及自动语言内容差异仍存在。
- 源码核查官方reference包含parse_asr_output（含语言元数据移除/重复处理），之前native raw与parsed reference比较口径不同。已加载锁定版本的纯文本parser重评分，保留raw与解析后文本，不改模型输出。
- b9c6d44f6失败于自写feature_gate.cpp编译，VARP没有bool转换；尚未产生特征数值证据。修复两处空值判断为==nullptr，重新运行该有界测试。
- b9fab6072正常执行比较后exit1：5种真实音频shape一致，但max_abs0.028–0.234、relativeL2约0.2–0.4%，超过原门槛。是可复现数值失败，不再是编译错误。
- Hann假设试验完成后应用窄补丁；原前端失败报告备份为feature-parity-before-hann.json。下一步host增量重编→特征门槛→4差异样本回归；保留失败，不放宽门槛。
- bd911ddf1完成exit0，但内部特征门槛exit1；结果备份feature-parity-periodic-hann.json。ASR差异subset仍1/4 exact，raw/解析口径已知，剩余实质差异未解决。
- 新增isolate_frontend.py：同一官方模型串行跑原特征与MNN特征，保存token IDs/结束原因；尚未运行结论。
- b0474b96c完成exit0：en-original/zh-480000/zh-en三样本在官方FP32 eager中仅替换MNN周期窗mel，生成token IDs与原特征完全一致；全部EOS结束，分别45/50/54 tokens，未触及128上限。
- 该实验排除“残余mel差异单独改变这些样本输出”及“官方参考被max_tokens截断”的解释，但不排除与量化/引擎误差交互。
- 下一单变量：官方参数逐个FP32->FP16->FP32，只模拟存储舍入（缓冲区/计算/前端不变），检查权重变化及3样本token；不等同于完整MNN格式/算子模拟。
- b1970a194存储舍入隔离无文本/token变化。已启动ba57ac232：先native encoder生成6份embedding，再单官方模型串行注入测试；避免两个完整模型驻留。
- ba57ac232完成exit0，6组encoder注入全部same_text/same_ids=true。不能据此声称所有encoder场景等价；剩余3差异的排查范围移至原生文本输入/位置/decoder。
- 启动ba77f7882构建native prompt probe并对3样本完整token序列核验；不改变模型或baseline报告。
- 原生prompt全token对照3/3通过。下一步host位置trace+assert顺序位置序列，测试可红；安装临时instrument_positions.py，需remove清理。
- bea12dbd8实际位置断言exit1：首次decode42 vs44，全部后续少2；编译/推理本身成功。已实施ASR-only边界计数补丁，不改权重。
- b0ab92a26完成exit0：9组原始位置断言全部green；4case均complete，其中3个raw exact，中英auto仅language元数据前缀不同，正文一致。ASR边界位置修补解决本轮实质文本差异。
- 已移除临时P0_POSITION_TRACE代码；compare_smoke改用锁定官方parser统一口径并保留raw，接下来无trace全量20case新跑，不再使用合并旧结果声称通过。
- 启动ba6407d75：无trace运行库增量构建→全20case fresh回归；保存final-position-fix标签结果。
- 启动独立read-only review workflow81670dc0，检查边界位置/periodic Hann补丁、部署/采样脚本与P0证据边界；不运行模型、不修改源码。手机仍为旧版runtime，必须Android重编复测后才归结新补丁实机成绩。
- 独立review中间反馈已落实：拒绝model路径..和远端symlink逃逸；remote run增加信号trap与TERM/KILL升级；position断言要求prefill+至少2连续decode，不能用prefill-only误通过。
- 验证：原9行真实position trace通过、prefill-only临时fixture被拒绝、路径遍历在调用adb前拒绝、bash语法通过；信号清理仍待新实机复测。
- 独立review正式报告归档reports/p0/implementation-review.md：认可窄native可行性，不认可完整P0关闭；位置补丁算术无阻塞。处理中间反馈后新增terminal/malformed-line位置检查，真实trace仍通过。
- 当前20份reference audio SHA逐项核对manifest通过；未发现现有陈旧输入，但评分器仍需强制身份校验。
- README/规划页已澄清历史规划和当前原生实测，避免“未下载/未实测”的旧状态误导；仍无APK，旧实机性能不归因最终补丁。
- ba6407d75正式完成exit0：fresh无trace全20case，20complete/20exact（统一官方parser、raw保留）。非合并旧结果，仍非独立人工准确率。
- 最终Android复测前备份旧device-inference与native hash；新增remote运行锁防止采集并发覆盖，锁存在时fail closed不自动删除。
- 评分器增加reference waveform SHA校验和未来run provenance；本轮已完成20/20报告保留，不追溯声称运行时已有新增检查。

- b720064df完成exit0：最终Hann/position补丁Android重新编译、部署SHA通过、collector正常退出，锁正常收尾。final-device-result.json：load11.6248s、infer4.72811s、RTF1.12469、采样PSS3.0604GiB，文本正确未截断。
- 已交付reports/p0/report.md并刷新status.md；明确原生可行性/工程一致性通过，但完整golden覆盖/严格frontend/窗口语义与产品性能仍未全部关闭；没有APK。

## Session: 继续生成最小 APK
- 恢复三份规划文件并执行 session-catchup（无待同步输出）；目前没有 Android 应用代码/APK，已有最终 MNN Android runtime 与实机 native 成功证据。
- git status 全为项目未跟踪文件；不替用户暂存/提交。
- 新增 Phase 6：Java/JNI 最小离线 APK、独立模型导入、公开音频测试；IME/API/量化不属于本轮。
- 已添加最小 Android manifest、Java Activity、严格有界 WAV 校验/规范化、协议显示解析、JNI 单请求 CPU 引擎（只接受正常 EOS，拒绝截断），全部仍待编译。
- 导入采用 SAF 用户选择目录，仅固定清单7文件，逐个大小/SHA校验与 .part 原子替换；无网络/录音/存储广泛权限，无导出推理入口。
- SDK/JDK环境正在后台实现 b7d3d175f；APK构建使用官方工具链直接打包，不新增Gradle在线依赖。
- 首次APK环境任务未执行Nix：既有 scripts/nix-env.sh 无执行位；已修正，改显式 bash 入口继续。没有将工具启动失败当 SDK 构建失败。
- 添加构建/Java测试/受限APK部署脚本。部署可在手机内将已验证P0模型复制到本app UID私有目录，避免再次无线传输1.6GB；逐文件重新SHA核验。
- 可选 smoke 只点击本app可见的内置公开中文示例，验证真实普通app UID、正文、非截断；不绕过锁屏，不自动授权麦克风。
- 独立只读审查 f6c1f4b0 已启动，报告目标 reports/apk/independent-review.md。
- 新增 docs/minimal-apk.md，并修正 README 仍称“最终native回归未完成”的历史过期状态；APK文档明确为开发包、未验证前不宣称安装/推理成功。
- manifest XML 与 shell/Python脚本语法检查通过；Java/JNI真实编译待SDK依赖下载。没有为了最小包而省略真实MNN推理。
- 实机连接检查：adb devices为空；原授权100.64.0.3:33317重连返回Connection refused。未安装APK、未读取个人数据；已告知用户恢复无线调试/提供新端口，构建不因此阻塞。
- JNI先行交叉编译/链接成功（独立复用已实现NDK，无需等待JDK）；NEEDED仅libMNN/log/dl/m/c，无缺失libc++_shared依赖。此为JNI编译证据，不是APK运行证据。
- 独立审查已完成，提示Activity多实例并发与stale result风险；正在获取完整报告并修正，不将审查完成当通过。
- 审查修正已应用：process-wide任务所有权、重建UI重连、UUID+pending/terminal报告、目录枚举最多10000且只保留7个清单名称、JNI字符串RAII/异常保护/stream生命周期。
- F2采用单DSO方案，成功链接最终P0的578个现有对象+JNI（SINGLE_DSO_LINK_OK），逐对象SHA报告已生成；保留原P0运行库未修改。
- 新增MNN许可证和来源/公开示例用途通知随APK打包；仅本地开发测试，未声称公开示例具独立再分发许可。
- 尝试直接运行已出现路径的JDK失败（libjli.so尚缺）；该store仍在Nix实现中，未判定Java源失败。reports/apk/java-tests.txt尚无通过结果，后台构建待环境完成。
- 增加WAV 0.1/30秒边界、越界与奇数字节chunk测试；增加签名APK ZIP精确校验（唯一JNI单库、无权重/中间文件、清单/示例SHA、API/ABI/零权限），APK_READY前必须全部通过。
- 当前后台主线 b0c0c6b1f（.pi/tasks/session-525127-525127/b0c0c6b1f.output）正在完成SDK35/build-tools/JDK17依赖实现，随后自动执行Java tests→单DSO链接→APK签名/静态包验证。尚无APK_READY，不宣称APK已生成。
- 独立审查原文及逐项处置已归档 reports/apk/independent-review.md / review-disposition.md；原P0库不修改，新链接产品需APK实机验证。
- 目前实机阶段唯一已知外部阻塞是原无线ADB端口Connection refused；构建不依赖设备，完成通知后继续核验产物。
- b0c0c6b1f完成失败exit3，准确阶段：SDK/JDK成功、21 Java checks通过、P0库SHA通过、578对象单DSO链接通过；javac Activity失败于LambdaMetafactory.metafactory缺失。
- 反馈loop：一行Lambda在Android bootclasspath下稳定exit3（reports/apk/lambda-probe-red.txt）；单变量改--release 8 + Android classpath后最小例和真实全部Java代码编译通过。
- scripts/build-minimal-apk.sh已修编译参数，保持Java8字节码并由d8去糖；接下来重跑完整签名/包校验，未把Java编译成功当APK生成。
- 新任务 bbe10cd9b 运行完整构建，日志 .pi/tasks/session-525127-525127/bbe10cd9b.output。包检查排除ZIP目录项，只对实际native文件强制单DSO集合，避免把lib/目录误判额外库。
- bbe10cd9b失败仅在末尾静态检查字段名：实际aapt2输出minSdkVersion:'29'而非sdkVersion:'29'。Java21项、JNI链接、DEX、APK打包、签名验证此前均完成。
- 修正为精确minSdkVersion:'29'断言后，现有签名APK的单DSO、嵌入资产、无模型权重、零权限、API29/35、ABI及ZIP完整性检查全部通过。
- 最小签名APK已交付 dist/qwen-asr-minimal-debug.apk，2,380,584 bytes，SHA256 ff432527ad62e696208f3311804265da3a0369c5ffa9c1c36dda6478073b6877。签名v3有效、min29/target35/arm64、零权限、21Java测试与静态包检查通过。
- reports/apk/status.md / result.json记录产物与界限；README及Phase6更新。原任务exit1日志保留，修正后静态验收独立记载，不把历史失败改写为exit0。
- 安装、APK UID推理与生命周期/重复请求测试仍blocked于设备端口拒绝连接；未声明产品/完整P0质量通过。等待用户恢复无线ADB，不再盲重连。

## Session: 用户骁龙验证成功 / 首版提交
- 用户明确反馈在骁龙SoC手机上APK可以加载模型且正确输出，并要求提交当前代码、禁止提交模型。
- 作为user-reported手动功能验证记录；未提供具体SoC/Android版本、运行日志、APK哈希核对或性能数据，不推断这些信息。
- 恢复规划并执行catchup，无额外恢复输出。Git为unborn main，222个候选文件、无已暂存变更；models/.work/.cache/dist等已忽略。
- 提交前检查通过：21项Java测试、5项P0契约测试、APK静态包检查、Python AST与shell语法、核心代码git diff --cached --check。
- 显式暂存源码/配置/锁文件/文档/文本证据，223个文件约5.91MB；逐Git blob审计无模型/权重、二进制、符号链接、密钥扩展、明显凭据模式或>5MiB单文件。
- git check-ignore确认原模型、模型传输副本、APK及debug keystore均被忽略；不删除任何本地模型/产物。仓库只包含模型版本/哈希清单和转换代码，不包含模型实体。即将创建本地初始提交，不push。
- 本地初始功能检查点已创建，提交树223文件审计通过，无模型实体/二进制/签名凭据；未push。将本条提交完成记录与Phase7完成状态纳入同一个尚未推送的本轮提交（仅amend自己刚创建的提交）。

## Session: 通用SoC路线与App功能完善
- 基线89d244a，工作区干净；用户调整目标：暂不针对天玑SoC做特性优化，先广泛SoC支持、完善App。
- 第一可交付增量定为前台录音→停止→转写；继续使用标准Android AudioRecord、MNN CPU，不加入厂商SDK/云端回退。
- 先修订路线与验收边界，再实现与构建；用户骁龙成功反馈仍只归因0.1版，不冒充新录音功能已实测。
- 已写docs/app-roadmap.md并更新旧规划优先级；实现0.2前台AudioRecord采集/权限/停止/取消/后台自动丢弃，模型与native路径不变。
- 保留0.1 APK于dist/v0.1（ignored）及历史报告reports/apk/v0.1，不把用户的0.1成功归因新版本。
- 新增纯Java PCM边界与取消优先测试，32项通过。真实AudioRecord/权限UI/设备生命周期尚未测试。
- 主构建b44376ae9：Java32项→native/DEX/签名/精确RECORD_AUDIO白名单，日志.pi/tasks/session-525127-525127/b44376ae9.output。
- 独立只读录音审查workflow3d50f2ab已启动；无设备录音访问。docs/minimal-apk.md补覆盖安装与权限/取消/后台/30秒/重复请求手动验收清单。
- b44376ae9日志已出现APK_READY：0.2全构建/签名/包检查通过，RECORD_AUDIO为唯一权限，32Java+5契约测试通过。
- 比对native DSO SHA与0.1完全一致；App新增录音没有改MNN数学/模型/厂商路径。当前result/status更新为0.2，不再混用旧0.1哈希与用户反馈。
- 独立录音审查仍pending，真实设备权限/采集/生命周期未运行；本轮未自动访问手机麦克风、未提交或推送更改。
- 独立录音审查d5741343 request-changes：R1 start/cancel、R2 handoff/cancel、R3返回键双读竞态。原报告归档reports/apk/recording-review.md。
- 已将审查未通过的0.2 APK移至dist/review-rejected-v0.2，不继续把它作为可验收录音版本；保留0.1。
- 将实现同一session生命周期gate和确定性latch回归；无设备麦克风访问。
- 已修R1/R2/R3并新增20项latch/fake-backend生命周期gate测试，连同原32项Java测试全部通过；仅覆盖会话次序，不声称真实AudioRecord/权限/硬件延迟通过。
- 包检查新增versionCode2/versionName0.2断言，文档明确cancel/commit先后语义、异步硬件释放和系统返回静音的限制；准备重新构建与独立复核。
- 修复版后台构建b83c3f5b0与独立复核da49b719均已启动。等待自动完成通知，不自动采集麦克风或提交代码。
- result/status明确原0.2属于审查拒绝旧产物，避免旧哈希或“构建通过”被误用为录音可验收证据。
- b83c3f5b0正式完成exit0：修复版0.2 APK_READY，32 helper+20 lifecycle gate测试通过，单DSO/版本/签名/权限校验通过。当前dist主路径已是修复版，result.json更新新SHA；独立复核尚待完成，不声称设备录音验证。
- 独立复核b3c4efaf完成：R1/R2/R3源码阻塞已解决，未发现新阻塞；独立复跑32+20 host检查通过。报告已归档reports/apk/recording-fix-review.md。
- 比对审查指纹、当前源码和APK build-input全部一致，APK SHA核对通过；result/status更新为build+focused-review passed / ready for manual device testing。
- Phase8本轮实现与构建/复核交付完成。仍待真实手机麦克风权限/生命周期/释放延迟验收；硬件start调用可能延迟UI、进程kill留临时WAV等限制保留。未提交、未push、未访问手机麦克风。

## Session: 0.2用户验收与继续路线B
- 用户反馈“测试没有问题”，明确要求提交并继续；记录为0.2手动反馈，不扩大为逐项硬件/所有SoC验收。
- 先提交当前已审查录音版，保持模型/APK/缓存/密钥不入Git、不push；之后独立推进0.3模型状态/结果管理和临时文件清理。
- 0.2本地检查点已提交a4c6a65（feat: add foreground recording with lifecycle-safe cancellation）；提交后工作区干净。52项Java+5项契约、APK检查与整个index242个文本文件安全审计通过；无模型/录音/APK/密钥，不push。
- 开始0.3路线B第一增量，后续改动保持未提交，待实现/构建/独立复核/用户验收。
- 0.3实现模型状态/空间、编辑对话框、系统分享、SAF TXT快照导出、确认清除与互斥临时文件清理；原生引擎与录音gate未修改。14项清理/UTF-8/上限/symlink/非递归测试通过，总66项Java检查。
- 保留0.2 APK于dist/v0.2（ignored）及报告快照reports/apk/v0.2；0.3不继承用户0.2手动验收结论。
- 0.3构建bbf0c292d已启动，日志.pi/tasks/session-525127-525127/bbf0c292d.output；独立只读复核2af328d8同步执行。66项host tests已通过，0.3设备测试/构建尚未归结。
- bbf0c292d正式完成exit0：0.3 APK_READY，32+20+14 host tests、编译/DEX/签名/权限/包检查通过；native DSO与0.2一致。result/status已记录新版本，不混用旧0.2用户验收。
- 独立review中间反馈：host66项独立通过，未发现当前所有权下误删模型/active WAV；发现startup重复清理导致计数0且状态停留执行中。等完整只读报告后集中修复，不在审查期间修改源码。
- 0.3审查第二次中间反馈已记录：确认启动状态问题，另有clear与pending SAF快照失效风险（尚无设备复现）及云provider隐私说明缺口；继续等待最终只读报告，不将中间反馈当审查批准。
- 0.3原始独立审查4832912e完成，无已证实发布阻塞，F1启动重复清理属确认低严重缺陷；报告归档reports/apk/result-management-review.md。开始集中修复/加强后再重建，不将旧审查当新源码已批准。
- 已修startup清理一次/计数/终态；加入ExportSession全进程epoch、单pending与唯一请求码，clear撤销旧票据，实际写入前在同一任务所有权下二次验证；Activity销毁放弃请求，延迟旧回调不会消费新请求。
- App+文档补云SAF/provider同步提示。扩展报告part/编辑结果/嵌套model/写入和close异常、startup计数、clear前后/旧回调/重建票据测试，32+20+34=86项host检查通过；没有真实SAF/lifecycle测试结论。
- 修复版0.3构建baacdc2b1已启动（.pi/tasks/session-525127-525127/baacdc2b1.output），独立后续复核b74b3378同步执行；审查期间冻结源码，不push/commit、不读取手机数据。
- baacdc2b1正式完成exit0：修复版0.3 APK_READY，32+20+34=86项host检查及编译/签名/包检查通过。result.json更新当前APK哈希；独立后续复核仍pending，未实机验收或提交0.3。
- 后续复核中间反馈：startup/cloud提示已修复，host86项独立通过；发现clear与新export begin未共享所有权的交错风险，待最终报告后集中修复并新增针对性测试。审查期间不改冻结源码，当前构建不标为最终验收包。
- export-fix-review566272bd完成并归档：F1/F2关闭，N1导出创建未持任务owner仍需修；当前0.3不作为最终验收包。开始窄修复+可控线程测试，不改变录音gate或native。
- N1窄修复：ExportSession.beginOwned先CAS取得RUNNING，再调用Supplier读文本+begin，finally仅释放自身owner；MainActivity使用该入口，无预先读取lastText。picker等待期不持owner，clear/write原所有权保持。
- 新增clear invalidate之后/text清空之前暂停的latch测试：busy拒绝且Supplier读取0次、不释放clear owner；清除完成空结果拒绝；新文本导出、后续clear撤销、supplier失败释放均通过。32+20+41=93项host检查通过。
- 最终窄复核1684d8ea与重建bc41cf58a已启动，日志.pi/tasks/session-525127-525127/bc41cf58a.output。审查期间冻结源码，不自动提交或推送0.3。
- bc41cf58a正式完成exit0：export beginOwned修复版0.3 APK_READY，32+20+41=93项host检查和完整编译/签名/权限/版本/包检查通过；result.json记录当前APK SHA。最终独立复核仍待完成，未提交/推送/实机验收0.3。
- 最终复核2662ccce已关闭N1且独立93项host检查通过，指纹与APK build-input一致；0.3可供用户测试，但尚无用户验收。

## Session: 继续模型管理0.4
- 用户要求继续，保留0.3 APK/报告到dist/v0.3与reports/apk/v0.3。不自动提交未验收代码。
- 计划新增有界文件导入进度/协作取消/已校验文件复用与确认删除内部模型；本轮恢复核查确认尚未实现（完整已校验文件复用原已存在）。不下载模型、不改native/录音gate、不访问设备。

## Session: 继续规划 / 代码复杂度审计
- 已恢复三份规划文件、catchup无额外输出；git diff --stat确认0.3相关源码/报告未提交，保留原样。
- 发现顶部Current Phase仍为9而文末已有Phase10；先核查0.4是否真正落盘，不把进度意图当实现。
- 首次合并读取超过工具50KB输出上限；已单独完整重读task_plan与progress尾部，findings完整段落已在输出中；后续按文件/范围有界读取。
- 已完整检查MainActivity及录音、导出、文件/WAV helper、JNI、host测试入口；独立只读review cf6fec84正在执行，不触碰现有源码。
- 本轮Nix host重跑93项checks全部通过，日志.work/reviews/current-host-tests.txt。不是0.4构建或Android设备测试。

- 审计完成：独立报告归档reports/review/current-complexity-independent.md，综合报告docs/code-complexity-review.md；确认1项条件性模型残片恢复缺陷与多项维护/覆盖风险，未伪称实机复现。
- 已更新路线图与Phase12待实施拆分；本轮93项Java检查、5项P0契约测试、26项构建输入指纹通过。未改产品源码/测试、未构建APK、未提交或访问设备。

## Session: 实施解耦重构
- 用户明确要求继续重构优化降低耦合；恢复planning/catchup，保留全部既有未提交0.3代码/文档。
- 本轮实施Phase12，不叠加0.4新功能；单writer负责生产源码/测试，父会话负责规划、构建证据与独立复核。
- writer88cf4a75已启动，范围TaskCoordinator/应用操作层/ModelRepository-SAF隔离/生产组件测试；要求part缺陷红绿证据及Java编译，不自行构建覆盖APK报告。
- 父会话已建立docs/refactor-phase12.md验收清单并保留旧APK/报告，不修改writer负责的源码。
- 初稿完成200项host检查，但父审未接受：发现主线程/生命周期/报告回归和测试副本冒充生产流程覆盖。当前源码未构建APK，不对外作为可验收包；进入定向修正。
- 第二轮writer412002af完成，但父审仍发现取消录音在worker创建session前可漏掉、owner释放后缺UI通知、preflight/pending失败不写终态、初始化错误被吞等问题；暂停委派，由父会话接管唯一源码writer集中收尾。
- 父会话完成定向收尾：录音control在owner admission同步发布，finally清理；owner释放后状态失效通知；UI用主线程弱目标回调读当前状态；preflight/pending失败同样写failure终态；坏manifest显示初始化失败而不误调用未安装graph。
- 新增AdmissionBoundaryTest直接调生产RequestRunner/TaskCoordinator/RecordingControl/ExportSession，覆盖worker未开始前pause取消、busy拒绝不运行hook、释放通知、preflight失败清正文与终态、executor拒绝回滚。纯Java脚本移除android.jar依赖。
- 父复跑243项host checks通过。源码冻结，独立review0f95a7be和完整APK构建bfe1aeb24同步开始；构建日志.pi/tasks/session-525127-525127/bfe1aeb24.output。
- bfe1aeb24完整APK构建exit0，APK_READY；独立review de31b20c未发现当前正常生产路径发布阻塞，确认解耦/录音admission/owner通知/终态/导出保护。报告reports/review/phase12-independent.md。
- 独立审查指出非阻塞测试证据问题：坏hash曾实际测超长，rename用例未到发布阶段，假Map重名不成立，cleanup身份断言不足。已仅修改测试：同长度坏hash且断言SHA原因、复制完成后注入目标目录并断言rename原因、删除伪重名case、枚举cap收窄为异常传播、cleanup断言同上下文/路径。
- 生产源码保持独立审查版本，父复跑245项checks通过。最终指纹构建b4b1d2d31进行中，仅因测试变化重跑，不冒称独立审查者运行了245项。

- 最终构建b4b1d2d31 exit0：APK 2,405,160 bytes，SHA3772e2049106301f7553fdc48d25e6c11ab27f32577491de0347fdecf70152d3；245 checks与签名/包检查通过，47项输入SHA匹配。新旧APK的native DSO逐字相同。
- reports/apk/result/status已改为本轮重构身份，独立审查/测试修正边界明确；Phase12完成，设备回归pending，0.4取消/删除未实施。无commit/push、未访问设备或私人数据。

## Session: 下一功能确认
- 用户询问下一步功能；恢复规划并运行session-catchup（无额外输出），核对工作区与路线图。
- 确认Phase12重构已交付构建/审查，设备回归仍pending；下一功能为0.4模型管理：进度、协作取消、已校验完整文件复用、确认删除。
- 本轮仅给出优先级与验收建议，不启动开发、不提交或访问设备；保留既有未提交改动。

## Session: 用户验收重构版 / 输入法优先
- 用户反馈“验证没问题”，记录为重构版用户手动验收，不扩展成全设备/逐项自动验收；未授权新提交，本轮不commit/push。
- 用户明确改变优先级：跳过尚未实现的模型管理增强，先实现离线语音IME。
- 首版限定用户手动启用/切换、前台点击录音、停止转写、预览确认提交；共享现有进程任务owner与CPU引擎，隐藏/换输入框使旧会话失效，敏感字段拒绝录音/提交。
- 唯一源码writer workflow698cc694已启动；父会话仅维护规划/文档和后续验收，未并行修改生产代码。旧重构APK/报告已保留dist/pre-ime-0.3与reports/apk/pre-ime-0.3。
- 新建docs/voice-ime.md的输入法隐私/会话/验收契约，路线图按用户优先级更新。版本拟0.4-ime-debug，原模型管理增强延期。
- 初稿918e0dd5未通过父审：录音WAV没有进入native推理、%p非法format、缺launcher入口与真实预览、UI worker直调/强引用、密码visible漏判、解绑后不能恢复。父会话接管唯一源码writer；未构建/交付该初稿。
- 已重写IME会话/生产编排/Service：同owner下真实Backend录音→WAV→校验→JNI→解析→预览；复用ForegroundRecorder，移除重复采集实现与无用ImeSink；IME不接触App正文/报告。
- 父新增真实生产controller的全流程fake-backend/owner/commit测试：真实capture字节传入transcribe、同request清理、迟到结果丢弃、同字段重启、敏感字段、null/拒绝/异常连接单次消费、交接取消与thread latch。Android Backend仅编译，真实硬件仍待测。
- ba7d5d8dd正在执行host回归+Android Java编译；APK包检查改为强制读取manifest与IME metadata编译树，仍精确RECORD_AUDIO白名单。
- ba7d5d8dd正式完成exit0：既有245项+新增89项IME生产controller检查=334 checks全部通过；全部Android Java源码编译通过。仅deprecated API编译提示，不是运行失败。硬件/系统InputConnection尚未实测。
- 独立只读审查f5b0daca进行中，源码保持冻结；启动完整APK构建验证XML资源/DEX/签名/权限与组件，不将构建通过冒充审查批准。
- bff361037失败于末尾新增manifest-tree解析断言（service缩进假设错误），并非Java/native编译失败。真实编译树确认AsrImeService、BIND_INPUT_METHOD、exported=true与action/metadata存在；第二处bool格式断言也需按真实输出修正。
- 此次exit1保留，不将已生成签名文件作为完成验收包；旧reports/apk/result/status身份需要在最终成功后统一更新，当前权威状态为本条构建失败记录。
- 独立审查ec61deb8中间反馈：独立334项host checks通过，未执行Android/完整构建；重点核查输入法选择器关闭后恢复（当前切换入口visible=false/session=null，可能没有恢复回调）。这仍为审查中间发现，不当作最终阻塞结论或批准；源码继续冻结等待完整报告/指纹。
- reviewer ec61deb8确认picker恢复缺陷：打开选择器前visible=false，取消或重选自身不保证onStartInputView，onWindowShown仅render导致键盘保持禁用。最终报告前不改源码；建议新会话恢复而不复活旧preview。
- 审查期reports/apk变化来自父bff361037构建及status身份澄清，不是审查者写入；生产源码/测试仍冻结。intercom/send与subagent_supervisor/send均返回由native supervisor处理且未确认送达，不宣称已通知子agent。
- ec61deb8完整独立报告已消费并归档reports/review/ime-independent.md（e2b156...）；31源指纹保持冻结，解除冻结后集中修复B1/N1/N2/N3与构建checker。
- B1采用明确hide策略：生产leaveForExternalUi先invalidate/cancel→requestHideSelf→picker/settings；取消选择/重选本身后如键盘隐藏，用户点输入框触发新onStartInputView，不恢复旧preview/自动录音。设置启动失败同样不留下可见死会话。
- N1加共享任务忙提示；N2删除无连接AppState假断言、明确fake字节传递意义、清理失败改为删除前抛出并测试残留/teardown，补stale failure/stop/blocked-cleanup/external command顺序；不冒称Android picker覆盖。N3文档改为采集交接后处理阶段不可取消，包含校验/JNI前期。
- 修正checker对既有失败包的静态断言已通过；保留bff361037历史exit1，不冒充旧源码包包含B1修复。git diff --check通过。
- B1修复版全构建b4ea5c08d与独立窄复核3c49a277同步启动，生产源码/测试/docs再次冻结；构建只改输出reports/apk/dist。
- b4ea5c08d正式完成exit0，APK_READY；245+160=405项host检查及完整资源/Java/DEX/native/签名/权限/IME组件检查通过。当前APK2413416 bytes，SHAc433d1b82f30b5b1ef3ab37fbd4b349199196bd8507386d97e0463ca5c451a2c，55输入指纹匹配，native DSO与旧0.3逐字相同。
- reports/apk/result.json与status.md已更新为当前IME构建身份；仍标独立B1修复复核pending、不作为最终验收包，硬件设备测试未执行。
- 最终复核39a429db已完整消费并归档reports/review/ime-fix-independent.md：B1源码缺陷关闭，未发现新阻塞，独立405项host检查通过。保留两个P3：旧同controller任务的新会话忙提示不足、stop断言位于handoff后较弱；不夸大覆盖。
- 父核对31合并审查SHA、55 build-input SHA及APK SHA全部一致；静态包检查与git diff --check通过。result/status/disposition已更新可供手动IME设备测试，Phase13实现交付完成。未改变最终复核后的生产源码/测试，不需再次重建。
- APK dist/qwen-asr-minimal-debug.apk，0.4-ime-debug code4，2413416 bytes，c433d1b82f30b5b1ef3ab37fbd4b349199196bd8507386d97e0463ca5c451a2c。无安装、设备数据/麦克风访问、commit或push。

## Session: IME透明背景反馈
- 用户反馈底部输入法背景透明、按钮文字难辨；源码确认root未设置background。
- 本轮仅修视觉样式，保留旧包/身份到dist/pre-ime-background及reports/apk/pre-ime-background；不把此反馈当其他IME功能全面验收。
- b82a77e99 exit0 / APK_READY：405项host checks和完整构建/签名/包检查通过，55输入SHA核对。相较上包仅AsrImeService显示样式变更，native DSO相同。
- 新APK SHА2070cd7f1d1eb7a51be971c16a328d5db1a1950b99e0506b657a9337fbd4044a，2413416 bytes；result/status更新视觉修复身份。旧逻辑审查保留但不声称新版Service已独立复审；视觉效果待用户确认。无commit/push/设备访问。

## Session: 背景修复验收 / 下一步待确认
- 用户反馈“验证没问题”，记录为当前背景修复包用户手动反馈，不扩展成所有IME生命周期/设备验收。
- 用户要求先确认下一步，本轮不启动开发/构建/提交。建议回到延期的模型管理增强（进度、协作取消、完整文件重试复用、确认删除、维护不清转写结果），模型驻留提速另设后续阶段。

## Session: 独立模型管理页面规划
- 用户要求生成新规划文件、细化模型管理，并提供独立UI页面；仅授权本轮规划。
- 已恢复三份规划并运行catchup（无额外输出）。合并读取progress/findings超工具上限，已补读progress前210行；后续有界读取。
- 已新建docs/model-management-plan.md：独立Activity与主页/IME导航、文字线框、模型/任务双状态、按钮矩阵、MM-01~08功能契约、取消/发布/epoch、生命周期、结果隔离、M1~M4任务与测试门槛。
- 明确当前7文件合计1,573,492,181 bytes动态显示；不把原始模型目录、磁盘与RAM混淆。状态刷新不自动hash；重试重新选目录且复用完整文件；删除只删白名单内部副本。
- 默认前台管理：旋转不取消，离开/锁屏请求取消且owner收尾后才释放；无后台服务承诺。这些为新规划默认方案，非现有实现或用户已确认产品决策。
- 规划交付检查通过：333行新规格的本地链接、代码围栏、必要章节标记及git diff --check；55项APK build-input指纹全部未变，证明本轮未修改构建输入。未运行新代码测试/构建/设备操作，无commit/push。Phase15仅规划完成。

## Session: 从 D3pb0j 交接恢复
- 已读取 /tmp/llm-asr-handoff-D3pb0j.md，当前工作区为 /home/zhb/gitrep/llm-asr；session-catchup无额外输出。
- 初次合并读取三份规划触发50KB截断，改为有界补读；已核对git status/diff，保留全部既有未提交和未跟踪成果。
- 交接明确独立模型管理尚仅规划；本次“继续工作”先恢复并核对默认方案，实施范围待明确，不自动开发、构建、提交或访问设备。

## Session: 开始0.5独立模型管理实施
- 用户明确“开始”，接受上一轮M1→M4及默认产品边界；已恢复当前规划/catchup（无额外输出）。不再等待重复实施确认。
- 已建立Phase16，保留原0.4 APK/报告至pre-model-management-0.4，生产输入快照至.work/model-management-baseline；不重置现有工作区。
- 已核对AppGraph/ModelRepository与host入口，现有仓库没有取消/hash进度/delete，原405项为待复跑基线，不冒充新功能证据。
- 委派workflow0789736f在JS解析时失败（task单引号跨行），源码worker未执行；改用模板字面量重新启动。父会话未修改生产代码。
- 修正JS模板字面量后唯一源码writer workflow ada832dd已启动，负责M1→M3与M4构建输入/host和Android编译；父会话不并行改源码。
- 新增docs/model-management-validation.md分层验收矩阵，规划状态更新为用户已确认实施。备份APK的大小/SHA与备份result.json一致。
- 当前尚未完成新功能实现/测试/构建，当前dist主APK仍为旧0.4，不冒称可交付0.5。
- ada832dd/173038b7超时退出，partial artifact仅最后一句C1定位；已落盘模型readiness/control/state/controller、repo扩展及5份新测试，但尚无ModelManagementActivity、Android导航/版本接线。源码不交付，不覆盖旧APK。
- 接管前恢复现有规划/catchup；后续按更小任务单writer推进，不简单重跑整个M1→M3大任务。
- 发现超时writer遗留孤儿shell1201929和java1202017（ModelRepositoryCancelTest已运行14分钟），已仅终止这两个确认属于本次writer的进程，避免继续后续测试/写输出。
- 父复跑日志bbd20822e显示现有前5组151项通过，随后PartRecoveryTest失败：其红证据编译副本未带新增ModelManagementState依赖，不能把编译失败当空间次序红绿。尚未执行到C1，最后一句partial不能准确表示阻塞位置。
- 已启动小范围单writer修复22170594，仅纯Java核心/测试，明确重现PartRecovery编译依赖失败与有界测试防挂起；Android页面/接线另起后续阶段，父不并行改生产源码。
- bbd20822e正式终态通知exit1，与先前已读取日志一致：PartRecoveryTest独立编译缺ModelManagementState依赖；这是已交给22170594处理的同一次失败，不是新增失败或核心修复结果。未重跑/未干预正在修复的writer。
- 收到核心writer1e38833a检查点并完整读取148行权威报告 .pi-subagents/artifacts/outputs/1e38833a/.work/model-management-core-repair-output.md；报告称847 host checks/15 suites、确定性核心117+223各三次通过，生产行为变异红证据已恢复。父会话尚待独立复跑，不把子报告当Android接线完成。
- 该slice明确未改Activity/AppGraph/Service/SAF/manifest，未构建APK；下一步父核对核心接口/旧Android调用后推进独立页面与统一readiness。
- 核心workflow22170594已正式completed/ok，权威报告已消费；父独立复跑b8cc832f1执行中（有界180秒），不重确认子终态。
- 父已核对core主要实现与旧Main/AsrOperation/AppState/ImeBackend/SAF/IME接线；创建.work/model-management-android-handoff.md，明确统一readiness、移除旧维护入口、页面/票据/生命周期、安全错误与版本包门槛。等待父复跑正式结果后启动Android唯一writer，避免测试读取与源码写入交错。
- 父独立复跑b8cc832f1正式exit0，完整日志确认15 suites/847 checks全部通过；核心修复验证通过，但不等于Android页面/SAF/真实设备验收。日志.pi/tasks/session-1194494-1194494/b8cc832f1.output。
- Android唯一writer workflow a25d515e已启动，范围页面/主页IME导航/共享readiness/SAF与生命周期/版本checker及测试编译；不构建覆盖APK。父更新验收矩阵，核心M2/M3 host通过与Android pending明确区分。
- Android workflow a25d515e / writer84371bfd正式completed/ok；完整读取162行权威报告 .pi-subagents/artifacts/outputs/84371bfd/.work/model-management-android-output.md。报告947 host checks/16 suites、SDK35全Java编译通过；未构建APK/真实SAF/设备验证。
- 已接页面/共享readiness/ModelAccess/SAF票据与生命周期/版本0.5；明确剩余展示缺口：聚合预检空间未暴露、删除失败仅终态统计、部分逐文件复用状态未持久快照。父审与独立审查决定修复优先级，不全勾验收。
- 源码61输入冻结，fresh独立双审e6cfb984已启动：Android生命周期/隐私用户流与核心文件/取消/owner/测试证据分工；均不执行构建/测试，避免与父完整构建输出竞争。
- 父完整APK构建b6b11ff8b已启动（含当前host947重跑与全部打包检查），报告status明确验证中/不可将旧result与新输出混用。旧0.4独立备份保持可用；构建和审查后统一身份。
- b6b11ff8b完整构建正式exit0/APK_READY，父读取完整关键日志确认947 checks/资源JavaDEXnative签名权限组件均通过；APK2442088 bytes，SHA f4ad0e57b4d3da661a310df94510affbb42bad95bd71b2533310974b7caf37ab。
- 父核对72构建输入及61冻结输入SHA均匹配，native DSO与旧0.4逐字一致；result/status更新准确0.5身份并保留独立双审pending/非最终验收包。源码仍冻结，不在审查中改代码。设备未测。
- 独立Android reviewer98e2832f中间反馈：未证明ModelPageSession double-peek会导致生产NPE（main线程准入约束），不把父疑点当已确认缺陷。确认待完整报告的问题：重进页自动INSPECT覆盖先前取消/失败/删除终态；provider CancellationException原文可透入errorCode/UI。源码继续冻结，待双审完整artifact后集中修。
- 独立双审e6cfb984完整完成，父已完整消费并归档98e2832f/44a6d42a报告至reports/review/model-management-{android,core}-independent.md，61冻结SHA匹配。结论不是无条件通过：恢复终态被INSPECT覆盖、provider取消异常脱敏缺口、失败终态迟到取消、lazy验证漏非法part、复用/空间/删除显示与listener测试漏检需修。
- 原947-check构建保留dist/model-management-review-baseline及reports/apk/model-management-review-baseline；解除源码冻结进入集中修复，旧包仍非最终验收。两审均未执行测试，不冒称独立947复跑。
- 已形成reports/review/model-management-disposition.md逐项接受修复/明确defer，唯一writer639de525启动。范围恢复终态、provider安全边界、非法part lazy验证、完整终态仲裁、结构化预检/复用/删除显示、测试断言与APK派生报告绑定；不并行改源码、不全构建。result/status标当前包为审查修复前，不伪装通过。
- 修复writer186ebfc3中间检查点：accepted batch实现，final-host/final-javac日志已由父读取，旧数字检查现945（readiness断言由35改33）加7组review回归/provider边界/3个可编译行为mutants/实际checker绑定fixtures通过；不能沿用947为新总数或把分组混为checks。
- Writer仍在最终报告/指纹收尾，本轮不并行启动构建或改源码。新增self-review修复executor拒绝终态重入取消、provider LinkageError脱敏；待正式完整报告后父核对并重建/窄复核。
- 修复workflow639de525/186ebfc3正式completed/ok，父完整读取179行权威报告 .pi-subagents/artifacts/outputs/186ebfc3/.work/model-management-review-fixes.md；accepted修复实现与红绿/3变异/checker绑定证据齐全，仍待父重建及独立复核。
- Writer报告工具错误补录：首次缺Java PATH exit127（不算缺陷证据），改已安装JDK；广泛SDK find超时后改确切已安装SDK路径。无遗留命令，不重新触发下载。
- 修复后69输入冻结，独立双复核99c50a92（恢复/隐私/终态及展示/测试/绑定）和完整构建b9bbf4283同步启动；均不修改源码，审查不运行测试争用输出。status标重建中，旧result身份不能用于新输出。
- 父独立准备docs/model-management.md使用与手动设备验收清单，明确仍构建/复核中，不把主机provider包装测试当真实SAF，记录取消/重建/删除和证据边界；不触碰冻结源码。
- b9bbf4283正式exit0/APK_READY，父读取完整关键日志：945数值checks+7review groups/provider/3可编译行为变异/actual checker fixtures及全构建通过。78 build-input/69冻结SHA匹配，6份派生报告与APK绑定核验通过、standalone checker通过，native DSO与0.4一致。
- 新APK2442088 bytes，SHA57f51fa5658a09b48d3616f0b0fc735f11c73ae587035fa9589bf1fe2f1a148f，result/status统一修复版身份；独立99c50a92复核仍pending，暂不交付最终验收，源码保持冻结。
- 独立双复核99c50a92正式完成，父完整消费8d949144/3db342f7并归档reports/review/model-management-fix-{safety,evidence}-review.md，69冻结SHA匹配。核心恢复/隐私/终态/非法part与F4/F5/F6均源码关闭；剩E1已发布A在B失败后仍显示待复制、E2零可用空间全复用未直接测。父接管唯一writer作末次小修，保持此前安全协议。
- 父E1新增真实queued controller A发布/B坏hash、发布内取消两序列，旧生产实现明确断言失败（.work/model-final-polish/publication-red.log）；E2将全复用/parts-only空间设0且断言无COPY/open、已通过。只在publishPart成功后、cancel checkpoint前新增历史状态，不改变READY/gate。
- 父末次三文件差异targeted publication/planning通过；69输入再次冻结，最终窄reviewbee6c8ca与完整构建b0453981b启动。核心安全双复核已归档，不在最终窄审扩大可选polish。
- 最终b0453981b正式exit0/APK_READY，945数值+8review/provider/3mutants/checker fixtures及全包检查通过。父核对78 build-input包含且一致于69冻结输入、APK/6报告绑定、旧0.4 native DSO逐字一致。
- 当前APK2442088 bytes，SHA6e4fbf7a4cf07262912019dc667258123a32f95931b13bb1c046cf6e77471073；result/status同步最终构建身份，末次bee6c8ca窄review仍pending，不把构建通过冒充最后批准。
- 最后review8c20d499正式完成并完整消费/归档，E1/E2源码和测试缺口关闭，无新阻塞；父最终重核78构建/69冻结、APK SHA/6报告绑定、native一致、check-minimal-apk与diff-check通过。Phase16实现交付完成，更新result/status/规格/使用/验收，真实设备pending，无commit/push。

## Session: 模型导入内部根路径失败反馈
- 用户关联任务 c8dbb9fb-04e3-41ad-9183-bd044be604e9，逐条提供导入未完成/复用0/非法model根路径或符号链接/清理未知/概况读取失败。
- 定位checkRoot要求absolute-normalized==canonical，Android可信私有父目录别名会被误拒绝；设备实际路径未读取，不断言用户源模型坏或确有残片。
- 备份当前0.5 APK/报告至pre-model-root-fix；父唯一writer新增专用forAppFiles延迟解析可信filesDir后拼model，保留model叶/managed文件及一般repository严格边界。
- 新主机真实symlink fixture在接线stub+旧逻辑下运行失败，异常与用户一致（.work/model-root-fix/red.log）；修复后全量host通过（.work/model-root-fix/host.log），含新增controller导入/READY/复用/刷新/校验/删除与非法根/文件/part拒绝。
- fresh只读review 4e96a5f8已启动；完整APK重建将启动。真实设备/SAF尚未测，无设备/麦克风/私人数据访问、commit/push。
- 完整构建bc04a521f正式exit0/APK_READY：全部host含新alias组、资源JavaDEXnative签名包检查通过；父核对79输入指纹/standalone checker/旧包native DSO一致与diff-check通过。新APK2446184 bytes，SHA1c8de63959cb8d8b99aa7706e8239bb2349fb49eea2ccb4b3b7d1d245652f7b3；result更新身份，独立review仍pending。
- 独立窄审35d4908c正式完成，完整报告归档reports/review/model-private-root-review.md：无阻塞，2项LOW为first-worker/once-only及alias ModelAccess直接覆盖测试加强，明确defer且不夸大当前证据。父再次核对79输入与APK SHA匹配；复核后不改生产源码/测试。result/status更新可供手动设备验证，无安装/清数据/commit/push。首次按cwd读取review路径ENOENT，后从隔离output路径完整读取，不丢失报告。

## Session: 用户验收路径修复 / 新增运行日志
- 用户明确“没有问题”，记录为路径修复手动反馈；新授权日志页面、模型加载/完成/推理完成时间戳和日志文件导出。
- 已检查App/IME共用JNI：当前load与response在单个native调用内，必须添加真实阶段回调，不能在返回后伪造load完成时刻。
- 已保留旧包/报告至pre-runtime-logs并保存构建输入baseline；下一步唯一writer实施，父仅补文档和review准备。
- 唯一源码writer workflow7681e5d0已启动（30分钟有界），负责typed有界持久日志、App/IME真实JNI事件、LogsActivity导出与version/checker/test接线；不全构建、不改dist/reports/apk。父不并行改源码。
- 新增docs/runtime-logs-plan.md实施契约和docs/runtime-logs-validation.md分层验证矩阵；明确日志隐私、真实事件时间、后台IO/有界队列、SAF快照生命周期和native变更不能沿用旧DSO一致结论。
- workflow7681e5d0/worker a7c0e1f9 30分钟timeout exit143，无最终报告、新测试或有效验证结果。最后工具是全/nix/store无界find，非测试本身超时；父确认无遗留find/javac/test进程。已落日志core/UI/接线/JNI初稿，不能交付。
- 父源码核对发现真实阻塞：JNI找不存在VALUES字段导致无阶段回调/缺前向声明；持久化未接普通append、主线程restore、part符号链接缺口；导出写live而非ticket快照/close异常误报成功/Activity捕获/UI提示错位；无新增测试。整理.work/runtime-logs/core-recovery-handoff.md，按core→native adapter→Android导出小slice重做，非简单重跑大任务。
- b8ef3fe86开始独立核对partial host和Android javac并分别记录.work/runtime-logs/partial-{host,javac}.log，未覆盖APK；等待正式编译结果后启动core唯一writer。
- b8ef3fe86正式exit127：命令被fish解析，Bash变量赋值语法拒绝，测试并未执行；不得当host失败/通过证据。父改用bash工具有界执行Android javac，exit0，完整日志.partial-javac实际路径为.work/runtime-logs/partial-javac.log；仅Java编译不说明JNI/UI/持久化正确。
- core唯一writer workflow8eee5457已启动，显式newapi/gpt-6-astra，小slice仅RuntimeLog core+新增core测试，不碰Android/JNI/main scripts，完成后再接下一层。没有并行源码writer。
- core writer a007bbd2中间检查点（非最终验收）：报告8组focused生产core测试通过（真实symlink/坏持久文件/permission-denied/阻塞写时1万append合并/不可变UTF8导出）；完整host前17个Java程序通过后在model_android_source_test.py:63失败，原因synthetic manifest仍2 Activity而checker要求3。writer按core范围未修改该fixture，将补跑剩余host尾部。等待权威完整报告，不把中间通知当已独立复跑/Android完成。
- core workflow8eee5457/a007bbd2正式completed/ok；父完整读取权威报告，独立运行.work/runtime-logs/test-core.sh exit0/8groups，日志.work/runtime-logs/parent-core.log。新core只代表日志存储，不代表Android/JNI接线完成；17 Java+host尾部分别通过，全套仍被已知2/3Activity fixture阻塞。
- 已创建.work/runtime-logs/inference-handoff.md并启动唯一slice2 writer f8c75745（newapi/gpt-6-astra），仅共享App/IME adapter+真实JNI事件+测试/编译，不改LogsActivity/export/AppGraph/manifest/main scripts/core。明确已有JNI异常保留、正常response判据、Java解析后才request success与数值单调耗时。
- slice2 worker30170dc7请求遥测故障策略；父经supervisor回复06b2777e确认：保留成功ASR payload，非法/缺失callback不得伪造REQUEST_SUCCESS或REQUEST_FAILURE，新增固定LOG_TELEMETRY_FAILED表示记录不完整；真实native/验证/协议失败才请求失败。NativeResponse.parse当前为IAE，handoff称IOException是父误记，以源码为准并在adapter固定错误规范化。
- f8c75745 workflow报告failed原因是intercom协调detach，不等于writer实现失败；已回复后按要求对exact30170dc7注册nonblocking subagent_wait自动唤醒，无替代writer/resume/重复启动。继续等待其正式终态。
- slice2 worker30170dc7中间报告：真实App/IME已调用共用InferenceAdapter，新增MODEL_ACCESS_*区分缓存/懒SHA与真实加载；int ABI1..4/Java解析后终态/日志Throwable隔离、JNI pending异常保护。子称adapter9组279checks、core8组、Android Java及NDK API29 object编译通过；尚待完整报告与父复核，不当真机JNI已测。父不在其收尾阶段写源码。
- slice2 30170dc7正式completed（wait通知确认），父完整读取权威inference-repair.md，独立test-inference.sh exit0：9groups/330checks（.work/runtime-logs/parent-inference.log）。子scope核对331 reports/dist不变，MainActivity未变、原生配置不变；NDK只object编译，未JNI运行/全包。
- slice3唯一writer workflow04d86ee8启动，范围LogsActivity/不可变SAF快照session+bounded独立导出owner/graph导航/主host与3Activity版本fixture；不改已核对core/inference/native。详细.work/runtime-logs/android-handoff.md，明确provider close失败不成功、日志页面状态不写AppState、录音/维护离开语义、固定事件中文含义。
- slice3 workflow04d86ee8在JS解析阶段失败（单引号prompt含don’t的ASCII引号），未启动writer；改模板字面量后fbc43643已实际启动，仍只有一个源码writer。
- slice3 fbc43643/16563e13服务429（astra及fallback terra冷却）失败；已写LogExportController初稿与entry快照，无最终报告/验证。用户“继续”后父接管唯一writer，不反复重试同限流。
- 父完成LogExportPage生命周期helper/LogsActivity重写/中文事件含义/graph独立导出owner与应用context backend/录音确认和模型维护导航限制；controller固定快照、close后成功、slot覆盖阻塞IO、状态不写AppState。新增LogExportTest96checks，接主host core8组/inference330及3Activity负向fixtures，full host exit0和Android javac通过。
- 当前源码冻结.work/runtime-logs/frozen-sha256.json；独立双review03d758e1启动（core/JNI安全及UI/export/package两个只读方向），父开始完整APK构建。真实SAF/JNI/device仍未测，尚不交付最终包。
- b599e59ef正式exit0/APK_READY：全量host+Java/native新ABI链接+资源DEX签名包检查通过，父核对99输入/100冻结SHA一致、578 MNN对象与旧报告一致。APK2466664 bytes SHAd3aeda18c78f86b3a24fe9c030e29f83047e49cf8f1333fbf65ef59bf10c3851；result同步当前构建，独立双审仍pending。首次报告脚本stat字段误作函数TypeError，仅阻止报告更新，已修正；不影响构建/校验。两个reviewer中间均无普通路径阻塞，nonblocking观察待完整报告处置，源码冻结。
- 独立双审03d758e1正式completed/ok，父完整消费fe64f868/d73c3154报告，归档reports/review/runtime-logs-{safety,export}.md；无普通生产路径阻塞，4项非阻塞发现按disposition明确defer，未改已审源码。父再次核对99输入/100冻结与APK SHA一致；报告状态统一为可供手机验证，非零缺陷或设备验收。


## Session: 用户授权编写重构规划然后执行 / Phase19
- 完整读取task_plan/progress/findings并运行skill catchup（无待同步输出），保留所有原有工作区成果。
- 新建docs/android-refactor-plan.md，界定本轮R0–R4与后续R5/R6，写明行为保持、测试、回退及不访问设备/不提交边界。
- 下一步保存小文件基线和旧APK，复跑host/Android javac，然后单writer分批实现。未改变产品源码。

- R0完成：100输入保存至.work/refactor-phase19-baseline/inputs及sha256.json，旧APK保存dist/pre-refactor-0.6，旧报告完整保存baseline/apk-reports。
- Nix完整host基线exit0（含可编译mutation/checker绑定负例），Android javac exit0；日志baseline/host.log、javac.log。未运行设备，未覆盖主APK。
- R1唯一writer ed473135已启动：模型DTO依赖提取+日志Error恢复红绿/架构约束。父仅读R2/R3调用与测试，准备.work/refactor-phase19-r2-handoff.md；没有并行改源码。
- 父已准备R3交接：报告序列化保持原键/异常/大小限制；JNI仅迁移类符号且集中Graph装配，native对象编译与最终链接证据分开。R1仍是唯一活动源码writer，后续slice未启动。
- R1超时初稿已停止；模型DTO迁移已落盘但尚未验收。日志初稿/测试拒绝：正常检查pending与释放分离可漏唤醒，测试同步列表未等待producer终止且无界重入；父改为原子正常释放+异常专属收尾，并重写有界生产测试。子曾无Android classpath编译全部Java失败、管道未pipefail，均非有效验证。

- R1父收尾通过：ModelReports移动仅改变类型归属；未精简SHA入口（保持边界/异常语义），未做文案枚举。替换子重写的mutation框架为原有可编译mutants，新增独立lexical架构约束及负例。
- 日志保留Error原样传播及LOG_TELEMETRY_FAILED契约，异常退出释放publisher；正常pending检查/释放同锁，不在finally重复释放新publisher。旧源码red编译通过/断言exit1；新测试含fatal身份、并发、一次重入，有界join。
- 全量host与Android javac父exit0，日志.work/refactor-phase19-r1/{red-compile.log,red.log,host-final.log,javac.log}。即将进入R2，不完整构建APK。
- R2唯一writer 667472bc启动（显式astra路线）；父补docs/android-refactor-validation.md，区分已通过R1、未完成R2/R3/R4和设备限制，不并行改产品代码。
- R2 writer0450851a正式完成，报告.work/refactor-phase19-r2/report.md完整读取；父源码核对并独立复跑parent-host.log/parent-javac.log exit0。新增admission/observer/fatal/cleanup覆盖执行生产策略；没有改R1日志/模型DTO、JNI或APK。
- R2试验错误为两次fixture编译和lexical guard未接受postDelayed，均修正后全套绿；纳入总计划错误，不将失败当产品行为红。
- R3唯一writer0127821f已启动，父仅读旧产物报告准备最终身份更新，不并行改产品/测试。独立审查需基于R3后冻结输入，旧0.6双审不归因重构版。

- R3 55c753c3正式完成，报告完整消费；父对照报告writer/Graph/IME/JNI和新增工具源码。writer全host/Android/真实NDK object通过；Nix并行eval-cache contention为忽略警告，不当失败。即将冻结R1–R3与日志修复并独立双审/父全构建。
- 父完整构建exit0/APK_READY，含111 build-input、112冻结输入匹配、actual linked DSO/Java native符号核验、578 MNN对象与R0相同、录音gate/recorder SHA不变；新APK2466664 bytes，d919891538cff713c4360d64968e8b2941d6eb49f2f42bc0265a04bd71d81060。日志.work/refactor-phase19-final/build.log。独立双审仍pending，尚不交付最终包。
- reviewer中间发现测试假阳性：TaskCoordinator listener内断言被生产观察异常隔离吞掉，待完整报告后集中加强外部断言。R3中间无新增生产阻塞，完整build尚未强制javac-h头编译（standalone object有）；源码继续冻结。
- 完整独立审查c3e1535b/1732b7b8已消费并归档reports/review/phase19-{core,android-jni}-review.md；解除冻结仅改两文件：TaskCoordinatorTest外部断言修复、build脚本加入真实javac-h头/NDK object前置。生产源码不变；准备负例证明新断言有效，再最终重建/窄复核。
- 修复断言有效性负例：可编译测试副本强制记录错误finalized=0，明确AssertionError exit1；真实全量host fix-host.log exit0。两文件重新冻结，所有产品源码保持原双审SHA。处置见reports/review/phase19-disposition.md。

- 最终重建exit0/APK_READY，日志build-final.log；强制生成JNI头编译和实际linked DSO符号检查均通过。新APK2466664 bytes，SHA0b891db946992cb649d33b4ec8bf0347738070e4593c306a0558d7f68460e43c。
- 708e5aac最终窄复核完整消费归档：两个接受问题源码层关闭，未发现新阻塞。父核对111 build-input/112 frozen/41变更输入包含关系、APK+6报告绑定、578 MNN对象不变、diff-check、暂存为空；未改变最终复核后的产品输入。
- 更新规划/验证/APK身份/审查处置，R0–R4工程交付完成；真实设备测试、R5 TXT隔离、R6拆包/文案/锁外通知仍pending。所有变化保持未提交，无设备/麦克风/私人数据访问。

## Session: 用户继续 R5/R6 优化 / Phase20
- 用户明确授权继续TXT导出隔离与功能拆包/剩余策略整理。已恢复历史规划与catchup（无额外输出），保留大量既有未提交成果；旧R0–R4已交付不等于R5/R6完成。
- 首次恢复合并输出超50KB，已分段补读；后续使用有界输出。下一步保存本轮小文件/APK基线，分R5、R6策略、R6包迁移三个单writer切片，最终独立复核和完整构建。
- Phase20基线完成：111输入SHA/副本、旧APK与全部APK报告已保存；Nix host全回归（含mutation/checker负例）及SDK35 javac通过，日志.work/refactor-phase20-baseline/{host,javac}.log。
- 准备R5唯一writer：实际ResultState/revision、独立导出生命周期/状态与生产联动测试；先不迁包、不修改R6模型或日志策略，分小切片验收。
- R5唯一writer workflow02102761已启动，输出目标.work/refactor-phase20-r5/report.md（实际artifact路径以完成回执为准）；父未并行改源码。
- 父完成R6只读预检查并保存.work/refactor-phase20-r6/preliminary-handoff.md：锁内安装/锁外通知、typed文件结果、codec与迁包构建防线。R6 writer尚未启动，避免同工作区并发写。

- R5初稿ba365fc7完整报告已读取，父不接受完成声明：clear不校验票据epoch；Activity从未设置exportPage.foreground导致无法begin；无导出状态通知/展示；编辑text/revision分读；queued即ADMITTED使clear/destroy不能撤销未写工作。开始父单writer修复，R6未启动。

- 父已完成R5窄修复：ResultState独立clear epoch；controller把QUEUED与真正WRITING分开并在同同步域复核；queued撤销仍持slot至收尾；生产UI前后台/weak导出通知/独立状态与原子编辑快照接线。保留100000字符限制，恢复edit共享owner。
- 新TextExportSafetyTest在初稿下可编译且明确AssertionError（clear后仍provider open）；重写误认可旧票据的测试，增加open/write/close阻塞、真实同controller销毁、requestCode全耗尽等。后台b11460b9d正在父全量host/Android编译，不以尚未返回的结果标通过。

- b11460b9d完成exit0，父完整读取host/javac日志：TextExportTest91checks（含完整requestCode耗尽和3个IO阻塞阶段）、clear生产红绿、既有全量回归/变异/checker通过；SDK35编译通过。额外R5 Activity接线6负例通过。R5源码14文件冻结准备独立审查；R6策略writer限定不改这些文件。

- 启动workflow00fe3a00：fresh R5只读安全复核 +唯一R6策略writer；严格不交叉修改R5冻结14文件。R6负责typed文件结果/controller锁外通知/日志codec，暂不迁包。父维护文档/验收，无并行源码改写。
- 收到R5 reviewer abd2a604中间反馈：冻结14文件一致，发现WRITING状态缺通知导致阻塞IO期间撤销提示陈旧。review仍检查fatal/close/覆盖；父保持冻结，待workflow完整结果汇总再修，不把中间意见当批准。R6唯一writer仍独立策略范围。
- R5审查继续：两项拟报告P2为WRITING通知/陈旧可撤销提示、普通write失败合并fatal close被吞；测试接线/交错覆盖待加强。父不修改冻结源码、不干预R6唯一writer；最终报告回来后集中处置并重测。

- workflow00fe3a00达到25分钟超时：R5 reviewer abd2a604已完成，完整报告归档reports/review/phase20-r5-independent.md；R6 worker5197729f failed，无交付报告，末句“host通过准备javac”非父验证证据。未发现遗留javac/test进程。父接管唯一writer，先复跑当前partial再集中修R5，不重复整个大委派。

- b707b4736全host/SDK35编译通过（partial基线，不代表新契约已通过）。父新增两个可编译红测试：ModelNotificationTest确认INSPECT锁内通知；TextExportFatalTest确认compound close fatal被吞，均明确AssertionError。锁外通知/typed failure code/codec真实调用接线及R5 fatal仲裁/前台250ms合并刷新修复中。

- 父完成首轮集中修复并启动b4c8c0fe6全host/javac：INSPECT pending/terminal/finish及cancel通知均安装锁内/回调锁外，typed失败闭合枚举，真实LogExportController改直接codec无Worker转发；R5 explicit close异常仲裁/前台coalesced busy刷新。尚待正式测试输出，不提前标通过。

- b4c8c0fe6正式exit0，父读完整host/javac：两个新行为红测试已绿，既有全套/mutation/绑定通过。随后增强R5测试：同AppState.resultState+真实RequestRunner/AppRequestPolicy在3个阻塞IO阶段执行clear/inference并核对报告（不是Android Graph运行）；deferred clear/destroy/duplicate、精确100000 UTF16边界、throwing observer；R6增加重入取消和observer内异线程取得controller锁。增强待复跑。

- b96279f0c完成exit0，父检查增强回归日志：TXT104checks+fatal/锁外/8接线负例、全套既有mutation/绑定及SDK35 javac通过。迁移前113输入已备份，git diff --check通过；开始唯一writer包迁移，保留根Android注册入口。

- R6迁包唯一writer workflowd0cce038已启动（40分钟有界），机械迁移62类及递归构建/测试/JNI符号/架构负例；根Activity/Service及AppGraph保留，父不并行改源码。最终验收顺序已存.work/refactor-phase20-final-handoff.md。

- 包迁移writer938a0b17在child30分钟上限超时（workflow设置40分钟不能覆盖child默认），无最终报告；已有host/javac62源/JNI object日志。无遗留测试进程。父接管并启动b3cac2615独立host/Java/JNI复跑；初比62类全部唯一映射，除imports/package及少量public可见性外未见方法体改变，需审查可见性必要性。

- b3cac2615父host/62Java/真实NDK object全通过。父收尾修正R6依赖检查为完整映射+允许边+15个实际checker负例；RuntimeLogEvent恢复package-private，测试移到diagnostics；host使用fresh临时classes防陈旧包污染。源码冻结准备双审/全构建。

- 最终双审中间发现两处测试接线/断言问题：mutation旧classes路径、LogExportTest自捕断言。R5两P2/source锁外已静态支持关闭，最终报告仍待完成；build与源冻结继续，后续统一修复重构建。

- reviewer193f09cb请求scope决定：日志codec真实close-owner LogExportController同样有普通write/flush+fatal close吞异常，属迁包前已存在相邻缺陷。父回复要求单独列为pre-existing具体P2，不误归因迁包；完整双审后考虑一致窄修，并修LogExportTest自捕断言。不扩展日志worker Error恢复。

- be617092e完整构建正式exit0/APK_READY：APK2470760 bytes SHA bbb85daa0401027cd366a640c57e701af0b32621f6d00b7e385ce845999361a6，119 build inputs匹配、jni-pre-r3 fixture已绑定、实际linked DSO符号与Java描述一致、签名权限组件通过。但mutation gate有审查确认陈旧classes缺陷，本包仅中间构建，仍需修复后重建，不能标最终验收。

- package reviewer9df12f23完整报告已读取归档reports/review/phase20-package-review.md：PKG-1 high需修fresh classes传递，PKG-2 freeze补JNI digest fixture，PKG-3 standalone JNI注释/unused list清理。确认62Java仅3处必要可见性、native4处prefix；无产品迁包逻辑回归证据。behavior报告仍待正式完成，保持冻结。

- workflow431fae3e结束返回behavior193f09cb为intercom detached而非最终报告，package完成。父此前已回复scope请求，现按工具指引对exact193f09cb注册nonblocking wait自动唤醒；不resume/不启动替代review，不改冻结源码。

- behavior193f09cb正式completed，完整报告消费归档reports/review/phase20-behavior-review.md；解除冻结仅集中修PKG1/2/3、BR1/BR-T1。原两R5P2/source通知修复已独立关闭，日志compound fatal为旧相邻缺陷不归因迁包。

- 最后修复落盘：MODEL_REVIEW_CLASSES强制显式本次classes（缺参fail closed通过）；JNI脚本修正subset说明/去unused；LogExportTest捕获后外部identity断言+compound红测试明确AssertionError，LogExportController改显式close仲裁。完整fixture补freeze现126项，准备真实fresh全构建，原中间APK不验收。

- b19042db8最终完整构建exit0/APK_READY，TXT104/log100、fresh classes3mutants、全套及Java/JNI/签名包检查通过。APK2470760 bytes SHA a03c0876ba0f73ccec6532a0eb91c0416a1edb2488b5e57f19f7ffb58b4947f1。父核对119 build/126 frozen全部SHA、APK报告绑定、578 MNN对象与baseline相同、standalone checker/diff-check通过；result/status已改本轮准确身份，窄review pending。

- 最终875e523f报告完整消费归档，PKG1/2/3/BR1/BR-T1全部源码关闭、5差异+1fixture无额外变更；父再次核对APK/119build/126frozen与记录一致。result/status/路线与验收统一最终身份，Phase20完成（设备pending），复核后未改产品输入、未提交/设备访问。

## Session: 用户授权提交并建立后续提交规则
- 用户要求提交当前代码，并在agents规则中规定每轮功能开发测试完成后先提交，再通知验证APK。采用工具标准识别文件名AGENTS.md（此前无同名或小写文件），明确不自动push/不提交模型、音频、产物或密钥。
- 已检查index为空、HEAD a4c6a65（0.2），当前待提交包含后续累计已完成源码/测试/文档/文本证据，非只R5-R6孤立差异。生产输入和APK不因本轮文档规则改变。

- 提交前全量host复跑通过（.work/refactor-phase20-final/precommit-host.log），APK checker/119build+126frozen与最终APK SHA一致；本轮仅新增规则/规划文档，无生产变更，无需重新构建。
- index全树531文本文件约8.60MB安全审计通过，模型/产物/音频/密钥/缓存均未纳入；317路径累计差异形成完整当前版本。历史5份readelf报告尾随空格原样保留，其余cached diff-check通过。创建本地检查点，准确提交哈希由Git日志及用户交付消息记录；不push。
