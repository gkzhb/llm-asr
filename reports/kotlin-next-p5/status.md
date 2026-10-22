# Kotlin next P5 / fresh R1–R3 修复 targeted 封口，待定点回审

任务 llm-asr-ap5 / 原唯一 writer ap5next-plan；base364c85c。六类1082原Java行迁为Kt，库存43Kt/20Java/63owner。**原 fresh 审查 REQUEST CHANGES 的 R1–R3 已完成本轮最小修复及 targeted 验证封口，待父消费和定点回审；尚未判定 CLOSED，未 full/提交，不交本轮 APK**。

## R1–R3 本轮修复与验证
- R1：原 Java 同冻结新 caller 的 Controller/Graph parser/Operation sample及wave四路径 oracle0；受审 Kotlin compile0 后四路径各由精确 self-suppression 外部断言拒绝。仅三 Kt 改为局部 Java member/TWR 语义；真实 SDK/Werror0、字节码5处 `invokevirtual Throwable.addSuppressed`、无 Kotlin selfguard/closeFinally。九个 private static final lambda 编号变化由父精确接受，完整其他成员形状保持、Java正例及9访问负例通过；无新helper/owner/bridge。原 inventory exit1作为历史保留，不改绿。
- R2：两个维护 guard 恢复 Java/Kotlin 通用构造 count==1、caller禁自建，保原五装配/报告/JNI约束；missing、异参数duplicate及两语言caller坏fixture实际调用同checker，非字符串自检。
- R3：维护 fixture/driver 增独立 `--compat-only`，正式 host 单新增 timeout2700 路由；原default五mutants/3900及45/90/180门槛不变。四个真实生产receiver nullable转发、sentinel/order、queuedowner与Windowwrongtype由普通/-O执行；每点可编译提前check坏副本分别由精确外断言1拒绝、SHA恢复后freshgreen0。包含R1 self/distinct/close-only/success-close-once。NonNull注解识别另有定向compile补证，不冒behaviorred。
- 一次完整host0（原全部gate+原5mutants+新compat实际执行），正式fast --rerun-tasks0/23actions/35JUnit0failure-error-skip、format0。备份不以数量代安全；本代34948原bytes+23个会覆盖generated定向补存核身份，20项预期generated变化有当代原件，旧review/build/plan/freeze保持；完整最终清单见ignored新修复域。
- 同路径delta复杂度collect0/compare0 `review-required-not-a-gate`，新Graph/Operation显式资源收尾及guard/fixture增长人工解释交定点审；旧跨语言compare2/unknown和已接受Controllerterminal/finish事务结论保历史。当前源码＝已测SDK draft；formal currentroot实际主classes/ABI仍后阶段full需再绑。
- 新证据 `.work/ap5next-r1-fix/`，新执行计划 `.planning/llm-asr-ap5-next-r1-fix/`。旧错误oracle、有效oracle/redv3、SDK成功但inventory差异stop及精确父准入分代保存。mutant源SHA为精确driver/baseline/class支持的事后确定性重建，不冒启动前permutant采样。

## 原 targeted 已执行事实（历史版本，不冒本轮全同）
- 五冻结caller/harness不变，原Java baseline通过；nullable真实生产探针、actualSDK/Werror、五新增生产mutants普通/-O compile0→精确behaviorred1→恢复0。
- 完整host-v3真实exit0：原全部门槛、新五mutants、Logs123checks/3mutants、Window82checks/7mutants、共享owned故障/收尾及mixed三模式等通过。host/fake不是Android运行验收。
- actualAGP隔离moduleprobe-v4 compiler31actions/exact8app5runtime50core通过；明确禁且仅禁native/prepare动作，**不是正式native/prepare/full**。actualGradle原六外层公开/protected子集delta0、三private erased helper与唯一Graph synthetic bridge精确flags/descriptor、Java正常编译+9访问负编译通过。实际owner8反例与core/runtimeclasspath正负编译通过。
- root追加精确canonical单hostfixture排除后，正式Nixcacheonly strict/offline fast-v2 --rerun-tasks exit0，无init/-x：core34/runtime1 JUnit全部0failure/error/skip，真实wrapper与module边界正负实际执行，core/runtime实际输出无NextAppContractTest及nested。原完整host对同冻结harness显式fake平台编译运行成功；不重复host。graph-fast0仅35taskdryrun；format0。
- root过滤后SHA8320ba75211771ee5ac630190a8ce6fe0e19f43f5e21139964927d44a083a417；此前moduleprobe/ABI执行于过滤前root代，production predicate未变，但**未称新root已执行该probe**，正式full时需currentroot实际主树class/ABI绑定。

## 明确附属例外与 unknown
- StateImpl 原构造 private0x2→普通public0x1、Java synthetic marker形状消失及KtMetadata差异由父新增第五privateowner精确准入接受，未加factory/bridge。新ignored supplemental访问补证：原Java与actualGradlev4各normal consumer0，同包/外包真实outer命名构造均具体private-access1；private nonstatic InnerClasses、this$0身份及四public非final方法符合准入。旧StateImpl阻塞据此关闭，反射差异保留，不称完整ABI相同/不推广其他可达package owner。
- after复杂度双采集0，cccc1682/knots1490，正式compare2 FAIL-UNKNOWN(scope变更)保真。六Java→Kt、新driver/harness及root/app零函数DSL需人工映射，不填0/退化0。Controller.terminal knots37/33/5→178/32/10为最高人工热点；原事务/monitor/日志顺序及行为证据已定位，非freshreview通过。详细ignored complexity-disposition.md及scope-reconciliation.json。
- 原两module/两host/fast失败均保留，不把编译失败冒行为red。Window/Logs仅同类型import短名避免fake字段遮蔽；fixture冻结不改。新hostfixture误收入runtime18error由单canonical排除修正，不排目录/不改core/runtime依赖或测试。

证据域ignored .work/ap5next-build/，旧调查.work/ap5next-plan冻结不改。30候选及current build/module/native输入分域、旧799生成备份、456原artifact、五冻结caller与原调查保护记录均保留。父监督计划/授权变更单列excluded supplemental，不刷新旧freeze。本批仅父有Beads/提交权；本轮工程验证通过不代表 review CLOSED；父消费新封口后另授原 reviewer 在新独占目录定点回审。最高 terminal/finish 已由原 fresh 审逐事务接受，本轮仅 run/资源异常语义及新维护门槛增量回审；旧 compare unknown 不被抹去。

设备、Android runner执行、mic/SAF/Binder/grant/系统IME/JNI4K16K、模型推理、私人文件/下载/远端CI均not run。未full/覆盖APK/stage/commit/push。上批多代ledger缺失不由本轮补造。
