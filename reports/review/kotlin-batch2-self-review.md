# Kotlin 批次2直接自审

任务llm-asr-ap5，基线a23277d，实现者直接自审（非独立review）。范围6类：task四类、AppState、ImeFieldPolicy；累计10/62，余52生产Java。bf516cc59全host/Android编译通过；bb3c4f1e9完整APK验证及最终身份核对通过。

## 行为对照

- TaskKind：同三个枚举名/顺序，保留Java values/valueOf。
- RequestContext：字段名称/描述/public final与mutable相同；原构造器允许null requestId/kind，Kotlin nullable显式保留；inputWav默认null，counts/flags默认0/false。
- TaskCoordinator：同AtomicBoolean CAS owner、CountDownLatch发布、released once；admitted→notify→execute顺序不变。同步executor抛Exception包装RuntimeException、Error不包装，外层同RuntimeException/Error回滚abandoned并finally释放；notify保留VMError/ThreadDeath原样传播与其他Throwable隔离。release仍先running=false再notify后countDown；默认daemon单worker名称不变。null端口IAE在同位置检查。
- RequestRunner：same ctx UUID/finishOnce CAS，busy不调用resource hooks，reject finalization once。execute仍preflight→before→body→success；CancellationException先于Exception，Exception/LinkageError策略失败通知，其他Error不吞；cleanup finally及cleanup失败仲裁同序。
- Lifecycle：`-Xjvm-default=all`生成真实default方法供原Java实现/匿名空实现；不是手写bridge或复制Java策略。SAM使用fun interface，checked exceptions用@Throws维持Java catch。
- AppState：同ResultState facade/AtomicReference/CopyOnWrite weak refs；null文本/status变空字符串，edit只r>0通知；removeListener用`===`身份，不能Kotlin `==`替换原Java `==`；listener Throwable隔离保持，清除失效弱引用次序保持。
- ImeFieldPolicy：位掩码/type/password/IME_NO_PERSONALIZED_LEARNING条件不变，静态方法@JvmStatic/私有ctor保持。

## Nix/构建范围

本批不改变flake。已有Kotlin2.0.21版本override是标准Nix依赖固定：官方归档SHA、相同内容缓存/官方fallback、原nixpkgs解包/wrapper。无编译器补丁、metadata伪造/剥离、哈希绕过、永久proxy或SDK/NDK替换。D8 warnings仍fail closed。

## 验证计划

- 新Java migration回归验证SAM/枚举/nullable fields/IAE端口、真实Lifecycle defaults与空实现、RequestRunner实际流程、AppState相等但非同一listener。
- 既有owner/admission/录音gate/Model/IME/导出/日志/mutants，全源Android编译/JNI/签名包/runtime class_def/输入指纹。
- bf516cc59正式exit0，主机编译10 Kotlin+69 Java含测试源，全源Android10 Kotlin+52 Java编译通过。migration Java互操作43checks/真实JVM default空实现、既有owner/57 runner/22 coordinator/70 admission/IME177和Model/日志/导出/mutants全通过，见.work/kotlin-batch2/{host,android}.log。
- 133输入冻结，bb3c4f1e9正式exit0/APK_READY：host再跑、全源10 Kotlin+52 Java、真实javac-h+NDK/linked JNI、DEX/runtime/资产/许可/签名/权限/组件/report binding通过，d8.txt为空。
- 最终127build/133frozen/current SHA全部一致。DEX class_def额外核对全部10 Kotlin生产类；native provenance/578对象与baseline相同，JNI DSO逐字相同；flake/lock/native源码与HEAD逐字一致。standalone checker通过。
- 最终APK3171318bytes SHA5de230d55bc81b641682c6be38a13c01fe839ff39e4045475e1df02a2b0a8e26。仅报告/文档收尾，不改已验证产品输入；本批无剩余阻塞，待安全index核对/本地提交。
- 不能把编译/host通过当真实Android生命周期或设备麦克风验收；本批不访问设备/私人数据，不push。
