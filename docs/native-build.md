# M2 显式 MNN/JNI 原生构建

任务 `llm-asr-76d.3`。实现与最终 strict offline 全量43项实际Gradle任务、checksum负例、直接自审和161输入绑定已通过；提交身份以 Git 为准，**提交失败不交付**。当前产物与证据见 [M2 报告](../reports/native-m2/status.md)。不继承 M1 的旧包验收，4KiB/16KiB 设备运行均待验证。

## 输入与构建分阶段

1. 显式准备锁定归档（来源及原始 SHA 见 [本机复现文档](local-apk-reproduction.md)）。默认受控缓存为 `.work/sources/mnn.tar.gz` 与 `kleidiai-v1.16.0.tar.gz`，只消费归档，不读取旧展开源码或旧对象。脚本不下载。
2. 离线解压与三补丁得到 `.work/native-m2/sources/`；核对 `native/source-lock.json` 的完整公开源码树摘要及补丁 SHA。无关上游 symlink 不复制，`schema/private/`/`source/internal/` 不展开、启用或编译；不接触模型权重。
3. 显式 CMake 目标在当前构建目录编译 MNN OBJECT targets，再编译 JNI 桥并链接一份 `libqwen_asr_jni.so`。不解析 `build.ninja` 或 glob 任意旧 `.o`。
4. AGP `buildNativeArtifact → prepareInputs → preBuild/assembleDebug` 消费当前产物，实际包验收重新核对 provenance/config/ELF/JNI/签名/权限/资产和源码指纹。

```bash
# 仅需要 Python/patch、已校验的两个公开源归档，无模型或设备。
python3 scripts/prepare-native-sources.py --output .work/native-m2/sources
python3 scripts/prepare-native-sources.py --check --output .work/native-m2/sources
# 若缓存置于其他位置，可显式 --cache /path/to/archive-cache。

# 新目录单独复现。源准备与编译是两个命令；配置阶段不获取依赖。
bash scripts/nix-env.sh apk bash scripts/build-native-artifact.sh \
  .work/native-m2/sources .work/native-m2/clean-build

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
