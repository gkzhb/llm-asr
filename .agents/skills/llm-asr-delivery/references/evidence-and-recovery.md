# 终态恢复与工程经验分支

按 [主流程](../SKILL.md) 的阶段选择分支；这里只保留可复用原因和安全动作，具体 task/工具/输入库存每批重新核。参考均为已提交工程记录，不要求读取私人 session 正文或复刻时间戳/PID 流水。

## A. 普通 final、迟到通知与成功链

**触发**：leaf final 仍写“后台未结束”，或界面/恢复后等待与磁盘完成事实矛盾。

- 未完 checkpoint：父保留原 writer，resume 原 session 继续同一 chain，取得后台真实终态及剩余 format/输入/审计/handoff，不先开审或第二构建。
- 默认以宿主自动终态为准，未见异常不用点查等待。重复等待而完整 disk exit/execution/log 已落且原 PID-start 消失，是异常恢复的具体条件，不是每次都探进程的许可证。
- 此时仅一次 exact task registry/PID-start 核验，记录 task/exit/log SHA 与通知缺口；launcher/runner/command 身份分清，PID 存在还要防重用。实际仍运行则返回原监督链；可信成功即封口，不等旧通知、不重跑 full。迟到 completed 若与已消费证据一致，只确认一致，不改冻结报告。
- agent final、命令成功、工程验收、提交、设备通过分别表述；磁盘退出码不能替代输入绑定或保护域审计。

来源：[SAF 监督进度](../../../../.planning/llm-asr-76d.6-followup-supervision/progress.md)、[正式 full](../../../../reports/android-test-m5-saf-open/full.md)；长期知识入口 `bd recall agent-delivery-terminal-evidence`。工具协议有更严格限制时服从宿主，不用本案例绕过禁止轮询。

## B. 生产源码根与独立 test-host

**触发**：加入 Android fixtures/androidTest，源码根沿用共享 `android/app/src`。

生产源扫描可能把其下测试目录当生产输入，导致 fixture/JUnit/provider 泄漏产品 DEX。先核实际 sourceSets/collector，使用独立 `android/test-host`，由 checker 读真实全部 DEX/class_data 区分产品、host、test owner 与方法形状。库存静态通过只证明包内存在，不证明标准 runner 真正发现/执行，更不证明系统 IME/SAF 生命周期。

来源：[本地验证 P1-B](../../../../docs/local-verification.md)、[P1-B full](../../../../reports/android-test-p1/full.md)、[SAF build](../../../../reports/android-test-m5-saf-open/build.md)。

## C. 测试必须诊断目标失败

**触发**：mutant/坏 checksum/Lint/SAF 故障应 red，却遇到编译失败或通用异常。

1. 可编译行为 mutant 必须走实际生产实现；缺 cache/工具异常不是目标拒绝。checksum red 指向当次目标 artifact expected/actual，Lint red 指向实际目标 NewApi 的 file/line/API，恢复原 bytes 后 green。
2. listener 可能吞异常，断言移测试线程；worker Throwable 显式带出。有界 latch/join，finally 先解除阻塞并等实际 IO 结束再清理目录，owner 不能随取消标志提前释放。
3. 先按生产状态转换核断言，不按测试期望反改生产。SAF R1：open 前已记录 `model.bin=PENDING`，open 失败时 failedFiles 为空不意味着 fileResults 为空；静态审查发现错误期望后，只修精确断言，保留 inspect 历史与 retry READY/SHA 覆盖。此发现与修复未在 Android runner 执行，不称运行 red/green。

来源：[R1 处置](../../../../.planning/llm-asr-76d.6-followup-supervision/review-r1-disposition.md)、[修复报告](../../../../reports/android-test-m5-saf-open/r1-fix.md)、[P1-A full](../../../../reports/local-verify-p1/full.md)。

## D. prepare、固定输出与 scope 阻塞

**触发**：新增测试模块需要锁/新依赖，或既有 driver 固定输出落旧 owner 域。

- 历史主树 `deactivateDependencyLocking + --write-locks` 曾删旧锁，已废弃，**不重放**。依赖准备必须父另授权并读 [dependencies skill](../../llm-asr-dependencies/SKILL.md)：隔离 temp 项目保正常 locking，strict/offline/已准缓存，仅导出获准新 module lock；主树旧锁/metadata 在复制前、导出前后 SHA 保持。缺缓存停报，不写 verification metadata 或加宽信任补绿。
- driver 固定旧 owner 目录时，先报父明确授权一个新的随机子目录，旧文件保护并保 SHA 前后证据；不覆盖旧路径、不用 symlink 绕 scope，也不擅自改 driver。成功后复制必要证据到本轮独占 work。
- 必要生产窄修超原 scope 时，把实际阻塞、最小 delta、旧权限/身份如何保持报父；接受后再修和回归。scope 有阻塞不等于可抑制 Lint、关 checksum 或删 gate。
- Android library 的 RECORD_AUDIO 静态声明只补工程契约，不授予麦克风运行权；真实权限 gate 仍须保持。

来源：[本地验证 N1 与 runtime 权限历史](../../../../docs/local-verification.md)、[full 输出授权监督](../../../../.planning/llm-asr-76d.6-followup-supervision/progress.md)、[权限窄修](../../../../reports/local-verify-p1/permission-fix.md)。

## E. 冻结与提交后的真实时点

**触发**：旧报告写 pending，或候选 current 与已审 freeze 不一致。

原报告只读保历史；对获准 supplemental 变化独立 delta，产品输入漂移则停下重新判验证/审查必要性。提交阶段比对完整 staged diff、blob mode/SHA、受审候选与 committed/current/frozen 关系；不能 rehash 旧 freeze、回写旧 head 或为了状态文字补脏改。父消费提交 receipt/Git 后才对用户交付。

来源：[SAF full 输入](../../../../reports/android-test-m5-saf-open/full-inputs.json)、[SAF full](../../../../reports/android-test-m5-saf-open/full.md)、[监督封口](../../../../.planning/llm-asr-76d.6-followup-supervision/progress.md)。纯文档批静态审查及产品输入/三包身份保持即可，不沿历史 pending 重新 full。
