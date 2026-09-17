# APK 状态：R5/R6构建与独立复核完成，可供手动设备验收

- APK：`dist/qwen-asr-minimal-debug.apk`
- 版本：`0.6-debug` / code6；application ID及既有Activity/IME组件名保持不变。
- 大小：2,470,760 bytes
- SHA-256：`a03c0876ba0f73ccec6532a0eb91c0416a1edb2488b5e57f19f7ffb58b4947f1`
- 完整构建：exit0 / APK_READY，日志`.work/refactor-phase20-final/build-final.log`。
- 119构建输入与126冻结输入各自全部匹配；全部fixture含JNI摘要已冻结/构建绑定。集合范围不同：冻结另含旧P0/deploy/环境脚本，构建另含模型manifest/flake等资产，不用数量相同作为验收。
- APK/六份派生metadata绑定、精确RECORD_AUDIO权限、API29/35、arm64单DSO、签名及实际JNI导出检查通过；578 MNN对象与本轮基线逐字相同，JNI DSO因包符号迁移改变。
- Host：TXT104、日志导出100、compound fatal/controller锁外通知、原模型/IME/录音/parser等回归通过。3个行为mutant使用本轮fresh编译目录，不再读取旧class。15个架构负例及源码/JNI/report绑定负例通过。计数单位不同不合计。

独立双审原报告已归档`reports/review/phase20-{behavior,package}-review.md`，接受的5项已集中修复并重新构建；fresh窄复核875e523f已关闭PKG-1/2/3、BR-1/BR-T1，未发现新的范围内阻塞；报告`reports/review/phase20-final-fix-review.md`。父再次核对最终APK/119构建/126冻结SHA一致，复核后未改产品输入。

旧APK保留`dist/pre-r5-r6-0.6/`。没有设备安装/SAF/Handler/IME/录音/JNI运行验收，也没有性能提升实测。外部provider可无限阻塞，slot不提前释放；清除不能撤回已开始的外部写入。未commit/stage/push、未访问设备/麦克风/私人数据。
