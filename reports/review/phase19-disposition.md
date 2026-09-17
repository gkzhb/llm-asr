# Phase19 独立审查处置

原报告：[核心](phase19-core-review.md)、[Android/JNI](phase19-android-jni-review.md)。两路只读审查，未执行测试；父执行全部构建/最终验证。不修改原报告结论。

| 发现 | 处置 | 证据与限制 |
|---|---|---|
| P2 TaskCoordinatorTest listener内关键断言会被吞（测试验收阻塞） | 修复：listener只保存finalized观察值，queue drain后测试线程断言 | `.work/refactor-phase19-final/assertion-negative`：同测试副本将观察值强制0，javac通过、运行明确AssertionError退出1；真实测试全host通过。此负例证明断言非静默吞掉，不冒称生产交接bug复现 |
| LOW 全构建仅查Java descriptor/DSO名，未直接Java派生头编译 | 修复：build-minimal-apk在实际链接前运行compile-jni-object.sh，javac-h生成头强制包含到同一JNI源码编译 | 最终完整构建需同时通过生成头/ARM64 object、真实链接DSO导出及包检查；不是设备JNI运行验证 |
| P2 正常publisher释放瞬间交接缺定向测试 | 明确defer：当前实现经独立静态检查正确，已有有界并发/一次重入/idle重启，不能冒称覆盖特定finally/新publisher交错 | 后续独立测试增强，不为测试注入生产调度hook |
| P3 RuntimeLogWorker自身Error可能保留scheduled | 明确defer：基线已有，此次只修RuntimeLogStore后续append恢复；不声称整条持久化链Error容错 | Error中断当轮后续sink，无下次append不保证重发；正常runtime异常策略保留 |

其他范围保持：R5 TXT独立导出、R6全包迁移/文案类型化/controller锁外通知未实施。旧TaskKind标签和无参数SHA重复入口暂保留。没有性能收益实测、设备/私人数据访问或commit/push。

最后两文件变更（测试/构建）已由[最终窄复核](phase19-final-fix-review.md)关闭；产品Java/C++保持原112输入双审版本。最终构建身份另见reports/apk/result.json与docs/android-refactor-validation.md。
