# 0.5 模型管理：可供用户手动设备验收

- APK：`dist/qwen-asr-minimal-debug.apk`，`0.5-debug` / code5，2,442,088 bytes。
- SHA256：`6e4fbf7a4cf07262912019dc667258123a32f95931b13bb1c046cf6e77471073`。
- 完整构建`b0453981b` exit0/APK_READY，日志`.pi/tasks/session-1194494-1194494/b0453981b.output`。
- 945项数值断言（16组）+8组review回归+provider边界+3个可编译行为mutants+source-policy+实际checker绑定fixtures，及资源/Java/DEX/native/签名/权限/Activity与IME组件检查通过。异类单位不相加冒称总数。
- 78构建输入与69最终冻结输入一致；当前APK与六份派生报告SHA绑定通过；native DSO与旧0.4逐字一致，仅RECORD_AUDIO权限。
- **独立首轮/修复双复核/末次三文件窄复核完成，所有接受的修复问题及E1/E2关闭，无剩余源码阻塞。** 处置及完整报告位于`reports/review/model-management-disposition.md`及其引用文件。审查者只做静态阅读/哈希，测试和完整构建由父会话执行。
- 新功能：独立模型管理页面（主页/IME入口）、共享就绪状态、文件/空间信息、分阶段进度、协作取消、完整文件复用、确认受限删除及失败恢复；模型维护不清App转写结果。
- 旧0.4保留`dist/pre-model-management-0.4/`，审查基线0.5保留`dist/model-management-review-baseline/`。最后复核后未再改生产源码/测试/构建输入。
- **尚未实测真实设备、SAF、Activity/IME生命周期或UI绘制/TalkBack。** 可供手动测试，不是设备全验收或正式商店发布包。
- 取消可等待阻塞provider IO，无后台服务保证。保留小manifest同步bootstrap、泛化外部owner提示、空目录删除失败未单列、规划前复用总量未累计及失败inspect后未知文件数可能缓存等限制；完整边界见`docs/model-management-validation.md`。
- 无commit/push/设备/麦克风/私人数据访问。使用及手动清单：`docs/model-management.md`。
