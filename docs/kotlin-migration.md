# Java → Kotlin 迁移

任务：`llm-asr-ap5`；活动计划：`.planning/llm-asr-ap5/`。

## 可行性与技术风险

没有语言/Android/JNI层面的根本不兼容，但不是零风险的文本转换。当前项目使用官方 Android 工具直接打包，不能只添加 `.kt`：

- `flake.lock` 锁定的 nixpkgs 默认提供 Kotlin 2.4.10，但实际SDK35 D8/R8只支持到metadata2.1，完整构建发现不兼容重写警告；该中间包拒绝交付。改为固定Kotlin2.0.21归档SHA的Nix override；编译器与stdlib必须同版。
- `scripts/compile-jvm-sources.sh` 先让 kotlinc 解析 Kotlin 和 Java 声明、输出 Kotlin class，再用 javac 编译 Java。JVM target 保持8；不引入 Gradle/Maven/反射库/协程。
- host Java tests 必须带同版 `kotlin-stdlib.jar`。APK 的 d8 输入显式加入 stdlib 和 annotations，不能编译通过而漏打包运行时。D8退出0也不充分：warning/metadata重写异常使构建fail closed，不绕过兼容性检查。
- Java 调用的静态工厂/常量/公开字段用 `@JvmStatic`、`const val`、`@JvmField` 保持；不把 DTO 改为 data class 引入新相等/拷贝语义。
- Kotlin 的空值检查、Unicode trim、split 默认规则与 Java 不完全相同，逐类保留原契约并补负例。
- `RecordingControl` 保留 JVM synchronized monitor 覆盖的 start/cancel/commit gate；不改成协程 Mutex，也不提前检查 backend 空值改变取消优先次序。
- JNI 声明首批仍为 Java，真实 `javac -h` 与 NDK强制头、实际DSO符号检查保持。后续迁移桥时需单独验证 JVM descriptor/native owner，不能手写假声明代替。

## Nix 配置说明

`overrideAttrs` 是标准的 Nix 包版本覆盖，不是修改编译器逻辑：固定官方2.0.21二进制归档和SHA256，继承nixpkgs原有解包/Java wrapper，编译器与运行时同源。同SHA的Nix内容缓存与官方URL只是下载来源备选。`nix-store --add-fixed`用于导入校验后的同内容归档，不绕过fetchurl哈希验证，干净机器也按固定URL/hash获取。不剥离或伪造Kotlin metadata、不吞D8警告、不修改SDK/NDK、不永久设置代理。

## 批次

第一批接入可复现混合构建，迁移 `AsrText`、`PcmWave`、`RecordingControl`、`NativeResponse`。当前其余58个生产文件仍为Java，不代表全量迁移完成。Java tests 保留以验证互操作。

首批已提交`a23277d`。第二批实现/完整构建/直接自审通过（提交身份以Git日志为准）：TaskKind/RequestContext/TaskCoordinator/RequestRunner/AppState/ImeFieldPolicy；累计10 Kotlin/52 Java。`-Xjvm-default=all`生成真实JVM接口default方法以保持Java调用契约，不是自造兼容层；SAM使用fun interface，公开字段继续@JvmField，nullable/exception次序/Listener身份比较保持原语义。Nix配置本批不变。

后续按模型与管理、日志/导出、推理、平台/Android入口分批转换；高风险行为变异测试先适配再移走被测试的生产Java。每批均需要全量host、全源Android编译、JNI、DEX/签名/权限/组件/产物输入检查及本地Git提交。

## 验证边界

第一批a23277d通过并提交；第二批bb3c4f1e9完整构建exit0，D8日志为空，127构建/133冻结输入匹配，JNI/MNN/Nix与基线不变。43 Java ABI/语义checks和既有全套/mutants通过，DEX实际包含全部10 Kotlin生产类；直接自审见[批次2报告](../reports/review/kotlin-batch2-self-review.md)。当前产物身份见[APK状态](../reports/apk/status.md)，本地提交成功后才交付。host/构建通过不等于真实设备验收：App/IME录音、切换字段、模型SAF、导出、生命周期与JNI运行仍需设备回归。代理不访问设备、麦克风或私人文件。本轮不改变模型数学、权限、包名或签名密钥，不推送远端。

## P2 日志双枚举窄批（2026-10-16，targeted 阶段）

本批任务仍为 `llm-asr-ap5`，当前实施计划为 `.planning/llm-asr-ap5-enum-build/`；上文第一、二批及旧计划是历史，不代表本批输入或 APK 验收。仅将 `diagnostics.RuntimeLogSource`、`RuntimeLogEventKind` 改为同包同名 Kotlin enum class，保留三个 source、29 个 kind 的名称及声明顺序，不新增事件或改日志功能。当前动态源码库存为 **12 Kotlin / 51 Java（共63文件）**；历史62文件计数不作为永久门槛，未达到 Java0，不关闭全量迁移任务。

`tests/KotlinMigrationTest.java` 保留真实 Java 调用方，覆盖 public/final Enum owner、常量字段、values/valueOf、反射库存、defensive array、ordinal/compareTo、全部 Java switch、默认 toString、非法名称 IAE/null NPE。真实 `RuntimeLogPersistence` 读取并原字节回写冻结 V1 fixture，`RuntimeLogCodec` 精确 UTF-8 展示、全部3×29词汇及非法持久名称拒绝；不手写生产 parser/codec 替身。

先在原 Java enum 上真实混编/运行322 checks green，核验原日志与输入 SHA 后才迁移；迁移后的 focused green 相同322 checks。`tests/runtime_log_enum_mutant_test.py` 从 host canonical 源集合复制当前生产实现，在各自 fresh classes 中独立交换 APP/IME 或 APP_STARTUP/APP_READY；必须编译0后由原 Java inventory/order 断言明确 exit1，再精确恢复原源 SHA 并重编 green0。编译、工具、cache 或 timeout 失败不计行为 red；普通 Python 与 `-O` 都不用 Python assert 作为门槛。driver 已接入既有 host/full 路径，不扩大 fast 图。

本阶段仅固定 Nix cache-only、strict/offline targeted；结果及冻结交接见 [P2 targeted 报告](../reports/kotlin-enum-p2/targeted.md)。**fresh 上下文隔离审查、后续唯一 full（含 JNI/native/Lint/DEX/三包签名与输入身份）和安全本地提交由父后置协调，本阶段未交付新 APK**。已有 APK/receipt 只保护原字节，不能用于证明这次源码已打包。Android runner、设备、麦克风、SAF/IME/JNI运行均 not run；依赖、Nix/Gradle锁和metadata、native数学/owner、权限签名/录音gate不变，无下载或设备操作。

### P2 R1 取消/超时收口修复（targeted，待定点回审）

首轮成功链不包含取消故障。fresh 同模型上下文隔离审查指出：共享 wrapper 内 GNU timeout 默认另建 PGID，driver 只杀 wrapper 组后便可能清仍被 compiler 使用的 tree；旧 targeted 报告的整组清理表述过强，作为历史保留，不据此否认静态 R1。新故障回归经实际 driver `run` 与实际共享 wrapper，以独占 fake compiler/Java 及后代注入 TERM/HUP、driver timeout，旧合法实现明确命中“删树前仍有 live owned worker”断言；finally 回收所有测试故障进程。

最小修复仅两个 compiler timeout 增加 `--foreground --kill-after=5s`，保留120秒 deadline；driver 在 KILL/wait 后有界确认 owned PGID 无运行中的 worker 才返回。普通非 daemon 编译/Java 链保持同组，不声称可围堵任意自行脱离 session 的进程。测试记录 PID/start/state/PGID，实际删树前核无活 worker，再确认 tree 消失和失败向外传播；覆盖11故障场景。内部 deadline/KILL 路径使用受控 PATH shim 将真实 GNU timeout 的120秒与5秒宽限缩为0.25/0.15秒，验证原参数和升级路径，**不是实测120秒 wall clock**。新测试接 host/full，不加入 fast。修复后新命令、wrapper/driver SHA及 freeze 另见 [R1 targeted](../reports/kotlin-enum-p2/r1/targeted.md)，不重写原77文件冻结或冒原链测过新wrapper。

原 Java baseline 当时只留 driver SHA、没有全文快照，因此能核原输入/compile/run日志/同 Java caller 与 wrapper，不能独立重证旧 driver 全文与后继“仅几处变化”的主张；此限制保留，不伪造旧快照或重跑旧成功基线。R1仍需父消费新 targeted 后 fresh 定点 CLOSED，之后唯一 full 与安全本地提交；本阶段没有新APK交付或设备验收。


### P2 R1 局部收口更正（r1-local，待 fresh 定点审）

上述 foreground 方案在回审中被拒绝：真实 kotlinc shell 非 exec 启动 JVM，其他共享 caller 没有 enum driver 补杀，不能扩大本窄批去改变所有调用语义。`scripts/compile-jvm-sources.sh` 已精确恢复 base 原字节（SHA256 `097cf23ad7f0e0a5cd8ebcf4e5f87523d3505bc8f90d389d9434ad5b76131adf`），原120秒默认 GNU timeout 策略未改，也不声称修复其他 caller。

当前只在 enum driver 自建 SID 内扫描全部普通 PGID 的运行工作，以原 leader PID/start 与 SID 限界，并通过 pidfd 绑定实际目标后发 TERM/KILL，实际无非 Z/X 工作才返回。普通 GNU timeout 新组、shell 非 exec JVM 链在此范围；主动换 SID/恶意 daemon、SIGKILL 或不可捕获的 driver 终止不作清理保证。无法读取/确认停止则抛 StopUnconfirmed，由实际 owned_tree 边界保留仍可能在用的树，而不是异常离开 TemporaryDirectory 继续删除；失败不算枚举行为 red。

新故障测试走实际 driver.run/owned_tree 与 base wrapper，覆盖 TERM/HUP、外层 timeout、真实 GNU timeout 不同 PGID、普通非 exec shell/worker，以及受控停止观察失败保树；测试 finally 仅在身份匹配工作实际停止后清保留树。内部 deadline 仅以 PATH shim 将120秒缩为0.35秒，验证原 argv 与124退出码，非120秒 wall 实测，不再要求 foreground 或137。对应新证据见 [r1-local targeted](../reports/kotlin-enum-p2/r1-local/targeted.md)。旧77/345候选、两轮报告和成功链保持历史，不重标当前；新输入仍需 fresh R1点审接受后方可唯一 full/安全提交，本阶段未交付新APK。


### P2 R1 main 恢复路径补充（待 fresh 定点审）

r1-local 的 SID/pidfd、run/owned_tree 保树和12场景证据已在回审核齐；唯一剩余静态 P2 是实际 main 的 mutant finally 在 StopUnconfirmed 后仍恢复副本源码，可能先改写活 compiler 使用的文件。此次仅区分该异常：未确认停止时保留当时 mutant 字节并原异常外传，不继续恢复、编译或清 classes；其他正常/已确认停止失败仍精确恢复。共享 wrapper、SID 收口、生产两 enum/Java caller/baseline 均不改。

现有 cleanup 追加实际 main 的有界故障路径：跳过早先成功的工具执行但运行真实 main/mutant/finally，故障 mutant 使用实际 run/原 wrapper 和普通活 worker，受控 stop 观察失败后核 tree、mutant SHA、classes 保留与无后续 compile；finally 确认 PID/start-owned 工作停止后才回收树。fake 仅为故障，不冒正常混编或枚举行为 red；正常生产 mutants 独立真实编译验证。证据见 [r1-main targeted](../reports/kotlin-enum-p2/r1-main/targeted.md)，旧77/345/404冻结报告保历史，不重标为当前。仍须父消费后 fresh R1点审 CLOSED，未运行本批 full/新APK/提交或设备验收。
