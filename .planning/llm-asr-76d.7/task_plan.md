# M6 窗口避让与返回适配 — llm-asr-76d.7

## Goal
以最小实现统一首页/模型/日志页 systemBars、displayCutout 与窗口返回处理，保持录音取消、推理忙拒绝、模型维护及 IME 隔离契约。生产回归、Java/Kotlin/JNI/完整 APK/包与构建输入绑定、必要直接源码审查、安全本地提交通过后交付；无授权的 Android 设备视觉/返回测试明确 not run。

## Binding
- role: project-agent；task: llm-asr-76d.7；assignee/唯一源码、Beads、plan writer: llm-asr-m6。
- 用户授权：继续认领任务开发；仅选本任务，不领取整个 epic。
- cwd/worktree: /home/zhb/gitrep/llm-asr；main；起始 HEAD: 82a5563。
- session: 01a0f7a4-5270-7015-b76f-af1ffbfab137；Herdr wJ:p1。
- plan: /home/zhb/gitrep/llm-asr/.planning/llm-asr-76d.7/；不写根历史/共享 active_plan。
- M0/M1/M2 closed；ap5 deferred/保留原 assignee。同项目另一个 live session idle 工具分析，无其他 working 产品 writer。

## Scope / Boundaries
仅 Activity 窗口/insets/back 适配与相关测试、构建输入接线、文档。保持 min29/target35、包名/组件/签名、权限/模型数学/单owner/录音gate/IME策略。不混 M3 模块、Kotlin 全迁、产品优化，不委派/设备/麦克风/私人文件/push/重写历史。保留两个他人未跟踪分析目录。约8GiB低并发；优先缓存/离线，不新增未验证依赖。

## Current Phase
Phase4 in_progress：b8f9fc4e0末次strict offline全验证通过，164freeze/current/report与74产品匹配；非敏感报告已保存，准备安全审计/本地提交，未提交不得交付。

### Phase 1: 认领与调查
- [x] 核 AGENTS、Beads/M0/M2/ap5、memories、Git、Herdr，原子 claim 并读回。
- [x] 核三页生产源码、Manifest/back 行为、现 Gradle 与测试接线；基线M2已提交报告保持不覆盖。
- **Status:** complete

### Phase 2: 最小实现与生产回归
- [x] 系统栏/cutout/IME 避让、现代返回接既有动作，初始化失败页不遗漏；平台API不引依赖。
- [x] 可失败接线负例、旧源码红测试、生产fake-platform69checks/3行为mutants实际通过。原录音/模型/owner/IME测试保留。
- **Status:** complete

### Phase 3: 完整验证与审查
- [x] full host/Java-Kotlin/JNI/APK/签名权限组件/实际输入指纹核验。
- [x] 直接源码审查与checker防误证据修复；测试覆盖和设备 not run 分开记录，非独立审查。
- **Status:** complete

### Phase 4: 安全提交与交付
- [ ] 文档/plan/Beads与必要知识；暂存安全及指纹检查，本地提交。
- [ ] 满足工程验收才关闭task；提交后报告身份与设备验证清单。
- **Status:** in_progress

## Next Step
暂存本轮明确文件，审计无私人内容/产物/密钥及164index指纹一致，成功本地提交后关闭本task并报告交付。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 根 findings/progress 读取超50KB | 1 | 分别补读315/341至末尾，完成恢复 |
| Beads汇总ready/blocked口径不同 | 1 | 显式ready/show/list核依赖与assignee，不按汇总猜测 |
| 新接线负例以this::onBackPressed期望一次，模型页实际两次 | 1 | 用完整BackRegistration构造字符串定位，3组回归通过；非产品行为红证据 |
| Manifest首次edit旧文本非唯一 | 1 | 原子拒绝无部分修改，改三节点唯一匹配 |
| app/build.gradle.kts及android_architecture_test.py路径不存在 | 1 | 使用实际android/app/build.gradle.kts与architecture_boundary_test.py |

## Blocked stop condition
owner/runtime冲突、需未授权架构/设备/私密输入、无法保持安全/签名/native契约或无可验证测试路径时停止；记录证据、尝试、blocker和下一输入，不抢占/放宽门槛/以旧APK冒充本轮交付。
