# Phase20 最终双审处置

## 原报告
- [行为审查](phase20-behavior-review.md)：R5两个旧P2源码关闭；R6通知/typed/codec未发现新增具体阻塞。BR-1/BR-T1为迁包前日志导出的相邻缺陷/测试假阳性。
- [包与工具链审查](phase20-package-review.md)：62 Java迁移等价（3处必要跨包可见性）、JNI仅4处符号prefix、组件名不变；PKG-1测试新旧classes接线阻塞。

## 集中修复（最终构建/窄复核均完成）
| 项 | 处置与证据 |
|---|---|
| PKG-1 high | host runner显式传`MODEL_REVIEW_CLASSES="$classes"`给mutation；Python强制环境变量+真实目录解析，无旧目录fallback。缺参测试fail closed通过。完整构建将使用fresh host与同一fresh mutation基线，旧PASS不冒充新证据。 |
| PKG-2 medium | 冻结从125扩至126，递归纳入全部fixtures含`jni-pre-r3.sha256`；原build119输入已含该文件。区分审查冻结集合与真实构建集合，不比较数量代替包含关系。 |
| PKG-3 low | JNI standalone明确ASR+传递依赖subset，去unused all_sources；完整APK仍全源owner+actual linked DSO检查。 |
| BR-1 P2既有 | 日志导出显式write/close异常仲裁；close非Linkage Error不能被普通primary吞掉，primary fatal身份保留；close结束后finally释放。新增production-controller compound测试先对旧生产运行，明确AssertionError，非编译红。 |
| BR-T1 P2测试 | 唯一fatal实例，catch只包worker调用，外部断言identity；不再自捕测试AssertionError。compound矩阵同时检查close一次、FAILED/slot释放。 |

最后差异共5个源码/测试/脚本文件加freeze manifest；不重写已通过review的R5/模型controller逻辑。完整构建日志`.work/refactor-phase20-final/build-final.log`；fresh窄审workflowce57c38b。

## 保留限制
- UI250ms是前台coalesced轮询，不保证主线程阻塞时墙钟响应；无实际Handler/SAF设备执行。
- 清除撤销点为maintenance worker更改epoch，不是用户点击瞬间；已进入写入不可撤回外部文件。
- 共享AppState+runner tests不等于Android AppGraph/AsrOperation运行；真实设备IME/录音/模型/导出回归pending。
- RuntimeLogWorker本身Error后scheduled恢复仍未处理；codec已测event-count、UTF8、写/flush失败，旧“malicious cap”fixture是write失败，不声称其跨越1MiB字节阈值。
- 重复SHA入口有不同限额/取消/异常契约，未强行合并、未削弱最终verify；无性能提升测量结论。
- 未设备/麦克风/私人数据访问、未commit/stage/push。

最终875e523f窄复核已关闭全部5项，无新范围内阻塞。父b19042db8完整构建exit0、fresh变异/119build/126frozen/实际APK与metadata绑定已重核；最终报告phase20-final-fix-review.md。
