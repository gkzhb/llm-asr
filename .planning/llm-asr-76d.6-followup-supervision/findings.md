# M5 后续监督发现

## 已恢复权威事实
- 用户要求读取 /tmp/llm-asr-handoff-2026-10-15-p1b-complete.md、Beads 与规划并继续。沿用父协调/leaf执行、开发和审查均 newapi/gpt-6.1-sol、review fresh fork:false、reviewagent 另阶段提交的分工。
- 当前 Git main/add5d2c，parent a73f7f3，tracked/index 空，原20 excluded 保持；.work/p1b-ship-review/commit-receipt.json 记录50限定文本安全提交及209build/80native/current/full/三包绑定成功。旧planning中的待提交是冻结当时状态，不重写、不重复 full。
- M5 llm-asr-76d.6 in_progress/p1-steward；唯一 blocks 前置 M3 closed，epic parent-child 非 blocks。M4仍in_progress（CI平台/workflow未做），ap5 deferred/原owner，tc3 blocked/设备另授权。bd status ready计数0而bd ready返回两质量任务，故继续用显式show而非聚合计数分派。
- Herdr本项目仅父wY:p1 working；另nix-config项目idle，无本项目其他child。一次ps未发现Gradle/Lint相关进程；不依旧交接假设无人。

## 已完成而不重做
- P1-B独立:test-host、SDK InstrumentationTestRunner/JUnit3、fake provider/engine/IME host、7 JVM及12仪器方法静态库存、正式三包checker/统一入口已提交。
- 唯一full174actions、原35+新7 JVM、生产app/runtime Lint、fixture/Lint/checksum目标拒绝恢复、原JNI/native/三包门槛为已接受工程证据；不是42个Android tests，hostLint未运行。
- 产品APK与P1-A字节相同，0.6-debug/code6/SHA3fe42d623e8f9b60e7d34fd63f432de2aa780520395fc8aeeb5bbbc1ca32df10；此轮尚无新产品变更/构建。

## 后续方向与边界
- 没有新设备授权，不连接旧ADB、不启动模拟器/安装/runner或采mic。真实Activity/Service/IME/SAF-Binder-grant/InputConnection与JNI-device全notrun。
- 优先调查无需改生产AppGraph/AsrImeService全局注入的SAF导出/ModelManagement恢复真实生产组件覆盖；若必须新增生产装配策略，先呈现最小方案与影响，不借测试名义暗改架构。
- m5follow-plan 已正式停写，父完整消费handoff/三文件。test-host实际core/runtime可达SafModelSource与controller，产品Activity/AppGraph在app不在其classpath。恢复策略JVM已覆盖、重写导出lambda不能冒Graph测试，因此只选SAF open实际缺口。
- 接受4工程文件最小切片：FakeDocumentsProvider/FixtureInstrumentationTest + 正式APK checker/其测试。新增open失败→inspect保留→同controller重试成功、open阻塞取消保留owner两方法；原7JVM与12仪器旧方法不改语义，新14仅静态编译。
- provider实为in-process ContentProvider，不是registered DocumentsProvider/Binder/grant；只读合成cache文件PFD，不能声称流close故障、系统取消或设备生命周期通过。检查worker Throwable外部断言、有界latch/join、失败finally释放和仅删除自有UUID目录。
- 允许docs仅追加新状态不篡改旧12/full历史；新reports/android-test-m5-saf-open及独立build plan/work。无新维护入口/依赖prepare/root锁metadata/生产图/模块owner baseline变化。先targeted/android-test-build/format→freeze→fresh审，full后置且旧driver新随机输出须明确授权。
