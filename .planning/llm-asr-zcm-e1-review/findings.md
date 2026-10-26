# Findings / resident-review-5

- 已全文读 AGENTS、delivery、code-review、planning、stewardship、Herdr skill、父三 plan 与两份 E1 授权、旧核心回审和新 handoff。
- bd --readonly context 确认 embedded llm_asr/project6c8fcd38；显式 show llm-asr-zcm 为 in_progress/p1-steward，无 dependency/dependent；没有 Beads 写入。
- Herdr 只读 list：父 wY:p1 done；原 resident writer wY:p5A idle；当前 reviewer wY:p5F working；IME writer idle，无产品并写证据。未创建/控制任何 pane。
- 首轮独立核45candidate、11seal引用、65production+7module+5supplemental+28protected 的 mode/bytes/SHA 全同；与 fix 的生产/模块/保护库存相同。10owned 均 exit0/root_reaped/owned_exited，20stream内外SHA同，ordinary/-O 各10源与当前字节同、各1compile4JVM。identity-start.json all_pass=true。最初库存字段选择错误已保留并记录，不是漂移。
- 新 E1 scope 仅四路径 delta（两测试两报告）；与旧 current-host 冻结测试逐字 diff：helper仅加 afterSync一行，Controller仅新增 waitForPublicationLock/publicationWaiters 与 committedFaults三模式调用，没有删旧矩阵。combined 仅观测 marker-first，禁止提升成两顺序均实测。old224 complexity 仅历史，不运行工具。
- 语义：真实 Controller→RequestRunner→owner/Control→ModelVerification.commit 持 disk；kind3 afterRename 在 io.write 返回与 gate.succeed/current=proof 之前。barrier 中 waiters 在唯一 lock() 生产方法内 WAITING，非仅 start。delete用同 proof 的 public deleteAll，恢复线程直接独立调用同 proof.restoreFast，符合反向序列授权。
- 放行后 delete 首 asset hook 前 afterSync 观测DIRTY实际文件force/close完成；外线程读kind1、epoch>publicationEpoch、notready/requireReady拒绝及资产仍在，放行后asset真删除/全部worker join+isAlive=false/异常ref空/ownercontrolfree。requireReady拒绝不直接反射current字段，结合current=null生产源码判定，不夸成单独字段探针。
- restore-only 两模式实际 true/guard成功，证明 disk 释放后完整提交合法恢复；combined 两者真实排队、实观marker已建立restore抛IOException。非公平锁可restore先取得并合法短时READY随后撤销，测试允许但未实际跑出，不是验两顺序。窄承诺不需对所有时间窗另造无界matrix。
- callbacks仅记录/有界barrier；关键 check 在外部线程，Throwable ref外验不受listener吞错；finally先释放两barrier再join，fixture registry真isAlive门保证未知退出保目录，既有workers用例同JVM实跑，保主异常语义沿Kotlin use，不重开PLATFORM-R2。事件的true-exit标签本身不是死亡证据，以join+isAlive核实际退出。
- afterRename 尚未目录sync，正好是 final write 未返回/成功相邻路径；commit从io.write尾到gate.succeed/current安装仍同一disk lock，排队者不能穿过。旧八stage/rollback/lateepoch保留同JVM成功，足覆此剩余反向等待，不声称Android耐久或所有窗口穷举。
- 正式结论 CORE-E1-remaining CLOSED，无本范围blocking。尾核identity-end全pass，首尾所有身份节完全相同；verification.json记录实际独立核查/notrun/诊断历史，旧失败审计attempt不删。无工程执行/产品写/index/Beads写，无新后台；父接受后才另授full与交付，旧APK不交。
