# M2 原生构建与 APK 验证

任务 `llm-asr-76d.3`，基线 `0446217`。本轮实现、最终验证、直接自审与输入核对完成；本地提交身份以 Git 为准，提交失败不交付。原生构建文档：[native-build.md](../../docs/native-build.md)。

## 产物身份

- 路径：`android/app/build/outputs/apk/debug/app-debug.apk`
- 包名：`org.llmasr.minimal`
- 版本：`0.6-debug` / versionCode 6；minSdk29 / targetSdk35 / arm64-v8a。
- 大小：5,202,124 bytes。
- APK SHA-256：`5e1a1e072937b9509bb326f617daefe18ed40214115b8413a601d341a2fb84c0`。
- JNI DSO SHA-256：`d5bacffc659fb82d862703fc486eda5b34a6184f92a7330dd15eabdb7dcaa416`。
- 签名证书 SHA-256：`e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`；与 M1 相同。
- 当前 APK/native 与 M1 **字节相同**，但本輪实际通过新路径重编与打包，不以旧产物/旧审查替代证据。产品源码/CPU数学/组件/权限保持不变。

## 本轮证据

最终 `bc3339b51` exit0：固定 Nix shell / Wrapper8.7 / AGP8.6.1 / Kotlin2.0.21 / NDK28.2；strict dependency verification + offline + rerun-tasks，43 actionable tasks 全部执行。无 action 的 generateDebugAssets 生命周期节点显示 UP-TO-DATE，未把它计为实际执行测试。

- 两个新 native 构建目录从已校验归档/三补丁的新源码树编译578对象，显式 CMake OBJECT targets/JNI链接不解析 build.ninja、不读取旧对象；现 AGP native artifact 输入明确。
- 全套 host、43项 Java ABI/语义、既有确定性边界与3个生产行为mutants通过。
- 新源码准备9项普通及Python-O、native4项普通及-O、staging8项、实际APK identity5项通过。真实dependency期望checksum篡改被Gradle拒绝exit1，负例通过。
- 全源10Kotlin/52Java、JNI生成头/descriptor/NDK object与实际DSO符号、签名/DEX/资产/权限/组件与报告绑定通过。
- native有效MNN cache、当前对象SHA/源树锁/NDK compiler/CMake4.4.2/Ninja1.13.2工具事实及fingerprint齐全。
- ELF四LOAD alignment16384、RELRO/不可执行栈/W^X与动态依赖精确集合通过；单DSO/static libc++，compressed native + extractNativeLibs=true包装保持。
- 161项最终构建输入、79项native输入、73项产品基线与current/final冻结逐条SHA一致。

必要非敏感报告保存在本目录；完整原始日志仅本地ignored保存，不提交模型、音频、APK、密钥、缓存或代理会话数据。此前 `bbda599af` 实际native编译通过但报告虚构路径FileNotFoundError导致整轮失败，已修并新增存在性回归；不将失败改写为成功。

## 审查与验证边界

本session直接源码自审，非独立Agent审查；接受项/修复与证据见 [.planning自审](../../.planning/llm-asr-76d.3/self-review.md)。没有剩余本轮已知阻塞。源路径变化使574对象完整SHA不同；代表对象差异仅debug/其relocations，最终strip DSO全字节相同，不声称全部对象逐字相同。

**4KiB/16KiB设备运行、普通App JNI推理、麦克风、SAF、IME生命周期未执行（not run）**。静态ELF16KiB对齐不等于设备验收；P0严格frontend/独立golden/性能限制仍存在，不随构建迁移关闭。未push，不自动恢复Kotlin迁移或领取M3。

建议用户验证：同签名覆盖安装、示例/WAV推理、模型管理、日志页及IME基础流程；设备授权与自动化验收需另行确认。当前版本行为与M1同字节，本轮重点是构建可复现与证据门槛，而非新UI或性能改动。
