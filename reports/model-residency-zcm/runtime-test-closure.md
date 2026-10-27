# Runtime测试闭包修复：TARGETED PASS，待fresh窄审，非full3或交付

Task `llm-asr-zcm/B`，base `8b71b56b8927031be938d188bacafe0b530a4b8a`。父接受full2失败现场后，仅授权修复runtime exact2testowner的测试依赖闭包。

AdapterTest两处ModelAndroidHelpersTest引用改为同文件private synthetic MemoryGuard/TestIo。两类body从原helper精确复制，仅顶层private可见性与本地副本注释，不抽框架/不改原helper或ModelVerification API，不把整个core test/canceltest引入runtime源集。完整nofollow/文件identity/限长读取/atomic rename/fsync/dirsync/故障stage/callback字段和行为保留。其他14groups441checks、proof/maintenance-firstwrite/restore交错与worker真实收尾不删除、不改lambda时序。

source/self增加拒外helper依赖回流与private fixture结构的精确负例，原BODIES/CORE/SUITES/方法数量/pins/原XML负例保持；这不是代替真实编译的证明。Gradle/runtime owner filter、生产Java/Kotlin/JNI/native84/源锁patch/expected/签名版本不变。

只在test生成域精确备份fresh后运行一次原`:core:test :asr-runtime:testDebugUnitTest` strict/offline/reruntasks/cache-only #apk，原单worker1536m/JUnit512m/45000ms wrapper/Werror未降低。真实Gradle exit0，24 actionable tasks全部executed（1m23s），runtime compileDebugUnitTestKotlin真实执行成功；fresh XML core34/runtime1均0failure/error/skip。Runtime XML system-out确认完整AdapterTest `14 groups, 441 checks`，proof/maintenance/worker和三finding回归保留。普通/`-O` source/self目标负例与两份真实XML checker均通过。完整结果按`runtime-test-closure-inputs.json`与`.work/residentb-runtime-test-fix/`真实终态，原full2编译失败历史保留，不是行为red。

Core XML SHA `353fde4926597c807ae173451c6046d3339af7de39562ba11680569676e357b3`；runtime XML SHA `58d5725d8957c9e45ce06e77ff1310afdd8cc7cf170aee345b8ce030cea20dff`。Test-only备份1个既有文件、4个已存在必要test生成根fresh，未清主产品/native/package。原helper两body→Adapter私有body字节对应审计通过，其他Adapter文件仅两引用/import/本地fixture插入，原断言/main完全保持；runtime Gradle/source过滤和原helper未改。

本轮不full3/assemble、不重跑成功host/T5/SDK578/三包，旧partial3包/native/receipt保护，旧full1/2仍FAILED。公开固定sample只原core既有required body允许读，不模型graph/weight/tokenizer/私音频/设备/DSO推理/新下载/密钥读取导出。完成父消费后另授fresh窄审与后续full，无stage/commit/Beads/二级派权限；实际ASR/Android runner/设备/PSS/质量性能/ASan仍NOTRUN。
