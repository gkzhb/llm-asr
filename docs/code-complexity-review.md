# 当前代码复杂度审计与后续开发建议

## 结论与范围

建议下一增量为 **先做小范围任务/模型管理重构，再完成0.4模型管理**；暂不直接叠加IME、AIDL、长音频或厂商加速。不为减少行数重写已验证MNN数学，也不一次引入Service、数据库、框架迁移。

本轮为源码审计、规划和host验证，没有修改产品源码、重新构建APK、访问设备或提交代码。这里的“复杂度”是职责耦合和跨线程状态复杂度；未运行圈复杂度分析器，不虚报数值。

## 实际版本

- 已提交检查点：`a4c6a65`（0.2），用户曾反馈测试正常，缺设备逐项日志。
- 当前源码/manifest为0.3；0.3构建及聚焦复核已有证据，尚无记录中的用户设备验收。
- 本轮核对 `reports/apk/build-input-sha256.json`：26项文件SHA全部匹配当前工作区。
- 0.4仅已规划：没有导入取消、字节进度、模型删除入口。已有逐文件状态文字、固定清单大小/SHA、`.part`替换及完整已校验文件复用；不要把这些已有能力归为0.4新实现。

## 优先级与代码依据

### 1. 高优先：将任务上下文从Activity剥离

位置：`android/app/src/org/llmasr/minimal/MainActivity.java:19-38,140-160,186-216`。

`RUNNING`、worker、recording和显示文本属于全进程，但requestId、inferenceReported、cleanedTemporary及执行闭包属于Activity实例。当前CAS确实提供互斥，不是已证实双任务并发；问题是下一版增加取消/删除/生命周期后，维护者需要理解多个字段和线程的隐含关系。worker执行期间还会保留旧Activity实例，弱引用UI桥不能消除闭包的强引用。

最小边界：先引入纯Java `TaskCoordinator` 与不可变请求上下文（id、任务种类、取消能力、终态），所有权在这里封装；文件操作用应用级Context的薄适配层；Activity只分发事件/渲染快照。首次提取保持单worker、不排队请求、同一互斥范围和既有JNI命名入口，不同时改服务架构。

可观察的语义耦合：`:64,186-201,321` 的校验/导入也走 `launch(...,true)`，`:192`清空lastText，随后覆盖last-result.json。这是确定的当前行为，不是崩溃证据。先用特征测试锁住行为；再作为独立产品行为变更，把“模型操作状态”与“转写结果”分开，避免用户仅校验模型就丢失尚未导出的编辑文字。

验收：拒绝重复启动；任何失败只能释放自身owner；请求id绑定报告/临时WAV；重建不能发布旧请求结果；模型维护是否保留正文成为显式策略并有测试。

### 2. 高优先：抽取模型仓库，0.4在这个边界内开发

位置：`MainActivity.java:217-272`。

`importModel`同时做SAF枚举、白名单、去重、空间检查、已存在文件SHA、复制限额、临时文件发布、清理和最终全量校验，Android调用与纯文件逻辑无法分别注入故障。风险来自错误路径和未来取消边界，而不是简单的行数。

最小边界：`ModelRepository`持固定清单/本地文件规则，`ModelSource`封装SAF query/open，`ModelOperation`负责进度和协作取消。取消必须与发布/终态有明确先后关系；provider阻塞read/query时只记录请求取消，IO返回并清理后才能解锁。不允许通过提前释放RUNNING制造“取消成功”。

**源码确认的条件性缺陷（中等严重度，未实机复现）：** `MainActivity.java:258-262`在回收旧模型`.part`之前检查完整文件所需空间。若导入中进程被杀，finally不执行；模型残片又不属于ResultFiles顶层清理范围。设新文件S、残片P、空闲F，当`F < S+64MiB`但`F+P >= S+64MiB`，本可回收后重试，却会一直提前报空间不足。应在模型仓库同一owner下验证并删除固定清单的遗留普通`.part`、确认成功后再检查空间；不让结果清理器递归删除模型。新增可注入空间查询的红绿回归，避免实际占满磁盘。此窄修复无需等待整个重构完成。

此外通用`launch`对CancellationException固定显示“已取消录音”，0.4新增导入取消必须按任务类型区分，不能只增加throw。

文件删除仅在同一owner下、用户确认后，按固定内部清单和明确临时文件名单操作；拒绝路径逃逸/符号链接，不泛化递归删除。删除开始即撤销verified；失败报告部分删除，不宣称事务回滚。导入保留完整文件复用，不宣称大文件字节级断点续传。

潜在IO优化：新文件copy后读part做SHA，最终verifyModel再次读取；完整复用文件也有二次校验。可先测字节数/时间，再讨论复制时增量hash和验证凭证。默认不删发布前检查/最终门槛；只在不放宽大小/SHA及发布后身份约束、具备故障测试时减少冗余读。此次没有性能实测收益结论。

验收：空/重复/超大目录、缺文件、短读/超长/坏SHA、空间不足、read/write/close/rename失败、取消在复制/校验/发布边界、已有文件保留、删除遇非普通文件、所有错误后状态一致。

### 3. 中优先：将结果版本和导出所有权约束封装起来

位置：`ExportSession.java:17-37`；`MainActivity.java:95-112,174-184,302-318`。

当前生产路径begin/clear/write已共享owner，epoch和唯一requestCode也已有测试；不将已修复的导出漏洞重新算作新缺陷。但 `begin(String)`可绕过owner，`beginOwned`允许任意AtomicBoolean，`valid`与实际写入的不可交错约束仍由调用者维持。编辑采用字符串相等判断，而不是显式结果revision；后续多入口扩展更容易破坏规则。

最小边界：`ResultStore`维护正文/revision，绑定唯一coordinator；导出票据由该store创建/消费；可绕过的入口收窄。保留picker等待不持owner、clear撤销旧票据、旧回调不消费新请求及云provider隐私提示。不把重构当已经修复已复现泄露。

验收：重用41项已有结果检查，增加结果revision变化、stale edit、clear/begin/write顺序和busy拒绝；Android层补SAF取消/重建/延迟回调。不要仅靠按钮disabled判断互斥。

### 4. 中优先：提取推理结果解析，并补生产编排测试

位置：`MainActivity.java:273-301`；`native/apk/asr_jni.cpp:50-55`；`scripts/test-minimal-apk.sh:8-11`。

JNI返回“两个计时值、token数、换行、raw正文”，Java直接substring/split/parse。JNI目前为可信内部生产者、且已有异常处理；未发现合法输出实际解析失败。建议先抽出 `InferenceResult.parse`，明确字段数、换行、有限/非负计时、token范围和Unicode/raw保留；暂不修改native ABI或MNN路径。

93项host checks只覆盖6个helper，不执行MainActivity/AudioRecord/ContentResolver/JNI。优先让重构后的生产coordinator/repository在fake executor、stream、backend下可测试，再补最小Android仪器/设备验收；不能无限增加helper断言并视为集成覆盖。

### 暂不改写：RecordingControl与MNN核心

`RecordingControl.java:10-25`已对start/cancel/commit使用同一同步gate，20项确定性测试通过。`startBackend.run()`在gate内，硬件start阻塞会拖慢UI取消，这是已有明确风险；不能简单移出锁，否则会重新打开cancel/start竞态。未来必须作为独立状态机/设备延迟项目处理。

JNI mutex、RAII、EOS判定与现有MNN补丁保持。性能优化应另外测量加载/推理/内存并设质量回归，不与0.4重构混跑。

## 推荐交付拆分（先后次序，不表示已实现）

1. **R1：行为保持的结构提取。** 特征测试→任务协调器→模型仓库；每一步独立93项回归/构建/复核，不同时重写录音gate/导出协议/JNI。
2. **0.4：模型管理完成。** 可观测进度、协作取消、已验证完整文件复用、确认删除；另行明确模型维护不清空转写结果的产品语义。保留0.3产物，做升级/失败恢复/取消/删除实机验收。
3. **C1：生命周期与诊断。** 请求身份/状态快照复用到未来服务；默认无正文/音频的用户主动诊断导出，多设备/重复请求/低内存/取消延迟。模型短驻留和native中止另立测试门槛，不预先承诺可靠取消。
4. **D/E/F：长音频/VAD→IME→权限受控Binder/AIDL。** 继承统一单引擎owner；厂商GPU/NPU优化仍推迟。真实采样率回退、量化分别做正确性/资源基准，不堆进同一版本。

## 本轮验证

- `bash scripts/nix-env.sh apk bash scripts/test-minimal-apk.sh`：32+20+41=93 checks通过。
- `python3 -m unittest discover -s tests/p0 -p 'test_*.py' -v`：5项契约测试通过，不是模型数值/准确率验证。
- 构建输入26项SHA：0 mismatch；只核对源码与记录，不代表本轮重新构建。
- 日志：`.work/reviews/current-host-tests.txt`、`current-p0-contract-tests.txt`、`current-build-identity.json`。
- 独立只读审查完成：`reports/review/current-complexity-independent.md`。与主审重构方向一致，补充上述模型残片低存储恢复缺陷；本轮未执行缺陷故障注入。
- 审查期间父会话只更新规划/文档，产品源码26项指纹始终匹配。独立审查没有运行测试；93+5项为父会话实际执行，不混淆证据来源。没有新增实机/P0完整准确率结论。
