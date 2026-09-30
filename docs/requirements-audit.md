# 项目需求与规划核查

核查日期：2026-09-30。角色：Steward；审计任务：`llm-asr-874`。

本页是本次核查快照，不是第二套任务库。**实时任务、依赖、负责人和状态以项目 Beads 为准**；根三份规划文件保留累计执行历史，当前审计上下文位于 `.planning/2026-09-30-bd-initialization-audit/`。

## 1. 已核验的当前进度

- 产品源码检查点为 `d5c1b64`，已经包含离线配置准备路径修复；根规划“等待提交”已过期，不能再次作为待办。
- 已实现：App 示例/WAV/前台录音转写、结果编辑/分享/TXT导出/清除、语音输入法预览确认与会话隔离、独立模型管理页面、进度/协作取消/文件复用/受限删除、运行日志页面和导出、R5/R6 导出隔离与模块拆包。
- 历史 Phase10 的模型管理已经由 Phase15/16 接替完成；Phase19/20 重构已完成。没有重新建立这些实施任务。
- 当前产物：`dist/qwen-asr-minimal-debug.apk`，`0.6-debug` / code6，`org.llmasr.minimal`，Android API29+ / target35 / arm64-v8a。
- 本次只读核对：APK 大小 2,470,760 bytes、SHA-256 `66be525e2f69b12b23f5a76f5bc2d0815f97c7447a672000162d38b6d4e7dfed` 与报告一致；122 项当前构建输入 SHA 全部匹配。
- **本次没有重新运行产品测试、构建 APK 或访问设备。** 历史 host/Java/JNI/签名/包检查通过不等于当前版本设备验收；报告仍为 `device_verified=false`。

## 2. 待做需求及建议顺序

优先级是本次整理的建议，不代表授权新一轮实施。所有后续任务目前均未分派；任务中的验收条件、范围与停止条件可由 `bd show <id>` 读取。

| 建议级别 | Beads ID | 需求 | 本次登记状态与解除条件 |
|---|---|---|---|
| P1 | `llm-asr-tc3` | 当前APK实机兼容性/生命周期回归 | blocked：需明确设备访问授权或用户主动提供绑定本版的逐项反馈；签名冲突不得自动卸载 |
| P1 | `llm-asr-ujz` | 独立有许可、人工标注的golden质量集 | open：先整理许可/标注/输入身份，再评测CER/WER及结束原因；20条衍生smoke不代替独立语料 |
| P1 | `llm-asr-cl4` | 严格frontend残余误差与eager/window参考边界 | open：分层诊断，不擅改官方reference或放宽1e-4旧门槛 |
| P2 | `llm-asr-rpb` | RuntimeLogWorker异常后的调度恢复 | open：窄工程修复；先构造真实生产红测试，避免异常后scheduled永久占用 |
| P3 | `llm-asr-mfe` | 日志剩余恢复/终态/选择器失败回归 | open：逐项确认是否仍存在，再做窄修；不是新的已证实隐私泄露 |
| P2 | `llm-asr-msj` | 决定下一产品批次与性能/API边界 | open decision：由用户选定下一项窄目标；不自行启用网络/服务/驻留 |
| P2 | `llm-asr-pa1` | 静音门控 / VAD | blocked：依赖产品决定及独立语料；不能将能量阈值宣称为可靠VAD |
| P2 | `llm-asr-8po` | 常见音频格式、重采样、长音频顺序分段 | blocked：依赖产品决定及独立语料；先确定格式/时长/资源与恢复边界 |
| P2 | `llm-asr-z1w` | 通用CPU加载延迟/内存优化、驻留或量化评估 | blocked：依赖产品决定、质量集与当前APK设备基线；无证据不承诺加速 |
| P3 | `llm-asr-9fe` | 本机推理API、鉴权/配额及示例调用 | blocked：依赖调用方/授权/服务边界决定；HTTP/LAN/服务端另行安全批准 |

建议先完成当前设备回归与P0质量基础；如果先开展无需设备的窄工程开发，日志Worker恢复是可独立界定的候选。质量基础补齐后，新功能可优先评估静音门控，再考虑长音频与加载优化；这不是已经选定的产品批次。

## 3. 实现事实与限制

- `native/apk/asr_jni.cpp` 每请求创建、加载并销毁引擎；CPU两线程、128输出token、仅正常结束视为成功。没有warm驻留或可靠native硬取消。
- WAV 仅 PCM16 / 单声道 / 16kHz / 0.1–30秒，2MiB输入限额；录音30秒硬上限。无常见格式解码、跨采样率回退或长音频分段链路。
- 本版无VAD。P0官方参考对纯静音也可能输出“嗯。”；不能把该现象仅归因转换错误，也不适合自动提交输入框。
- 已实现IME服务，但没有Binder/AIDL推理服务、HTTP API或`:inference`独立进程。旧详细规划中的这些结构是设想，不是当前事实。
- RuntimeLogStore.publisher 的异常恢复与日志compound close fatal已经修复；本次只登记仍保留的Worker调度恢复，避免重做已关闭问题。
- P0严格特征误差门槛仍失败，eager不等于显式window/FA2语义；20/20仅工程一致性。历史native load约11.6s、PSS约3.06GiB未达到原性能目标，不是本次APK性能实测。
- 在线模型下载、多模型、真流式、厂商GPU/NPU与商店发布没有启动。公开发布还需正式签名、依赖/示例音频再分发许可和平台合规；如要推进，应由用户确定范围后另立任务，不为填空闲机械建实施任务。
- 原P0部署工具跨会话锁/故障路径和干净环境模型转换全链路仍有历史覆盖限制；本次未使用这些工具。以后分派设备部署或模型重导出时须先核对，不能把APK native字节复现当整条模型转换复现。

## 4. Beads 初始化与协作边界

- 项目库：`/home/zhb/gitrep/llm-asr/.beads`；内嵌 Dolt，database=`llm_asr`，prefix=`llm-asr`，无全局库/重定向。
- 初始化后 context/ping/status/memories/list 查询成功。`bd init` 默认自动产生本地配置提交 `9f843db`；检查仅五个配置/README/ignore文本，未包含数据库、备份或凭据。
- 未安装 Git hooks、未生成替代AGENTS规则、未启动外部数据库服务。配置保持 `backup.git-push=false`；没有远端同步或推送。
- 自动导出保留；`export.git-add=false`，避免后续未经审计的导出自动混入暂存。提交只保留非敏感任务/memory文本和必要配置，不提交数据库、运行日志、备份、Agent会话记录或产物。
- Herdr同项目仅当前Steward会话，本次没有分派/启动新Agent，没有产品源码修改或设备/麦克风/私人音频访问。

## 5. 查询入口

```bash
bd context
bd status
bd ready
bd blocked
bd list --status blocked --limit 0
bd show llm-asr-rpb
bd memories '复现'
bd recall build-kleidiai-reproduction
```

`bd ready`仅代表数据库依赖可行，不代表用户已授权实施。分派前仍需检查实际owner、runtime、planning、范围与授权。

本机`bd 1.0.3(dev)`本次出现汇总口径不一致：`bd list`逐条显示5个open、5个blocked（审计关闭前另有1个in_progress），`bd ready`返回5条，但`bd status`曾显示ready=1/blocked=4；`bd blocked`仅列4条有依赖阻塞的任务，未列手动blocked的设备授权项。因此不要只凭汇总数判断可执行性，须结合`bd list --status blocked --limit 0`和显式`bd show <id>`。本轮没有修改工具或通过改变真实任务状态凑汇总。

已保存知识：`build-tiny-mirror`、`build-kleidiai-reproduction`、`asr-quality-evidence`。这些是可复用约束与证据解释，不是进度记录。

## 证据入口

- [当前APK状态](../reports/apk/status.md)、[身份与边界](../reports/apk/result.json)、[构建输入](../reports/apk/build-input-sha256.json)
- [P0阶段报告](../reports/p0/report.md)、[本机APK复现](local-apk-reproduction.md)
- [R5/R6验收记录](android-r5-r6-validation.md)、[最终审查处置](../reports/review/phase20-final-disposition.md)
- [通用App路线图](app-roadmap.md)、根 `task_plan.md` / `findings.md` / `progress.md`
