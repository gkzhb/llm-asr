# R1-main self review（不冒 fresh CLOSED）

唯一生产driver变更：mutant恢复try前restore_source=True；仅except StopUnconfirmed设置False并bare raise同异常；finally仅安全标志True时恢复。后续恢复SHA断言/compile不可达，compile_and_run内shutil.rmtree(classes)也在run抛错前未执行。正常或已确认停止的其他错误保原finally精确恢复。SID/pidfd/owned_tree完全未重写，wrapper base097cf23a…131adf。

现cleanup追加main-unconfirmed，实际调用driver.main/真实源复制/真实mutant/write/finally/owned_tree。仅测试级run seam跳过前green0工具执行（不是ABI/编译通过证据），失败mutant用actual_run+真实basewrapper、fake普通非execshell/compilerworker实际活着，stop观察seam真抛OSError而非伪alive/success。外部核StopUnconfirmed、当时mutantSHA、classesmarker、无后续调用与tree，再finally所有已观察SID内PID/start/pidfd回收工作、确认停止后删树。旧实现明确目标red1，实际source被恢复原SHA而worker仍活，不用compile/cache错误冒red。其余12case完整保留，新增一项13case；host现有调用自动纳入，不需新增文件/host接线。

测试工作目录新r1-main。原77/345/404和旧review报告不覆盖。真实生产mutants另跑（非测试级跳过），本批只必要ordinary/-O cleanup、mutants/host/format；fast源码路径未变不重复，full后置fresh点审CLOSED。未Beads写入/stage/commit/设备/下载。
