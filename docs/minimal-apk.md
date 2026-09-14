# 离线 APK（0.2 前台录音开发原型）

当前路线见[通用App路线图](app-roadmap.md)。0.1用户骁龙成功报告不能替代0.2麦克风验收；0.1产物保留于本地 `dist/v0.1/`，历史报告见 `reports/apk/v0.1/`。

## 范围

`org.llmasr.minimal`：Java Activity → JNI → 已修补 MNN CPU Qwen3-ASR。JNI与最终P0的578个原对象合并为一个DSO，使用单份静态libc++，原P0库不改动。新链接产物仍须独立APK进程验证。

- 导入固定清单模型目录，检查全部大小和 SHA-256；模型约 **1.57 GB（十进制）**，不嵌入 APK。
- 内置官方公开中文示例用于工程测试，或使用系统文件选择器选择 WAV。
- WAV 必须是 **16,000 Hz、单声道、PCM16、0.1–30 秒**，文件最大 2 MiB。不自动转换其他格式。
- Chinese / English / auto 三种提示语言，greedy，最多 128 新 token；截断视为失败，不冒充完整结果。
- 输出支持复制。仅新增 RECORD_AUDIO 运行时权限；没有 INTERNET 或全盘存储权限。
- 前台短句录音：授权后再次点击开始；停止转写/取消丢弃；30秒硬限制；离开界面取消尚在采集的录音。
- 每次请求加载/释放模型，单线程队列；不是 persistent warm 引擎。

**未实现**：VAD、输入法、AIDL/HTTP API、前台服务、流式输出、量化、GPU、后台恢复、可靠取消。静音可能输出“嗯。”，这是既有参考模型行为，不能将此版本作为可靠的自动听写输入法。

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


## 0.2 前台录音操作与手动验收

0.2使用相同包名/本地签名，可覆盖安装0.1，**不需要卸载或重新导入已存在的模型**。进程重启后先点击“校验已部署模型”，校验成功才允许录音。不要用不同签名强行替换或卸载来解决未知错误。

1. 点击“开始录音（最多30秒）”，首次按系统提示授权麦克风。拒绝权限不影响示例/WAV入口。
2. 授权完成后再次点击开始，界面显示录音时间；只在前台采集，不在权限返回时自动开麦。
3. 点击“停止录音并转写”；达到30秒也会结束采集并转写。采集结束立即释放麦克风，模型加载期间不会继续录音。
4. 点击“取消录音并丢弃”，或在录音期间返回/切后台/锁屏，取消尚在采集的录音。暂不支持取消已开始的native推理，也不保证后台推理存活。
5. 如设备不支持16kHz单声道PCM采集，显示错误并保留WAV入口；本版未实现44.1/48kHz回退重采样。不自动调整系统麦克风开关。

录音PCM只暂存在有界内存；转写时生成请求私有临时WAV并在事务结束清理。进程被系统强制杀死可能留下临时文件（后续生命周期里程碑处理），不能声称可靠安全擦除。`last-result.json`保存最后一次文本/raw结果，不自动上传；本地debug包不建议处理敏感音频。

### 真实设备验收清单（尚待执行）

- [ ] 同签名覆盖安装，模型仍在；选择示例/WAV回归正常。
- [ ] 首次权限拒绝、再次请求、永久拒绝、一次性授权失效、系统麦克风开关关闭时有提示，无崩溃。
- [ ] 授权完成没有自动录音，再点击后出现系统麦克风指示；说话2–5秒，停止后指示消失且输出正确。
- [ ] 不足0.1秒、普通短句、30秒上限均按界限处理。
- [ ] 取消、返回、切后台、锁屏、旋转/重建时释放录音，不误转写已取消内容。
- [ ] 与其他录音App争用、断开蓝牙设备/通话等中断，返回可恢复状态。
- [ ] 连续至少3次录音/转写与不同语言模式不混入上次文本或音频。

请记录手机/SoC/Android版本与APK版本、问题步骤；分享录音或文本由用户自主决定。上述未打勾项不因host单测/编译通过而自动通过。


### 0.2 录音生命周期的精确边界（审查修正）

启动、取消和转写交接使用同一会话同步gate。取消先获gate时禁止再启动麦克风或发布WAV/转写任务；启动先获gate时，取消在启动调用结束后生效，由采集worker停止并释放。采集循环不持锁。硬件启动调用可能延迟UI取消回调，因此不能承诺onPause返回之前麦克风已同步释放；取消到系统录音指示消失的延迟必须实机测量。

采集释放后先竞争一次转写提交：取消先赢则丢弃；提交先赢则进入不可取消的native推理，后续取消返回“不接受”，不伪装已取消。onPause通过同一gate锁存取消，后续回前台不会复活已取消会话。停止按钮不是取消，可以正常转写。

系统麦克风开关或录音争用有时会返回静音而非错误；当前无数据超时不等同静音检测，不能保证识别这类情况。VAD及设备测试仍待后续验收。
