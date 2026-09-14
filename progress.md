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
