# llm-asr-t0t IME resident UI readiness — 当前状态

任务 owner/唯一 Beads writer `p1-steward`；本批验证 writer `t0t-build`。源码基点 `64d00d16e13577b9e6ad049511acd96b3e7d7d6b`，在已审七候选 dirty freeze 上验证，**未提交、未交付 APK**。

## 已完成与审查

IME render/click 仅在 proof READY + resident LOADED + 非 pending 时允许录音；own-busy ACTIVE 保进度，预览手动确认不接 resident gate，原 worker 最终准入/no-hide 保持。原同 caller 旧源有效红、新普通/-O 三 compiled mutants 红恢复、旧返回两模式、fast35JUnit及离线 graph/format证据已分别保留；R1恢复primary/schema工具缺口已最小修并取得真实故障红绿/当前readiness双模式证据，父接受fresh定点CLOSED/无新增阻塞。fresh同sol上下文隔离，非跨家族；review请求high与runtime xhigh差异如实见父消费记录，不虚构reviewer handoff。

## 本次唯一 full：失败首停

获准命令 `bash scripts/nix-env.sh apk --cache-only bash scripts/verify-gradle.sh --offline --rerun-tasks`，实际外层 **exit1**，Gradle **4 actions executed**，原pipeline Gradle1/tee0/scan1。`:core:compileKotlin` 无法离线解析 `:core:kotlinBuildToolsApiClasspath` 的 `org.jetbrains.kotlin:kotlin-build-tools-impl:2.0.21`，其余“Did not resolve locked dependency”是同配置失败的级联诊断，不自行改锁或放宽strict。

实际已执行 Android测试包纯checker、JNI前置全维护Android源码对SDK35 android.jar混编（含当前Service）及arm64 JNI编译/符号、native增量链接/配置/provenance/ELF16KiB及prepareInputs；native字节仍固定 `4ee0b300caa099a7ef0358fdf42901b9f963d5e2be8fb8e330fb5cad55394481`。**本次 full 未到 host/JUnit/Lint/checksum故障/AGP Service编译/三APK与receipt，不用早前targeted补称full成功。** 详 [本次full](full.md)、[输入身份](full-inputs.json)。

旧三APK先备份并绑定SHA，未作本轮新包。没有第二full、联网prepare或下载补绿；原20精确物理POM例外不授信、不扩大，runtimeClasspath既有锁配置范围边界不冒全覆盖，原strict/metadata/锁全部保留。

后置保护audit exit1，唯一不一致为父拥有的`.planning/llm-asr-msj-next-supervision/progress.md`，前后SHA已报告父确认归属，leaf未恢复/覆盖。七候选、41,505历史输出、旧handoff及备份均核同；不把这次保护audit写成全绿。

## 下一步与 NOT RUN

父消费失败及保护证据后，决定是否另授仅当前已锁/已校验 Kotlin build-tools 配置闭包准备；缺缓存是环境准备 blocker，不是产品mutant red。fresh安全候选审/提交和任何可验证APK交付须等必要full实际通过并另授权。

Android runner、系统IME/fcitx5、Binder/生命周期、设备/麦克风/私人输入、模型计算、PSS/性能、远端CI均 NOT RUN；不连带关闭 M4/M5或长期任务。
