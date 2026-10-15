# Agent 长期路线与进展导航

## 1. 恢复顺序与事实来源

这是可随 Git 恢复的综合路线摘要，承接规划任务 `llm-asr-mqn`，不是第二任务库，也不授予整条路线的实施权。P 编号是交付分组，M 编号是既有现代 Android 工程任务；两者不能直接按编号相等解释。

继续、接手或分派时，先读 [AGENTS](../AGENTS.md)，核当前 Git/cwd/worktree/未提交 diff、`bd context`、目标显式 `bd show <id>` 及相关依赖、该任务绑定的 `.planning/<id>/` 三文件与 Herdr runtime。`blocks` 才是阻塞关系，`parent-child` / `relates-to` 不自动阻止实施；汇总计数与逐条矛盾时显式 show/list 核查。owner、基线和下一步由这次恢复确定，不固定为本文日期的值。M0 已交付，恢复基线不等于重做 M0。

| 要回答的问题 | 来源 |
|---|---|
| 当前允许做什么、谁在做、依赖是否满足 | 用户窄授权 + 本项目 Beads + runtime；本库 embedded `llm_asr`，异常先调查 |
| 已实现什么、哪些输入实际受测 | Git/current source + 当批 reports/receipt；旧 HEAD/旧 APK 只作历史 |
| 本轮阶段、诊断、单一下一步 | 明确 task/owner 绑定的 planning 三文件；不重开根累计历史 |
| 工程规格与验证层级 | [现代 Android M 路线](modern-android-roadmap.md)、[本地验证契约](local-verification.md)、[模块测试映射](module-test-map.md) |
| 委派与交付流程 | [项目 delivery skill](../.agents/skills/llm-asr-delivery/SKILL.md)；依赖变更另读 dependencies skill |

原 mqn/v07 研究提供了综合规划输入，但它们的本地未跟踪材料不是本文可读性的前提，也不是本轮提交候选。原 M 路线中“尚无 Gradle/新 Agent 先做 M0”等属于制定时快照，不能覆盖当前 Git 与 Beads。

## 2. 已交付与未完成：2026-10-15 快照

- 既有业务解耦、Gradle/AGP、显式 native 构建、core/runtime/app、JUnit 与窗口/返回切片已交付；Beads M0–M3、M6 为 closed，不重复立项。
- P1-A 本地护栏 `2d869e5` 已提交，证据入口 [P1-A status](../reports/local-verify-p1/status.md) / [full](../reports/local-verify-p1/full.md)。远端 CI 未选择或触发，M4 仍 in_progress。
- P1-B 测试工程 `add5d2c` 已提交，入口 [P1-B status](../reports/android-test-p1/status.md) / [full](../reports/android-test-p1/full.md)。SAF open 失败/重试与取消工程切片 `86a9446` 已提交，入口 [SAF status](../reports/android-test-m5-saf-open/status.md)、[full](../reports/android-test-m5-saf-open/full.md)、[输入清单](../reports/android-test-m5-saf-open/full-inputs.json)。Android runner、生产 Service/SAF/Binder/grant/IME 系统生命周期并未因此验收，M5 仍 in_progress。
- `llm-asr-ap5` deferred；恢复 Kotlin 迁移须合法 owner 交接。`llm-asr-tc3` blocked，实际设备范围/授权与当次 APK 身份仍需确认。`llm-asr-mqn` closed 仅表示规划完成，不代表 P1–P10 全部实施完成。
- 当前工程报告里的“待提交”等冻结文字保留其历史时点；提交真相查 Git 与相应提交 receipt，不为文字 pending 重建产品或回写旧 freeze。具体 APK 版本、签名与输入/产物 hash 查当批报告和 receipt，不从本文继承。

## 3. 综合顺序：P1–P10

主线是 **最小护栏 → 稳定 Kotlin 试点 → 通用契约 → 前置持久校验 → MNN 驻留 → sherpa → 双引擎闭环 → 页面状态 → UI/语言收口 → release**。新代码 Kotlin 优先；旧代码机械迁移与功能重写分批、分性质验收，不把语义重写称为等价转换。无需等生产 Java 全迁完才接第二引擎，也不先接 SDK 再补生命周期。

| 阶段 | 目标与进入条件 | 可检查的出口与保留边界 |
|---|---|---|
| **P1 验证护栏/准入** | A 本地分层验证已交；B 测试工程已交但 runner/生产生命周期未验；C native 布局、首模型来源/许可、驻留资源策略待决定 | 以已有最小护栏支撑窄批次，不等整个体系完美。C 需来源/许可、single/multi-DSO 实验与资源/receipt 恢复策略；不自动选择 CI 平台或 DSO 方案。M4/M5完整收口最迟 P7 结束、P8 开始前 |
| **P2 稳定边界 Kotlin** | P1 最小护栏、`ap5` 合法恢复/交接；每批少量稳定 helper/type，避开即将重写的 repo/controller | 实际编译、Java interop/ABI、可编译生产行为 mutants、真实 class/DEX owner；不混工具升级或功能重写，未达全生产 Java=0 不关 ap5 |
| **P3 通用引擎契约** | P2 方法稳定；请求/结果/能力/selection/registry 与 MNN compatibility adapter | 保 MNN UTF8/终止/覆盖语义与 owner/lease 回滚，App/IME 绑定请求快照。新接口 Kotlin；尚未 P5 时可暂兼容每请求加载，必须明示未达到免 load 目标 |
| **P4 多模型/持久 receipt** | P3 身份契约；受控 catalog、bundle/不可变 generation、安装与激活分离 | 完整 hash 在安装/准备阶段，持久 VerificationReceipt 绑定来源/策略/generation；正常跨进程有效证明免全量 hash。受管变更先失效、崩溃/信任不连续重验，size/mtime 不冒密码学完整性；不自动下载/激活或任意模型导入 |
| **P5 单 MNN resident** | P3/P4、批准的资源策略和必要模型/设备范围；独立 prepare→LOADED | App/IME 单 Manager 共享一个权重实例，连续请求不每次 hash/load；真实 JNI 调用计数与 reset 隔离音频/KV/decoder/文本，失败淘汰、静止后卸载、不双驻留。receipt 跨进程，native 不跨进程；PSS/trim/后台实测另授权 |
| **P6 单 sherpa offline** | 已批准 native 布局与资产来源/许可/NOTICE/闭包，复用 P3–P5 | 只选一个 CPU offline bundle；异常/RAII/exit/日志、ORT/C++runtime/JNI/ELF/包 gate 与坏模型负例；先卸载再切换。候选不是已安装能力，不扩 online/VAD/NNAPI/QNN |
| **P7 双引擎闭环** | P4–P6；授权公开质量集/设备证据与 M4/M5完整门槛收口 | 安装→校验→prepare→选择→App/IME→重启/切换/删除；冷/复用资源、真实质量与状态隔离。旧衍生样本一致性不当人工 golden；工程可先交，设备/quality 未满足不能越过相应验收或发布 gate |
| **P8 页面状态/生命周期** | P7 稳定接口、M4/M5闭环，复用已交 M6 | 逐页 UiState/ViewModel/权限/ActivityResult/返回与订阅；IME 单独平台边界，不将 native owner 绑页面销毁。SavedState 不默认存私人正文/URI；完成后按 M8 做 UI 决策，不预选 Compose |
| **P9 UI/Kotlin 收口** | P8、用户批准 UI 路线及 ap5 范围 | 逐页资源/主题/无障碍，项目自有生产 Java=0；测试/第三方分开统计。JNI 桥最后独立批次核真实 Kotlin descriptor/header/符号/DEX；保留自写 Java 桥需用户明确变更标准 |
| **P10 Release** | 双引擎/质量/设备/UI/语言目标与实际发布依赖满足 | 用户控制签名、R8 与 JNI 运行、NOTICE/隐私、4KiB/16KiB 包装及真实运行、commit/input/signature/APK 绑定。缺密钥/设备则 blocked；不自动 push、远端 CI、商店发布或上传 |

每个窄切片重新冻结当批输入与产物，不把旧 source 数量、方法数或 APK 字节身份设为永久门槛。设备/下载/架构决策需要另授权；当前窄任务不等待整条路线一次性批准，也不自行 claim 后续阶段。

## 4. Beads 入口：查重后复用

下列状态仅为上述日期核查快照；实施前显式 show，并由合法 Beads writer 更新，不从表直接分派。

| 入口 | 与 P 阶段的关系 |
|---|---|
| `llm-asr-76d`；`.1/.2/.3/.4/.7` | 现代化 epic；M0/M1/M2/M3/M6 已 closed，保留已交基线 |
| `llm-asr-76d.5` / `.6` | M4/M5 in_progress；P1 最小护栏与工程基础已交，P7 前完整收口 |
| `llm-asr-76d.8` | M7 open，P8；真实 blocks 含 M4/M5/M6，不能提前强关依赖 |
| `llm-asr-76d.9` / `.10` / `.11` | M8 UI 决策 / M9 资源化 / M10 发布，分别映射 P8 后决策 / P9 / P10，均未完成 |
| `llm-asr-ap5` | deferred，P2–P9 Kotlin 线；保持合法交接与全生产 Kotlin 目标 |
| `llm-asr-tc3` | blocked，P5/P7/P10 设备验证；运行前按当批身份刷新范围，不使用任务旧 APK |
| `llm-asr-ujz` / `llm-asr-cl4` | open，人工有许可质量集 / 前端与 oracle 语义，P7/P10 |
| `llm-asr-z1w` / `llm-asr-msj` | blocked 性能候选 / open 产品边界决策；驻留工程与既有设备/质量依赖须协调，不能顺带量化或默认 API/下载 |
| `llm-asr-rpb` / `llm-asr-mfe` | 日志 worker/覆盖窄修入口，按需独立授权，不假设 Kotlin/Flow 自动解决 |
| `llm-asr-mqn`、`llm-asr-v07` / `llm-asr-1lz` | 已关闭规划/研究，不当实施任务；P3–P6 无完全相同既建任务，未来授权后由 Steward 查重登记窄实施 task |

新工作执行 [delivery skill](../.agents/skills/llm-asr-delivery/SKILL.md) 的单 writer、分层证据与安全提交流程。owner/runtime 冲突、来源/许可不明、输入/签名无法解释、资源隔离无证据或必要权限缺失时停该分支，给出证据、尝试、blocker 与下一输入；不以路线存在解除门槛。
