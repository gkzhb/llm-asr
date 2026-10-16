# R1 main 恢复路径 targeted（非 fresh CLOSED）

Task llm-asr-ap5，owner/唯一Beads writer父p1-steward session01a140eb-c721-74b3-90a4-876b86ba9812/HerdrwY:p1；同一leaf p2enum-build-3 session01a143ce-b6d9-739d-abc9-acc7b2c4f3da，本次HerdrwY:p2Q / newapi/gpt-6.1-sol high。main/base a5204512cc779ed8e85116fdf7ea4453abf0b9ce，无worktree。父接受review3唯一剩余P2，原reviewer停写后续接；不写父plan/Beads/二级委派/stage/commit。

## 唯一小修

真实main内mutant compile_and_run抛StopUnconfirmed时，旧finally先write_bytes(original)，即便outer保tree也已截断/改写活compiler使用的副本。本次仅恢复块设置restore_source标志并except StopUnconfirmed关闭恢复、bare raise原异常；其他正常/已确认停止失败仍finally精确恢复。不会继续恢复编译或清classes。driver-exact-delta证明仅此块变，SID/pidfd/owned_tree完全未重写；sharedwrapper097cf23ad7f0e0a5cd8ebcf4e5f87523d3505bc8f90d389d9434ad5b76131adf=base、其他caller/twoenum/Java caller/baseline不改。

## 实际 main 目标 red / green

现cleanup追加main-unconfirmed。实际调用driver.main，真实host_sources/生产复制/常量交换/嵌套compile_and_run/finally/owned_tree，未手抄恢复实现。仅测试级run seam略过之前green0两次工具工作（不冒ABI成功），失败mutant走actual_run+实际basewrapper、普通非execshell/等价JVM worker。stop-confirmation seam抛真实OSError；外部观察StopUnconfirmed、活worker、tree、mutantSHA及classes marker；调用清单精确禁止后续compile/run。测试finally记录所有owned SID身份，pidfd/start安全终止并确认后才删保留tree。

旧实现main-old-red exit1精确RuntimeError R1 main restored live mutant source after StopUnconfirmed；actual main源SHA被恢复原SHA、当时worker仍活、保tree/classes、finally回收。红前driver与红测试全文真实原位snapshot及inputsSHA保留，不是重建历史。

新normal/-O/host矩阵13项=原12case完整保留+新actualmain1项；sourceSHA必须与当时mutant一致且不同原源，StopUnconfirmed外传/无后续工具/classes保留、finally收回后才清tree。原12项仍实际driver.run/owned_tree/basewrapper、多PGID/普通shell链、TERM/HUP/outertimeout及缩时GNUdeadline124；120→0.35机制非120wall，不改变base策略、不期待137。

开发首轮cleanup-green失败单列：原第12项finally读取刚退出/proc stat抛ESRCH，新增main尚未启动；不是目标red。仅测试identity增加ProcessLookupError视进程消失，生产收口不改。一次精确身份检查全部停止后回收残tree，development-finally-recovery.json。不能拿oldred main observation冒green，纠正记录留plan。

正常生产mutants必须另行真实编译（普通+host各一链），每链5fresh compile0、两指定Java顺序AssertionError1、精确SHA恢复重编green0和正常322checks；测试级跳过仅fault用途。

## 证据/保护与后续

新r1-main命令execution/exit/完整logSHA、输入前后snapshot、cleanup JSON bundles（原path/bytes/SHA/JSON或log完整内容）、真实production enum-runs及最终freeze/manifest/closure保留。旧77/345/404/freeze及review报告不覆写，不重复复制旧证据；protected历史逐SHA，26excluded/578nativeobjectsDSOprovenance/32旧包receipt保持。候选精确SHA由最终closure给出，不自引用本报告。

多域current211build/10module/80native/63source（12kt51Java），仅driver/cleanup构建输入SHA变，module/native/source保持，docs追加为supplemental/tracked差异。wrapper前轮已回base不再delta；native未重链接。fast源码路径未改不重复fast/graph，保前轮证据历史，不称当前重新执行。

最终普通/-O/host/productionmutants/format真实终态与无后台事实见handoff/closure及verified-command-index；此文不单独证明收口。无full/Lint/Android全源/APK/JNI/native构建/Android-test-build/DEX签名包验收/设备runner/ADBmic/private/模型/下载prepare/remoteCI/push；无stage/commit，不交APK、不关ap5。父消费后freshsol fork:false只本R1点审（同模型上下文隔离非跨家族），CLOSED之后唯一full与另授权两阶段安全本地提交，leaf不自宣CLOSED。


## 最终 gate 补充

普通/-O/host各13case均0，真实productionmutants普通+host两链均0（五compile0/两指定AssertionError1/精确恢复green0），host原全部gate保持。format1action0、pipeline0/0/scan1，旧aapt2实验warning披露。7工程commands/7bg全部真实自动终态消费（有效mainred1/testESRCH开发失败1/五成功0），无pending、不重跑无变化fastgraph。最终多域freeze/历史保护与逐文件candidateSHA以本批closure为准，未review-CLOSED/full/APK/提交。
