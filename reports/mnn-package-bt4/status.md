# llm-asr-bt4：MNN 打包工具与转换阻塞

实施基点：`f331efe9c72ca6ab8e91a87a9c5724320fe53541`（main）；sole implementation leaf `mnn-bt4-implement`，父 `mnn-package-coordinator` 唯一 Beads writer。实际模型路由 `newapi/gpt-6.1-sol`、thinking `high`。本轮未 stage/commit/push；该报告不是已交付声明，fresh 上下文隔离审查与安全提交由父后续安排。

## 结果分层

| 项目 | 本轮状态 |
|---|---|
| 通用参数化七资产包装 CLI | 已实现 `scripts/package-mnn-model.py`，Python 标准库 |
| 真实 CLI 合成行为测试 | 最终 exit0，15 项通过（3.285s）；负例要求 exit1 和目标拒绝，无 Traceback/残包；另测写入/验证 IO 失败不发布、payload CRC 损坏拒绝 |
| 当前 HF 原始输入/归档/补丁复核 | exit0：十文件共1,880,619,678 bytes 与 cb1 全匹配；两归档及四补丁 hash 匹配当前 native 锁；cached source 仅记录补丁目标 SHA，未冒充整树重新准备/验证 |
| fresh 生产 ZIP 解析器合成互操作 | exit0：fresh 编译56个生产源码，真实 `ModelZipArchive.prepare` 完整 CRC/SHA + 七流读取通过；`FixedModelPackage.mnn` 拒绝 synthetic tuple。不冒充真实包验收 |
| 当前 FP16 模型转换 | **BLOCKED / NOT RUN** |
| 七个新部署资产、与旧 tuple 比较 | **NOT RUN**，没有新模型输出，不用旧两个配置拼装假模型 |
| 真实模型 ZIP、当前 APK 可导入证明 | **无产物 / NOT RUN** |
| 模型 audio/prompt/positions/数值质量 | **NOT RUN**，没有复用历史绿报告作为当批证据 |
| Android/APK 构建、设备/SAF/麦克风/私人音频 | **NOT RUN**；本任务没有改应用或 APK 输入 |

## 精确阻塞与下一输入

1. `model-tools/.venv/` 不存在；主机的 uv 缓存大小为0，缓存位置 `/home/zhb/.cache/uv`。已存在的 Python 3.12 解释器/环境经 `find_spec` 探测，均无 `torch`、`qwen_asr`、`onnx`、`onnxruntime`、`safetensors`、`MNN`；未执行这些缺失库或安装 hook。
2. 项目 `model-tools/pyproject.toml` / `uv.lock` 要求 Python ≥3.12,<3.13，含 `torch==2.10.0+cpu`、`torchaudio==2.10.0+cpu`、`qwen-asr==0.0.6`、`onnx==1.20.1`、`onnxruntime==1.24.2`、`MNN==3.6.1` 等闭包。系统默认 Python3.14.8 且无 pip，不能替代锁环境。
3. `bash scripts/nix-env.sh model --cache-only ...` exit1，Nix 所需来源/工具闭包不全，明确报告 `local builds are disabled (max-jobs = 0)`。没有关 cache-only、更新锁、联网或以另版包凑绿。可用的孤立 Python3.12/uv 路径不等于已具备锁环境。
4. 匹配的 `.work/build/mnn-host/MNNConvert` 不存在；Python MNN wheel 即便可用也不能替代 pinned host converter。没有在 Python 前置缺失时浪费资源进行无用构建，也没有启动大模型。

**下一步需要父明确批准**：提供已审计且与当前锁一致的离线 wheel/环境缓存路径，或另授权联网准备锁定 Python/Nix 依赖闭包（需先审查来源/许可/代码及 install hooks、保留锁与 wheel hash）。随后才在新任务输出根中用当前 pinned+patched 源码低并发构建 host converter，串行重新跑 encoder/ONNX/MNN、decoder、prompt/positions/数值验证，再对七输出逐字节比较。不要只批准下载权重——权重已存在，缺的是工具依赖。没有批准前本 leaf 不继续转换分支。

即使以后重新导出成功，SHA 也可能不同。若不等于当前固定 tuple，不改变旧 manifest 或固定 modelId；只能明确报告不兼容，并在独立数值验证后以新 `GENERIC-NOT-IMPORTABLE-...` policy 包装。APK/catalog 支持变更须另授权。

## CLI 的验证契约

输入是恰含七个普通非链接资产的目录，以及源目录外的可选/显式可信 policy；输出是源外新 ZIP。默认 current-app 锚定仓库可信 tuple 指纹 `02624d4f3f12ce44ce8d1191d925ff2dddd3eedbe731692716bb017a284521a6`，generic 强制独立不可导入 modelId。读取流式≤64KiB，确定性 ZIP32 STORED，无 DD/extra/comment/ZIP64，精确 schema/条目集合，预检+写入+成包 CRC/SHA 检查，临时文件 fsync 后无覆盖原子发布。

测试使用微型生成数据而非真实权重，包含15个 test methods 及多组参数化拒绝。操作、限制、退出码与 generic 不等于应用支持的边界见 [CLI 文档](../../docs/mnn-model-packaging.md)。复杂度工具只提供人工审查信号，不证明功能或安全；同范围新文件没有旧函数基线，不能报告“零退化”。cccc1.8.3 / knots1.18.0 同范围显式扫描新 Python 文件（不用主 index 追踪），最终双 exit0，stderr 为空，cccc18个工具函数/19个测试记录、knots40个记录；策略验证函数 cccc cognitive12/cyclomatic14、knots cognitive9/mccabe11，测试库存函数 cognitive19，已分别记录人工阅读与行为覆盖，非 fresh 独立批准。最终快照与候选 SHA 的绑定在 receipt 中核对。最终 frozen 候选、命令/日志 SHA、保护域审计和未运行项在实施 handoff/receipt 中封口。
