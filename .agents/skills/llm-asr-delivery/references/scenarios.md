# 情境核查表：桌面推演，不是 agent 压测

使用方法：对照 [主流程](../SKILL.md) 指定章节，逐行确认文档能唯一导出安全动作；记录静态核查结果与真实执行的区别。以下不是已执行的 Android/Gradle/agent 试验。

| 情境输入 | 按 skill 应动作 | 不得误判 | 定位 |
|---|---|---|---|
| 用户要求派 agent 审查/修复，考虑用后台 `pi -p` 启动 | 按 §2 用 `agents` 派遣并记录宿主返回身份/配置，普通测试构建才用 `bg_run` | 独立 CLI、`--no-session`、PID 或仅查询过 agent 列表不证明受管派遣/`fork:false` | §2 派遣入口 |
| 已有同 scope 后台进程，用户重复 Retry 或要求纠正派遣入口 | 保唯一 ownership，协调已有执行者终态；后续新派遣使用 `agents`，记录旧入口偏差 | 不并发复制工作，不重标旧进程为 tuios 受管 agent；入口不可用停报 | §2 派遣入口 |
| leaf 普通 final，但 Gradle 后台仍未完、审计未封口 | resume 同 session 接原 chain，保单 writer，等自动终态补齐 handoff | final/completed 不是工程验收，不开第二 full/review | §3；经验 A |
| 重复等待，但 disk exit/log 完整、原 PID-start 已消失，自动通知缺口 | 依宿主协议一次 exact registry 恢复，记录缺口；成功则收尾 | 不循环 poll、不为等迟到通知重跑成功链 | §3；经验 A |
| 已消费可信成功后才收到一致 completed | 确认一致，继续后处理/交接，不改已冻结证据 | 不把旧通知当新授权或新构建必要性 | §3；经验 A |
| checker 全绿、DEX 方法库存符合，但仪器 runner 从未运行 | 只报编译/parsed DEX 静态通过，Android 运行 not run | 不声称 runner 发现/执行或系统生命周期验收 | §4；经验 B |
| reviewer 静态发现 R1：open 前已有 PENDING，测试却要求空历史 | 先报告父接受；原 writer 最小修精确断言，targeted 与定点回审 | 不改生产迁就测试，不把静态发现称 Android 运行 red | §5；经验 C |
| 新 module 缺依赖/cache，原 scope 仅 verify | 停报，读 dependencies skill；父另授权隔离 prepare，旧锁/metadata SHA 保护 | 缺 cache 不是行为 red，主树不 deactivate/write-locks | §4；经验 D |
| 受审 freeze 与当前/staged 候选 SHA 漂移 | 暂停提交，父解释 delta/授权修复、必要重验审；再比较全 cached diff/blob | 不自动 rehash freeze 或夹带未知 index | §6；经验 E |
| 新功能或 bugfix 准备交付 APK | 按版本门槛核旧→新版本，正式 full 前更新声明/有效校验预期，核实际 APK 版本与源码/receipt 一致 | 改文件名、同版本重打包或旧 full 证据不能替代版本递增与新输入验证 | §4 APK 版本门槛 |
| 纯文档导航/skill 变化，产品输入/receipt/三 APK SHA 保持 | 做结构链接/情境/安全 diff 与不变证明，报告无新产品验收；无需仅为此递增 APK 版本 | 不为旧 pending 或文档批启动 full/prepare | §4、§6；经验 E |
| worker cancel 标志出现，但 provider open 仍阻塞 | 保 owner，解除测试阻塞后有界等待真实终态，再清理 | 不删活 IO 目录，不将取消标志当已释放 | §4；经验 C |
| 坏 checksum mutant 因编译/网络错误 exit1 | 诊断并恢复，只有目标 expected/actual 拒绝才算 red | 非零不等预期安全负例成立 | §4；经验 C |
| 既有行为 driver 固定写旧 owner 域 | 父明确授权一个新随机子目录，保护旧域、复制新证据 | 不用 symlink 绕过、不覆盖旧证据 | §2、§4；经验 D |
| Beads ready，但 owner working 或只见 parent-child 关联 | 实时 show/plan/runtime 核 true blocks 与范围；冲突停报 | ready 不授权，关联不冒阻塞，unknown 不冒无人 | §1 |
| library Manifest 声明了录音权限，未获设备/mic 授权 | 只报静态契约，运行权限/麦克风 not run | 不访问设备/mic，不将声明当授权 | §4；经验 D |
| 同 sol 新上下文审查通过，未用其他家族 | 报 fresh `fork:false` 上下文隔离及范围 | 不称跨家族独立，不默认复制父上下文 | 入口、§5 |
| 第三条不同且有效诊断路径仍失败，或直接缺设备 | 前者止损给证据/下一输入，后者直接 not run/blocked | 不重复同命令凑三次、不关 gate 硬交付 | §7 |

核查完毕也只说明文档分支清晰；Pi runtime reload 实际发现/加载、真实后台恢复、构建与设备验收须分别取得执行证据，未执行写 not run。
