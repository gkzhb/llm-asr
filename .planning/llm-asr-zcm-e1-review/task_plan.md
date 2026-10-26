# CORE-E1 remaining fresh 定点回审

## Binding
- task llm-asr-zcm；reviewer resident-review-5 / project-agent；父 p1-steward/session01a16ecc-7157-76a9-8065-dbcf8e84589a 唯一 Beads writer。
- cwd /home/zhb/gitrep/llm-asr；main f7d1d795f591299a3aa70194537652ad33691275，index empty。
- 唯一写域：本目录三 plan 与 .work/resident-e1-review/；产品冻结只读。newapi/gpt-6.1-sol/high 新上下文隔离，非跨家族独立。
- 权威 e1-focused-review-authorization.md / e1-publication-authorization.md。只审上一 CORE-E1-remaining，不重开父已接受的四 CLOSED finding。

## Goal
通过真实源码与既有 ordinary/-O raw，判断反向 final publication→direct mutation/restore 等待矩阵是否满足批准的窄承诺；输出 review.md、verification.json、handoff.md。只运行 Git/只读元数据查询与标准库文件身份审计；禁止产品执行、测试编译/checker/复杂度/build/下载设备模型/private/Beads 写/stagecommit/二级派。

### Phase 1: 准入与首轮身份
**Status:** complete
全文恢复 AGENTS/skills/父三 plan/授权；首轮独立45candidate、11seal、65+7+5+28与HEAD/index全同，10owned/20stream核同。

### Phase 2: 语义与现有终态证据
**Status:** complete
逐行审 publicationWaiters/TestIo/真实 Controller→commit→disk→mutator/restore；核10owned/20stream、source/classpath/预算/旧兼容及测试增长，区分实测与合法允许顺序。

### Phase 3: 尾核与正式回报
**Status:** complete
重核冻结身份，输出 CLOSED 或精确 path/trigger/最小补例；标 NOT RUN 与 noBG 后停写。

## Next Step
reviewer正式停写/noBG；父消费 review.md、verification.json、handoff.md 并接受CORE-E1-remaining CLOSED后，才另授full/当前APK/两阶段安全提交。

## Errors
- 只读 CLI discovery 的 `herdr agent` 显示用法并 exit2（预期帮助接口），未执行工程命令。
- 首次 stdlib 身份审计把 input-domains.old_apks 的描述字符串误作库存而汇总 false；一次诊断探针进一步遇 TypeError。保留 identity-start-attempt1.json，改读真实 protected-after.json 的28条库存后全同，不是输入漂移。rg 一次探测不存在的 fix/handoff.md exit2，实际文件为 fix-handoff.md；未执行工程命令。
- stdlib 额外 class-byte 辅助审计初次把 JVM argv[4]（main类）当 CP，StopIteration；已改为 `-cp` 后一个参数。第二次探针将 Kotlin companion 私有方法误查顶层 class，辅助all_pass=false；保存 evidence-audit-attempt1.json 后改查真实 $Companion.class，all_pass=true。均为审计脚本诊断，不是工程测试/源漂移；没有以失败改门槛补绿。
