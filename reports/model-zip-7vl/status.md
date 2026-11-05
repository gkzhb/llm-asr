# 固定 ZIP 导入 7vl：正式 full 首停封口

Task `llm-asr-7vl`；唯一验证 leaf `zip-7vl-full`，父 `p1-steward` 唯一 Beads writer。
**FAILED / BLOCKED：未提交，不能交付 ZIP APK。**

唯一原入口 cache-only / strict offline full，BG `b5b63fd8a` 真实 **exit1**。
`:verifyHost` 初始 `KotlinMigrationTest` 抛出
`NoSuchMethodException: ModelPickerTickets$Ticket.<init>(int)`：旧反射 caller 仍要求
单参数私有构造，而当前 typed picker 的真实私有构造为 `(int, Mode)`。
这是实际编译成功后的 host ABI 契约阻塞，不是合法 mutant 目标红或缓存失败。
未改源码/test/pin/锁/metadata/gate，未 prepare/下载、扩预算或重试 full。

[当次命令、覆盖与失败](full.md) · [冻结输入、任务与三包身份](inputs.json) ·
[父已接受 fresh 审查摘要及边界](review.md)。

247 build / 10 module / 88 native / 3 external 当前输入 pre/post 全等；
37 候选 + baseline 身份不变，70 生产 owner、8 新未跟踪文件均库存。
三包构建/静态 checker、host7 JUnit、真实 checksum 0→1→0、native/JNI 子门槛已执行。
**ZIP/picker 普通/-O、后续原 host/runtime15链、core/runtime35 JUnit、production/isolated
Lint、五阶段 fixture、最终独立产品 checker 未执行。** 旧日志/XML/receipt 不冒当批通过。

三包虽生成，仍非 full 通过或 APK 交付。版本保持 `0.6-debug` / code6；仅新 APK SHA 区分。
设备、Android runner/install、系统 SAF/Binder grant/lifecycle、麦克风/私文、真实大 ZIP
权重导入/ASR、latency/PSS、掉电及空缓存/hermetic均 **NOT RUN**。
下一步由父消费失败后另授最小 caller 契约处置/必要重验；本 leaf 停止，不自行修复或提交。
