# M1 标准Android构建 — llm-asr-76d.2

## Goal / Binding
在main/449f01a（产品2fd7511）上让Gradle Wrapper+AGP接管混合编译/资源/DEX/签名打包，保留业务与native身份，验证旧回归/负例/JNI/产物绑定和依赖完整性；安全本地提交后交付。task llm-asr-76d.2，role project-agent，owner/assignee/唯一产品及Beads/plan writer llm-asr-m0，session01a0f34c-beea-7015-b76f-af1b670e3a91，Herdr wJ:p1；cwd /home/zhb/gitrep/llm-asr；plan固定本目录。用户明确暂停ap5（deferred保留llm-asr-kotlin）并授权优先基建开始；M0已真实关闭。

## Scope
只做M1：根Gradle/Kotlin DSL、sourceSets接现目录、固定Wrapper/AGP/Kotlin/JDK、生成assets/显式SHA native过渡输入、host/JNI及post-package验证、依赖校验/可锁配置/缓存与offline证据、文档。不是Exec包装旧整包脚本。不做M2干净native、模块拆分/JUnit/UI/CI/Compose/协程/语言迁移。产品源/min29/target35/包名/组件/权限/数学/签名关系不变。保留两个他人分析目录，不提交。设备/麦克风/私人数据、push/重写历史、委派均未授权。

## Current Phase
Phase3：最终b7658194f full strict/offline/仓库checksum负例通过，152输入/73产品/native/签名/报告绑定核对完；安全暂存和本地提交收尾。

### Phase 1: 工具与基线
- [x] 新用户决策、ap5暂停、M0关闭与M1claim/readback。
- [x] 官方AGP8.6：API35/Gradle8.7/JDK17；候选8.6.1/8.7/Kotlin2.0.21。
- [x] Wrapper distribution及JAR官方SHA/Gradle真实启动，旧APK/报告备份与产品冻结。
- **Status:** complete

### Phase 2: 标准构建实现
- [x] AGP编译/资源/DEX/签名，显式assets/native输入与任务边。
- [x] 旧host/JNI验证及独立输出的post-package身份检查。
- [x] 固定仓库、直接/传递依赖验证与可锁配置，禁止动态版本。
- **Status:** complete

### Phase 3: 验证与交付
- [x] 当前host/负例/全源Java-Kotlin/JNI/签名包/native指纹通过，最终152输入全匹配。
- [x] 准备缓存与strict offline rerun分别实测；checksum mutation被真实Gradle拒绝，不声称hermetic。
- [ ] 自审/必要修复/文档，当前源/产物/证据一致、安全本地提交。
- [ ] 本轮M1验收后才关闭，设备pending；M2另轮实施。
- **Status:** in_progress

## Next Step
显式暂存本轮本人源码/脚本/锁/证据/计划与Beads，审计index安全及152输入一致，再本地提交；成功后报告M1交付，设备pending，不自动推进M2或ap5。

## Resource policy
Gradle最多1 worker、JVM约1536MiB、Kotlin in-process，不同时native大编译/模型推理；现native仅校验与JNI object验证，不重编MNN。标准Wrapper归档SHA544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d（官方sidecar已核）。工具候选仍需实际编译，不因兼容表或exit0宣称完成。

## Errors Encountered
| Error | Attempt | Resolution |
|---|---|---|
| 官方services.gradle.org重定向后连接超时exit28 | 1 | 改查downloads.gradle.org官方直连，保持SHA门槛 |
| Gradle locking与failOnDynamicVersions互斥 | 1 | 显式selector拒绝动态版本，同时保留dependency locking |
| 旧包checker假设单classes.dex，AGP debug实际9 DEX | 1 | 遍历全部class_def并拒绝重复，新增缺定义/重复负例通过 |

## Blocked stop condition
owner冲突/共享scope并写、工具兼容无法证实、无法保持native/签名/安全边界、需要未授权设备/私有数据，停止登记blocker/证据/下一输入；不降低门槛或用旧APK完成本轮。
