# 依赖变更操作流程

本页命令示例从**项目根**执行。读取Gradle任务/配置以确认实际存在；帮助或诊断也可能解析配置及执行插件代码，按任务授权使用缓存，不把`--offline`当代码安全sandbox。不是自动执行清单。

## 1. 先恢复范围

1. 读AGENTS、显式task/owner、必要planning及Git index/工作树；scope不明先问。
2. 分类JVM/plugin/toolchain/native/Python/model，检查现库可否满足，不先下载。
3. 填下方准入记录并界定允许联网/构建/数据/设备；查维护/CVE及许可须公开可信来源，无法查写unknown。
4. 获取新依赖前审源/构建script/install hook；不运行未经审查上游demo。

## 2. 比较解析结果（inspect）

在已经准备的固定Nix/Gradle环境，常见诊断例子：

```bash
bash scripts/nix-env.sh apk bash -c \
  './gradlew :app:dependencies --configuration debugRuntimeClasspath --offline --dependency-verification strict --no-daemon'
bash scripts/nix-env.sh apk bash -c \
  './gradlew :app:dependencyInsight --dependency kotlin-stdlib --configuration debugRuntimeClasspath --offline --dependency-verification strict --no-daemon'
```

core可用runtimeClasspath，Android runtime用debugRuntimeClasspath，**先核实际configuration**。关注升级前后transitive版本、variant/minSdk/Kotlin、scope、licenses及引入JNI/resource/Manifest；同版本坐标不证明内容没漂移，需checksum。

## 3. 受控prepare/update（明确授权后）

- 使用目标module/task/configuration执行依赖解析和必要缓存准备；不configuration阶段下载native源码、不顺带全栈升级。
- 在准备分支更新固定声明（或批准后version catalog），用Gradle官方lock/verification写入机制生成受影响证据。新artifact尚无checksum可在**独立bootstrap**获取记录，随后来源核验；不能自动将抓到的任何hash直接信任提交。
- 定向`--update-locks`或必要`--write-locks`仅在准备范围；`--write-verification-metadata sha256`也只准备阶段。不要把这些参数交给verify-gradle.sh，它刻意拒绝写/松动证据。
- 新hash与官方签名/sidecar等可用证明交叉核；缺SHA sidecar可实算并注明首次HTTPS信任，不伪造上游独立证明。保留合理multiple checksum须有来源证据，不将冲突当镜像便利。
- 审声明/锁/metadata/仓库diff完整范围；清除不再需要的条目先核所有支持variant/plugin/configuration，不能盲删锁文件或用force冲突。
- Python更新独立pyproject/uv.lock流程；native更新独立source-lock/patch/tree/flags/工具输入。模型更新独立资产清单与许可，不隐式更新参考oracle。

## 4. 验证（不得改证据）

现完整入口：

```bash
bash scripts/nix-env.sh apk bash scripts/verify-gradle.sh --offline --rerun-tasks
bash scripts/nix-env.sh apk bash tests/gradle_dependency_verification_test.sh
```

第一条完成当前verifyDebug host/JUnit/module/JNI/native/AGP/包身份门槛；第二条隔离artifact checksum变异目标拒绝，失败原因必须具体，不把网络未连接当篡改拒绝。命令长时使用背景任务并遵守宿主shell策略；串行/单worker，不并跑大模型。

按影响补充：
- JVM纯测试依赖：目标JUnit/Java互操作/模块负例，确认测试库不进入产品DEX；必要完整构建避免构建配置回归。
- Android运行依赖：全源编译、DEX、Manifest权限/组件/resource差异、APK/签名/资产/包输入检查；UI/生命周期真实测试按授权运行。
- native：全新构建目录受控源准备、真实编译/JNI、所有ELF/依赖runtime/layout及坏配置故障；hash变更解释及质量/设备按gate处理。
- plugin/toolchain：Wrapper/插件checksum、compiler/stdlib/AGP/D8/NDK兼容、clean full build、缓存后offline与checksum负例。
- 模型：大小/SHA/依赖闭包/许可及管理事务、校验证据失效/跨重启、resident/request隔离；质量/设备另批准。

配置锁、verification、offline、host、ELF/包装、native故障和设备是不同层证据。明确未运行项及原因；不为了新依赖关警告/静态门槛或复用旧报告。

## 5. Review与限定提交

- 审直接/传递/plugin/native闭包、license/NOTICE、漏洞例外、权限/网络/日志、资源及构建输入清单。
- 新二进制/源码不能仅因checksum通过就执行；源码来源/hash证明可复现，不保证无恶意。
- 暂存前检查index原内容与归属；限定本轮路径，不stage -A混入用户规划/权重/密钥。工具锁/文本NOTICE和安全报告可提交，下载cache/AAR/JAR/so/模型不因本skill获得提交许可；Wrapper JAR是现已审官方构建输入的特例，不推广到vendor二进制。
- 按AGENTS测试/审查/身份绑定完再本地提交，再报告APK；不push、不amend历史。扫描或提交失败如实blocked。

## 准入/升级报告模板

```text
Task/owner/base:
变更类型、库/模块/scope:
使用理由与替代方案:
原版本 → 新版本（源码commit或artifact坐标）:
官方来源/归档/许可证/NOTICE:
直接与传递依赖差异:
仓库/锁/校验摘要/工具来源:
CVE及例外依据、owner、复查期限:
SDK/JDK/AGP/Kotlin/NDK/ABI/native runtime兼容:
权限/联网/日志/模型或代码信任变化:
APK体积、线程/内存预算与测量边界:
已执行验证及目标负例:
not run（设备/clean缓存/remote CI等）:
源码/构建输入/签名/APK身份:
回退方案与剩余blocker:
安全暂存/本地提交/未push:
```
