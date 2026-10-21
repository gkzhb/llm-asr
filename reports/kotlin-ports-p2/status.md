# Kotlin 日志 ports 窄批：targeted 检查点

任务 `llm-asr-ap5`；owner `p1-steward`。基点 `9a2a0239febc936e769820a098e8d588ecfaba5b`，唯一 writer `ap5ports-plan`。本批仅将 `RuntimeLogClock` / `RuntimeLogSink` 等价迁移至 Kotlin；当前为14 Kotlin、49 Java生产实现（共63 owner），不表示全量迁移完成。

## 保持的契约

- Clock：真实 JVM default `advance(JJ)V`，保 exact `UnsupportedOperationException` 与 message；System/Fake 为 public static final，保公开构造器、方法 non-final flags、Fake私有long字段与普通溢出/setter语义。
- Sink：Java SAM、原List泛型描述符及nullable输入；None仍为可构造final嵌套类，忽略null、元素及全部int参数，不改Store/Worker通知、IO或生命周期。
- 编译实际诊断 `NON_FINAL_MEMBER_IN_FINAL_CLASS` 后，仅在8个对应方法局部Suppress以保Java方法flags；未改全局warnings-as-errors、类finality或编译器。泛型签名实测一致，不加JvmSuppressWildcards。
- baseline仅两对应源path/SHA与scope说明；picker仅两后缀、host仅一行新ports负例接线。module/Nix/依赖锁、native/JNI、签名权限、录音gate和导出epoch均不改。

## 当批已执行

- 原Java新增特征：fresh混编12Kt40Java，400checks、javap exit0；迁移同一caller：14Kt38Java、同400checks与公开成员签名/flags一致。caller SHA256：`e86848df6bfac6227db6fd8b0fa12dc754941ce08983ce44bb3eb66ff8faf905`。
- 普通/-O各两生产副本mutants：Clock default no-op / None拒null；每项fresh compile0、精确外部Java AssertionError1、原源码SHA恢复freshgreen0。无编译错误/超时冒行为red。
- 既有cleanup普通/-O各13case；正式完整host exit0，保默认双enum负例、新ports负例、400ABI/日志Store/Worker/Inference/模型和picker等原gate。
- Nix cache-only + Gradle strict/offline fast `--rerun-tasks`：exit0，23 actions全执行，core34+runtime1 JUnit零失败/错误/跳过，实际core/runtime唯一owner与边界通过；format exit0。不是Android runner或APK验证。
- 同cccc1.8.3/knots1.18.0前后211维护源分析；两Java→Kt导致compare exit2/scope UNKNOWN，逐类/方法人工映射。cccc不覆盖Kotlin；knots不解析两Fake ctor，均不填0。测试特征与runner main复杂度增长明确列为fresh审查热点，不修改旧基线消警。

## 当前源码身份与边界

- `RuntimeLogClock.kt` SHA256：`109268a07c22514885ba67ffef8635c7a7933af1807d9903fb17ec7a82426453`。
- `RuntimeLogSink.kt` SHA256：`b24ecb6f21253bc2507b2090875a348c92ed74f467f71fb36e20eefcee3efe63`。
- 非敏感完整命令exit/logSHA、Java-Kotlin javap、输入分域、精确10候选manifest、复杂度映射及保护域审计在 ignored `.work/ap5ports-build/`；新计划 `.planning/llm-asr-ap5-ports-build/`。旧调查/报告/产物不替换为本批通过证据。
- **尚未 fresh review、full、当批APK构建/DEX/JNI/native完整包验证或本地提交，不交付新APK。** 旧APK/receipt只作受保护历史，不对应当前改动验收；后置须父另授权fresh审→full→安全两阶段提交。
- Android runner、设备/ADB/模拟器、麦克风/私人文件、模型推理、下载/prepare、远端CI和push全部not run；主机测试不等于设备验收。
