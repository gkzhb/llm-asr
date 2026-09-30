# Kotlin 首批直接自审

任务 `llm-asr-ap5`，基线 `d929cb1`。本报告为实现者直接自审，不冒称独立审查。首批4生产类；余58 Java未迁移。

## 检查范围

- `AsrText`：原 marker偏移10、全局结束标记移除、Java trim 的 <=U+0020契约保留；Unicode whitespace边界Java caller回归。
- `PcmWave`：RATE/上限/下限常量同值，signed short拆字节保留，调用原 `WaveInput.le` 明确long扩展，`@Throws(IOException)`保留Java checked catch；length负值先于null解引用的短路语义保留。
- `RecordingControl`：所有原synchronized public方法仍为JVM `ACC_SYNCHRONIZED`，worker/backend执行仍持同this monitor，cancel/start/commit资格与赋值顺序不变；backend失败/Error仍不设置started；已取消时null backend仍返回false。
- `NativeResponse`：nullable byte[]入口维持IAE空返回，UTF8 decoder REPORT不变，Java Pattern split limit0保留尾部空字段丢弃语义，Long/Double Java parser保留，MAX_TOKENS128不变，fields公开final和工厂static的Java ABI回归。未使用data class或新相等语义。
- 构建：kotlinc解析混合声明后javac输出Java，不打compiler/reflect，仅显式stdlib+annotations加入d8。JNI仍真实Java声明生成头再NDK强制include/实际符号校验。源码递归计数/唯一源及依赖负例仍执行。
- 测试运行路径：host Java caller追加同版stdlib，既有production mutant使用本轮fresh类目录+runtime；不继承旧class缓存。
- 包检查：读取DEX `class_def`，不能只用Intrinsics字符串/引用通过。缺runtime class_def但保留string/type的绑定重算负例被拒绝；旧权限/组件/资产/ABI/签名/report-binding检查保留。
- 许可：APK携带锁定分发的Apache text和stdlib相关第三方notices，未打包编译器第三方代码。jar本体不入Git；构建记录compiler版本与runtime jar SHA。

## 发现及处置

1. 空host classpath原被Bash `${2:?}`拒绝，未进入编译；改`${2?}`后全套重跑通过，失败日志保留。
2. 初版DEX字符串扫描只能证明引用，改实际class定义解析；绑定fixture专门保留缺定义的引用。
3. Kotlin null短路次序与Java不同的风险通过nullable backend/PCM参数且在原位置解引用处理，新增Java caller回归。
4. 编译提示PcmWave重复非空assert，去掉不必要assert，最终全构建重跑；未改合法/非法输入契约。

## 当前证据和限制

- `b48f7db3f` exit0：完整host、4 Kotlin +58 Java的Android全源JVM8编译通过。`KotlinMigrationTest`23 checks；既有录音/owner/IME/模型/日志/导出、production mutation及checker负例均通过，见 `.work/kotlin-migration/host-fixed.log` / `android-fixed.log`。
- `ba88ee850` 完整APK/JNI构建exit0，但D8 metadata2.4超出支持2.1且出现重写异常。自审拒绝交付该中间包；新增D8诊断fail-closed门槛，不能以exit0或DEX类存在代替工具链兼容性。
- 兼容compiler2.0.21归档85822011 bytes，经官方sha sidecar比对和ZIP130 entries校验；官方SHA0352c0a45bd22f80f6b26e485cd04da8047baa5de54865281fb9f89a4a7bcf2a固定Nix override。网络失败不放宽SHA，用户新增代理授权后Range续传成功。本版assets许可按同版归档更新。
- `b207f5970` env exit0，真实kotlinc2.0.21；更新132冻结输入后`b5a7aff69`最终全构建exit0/APK_READY。全套host/23新checks/3mutants、全源Java+Kotlin、真实JNI生成头/object/linked ABI、DEX class_def/runtime、签名/组件/权限/绑定通过。d8.txt完全为空，不沿用2.4编译证据。
- 最终127 build-input/132 frozen/current SHA相同，native provenance逐项和旧报告相同（578对象），JNI DSO逐字相同；standalone APK checker再次通过。APK3167222 bytes，SHAf24b4da1f254f17a324b756990408e3f4474c2b09afce94290d06d608562a163。最终复核后只更新文档/报告，无产品输入改动。
- 本批未发现剩余源码/构建阻塞，准备安全暂存与本地提交；任务仍未全量完成。
- 无设备/麦克风/私人数据访问，不声明Android运行/SAF/IME/JNI验收或性能提升。
- 余58类继续保持Java；本批不关闭全量迁移任务，不变更协程/Compose/Gradle/数学/权限。
