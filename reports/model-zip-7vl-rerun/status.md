# 7vl 修复 caller 后新 full：第二处原 host oracle 首停

Task `llm-asr-7vl`；唯一验证 leaf `zip-7vl-full`，父 `p1-steward` 唯一 Beads writer。
**FAILED / BLOCKED；没有安全提交或 ZIP APK 交付。**

已接受 caller 修复及透明准入补充后，唯一新正式 full BG `b1bfbf5e1` 真实 exit **1**，
约55分19.5秒。原入口、strict/offline/cache-only、全部门槛与资源预算未改，无盲重试。
首停 `:verifyHost` 的 `PartRecoveryTest.java:150`，`AssertionError: check 6`：
旧生产 mutant 的精确替换 anchor 仍为 `reclaimEntryPart(e)`，当前已审 U1 生产源码为
`reclaimEntryPart(e, progress)`。替换未发生，`check(!buggy.equals(src))` 在写/编译
mutant **之前**拒绝；这不是有效的生产行为 mutant 红，也不是 timeout/cache 错误。
生产 PartRecovery 初始 green 已完成；本 leaf 未修改测试/产品或扩大范围。

[新 full 覆盖及诊断](full.md) · [输入、三包与实际负例证据](inputs.json) ·
[父已接受审查摘要/未测边界](review.md)。

**本次已正式执行** ZIP 普通/-O 全矩阵（每模式17混编/31JVM/48 owned child），
picker 普通/-O（各6 child）、全部 C1/C2/U1/N1 与11个最终出口 body、ZIP八个
可编译 mutant 和七个 halt/recovery、原 runtime15全链及此前原 host 各协议。
旧 caller 的 NoSuchMethod 阻塞已不再出现。SDK/native/JNI子链、三包静态checker、
host7 JUnit、checksum0→1→0通过。

**尚未完成** PartRecovery mutant及之后的原host尾链、core/runtime35 JUnit、
production/isolated Lint、五阶段 fixture、根JNI/其余全门槛和独立最终产品 checker。
旧taw/第一次失败full/XML/receipt不作当批验收。

247build/10module/88native/3external及工具冻结 pre/post全等；source37+已审caller+
baseline保持，70owner/8新untracked库存。版本仍 `0.6-debug`/code6，三包生成不等full
通过。设备/runner/install/SAF/Binder grant/lifecycle/mic/private/大ZIP权重ASR/perf/
掉电/empty-cache hermetic均NOT RUN。父消费具体失败后另授最小处置，本leaf停止。
