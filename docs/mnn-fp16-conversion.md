# 当前 checkpoint 的隔离 FP16 转换入口

本页是 CLI 提交 `e1e310e` 后的转换补充，不改历史 P0 配方或可信 manifest。打包 CLI 使用见 [MNN ZIP 文档](mnn-model-packaging.md)。新转换包与当前 APK 是否同一字节身份必须逐文件比较，不能按“同模型/FP16”推定可导入。

## 1. 前提与边界

- 已审且固定的 Python 包：`model-tools/pyproject.toml` / `uv.lock`，Python3.12。包获取/安装/源码 hook 需要明确授权和审计，不自动联网修复环境。
- pinned MNN revision、四个当前补丁与完整 prepared tree 由 `scripts/prepare-native-sources.py --check --output <prepared>` 校验；host converter 必须同源，不能拿 PyPI wheel 的 converter 代替。
- explicit cached uv0.12.17 代替 flake0.12.11 是本批已接受的工具 route 例外，锁未变；本批依赖获取/静态审计/离线 sox 构建及新 venv 修复的证据在当批报告，不宣称完整冷缓存重现或无漏洞。
- 一模型实例、CPU2threads、构建≤2jobs；模型与编译串行。命令放任务独占新目录，记录 exit/log SHA、输入/图/权重 SHA、timeout 和采样 RSS。不能运行历史硬编码 helper 去覆盖旧 `models/mnn-16/`、`reports/p0/`。

## 2. encoder / prompt 显式路径

以下命令仅示范路径接线；变量必须先指向已验证的当前输入/新输出，先获得适用准备执行授权。`EVIDENCE`、`ENCODER` 父目录预先建立，不能指到旧报告。

```bash
python model-tools/check_audio_parity.py --model "$MODEL" \
  --source "$SOURCE/transformers/llm/export/utils/audio.py" \
  --lengths 20,99,100,101,800,801 --output "$EVIDENCE/audio-python-parity.json"
python model-tools/check_prompt.py --model "$MODEL" \
  --source "$SOURCE/transformers/llm/export/llmexport.py" \
  --output "$EVIDENCE/prompt-parity.json"
python model-tools/onnx_audio_gate.py export --model "$MODEL" \
  --source "$SOURCE/transformers/llm/export/utils/audio.py" \
  --directory "$ENCODER" --output "$EVIDENCE/onnx-audio-parity.json"
python model-tools/onnx_audio_gate.py validate --directory "$ENCODER" \
  --output "$EVIDENCE/onnx-audio-parity.json"
"$CONVERTER" -f ONNX --modelFile "$ENCODER/audio.onnx" \
  --MNNModel "$OUTPUT/audio.mnn" --fp16 --saveExternalData --transformerFuseC4=0
python model-tools/mnn_audio_gate.py prepare --directory "$ENCODER"
# 用同源构建的 native/p0/audio_gate.cpp probe，实际运行同session长度801,20,99,100,101,800,801。
"$AUDIO_GATE" "$OUTPUT/audio.mnn" "$ENCODER" 801,20,99,100,101,800,801
python model-tools/mnn_audio_gate.py validate --directory "$ENCODER" \
  --model "$OUTPUT/audio.mnn" --output "$EVIDENCE/mnn-audio-parity.json"
```

`check_audio_parity.py` 生成指标不是自动拒绝所有误差，调用链必须另断言六结果 finite、max_abs<1e-5；prompt 三行 equal，ORT 七次动态形状全部 pass。MNN encoder gate 预设FP16存储 max_abs≤0.002、relativeL2≤0.02，不是识别准确率；新增 `--model` 将实际 graph/weight 大小SHA绑定到 gate。

## 3. decoder-only 新 helper

```bash
python model-tools/convert_fp16.py \
  --model "$MODEL" --source "$SOURCE" --converter "$CONVERTER" \
  --output "$OUTPUT" --audio-gate "$EVIDENCE/mnn-audio-parity.json" \
  --evidence "$EVIDENCE"
```

新 helper 仅FP16：先核当前六长度 MNN gate 的既定门槛与两资产SHA，再确认 decoder 输出不存在。捕获音频元数据后释放 encoder，只导出 decoder/tokenizer/config；每个真正 converter 子命令有 timeout、exit 记录，用参数数组 subprocess 替代上游隐藏退出码的 shell 调用。导出后再次核 encoder 未变、七资产存在，写任务 evidence `exported-assets.json`，**不写可信清单**。需要外层 owned-process timeout/RSS 控制；脚本本身不是沙箱，也不自动下载、安装、审批或修改 gate。

重跑拒绝覆盖已有 decoder 文件；失败后检查真实子进程已终态，换独立输出/证据目录诊断，不让旧中间输出满足本次成功。CLI/helper 的 source/model/converter 都是调用者已审核的本地路径，不应面向不可信多用户输入执行。

微型 gate 回归（不加载权重）：

```bash
python3 -B model-tools/test_convert_fp16.py
```

覆盖当前绑定 gate 接受、缺失/重复长度、失败/非 finite/超门槛、未绑定或不同 graph/weight 大小SHA及实际资产变更拒绝。它不替代真实转换/数值运行。

## 4. 当前字节与质量限制

- 本批七真实资产完整，encoder/ORT/MNN、三语言 native prompt、text/audio positions、完整词表首步 logits 对照已实际执行；详细数值/完整与未运行项见 [转换报告](../reports/mnn-package-bt4/conversion-progress.md)。
- 两外置权重、tokenizer、两配置与历史可信 tuple 相同；两个 graph SHA 不同，当前 APK 会拒绝。当前 converter 默认向新图写随机 UUID4；旧 graph 不存在，不能证明仅 UUID 不同，不能改UUID/旧SHA来凑导入。模型数值行为验证与应用信任身份分开。
- **strict mel frontend 门槛仍失败**。当前样本下 frontend 单变量替换 token 未变、native ASR 完整输出匹配，只是一个公开录音/两强制语言的工程证据，不证明普遍无影响、独立CER/WER或设备质量。不得把 generic ZIP 标成当前 APK 可导入或质量发布版。
- 本批不改 Android/catalog/数学补丁、不构建 APK、不访问设备/麦克风/私人音频。新 catalog/APK 身份须另授权与验证。全部权重/ZIP/缓存仍忽略，只提交维护脚本、文档和必要非敏感摘要。
