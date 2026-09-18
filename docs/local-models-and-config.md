# 本地模型、配置文件与非 Git 产物说明

本文说明仓库没有提交的大文件和本地配置：**放在哪里、由什么生成、哪些需要一起迁移、哪些不能公开**。内容依据当前脚本、本地目录及 P0 记录核对；不是从零部署的一键脚本，也不代表重新执行了模型转换或设备验收。

所有路径均相对于仓库根目录，不依赖某台机器的用户名或绝对路径。可通过下面的命令定位：

```bash
cd /path/to/llm-asr  # 替换成自己的仓库目录
ROOT=$(git rev-parse --show-toplevel)
printf '%s\n' "$ROOT"
```

## 1. 先看结论

- **原始模型**：`models/raw/Qwen3-ASR-0.6B/`，从 ModelScope 固定版本下载，包含原始权重及官方配置。
- **手机实际使用的模型**：`models/mnn-16/`，由固定版本、打过项目补丁的 MNN 工具导出；不是直接下载的手机模型包。
- **手机所需配置**：同目录的 `config.json`、`llm_config.json`，必须与权重、tokenizer 配套，不能拿原始目录中的同名配置替换。
- **部署文件身份**：`reports/p0/mnn-model-manifest.json`，记录七个部署文件的大小及 SHA-256，已提交 Git。
- **本地签名密钥**：`.cache/android-signing/debug.p12`，由 APK 构建脚本首次生成，不属于模型配置，不能提交 Git。
- **仅克隆 Git 仓库不会获得模型、虚拟环境、编译器输出、APK 或签名密钥**。仓库保存脚本、版本锁、补丁和必要的文本验证证据。

这里的“非 Git 追踪”主要指被 `.gitignore` **主动忽略**的文件。因此，`git status` 显示干净，不表示这些文件不存在。

## 2. 本地目录总览

| 位置 | 内容与生成方式 | Git 状态 / 迁移建议 |
|---|---|---|
| `models/raw/Qwen3-ASR-0.6B/` | `model-tools/download.py` 下载的官方模型快照 | 忽略；可按锁文件重新下载，重新转换时需要 |
| `models/mnn-16/` | MNN 图、外置权重、tokenizer、运行配置及导出辅助文件 | 忽略；只在手机运行时，优先迁移七个部署文件 |
| `.work/onnx-audio-p0/` | encoder ONNX 图、参考数组、native 验证输入输出、`export.json` | 忽略；用于转换/数值验证，不需要导入手机 App |
| `.work/sources/` | 固定版本的 MNN、Qwen3-ASR 源码解压目录与源码归档 | 忽略；来源与归档哈希在 `model-tools/source-lock.json` |
| `.work/build/mnn-host/` | CMake/Ninja 生成的主机 MNN 转换器和库 | 忽略；由 `scripts/build-mnn-p0.sh host` 构建 |
| `.work/build/mnn-android/` | Android ARM64 MNN runtime 等 | 忽略；由 `scripts/build-mnn-p0.sh android` 构建 |
| `model-tools/.venv/` | Python 模型工具依赖及本机兼容修补 | 忽略；根据 `model-tools/pyproject.toml`、`uv.lock` 重建 |
| `.cache/nix-env/<hash>/` | `scripts/nix-env.sh` 复制的 `flake.nix` / `flake.lock` 小型镜像 | 忽略；脚本按两份文件内容的 SHA-256 自动建立 |
| `.cache/android-signing/debug.p12` | `scripts/build-minimal-apk.sh` 生成的本地 debug 签名身份 | 忽略；需要同签名升级时单独安全备份，勿公开 |
| `.work/build/minimal-apk/` | Java/JNI、资源、资产与打包中间产物 | 忽略；APK 构建脚本生成 |
| `dist/` | 当前及历史本地 APK | 忽略；当前输出为 `dist/qwen-asr-minimal-debug.apk` |
| `bench/audio/` | 公开样本及工程测试衍生音频 | 忽略；由 `model-tools/prepare_smoke_audio.py` 准备，不是模型权重 |
| `.pi/`、`.pi-subagents/` | 本地代理任务、会话与中间审查数据 | 忽略；不属于模型重建必需输入，不应整体上传 |

`models/`、`.work/`、`.cache/` 的目录级忽略也会排除其中的 JSON 文件。JSON 本身不一定敏感；它们在这里被忽略，是因为属于下载快照或本地派生产物。`.gitignore` 还单独防护了权重、编译产物、`.env`、签名密钥等文件类型。

## 3. 原始模型：从哪里来，配置如何产生

### 3.1 固定来源

- 模型：`Qwen/Qwen3-ASR-0.6B`
- 平台：ModelScope
- revision：`4ce9cc728b473a5aedbe7b6e1ea45646316824dc`
- 下载锁：[`model-tools/model-lock.json`](../model-tools/model-lock.json)
- 下载入口：[`model-tools/download.py`](../model-tools/download.py)

锁文件保存允许下载的文件名、字节数和 SHA-256。下载脚本使用固定 revision，而不是每次解析最新 `master`。锁文件中的 `source` 是来源记录，不是脚本实际下载时使用的浮动版本。

在仓库根目录运行：

```bash
python3 model-tools/download.py
# 默认输出 models/raw/Qwen3-ASR-0.6B/
```

需要 Python 3、`curl`、网络和足够磁盘空间。已有文件只有在大小与 SHA-256 都匹配时才跳过；下载写入 `.part`，校验成功后才改名为正式文件。脚本支持自定义 `--output`，但当前其他转换脚本使用默认路径；只改下载路径不会同步修改整条转换链。

### 3.2 文件用途

| 文件 | 用途 / 生成来源 |
|---|---|
| `model.safetensors` | 官方原始 checkpoint；锁定大小为 **1,876,091,704 bytes** |
| `config.json` | 官方模型结构配置，包含音频 encoder 与文本 decoder 等定义 |
| `configuration.json` | 官方 ModelScope 侧配置 |
| `generation_config.json` | 官方生成参数配置 |
| `preprocessor_config.json` | 官方音频预处理配置 |
| `tokenizer_config.json`、`vocab.json`、`merges.txt` | 官方 tokenizer 配置、词表及合并规则 |
| `chat_template.json` | 官方对话/音频提示模板 |
| `README.md` | 该固定模型快照的模型说明 |

**这些 JSON 是随上游模型下载的，不是 App 启动时生成，也不是开发者手工填入的本机配置。** 原始目录总计约 1.88 GB（十进制），不应直接交给 App 的模型导入页面。

## 4. MNN 部署模型：七个文件必须配套

当前位置：`models/mnn-16/`。当前是 **16-bit 浮点权重存储基线**，不是已完成的 INT4 发布模型；文件夹名也不表示所有计算都以 FP16 执行。

| 部署文件 | 当前字节数 | 作用 |
|---|---:|---|
| `audio.mnn` | 2,989,192 | 音频 encoder 计算图 |
| `audio.mnn.weight` | 373,185,600 | encoder 外置权重 |
| `llm.mnn` | 1,628,400 | 文本 decoder 计算图 |
| `llm.mnn.weight` | 1,192,493,056 | decoder 等外置权重 |
| `tokenizer.txt` | 3,194,212 | MNN 导出的 tokenizer 数据 |
| `llm_config.json` | 1,104 | 模型结构、音频 token、提示模板、权重布局等元信息 |
| `config.json` | 617 | 运行入口、相对文件路径与推理参数 |
| **合计** | **1,573,492,181** | 约 **1.57 GB / 1.47 GiB** |

以上身份以 [`reports/p0/mnn-model-manifest.json`](../reports/p0/mnn-model-manifest.json) 为准。重新导出可能改变大小或哈希，不能仅凭文件名认为是同一版本。

本地还存在这些辅助文件，它们**不在 App 的七文件导入清单内**：

- `export_args.json`：导出器保存的参数记录，包含本机输入/输出/转换器绝对路径。不是 App 配置，也不是凭据；公开前仍应注意路径隐私。
- `llm.mnn.json`：转换器生成的图描述/中间表示，不是运行入口配置。
- `onnx/llm.onnx`：decoder 导出中间图。
- `.export.log`：导出日志。

不要把这几个辅助文件误认为第八个必需部署文件，也不要仅复制 `.mnn` 而遗漏 `.mnn.weight`。

## 5. 实际生成链路与配置来源

```text
model-tools/model-lock.json
        │ download.py：固定版本下载、大小/SHA-256 校验
        ▼
models/raw/Qwen3-ASR-0.6B/
        │ 固定 MNN 源码 + 项目补丁 + Python 工具环境
        ├─ encoder 单独导出/验证 → .work/onnx-audio-p0/audio.onnx
        │                         → 匹配版本 MNNConvert → audio.mnn[.weight]
        └─ export_decoder.py → llm.mnn[.weight] + tokenizer.txt
                              + llm_config.json + config.json + export_args.json
        ▼
models/mnn-16/ 的七个部署文件
        │ export_decoder.py 的 finalize：逐文件哈希
        ▼
reports/p0/mnn-model-manifest.json（Git 追踪）
        │ build-minimal-apk.sh 复制到 APK assets
        ▼
APK assets/model-manifest.json → App 导入时检查同一批文件
```

### 5.1 工具和补丁前提

当前固定 MNN 源码目录：

```text
.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36/
```

另有 Qwen3-ASR 源码快照：

```text
.work/sources/Qwen3-ASR-7c6daf77a2421100f5fb066495372c00129d39ff/
```

源码归档版本和 SHA-256 记录在 [`model-tools/source-lock.json`](../model-tools/source-lock.json)。下载、解压源码本身不是 `download.py` 的职责，`build-mnn-p0.sh` 也不会自动获取源码。

关键补丁及应用脚本：

| 脚本 | 保存的补丁 | 作用 |
|---|---|---|
| `model-tools/patch_mnn.py` | `patches/mnn-p0.patch` | ASR prompt、短音频卷积形状、有效特征长度等导出修正 |
| `model-tools/patch_whisper_hann.py` | `patches/mnn-whisper-periodic-hann.patch` | native Whisper 前端使用周期 Hann 窗 |
| `model-tools/patch_asr_positions.py` | `patches/mnn-asr-boundary-positions.patch` | ASR 音频边界 token 的位置计数修正 |

补丁脚本检查固定源码片段，不应对已修补目录反复执行；它们还会写入补丁和 provenance 报告。修改 native 源码/头文件后必须重编 runtime，不能混用旧二进制。

环境与构建入口（均从仓库根目录运行）：

```bash
# 创建/恢复锁定的 Python 环境；首次执行需要下载依赖
bash scripts/nix-env.sh model uv sync --project model-tools --frozen

# 以下命令以固定源码已解压、必要补丁已应用为前提
bash scripts/nix-env.sh native bash scripts/build-mnn-p0.sh host
bash scripts/nix-env.sh native bash scripts/build-mnn-p0.sh android
```

`model-tools/fix_local_wheels.py` 记录了当前主机所需的兼容处理：延后可选日语对齐依赖加载、清理 MNN ELF 的可执行栈标记。需要时使用 `model-tools/.venv/bin/python` 执行该脚本，并重新验证 import/测试；不能把原生 wheel 的版本号相同当作主机兼容性已通过。Python 的 `MNN==3.6.1` wheel 也不能替代匹配源码构建的主机 `MNNConvert`。

### 5.2 为什么当前模型采用分阶段导出

项目提供两类入口：

- `model-tools/export_mnn.py --bits 16`：全模型导出入口，先检查 Python encoder、ONNX 动态形状和 prompt 对齐报告，再调用上游 `llmexport.py`。支持 4/8/16 参数不代表其他位宽已导出、验证或可以直接导入当前 App。
- `model-tools/export_decoder.py`：当前低内存流程使用的 decoder-only 入口。先确认音频配置已捕获，再释放 encoder，仅导出 decoder/tokenizer/config，并验证已通过门槛的 `audio.mnn` 与权重没有变化。

实际历史中，全模型同时转换因主机内存/换页压力被停止，之后改为：

1. `onnx_audio_gate.py export` 只加载 encoder，生成 `.work/onnx-audio-p0/audio.onnx`、输入与官方参考数组，以及 `export.json`。
2. `onnx_audio_gate.py validate` 用 ORT 在同一 session 内切换输入长度，写出 `reports/p0/onnx-audio-parity.json`。
3. 单独运行匹配版本的 `.work/build/mnn-host/MNNConvert`，将这个 encoder ONNX 转为 FP16 权重存储的 `audio.mnn` / `audio.mnn.weight`，不启用 transformer fusion。
4. `mnn_audio_gate.py prepare` 生成 native 输入；编译运行 `native/p0/audio_gate.cpp` 得到 `actual-*.bin`，再通过 `mnn_audio_gate.py validate` 写出 MNN encoder 对齐报告。
5. `export_decoder.py` 读取上述门槛，生成余下部署文件，并生成清单。

转换器调用和 native gate 编译/执行并没有封装成一个完整恢复脚本。因此，**不能只执行 `export_decoder.py` 就从空目录生成全部七文件**，也不能把 Git 中已有的历史报告当作新机器本次验证通过。encoder 原始/修补数值检查还对应 `check_audio_parity.py`，prompt 检查对应 `check_prompt.py`；应对本次输入重跑并保留失败证据。

各阶段会覆盖相应本地产物或文本报告。重建前备份当前已验证产物，不要在唯一可用模型目录上做无备份的实验。完整历史及门槛边界见 [`progress.md`](../progress.md) 和 [P0 报告](../reports/p0/report.md)。

### 5.3 `config.json`：运行参数如何生成

第一步由本地固定 MNN 导出器 `transformers/llm/export/llmexport.py` 的 `LlmExporter.export_config(True)` 写出基础配置。第二步由 `export_decoder.py`（全模型流程则是 `export_mnn.py`）覆盖项目的运行策略。

当前关键字段如下；这是说明用的**节选**，不是让用户覆盖原文件的完整模板：

```json
{
  "llm_model": "llm.mnn",
  "llm_weight": "llm.mnn.weight",
  "tokenizer_file": "tokenizer.txt",
  "backend_type": "cpu",
  "thread_num": 2,
  "precision": "high",
  "memory": "low",
  "sampler_type": "greedy",
  "max_new_tokens": 128,
  "async": false,
  "mllm": {
    "backend_type": "cpu",
    "thread_num": 2,
    "precision": "high",
    "memory": "low"
  }
}
```

文件还保留上游生成的 temperature/top-k/top-p 等字段，但当前选择的是 greedy，不能仅凭这些字段的存在宣称启用了随机采样。配置里的模型路径是同目录相对文件名，不应替换为本机绝对路径或手机外部路径。

### 5.4 `llm_config.json`：结构与权重布局如何生成

同样由 `LlmExporter.export_config(True)` 写出，不过内容来自导出器读取的原始模型配置、模型映射和转换阶段产生的元信息，包括：

- `model_type=qwen3_asr`、`hidden_size=1024`、`layer_nums=28`；
- `is_audio=true`、`audio_type=qwen3_asr` 和音频边界/pad token；
- mRoPE、最大位置长度、attention 配置；
- 修补后的 Jinja ASR prompt 模板、EOS 与默认语言；
- `tie_embeddings` 的权重偏移、存储位宽等转换产物相关参数。

**不能手工猜测权重偏移或从另一份模型复制此文件。** 它与 `llm.mnn.weight` 等部署文件是同一批产物。

默认 `asr_language` 是 `Chinese`；JNI 在创建模型后通过 `set_config` 设置本次语言以及 CPU、线程数、greedy 等策略，不会因此改写模型目录里的 JSON。详见 `native/apk/asr_jni.cpp`。

### 5.5 `export_args.json` 与部署清单的区别

- `export_args.json`：上游导出器的 `export_config` 保存参数对象，供追溯使用；可以包含主机路径，不需要导入 App。
- `reports/p0/mnn-model-manifest.json`：项目 `export_decoder.py` 的 `finalize()` 遍历七个部署文件，记录字节数与 SHA-256；App 用它确认文件身份。

如果部署文件已完整存在，下面的命令**不加载或重新导出模型**，但会重新读取全部文件并**覆盖已追踪的部署清单**：

```bash
python3 model-tools/export_decoder.py --finalize-existing
```

这个命令只检查产物完整性、部分配置字段并重新计算哈希，**不是数值验证，也不能证明未知来源模型安全或正确**。不要用它更新清单来绕过哈希不匹配。清单中的 `kind` 是脚本写入的历史阶段标签，不会自动跟踪后续设备验收状态；实际验收结论应查对应报告。

## 6. APK 与手机上的位置

### 6.1 构建时

`scripts/build-minimal-apk.sh` 将已追踪的清单复制到：

```text
.work/build/minimal-apk/assets/model-manifest.json
                        ↓ 打包
APK 内 assets/model-manifest.json
```

APK **不嵌入约 1.57 GB 的模型权重**。构建脚本检查本地两份配置与清单一致，以及运行配置没有改用其他模型文件路径。

改动部署配置哪怕只改变 JSON 格式，也会改变文件 SHA-256。完成必要验证、更新清单后，还必须重新构建含新清单的 APK；旧 APK 仍会按旧哈希拒绝导入修改后的文件。

### 6.2 手机导入与内部副本

将 `models/mnn-16/` 中七个部署文件复制到手机用户可选目录，然后通过 App 的“模型管理 → 选择模型目录 / 修复导入”选择该目录。外部目录没有固定路径，由用户决定。

App 将文件校验并复制到 `Context.getFilesDir()/model/`，主用户下通常为：

```text
/data/user/0/org.llmasr.minimal/files/model/
```

以 Android API 实际返回目录为准；多用户环境不一定是用户 0。导入会使用 `.part` 临时文件，校验后发布；App 不从原始 safetensors 现场转换模型，也没有自动下载模型的功能。

历史 native-shell P0 测试目录为 `/data/local/tmp/qwen-asr-p0/model/`，与 App 私有目录**不是同一个位置**。本文未访问或核验手机当前文件状态。当前导入、取消、删除行为见 [模型管理说明](model-management.md)。

## 7. 本地配置、密钥与迁移边界

### Nix 镜像配置

`scripts/nix-env.sh` 对 Git 中的 `flake.nix` 和 `flake.lock` 内容计算 SHA-256，将两者复制到 `.cache/nix-env/<hash>/`，然后从该小目录启动 devShell。这样避免把多 GB 模型当作 path flake 源复制进 Nix store。

镜像可重新生成；要更改工具链应修改仓库中的源文件，不要只编辑缓存副本。缓存中的 flake 不是另一套需要手工维护的模型配置。

### Debug 签名密钥

`scripts/build-minimal-apk.sh` 首次发现 `.cache/android-signing/debug.p12` 不存在时，使用 `keytool` 生成 RSA/PKCS12 debug keystore，并设置目录/文件权限；后续构建复用它。

- 它不从 Git 恢复，也不是 `model-lock.json` 可以重建的原身份。
- 重新生成会得到不同签名，通常不能直接覆盖安装旧签名 APK。
- 需要继续同签名升级时，应通过独立安全渠道备份/迁移，不能提交仓库或贴进文档。
- 这是开发签名配置，不是正式发布密钥方案。不要为解决签名不一致而盲目卸载 App，卸载可能删除已导入模型及本地数据。

### 换机器时带什么

| 目标 | 必需的额外材料 |
|---|---|
| 只在手机导入运行 | 与 APK 清单一致的七个 MNN 部署文件，以及对应 APK |
| 继续构建同签名开发 APK | 固定源码/补丁、对应 Android runtime 或重建条件、模型配置与清单、公开样本、原 debug keystore |
| 重新转换模型 | 原始模型快照、锁定 Python 环境、固定/修补的 MNN 源码及匹配转换器；重新跑本次验证 |
| 只阅读/修改 Java 或文档 | Git 源码；具体编译/测试仍需对应工具链，部分检查可能依赖本地产物 |

模型和编译缓存可恢复不等于全部可随意删除：本地签名身份无法按原样重建，未备份的用户音频/转写更不属于可丢弃缓存。本文不要求清理任何本地目录。

## 8. 只读检查命令

从仓库根目录检查哪些文件被忽略、哪些清单已被 Git 追踪：

```bash
git check-ignore -v models/raw/Qwen3-ASR-0.6B/config.json \
  models/mnn-16/config.json .cache/android-signing/debug.p12

git ls-files model-tools/model-lock.json model-tools/source-lock.json \
  model-tools/uv.lock reports/p0/mnn-model-manifest.json
```

以下命令只读校验原始模型及七个部署文件，**不会下载、改写配置或更新清单**。会顺序读取约 3.45 GB 数据，耗时取决于磁盘：

```bash
python3 - <<'PY'
import hashlib
import json
from pathlib import Path

checks = [
    ('model-tools/model-lock.json', 'models/raw/Qwen3-ASR-0.6B', 'path'),
    ('reports/p0/mnn-model-manifest.json', 'models/mnn-16', 'file'),
]
failed = False
for manifest, directory, name_key in checks:
    for item in json.loads(Path(manifest).read_text())['files']:
        name = item[name_key]
        if Path(name).name != name or name in ('.', '..'):
            raise ValueError('Invalid manifest filename')
        path = Path(directory) / name
        ok = path.is_file() and path.stat().st_size == item['bytes']
        if ok:
            digest = hashlib.sha256()
            with path.open('rb') as stream:
                for block in iter(lambda: stream.read(4 * 1024 * 1024), b''):
                    digest.update(block)
            ok = digest.hexdigest() == item['sha256']
        print(('OK  ' if ok else 'FAIL'), path)
        failed |= not ok
raise SystemExit(1 if failed else 0)
PY
```

哈希一致说明文件与清单相符，不等于当前手机推理、准确率或性能已验收。验证模型时不需要读取、输出或上传任何本机私钥。
