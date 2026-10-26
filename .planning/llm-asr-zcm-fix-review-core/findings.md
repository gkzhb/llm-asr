# Findings / final

- CORE-R1 CLOSED：public import/delete/reclaim scoped marker覆盖实际写、streamclose、failurecleanup，import完成asset后显式close再自身finalSHA；noMutation阻新begin/restore/commit，current requireReady两marker检查，DIRTY每新写事务先确认。
- CORE-R2 CLOSED：state-only invalidate/markInvalid，保等待publication后二次清current/epoch，unlock后notify；当前canonical首回调实际同线程restore+跨线程probe补足旧两通知fixture在修后不触发的局限。
- CORE-E1 PARTIALLY CLOSED，剩余P2 blocking：当前finalCOMMITTED八阶段故障/不同rollback异常/真实Controllerowner/control/lateepoch均真green；但未实际让directmutation与restore线程在真实Controller finalledger/succeed publication持disk阶段等待，释放后核再次invalidate/顺序/usableproof，父明确要求的反向真实barrier矩阵缺失。具体最小建议见review.md CORE-E1-remaining，不声称生产已发现race。
- 可见COMMITTED而dirsync+rollback失败的后续新实例恢复，符合批准brief“完整终态+连续性可证”的允许政策；非任意失败授READY，也非独立JVM/设备断电证明。
- direct repo保证新verify不跨待写lifetime；真实App/IME/native依Graph shared owner，不把新marker宣传为已过native准入的外部API读lease。
- independent首尾：45candidate/18seal/65build+7module+31supplemental/28protected全同，HEADf7d1d79/indexempty。142ownedraw全部SHA/exit/stdout/stderr/ownedexit/rootreaped同（1160/261）；旧203不重标当前新执行。
- oldsource四文件同原freeze，旧CORE-R1/R2 compile0/精确AssertionError1/currentgreen0；新canonical corefault ordinary-O各1compile4JVM0；四mutants各9compile11JVM精确red/restored0并同当前源。
- complexity223→224实际raw/installedbinary/source SHA同，compare2 FAILUNKNOWN、delta0bytes，已人工映射import/delete bodyrename与新scope安全分支，不改旧基线，不把盲区记0。
- 未启动BG、未改产品/Beads/index、未委派/编译/测试/checker/build；actualGradleApp/AGP/D8/full/currentAPK/device/model均NOTRUN。父消费报告后唯一下一步是明确接受并另授原writer补E1-remaining，不提前full。
