# Phase20 R5/R6 验证记录

## 当前状态
R0–R4旧交付包保留于 `dist/pre-r5-r6-0.6/`，SHA `0b891db946992cb649d33b4ec8bf0347738070e4593c306a0558d7f68460e43c`。
R5/R6实现、完整APK和最终独立复核已完成。最终APK SHA `a03c0876ba0f73ccec6532a0eb91c0416a1edb2488b5e57f19f7ffb58b4947f1`，2470760 bytes，0.6-debug/code6。设备回归仍pending。

## R5 目标契约
- App正文由ResultState持有，正文/revision同一次快照供编辑；revision防止ABA旧编辑覆盖新结果。
- 导出冻结点击时正文；编辑或新推理不改变已选快照。显式清除提高独立clear epoch，撤销尚未真正开始写的旧票据。
- begin与clear、最终write-admission共享ResultState同步域；只入队不算不可逆写入。最终epoch复核/WRITING安装与clear互斥，之后才进入provider open。
- 仅一个导出slot；选择器可撤销释放，已入队工作即使撤销也待真实runnable收尾后释放，open/write/close期间不提前释放。
- TXT导出不占ASR/model owner；本地清理与推理/模型操作仍共用原owner。导出状态不写ASR状态，观察者仅发失效通知并由主线程渲染。
- 销毁页面丢弃未消费回调、撤销queued work；已开始写入不可撤回。旋转不保存正文/URI，不恢复旧票据。
- 原100000字符上限保留，UTF-8、close成功后才报成功，失败可能留下外部空/部分文件，无后台重试。云provider可能自行联网。

## 已有证据
- 基线111输入备份，完整host及SDK35 javac通过，日志 `.work/refactor-phase20-baseline/`。
- 初稿报告 `.pi-subagents/artifacts/outputs/ba365fc7/.work/refactor-phase20-r5/report.md` 为writer自述，**不作为验收通过依据**。
- 父审确认clear只变revision无admit检查、Activity未接foreground、无导出通知、text/revision分读等问题，记录 `.work/refactor-phase20-r5/parent-rejection.md`。
- 新生产回归 `TextExportSafetyTest` 在可编译初稿下明确AssertionError：clear-before-admission仍open provider。日志 `.work/refactor-phase20-r5/red.log`。不是编译错误充当红测试。

## 待完成
- [x] 修复后完整host及Android Java编译：`.work/refactor-phase20-r5/parent-{host,javac}.log`。TextExportTest 91 checks +完整请求码耗尽/open-write-close阻塞 +独立clear红绿；其余现有全回归、编译mutants/checker负例通过。
- [x] Activity生命周期/weak通知/独立状态接线静态负例：`tests/text_export_source_test.py`含6个负例（非设备运行）。
- [x] R6策略父收尾：锁外通知/typed文件结果/codec直接调用，完整host/javac及两项行为红绿通过；增强日志`.work/refactor-phase20-r6/policy-final-{host,javac}.log`，TXT104 checks、真实同AppState+runner任务共存、deferred/duplicate/cap/observer、模型重入取消/跨线程锁获取通过。
- [x] R6策略/迁包最终独立源码复核：双审及5项修复窄审已关闭，reports/review/phase20-*。
- [x] R6包迁移独立host/SDK35 javac/NDK object及完整linked DSO回归。
- [x] fresh独立源码审查、集中修复；最终875e523f无剩余范围内阻塞。
- [x] 完整APK/DEX/JNI/签名/权限/组件，119构建/126冻结SHA、6派生报告绑定；578 MNN对象不变。
- [ ] 用户真实设备验收（本轮不自动访问）：SAF取消/云提供方/阻塞、旋转、clear竞态、多页面、IME/录音/模型/日志回归。

主机fake stream/executor验证次序和清理，不证明真实Android provider、硬件、InputConnection或新JNI运行。没有性能提升测量结论，不修改MNN数学/权重/录音gate。


## 最终证据与明确限制
最终完整日志`.work/refactor-phase20-final/build-final.log`，父再次检查reports/apk/result.json/status.md和所有输入指纹。LogExportTest100、TextExportTest104、compound fatal和model锁外通知回归、15包依赖负例与3个fresh classpath编译mutants通过；单位不同不合计。原陈旧classes gate已修，不使用中间构建冒充最终证据。

功能按task/audio/model/asr/transcription/ime/modelmanagement/diagnostics/platform分包，root保留5个装配/Android入口。迁移归一比较62类保持方法体，3处必要跨包可见性；native只4处类符号prefix修改。最后窄修为相邻日志导出旧fatal缺陷，不修改推理数学。

重复SHA helper暂留：一个读取EOF，一个有cancel/progress/增长限额，异常契约不同；不删最终verify。RuntimeLogWorker自身Error后scheduled恢复仍defer。codec测试包含event-count/UTF8/write/flush，不把“malicious cap”write失败fixture说成1MiB阈值测试。完整Android图/Handler调度/SAF/hardware未运行，不保证250ms墙钟刷新、设备性能或所有SoC兼容。
