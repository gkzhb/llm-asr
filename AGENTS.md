# 项目协作规则

## 每轮功能开发的完成顺序

1. 完成功能实现及相关文档更新。
2. 运行本轮相关测试；涉及 Android 应用时完成必要的 Java/JNI 编译、APK 构建和包检查。完成必要的代码审查并修复阻塞问题。
3. 核对当前源码、测试证据和 APK 的构建输入指纹，确保向用户交付的 APK 对应本轮代码。真实设备未验证的项目必须明确标为待验证，不得将主机测试通过等同于设备验收。
4. **每轮功能开发、测试和必要审查完成后，必须先进行本地 Git 代码提交，再告知用户可以验证 APK。** 提交包含本轮相关源码、测试、构建脚本及文档，提交说明应概括功能和验证情况。
5. 提交成功后向用户报告提交短哈希、APK 路径、版本/身份及建议验证项。未提交成功或测试/审查仍有阻塞时，只报告当前状态，不宣称本轮已交付可验证 APK。

## 提交与数据安全

- 提交前检查暂存差异和文件清单；不得夹带无关用户改动，不得重置或覆盖用户成果。存在无法确认的改动归属或提交阻塞时，先说明并请求确认。
- **不提交模型权重、音频或私人转写内容、APK/其他编译产物、下载缓存、签名密钥、凭据及本地代理会话数据。** 保持 `.gitignore` 防护，只保留必要的非敏感文本验证报告和产物哈希。
- 此规则授权功能轮次完成后的本地提交；**未经用户明确授权不推送远端**，不自动修改已有提交或重写历史。
- 构建失败、检查失败、提交失败应如实记录；不得跳过安全检查、降低测试门槛或用旧产物/旧审查替代当前证据。
- 设备、麦克风及私人文件访问需要单独授权；本地提交不构成此类授权。

## 项目上下文与任务导航

- 项目需求核查：[docs/requirements-audit.md](docs/requirements-audit.md)。该页是快照；实时任务、依赖、blocker、assignee 以本项目 Beads 为准：`bd status`、`bd ready`、`bd blocked`、`bd show <id>`。
- 根 `task_plan.md` / `findings.md` / `progress.md` 保留累计执行历史；活动任务使用明确绑定任务 ID / owner 的 `.planning/<id>/`，一计划一 writer。旧阶段 TODO 不应绕过当前 Beads 重复分派。
- Agent 运行状态以 Herdr 查询为准；`bd ready` 不等于实施授权。分派前核对依赖、owner、runtime 与任务范围。发现汇总与逐条记录不一致时以显式 `bd show <id>` / `bd list --status blocked` 核查，不只看 `bd status` 的计数。

## Project Knowledge

长期项目知识保存在本项目 Beads memories；索引不代替任务状态或 memory 正文。

- 查找：`bd memories '<keyword>'`；全文：`bd recall '<key>'`。
- 已存类别关键词：`Nix` / `资源约束` / `复现` / `KleidiAI` / `ASR` / `P0` / `验证边界` / `Kotlin` / `D8`。
- 相关 keys：`build-tiny-mirror`、`build-kleidiai-reproduction`、`asr-quality-evidence`、`kotlin-d8-compatibility`。
- 仅在成功保存知识且需要新增导航时更新此索引，不复制调试流水账。
