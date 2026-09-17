# Phase12 解耦重构验收清单

## 范围 / 尚在实施

本增量是0.3行为保持重构，另包含模型残片恢复的窄修复。不是0.4功能版本：不新增导入取消/删除模型、后台服务、驻留引擎、厂商后端或权限。

旧源码/测试备份 `.work/refactor-baseline/`；旧APK `dist/pre-refactor-0.3/`；旧报告 `reports/apk/pre-refactor-0.3/`。均不把旧APK验收归因新源码。

## 结构边界

- Activity只UI、权限/选择器/生命周期事件；长任务不得通过lambda/方法引用持有Activity。
- 应用级操作层仅持ApplicationContext；纯Java任务协调器持唯一owner和每请求上下文。
- 模型仓库仅固定manifest文件，SAF查询/open由薄适配器提供。
- JNI入口/数学/单DSO链接、RecordingControl gate、ExportSession epoch/唯一请求code保持。

## 自动验收

1. 原93项host检查全部保留且通过；新增测试必须调用生产coordinator/repository，不复制一套仅测试用的状态机。
2. 拒绝busy任务不触碰活动文件、不读snapshot、不释放他人owner；executor拒绝提交也恢复状态；请求ID绑定pending/终态/清理。
3. UI观察者替换/旧回调不能影响新任务身份；应用操作不会保留旧Activity。
4. 同一owner覆盖导入/模型校验/WAV/JNI/report/clear/export write；导出picker等待不持owner；clear使旧ticket失效。
5. 模型复制大小/SHA、固定白名单、最终验证和原子发布不放宽。`.part`先受限回收再空间检查，覆盖失败删除、symlink/目录拒绝、短长流/坏SHA/read/write/close/rename失败。
6. 崩溃残片低空间问题有真实失败→通过证据，使用小文件与可注入空间查询，不实际填满磁盘。
7. 既有报告/正文语义保持；本轮不悄悄改变“模型维护清空正文”。
8. 构建输入指纹包括新增源码/tests；Java8 Android编译、DEX、签名、包检查通过，版本仍0.3-debug但以新hash区分重构包。
9. 独立只读审查确认现有保护未被拆坏；必要修复后重测并重新核对源码/构建指纹。

## 设备验收（本轮不自动执行）

- 从旧0.3升级，模型保留；示例/WAV/录音转写与结果操作回归。
- Activity重建、取消录音、provider失败、导出picker与清除交错。
- 导入中杀进程后重试及低空间恢复；不把host fake故障注入当真实存储/SAF证据。
- 不承诺native推理中止、硬件start/释放延迟或SAF阻塞read即时取消。

## 结果

实施、构建、独立复核尚待完成；详细结果完成后追加。

## 当前实现结构（待最终审查/构建归结）

- `MainActivity`：视图、权限、SAF chooser、生命周期；仅调用命名操作，不再提交持Activity的长任务闭包。JNI类名保持不动，调用经静态桥，不提前加载native。
- `AppGraph`：进程单例组装与固定manifest JSON适配；`OperationContext`仅保存ApplicationContext。
- `AsrOperation`：录音/文件/模型/结果的命名用例入口，Android端口适配；不依赖具体Activity实例。
- `TaskCoordinator`：唯一owner、串行执行器、同步admission、短事务、释放后状态通知；无raw AtomicBoolean句柄。
- `RequestRunner` / `RequestContext`：纯Java请求生命周期（清理→pending→body→终态→finally），每请求ID/临时文件/已写终态标记，无永久请求map。
- `AppState`：当前文本/状态/模型验证状态；UI主线程弱目标刷新并读取coordinator busy，不复制busy状态。
- `ModelRepository` / `ModelManifest` / `ModelEntry` / `ModelSource`：纯Java固定文件规则；`SafModelSource`负责有界目录枚举与stream打开。
- `NativeResponse`：纯Java协议/严格UTF-8解析；不改JNI协议或MNN数学。

### 本轮行为边界

- 模型操作清空正文并写最后报告的旧策略保留；取消/删除模型等0.4功能仍未交付。
- 录音control在接受任务时同步发布，worker未开始时onPause也能锁存取消；录音gate本身未修改。
- 模型残片恢复：固定清单普通`.part`先回收再检查空间；不递归、不清外部文件、SHA/最终校验不放宽。源码顺序变异测试复现旧错误，不等于对历史APK实机复现。
- 类/代码总量增加是职责隔离与可测端口的成本，不声称“行数减少=复杂度降低”，也未测圈复杂度/性能提升。

### 当前父会话验证

- 243 host checks：原93 + parser25 + repository33 + part-recovery15 + runner42 + coordinator14 + admission22。
- 5 P0契约检查通过；无模型数值/独立准确率新结论。
- 确認RecordingControl/ForegroundRecorder/PCM/WAV/text helper与重构前SHA一致；权限/版本/native源码未改。
- 初稿与第二稿曾被父审拒绝，当前测试结果仅针对父修后冻结源码；不沿用初稿200/第二稿221作为最终构建证据。

## 最终交付

- 最终构建`b4b1d2d31` exit0，245项host检查通过，Java8/DEX/单DSO/签名/包检查通过；47项构建输入指纹匹配。
- APK：`dist/qwen-asr-minimal-debug.apk`，2,405,160 bytes；SHA-256 `3772e2049106301f7553fdc48d25e6c11ab27f32577491de0347fdecf70152d3`。仍0.3-debug，未声称0.4已实现。
- 独立审查未发现当前正常生产路径发布阻塞，报告`reports/review/phase12-independent.md`；测试证据缺口处置`phase12-disposition.md`。独立执行243项，后续2份测试加强后父执行245项，生产源码不变。
- 原生DSO与重构前APK逐字一致。没有新的模型/性能/设备结论；用户需要做升级、录音、结果操作与生命周期回归。
- 文件数/总代码增加换来明确职责和生产核心可测试性，不把Activity行数下降当整体圈复杂度降低。
