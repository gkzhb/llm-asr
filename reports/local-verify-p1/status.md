# P1-A / B3b 当前状态（2026-10-15）

**工程验证通过待安全提交；未交付可验证 APK，M4 不关闭。**

- Task `llm-asr-76d.5`，父 owner/唯一 Beads writer `p1-steward`；B3b 验证/报告 leaf `p1full-test` 已运行唯一完整 strict/offline/cache-only 入口，bg `b619c639e` durable completed0，428.052s；Gradle111action全部executed，所有原gate保留并实际通过。
- core34/runtime1共35JUnit零失败/错误/跳过；原host/ABI/latch/生产mutants/真实模块编译边界/JNI/native配置ELF/APK checker通过；App/runtime生产Lint均0Error/Fatal，App17/runtime10同源warning明确defer。
- 本次full内新独立Lint fixture实际identity/files目标拒绝及默认AGP0→唯一NewApi1→恢复0；实际checksum0→唯一AGP SHA目标1→恢复0（fresh HTML/cache SHA诊断）通过，不借旧B2证据冒充本次运行。
- currentAPK：`android/app/build/outputs/apk/debug/app-debug.apk`，0.6-debug/code6，5216237 bytes，SHA256 `3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10`；min29/target35/arm64，唯一RECORD_AUDIO，v3单签名证书 `e9365bf6d711776e9ce7f7d994b21a081960b1563717d89987da04688893c16c`。这只是待提交工程产物身份，不是安装/麦克风授权。
- 195build/80native前后/current及实际APK报告精确绑定批准freeze；native d5bacffc…a416不变，578 MNN对象SHA保持，只JNI复编/最终增量链接，非clean native重编。旧166生成文件/旧APK已备份、632历史SHA保持；本leaf只改最终文档/计划，不改被测build域。
- 详 [full报告](full.md)、[输入/产物清单](full-inputs.json)、[35JUnit清单](full-junit.json)、[证据摘要](full-evidence.json) 与 ignored `.work/p1-full/handoff.md` / `final-input-freeze.json`。
- 已接受权限差异review为同模型fresh只读上下文隔离，非跨家族验证，未由本leaf重跑。下一步：父消费交接，reviewagent只读交付证据/安全清单审后另授限定本地提交；本leaf不得stage/commit/push。
- 未做设备4K/16K、麦克风、SAF/IME/JNI运行、权重模型计算、远端CI/provider部署、空缓存/hermetic/全native clean复现。未关闭M4，不push。

---

# 历史：P1-A / B2 检查点（非当前状态）

- 当前 leaf `p1b2-build`，父 owner/唯一 Beads writer `p1-steward`；main/base `7eace17a8ea636db6010f95d2229c28dc2f78467`，未 stage/commit/push。
- **B2 实现及有界回归通过，fresh review 待父安排；不是 M4 完成或 APK 交付。** 工具缓存阻塞已在后续工具准备批解除，以下旧阻塞段仅保留历史。
- 已核默认 AGP 隔离 Lint 0→唯一 NewApi 1→原源恢复0；普通 Gradle task 调同生产 validator/真实 passed Configuration 的 files 缺/多/替换目标拒绝及恢复通过，不称 typed/default AGP 可变性试验。
- 最新主 app6/runtime7 ONLY RESOLVE、exact35 fast 图、rerun23 action/35JUnit 零失败错误跳过、checksum help0/target1/0、Python3.12.14/PATH3.14.7 normal/-O 矩阵均通过。旧412 metadata 字段/hash/origin/config 保持，仅批准23新增，legal-only5 POM 未合并；主锁/pin/production/native/collector不变。
- 193 当前 build inputs 回归前后精确一致；全新分域 B2 freeze/证据及限制见 [B2 报告](b2.md)、`.work/p1-b2/final-handoff.md`。历史 driver 启动 SHA 缺失，不伪称 launch attestation；当前隔离 payload/log/XML 已恢复核对。
- **未做 B3 production Lint/full/JNI/native/APK/package/current APK 输入绑定、fresh review、设备/麦克风/私人文件/模型/远端 CI、提交。** 不提供旧包作为本轮可验证 APK。唯一下一步：父只读 review。

---

# 历史：P1-A 初始代码检查点（当时阻塞，非当前状态）

- task `llm-asr-76d.5`；父owner/唯一Beads writer p1-steward，leaf源码/执行plan writer p1-build。
- main基线 `7eace17a8ea636db6010f95d2229c28dc2f78467`。未stage/commit/push，不关闭任务，不宣称APK交付。
- **仅代码检查点及Python/shell/source gate通过；固定Nix工具闭包缺失，真实Gradle图/35JUnit/Lint/JNI/native/APK/checksum变异均未执行。**

## 实现与范围

`scripts/verify-local.sh fast|full|lint|format|graph-fast` 同源入口；旧 `verify-gradle.sh` 保持full语义，原verifyDebug依赖全部保留，加限定格式/source/入口负例、AGP原生debug Lint和隔离真实checksum负例。参数明确allowlist，拒绝任务/属性/init/排除gate/写锁metadata/strict覆盖；保留offline/rerun/clean。mock shell及静态接线测试不是Gradle acceptance。

fast接core34/runtime1JUnit、实际module编译边界及source架构/隐私/限定文本负例；不直接接app/native/full链，但真实任务图仍待缓存解除后执行。Lint直接接AGP两个debug任务，不baseline/抑制/关error，保留app preBuild受控输入native链，故明确不是无native快速层。

`nix-env.sh apk --cache-only` 实际显式Nix offline、禁止更新/写flake锁、空substituters/builders、max-jobs0，缓存缺失拒绝。格式仅9维护文本，只读UTF8/LF/final newline/trailing whitespace/首部tab/NUL，不扫历史readelf、不机械全仓格式化。工具/依赖/锁/metadata/production/native数学/身份权限无变更。契约见[本地分层验证](../../docs/local-verification.md)。

## 验证/失败证据

| 命令 | exit/事实 | ignored日志 |
|---|---|---|
| 初次 `bash scripts/nix-env.sh apk bash .work/p1-build/inspect.sh` | 1，错误地只内层Gradle offline，外层Nix发生cache.nixos.org copying path；父核PID447595后SIGTERM，恢复核447575/447595均不存在，Gradle未执行 | `.work/p1-build/inspect.log` / `inspect.exit` |
| `bash scripts/nix-env.sh apk --cache-only bash -c "printf CACHE_ONLY_SHELL_READY"` | 1，真实缺patch/alsa/NDK/JDK工具closure，max-jobs0目标拒绝；未补下载 | `.work/p1-build/cache-only.log` / `cache-only.exit` |
| `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks` | 1，外层cache缺失，Gradle未启动，不是Lint/checksum目标负例 | `.work/p1-build/full-cache-only.log` / `full-cache-only.exit` |
| 入口mock/静态接线＋格式负例，普通及-O；实际checker＋bash -n＋git diff --check | 0，参数目标拒绝/strict/full/graph/DEX/exit/cache-only实参保护；格式目标diagnostic/raw证据排除 | `.work/p1-build/frozen-targeted.log` / `frozen-targeted.exit` |
| architecture/window/model/text-export/app-report 五个source checker旧回归 | 0，architecture15及其余目标source变异fixtures通过；lexical非Android运行 | `.work/p1-build/source-regression.log` / `source-regression.exit` |

首次下载违反本轮无下载边界，明确保留事实，不称纯缓存验证、不删除缓存或伪造结果。后续使用真正cache-only拒绝缺失closure。当前与M3已记录flake.nix/lock SHA相同，不为补缓存改toolchain pin。

## 冻结与剩余项

当次179源码/构建文本输入freeze map位于ignored `.work/p1-build/input-freeze.json`，规范JSON maps摘要 `799e3b0b3a3fb05310173461a936630e611f20dc4a61931d236f1ff8c25976c3`，不是Git提交。无本轮APK/report身份，不复制旧M3包证据。

阻塞输入：固定Nix shell closure（日志具体列FixNullPtrCast.patch、alsa-lib1.2.16.1、NDK28.2/JDK17/21等）受控准备授权或合法完整缓存；之后核Lint运行artifact缓存及strict校验/锁，不自动bootstrap。父fresh同模型审查此代码检查点后决定是否接受/修复/准备缓存；解除后先graph-fast证明无native，再fast/Lint/full真实串行复验，并核新APK当次输入和签名。任何Lint生产遗留超scope修复需先报证据/范围。

设备/仪器/麦克风/SAF/IME/JNI/模型计算/4KiB16KiB运行/远端CI全部not run；P1-B/C非本slice。无本轮工程交付，M4保持未验收。
