## 窄复核结论

**两个已接受问题均可在源码审查层关闭；未发现新增阻塞。** 完整构建是否成功及最终 APK 身份，仍由父会话重建后闭环，本结论不代替执行验收。

### 1. 产品源码未改：确认

只读核验结果：

- 原冻结与当前冻结均为 **112 项**，路径集合完全一致。
- SHA-256 变化仅有：
  - `tests/TaskCoordinatorTest.java`
  - `scripts/build-minimal-apk.sh`
- 当前磁盘 **112/112 项匹配当前 frozen.json**。
- 冻结内 **65 项 Android/native 产品输入**与原审版本一致。
- 对两文件逆向撤销本次修正后，在内存中重建的内容分别匹配原冻结 SHA-256，确认没有夹带其他修改。

### 2. 原 P2：listener 断言被吞——关闭

**证据：`tests/TaskCoordinatorTest.java:105–121`。**

listener 现在仅记录释放时观察到的 `finalized` 值；`queue.drain()` 返回后，第 121 行在测试主线程检查观察序列严格等于 `[1]`。该断言不再位于 `TaskCoordinator.java:78–82` 的 listener 异常隔离范围内，失败会从 `main` 传播，不能被生产隔离逻辑吞掉。

原有去重、移除、busy 序列、坏 observer 隔离和 fatal 检查均保留。故意抛出 `AssertionError("observer fault")` 的坏 listener 是隔离测试输入，不是仍被吞掉的验收断言。

只读查看了 `.work/refactor-phase19-final/assertion-negative/`：

- 负例源码相对当前测试仅将观察值改为 `0`。
- `run.log` 显示主线程 `AssertionError: check 19`，定位到第 121 行。
- `compile.log` 为空，日志本身未记录退出码；**我没有执行该负例，也不独立宣称其编译或退出码已验证**。

### 3. 原 LOW-1：完整构建缺少 Java 派生签名编译——关闭

**证据：`scripts/build-minimal-apk.sh:3,43–46`，`scripts/compile-jni-object.sh:3,10–18`。**

新增调用在实际链接之前无条件执行。子脚本用 `javac -h` 生成当前 Java native 声明的头，再以 `-include` 强制纳入同一 `native/apk/asr_jni.cpp` 的 ARM64 编译。父、子脚本均启用 `set -euo pipefail`，没有忽略失败的分支；签名编译失败会阻止继续链接打包。

相对原冻结，构建脚本**仅增加两行说明和一行调用**，既有 host 测试、MNN 哈希检查、Java descriptor/真实 DSO 导出检查、DEX、签名、对齐、报告绑定、输入指纹及最终包检查均未删除或绕过。

### 残余限制

- 签名检查 object 是强制前置产物；链接脚本仍重新编译同一 JNI 源码，而非直接链接这个检查 object。冻结输入不变时满足本次接受的修复方式，但不提供构建期间并发改源码的原子快照保证。
- 本次未运行 tests、编译、构建或设备命令，也未验收最终 APK。
- 原 disposition 中 publisher 交接定向测试缺口、RuntimeLogWorker Error 恢复边界及 R5/R6 defer 均保持，不因关闭这两项而视为解决。