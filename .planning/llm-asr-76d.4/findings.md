# M3 发现与最小方案（依赖调查检查点）

## 已核事实
- Gradle8.7/AGP8.6.1/Kotlin2.0.21/JDK17保持；Nix patched aapt2、单worker、严格dependency SHA与locking保持。
- :app位于android/app；根verifyDebug连接全部旧host、staging/native负例、真实JNI、APK checker。迁模块须扩展Gradle inputs与Python指纹/inventory/DEX owner收集，不能只改sourceSets。
- 实核63生产类：10Kotlin/53Java（M6新增ActivityWindow；前期按路线旧快照写62，已纠正）；旧host显式名单含50类，其中JniNativeTranscription虽无Android依赖但属于native实现；其余纯JVM源码构成闭合依赖集合。org.json仅AppGraph/AppReportWriter，保持平台侧。
- M2 native期望SHA d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416；改变必须停，不改门槛。
- 原host有28个Java main入口，ModelReviewFixTest分8参数路径，production mutants另有3个模型、PartRecovery内嵌红绿、ActivityWindow的3个。JUnit迁移不得代替旧host/source/checker/ABI/native gates。
- InferenceAdapterTest直接构造JNI桥（验证构造不加载库），且读真实App/IME/native源码；应在runtime测试层接JUnit，不能把测试only JNI假类放core或引入core→runtime反依赖。
- PartRecoveryTest在测试内重编真实ModelRepository，原路径和固定.work/red要同步，确保fresh隔离/行为red，不能以编译失败算mutant killed。
- 来源/锁：现缓存无JUnit/Hamcrest；受控公开依赖下载需单独来源/SHA核验并锁全部传递依赖；strict gate不放宽。Maven Central旧JUnit/Hamcrest的.sha256 sidecar均404（两请求exit22）；改用官方仓库.sha1 sidecar核交叉并计算真实SHA256固定，不编造官方SHA256。

## 最小模块图（已实施，并经父独立复验及异家族审查确认）
`:app → :asr-runtime → :core`，并`:app → :core`（app直接使用业务类型）。
- :core：50纯JVM生产类；纯JVM状态机、模型文件边界、日志/导出/IME策略、录音控制/波形/推理协议与注入port。不得依赖Android/org.json/app/runtime。
- :asr-runtime：5生产适配类：JniNativeTranscription、ForegroundRecorder、SafModelSource、OperationContext、ImeBackend；Android library仅依赖core，JNI DSO任务与打包暂留app（避免不必要native输出位置变化）。
- :app：8装配/UI/产品平台类：AppGraph、3Activity、IME service、ActivityWindow、AsrOperation、AppReportWriter。
- 决定先保留现源码物理路径，通过三个模块明确sourceSets includes/excludes建立唯一归属和真实独立编译边界（与M1渐进sourceSets模式一致），避免一轮同时大量迁路径/测试/source指纹。不是Exec包装：core Java-library/Kotlin-JVM真实编译与JUnit，runtime Android library，app真实AGP依赖模块。包/公开类/Manifest/native symbol/全部生产源码字节保持。未来物理搬迁不属于本轮完成必要条件。
- 核心JUnit以直接调用旧main的逐入口/逐ModelReview路径真实@Test适配开始，每入口仍执行全部原latch/断言/清理；保留main独立兼容入口。runtime的InferenceAdapterTest独立@Test，不依赖core测试运行Android。

## 父验收与审查处置
- 父复验bbb64c7ae completed/exit0：clean core/runtime→strict offline rerun70action全执行、35JUnit/全部原gate、checksum目标拒绝通过；174current一致，APK a0494d25...a3f3未变。只读证据reports/modules-m3/parent-verification.md。
- 异家族m3-review审查完成，父逐项处置无本轮阻塞，详见只读independent-review.md与review-disposition.md。F002遗漏core已有InferenceAdapterTest exclude，父实际class确认仅runtime存在，不改源码重复加exclude；F001保留app空sourcepath防隐式重编；F003 hard63为有意fail-closed；其余为正常确认。不把review原文误前提当已确认缺陷。
- 父安全本地功能提交已成功，完整9ec98d3b9e9a2f28af21d86f811639e3a0302ade实核；174current/committedSHA、4external与APK身份一致，父批准工程验收并显式授权关闭M3成功。功能APK可供用户手动验证，但设备/仪器/麦克风/SAF/IME/JNI4K16K仍not run，不等同设备验收。此次仅关闭状态文本，无174输入/产物变化、不stage/commit；关闭文本由父另提交，不关tc3/quality/epic，不领下一任务。

## 恢复资料
已全文恢复memories：gradle-nix-verification、kotlin-d8-compatibility、modern-android-migration-boundaries。offline≠hermetic；全DEX class_def身份，静态16KiB≠设备验收。
初始工作区只有父监管和两个他人分析目录未跟踪，无跟踪diff/index，HEAD符合分派。
