# 最小离线 APK（开发原型）

## 范围

`org.llmasr.minimal`：Java Activity → JNI → 已修补 MNN CPU Qwen3-ASR。JNI与最终P0的578个原对象合并为一个DSO，使用单份静态libc++，原P0库不改动。新链接产物仍须独立APK进程验证。

- 导入固定清单模型目录，检查全部大小和 SHA-256；模型约 **1.57 GB（十进制）**，不嵌入 APK。
- 内置官方公开中文示例用于工程测试，或使用系统文件选择器选择 WAV。
- WAV 必须是 **16,000 Hz、单声道、PCM16、0.1–30 秒**，文件最大 2 MiB。不自动转换其他格式。
- Chinese / English / auto 三种提示语言，greedy，最多 128 新 token；截断视为失败，不冒充完整结果。
- 输出支持复制。模型仅本地运行；无 INTERNET、RECORD_AUDIO、全盘存储权限。
- 每次请求加载/释放模型，单线程队列；不是 persistent warm 引擎。

**未实现**：录音、VAD、输入法、AIDL/HTTP API、前台服务、流式输出、量化、GPU、后台恢复、可靠取消。静音可能输出“嗯。”，这是既有参考模型行为，不能将此版本作为可靠的自动听写输入法。

普通 APK 的内存/性能要以 `reports/apk/` 的实测为准，不能套用 native-shell 结果。P0 的严格前端数值门槛、独立人工语料和窗口语义限制仍见 [P0 报告](../reports/p0/report.md)。

## 构建

前提：P0 固定源码、补丁、模型清单与公开示例已按项目流程生成；`.work/build/mnn-android/libMNN.so` 必须是全部最终补丁重编后的运行库。

```bash
# 已有最终MNN库时无需重新导出/构建模型
bash scripts/nix-env.sh apk bash -euo pipefail -c '
  bash scripts/test-minimal-apk.sh
  bash scripts/build-minimal-apk.sh
'
```

工具链固定于 flake.lock：SDK platform 35、build-tools 35.0.0、NDK 28.2.13676358、JDK17；通过 aapt2 → javac → d8 → zipalign → apksigner 生成，不依赖 Gradle/Maven。

输出：`dist/qwen-asr-minimal-debug.apk`。minSdk29、targetSdk35、arm64-v8a only。

本地 debug keystore：`.cache/android-signing/debug.p12`，不入 Git。此为 **debuggable 开发包**，不是正式签名发布版本，勿用于敏感数据或直接上架。换机器重新生成 key 不能覆盖安装旧签名包；不要盲目卸载，以免删除已导入模型。

Nix 提供锁定环境，不表示完全 sandbox/offline APK 构建或跨机器逐字节相同（debug证书/ZIP时间戳会不同）。首次 SDK/JDK 实现需要网络；准备好工具链与P0产物后，本脚本不联网。

## 使用

1. 将 `models/mnn-16/` 中以下文件复制至手机用户可选的普通目录：
   `config.json`, `llm_config.json`, `audio.mnn`, `audio.mnn.weight`, `llm.mnn`, `llm.mnn.weight`, `tokenizer.txt`。
2. 安装 APK，点击“导入模型目录”，由用户在系统选择器中指定目录。
3. 应用复制到内部私有存储，按打包清单校验。成功文件在重试时跳过；失败文件不生效。
4. 点击“转写内置中文示例”或选择符合要求的 WAV。保持屏幕解锁、应用在前台，关闭其他高内存应用；短示例首次校验与加载可能需较长时间。
5. 完成后读取或复制结果。全链路每请求释放模型，全进程任务所有权覆盖文件导入/音频规范化/JNI/报告，防止Activity重建后竞争共享文件。运行期间按钮禁用，系统仍可能因内存不足终止进程。

需要约 1.6GB 应用内存储；如果原模型目录也保留，约需两份文件空间。重导入替换文件时还需最大单文件约1.2GB临时空间。推理RAM预算沿用P0约3.1GiB，仅为准备参考，不保证所有手机可运行。

应用私有 `files/last-result.json` 在每个任务开始时写入UUID与pending，结束时写入对应complete/failed状态；转写完成保存指标和raw文本。pending不是成功，模型操作不是推理证据。对 auto 模式，显示仅去掉协议字段，不执行官方重复归一化，因此不声称与官方完整评分 parser 等价。Android native 崩溃/OOM 不能被 Java catch 捕获，可能只留下pending结果。

## 开发设备测试（原授权设备）

```bash
# 必须先有已授权的 100.64.0.3:33317 连接及P0模型部署
bash scripts/nix-env.sh adb python3 scripts/deploy-minimal-apk.py --run-sample
```

脚本安装本项目包，停止本项目上次进程，逐个检查 P0 目录模型 SHA，在手机上通过 `run-as` 复制进本app私有数据；不读取个人数据。随后启动应用，仅在当前前台确为本app且按钮可见时点击内置中文示例。不绕过锁屏、不设置输入法、不修改全局设置。必要时由用户手动解锁。

预期正文：`甚至出现交易几乎停滞的情况。`。验收还要求普通 app UID ≥10000、正常结束且未截断，结果保存 `reports/apk/device-sample-result.json`。测试只代表单条公开工程样本，并非人工独立准确率或持续热稳定测试。

## 验证产物

- `reports/apk/java-tests.txt`：有界 WAV/协议纯Java测试。
- `reports/apk/signature.txt` / `badging.txt` / `permissions.txt` / `contents.txt`：包签名、平台/ABI、权限和内容。
- `reports/apk/artifact-sha256.txt` / `build-input-sha256.json`：APK、native库与构建输入身份。
- `reports/apk/device-deployment.json` / `device-sample-result.json`：只有实际部署/推理成功后才存在有效证据。
- `reports/apk/independent-review.md`：独立只读审查，不替代编译和实机测试。
