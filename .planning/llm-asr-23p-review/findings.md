# Findings / llm-asr-23p / ime-review

准入：AGENTS、delivery、code-review、父三 plan 与精确 review 授权已读。bd context 为 embedded llm_asr；llm-asr-23p in_progress、owner p1-steward，无另 claim。HEAD 精确匹配，cached diff 空；9 tracked modifications 与授权一致，其他旧 untracked 不触碰。PI_MODEL gpt-6.1-sol；本审为同模型新上下文隔离，非跨家族。

证据边界：父已消费 targeted，不等于本审语义通过；旧 outer UNKNOWN/signal15 保留；本 reviewer 不执行测试构建。

首冻结：10 候选 mode/bytes/SHA 全同，manifest367fe2ae…b142、handoff21ce6d24…a842、HEAD/index 精确同。Herdr 当次同仓父 done、原 ime-build idle、本审 working，无第二产品 writer。完整9 tracked diff + 新status全文已读。

语义初核：Service previous catch 仅覆盖一次平台调用，false/RuntimeException 才单 picker；hide 与 launch 独立 catch；fresh alive/controller/shown/editor 后 renew，敏感 begin 拒绝/权限与busy在录音准入，commit显式且旧preview已失效。RequestRunner cleanup→finished→coordinator release 未改；新增实际worker测试分capture/native/cleanup/late-failure及queued；新fixture两修正确为旧tree引用与fresh默认非空status。

R1（P2/测试证据阻塞）：tests/kotlin_next_migration_test.py:428–430 新 ime_return_suite 裸 finally 调 cleanup_workspace，遇 unknown ledger/损坏JSON时再次 raise，覆盖正在传播的原超时/KeyboardInterrupt/执行错误对象。共享 Workspace.__exit__:482–489 原本显式保 primary/add_note，新路未沿用。静态发现，未执行故障测试；保输入/拒恢复机制本身有效，不能说会删活输入，也不否定既有正常mutant原始证据。最小修复仅保 primary 与 cleanup note，维持 remove=False/严格 quiescent。

独立只读核证：83 unique owned/root records及closed stdout/stderr SHA、四mutants普通/-O每项compile0/精确red1/restored0、source/caller恢复、35JUnit全零、27protected SHA、SDK entry API28 finalboolean/min29与旧host原命令预算顺序全部核同。未执行任何测试或编译。七复杂度源码 before=HEAD/current=冻结，两个工具二进制/raw/argvSHA全同，21ambiguous逐组映射（Java既有方法后移136行/新增ImeFixture、Kotlin backend新增、Python两个nested compile_run）；render+1热点不需重构，但driver分数不免除R1。

查阅错误：尝试 ImeSession.kt 返回 ENOENT；实时库存为 ImeSession.java，已全文读正确唯一源。一次 rg 指向不存在 ime-plan/verification.json，实际为 verification.md 已全文读；不据错误路径下结论。
