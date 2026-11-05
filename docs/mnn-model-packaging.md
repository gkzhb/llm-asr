# MNN 七资产 ZIP 打包 CLI

入口：[`scripts/package-mnn-model.py`](../scripts/package-mnn-model.py)。仅 Python 标准库，Python ≥3.9、POSIX 本地文件系统（需 `O_NOFOLLOW`、目录 fd 和同目录 hard-link 原子发布）。不下载、不安装、不执行模型代码，不读取录音，不转换模型；不修改 APK、catalog 或可信清单。

**打包成功只证明字节和 ZIP 格式，不证明模型数学、识别质量或设备运行正确。** 任意 MNN 目录不一定恰有七资产；本 CLI 有意只实现[七资产 schema 1](model-package-format.md)，不是任意目录归档器。

## 1. 默认：当前 APK 固定模型

准备一个只含七个部署资产的目录（不要把导出器的 ONNX、日志、`export_args.json` 或旧 ZIP 放进去）。源目录不包含 `model-package.json`；工具从外部受控策略生成它。输出父目录必须已存在，输出必须是新路径且在源目录外。

```bash
python3 scripts/package-mnn-model.py \
  --source /path/to/seven-verified-assets \
  --output /path/to/new-current-model.zip
```

默认 `--mode current-app` 从仓库 `reports/p0/mnn-model-manifest.json` 读取七元组，先核 canonical `file:bytes:sha256\n` 的 SHA-256 等于生产 `FixedModelPackage.FINGERPRINT` 对应固定值，再逐资产检查。不会把任意包内自报 SHA 当作 Android 信任。可附 `--manifest /external/trusted-policy.json`，但它必须与当前固定 policy 完全一致，不是覆盖信任的参数。

本轮未获得七个真实部署资产，因此上面是操作方式，**不是已产出当前可导入 ZIP 的声明**。实际转换阻塞见[当批报告](../reports/mnn-package-bt4/status.md)。不要修改历史清单来接受不同的重新导出结果。

## 2. 显式 generic：格式可复用，当前 App 不支持

用于其他已独立验证的七资产 MNN 模型，或者字节不同的重新导出。调用者先在源目录**之外**提供经自己审计/授权的 policy。CLI 不自动扫描生成信任，不下载 policy，也不证明 policy 的外部可信性。完整字段如下（SHA 和大小必须替换为真实经审计值；不能用示意值冒充模型）：

```text
{
  "schema": 1,
  "modelId": "GENERIC-NOT-IMPORTABLE-your-distinct-release",
  "engine": "mnn",
  "files": [
    七条 {"file": "安全根文件名", "bytes": 正整数, "sha256": "64位小写SHA256"}
  ]
}
```

```bash
python3 scripts/package-mnn-model.py \
  --mode generic \
  --manifest /path/to/external-audited-policy.json \
  --source /path/to/exact-seven-assets \
  --output /path/to/new-GENERIC-NOT-IMPORTABLE-model.zip
```

`modelId` 强制 `GENERIC-NOT-IMPORTABLE-` 前缀；不得套用当前固定 modelId。stdout JSON 的 `compatibility` 也明确为 `GENERIC-NOT-IMPORTABLE`。即使 schema、CRC、SHA 与 ZIP profile 都合法，**当前 APK 仍会拒绝该 tuple/modelId**。支持新 catalog/新 APK 是另一个需要授权、源码审查与验收的任务；本 CLI 不扩大应用支持范围。

策略拒绝重复/未知键、重复/保留/不安全名字、路径、非整数/零/负数大小、坏 SHA、`.part` 名冲突及过大资产。文件名为 1–128 个 ASCII `[A-Za-z0-9._-]`，首字符为字母或数字；合计名字≤1024字节。资产顺序由 policy 决定并保留，策略键顺序不影响结果。

## 3. 安全与确定性

- 输入必须是静止的、普通非 symlink 文件；源目录及每层祖先不能是 symlink。目录内**恰好**七个声明资产；额外、缺失、目录、FIFO 等均拒绝。policy 本身也必须是外部普通非 symlink 文件。
- 目录 fd 和 `O_NOFOLLOW|O_NONBLOCK` 防止路径链接/特殊文件被误读，FIFO 不会在打开时挂起。仍要求调用期间无其他 writer，不宣称恶意同权限并发修改或不可信文件系统下的完整快照隔离。
- 首遍预检大小/SHA；写入时再次流式核大小/SHA；成包后再次独立完整读取每个 payload，核 CRC/SHA，并检查 LFH/CEN/EOCD 的精确 profile。
- 大文件每次读取≤65,536字节；不把权重加载入内存。权重共三遍读取（预检、写入、成包验证），另有整包 SHA 一遍。预留输出大小的可用磁盘；不删除旧模型来腾空间。
- seekable 临时普通文件与输出位于同一父目录，mode `0600`。确定性 metadata：2024-01-01 00:00:00、Unix regular `0600`、STORED、固定顺序；无 DD、extra、comment、ZIP64、前后垃圾或目录 entry。writer 保守限制总包＜2 GiB（Python stdlib 的非 ZIP64 安全阈值），不承诺支持所有理论 ZIP32 大小。
- 成功验证并 fsync 临时文件后，用 hard-link 原子发布到**不存在的**输出名；现有普通文件、目录或 dangling symlink 均不覆盖，并发同名发布最多一个成功。异常时只清理本次随机 `.part`，不删除用户输出。进程被强杀可能留下 `.part`；确认进程已退出后人工处理，不自动递归清理。未承诺掉电下目录项持久性。
- stdout 成功 JSON 包含输出路径、整包 size/SHA、精确条目、七资产 size/SHA、逐 payload CRC、tuple SHA、模式和兼容性。错误 stderr `ERROR:`，exit1；参数用法错误由 argparse 返回 exit2。把 stdout 重定向到源目录外的新报告时也应避免覆盖旧报告；CLI 不管理 shell 的重定向目标。

## 4. 验证入口与边界

```bash
python3 -B tests/mnn_model_package_test.py
```

测试真实启动 CLI，使用微型生成数据：确定性、ZIP metadata/CRC/SHA、无覆盖、并发发布、源/祖先链接、特殊文件、缺失/额外项、路径/重复/坏大小/坏 hash/坏 policy、当前 APK 信任不能被 synthetic 自报绕过、流式读取上限、成包篡改拒绝。不会加载真实权重。

当批还在隔离输出内 fresh 编译真实 `ModelZipArchive` / `FixedModelPackage` 等生产源码，对 CLI 生成的**合成策略** ZIP 执行 `prepare` 完整 CRC/SHA 和逐 payload 读取；并验证生产 `FixedModelPackage.mnn` 拒绝该合成 tuple。这是主机格式互操作证据，不是真实模型包可导入证明，更不是 Android SAF/设备验收。详细结果与 NOT RUN 项见当批报告。
