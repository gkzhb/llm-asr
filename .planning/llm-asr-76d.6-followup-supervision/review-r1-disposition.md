# SAF open fresh审查处置

## Binding
任务llm-asr-76d.6；父/唯一Beads writer p1-steward；base add5d2c；2026-10-15。审查为用户指定同sol fresh上下文隔离，非跨家族独立。

## R1 / P2 — CLOSED（fresh定点复核接受）
- 来源：.work/m5saf-review/final-review.md / handoff.md，父已完整消费。
- 位置：FixtureInstrumentationTest.java:195–196。
- 父读取实际生产ModelRepository.importFrom确认在source.open之前已progress.fileResult(model.bin,PENDING)；普通FILE_FAILURE终态保留此历史，failedFiles空合理但fileResults空错误。
- 影响：未来仪器正常执行必在inspect/retry前失败。静态确定路径，未执行Android red，原编译/DEX/JVM绿不消除此问题。
- 最小允许修复：只改该suite的注释与断言，保留failedFiles空，精确fileResults单项model.bin且status=PENDING；必要ModelReports import。保留inspect历史map相等与同controller retry/READY/字节SHA及所有取消断言，不改生产凑绿/不删断言。
- 原writer接续独占plan与新.work/m5saf-build/r1-fix证据；允许新reports/android-test-m5-saf-open/{r1-fix.md,r1-input-sha256.json}。原targeted报告/freeze/receipt和review证据保留不覆盖；原plan错误推断以追加纠正保留历史，不伪称旧版本已修。
- 必要checker/统一android-test-build与format真实终态→新inputs/三包绑定/保护域审计→停写→fresh定点复核。无Android行为redgreen可执行，明确notrun；不造host Android模拟框架。
- R1 CLOSED前不full、不提交。无需重开已核旧12/provider/checker全范围或P1-B历史；不无限扩大审查循环。

## 修复及复核接受
父完整消费.work/m5saf-build/r1-fix/handoff.md与.work/m5saf-review-2/{final-review,handoff}.md。最小suite修复与精确PENDING断言正确，inspect/retry/cancel断言保持；新targeted118actions/format/209build10module80native与三包证据已独立核齐，64scope首尾不变。R1由fresh定点审判CLOSED，父接受，不是Android运行redgreen。进入另leaf一次必要full，源码冻结，未提交仍不得交付；M5保持未整体完成。

## 其余结论
其余限定范围NO ADDITIONAL BLOCKING：209build/10module80native、11seal、三包全DEX/15原工具raw/7JVM XML及targeted与format终态review独立核齐。此为旧版本事实，不当修后current。M5仍in_progress、设备授权及所有runner/SAF实际运行边界保持。
