# 固定 MNN 模型 ZIP 包 v1

此格式只包装 APK 内置的 Qwen3-ASR-0.6B MNN FP16 部署清单，不是任意 ZIP/模型平台。目录导入仍保留。模型与 tokenizer 的取得、许可与来源需自行确认；本文不授权下载或再分发，也不会让包内自报 SHA 成为信任锚。`config.json` / `llm_config.json` 是锁定 SHA 的普通资产，不是本包装清单。

## 根布局与完整清单

ZIP 根恰好下列八个普通文件，无外层目录、目录 entry、README、隐藏文件或其他元数据：

```text
model-package.json
audio.mnn
audio.mnn.weight
llm.mnn
llm.mnn.weight
tokenizer.txt
llm_config.json
config.json
```

将下列完整 JSON 保存为 UTF-8（无 BOM）的 `model-package.json`。名称/大小/SHA 必须逐值等于 APK 信任清单；文件顺序和键顺序不影响匹配。SHA 小写，不接受字符串转义、非 ASCII、重复/未知键、带引号/小数/指数/负数大小、尾 token。JSON 实际展开上限16,384字节。

```json
{
  "schema": 1,
  "modelId": "qwen3-asr-0.6b-mnn-fp16-p0-1",
  "engine": "mnn",
  "files": [
    {"file":"audio.mnn","bytes":2989192,"sha256":"cd6237e0c02003345ba035e0fa6e67fe8658f270978ca8fbd4a60bf27ad5e825"},
    {"file":"audio.mnn.weight","bytes":373185600,"sha256":"1456a76d7039681fbfdd94ffdac486545da62859f80a58b43a6f527f42a72382"},
    {"file":"llm.mnn","bytes":1628400,"sha256":"3ce4f4d93e0fc1d2adb10d834421ae98f421913d85f5238abb6929d7a65ebb5b"},
    {"file":"llm.mnn.weight","bytes":1192493056,"sha256":"001a7838a2420182f0c6855657920d5a45c7b8b0999a7e045ef7ef0955a22ab4"},
    {"file":"tokenizer.txt","bytes":3194212,"sha256":"70e5e435679b6520af2b8f5fd400e30023600321b09ab6153f7215b659b68c07"},
    {"file":"llm_config.json","bytes":1104,"sha256":"8e548c5393334616cffb6e8a751603ad8314d01293aa29765f1f507f0b412b85"},
    {"file":"config.json","bytes":617,"sha256":"fd4127baf9ff5c45aaa2224f65db36e9110088d2aa7223e4853238d31be84e9d"}
  ]
}
```

新增编译期 modelId/engine 映射绑定 canonical `file:bytes:sha256\n` 顺序指纹 `02624d4f3f12ce44ce8d1191d925ff2dddd3eedbe731692716bb017a284521a6`；不修改历史 `reports/p0/mnn-model-manifest.json`。

## 可重复生成：只用 Python 标准库

推荐使用已实现的标准库 CLI：[`scripts/package-mnn-model.py`](../scripts/package-mnn-model.py)，操作与信任边界见 [MNN 打包说明](mnn-model-packaging.md)。默认模式锚定当前 APK 的固定 tuple；generic 模式必须使用不同的 `GENERIC-NOT-IMPORTABLE-` 身份，不使任意新模型进入当前 catalog。CLI 输入源目录只含七资产，外部 policy 不能放在源内；它会生成第八个 JSON、拒绝覆盖并验证最终 ZIP。下面保留历史手动配方作为格式说明，不把历史产物缺失或旧测试冒充本次转换成功。

以下供用户在**已拥有、获准使用的七文件目录**执行；本轮工程测试只对微型合成文件使用相同 ZipInfo 配方，未读取、打包或下载真实权重。输出不能放在源目录内。先保存上面的 JSON，再运行经审阅的代码。代码预检每个源为普通非符号链接文件、实际大小与可信 SHA 匹配，流式写入（不把大权重一次装入 RAM）。输出用 `xb` 创建且可 seek，已有输出不会覆盖。

```python
from pathlib import Path
import hashlib
import json
import shutil
import stat
import zipfile

source = Path("/path/to/owned-model-directory")
output = Path("/path/to/new-model-package.zip")
manifest_path = source / "model-package.json"
manifest = json.loads(manifest_path.read_text(encoding="ascii"))
# 此 JSON 须为上文完整可信版本；不要改 hash 来迁就不同权重。
names = ["model-package.json"] + [e["file"] for e in manifest["files"]]
if len(names) != 8 or len(set(names)) != 8:
    raise ValueError("exactly seven distinct model files required")
for entry in manifest["files"]:
    path = source / entry["file"]
    if path.is_symlink() or not stat.S_ISREG(path.stat().st_mode):
        raise ValueError("regular source required")
    digest = hashlib.sha256()
    count = 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(65536), b""):
            count += len(chunk)
            digest.update(chunk)
    if count != entry["bytes"] or digest.hexdigest() != entry["sha256"]:
        raise ValueError("source does not match trusted manifest")
with output.open("xb") as target:  # seekable; not stdout/pipe/socket
    with zipfile.ZipFile(target, "w", allowZip64=False) as package:
        package.comment = b""
        for name in names:
            path = source / name
            if path.is_symlink() or not stat.S_ISREG(path.stat().st_mode):
                raise ValueError("regular source required")
            info = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = 0o100600 << 16  # Unix regular, owner read/write
            info.compress_type = zipfile.ZIP_STORED  # ZIP_DEFLATED also supported
            info.extra = b""
            info.comment = b""
            info.file_size = path.stat().st_size
            with path.open("rb") as original, package.open(info, "w", force_zip64=False) as entry:
                shutil.copyfileobj(original, entry, length=65536)
```

输入文件在预检与打包期间不能修改；应用仍会独立完整 CRC/SHA 审核，不信任本脚本或包内大小。若生成失败，输出可能是半包：自行确认无程序仍写入后删除该输出并用新路径重建。不要修改旧模型目录或删权重来腾空间。七个资产合计1,573,492,181字节，ZIP32 足够；不要使用 `force_zip64=True`。

## 有意严格的 ZIP32 profile

只支持单磁盘、8条目、STORED 或 raw DEFLATED、flags0/UTF-8 bit11、DOS/Unix producer、普通文件 mode。CEN 和 LFH 的名称、flags、method、CRC、大小必须一致，LFH/数据从0无缝覆盖到 CEN，EOCD 是最后22字节。不接受前缀/尾垃圾、DD（bit3 data descriptor）、ZIP64、extra fields、entry/archive comments、加密、split、自解压、目录、symlink/FIFO/device、未知 host、未知/重复/缺失文件或嵌套路径。

许多合法的通用 ZIP 也会被拒绝：非 seekable 输出常用 DD，默认 `ZipFile.write` 可能附带不符合要求的 metadata，某些工具总用 ZIP64/extra/comment。请从原始已校验七文件用上述固定 ZipInfo/seekable 配方重新生成，不靠改文件后缀、不在线转换、不把未知 ZIP 解到模型根。没有元数据时不声称识别“链接意图”；应用只流式复制固定白名单 payload 到普通 `.part`，从不创建任何链接。

## 空间、取消与失败恢复

输入 ZIP 实际上限1,574,540,757字节；每遍实际展开≤模型总量+16KiB，CEN≤4KiB、8 entries、每名≤128bytes。先复制一份 app 私有 archive（在 model 根外），落包时保守预留完整模型+64MiB，每次写前再检查；完成首遍审核后再按已有文件 SHA 计算剩余复制量。最坏额外占用 `archive + model + 64MiB`，上限3,215,141,802bytes。即使已有模型可完全复用，落包阶段仍可能比理论最优更早拒绝低空间；**不会先删旧模型**，可显式选择原目录入口。页面显示复制 archive 进度与实际 archive 成本，不能把它隐藏在模型复制预算中。

完整首遍 JSON/CEN/LFH/CRC/可信 SHA 全部通过后才发布正式文件。先失败保旧有效文件与证明（maintenance 可能卸载内存 resident）；之后复用已有有效文件，正确新文件逐个发布保留，**不是整包原子回滚**。发布中失败/进程死亡保持 DIRTY、不能 READY，重选 ZIP/目录修复或完整 SHA 恢复；不会从半文件偏移续传。所有 provider/Inflater/ZIP/清理关闭完成后才最终 SHA 与持久证明。固定 archive 残片可下次清理，未知项不递归删除。

30分钟为协作 deadline：仅实际 IO 返回后检查，不保证强制中断慢 provider。取消期间保持共享 owner 与活目录，不提前释放或启动第二任务；真实收尾后才可重试。URI 不持久化、不请求持久 grant、不根据后缀判模式；应用本身无网络权限，云 provider 仍可能自行联网，优先本地文件。

Android 系统 SAF/Binder/grant、真实生命周期/存储性能/掉电、麦克风与 ASR **NOT RUN**。主机 synthetic 测试不等设备验收；本轮 implementation 未构建或交付新 ZIP APK。
