# Java → Kotlin 迁移

任务：`llm-asr-ap5`；活动计划：`.planning/llm-asr-ap5/`。

## 可行性与技术风险

没有语言/Android/JNI层面的根本不兼容，但不是零风险的文本转换。当前项目使用官方 Android 工具直接打包，不能只添加 `.kt`：

- `flake.lock` 锁定的 nixpkgs 默认提供 Kotlin 2.4.10，但实际SDK35 D8/R8只支持到metadata2.1，完整构建发现不兼容重写警告；该中间包拒绝交付。改为固定Kotlin2.0.21归档SHA的Nix override；编译器与stdlib必须同版。
- `scripts/compile-jvm-sources.sh` 先让 kotlinc 解析 Kotlin 和 Java 声明、输出 Kotlin class，再用 javac 编译 Java。JVM target 保持8；不引入 Gradle/Maven/反射库/协程。
- host Java tests 必须带同版 `kotlin-stdlib.jar`。APK 的 d8 输入显式加入 stdlib 和 annotations，不能编译通过而漏打包运行时。D8退出0也不充分：warning/metadata重写异常使构建fail closed，不绕过兼容性检查。
- Java 调用的静态工厂/常量/公开字段用 `@JvmStatic`、`const val`、`@JvmField` 保持；不把 DTO 改为 data class 引入新相等/拷贝语义。
- Kotlin 的空值检查、Unicode trim、split 默认规则与 Java 不完全相同，逐类保留原契约并补负例。
- `RecordingControl` 保留 JVM synchronized monitor 覆盖的 start/cancel/commit gate；不改成协程 Mutex，也不提前检查 backend 空值改变取消优先次序。
- JNI 声明首批仍为 Java，真实 `javac -h` 与 NDK强制头、实际DSO符号检查保持。后续迁移桥时需单独验证 JVM descriptor/native owner，不能手写假声明代替。

## 批次

第一批接入可复现混合构建，迁移 `AsrText`、`PcmWave`、`RecordingControl`、`NativeResponse`。当前其余58个生产文件仍为Java，不代表全量迁移完成。Java tests 保留以验证互操作。

后续按协调器、模型与管理、日志/导出、推理、平台/Android入口分批转换；高风险行为变异测试先适配再移走被测试的生产Java。每批均需要全量host、全源Android编译、JNI、DEX/签名/权限/组件/产物输入检查及本地Git提交。

## 验证边界

第一批Kotlin2.0.21最终完整构建b5a7aff69 exit0，D8日志为空；127构建输入/132冻结输入匹配，JNI/MNN身份不变。新增23 Java ABI/语义checks和既有全套/mutants通过；直接自审见[报告](../reports/review/kotlin-batch1-self-review.md)。当前产物身份见[APK状态](../reports/apk/status.md)，本地提交成功后才交付。host/构建通过不等于真实设备验收：App/IME录音、切换字段、模型SAF、导出、生命周期与JNI运行仍需设备回归。代理不访问设备、麦克风或私人文件。本轮不改变模型数学、权限、包名或签名密钥，不推送远端。
