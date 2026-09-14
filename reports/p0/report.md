# P0 阶段报告：Qwen3-ASR / iQOO Z1

## 结论

**已证实原生 CPU 端侧可行性；修补后的 20-case 工程回归通过；完整 P0 质量门槛仍未全部关闭。**

这不是 APK/IME 完工，也不是产品性能达标报告。建议在保持这些基准的同时进入最小测试 APK 搭建，但必须保留尚未关闭的质量和资源保护任务，不能把它们标为通过。

## 本阶段实际交付

- `flake.nix` / `flake.lock`：ADB、CPU 模型工具、Android NDK 环境；`scripts/nix-env.sh` 避免把大模型作为 path flake 源复制到 store。
- ModelScope 官方 `Qwen/Qwen3-ASR-0.6B` 固定快照及文件校验；锁见 `model-tools/model-lock.json`。
- 固定 MNN commit `a03b005cf6f888ebf092e4753840f935827f9c36` 的主机转换器和 Android CPU runtime。
- 模型转换、数值检查、参考生成、原生推理/设备探测/部署和采样脚本。
- 可重现源补丁：ASR prompt、短音频卷积/特征长度、Whisper 周期 Hann、ASR 音频边界位置计数。
- 原生测试程序及模型已在授权设备专用目录部署，逐文件 SHA-256 校验；未读取私人音频、启用麦克风、root 或修改频率。

## 已通过的验证

| 检查 | 证据与结果 |
|---|---|
| 原始 encoder 差异复现 | 20 帧原始 wrapper relative L2≈23.43%；见 `audio-parity-original.json` |
| 修补 Python encoder | 20/99/100/101/800/801 帧与官方 FP32 eager 的 max_abs 均为 0 |
| ONNX 动态长度 | 同一图/session 7 次切换，max_abs≤7.60e-7，relative L2≤2.85e-6 |
| MNN encoder | FP16 权重存储、CPU high precision；6 种长度通过预设门槛，relative L2≤0.2591%（随机 mel） |
| 真音频 encoder 隔离 | 3 样本×2种特征来源，MNN embeddings 注入官方 decoder 后 token 全部相同 |
| 实际原生 prompt | 3 样本完整 token/占位序列与官方相同，数量分别 214/408/269 |
| 位置编码红绿 | 原生 decode 第一位置42而非44；修补后9组trace全部通过，临时trace已移除 |
| 完整主机工程回归 | **全新运行20/20正常、20/20解析后逐字匹配**；`mnn-smoke-final-position-fix.jsonl`及summary，raw输出保留 |
| 最终补丁手机运行 | 哈希核验后完整转写，EXIT=0、status=1、truncated=false；见 `final-device-result.json` |
| 独立审查 | `implementation-review.md`认可窄可行性和边界位置修补算术，不代替最终回归/构建；审查部分安全项已修正，其余见下文 |

### 关键根因与修补

1. **Prompt 换行和语言配置**：匹配官方模板；每次请求语言必须在模型配置合并后设置。
2. **短音频 CNN padding**：单短块不能总是填充到100帧，否则多层卷积边缘不同。
3. **Whisper Hann 窗**：周期窗与官方一致。仅 Whisper opt-in，其他 spectrogram 默认保持不变；公共参数结构增加字段，所有消费者须匹配重编。此项显著降低前端误差，但没有单独消除转写差异。
4. **ASR boundary position**：起止 audio token 被插入 token 序列，却未进入位置状态，造成decode持续少2。只对 `qwen3_asr` 计入存在的边界token，最终完整回归由17/20解析匹配提升为20/20。

## 最终补丁版本实机结果

设备 vivo V1986A / Android12 API31 / arm64-v8a；4×A55+4×A77，原生 HWCAP 确认 NEON/FP16/dot-product；仅CPU两线程。

| 指标 | 实测 |
|---|---:|
| 公开中文音频 | 4.20394s |
| 模型加载 | 11.6248s |
| 推理 | 4.72811s |
| RTF（不含加载） | 1.12469 |
| 采样峰值 PSS | 3,209,091KiB ≈3.0604GiB |
| 转写 | 甚至出现交易几乎停滞的情况。 |
| 结束 | 正常、未截断 |

- `device-deployment-final-patches.json` 绑定实际模型、runtime、程序及输入音频哈希；`final-device-result.json` 绑定原始报告哈希。
- 不是warm常驻推理、P95或20分钟热稳态；与之前0.90 RTF的单次测试条件不同，不构成性能回退/优化结论。
- 内部分项计时存在重叠，不能相加当总耗时；PSS为采样值，不保证捕获绝对峰值。
- **未达到规划加载≤5s、短句PSS≤2GiB目标，也超过2.5GiB保护复核线。** 现在不应默认做常驻输入法。

## 仍未关闭的 P0 / 发布前问题

1. **语料覆盖不足**：20个case仅来自两条独立公开录音加裁剪、拼接、重复、静音、噪声；无独立人工标签，缺音乐/方言/数字等覆盖。20/20是后端一致性，不是100%识别准确率或CER/WER。
2. **严格前端数值门槛未过**：周期Hann后5条真实音频max_abs约0.000607–0.00575，未达到原1e-4门槛。固定官方decoder的隔离实验在3个困难case上未改变token，不能推广为所有输入无影响。保留失败，不事后放宽门槛。
3. **oracle语义范围**：参考是锁定官方FP32 eager，不含显式窗口mask；不是FA2/真流式等价性证明。
4. **参考结束覆盖**：3个差异长样本已单独记录EOS并确认非截断；其余参考使用公开transcribe接口，主报告未逐条记录EOS。MNN20条均正常结束。
5. **测试设施安全边界**：路径解析、运行锁、信号清理、断点分块校验已加强，但未完整实测中断/强杀/断网故障路径；部署与推理必须保持串行，部署脚本尚无跨会话原子锁。工具仅面向可信本地测试，不是开放API。
6. **可复现检查**：构建有固定输入和补丁，但还需干净机器从零复现所有补丁/转换；部分早期gate未强制检查所有关联源码/模型哈希。最终报告与版本哈希必须一起使用。
7. **应用未实现**：没有Gradle Android工程、JNI App集成、APK、IME或本地HTTP/AIDL服务。原生ASR harness输出仍含auto语言协议，产品应解析后再显示/提交。

## 后续优先顺序

1. 收集/建立独立、有许可和人工标注的golden覆盖，明确eager基准范围；前端残余误差继续分层归因。
2. 最小测试APK：补齐SDK/JDK/Gradle、JNI、模型导入和文件转写页，复用本阶段patch和manifest，先不常驻、不默认开麦。
3. 降内存/延迟：decoder INT8/INT4对照、混精度、避免重复权重/缓存、线程与常驻策略；每次都重跑已通过的一致性和质量集。
4. 通过资源保护和生命周期验证后接录音/VAD、IME和API；持续热测、P95、真实宿主编辑器共存测试不能省略。

## 重现入口

```bash
bash scripts/nix-env.sh model       # CPU reference / export environment
bash scripts/nix-env.sh native      # fixed Android NDK
bash scripts/nix-env.sh adb         # ADB without copying model data into Nix source
python3 -m unittest discover -s tests/p0 -v
```

源文件恢复、转换和patch细节见 `model-tools/`、`patches/`、`source-lock.json` 与 `progress.md`。不要直接对已打补丁的源码重复运行patch脚本；脚本会拒绝不匹配片段。不要把所有历史报告覆盖成最终版本。
