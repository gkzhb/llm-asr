# M3 直接自审（非独立review）

writer m3-build / llm-asr-76d.4 / base-current538dcb66。父复审待验收，未提交未交付。

## 已检查与证据

- 真实module compile依赖：`:app → :asr-runtime → :core`，core无Android/JNI/UI classpath；真实javac正负compile5拒绝有目标diagnostic，固定工具版本/strict保留。
- sourceSets唯一归属：实际生产63（50core/5runtime/8app）；JavaCompile include+空sourcepath，app Kotlin task exclude。最终fresh全部63顶级class/内嵌class唯一归属，core class无android/org.json字节。checker5负例普通/-O；freshapp Kotlin为0，12DEX无重复、所有63源owner均定义。
- 测试实质：core34/runtime1JUnit直接执行原main全部body，不反射、不吞Throwable、异常/回调/latch/cleanup断言无产品改动；原28入口/8路径/43ABI及所有production mutants完整重新执行。coverage map docs/module-test-map.md。
- 平台边界：InferenceAdapterTest包含JNI桥构造，留runtime；core无fake JNI实现/反向runtime依赖。runtime测试不冒称JNI/Android运行。
- 指纹：新modulebuild/locks/src/test与所有旧build输入加入APK清单174；JUnit body额外sample/caller/native源有task inputs。最终freeze-current-report174全部匹配，74product frozenSHA原字节保持。
- JNI/native：全63源freshSDK35编译、javac真实生成头、NDKobject、当前DSO实际符号/config/578对象/provenance/ELF LOAD16KiB/staticruntime保持d5bacffc...416；没有改数学/flags/expected SHA。
- 实际包：apksigner验签、aapt2组件/权限/Manifest、zipalign、资产hash、全部DEX owner/无测试依赖，签名/包名/version/min29-target35不变；APKnewSHA a0494d25...a3f3。
- 依赖安全：JUnit4.13.2/Hamcrest1.3直接/传递锁，5artifact真实SHA256固定，官方HTTPS与SHA1 sidecar交叉；不以404生成假SHA256，不调用strict-relax；隔离真实AGP checksum拒绝exit1证明gate运行。根plugin mapping仅复用固定实现，不升级工具。
- 数据安全：生产src无diff；未访问设备/麦克风/私人文件；没有模型/音频/APK/缓存/key进入待提交报告。reports仅JSON/XML树/签名公证书SHA/公开构建输入摘要，用例报告只保存名称不复制system-out（后者包含测试模拟私密字符串）。两个他人分析与父监管目录未改。
- 并发/资源：单Gradleworker/fork，native低并发，未与模型reference运行；没有委派/安装/上传/push/merge/stage/commit/close。

## 阻塞修复

本轮发现的AGP source-filter失效与Kotlin重复DEX已由实际负例/包gate捕获并修复，不以green JUnit冒称模块成立。owner checker缺顶级class保护不足亦由新增negative查出修复。最终strict offline rerun70任务0失败；具体失败命令日志见status.md和独占progress。

## 仍不能宣称的内容

- 父/独立代码审查尚未完成，父复验未执行，任务acceptance中的提交未满足；等待父审，不关闭任务。
- Android仪器/Activity lifecycle真实SDK运行、SAF/provider、IME/InputConnection、录音/native运行、4KiB/16KiB设备均not run。
- 核心测试classpath无Android，但整仓配置仍要求Android SDK，不能宣称完全无SDK开发环境；offline依赖缓存准备后通过不等于hermetic。
- 源码物理共享过渡必须保留task级filter+actual owner gate；新增类需要明确归属/基准更新。未承诺release build、R8、CI或M5。
- JUnit调用main为完整body过渡映射，不是逐私有case现代化；原PartRecovery固定.work/red靠单writer/单worker/fork串行，未承诺抵抗独立外部并跑。
- core平台污染checker目前是classbytes保守扫描，不是完整bytecode语义解析；真实compile classpath负例另外提供实质边界。
