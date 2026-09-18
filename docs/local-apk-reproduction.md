# 从 Git 检出复现 APK（本机验证）

本次以 `39a7923` 为基线，没有修改产品源码、测试或构建脚本，未下载模型权重。新生成 debug 密钥；不承诺 APK 与旧签名包字节相同。

## 已验证结果

- 固定 MNN 归档 SHA、两份模型 JSON 的大小/SHA、公开样例 SHA 全部匹配。
- 三份 MNN 补丁应用成功；prompt/audio 和 Hann 修改后源码 SHA 与 provenance 匹配，生产 omni 不含临时位置 trace。
- 重编 **578/578 MNN 对象的路径及 SHA 与 HEAD 记录完全一致**。
- `libMNN.so`：`10910daddb4e1c10fdc11eb1e5d9d34ab44d338d8aa8fc4f04d3a5968002edb8`，与历史 P0 相同。
- 最终 JNI DSO：`d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`，与历史 APK 记录相同。
- 原始 `build-minimal-apk.sh` 完整执行成功：host 回归、fresh-class mutants、Java/JNI 编译及 ABI 检查、DEX、资源、签名、zipalign 和包检查通过。
- 119 个构建输入 SHA 与当前文件一致；产品源码/测试/构建脚本均未变更。此次为父代理证据核对，不声称新增独立源码审查或真机验收。
- 新 APK 身份见 [当前状态](../reports/apk/status.md)，历史身份见 `reports/apk/pre-local-reproduction/`。

## 1. 精确恢复配置（不需要加载权重）

在仓库根目录执行。利用已追踪的历史有效配置及导出器的序列化格式恢复，并以模型清单哈希作最终判定；不修改清单，不从其他模型猜测配置。

```bash
python3 - <<'PY'
from pathlib import Path
import hashlib, json
p = Path('reports/p0/device-inference-final-patches.txt')
line = next(s for s in p.read_text().splitlines() if s.startswith('P0_EFFECTIVE_CONFIG '))
c = json.loads(line.split(' ', 1)[1])
i = list(c).index('model_type')
manifest = {e['file']: e for e in json.loads(Path('reports/p0/mnn-model-manifest.json').read_text())['files']}
outputs = []
for name, data, indent, tail in [
    ('config.json', dict(list(c.items())[:i]), 2, '\n'),
    ('llm_config.json', dict(list(c.items())[i:]), 4, ''),
]:
    b = (json.dumps(data, ensure_ascii=False, indent=indent) + tail).encode()
    e = manifest[name]
    assert len(b) == e['bytes'] and hashlib.sha256(b).hexdigest() == e['sha256']
    path = Path('models/mnn-16') / name
    if path.exists():
        assert path.read_bytes() == b, f'Refusing to replace {path}'
    outputs.append((path, b))
for path, b in outputs:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_bytes(b)
    print('VERIFIED', path)
PY
```

## 2. 源码、补丁与样例

以下是本次验证的下载位置。所有下载先写入临时文件，核对摘要再解压/放置；不要覆盖已有未知源码或样例。

| 输入 | 来源 | 校验 |
|---|---|---|
| MNN | `https://codeload.github.com/alibaba/MNN/tar.gz/a03b005cf6f888ebf092e4753840f935827f9c36` | SHA-256 `4bfeaff68193a58f097d1d549a9b31f5ef588ba796c9768990e0afd0b439c59d` |
| KleidiAI 1.16.0 | `https://codeload.github.com/ARM-software/kleidiai/tar.gz/refs/tags/v1.16.0` | SHA-256 `57b0a95c559ec6a4b1460bc742af600bfb6cdff8dc39154b376c25347d7b0891`；上游 CMake MD5 `0a9e9008adb6031f9e8cf70dff4a3321` |
| 公开中文样例 | `https://qianwen-res.oss-cn-beijing.aliyuncs.com/Qwen3-ASR-Repo/asr_zh.wav` | SHA-256 `46dbc998c9d1d48111267c40741dd3200f2e5bcf4075f8c4c97f4451160dce50` |

MNN 解压到 `.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36/`，依次使用 `patch --batch --fuzz=0 -p1` 应用仓库的：

1. `patches/mnn-p0.patch`
2. `patches/mnn-whisper-periodic-hann.patch`
3. `patches/mnn-asr-boundary-positions.patch`

直接应用已有 diff，避免补丁生成脚本覆盖历史 provenance。不要应用临时 `instrument_positions.py`。位置补丁 provenance 含历史临时 trace，其整文件 SHA 不能直接当最终生产源码 SHA；本次最终 native 对象及库摘要才是精确复现的核对依据。

样例保存为 `bench/audio/zh-original.wav`。本次上游 WAV 本身已匹配，无需重新采样。

## 3. 必须显式准备 KleidiAI

**首次重编失败不是编译错误，而是上游下载失败后的静默功能降级。** MNN 的 CMake 将 KleidiAI 下载失败记为 warning，并继续生成不含该依赖的 473 对象库；历史是 578 对象。原生库 SHA 门槛正确拒绝了该产物。

将已校验的 KleidiAI 归档解压到：

```text
.work/build/mnn-android/_deps/kleidiai-1.16.0/
```

首次配置可用下面的命令（明确传入工具链，不能先以默认 host 配置空目录）：

```bash
bash scripts/nix-env.sh native bash -euo pipefail -c '
  cmake -S .work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36 \
    -B .work/build/mnn-android -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
    -DMNN_KLEIDIAI=ON \
    -DKLEIDIAI_SRC_DIR="$PWD/.work/build/mnn-android/_deps/kleidiai-1.16.0"
  bash scripts/build-mnn-p0.sh android
'
```

本次重试是在原 Android CMakeCache 上补 `KLEIDIAI_SRC_DIR` 后运行原脚本；上述空目录首次配置形式未另作第二次全新目录验证。首次配置后脚本补齐 Release/LLM/audio 等参数并编译。不修改历史 `native-artifact-sha256.txt` 来接受不匹配的库。

## 4. 构建与交付边界

先备份本地历史 `reports/apk/` 与已有 APK；构建脚本会覆盖生成报告。新检出还需要创建测试使用的临时父目录：

```bash
mkdir -p .work/build .work/model-review-fixes
bash scripts/nix-env.sh apk bash scripts/build-minimal-apk.sh
```

若命令用 `tee` 保存日志，外层明确使用 `bash -o pipefail`，不要假定登录 shell 是 Bash。本次早期尝试在 fish 中设置 `set -o pipefail` 失败，导致管道曾报告假 exit0；最终重试使用 `bash` 脚本及 `set -euo pipefail`，exit0 与 APK_READY/所有检查互相印证。

新密钥由原构建脚本在 `.cache/android-signing/debug.p12` 首次生成。密钥、源码归档、音频、模型配置和编译产物都保持 Git 忽略。新签名不能直接覆盖旧签名安装，不要为绕过冲突自动卸载，以免丢失模型/私人结果。

没有执行设备、麦克风、SAF、IME 或 JNI 推理验收。精确复现 native 不能替代当前 APK 的设备验收。APK 不含模型；手机运行仍需与清单一致的七个模型文件。
