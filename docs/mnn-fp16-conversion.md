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

## 4. 可维护的完整分阶段编排

`model-tools/reproduce_fp16.py` 串行调用上述已提交 helper，不依赖 `.work` 中的临时 runner；新入口不准备/下载环境、不编译 converter/probe、不修改数学补丁或信任清单。调用前仍须按本页前提核对官方 raw、prepared source `--check`、同源 converter/动态库与 `native/p0/audio_gate.cpp` probe、受审 Python 环境。它不是不可信代码执行沙箱。

```bash
python3 model-tools/reproduce_fp16.py \
  --model "$MODEL" --source "$SOURCE" --converter "$CONVERTER" \
  --audio-probe "$AUDIO_GATE" --python "$AUDITED_PYTHON" \
  --output "$NEW_OUTPUT" --evidence "$NEW_EVIDENCE"
```

- `output` / `evidence` 必须是**不存在、互不嵌套、不与输入重叠**的新目录；拒绝覆盖，不支持原地 resume。失败保留已有阶段证据；重新执行须使用另一组新路径。
- Linux `/proc` 进程组+递归 PPID 采样，单模型串行，线程环境固定为2，encoder 总预算1800秒、decoder2400秒、进程树采样 RSS≤9216MiB（共享页会重复计入；采样不是硬内核内存隔离）。可调小预算，不能从 CLI 放大这些上限。
- `reproduction.json` 记录实际 argv/exit/logSHA/阶段产物SHA，失败后后续阶段明确 `NOT_RUN`。六长度 Python、三语言 prompt、七次同 session ORT 动态长度与当前 ONNX SHA、当前 encoder graph/weight gate 均检查后才导出 decoder；最终重新核七资产清单。不将进程 exit0 单独称数值通过。
- 普通可捕获的 `SIGTERM`/`SIGINT` 使用非抛异常取消 latch：先持久化阶段 `RUNNING`，实际启动后记录 `started`/PID 身份，停止并有界等待直接子进程，再写阶段实际 exit、logSHA 和运行 `CANCELLED`（CLI exit `128+signal`）；后续阶段保持 `NOT_RUN`。重复取消不打断第一次清理，返回后恢复原 handler。若已观察子进程终态无法确认则标 `FAILED`/cleanup uncertainty，保留输入与证据，不冒称可安全释放。采样只能覆盖直接子进程和已观察的 PID-start 身份，不保证任意逃逸/重挂的孙进程均被等待回收；`SIGKILL`、断电或不可写文件系统不提供持久终态保证。
- `--dry-run` 只打印 `NOT_RUN` 的准确命令计划，不创建输出目录、不转换、不做数值验收。入口不自动执行 frontend/positions/logits/完整 ASR 或设备测试；这些是单列的工程验证，不应因 conversion PASS 而关闭 strict frontend FAIL。
- 输入前提仍由调用者承担：prepared source、converter依赖库/probe同源身份和完整环境审计不是只凭入口检查文件存在就得到证明。`--python` 必须指已审本地解释器，不能拿任意脚本/未知可执行文件当可信工具。

不载入真实权重的生产接口回归：

```bash
python3 -B model-tools/test_reproduce_fp16.py
python3 -B model-tools/test_convert_fp16.py
```

测试实际启动 tiny fake child，覆盖串行排序、带空格路径、子进程非零、exit0但 gate失败、旧ONNX/encoder/最终清单绑定拒绝、失败保留/后续NOT_RUN、无覆盖、dry-run、资源上限和真实 timeout，以及实际CLI的外部TERM/INT就绪子进程、重复取消、spawn后首采样前取消及终态写入取消。**这些 fake 不证明真实数值门槛。** v1v 真转换先经任务独占临时编排运行，之后才提炼此维护入口；不能追溯声称真权重命令已经运行过新入口。真批结果和此证据差异见 [v1v 报告](../reports/mnn-reproduction-v1v/status.md)。

## 5. 当前字节与质量限制

- 本批七真实资产完整，encoder/ORT/MNN、三语言 native prompt、text/audio positions、完整词表首步 logits 对照已实际执行；详细数值/完整与未运行项见 [转换报告](../reports/mnn-package-bt4/conversion-progress.md)。
- 两外置权重、tokenizer、两配置与历史可信 tuple 相同；两个 graph SHA 不同，当前 APK 会拒绝。当前 converter 默认向新图写随机 UUID4；旧 graph 不存在，不能证明仅 UUID 不同，不能改UUID/旧SHA来凑导入。模型数值行为验证与应用信任身份分开。
- **strict mel frontend 门槛仍失败**。当前样本下 frontend 单变量替换 token 未变、native ASR 完整输出匹配，只是一个公开录音/两强制语言的工程证据，不证明普遍无影响、独立CER/WER或设备质量。不得把 generic ZIP 标成当前 APK 可导入或质量发布版。
- 本批不改 Android/catalog/数学补丁、不构建 APK、不访问设备/麦克风/私人音频。新 catalog/APK 身份须另授权与验证。全部权重/ZIP/缓存仍忽略，只提交维护脚本、文档和必要非敏感摘要。
