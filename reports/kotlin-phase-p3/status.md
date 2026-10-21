# Kotlin 推理 phase/ports 窄批 targeted 状态

任务 `llm-asr-ap5`，base `809c4a986651592bb7e9d855a78880828178582b`。本批将 `InferencePhase`、`InferencePhaseListener`、`NativeTranscription` 三个 core owner 行为保持迁移至 Kotlin；当前生产 **17 Kotlin / 46 Java，63 top owner**。这不是完整迁移完成，**尚未 fresh 审查、full、安全本地提交或新 APK 交付**。

## 契约与范围

- 保四个 enum 名称/顺序、显式 JNI code 1/2/3/4、`public final int code`；不改为 ordinal/getter-only。
- 保 `InferencePhaseListener` Java SAM、JNI `onPhase(I)V` 和接口上真实 `public static final NONE` field；NONE 对全部 int 无动作。
- 保 `NativeTranscription` 单 abstract 方法、四个可空 String、可空 listener、可空 byte[] 返回及 `throws IOException`。纯 abstract port 不添加实现体凑第三行为 mutant。
- 实际 javap 原公开 ABI 子集的 flags/descriptors/IOException/JVM8 保持；Native 返回及五引用参数六处 Nullable 注解明确核验。新增 enum `getEntries`、Listener `Companion` field/嵌套类与 Kotlin metadata 为获准编译器附属，**不是 class 字节或全部公开成员完全相等**。
- 不改真实 `JniNativeTranscription`、native、`InferenceAdapter`、Logging listener/消费者语义、模块/工具版本/依赖/权限签名/录音 gate/隐私或 Store 通知。

## 当批 targeted 实际证据

全部工程命令外层 Nix cache-only，Kotlin 2.0.21/JVM8；Gradle 固定 strict/offline，无 prepare/download。当前 caller SHA256：
`2d39e8d0b35ffa3f35c2b07a21ff1e78b74c0aa99538d5acab8b18c7def5fc7d`。

| 验证 | 实际结果与边界 |
|---|---|
| 原 Java fresh 特征基线 | 在隔离受维护源码副本恢复三原 Java，生产源/driver/compiler wrapper 精确身份核同；fresh compile 0、javap 0、Java run 0，再冻结同 caller |
| 迁后普通 / Python `-O` | 各自两个生产副本 mutants：Phase code 1→0、NONE 对 MIN_VALUE 抛错；每个 fresh compile 0 → 指定外部 AssertionError 1 → 原源码 SHA 恢复后 fresh green 0；同 caller/driver |
| 正式 host 当前 caller | exit 0，167.543s；725 ABI/行为 checks，原 cleanup 13 case、default enum/ports/新 phase mutants、真实 Adapter 10 组354checks，以及原 owner/privacy/model/picker 等全部 gates |
| fast | exit 0，25.593s；23 actionable tasks 全 executed；真实 core34/runtime1 共35 JUnit，零 failure/error/skipped；真实 module classpath 正例/禁止依赖负例保持，Gradle0/tee0/DEXscan1 |
| format | exit 0，8.811s；1 action executed，现46项维护文本；另对精确新候选做 UTF-8/LF/尾空白检查，不冒称 formal format 覆盖全部新 Kotlin |
| 复杂度 | before/当前after均211维护源码临时Git库存，主index不动；cccc1.8.3/knots1.18.0同二进制/argv/raw身份，两采集exit0；compare2因三Java→Kt范围变化保 FAIL-UNKNOWN，逐函数人工映射；cccc Kotlin盲区/knots enum或lambda零记录不填0，不改旧baseline |

命令入口沿现 `tests/runtime_log_enum_mutant_test.py --phase [--green-only]`、`scripts/test-minimal-apk.sh`、`scripts/verify-local.sh fast --offline --rerun-tasks` / `format --offline`，host增加一行永久接线，原收尾 helpers/shared compiler 逐字保持。

### 测试修正与历史证据

v1 caller 最初重复 `new JniNativeTranscription()`；host 混编通过后静态核发现 core 测试不可依赖 runtime 类。按预授权仅删除这两行冗余断言，未修改生产/模块/gate；真实构造仍由 runtime 原 `InferenceAdapterTest` 覆盖。当前 v2 重新建立原 Java fresh 基线，再普通/-O/host 回归；旧 v1 成功证据保历史、不冒当前。最终 fast 真实核 core 不可见 JNI bridge 的编译负例仍 exit1，而当前 core ABI test 正常编译执行。

新增 Java 特征分小函数；driver 局部模式令 main 复杂度显著增长（cccc C44/Y22→C66/Y32；knots C33/M26→C46/M41），以及 `inferenceSam` 组合断言分支命中审查信号，已列后续 fresh review 重点。行为负例通过不替代审查，不为降低分数改 cleanup 框架。

## 输入、保护与交付边界

当批源码/测试/build pins 与命令、javap、mutant及JUnit原始证据在 ignored `.work/ap5phase-build/`，入口 `verification.md`、`candidate-manifest.json`、`current-inputs.json`、`closure.json`、`evidence-manifest.json`。精确11路径候选含三删除的 base blob 身份；三 rename 对 native provenance 输入库存的影响如实分列，**不宣称对 base 全部 native inputs 字节相同**。

原 APK、正式 receipts、签名、native 对象/DSO、旧调查和冻结证据受保护；targeted 没有重建/覆盖 APK，旧 ports 批 APK 不是本批产物。公开摘要不复制海量 raw，不提交 APK/缓存/模型/音频/密钥或私文。

**未运行** fresh review/full、当前 APK 构建与包签名验收、JNI/NDK重编、Lint真实生产任务、隔离 fixture 五阶段、Android runner发现/执行、设备/ADB/模拟器/麦克风/私人文件、真实SAF/Binder/grant/IME/JNI及4K/16K运行、模型计算、远端CI/release/R8；未 stage/commit/push。host JVM/fakes/源码JNI检查不替代这些层级。

唯一下一步：父协调消费当前 targeted 封口，另授同模型 `fork:false` 上下文隔离 fresh 审查（非跨家族）；审查接受后才另授 full 与两阶段安全本地提交，成功前不宣称本批 APK 可验证交付，不关闭 ap5/M4/M5。
