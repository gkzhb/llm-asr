# BVA targeted 与正式 full 内行为证据

状态：当前 reviewed source 冻结与本次 full 工程验证已通过；**尚未安全提交，不是 APK 交付或设备验收**。测试基点 `8165e26238c37e4ab68cb093f71c0e14406b5b57` 加本轮 dirty freeze，不声称测试过未来 commit 对象。

## 功能与生命周期边界

保留现有 Views/三 Activity/Graph 和 owner 协议。HOME 为唯一根，进入转写、输入法设置、模型列表→详情、日志；移除 Models→Logs 与 IME→Models 兄弟跳转，IME App 只进入 Main HOME。活动页统一 Back，WAV 票据高水位不复用、离页与重建撤销，TXT lane 独立 busy/风险常驻，队列 revoke 不提前释放真实 IO owner。

正常主动页内 Back/外部动作在录音中走三选；**平台 onPause 仍立即 cancelRecording 防后台及迟到采集**，新 Intent 只排队 HOME，resume 只反映实际已取消/收尾或已提交转写继续，不保证平台 pause 前能先出现三选，更不虚假恢复录音。capture dialog generation/当前 owner/foreground/actual isShowing 联合消费，旧通知不能撤销新窗口。IME 用 typed phase、七种 Canvas 图标、48dp 语义按钮，预览重录是键盘内两步确认；资格失效撤销旧确认，已提交 native 只丢结果不伪装硬停。

## 本次 full 实际新增四 gate

原 `scripts/test-minimal-apk.sh` 四次入口全部真实执行，driver4200/compile270/JVM45 原预算不变，完整 NAV12 与 IME7 矩阵，未缩小为 targeted。

| gate | mutants | fresh compile/JVM routes | seconds |
|---|---:|---:|---:|
| navigation-ordinary | 12 | 25 | 329.628 |
| navigation-optimized | 12 | 25 | 331.530 |
| ime-ordinary | 7 | 15 | 353.708 |
| ime-optimized | 7 | 15 | 355.657 |

合计 80 次 fresh compile→JVM route，38 个 mutant-mode 实例各 compile0→精确外部 AssertionError1→原字节恢复 compile0/JVM0；四 gate 总时长 1370.523s。编译/工具/timeout 不算行为 red。

生产 Activity/Service/Controller 与完整调用者真实混编，finite Android fakes 只证明回调/owner/Canvas 命令。导航 production 副本由既有 generator 追加一个 LF，原SHA与compiledSHA分别记录；IME production 副本逐字节一致。所有 attempted ledger 的真实 exit、root_reaped/owned_exited、stdout/stderr SHA、source-copy/tool/driver SHA 在本轮绑定证据中逐项核验。

原 ZIP/全局导入/清理/unknown/空槽重试、Logs3 mutants、Kotlin/ABI/runtime/IME readiness/first-use/start、owner/PartRecovery/model mutants/Java-JNI/模块边界门槛仍由完整入口实际执行；不拿新矩阵替代旧门槛。

## 前序证据与审查修复

前序 A26 实现及 review repair、dialog generation、actual showing、late-notify proof 均保留原阶段证据，不回写旧失败。最后 late-notify 定点 ordinary/-O 对旧 fake 合法变异完成 compile0/目标 red1/restored0；真正 delegate 调用返回后才记 count/identity/target/order，并断言 fresh show 后仍 pending、实际投递晚于 fresh show、重复投递不执行。此定点证据为前序同输入绑定，不冒本轮重复执行；本轮正式 NAV green/restore 已包含强断言。

## 层级与未验

本次 JVM/JUnit/编译/静态 DEX 与包验不等 Android runner。系统任务栈/Back dispatcher/真实 Dialog queue、权限/系统 SAF/IME/Binder/grant、图标像素/TalkBack/大字体/旋转、麦克风、真实 native ASR/质量性能均 **NOT RUN**。

证据：`.work/bva-full-execution/execution/binding.json`，SHA `054930ca88ffb9d2dc2753d7aa08015cac3eb41ea306ec4152242f647df520b3`；当批 gate 根 `.work/build/bva-ui-MoTUS6/`。原始日志为 ignored 工程证据，不提交 APK/音频/模型/代理记录。
