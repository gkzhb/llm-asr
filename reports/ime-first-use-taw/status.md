# IME 首次使用 taw：正式 full 失败封口

Task `llm-asr-taw`；唯一验证 leaf `taw-full`，父 `p1-steward` 唯一 Beads writer。
**状态：BLOCKED，非 APK 交付，未 stage/commit/push。**

唯一获准 cache-only / strict offline full 真实后台 `becdd8726` 已失败终态，exit **1**。
失败于 `:asr-runtime:generateDebugUnitTestLintModel`：离线解析
`:asr-runtime:debugUnitTestCompileClasspath` 无可用缓存解析 `junit:junit:4.13.2`；
同时报告 lock-state 中 JUnit / `org.hamcrest:hamcrest-core:1.3` 未解析。
这是依赖解析阻塞，不是合法行为 mutant 红，亦不能据此断言 artifact 文件物理缺失。
未修改源码/identity/锁/metadata，未补下载、扩时限或重试。

[当次完整事实](full.md)、[输入逐 SHA](inputs.json)、[产物与分层证据](artifacts.json)。
240 build / 10 module / 84 native / 3 external 输入与启动 freeze 全等，
19 受审候选 + baseline 精确保持；原 1986 tracked / 752 excluded 文件均未变。
仅允许构建输出变化；本报告是 supplemental，不冒充编译输入。

本次已完成原 host 全链、真实 Java/Kotlin SDK 编译、JNI object、native 增量链接/身份、
三 APK 静态签名/DEX 隔离、7 host JUnit、真实 checksum 0→1→0。
**尚未完成**原 35 core/runtime JUnit、本次 production Lint report / Error/Warning 验收、
隔离 Lint 与五阶段 fixture、独立产品最终 package checker 等 full 必需后续门槛。
旧 XML/receipt 不借用。生成三包不表示 full 通过或允许交付。

Android runner/install、设备/系统 IME/SAF/Binder/grant、真实麦克风/私人输入、ASR、
latency/PSS/4K16K runtime 均 **NOT RUN**。构建在 main `3899242` dirty freeze 下执行，
没有被测试的新 commit。下一步由父决定是否另授窄范围离线缓存解析调查/必要 prepare；
本 leaf 停止，不自行重复 full。
