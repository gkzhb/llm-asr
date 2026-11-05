# v1v：全新分阶段 FP16 重导出及三方对照

任务 `llm-asr-v1v`；源码基点 `a5a3b45adfab3daa0a0ab1ca242a997467671e06`。本报告不包含权重、音频、私人内容或本机路径。模型与完整忽略证据不纳 Git。本轮没有 APK/catalog/信任清单/量化/数学阈值改动，没有设备部署。

## 真实转换与当前验证

实际从官方 HF snapshot revision `5eb144179a02acc5e5ba31e748d22b0cf3e303b0` 的 raw safetensors 新导出，不复制上一批 MNN。十原始文件重核 SHA，其中九共享文件与历史 ModelScope 锁相同。prepared tree 按当前 native 锁四补丁 `--check` 通过；缓存 Python3.12.14/118包锁定版本、三项已声明兼容修复、同源 MNNConvert/动态库/probe 的 SHA 本轮重核。仅复用已有工具，未新增下载。

- encoder：六长度 Python parity、三语言 prompt、重新导出 opset15 ONNX、同 session 七次 ORT 动态长度、`MNNConvert -f ONNX --fp16 --saveExternalData --transformerFuseC4=0`、匹配 native gate。exit0，87.36秒，递归进程树 RSS 峰值3,716,485,120bytes。
- decoder：已提交 `convert_fp16.py` 绑定本批 encoder graph/weight SHA gate，独立上游 exporter 16/16/16位配置、释放 encoder，再导出 ONNX/MNN/tokenizer/config。所有五次真实 converter subprocess exit0。57.47秒，峰值3,547,889,664bytes。
- 当前下游验证：native 三语言 prompt 相同；text/audio prefill+两步 decode 位置六记录正确；各151,936词首步 logits 相对本轮新官方 FP16-rounded eager 参考通过。text max_abs `7.9274e-5`、relativeL2 `3.7067e-6`；audio max_abs `0.0360904`、relativeL2 `0.00113953`，两分支 argmax 均相同。
- encoder MNN max_abs 最高 `0.000430724`、relativeL2 最高 `0.00258949`，在既定0.002/0.02门槛内。ORT max_abs最高 `1.73599e-6`。
- 一条现有公开中文录音，在 Chinese/English 两强制语言下，新 native 与本轮新官方参考解析文本一致，正常 EOS、未截断。验证链161.20秒、峰值5,950,398,464bytes。仅工程 smoke，不是独立 CER/WER。

**历史严格 frontend `1e-4` FAIL 保留、未重新跑也未关闭。** 历史20个衍生case、旧手机性能和用户反馈不作为本轮结果。未测 FA2/流式等价、独立质量集或真实设备。

## 7资产与三方身份

新目录 `models/mnn-v1v-fp16/`。七文件字节数仍合计 **1,573,492,181**。新 graph SHA：audio `5b1cc5d847d847caf148cf3002bf3c4915129339f49e3cb85a5c26366d066689`、llm `1b5ea2ecd7f19d8d2e55db6cc02ac4b6a1f8f47a5a1fb10c49b02aa29aed5226`。两外置权重、tokenizer、两配置与 QwenASR/bt4 全部逐完整SHA相同；新图SHA不同。

| 图 | 比较 | 总不同字节 | UUID外不同字节 | 仅内存归一化后完全相同 |
|---|---|---:|---:|---|
| audio.mnn | QwenASR → bt4 | 1031 | 1000 | 否 |
| audio.mnn | QwenASR → v1v | 1028 | 1000 | 否 |
| audio.mnn | bt4 → v1v | 31 | 0 | 是 |
| llm.mnn | QwenASR → bt4 | 2593 | 2567 | 否 |
| llm.mnn | QwenASR → v1v | 2595 | 2567 | 否 |
| llm.mnn | bt4 → v1v | 30 | 0 | 是 |

从实际 schema `Net.mnn_uuid` 边界解析；归一化仅内存操作，从未写改图或 UUID。新 graph 的非UUID内容稳定复现了 bt4，**没有恢复 QwenASR 的原图字节身份**。新 encoder ONNX SHA `360e0a9f1aeca23831d9044b3a40b52c685e9869767d3e2602b33527bc69f389`；decoder ONNX SHA `4e36070624f5ffcb1e27cf86597f2da6b5fd7b38f4d71a66e10b8873d60c1d4f`，两份均与bt4对应ONNX逐SHA相同（pytorch2.10.0，opset15；decoder另有LlmExporter domain1）。

### 有界语义定位

只读 schema walk 分类所有差异，当前 schema 无未知字段，结构投影在省略UUID后 bt4/v1v相同，而 QwenASR/v1v不同。全部 UUID外差异均落在 **Const/Blob 实际数值载荷**，不是 serializer padding、字段顺序或纯元数据：

- audio：`/Constant_29_output_0`，FP16 `[1500,896]`，1,344,000元素中992元素不同（1000载荷字节）；max_abs `0.00048828125`、RMSE `5.26848e-6`。
- decoder：四个FP32常量 `onnx::Gather_147` / `_145` `[2048,64]`、`_143` / `_140` `[130,64]`，载荷不同字节1798/445/161/163。最大绝对差分别 `5.96046e-8` / `5.96046e-8` / `7.24941e-6` / `7.46548e-6`。外置权重精确相同不代表这些内嵌常量也相同。

这是有证据的数值常量差异，但**不是已证明识别质量退化或精确工具链原因**。新用户历史文档确认最终成功路径本来就是相同分阶段路线、MNN上游revision与位宽/fusion参数；完整历史 compiler/Python/ONNX/converter binary 身份仍未知。更像可稳定复现的环境/导出工具链差异，而非重跑消除了随机非UUID漂移；不声称已证明具体根因或所有数学完全相同。

## 导入及打包边界

本批新图不匹配当前 APK 的固定可信tuple，**当前 APK 应拒绝导入**。没有改旧SHA、图UUID或catalog来凑通过。未生成冗余约1.57GB generic ZIP；原 `Qwen3-ASR-0.6B-QwenASR-current-app-pp9.zip`（SHA `424fadca022d22ee9f9ea0dc2a222800a48777e1f41f0aac6dfcf60fe1a01425`）保留不覆盖。新导出资产manifest放独占证据目录，不在用户QwenASR或payload内生成可信清单。

## R1：取消与终态记录修复（待 fresh focused review）

首次 fresh review 发现真实阻塞：普通 TERM 终止监督 CLI 后，已另起 session 的实际子进程仍活；TERM/INT 下持久 receipt 仍 `RUNNING`、实际启动阶段误留 `NOT_RUN`。在隔离旧冻结源码上，用真实 CLI + tiny child 就绪信号重复观察到精确 red：TERM exit -15 / child 活，INT exit -2 / child 停，二者缺实际 exit/logSHA。旧冻结证据未覆盖或改写。

窄修使用可捕获信号 latch、启动前RUNNING/实际started与PID身份记录、直接子进程有界停止等待、取消终态/实际exit/logSHA及后续NOT_RUN；重复取消不抛异常打断清理，恢复先前handler。无法确认已观察进程终态时失败封闭并记录不确定性，不声称采样能等待所有逃逸孙进程。没有SIGKILL/断电持久化保证。本修复无模型数学/门槛/资源预算或APK变化；真实维护入口权重仍 **NOT RUN**，必须父新上下文定点审查闭合后另授权。tiny child 信号测试不当作模型质量证明。

## 新维护入口与证据边界

按用户追加要求，新增 `model-tools/reproduce_fp16.py` / `fp16_process.py` / `test_reproduce_fp16.py`，用显式本地参数复用已提交 helper，串行有界、fresh/no-clobber、实际exit/log/逐阶段SHA、失败保留与后续NOT_RUN，不混入安装下载/Android或全仓目录重构。使用和前提见[转换文档](../../docs/mnn-fp16-conversion.md)。

**真实大模型链先由任务独占临时编排执行，之后才提炼维护入口。** 新入口的 tiny fakechild 生产接口测试和真实已审输入 `--dry-run` 不等同真实权重转换跑过新入口，不冒用旧命令。接口负例覆盖真实子进程失败、exit0 gate坏/过期SHA、无覆盖、路径空格/venv解释器、资源timeout/RSS和后续NOT_RUN；数值验收仍引用上面的本轮真实链。复杂度对新三Python文件显式同工具scope采集，非新增函数的“退化0”声明。cccc1.8.3/knots1.18.0 均exit0、stderr空、三文件都有函数记录；新热点为进程监督/递归owner/终止回收、路径准入和ORT布尔gate，逐项人工关联timeout/RSS/orphan/no-clobber/staleSHA负例，不以降分代替安全行为。原产品源码/锁/补丁、旧工具与venv、raw/QwenASR/bt4/旧ZIP/APK及index保护审计和父 fresh review/安全提交以当前 receipt 为准；leaf 不 stage/commit/push。
