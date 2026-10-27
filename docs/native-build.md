# M2 显式 MNN/JNI 原生构建

## B request cleanup 正式源码接线（未完成native身份交付）

本轮按官方正常复用合同，将`patches/mnn-asr-request-cleanup.patch`由旧10成员简化为Llm/Omni/Tokenizer精确6成员；撤销新增Module/StaticModule四成员API及descriptor/Command/geometry强清，恢复原三数学patch后字节。新patch SHA `059521069394f08ac0a89bdd2d82aa605dd39fc537a0cc6c1b5de1d0450c8392`，MNN tree `cdf16da6e5bd5c66095ac9796d385e58267c3dc6b37f5548f37dc15c68069626`；锁由旧完整库存替换已解释6成员/恢复4成员推导，再由fresh四patch原子prepare独立核实，不盲接受扫描结果。revision/原归档/三数学patch不变。`prepare-native-sources.py`精确核lock.patches，缺/多/错映射拒绝，原archive/tree/symlink门槛保留。新稳定根`.work/native-session-reuse/sources`供app/root JNI共同消费；旧native-m2/native-resident-b及其build/证据冻结。StaticModule/CPUAttention observer friend仅在新host测试副本，不纳生产锁。

### 官方API复用与生命周期决策

锁定revision `a03b005cf6f888ebf092e4753840f935827f9c36`的[session.md](https://github.com/alibaba/MNN/blob/a03b005cf6f888ebf092e4753840f935827f9c36/docs/inference/session.md#L49)第49行明确建议Session只创建一次、多次使用；文档SHA `085bab95c4c53030fa1fcddbbb5f1c41b15749c16faf896a72b498f602c14854`。同文第285行共享Runtime须映射/拷贝填充，不能缓存host指针跨resize；第493–518行要求Session变shape后正常resize、CPU运行同步。锁定[module.md](https://github.com/alibaba/MNN/blob/a03b005cf6f888ebf092e4753840f935827f9c36/docs/inference/module.md#L5) SHA `6a59ee69b9331887b682bf58a281df67337e75b165242da4cbf994c8d5150ab3`第5/8/24/155–157/266行：Module自动resize，Executor单线程且创建/使用/销毁绑定scope，shapeMutable=true直接借当前输入，clone共享不变权重/常量。它不保证Llm自动清除请求语义。

工程合同是输入活到同步onForward和输出读取完成、下一次执行重新绑定当前输入、请求生成状态不串用；不是idle所有私有指针为空/物理擦除/PSS下降。真实锁定StaticModule::_resize逐input刷新refTensorContent，地址变化触发正常needMalloc，shape/control内容变化触发正常resize；NetModule每forward原有clearCache不改。Llm::forwardRaw参数/inputs保持到输出readMap及async=false wait，Omni::audioProcess局部features保持到encoder返回及embedding物化，JNI正常状态后先完成`raw.str()`文本复制，再清请求语义，最后复制至JNI byte array；任一步失败仍默认淘汰。SDK证据是一个arm64 JNI对象及四个native方法的descriptor/export ABI，不是四种CPU架构。默认不增加last-input-owner/历史音频保活，若实际重绑定缺口成立仍必须阻塞处置。新合成图数值/对象寿命证据与完整身份见当批报告，不冒实际ASR。

JNI现四方法：double prepareNative(config,cache,listener)、void releaseNative()及原两byte[]。单mutex默认evict覆盖异常/非正常/cleanupfalse/UTF/JNIallocation-copy-pending，raw寿命长于guard；fresh成功load空态单独确认，不在RUNNING上伪造NORMAL_FINISHED cleanup成功。请求只用已prepare实例，auto显式空语言，逻辑KV previous保留/remove=previous，下次Attention清旧，不声称idlefree/物理擦除。

父已接受新生产native候选身份：`4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`、5,406,248 bytes，并精确授权rebase（仅native.sha与B范围说明）。真实578当前对象+JNI单DSO构建与逐gate独立audit完成；旧expected A下原strict verifier实际exit1 `Native identity mismatch`保留历史，不能改称当时通过。当前新expected下原`verify-native-artifact.py`已实际exit0，完整身份/config/578/JNI/ELF门槛通过，当前artifact新增verification.json；新DSO原native_artifact_test普通/`-O`各4测试通过，详[身份报告](../reports/model-residency-zcm/native-identity.md)。不重编SDK/578、不改原verifier或跳等式。旧M2/A历史身份在下文保留原时点，不全局替换。实际targeted四ABI SDK/NDKobject和host控制流不等Android链接/ASR。`tests/native_request_cleanup_test.sh`是正式真实定义入口：显式`--build`可在全新目录构建CPU MNN/Express/Llm/Omni/audio与三个维护probe；`--prebuilt`只消费绑定当前源码/headers/libs的已审build，不隐式构建/下载。运行Add/Reshape/MatMul/Attention和Session map/unmap、真实LlmOmni字段+Module组合、真实BPE/cache，owned-session/每case60秒及完整流receipt。八生产漏清变异与旧输入内容数值负控另在当批证据，后者只证明测试sensitivity，不声称发现上游算法bug。旧U1的全私有状态枚举/必须读取实际固定图要求由用户明确授权的API有效寿命/next-rebind合同取代，旧报告保留历史；任何具体可达失效借用或KV错误仍是blocker。正式full/新APK/实际ASR设备/ASan/性能/提交仍NOTRUN，当前状态见[模型驻留](../reports/model-residency-zcm/status.md)。

任务 `llm-asr-76d.3`。实现与最终 strict offline 全量43项实际Gradle任务、checksum负例、直接自审和161输入绑定已通过；提交身份以 Git 为准，**提交失败不交付**。当前产物与证据见 [M2 报告](../reports/native-m2/status.md)。不继承 M1 的旧包验收，4KiB/16KiB 设备运行均待验证。

## M3 模块接线说明

M3仅将Java JNI桥/Android适配归属`:asr-runtime`、纯JVM业务归属`:core`；显式MNN/JNI artifact任务/当前native目录仍由`:app`持有。生产源码物理路径、包名、JNI头/符号/native数学flags与SHA门槛不改。当前全源为10Kotlin+53Java（M6新增ActivityWindow）；包检查汇集三个模块实际class并核全DEX唯一owner，不沿用app单目录假设。见[完整测试映射](module-test-map.md)和[M3实施报告](../reports/modules-m3/status.md)。M3的设备运行仍not run，旧用户M2手工验证不自动覆盖新包。

## 输入与构建分阶段

1. 显式准备锁定归档（来源及原始 SHA 见 [本机复现文档](local-apk-reproduction.md)）。默认受控缓存为 `.work/sources/mnn.tar.gz` 与 `kleidiai-v1.16.0.tar.gz`，只消费归档，不读取旧展开源码或旧对象。脚本不下载。
2. 离线解压与三补丁得到 `.work/native-m2/sources/`；核对 `native/source-lock.json` 的完整公开源码树摘要及补丁 SHA。无关上游 symlink 不复制，`schema/private/`/`source/internal/` 不展开、启用或编译；不接触模型权重。
3. 显式 CMake 目标在当前构建目录编译 MNN OBJECT targets，再编译 JNI 桥并链接一份 `libqwen_asr_jni.so`。不解析 `build.ninja` 或 glob 任意旧 `.o`。
4. AGP `buildNativeArtifact → prepareInputs → preBuild/assembleDebug` 消费当前产物，实际包验收重新核对 provenance/config/ELF/JNI/签名/权限/资产和源码指纹。

```bash
# 仅需要 Python/patch、已校验的两个公开源归档，无模型或设备。
python3 scripts/prepare-native-sources.py --output .work/native-session-reuse/sources
python3 scripts/prepare-native-sources.py --check --output .work/native-session-reuse/sources
# 若缓存置于其他位置，可显式 --cache /path/to/archive-cache。

# 新目录单独复现。源准备与编译是两个命令；配置阶段不获取依赖。
bash scripts/nix-env.sh apk bash scripts/build-native-artifact.sh \
  .work/native-session-reuse/sources .work/native-session-reuse/clean-build

# AGP android/app/build/native-debug 为本轮实际生产 native 目录。
bash scripts/nix-env.sh apk bash scripts/verify-gradle.sh --offline --rerun-tasks
```

已有 prepared tree 仅复验，不覆盖；漂移必须停止排查，不能自动重解压覆盖未知改动。输出不允许 `..`/symlink/special/重复归档路径；tree 摘要对照已提交锁，不能只修改本地 `sources.json` 伪造通过。使用本地 `flock` 序列化同路径准备/构建，防止合作进程并写；不宣称抵抗拥有目录任意写权限的恶意宿主进程或断电持久化事务。

## 显式目标与身份保持

`native/cmake/mnn-artifact.cmake` 通过 `CMAKE_PROJECT_MNN_INCLUDE` 注入固定上游顶层工程，deferred finalizer 读取上游公开的 `MNN_OBJECTS_TO_LINK`（CMake `TARGET_OBJECTS`）并生成显式列表。`qwen_asr_artifact` 依赖这批真实对象、JNI 生成头与链接脚本；原 MNN shared 编译配置不改为 static（避免额外 `MNN_BUILD_STATIC_LIBS` defines），但 **不打包 MNN DSO**。

沿用原链接顺序与参数：JNI 首位、577 上游对象及 dummy 末位；578 MNN 对象均限于当前 build。单 DSO 与一个 static libc++，原 16KiB max-page-size/无未定义符号/GC/strip 保持。

锁定 MNN `a03b005cf6f888ebf092e4753840f935827f9c36`、KleidiAI1.16.0、NDK28.2.13676358、arm64/API29/Release；CPU/ARM82/SME2/KleidiAI 配置维持。`MNN_BUILD_LLM_OMNI` 会强制 OPENCV/audio/imgcodecs ON，报告核有效 cache 而非只记命令行 OFF。KleidiAI 必须准备，不接受上游下载失败后的静默降级。

原期望 JNI SHA 保留为：

```text
d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416
```

首轮全新目录及第二个 AGP native-debug 目录实际构建均匹配该 SHA；完整 strict offline 验证43个action全部执行通过，当前APK也与M1字节一致；仍不等于设备已验收。新位置对象完整 SHA 可能因源路径等非运行元数据不同，不声称旧对象逐字相同；最终 stripped DSO 在首轮逐字一致。若最终字节变动，必须保留差异并走解释/质量/设备 gate，不能更新期望哈希凑通过。

## 验证层次与回退

- 生产源码准备负例：坏归档摘要、archive traversal/duplicate/special、path symlink/`..`、prepared tree drift及提交锁不一致；普通及优化 Python 均运行。
- 原生边界负例：对象计数/重复/越界/symlink、真实 ELF LOAD 对齐/stack/RELRO 变异和报告输入清单存在性；prepared报告改写也必须被提交tree lock拒绝。
- 真实 native gate：生成 JNI header/descriptor/导出符号、当前对象 SHA、MNN有效配置、NDK compiler SHA、ELF arm64/LOAD16KiB/RELRO/无可执行栈及单库动态依赖。
- 全量 host/Java-Kotlin/AGP/签名/组件/权限/资产/DEX和报告绑定继承原验证门槛，但输出为本轮独立包及报告。
- 4KiB/16KiB 普通 App JNI运行、SAF/IME/麦克风和具体设备行为没有本轮授权，均 `not run`。静态对齐不保证系统/插件依赖/运行成功。

旧 [M1](gradle-build.md) 已校验 DSO 的过渡设计、旧手工 `link-apk-native.py` 及历史 provenance 保留作回退/复现历史，不作为当前 AGP 发布输入。旧报告不覆盖，新报告在 `android/app/build/verification/debug/run-*/`，native 自身在 `android/app/build/native-debug/artifact/`。模型/音频/源缓存/密钥/APK/编译对象不提交，不 push 或重写历史。
