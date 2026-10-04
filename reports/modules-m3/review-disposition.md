# M3 独立审查逐项处置

父监管 m3-steward；基准538dcb66。异家族独立会话 m3-review（newapi/MiniMax-M3/max）只读审查完成，原文保存在 independent-review.md。审查未执行测试；父实际复验见 parent-verification.md。原文中的数量/机制描述不作为未经核验的事实。

| ID | 父核对与处置 | 当前是否阻塞 |
|---|---|---|
| F001 全subprojects空sourcepath | 三个模块目前全部共享原源码根，app也必须避免隐式重编core/runtime。不能按建议移除app保护。javac明确提供的源文件仍可被annotation processor处理，空sourcepath不等于所有APT失效；本轮未配置APT，未证明实际缺陷。未来新独立源码模块/处理器接入时须重新核界，不增加未经验证的框架。保留当前保护。 | 否，未来维护注意事项 |
| F002 InferenceAdapter重复编译/PartRecovery污染 | 报告遗漏core/build.gradle.kts明确 `java.exclude("InferenceAdapterTest.java")`。父复验clean后find实际core/build/classes/java/test没有该class，只有runtime debugUnitTest目录存在。重复编译判断不成立，不按建议重复添加exclude。PartRecovery子javac/java明确独立输出/classes classpath，并非共享父JUnit classloader；现保持原脚本和串行约束，未证明污染。 | 否，不采纳错误前提 |
| F003 hard63源清单 | 明确M3已冻结63源码，错误消息要求review module ownership，文档和自审已说明新增类须显式更新清单/归属。当前是有意fail-closed，不自动放宽；后续任务遵守该更新边界。 | 否，已文档化 |
| F004 checksum sidecar | 原文正文确认未新增风险且无修复建议；父实际隔离metadata篡改按指定checksum拒绝exit1，测试脚本exit0。本脚本无所称sidecar find路径。offline不称hermetic。 | 否，正常gate确认 |
| F005 root workingDir | 原文正文明确正确，是保留旧回归相对路径所需，不是缺陷。保留。 | 否，正常行为确认 |

## 结论

父核对当前实现、异家族只读审查和实际独立复验后，无剩余本轮工程阻塞。未修改被审生产/测试/构建输入，因此174指纹及父复验APK仍有效。保留原review以便追溯，但不把泛化未来假设或错误前提标为已确认P2缺陷。

仍需安全暂存/提交成功后才能交付。Android仪器/设备/麦克风/SAF/IME/JNI及4KiB/16KiB运行均not run；不关闭通用设备或ASR质量任务，不自动实施M4/M5。
