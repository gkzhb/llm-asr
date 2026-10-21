# Kotlin public core boundaries / targeted checkpoint

Task `llm-asr-ap5`，base `74f6acf3d2ed2e4589805bf3d3e144764ffeae35`。本批20个生产Java owner行为保持迁为Kotlin（1477原Java物理行），当前 **37 Kotlin / 26 Java / 63 top owner**。全部仍属core，不改模块/依赖/工具版本、JNI/native数学、权限签名、录音gate或隐私策略；没有恢复暂缓的TranscriptResult。

## 本批范围

- ASR/IME：InferenceAdapter、ImeController。
- 日志：LogExportController、LogExportPage、RuntimeLogCodec、RuntimeLogText、RuntimeLogWorker。
- 模型公开边界：FileSafety、ModelAccess、ModelEntry、ModelManifest、ModelProviderBoundary、ModelReports、ModelSource。
- 模型页面策略：ModelPageSession、ModelPickerTickets、ModelUiText。
- 文本：ResultFiles、TextExportController、TextExportPage。

保留字段/构造/方法descriptor、原公开flags与泛型/throws、Java SAM和真实default、nullable入口原异常顺序、identity、独立monitor/this锁和volatile。Worker token和实际IO收尾、TXT queued撤销、两export显式close/fatal优先级不改变，未用use或新状态机替代。

## 当批真实targeted证据

- 固定Nix `apk --cache-only`、Kotlin/stdlib2.0.21、JVM8，Gradle严格offline；无prepare/下载/写锁或metadata。
- 最终v2同Java caller SHA256 `c54964197dd2be832cb940fd235d15fa06a03ebe5b4f2e3fbb6aa42bd738c410`：隔离base20Java fresh混编/javap/run0、796 checks；迁后fresh0、801 checks。原公开ABI子集normalized javap delta0，不把常量池索引变化误算泛型退化。
- 五nested类型（Picker Ticket、Log/TXT Ticket/State）保原private同descriptor；父明确仅准原参数追加单DefaultConstructorMarker的public synthetic编译器桥。新增Companion/合成方法/marker构造分列可反射观察，**不是全部反射/JVM成员等同或安全沙箱**。2个正常Java consumer正例及23个实际构造/真实mangled桥/private字段负编译按具体诊断拒绝。未public化普通产品构造。
- static helper通过companion逐声明open保原非final static flags；根据真实71 instance-open/25 object-open诊断逐声明Suppress，后续编译warning0，Gradle保allWarningsAsErrors，没有全局关warnings。
- 十个真实生产副本mutants普通、Python `-O`、正式host三链各compile0→精确外部AssertionError1→原SHA恢复freshgreen0：Entry hash、Manifest重复、ImportPlan零预算、Source后cancel、provider假cancel、foreign Ticket、Codec flush、Worker stale-token、TXT queued revoke、Adapter null固定失败。纯abstract没有伪造方法体。
- 完整host exit0：801 ABI checks、原enum/ports/phase真实mutants、13cleanup、model三mutants、PartRecovery实际mixed fresh、picker123 checks/三行为mutants、Worker107、IME177、Adapter10组354及全部原gate均实际执行。
- fast exit0，23 actions全执行；core34/runtime1共35 JUnit failure/error/skipped均0；真实module编译正负例保持。format exit0、1 action；正式窄46文本范围保持，候选另逐文本UTF8/LF/尾空白核。
- before/after211维护源双collect0，同cccc1.8.3/knots1.18.0及binary/flags。**旧compare文件0字节且未保独立exit/stderr，旧文稿“实际compare2”未证实，不能据预期追溯旧终态。** 本轮C1经另授权仅新跑一次廉价compare：原不变manifests/comparator，新时间/argv/输入SHA/exit2/stdout/stderr完整保存于`.work/ap5batch-r1-fix/c1-compare/`，实际FAIL-UNKNOWN为20Java→Kt范围变化。此为新补证，不覆盖旧空文件。cccc Kotlin、knots构造/init和Worker部分命名方法解析缺失unknown，不填0或宣称复杂度下降。3lane、IME/Manifest和driver增长已逐方法人工说明供fresh审。

## 历史失败不重标

首Kt compile0/javap0/run1命中过强public构造特征，经父明确B1精确澄清后改为五类型白名单并重新原Java/Kt同caller；不是行为mutant red。访问负例首轮因中文诊断与英文预期不符而失败，保留日志，固定javac诊断locale后真实正负例通过。static helper初始final差异实际修正，不豁免。旧caller/基线/失败冻结保持。

## 输入/保护与未完成

动态current **214 build / 10 module / 80 native**，数量仅当批；targeted前后逐SHA相同。相对已提交phase：build20删20增8授权变；module全同；native20路径rename，其余保持，不能称native来源库存相对base全等。578 MNN对象SHA/inode/mtime、DSO/provenance、旧三APK与正式receipts保持；fast仅合法更新生成class/JUnit，旧PartRecovery固定输出已先备份。旧phase APK不是本批产物。

fresh同sol fork:false上下文隔离审（非跨家族）已给REQUEST CHANGES两P2：新PartRecovery/model-review混编调用未保证普通后代真实退出，正常成功不覆盖timeout/中断/外层host清依赖风险。父已接受定点修复，两caller复用既有picker owned机制的窄adapter，Java启动前work/依赖租约及cancel/父EOF、model SIGTERM中断、host未确认拒删保护；本轮实际旧两caller+未改wrapper的不同PGID/父先退故障各命中外部AssertionError1（返回时compiler仍活，先观测再紧急回收）；修复普通/-O故障driver各0，覆盖Part四case、model六case及未确认保留/真实下一次拒删/实际退出后恢复。必要host exit0/422.614s，原Part空间预算mutant和model三compiledmutants保持，fast0/23actions35JUnit全零，format0；变更工具同版本211→213源双collect0/热点人工说明齐。后续fresh已接受R2 CLOSED/C1 CLOSED及141adapter/331faultdriver规模，但R1剩余新增cleanup独立Python无界wait仍OPEN。父选择移除该删除子进程：当前Java线程markerfailclosed/NOFOLLOW同步清理、interrupt检查，并以同work文件锁覆盖清理到本轮结束，阻止被JUnit中断但尚在慢FS的线程与下一次准入并发重用。该窄修仅四路径及定向cleanup故障/Part实际空间mutant/fastformat、变更工具复杂度；不重跑完整host或C1compare。

证据时点更正：此前fixedPart先waitJava退出，再boundedwaitworker后记录false，仅证明“返回后有界观察内、emergency前停止”，**不证明caller返回前worker停止**。TERM/父EOF短异步窗口安全依据是work/dependencyreservation拒重用/拒删，旧封口保历史不覆盖。本轮R1cleanup实际旧入口受控删除进程阻塞+Java线程中断，编译0后外断言1确认Java返回时删除子进程仍活，观测先落再紧急finally回收；新入口普通/-O各0，实际同work锁拒并发、interrupt恢复后不重建、NOFOLLOW/symlink、保留lease拒重用及恢复通过。必要fast0/23actions35JUnit零失败错误跳过，内含Part实际fresh5Kt4Java/EXPECTED_FAIL空间预算mutant；format0及同版本213源工具双collect0/热点人工处置齐。本点未重跑完整host/十mutants/C1compare；随后定点审接受同步cleanup/时点与规模，但发现固定Linux/JDK传统fcntl锁下T2另channel重叠失败后close可能释放T1跨JVM锁，R1-lock仍OPEN。父另授权同四路径最小修：normalized work在任何FileChannel.open前先取得同JVM非阻塞reservation，finally完整释放跨JVM锁/关闭channel后才释放JVM准入；actual三方T1blockedinterrupt→T2拒返回→C独立JVM的旧当前源同test compile0后精确AssertionError1实证T1仍活时C获得锁；修复普通/-O各0，C实际拒绝且old-input不变，T1真实退出后same/crossJVM恢复准入。必要fast0/23actions35JUnit全零、Partfresh空间mutant保持，format0和同213工具双collect0/热点人工说明齐。四pathdelta/保护封口后待父仅R1-lock点审，不凭green预判CLOSED；未host十mutants/C1重复或full提交。旧targeted/review冻结不回写。**仍没有full、当前APK构建/包验收或本地提交，不是APK交付**；R1/R2/C1定点fresh回审CLOSED后唯一full与安全本地提交另授。未生产Lint/真实JNI-NDK复编/fixture五阶段/当前包身份检查，不用targeted代全门槛。

Android runner发现/执行、设备/ADB/模拟器、麦克风/私人文件、真实SAF/Binder/grant/系统IME/JNI/4K16K运行、模型质量性能/计算、下载、远端CI/releaseR8均not run。ap5非Java0不关闭，不连带关闭M4/M5/tc3。

详细候选/命令/raw/ABI/复杂度/保护manifest位于本批ignored `.work/ap5batch-build/`，仅交非敏感文本摘要；不提交APK/编译产物、权重音频、密钥、缓存或代理会话。
