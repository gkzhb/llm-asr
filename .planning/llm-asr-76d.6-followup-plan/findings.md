# Findings

## 恢复基线
- Git main/add5d2c tracked/index clean；父监督新目录外原20excluded保留。M5 in_progress/p1-steward；M3 closed。没有claim/状态写入。
- P1-B 已提交，35+7 是JVM；12仪器方法仅编译/DEX静态，不重做full/prepare/旧审查。
- 实际模块为 core/、android/asr-runtime/、android/app/、android/test-host/；test-host implementation core/runtime，不依赖app。

## 初读事实
- SafModelSource 已位于 runtime可达源码，公开构造器(ContentResolver,Uri)；enumerate走ModelProviderBoundary、cursor close；open走ModelProviderBoundary.open。
- FixtureInstrumentationTest现7共享fixture方法+4 SAF enumerate方法+1真实EditText InputConnection（手动invalidate，非Service）。FakeDocumentsProvider现只query，固定一条synthetic model.bin；无openFile/openAssetFile。
- 因而重复query/close/cancel测试无新价值；若新增应关注生产open→repository/controller真实导入链或导出Android适配/恢复缺口。
- docs/local-verification现android-test-build三包门槛精确12仪器方法和7FixtureContractTest；新增方法必须同步checker及负例/库存，不只加未入执行过滤的测试类。
- 本阶段无测试/Gradle/编译/下载/hash大产物。

## 最终决定
- 只推荐在现FixtureInstrumentationTest加两方法：SAF open拒绝→真实controller FAILED→inspect保留→同controller重试成功；open阻塞取消期间共享owner保留至IO结束。
- ModelReviewFixTest.history/publication与ModelAndroidHelpersTest已经执行恢复/票据/rotation策略；再包fake只是重复。TXT/log真正resolver装配在app AppGraph的private过程，test-host不依赖app；测试里重写lambda不能宣称覆盖Graph。
- 精确最小允许4已有工程文件：FakeDocumentsProvider.java、FixtureInstrumentationTest.java、verify-android-test-apk.py、android_test_apk_test.py；docs只加新历史区分段、新报告另域。14编译方法/7JVM，原fast/root入口/锁metadata/生产/frozen不改。
- 可主机推进的是编译/三包/DEX/checker有效拒绝，而非SAF行为运行。不引入自制host ContentResolver框架冒系统契约；若父要求新增host Android行为即需另行决定范围/实际runtime授权。
- 完整file:line、覆盖/classpath矩阵、断言、允许/禁止文件、未来命令与M5剩余见 .work/m5follow-plan/handoff.md。
- 实时Git最终main/add5d2c，tracked/index空；新增只本leaf三规划（handoff被现ignore忽略）。原unknown20及父新监督保持。没有启动后台进程，无设备/模型/下载动作。
